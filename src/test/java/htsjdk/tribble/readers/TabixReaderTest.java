package htsjdk.tribble.readers;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.seekablestream.ISeekableStreamFactory;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.seekablestream.SeekableStreamFactory;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.TestUtil;
import htsjdk.testutil.ftp.LocalFtpServer;
import htsjdk.tribble.TestUtils;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.tribble.index.tabix.TabixFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Created by IntelliJ IDEA.
 * User: jrobinso
 * Date: Jul 6, 2010
 * Time: 8:57:40 PM
 * To change this template use File | Settings | File Templates.
 */
public class TabixReaderTest extends HtsjdkTest {

    static String tabixFile = TestUtils.DATA_DIR + "tabix/trioDup.vcf.gz";
    static TabixReader tabixReader;
    static List<String> sequenceNames;

    @BeforeClass
    public void setup() throws IOException {
        tabixReader = new TabixReader(tabixFile);
        sequenceNames = new ArrayList<String>(tabixReader.getChromosomes());
    }

    @AfterClass
    public void teardown() throws Exception {
        tabixReader.close();
    }

    @Test
    public void testSequenceNames() {
        String[] expectedSeqNames = new String[24];
        for (int i = 1; i < 24; i++) {
            expectedSeqNames[i - 1] = String.valueOf(i);
        }
        expectedSeqNames[22] = "X";
        expectedSeqNames[23] = "Y";
        Assert.assertEquals(expectedSeqNames.length, sequenceNames.size());

        for (String s : expectedSeqNames) {
            Assert.assertTrue(sequenceNames.contains(s));
        }
    }

    @Test
    public void testSequenceSet() {
        Set<String> chroms = tabixReader.getChromosomes();
        Assert.assertFalse(chroms.isEmpty());
        Assert.assertTrue(chroms.contains("1"));
        Assert.assertFalse(chroms.contains("MT"));
    }

    @Test
    public void testIterators() throws IOException {
        TabixReader.Iterator iter = tabixReader.query("1", 1, 400);
        Assert.assertNotNull(iter);
        Assert.assertNotNull(iter.next());
        Assert.assertNull(iter.next());

        iter = tabixReader.query("UN", 1, 100);
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());

        iter = tabixReader.query("UN:1-100");
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());

        iter = tabixReader.query("1:10-1");
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());

        iter = tabixReader.query("chr2:0-1");
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());

        iter = tabixReader.query(999999, 9, 9);
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());

        iter = tabixReader.query("1", Integer.MAX_VALUE - 1, Integer.MAX_VALUE);
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());

        iter = tabixReader.query("1", -1, Integer.MAX_VALUE);
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());

        iter = tabixReader.query("1", Integer.MAX_VALUE, -1);
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());

        iter = tabixReader.query("1", Integer.MAX_VALUE, 0);
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());

        iter = tabixReader.query("1:100-1000");
        Assert.assertNotNull(iter);
        Assert.assertNotNull(iter.next());
        Assert.assertNull(iter.next());

        final int pos_snp_in_vcf_chr1 = 327;

        iter = tabixReader.query("1:" + pos_snp_in_vcf_chr1 + "-" + pos_snp_in_vcf_chr1);
        Assert.assertNotNull(iter);
        Assert.assertNotNull(iter.next());
        Assert.assertNull(iter.next());

        iter = tabixReader.query("1:" + pos_snp_in_vcf_chr1);
        Assert.assertNotNull(iter);
        Assert.assertNotNull(iter.next());
        Assert.assertNull(iter.next());

        iter = tabixReader.query("1", pos_snp_in_vcf_chr1, pos_snp_in_vcf_chr1);
        Assert.assertNotNull(iter);
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());

        iter = tabixReader.query("1", pos_snp_in_vcf_chr1 - 1, pos_snp_in_vcf_chr1 - 1);
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());

        iter = tabixReader.query("1", pos_snp_in_vcf_chr1 + 1, pos_snp_in_vcf_chr1 + 1);
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());
    }

    /**
     * Test reading a local tabix file
     *
     * @throws java.io.IOException
     */
    @Test
    public void testLocalQuery() throws IOException {

        TabixIteratorLineReader lineReader =
                new TabixIteratorLineReader(tabixReader.query(tabixReader.chr2tid("4"), 320, 330));

        int nRecords = 0;
        String nextLine;
        while ((nextLine = lineReader.readLine()) != null) {
            Assert.assertTrue(nextLine.startsWith("4"));
            nRecords++;
        }
        Assert.assertTrue(nRecords > 0);
    }

    /**
     * Test reading a tabix file over http
     *
     * @throws java.io.IOException
     */
    @Test
    public void testRemoteQuery() throws IOException {
        String tabixFile = TestUtil.BASE_URL_FOR_HTTP_TESTS + "igvdata/tabix/trioDup.vcf.gz";

        try (TabixReader tabixReader = new TabixReader(tabixFile)) {
            TabixIteratorLineReader lineReader =
                    new TabixIteratorLineReader(tabixReader.query(tabixReader.chr2tid("4"), 320, 330));

            int nRecords = 0;
            String nextLine;
            while ((nextLine = lineReader.readLine()) != null) {
                Assert.assertTrue(nextLine.startsWith("4"));
                nRecords++;
            }
            Assert.assertTrue(nRecords > 0);
        }
    }

    @Test
    public void aQueryOverFtpReturnsWhatTheSameQueryOnTheLocalFileReturns() throws IOException {
        try (LocalFtpServer server = new LocalFtpServer()
                        .addFile("/igv/trioDup.vcf.gz", Files.readAllBytes(Paths.get(tabixFile)))
                        .addFile("/igv/trioDup.vcf.gz.tbi", Files.readAllBytes(Paths.get(tabixFile + ".tbi")));
                TabixReader ftpReader =
                        new TabixReader(server.url("/igv/trioDup.vcf.gz").toString())) {
            Assert.assertEquals(linesIn(ftpReader, "4", 320, 330), linesIn(tabixReader, "4", 320, 330));
            Assert.assertFalse(linesIn(ftpReader, "4", 320, 330).isEmpty());
        }
    }

    private static List<String> linesIn(final TabixReader reader, final String contig, final int start, final int end)
            throws IOException {
        final TabixReader.Iterator iterator = reader.query(reader.chr2tid(contig), start, end);
        final List<String> lines = new ArrayList<>();
        String line;
        while ((line = iterator.next()) != null) {
            lines.add(line);
        }
        return lines;
    }

    /**
     * Test TabixReader.readLine
     *
     * @throws java.io.IOException
     */
    @Test
    public void testTabixReaderReadLine() throws IOException {
        try (TabixReader tabixReader = new TabixReader(tabixFile)) {
            Assert.assertNotNull(tabixReader.readLine());
        }
    }

    // Line reading

    private static InputStream utf8(final String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void readLineDecodesUtf8() throws IOException {
        final String line = "chr1\t100\tcafé → λ 日本 " + new String(Character.toChars(0x1F600));
        final InputStream in = utf8(line + "\nnext\n");
        Assert.assertEquals(TabixReader.readLine(in), line);
        Assert.assertEquals(TabixReader.readLine(in), "next");
        Assert.assertNull(TabixReader.readLine(in));
    }

    @Test
    public void readLineReturnsALastLineThatHasNoTrailingNewline() throws IOException {
        final InputStream in = utf8("first\nlast");
        Assert.assertEquals(TabixReader.readLine(in), "first");
        Assert.assertEquals(TabixReader.readLine(in), "last");
        Assert.assertNull(TabixReader.readLine(in));
    }

    @Test
    public void readLineReadsALineLongerThanItsBufferWhole() throws IOException {
        final String line = "x".repeat(5000) + "→";
        Assert.assertEquals(TabixReader.readLine(utf8(line + "\n")), line);
    }

    @Test
    public void aQueryReturnsTheLastRecordWhenTheFileHasNoTrailingNewline() throws IOException {
        final String first = "chr1\t100\t200\tcafé";
        final String last = "chr1\t300\t400\tλ→日本";
        final Path bed = Files.createTempFile("noTrailingNewline.", ".bed.gz");
        IOUtil.deleteOnExit(bed);
        try (final BlockCompressedOutputStream out = new BlockCompressedOutputStream(bed)) {
            out.write((first + "\n" + last).getBytes(StandardCharsets.UTF_8));
        }
        final Path index = bed.resolveSibling(bed.getFileName() + FileExtensions.TABIX_INDEX);
        IOUtil.deleteOnExit(index);
        IndexFactory.createTabixIndex(bed, new BEDCodec(), TabixFormat.BED, null)
                .write(index);

        try (final TabixReader reader = new TabixReader(bed.toString())) {
            final TabixReader.Iterator records = reader.query("chr1:1-1000");
            Assert.assertEquals(records.next(), first);
            Assert.assertEquals(records.next(), last);
            Assert.assertNull(records.next());
        }
    }

    // Custom stream factories

    /** Serves {@code myfs://<path>}, a scheme no NIO provider handles, from the local file at {@code <path>}. */
    private static final class MyfsStreamFactory implements ISeekableStreamFactory {
        private final ISeekableStreamFactory delegate;

        MyfsStreamFactory(final ISeekableStreamFactory delegate) {
            this.delegate = delegate;
        }

        private static String local(final String path) {
            return path.startsWith("myfs://") ? path.substring("myfs://".length()) : path;
        }

        @Override
        public SeekableStream getStreamFor(final URL url) throws IOException {
            return delegate.getStreamFor(url);
        }

        @Override
        public SeekableStream getStreamFor(final String path) throws IOException {
            return delegate.getStreamFor(local(path));
        }

        @Override
        public SeekableStream getStreamFor(
                final String path, final Function<SeekableByteChannel, SeekableByteChannel> wrapper)
                throws IOException {
            return delegate.getStreamFor(local(path), wrapper);
        }

        @Override
        public SeekableStream getBufferedStream(final SeekableStream stream) {
            return delegate.getBufferedStream(stream);
        }

        @Override
        public SeekableStream getBufferedStream(final SeekableStream stream, final int bufferSize) {
            return delegate.getBufferedStream(stream, bufferSize);
        }
    }

    @Test
    public void aFileServedOnlyByACustomStreamFactoryOpensWithoutAnIndexPath() throws IOException {
        final String record = "chr1\t100\t200\tfeature";
        final Path dir = Files.createTempDirectory("tabixCustomFactory.");
        final ISeekableStreamFactory original = SeekableStreamFactory.getInstance();
        try {
            final Path bed = dir.resolve("features.bed.gz");
            try (final BlockCompressedOutputStream out = new BlockCompressedOutputStream(bed)) {
                out.write((record + "\n").getBytes(StandardCharsets.UTF_8));
            }
            IndexFactory.createTabixIndex(bed, new BEDCodec(), TabixFormat.BED, null)
                    .write(dir.resolve("features.bed.gz" + FileExtensions.TABIX_INDEX));

            SeekableStreamFactory.setInstance(new MyfsStreamFactory(original));
            try (final TabixReader reader = new TabixReader("myfs://" + bed.toAbsolutePath())) {
                final TabixReader.Iterator records = reader.query("chr1:1-1000");
                Assert.assertEquals(records.next(), record);
                Assert.assertNull(records.next());
            }
        } finally {
            SeekableStreamFactory.setInstance(original);
            IOUtil.recursiveDelete(dir);
        }
    }

    // CRLF stripping

    @Test
    public void staticReadLineStripsTrailingCr() throws IOException {
        final InputStream in = utf8("a\r\nb\r");
        Assert.assertEquals(TabixReader.readLine(in), "a");
        Assert.assertEquals(TabixReader.readLine(in), "b");
        Assert.assertNull(TabixReader.readLine(in));
    }

    @Test
    public void aQueryOnACrlfFileReturnsCleanLines() throws IOException {
        final String header = "##fileformat=VCFv4.3\r\n" + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\r\n";
        final String record = "chr1\t100\t.\tA\tG\t30\tPASS\t.\r\n";

        final Path vcf = Files.createTempFile("crlf.", ".vcf.gz");
        IOUtil.deleteOnExit(vcf);
        try (final BlockCompressedOutputStream out = new BlockCompressedOutputStream(vcf)) {
            out.write((header + record).getBytes(StandardCharsets.UTF_8));
        }

        final Path index = vcf.resolveSibling(vcf.getFileName() + FileExtensions.TABIX_INDEX);
        IOUtil.deleteOnExit(index);
        IndexFactory.createTabixIndex(vcf, new htsjdk.variant.vcf.VCFCodec(), TabixFormat.VCF, null)
                .write(index);

        try (final TabixReader reader = new TabixReader(vcf.toString())) {
            final TabixReader.Iterator records = reader.query("chr1:1-1000");
            final String line = records.next();
            Assert.assertNotNull(line);
            Assert.assertFalse(line.contains("\r"), "queried line should not contain \\r");
            Assert.assertNull(records.next());
        }
    }

    // Region strings

    private static final int MAX = Integer.MAX_VALUE;
    private static final String HLA = "HLA-A*01:01:01:01";

    private interface ReaderAction {
        void accept(TabixReader reader) throws IOException;
    }

    /** Writes a tabix-indexed BED with the given contigs, one 100-200 record each, and hands it to the action. */
    private static void withRegionReader(final ReaderAction action) throws IOException {
        final Path dir = Files.createTempDirectory("tabixRegions.");
        try {
            final Path bed = dir.resolve("regions.bed.gz");
            try (final BlockCompressedOutputStream out = new BlockCompressedOutputStream(bed)) {
                final StringBuilder lines = new StringBuilder();
                for (final String contig : new String[] {"chr1", HLA, "chrUn-1", "chrA", "chrA:1-10"}) {
                    lines.append(contig)
                            .append("\t100\t200\tin-")
                            .append(contig)
                            .append('\n');
                }
                out.write(lines.toString().getBytes(StandardCharsets.UTF_8));
            }
            final Path index = dir.resolve("regions.bed.gz" + FileExtensions.TABIX_INDEX);
            IndexFactory.createTabixIndex(bed, new BEDCodec(), TabixFormat.BED, null)
                    .write(index);
            try (final TabixReader reader = new TabixReader(bed.toString())) {
                action.accept(reader);
            }
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    private static void assertRegion(final String region, final String contig, final int begin, final int end)
            throws IOException {
        withRegionReader(reader -> {
            final int[] parsed = reader.parseReg(region);
            Assert.assertEquals(parsed[0], reader.chr2tid(contig));
            Assert.assertEquals(parsed[1], begin);
            Assert.assertEquals(parsed[2], end);
        });
    }

    private static void assertRegionThrows(final String region, final String messagePart) throws IOException {
        withRegionReader(reader -> {
            try {
                reader.parseReg(region);
                Assert.fail("Expected IllegalArgumentException for " + region);
            } catch (final IllegalArgumentException e) {
                Assert.assertTrue(e.getMessage().contains(messagePart), e.getMessage());
            }
        });
    }

    @Test
    public void aWholeContigParsesAsTheWholeContig() throws IOException {
        assertRegion("chr1", "chr1", 0, MAX);
    }

    @Test
    public void aStartOnlyRangeRunsToTheEnd() throws IOException {
        assertRegion("chr1:100", "chr1", 99, MAX);
    }

    @Test
    public void aStartAndEndRangeIsZeroBasedHalfOpen() throws IOException {
        assertRegion("chr1:100-200", "chr1", 99, 200);
    }

    @Test
    public void aContigNameWithColonsIsFoundWhole() throws IOException {
        assertRegion(HLA, HLA, 0, MAX);
    }

    @Test
    public void aRangeOnAContigNameWithColonsSplitsAtTheLastColon() throws IOException {
        assertRegion(HLA + ":100-200", HLA, 99, 200);
    }

    @Test
    public void aContigNameWithAHyphenIsFoundWhole() throws IOException {
        assertRegion("chrUn-1", "chrUn-1", 0, MAX);
    }

    @Test
    public void aRangeOnAContigNameWithAHyphen() throws IOException {
        assertRegion("chrUn-1:5-10", "chrUn-1", 4, 10);
    }

    @Test
    public void anAmbiguousRegionIsAnError() throws IOException {
        assertRegionThrows("chrA:1-10", "Use {chrA:1-10} or {chrA}:1-10 instead");
    }

    @Test
    public void bracesChooseTheWholeName() throws IOException {
        assertRegion("{chrA:1-10}", "chrA:1-10", 0, MAX);
    }

    @Test
    public void bracesChooseTheShorterName() throws IOException {
        assertRegion("{chrA}:1-10", "chrA", 0, 10);
    }

    @Test
    public void mismatchedBracesAreAnError() throws IOException {
        assertRegionThrows("{chrA:1-10", "Mismatching braces");
    }

    @Test
    public void textAfterTheClosingBraceOtherThanAColonIsAnError() throws IOException {
        assertRegionThrows("{chrA}1-10", "closing brace");
    }

    @Test
    public void anEndOnlyRangeStartsAtOne() throws IOException {
        assertRegion("chr1:-100", "chr1", 0, 100);
    }

    @Test
    public void aTrailingHyphenRunsToTheEnd() throws IOException {
        assertRegion("chr1:100-", "chr1", 99, MAX);
    }

    @Test
    public void thousandsSeparatorsAreAccepted() throws IOException {
        assertRegion("chr1:1,000-2,000,000", "chr1", 999, 2_000_000);
    }

    @Test
    public void aZeroStartReadsFromTheStart() throws IOException {
        assertRegion("chr1:0-50", "chr1", 0, 50);
    }

    @Test
    public void aZeroEndRunsToTheEnd() throws IOException {
        assertRegion("chr1:100-0", "chr1", 99, MAX);
    }

    @Test
    public void aStartAfterTheEndGivesAnEmptyRange() throws IOException {
        assertRegion("chr1:10-1", "chr1", 9, 1);
        withRegionReader(reader -> Assert.assertNull(reader.query("chr1:10-1").next()));
    }

    @Test
    public void trailingTextIsAnError() throws IOException {
        assertRegionThrows("chr1:100-200x", "chr1:100-200x");
    }

    @Test
    public void aNonNumericPositionIsAnError() throws IOException {
        assertRegionThrows("chr1:abc-200", "chr1:abc-200");
    }

    @Test
    public void aPositionBeyondIntRangeIsAnError() throws IOException {
        assertRegionThrows("chr1:1-99999999999", "too large");
    }

    @Test
    public void anUnknownContigHasIndexMinusOne() throws IOException {
        withRegionReader(reader -> {
            Assert.assertEquals(reader.parseReg("chrNope:0-1"), new int[] {-1, 0, MAX});
            Assert.assertEquals(reader.parseReg("chrNope"), new int[] {-1, 0, MAX});
        });
    }

    @Test
    public void aQueryOnAContigNameWithColonsReturnsItsRecords() throws IOException {
        withRegionReader(reader -> {
            final TabixReader.Iterator records = reader.query(HLA + ":150");
            Assert.assertEquals(records.next(), HLA + "\t100\t200\tin-" + HLA);
            Assert.assertNull(records.next());
        });
    }
}
