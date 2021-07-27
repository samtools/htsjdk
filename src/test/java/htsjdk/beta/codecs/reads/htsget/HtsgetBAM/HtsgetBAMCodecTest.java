package htsjdk.beta.codecs.reads.htsget.HtsgetBAM;

import htsjdk.HtsjdkTest;
import htsjdk.beta.codecs.reads.htsget.HtsgetBAMCodec;
import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.beta.plugin.interval.HtsIntervalUtils;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.beta.plugin.registry.HtsDefaultRegistry;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
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

// NOTE: running these tests require a local htsget server

public class HtsgetBAMCodecTest extends HtsjdkTest {
    private final static IOPath htsgetBAM = new HtsPath(
            HtsgetBAMFileReaderTest.HTSGET_ENDPOINT +
                    HtsgetBAMFileReaderTest.LOCAL_PREFIX + "index_test.bam");
    private final static File bamFile = new File(
            "src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam");

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void testThrowOnGetEncoder() {
        // htsget has no write support, so attempts to get an encoder throw
        HtsDefaultRegistry.getReadsResolver().getReadsEncoder(htsgetBAM, new ReadsEncoderOptions());
    }

    @Test
    public void testGetHeader() {
        try (final ReadsDecoder htsgetDecoder =
                HtsDefaultRegistry.getReadsResolver().getReadsDecoder(htsgetBAM, new ReadsDecoderOptions())) {
            final SAMFileHeader expectedHeader = SamReaderFactory.makeDefault().open(bamFile).getFileHeader();
            final SAMFileHeader actualHeader = htsgetDecoder.getHeader();
            Assert.assertEquals(actualHeader, expectedHeader);
        }
    }

    @Test
    public void testIteration() {
        try (final ReadsDecoder htsgetDecoder =
                     HtsDefaultRegistry.getReadsResolver().getReadsDecoder(htsgetBAM, new ReadsDecoderOptions())) {
            Assert.assertEquals(htsgetDecoder.getFileFormat(), ReadsFormats.HTSGET_BAM);
            Assert.assertEquals(htsgetDecoder.getVersion(), HtsgetBAMCodec.HTSGET_VERSION);
            Assert.assertTrue(htsgetDecoder.getDisplayName().contains(htsgetBAM.getURIString()));

            int count = 0;
            for (final SAMRecord samRecord: htsgetDecoder) {
                count++;
            }
            Assert.assertEquals(count, 10000);
        }
    }

    @Test
    public void testQueryInterval() throws IOException {
        try (final ReadsDecoder htsgetDecoder =
                     HtsDefaultRegistry.getReadsResolver().getReadsDecoder(htsgetBAM, new ReadsDecoderOptions())) {
            Assert.assertTrue(htsgetDecoder.isQueryable());
            Assert.assertFalse(htsgetDecoder.hasIndex());

            final QueryInterval[] query = new QueryInterval[]{new QueryInterval(0, 1519, 1520), new QueryInterval(1, 470535, 470536)};
            try (final SamReader fileReader = SamReaderFactory.makeDefault().open(bamFile);
                 final CloseableIterator<SAMRecord> csiIterator = fileReader.query(query, false)) {
                final Iterator<SAMRecord> htsgetIterator = htsgetDecoder.query(
                        HtsIntervalUtils.fromQueryIntervalArray(query, htsgetDecoder.getHeader().getSequenceDictionary()),
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

    @Test
    public void testQueryUnmapped() {
        try (final ReadsDecoder htsgetDecoder =
                     HtsDefaultRegistry.getReadsResolver().getReadsDecoder(htsgetBAM, new ReadsDecoderOptions())) {
            Assert.assertTrue(htsgetDecoder.isQueryable());
            Assert.assertFalse(htsgetDecoder.hasIndex());
            final CloseableIterator<SAMRecord> unmappedIt = htsgetDecoder.queryUnmapped();

            int unmappedCount = 0;
            while (unmappedIt.hasNext()) {
                unmappedIt.next();
                unmappedCount++;
            }
            Assert.assertEquals(unmappedCount, 279);
        }
    }

    @Test(expectedExceptions = HtsjdkPluginException.class)
    public void testRejectQueryMate() {
        SAMRecord samRec = null;
        try (final ReadsDecoder htsgetDecoder =
                     HtsDefaultRegistry.getReadsResolver().getReadsDecoder(htsgetBAM, new ReadsDecoderOptions())) {
            Assert.assertTrue(htsgetDecoder.isQueryable());
            Assert.assertFalse(htsgetDecoder.hasIndex());
            for (final SAMRecord samRecord : htsgetDecoder) {
                samRec = samRecord;
                break;
            }
        }

        try (final ReadsDecoder htsgetDecoder =
                     HtsDefaultRegistry.getReadsResolver().getReadsDecoder(htsgetBAM, new ReadsDecoderOptions())) {
            htsgetDecoder.queryMate(samRec);
        }
    }
}
