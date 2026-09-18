package htsjdk.index;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFormatException;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.RuntimeIOException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

/** {@link FileBackedBinningIndex} must answer exactly as the same file read whole into a {@link BinningIndex}. */
public class FileBackedBinningIndexTest extends HtsjdkTest {
    private static final int REFERENCES = 5;

    private static long offset(final long blockAddress, final int withinBlock) {
        return BlockCompressedFilePointerUtil.makeFilePointer(blockAddress, withinBlock);
    }

    /** Records on references 0, 2 and 3; references 1 and 4 have none. */
    private static BinningIndex build(final int minShift, final int depth, final boolean forCsi, final long unplaced) {
        final BinningIndex.Builder builder = new BinningIndex.Builder(minShift, depth, forCsi).reportingRecordCounts();
        long block = 10;
        for (final int reference : new int[] {0, 2, 3}) {
            for (int start = 1_000; start < 900_000; start += 7_919) {
                builder.add(reference, start, start + 150 + reference * 20_000, offset(block, 0), offset(block, 300));
                builder.addRecordCounts(1, 0);
                block += 5_000;
            }
        }
        builder.addNoCoordinateRecords(unplaced);
        return builder.build(REFERENCES);
    }

    private static byte[] baiBytes(final BinningIndex index, final boolean withTrailer) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final BinaryCodec codec = new BinaryCodec(bytes);
        codec.writeBytes(new byte[] {'B', 'A', 'I', 1});
        codec.writeInt(index.getReferenceCount());
        index.writeBaiLayout(codec);
        codec.close();
        final byte[] all = bytes.toByteArray();
        return withTrailer ? all : java.util.Arrays.copyOf(all, all.length - 8);
    }

    private static byte[] csiBytes(final BinningIndex index, final byte[] aux) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final BinaryCodec codec = new BinaryCodec(new BlockCompressedOutputStream(bytes, (Path) null));
        index.writeCsi(codec, aux);
        codec.close();
        return bytes.toByteArray();
    }

    private static Path write(final byte[] bytes, final String suffix) throws IOException {
        final Path path = Files.createTempFile("FileBackedBinningIndexTest.", suffix);
        path.toFile().deleteOnExit();
        return Files.write(path, bytes);
    }

    private static BinningIndex wholeBai(final byte[] bytes) {
        final BinaryCodec codec = new BinaryCodec(new java.io.ByteArrayInputStream(bytes));
        codec.readBytes(new byte[4]);
        return BinningIndex.readBaiLayout(codec, codec.readInt(), BinningIndex.BAI_MIN_SHIFT, BinningIndex.BAI_DEPTH);
    }

    private static BinningIndex wholeCsi(final byte[] bytes) throws IOException {
        try (InputStream in = new BlockCompressedInputStream(new java.io.ByteArrayInputStream(bytes))) {
            return BinningIndex.readCsi(new BinaryCodec(in)).index();
        }
    }

    /** References asked for out of order, twice over, and the spans of some queries. */
    private static void assertAnswersAs(final FileBackedBinningIndex actual, final BinningIndex expected) {
        Assert.assertEquals(actual.getReferenceCount(), expected.getReferenceCount());
        Assert.assertEquals(actual.getMinShift(), expected.getMinShift());
        Assert.assertEquals(actual.getDepth(), expected.getDepth());
        for (final int reference : new int[] {3, 0, 4, 2, 1, 3, 0}) {
            Assert.assertEquals(actual.getReference(reference), expected.getReference(reference), "ref " + reference);
        }
        for (int reference = 0; reference < REFERENCES; reference++) {
            for (int start = 1; start < 1_000_000; start += 133_331) {
                Assert.assertEquals(
                        actual.getSpanOverlapping(reference, start, start + 40_000)
                                .getChunks(),
                        expected.getSpanOverlapping(reference, start, start + 40_000)
                                .getChunks());
            }
        }
        Assert.assertEquals(actual.getNoCoordinateCount(), expected.getNoCoordinateCount());
        Assert.assertEquals(actual.loadAll(), expected);
    }

    @Test
    public void testBai() throws IOException {
        final byte[] bytes = baiBytes(build(14, 5, false, 12), true);
        try (FileBackedBinningIndex index = FileBackedBinningIndex.open(write(bytes, ".bai"), false)) {
            Assert.assertFalse(index.isCsi());
            assertAnswersAs(index, wholeBai(bytes));
        }
    }

    @Test
    public void testBgzfCompressedCsi() throws IOException {
        final byte[] bytes = csiBytes(build(12, 7, true, 3), new byte[] {1, 2, 3});
        try (FileBackedBinningIndex index = FileBackedBinningIndex.open(write(bytes, ".csi"), false)) {
            Assert.assertTrue(index.isCsi());
            Assert.assertEquals(index.getAux(), new byte[] {1, 2, 3});
            assertAnswersAs(index, wholeCsi(bytes));
        }
    }

    @Test
    public void testPrefilledBai() throws IOException {
        final byte[] bytes = baiBytes(build(14, 5, false, 12), true);
        try (FileBackedBinningIndex index = FileBackedBinningIndex.open(write(bytes, ".bai"), true)) {
            assertAnswersAs(index, wholeBai(bytes));
        }
    }

    @Test
    public void testPrefilledCsi() throws IOException {
        final byte[] bytes = csiBytes(build(14, 6, true, 0), new byte[0]);
        try (FileBackedBinningIndex index = FileBackedBinningIndex.open(write(bytes, ".csi"), true)) {
            assertAnswersAs(index, wholeCsi(bytes));
        }
    }

    @Test
    public void testBaiFromAStream() {
        final byte[] bytes = baiBytes(build(14, 5, false, 12), true);
        for (final boolean prefill : new boolean[] {false, true}) {
            try (FileBackedBinningIndex index =
                    FileBackedBinningIndex.open(new SeekableMemoryStream(bytes, "bai"), prefill)) {
                assertAnswersAs(index, wholeBai(bytes));
            }
        }
    }

    @Test
    public void testClosingTheIndexClosesTheStreamItWasOpenedFrom() {
        final byte[] bytes = baiBytes(build(14, 5, false, 12), true);
        for (final boolean prefill : new boolean[] {false, true}) {
            final boolean[] closed = {false};
            final SeekableMemoryStream stream = new SeekableMemoryStream(bytes, "bai") {
                @Override
                public void close() throws IOException {
                    closed[0] = true;
                    super.close();
                }
            };
            FileBackedBinningIndex.open(stream, prefill).close();
            Assert.assertTrue(closed[0], "stream left open with prefill=" + prefill);
        }
    }

    @Test
    public void testTheStreamIsClosedOnlyOnce() {
        final byte[] bytes = baiBytes(build(14, 5, false, 12), true);
        for (final boolean prefill : new boolean[] {false, true}) {
            final int[] closes = {0};
            final SeekableMemoryStream stream = new SeekableMemoryStream(bytes, "bai") {
                @Override
                public void close() throws IOException {
                    closes[0]++;
                    super.close();
                }
            };
            final FileBackedBinningIndex index = FileBackedBinningIndex.open(stream, prefill);
            index.close();
            index.close();
            Assert.assertEquals(closes[0], 1, "prefill=" + prefill);
        }
    }

    @Test
    public void testCsiFromAStream() throws IOException {
        final byte[] bytes = csiBytes(build(14, 6, true, 9), new byte[0]);
        for (final boolean prefill : new boolean[] {false, true}) {
            try (FileBackedBinningIndex index =
                    FileBackedBinningIndex.open(new SeekableMemoryStream(bytes, "csi"), prefill)) {
                assertAnswersAs(index, wholeCsi(bytes));
            }
        }
    }

    @Test
    public void testIndexOnAnotherFileSystem() throws IOException {
        final byte[] bytes = baiBytes(build(14, 5, false, 12), true);
        try (FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path path = Files.write(jimfs.getPath("/reads.bai"), bytes);
            for (final boolean prefill : new boolean[] {false, true}) {
                try (FileBackedBinningIndex index = FileBackedBinningIndex.open(path, prefill)) {
                    assertAnswersAs(index, wholeBai(bytes));
                }
            }
        }
    }

    @Test
    public void testReclaimedReferencesAreReadAgain() throws IOException {
        final byte[] bytes = baiBytes(build(14, 5, false, 12), true);
        try (FileBackedBinningIndex index = FileBackedBinningIndex.open(write(bytes, ".bai"), false)) {
            final ReferenceBins before = index.getReference(3);
            index.clearCache();
            final ReferenceBins after = index.getReference(3);
            Assert.assertNotSame(after, before);
            Assert.assertEquals(after, before);
            assertAnswersAs(index, wholeBai(bytes));
        }
    }

    @Test
    public void testReclaimedReferencesOfAPrefilledIndexAreReadFromTheFile() throws IOException {
        final byte[] bytes = csiBytes(build(14, 6, true, 9), new byte[0]);
        try (FileBackedBinningIndex index = FileBackedBinningIndex.open(write(bytes, ".csi"), true)) {
            index.clearCache();
            assertAnswersAs(index, wholeCsi(bytes));
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCsiWithABinningSchemeBeyondWhatCanBeAddressedIsRejected() throws IOException {
        final ByteArrayOutputStream header = new ByteArrayOutputStream();
        final BinaryCodec codec = new BinaryCodec(header);
        codec.writeBytes(BinningIndex.CSI_MAGIC);
        codec.writeInt(14); // min_shift
        codec.writeInt(100); // depth: 14 + 3 * 100 bits of position
        codec.writeInt(0); // l_aux
        codec.writeInt(0); // n_ref
        FileBackedBinningIndex.open(new SeekableMemoryStream(header.toByteArray(), "csi"), false);
    }

    @Test(expectedExceptions = SAMFormatException.class)
    public void testANegativeCountIsRejected() throws IOException {
        final ByteArrayOutputStream bai = new ByteArrayOutputStream();
        final BinaryCodec codec = new BinaryCodec(bai);
        codec.writeBytes(new byte[] {'B', 'A', 'I', 1});
        codec.writeInt(1); // n_ref
        codec.writeInt(1); // n_bin
        codec.writeInt(4681); // bin
        codec.writeInt(-1); // n_chunk
        try (FileBackedBinningIndex index =
                FileBackedBinningIndex.open(new SeekableMemoryStream(bai.toByteArray(), "bai"), false)) {
            index.getReference(0);
        }
    }

    @Test
    public void testAReferenceIsHeldWhileThereIsMemoryForIt() throws IOException {
        final byte[] bytes = baiBytes(build(14, 5, false, 12), true);
        try (FileBackedBinningIndex index = FileBackedBinningIndex.open(write(bytes, ".bai"), false)) {
            Assert.assertSame(index.getReference(2), index.getReference(2));
        }
    }

    @Test
    public void testBaiWithoutTheTrailingCount() throws IOException {
        final byte[] bytes = baiBytes(build(14, 5, false, 12), false);
        try (FileBackedBinningIndex index = FileBackedBinningIndex.open(write(bytes, ".bai"), false)) {
            Assert.assertFalse(index.getNoCoordinateCount().isPresent());
            Assert.assertEquals(index.getReference(3), wholeBai(bytes).getReference(3));
        }
    }

    @Test(expectedExceptions = RuntimeIOException.class)
    public void testAFailedReadOfTheTrailingCountIsNotTakenForItsAbsence() {
        // A linear index with every window a BAI can have is a little bigger than the reader's buffer, so the count
        // that follows it has yet to be read from the stream when it is asked for.
        final BinningIndex.Builder builder = new BinningIndex.Builder(14, 5).reportingRecordCounts();
        builder.add(0, 1, 100, offset(0, 0), offset(0, 50));
        builder.add(0, (1 << 29) - 200, (1 << 29) - 100, offset(9, 0), offset(9, 50));
        final boolean[] failing = {false};
        final SeekableMemoryStream stream = new SeekableMemoryStream(baiBytes(builder.build(1), true), "bai") {
            @Override
            public int read(final byte[] buffer, final int offset, final int length) throws IOException {
                if (failing[0]) {
                    throw new IOException("injected failure");
                }
                return super.read(buffer, offset, length);
            }
        };
        try (FileBackedBinningIndex index = FileBackedBinningIndex.open(stream, false)) {
            index.getReference(0);
            failing[0] = true;
            index.getNoCoordinateCount();
        }
    }

    @Test
    public void testIndexWithNoReferences() throws IOException {
        final byte[] bytes =
                baiBytes(new BinningIndex.Builder(14, 5).reportingRecordCounts().build(0), true);
        try (FileBackedBinningIndex index = FileBackedBinningIndex.open(write(bytes, ".bai"), false)) {
            Assert.assertEquals(index.getReferenceCount(), 0);
            Assert.assertEquals(index.getNoCoordinateCount().orElseThrow(), 0);
            Assert.assertTrue(index.getSpanOverlapping(0, 1, 100).isEmpty());
        }
    }

    @Test(expectedExceptions = SAMFormatException.class)
    public void testNeitherBaiNorCsiIsRejected() throws IOException {
        FileBackedBinningIndex.open(write(new byte[] {'T', 'B', 'I', 1, 0, 0, 0, 0}, ".tbi"), false);
    }
}
