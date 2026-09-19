package htsjdk.tribble.readers;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.TestUtil;
import htsjdk.tribble.TestUtils;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.tribble.index.tabix.TabixFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
}
