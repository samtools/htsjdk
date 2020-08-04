package htsjdk.beta.codecs.reads.htsget.HtsgetBAM;

import htsjdk.HtsjdkTest;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.registry.HtsReadsCodecs;
import htsjdk.beta.plugin.interval.HtsInterval;
import htsjdk.beta.plugin.interval.HtsQueryRule;
import htsjdk.beta.plugin.reads.ReadsDecoder;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.samtools.HtsgetBAMFileReaderTest;
import htsjdk.samtools.QueryInterval;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.util.CloseableIterator;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

//TODO: these tests were lifted from HtsgetBAMFileReaderTest

// NOTE: running these tests require a local htsget server

public class HtsgetBAMCodecTest extends HtsjdkTest {
    private final static IOPath htsgetBAM = new HtsPath(HtsgetBAMFileReaderTest.HTSGET_ENDPOINT + HtsgetBAMFileReaderTest.LOCAL_PREFIX + "index_test.bam");

    private final static File bamFile = new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam");

    @Test
    public void testGetHeader() {
        final ReadsDecoder htsgetDecoder = HtsReadsCodecs.getReadsDecoder(htsgetBAM, new ReadsDecoderOptions());
        final SAMFileHeader expectedHeader = SamReaderFactory.makeDefault().open(bamFile).getFileHeader();
        final SAMFileHeader actualHeader = htsgetDecoder.getHeader();
        Assert.assertEquals(actualHeader, expectedHeader);
    }

    @Test
    public void testQueryInterval() throws IOException {
        final ReadsDecoder htsgetDecoder = HtsReadsCodecs.getReadsDecoder(htsgetBAM, new ReadsDecoderOptions());
        final QueryInterval[] query = new QueryInterval[]{new QueryInterval(0, 1519, 1520), new QueryInterval(1, 470535, 470536)};
        try (final SamReader fileReader = SamReaderFactory.makeDefault().open(bamFile);
             final CloseableIterator<SAMRecord> csiIterator = fileReader.query(query, false)) {
            final Iterator<SAMRecord> htsgetIterator = htsgetDecoder.query(
                    HtsInterval.fromQueryIntervalArray(query, htsgetDecoder.getHeader().getSequenceDictionary()),
                    HtsQueryRule.OVERLAPPING);
            Assert.assertTrue(htsgetIterator.hasNext());
            Assert.assertTrue(csiIterator.hasNext());
            SAMRecord r1 = htsgetIterator.next();
            SAMRecord r2 = csiIterator.next();
            Assert.assertEquals(r1.getReadName(), "3968040");
            Assert.assertEquals(r2.getReadName(), "3968040");

            r1 = htsgetIterator.next();
            r2 = csiIterator.next();
            Assert.assertEquals(r1.getReadName(), "140419");
            Assert.assertEquals(r2.getReadName(), "140419");
        }
    }

}
