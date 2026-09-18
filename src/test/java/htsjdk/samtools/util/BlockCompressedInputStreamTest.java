package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.FileTruncatedException;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordSetBuilder;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import htsjdk.samtools.util.zip.InflaterFactory;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Inflater;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class BlockCompressedInputStreamTest extends HtsjdkTest {
    // random data pulled from /dev/random then compressed using bgzip from tabix
    private static final Path BLOCK_UNCOMPRESSED = Paths.get("src/test/resources/htsjdk/samtools/util/random.bin");
    private static final Path BLOCK_COMPRESSED = Paths.get("src/test/resources/htsjdk/samtools/util/random.bin.gz");
    private static final long[] BLOCK_COMPRESSED_OFFSETS = new long[] {
        0, 0xfc2e, 0x1004d, 0x1fc7b, 0x2009a,
    };
    private static final long[] BLOCK_UNCOMPRESSED_END_POSITIONS = new long[] {64512, 65536, 130048};

    @Test
    public void testTruncatedStream() throws Exception {
        byte[] compressed = Files.readAllBytes(BLOCK_COMPRESSED);
        byte[] truncated = Arrays.copyOf(compressed, compressed.length * 2 / 3);
        try (BlockCompressedInputStream stream = new BlockCompressedInputStream(new ByteArrayInputStream(truncated))) {
            Assert.expectThrows(FileTruncatedException.class, () -> InputStreamUtils.readFully(stream));
        }
    }

    @Test
    public void stream_should_match_uncompressed_stream() throws Exception {
        byte[] uncompressed = Files.readAllBytes(BLOCK_UNCOMPRESSED);
        try (BlockCompressedInputStream stream =
                new BlockCompressedInputStream(Files.newInputStream(BLOCK_COMPRESSED))) {
            for (int i = 0; i < uncompressed.length; i++) {
                Assert.assertEquals(stream.read(), Byte.toUnsignedInt(uncompressed[i]));
            }
            Assert.assertTrue(stream.endOfBlock());
        }
    }

    @Test
    public void endOfBlock_should_be_true_only_when_entire_block_is_read() throws Exception {
        long size = Files.size(BLOCK_UNCOMPRESSED);
        // input file contains 5 blocks
        List<Long> offsets = new ArrayList<>();
        for (int i = 0; i < BLOCK_UNCOMPRESSED_END_POSITIONS.length; i++) {
            offsets.add(BLOCK_UNCOMPRESSED_END_POSITIONS[i]);
        }
        List<Long> endOfBlockTrue = new ArrayList<>();
        try (BlockCompressedInputStream stream =
                new BlockCompressedInputStream(Files.newInputStream(BLOCK_COMPRESSED))) {
            for (long i = 0; i < size; i++) {
                if (stream.endOfBlock()) {
                    endOfBlockTrue.add(i);
                }
                stream.read();
            }
        }
        Assert.assertEquals(endOfBlockTrue, offsets);
    }

    @Test
    public void decompression_should_cross_block_boundries() throws Exception {
        byte[] uncompressed = Files.readAllBytes(BLOCK_UNCOMPRESSED);
        try (BlockCompressedInputStream stream =
                new BlockCompressedInputStream(Files.newInputStream(BLOCK_COMPRESSED))) {
            byte[] decompressed = new byte[uncompressed.length];
            stream.read(decompressed);
            Assert.assertEquals(decompressed, uncompressed);
            Assert.assertTrue(stream.endOfBlock());
            Assert.assertEquals(stream.read(), -1);
        }
    }

    @Test
    public void seek_should_read_block() throws Exception {
        byte[] uncompressed = Files.readAllBytes(BLOCK_UNCOMPRESSED);
        try (SeekableFileStream sfs = new SeekableFileStream(BLOCK_COMPRESSED)) {
            try (BlockCompressedInputStream stream = new BlockCompressedInputStream(sfs)) {
                // seek to the start of the first block
                for (int i = 0; i < BLOCK_COMPRESSED_OFFSETS.length - 1; i++) {
                    stream.seek(BLOCK_COMPRESSED_OFFSETS[i] << 16);
                    Assert.assertEquals(sfs.position(), BLOCK_COMPRESSED_OFFSETS[i + 1]);
                    // check
                    byte[] actual = new byte[uncompressed.length];
                    int len = stream.read(actual);
                    actual = Arrays.copyOf(actual, len);
                    byte[] expected =
                            Arrays.copyOfRange(uncompressed, uncompressed.length - actual.length, uncompressed.length);
                    Assert.assertEquals(actual, expected);
                }
            }
        }
    }

    @Test
    public void available_should_return_number_of_bytes_left_in_current_block() throws Exception {
        try (BlockCompressedInputStream stream = new BlockCompressedInputStream(BLOCK_COMPRESSED)) {
            for (int i = 0; i < BLOCK_UNCOMPRESSED_END_POSITIONS[0]; i++) {
                Assert.assertEquals(stream.available(), BLOCK_UNCOMPRESSED_END_POSITIONS[0] - i);
                stream.read();
            }
        }
    }

    private static class CountingInflater extends Inflater {
        // Must be static unfortunately, since there's no way to reach down into an inflater instance given a stream
        static int inflateCalls = 0;

        CountingInflater(boolean gzipCompatible) {
            super(gzipCompatible);
        }

        @Override
        public int inflate(byte[] b, int off, int len) throws java.util.zip.DataFormatException {
            inflateCalls++;
            return super.inflate(b, off, len);
        }
    }

    private static class CountingInflaterFactory extends InflaterFactory {
        @Override
        public Inflater makeInflater(boolean gzipCompatible) {
            return new CountingInflater(gzipCompatible);
        }
    }

    @FunctionalInterface
    private interface CheckedExceptionInputStreamSupplier {
        InputStream get() throws IOException;
    }

    private List<String> writeTempBlockCompressedFileForInflaterTest(final Path tempFile) throws IOException {
        final List<String> linesWritten = new ArrayList<>();
        try (final BlockCompressedOutputStream bcos = new BlockCompressedOutputStream(tempFile, 5)) {
            String s = "Hi, Mom!\n";
            bcos.write(s.getBytes()); // Call 1
            linesWritten.add(s);
            s = "Hi, Dad!\n";
            bcos.write(s.getBytes()); // Call 2
            linesWritten.add(s);
            bcos.flush();
            final StringBuilder sb =
                    new StringBuilder(BlockCompressedStreamConstants.DEFAULT_UNCOMPRESSED_BLOCK_SIZE * 2);
            s = "1234567890123456789012345678901234567890123456789012345678901234567890\n";
            while (sb.length() <= BlockCompressedStreamConstants.DEFAULT_UNCOMPRESSED_BLOCK_SIZE) {
                sb.append(s);
                linesWritten.add(s);
            }
            bcos.write(sb.toString().getBytes()); // Call 3
        }
        return linesWritten;
    }

    @DataProvider(name = "customInflaterInput")
    public Object[][] customInflateInput() throws IOException {
        final Path tempFile = Files.createTempFile("testCustomInflater.", ".bam");
        IOUtil.deleteOnExit(tempFile);
        final List<String> linesWritten = writeTempBlockCompressedFileForInflaterTest(tempFile);
        // wrap our expected output in a lambda to prevent massive string expansion of the test params during test
        // execution
        final QuietTestWrapper<List<String>> expectedOutputSupplier = new QuietTestWrapper<>(linesWritten);

        final InflaterFactory countingInflaterFactory = new CountingInflaterFactory();

        return new Object[][] {
            {
                (CheckedExceptionInputStreamSupplier) () ->
                        new BlockCompressedInputStream(Files.newInputStream(tempFile), false, countingInflaterFactory),
                expectedOutputSupplier,
                4
            },
            {
                (CheckedExceptionInputStreamSupplier)
                        () -> new BlockCompressedInputStream(tempFile, countingInflaterFactory),
                expectedOutputSupplier,
                4
            },
            {
                (CheckedExceptionInputStreamSupplier)
                        () -> new AsyncBlockCompressedInputStream(tempFile, countingInflaterFactory),
                expectedOutputSupplier,
                4
            },
            {
                (CheckedExceptionInputStreamSupplier) () -> new BlockCompressedInputStream(
                        new URL("http://broadinstitute.github.io/picard/testdata/index_test.bam"),
                        countingInflaterFactory),
                null,
                21
            },
        };
    }

    @Test(dataProvider = "customInflaterInput")
    public void testCustomInflater(
            final CheckedExceptionInputStreamSupplier bcisSupplier,
            final QuietTestWrapper<List<String>> expectedOutputSupplier,
            final int expectedInflateCalls)
            throws Exception {
        CountingInflater.inflateCalls = 0;

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(bcisSupplier.get()))) {
            String line;
            for (int i = 0; (line = reader.readLine()) != null; ++i) {
                if (expectedOutputSupplier != null) {
                    Assert.assertEquals(
                            line + "\n", expectedOutputSupplier.get().get(i));
                }
            }
        }

        Assert.assertEquals(CountingInflater.inflateCalls, expectedInflateCalls, "inflate calls");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSetNullInflaterFactory() {
        // test catching null InflaterFactory
        BlockGunzipper.setDefaultInflaterFactory(null);
    }

    // Blocks that hold no data, in mid-stream. Joining block-compressed files end to end, as cat does, leaves the
    // end-of-file marker of each part where the parts meet, and it is not the end of the data.

    private static final String FIRST_PART = "first part, line 1\nfirst part, line 2\n";
    private static final String SECOND_PART = "second part, line 1\n";

    /** A whole block-compressed file: the text, then the empty block that marks the end. */
    private static byte[] blockCompressed(final String text) throws IOException {
        return blockCompressed(text.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] blockCompressed(final byte[] content) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BlockCompressedOutputStream out = new BlockCompressedOutputStream(bytes, (Path) null)) {
            out.write(content);
        }
        return bytes.toByteArray();
    }

    private static byte[] joined(final byte[]... parts) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (final byte[] part : parts) {
            bytes.write(part);
        }
        return bytes.toByteArray();
    }

    /** Reads to the end, each time asking for exactly what is left of the current block, so that every read starts on a block boundary. */
    private static String readBlockByBlock(final BlockCompressedInputStream in) throws IOException {
        final ByteArrayOutputStream text = new ByteArrayOutputStream();
        final byte[] buffer = new byte[1 << 16];
        while (true) {
            final int n = in.read(buffer, 0, Math.max(1, in.available()));
            if (n == -1) {
                return text.toString(StandardCharsets.UTF_8);
            }
            text.write(buffer, 0, n);
        }
    }

    @Test
    public void testReadThatStartsAtAnEmptyBlockGoesOnToTheNextBlock() throws IOException {
        final byte[] file = joined(blockCompressed(FIRST_PART), blockCompressed(SECOND_PART));
        try (BlockCompressedInputStream in = new BlockCompressedInputStream(new ByteArrayInputStream(file))) {
            final byte[] buffer = new byte[1 << 16];
            Assert.assertEquals(in.read(buffer, 0, FIRST_PART.length()), FIRST_PART.length());
            Assert.assertEquals(in.read(buffer, 0, buffer.length), SECOND_PART.length());
            Assert.assertEquals(in.read(buffer, 0, buffer.length), -1);
        }
    }

    @Test
    public void testJoinedFilesAreReadToTheEnd() throws IOException {
        final byte[] file =
                joined(blockCompressed(FIRST_PART), blockCompressed(SECOND_PART), blockCompressed(FIRST_PART));
        try (BlockCompressedInputStream in = new BlockCompressedInputStream(new ByteArrayInputStream(file))) {
            Assert.assertEquals(readBlockByBlock(in), FIRST_PART + SECOND_PART + FIRST_PART);
        }
    }

    @Test
    public void testSeveralEmptyBlocksInARow() throws IOException {
        final byte[] empty = BlockCompressedStreamConstants.EMPTY_GZIP_BLOCK;
        final byte[] file = joined(blockCompressed(FIRST_PART), empty, empty, blockCompressed(SECOND_PART));
        try (BlockCompressedInputStream in = new BlockCompressedInputStream(new ByteArrayInputStream(file))) {
            Assert.assertEquals(readBlockByBlock(in), FIRST_PART + SECOND_PART);
        }
    }

    @Test
    public void testFileThatStartsWithAnEmptyBlock() throws IOException {
        final byte[] file = joined(BlockCompressedStreamConstants.EMPTY_GZIP_BLOCK, blockCompressed(FIRST_PART));
        try (BlockCompressedInputStream in = new BlockCompressedInputStream(new ByteArrayInputStream(file))) {
            Assert.assertEquals(readBlockByBlock(in), FIRST_PART);
        }
    }

    @Test
    public void testAvailableIsNotZeroWhileThereIsDataToCome() throws IOException {
        final byte[] file = joined(blockCompressed(FIRST_PART), blockCompressed(SECOND_PART));
        try (BlockCompressedInputStream in = new BlockCompressedInputStream(new ByteArrayInputStream(file))) {
            Assert.assertEquals(in.read(new byte[FIRST_PART.length()], 0, FIRST_PART.length()), FIRST_PART.length());
            Assert.assertEquals(in.available(), SECOND_PART.length());
        }
    }

    @Test
    public void testTheEndOfTheStreamStaysTheEnd() throws IOException {
        try (BlockCompressedInputStream in =
                new BlockCompressedInputStream(new ByteArrayInputStream(blockCompressed(FIRST_PART)))) {
            final byte[] buffer = new byte[1 << 16];
            Assert.assertEquals(in.read(buffer, 0, FIRST_PART.length()), FIRST_PART.length());
            Assert.assertEquals(in.read(buffer, 0, buffer.length), -1);
            Assert.assertEquals(in.read(buffer, 0, buffer.length), -1);
            Assert.assertEquals(in.available(), 0);
        }
    }

    @Test
    public void testFilePointerAtTheEndOfAFileIsThatOfItsEndOfFileMarker() throws IOException {
        final byte[] file = blockCompressed(FIRST_PART);
        final long marker = file.length - BlockCompressedStreamConstants.EMPTY_GZIP_BLOCK.length;
        try (BlockCompressedInputStream in = new BlockCompressedInputStream(new ByteArrayInputStream(file))) {
            final byte[] buffer = new byte[1 << 16];
            Assert.assertEquals(in.read(buffer, 0, FIRST_PART.length()), FIRST_PART.length());
            Assert.assertEquals(in.getFilePointer(), BlockCompressedFilePointerUtil.makeFilePointer(marker, 0));
            Assert.assertEquals(in.read(buffer, 0, buffer.length), -1);
            Assert.assertEquals(in.getFilePointer(), BlockCompressedFilePointerUtil.makeFilePointer(marker, 0));
        }
    }

    @Test
    public void testSeekingToAnEmptyBlockReadsWhatFollowsIt() throws IOException {
        final byte[] first = blockCompressed(FIRST_PART);
        final byte[] file = joined(first, blockCompressed(SECOND_PART));
        // Where the first part's data ends, which is where its end-of-file marker lies
        final long emptyBlock = first.length - BlockCompressedStreamConstants.EMPTY_GZIP_BLOCK.length;
        try (BlockCompressedInputStream in = new BlockCompressedInputStream(new SeekableMemoryStream(file, "joined"))) {
            in.seek(BlockCompressedFilePointerUtil.makeFilePointer(emptyBlock, 0));
            Assert.assertEquals(readBlockByBlock(in), SECOND_PART);
        }
    }

    @Test
    public void testFilePointerAfterTheLastByteOfAPartCanBeSeekedTo() throws IOException {
        final byte[] file = joined(blockCompressed(FIRST_PART), blockCompressed(SECOND_PART));
        try (BlockCompressedInputStream in = new BlockCompressedInputStream(new SeekableMemoryStream(file, "joined"))) {
            Assert.assertEquals(in.read(new byte[FIRST_PART.length()], 0, FIRST_PART.length()), FIRST_PART.length());
            final long betweenTheParts = in.getFilePointer();
            Assert.assertEquals(readBlockByBlock(in), SECOND_PART);
            in.seek(betweenTheParts);
            Assert.assertEquals(readBlockByBlock(in), SECOND_PART);
        }
    }

    @Test
    public void testJoinedFilesAreReadToTheEndAsynchronously() throws IOException {
        final byte[] file =
                joined(blockCompressed(FIRST_PART), blockCompressed(SECOND_PART), blockCompressed(FIRST_PART));
        try (BlockCompressedInputStream in = new AsyncBlockCompressedInputStream(new ByteArrayInputStream(file))) {
            Assert.assertEquals(readBlockByBlock(in), FIRST_PART + SECOND_PART + FIRST_PART);
        }
    }

    /**
     * A BAM is read a record at a time, so where two block-compressed parts meet between two records, the read of
     * the second part's first record begins exactly at the first part's end-of-file marker.
     */
    @Test
    public void testBamJoinedBetweenTwoRecordsIsReadToItsEnd() throws IOException {
        final SAMRecordSetBuilder records = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        for (int i = 0; i < 500; i++) {
            records.addFrag("read" + i, 0, 1 + 10 * i, false);
        }
        final Path bam = Files.createTempFile("joined.", ".bam");
        IOUtil.deleteOnExit(bam);
        try (SAMFileWriter writer = new SAMFileWriterFactory().makeBAMWriter(records.getHeader(), true, bam)) {
            records.getRecords().forEach(writer::addAlignment);
        }
        final byte[] uncompressed;
        try (BlockCompressedInputStream in = new BlockCompressedInputStream(bam)) {
            uncompressed = in.readAllBytes();
        }

        // Walk the BAM layout to the end of the 200th record: magic, header text, references, then records, each
        // field or record preceded by its length.
        final ByteBuffer layout = ByteBuffer.wrap(uncompressed).order(ByteOrder.LITTLE_ENDIAN);
        int position = 4;
        position += 4 + layout.getInt(position);
        final int references = layout.getInt(position);
        position += 4;
        for (int i = 0; i < references; i++) {
            position += 4 + layout.getInt(position) + 4;
        }
        for (int i = 0; i < 200; i++) {
            position += 4 + layout.getInt(position);
        }
        final byte[] joinedBam = joined(
                blockCompressed(Arrays.copyOfRange(uncompressed, 0, position)),
                blockCompressed(Arrays.copyOfRange(uncompressed, position, uncompressed.length)));

        try (SamReader expected = SamReaderFactory.makeDefault().open(bam);
                SamReader reader =
                        SamReaderFactory.makeDefault().open(SamInputResource.of(new ByteArrayInputStream(joinedBam)))) {
            final List<SAMRecord> found = reader.iterator().toList();
            Assert.assertEquals(found.size(), 500);
            Assert.assertEquals(found, expected.iterator().toList());
        }
    }
}
