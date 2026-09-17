package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.GzipTestStreams;
import java.io.IOException;
import java.io.InputStream;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SamReaderFactoryGzipTest extends HtsjdkTest {

    private static final String HEADER = "@HD\tVN:1.6\tSO:unsorted\n@SQ\tSN:chr1\tLN:1000\n";
    private static final String READ_1 = "read1\t4\t*\t0\t0\t*\t*\t0\t0\tACGT\tIIII\n";
    private static final String READ_2 = "read2\t4\t*\t0\t0\t*\t*\t0\t0\tTTTT\tIIII\n";
    private static final String READ_3 = "read3\t4\t*\t0\t0\t*\t*\t0\t0\tGGGG\tIIII\n";

    private static int countRecords(final InputStream in) throws IOException {
        int count = 0;
        try (SamReader reader = SamReaderFactory.makeDefault().open(SamInputResource.of(in))) {
            for (final SAMRecord ignored : reader) {
                count++;
            }
        }
        return count;
    }

    @Test
    public void readsRecordsFromEveryMemberOfMultiMemberGzippedSam() throws IOException {
        final byte[] gzip = GzipTestStreams.multiMemberGzip(HEADER, READ_1, READ_2, READ_3);
        Assert.assertEquals(countRecords(GzipTestStreams.asSlowPipe(gzip)), 3);
    }

    @Test
    public void readsRecordsFromEveryBlockOfBgzfSam() throws IOException {
        final byte[] bgzf = GzipTestStreams.multiBlockBgzf(HEADER, READ_1, READ_2, READ_3);
        Assert.assertEquals(countRecords(GzipTestStreams.asSlowPipe(bgzf)), 3);
    }
}
