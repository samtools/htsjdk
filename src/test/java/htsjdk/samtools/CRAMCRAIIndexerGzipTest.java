package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAIIndex;
import htsjdk.samtools.util.GzipTestStreams;
import java.io.IOException;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CRAMCRAIIndexerGzipTest extends HtsjdkTest {

    // CRAI columns: sequence id, alignment start, alignment span, container offset, slice offset, slice size
    private static final String ENTRY_1 = "0\t1\t100\t1000\t50\t500\n";
    private static final String ENTRY_2 = "0\t101\t100\t2000\t50\t500\n";
    private static final String ENTRY_3 = "1\t1\t100\t3000\t50\t500\n";

    @Test
    public void readsEntriesFromEveryMemberOfMultiMemberGzip() throws IOException {
        final byte[] gzip = GzipTestStreams.multiMemberGzip(ENTRY_1, ENTRY_2, ENTRY_3);
        final CRAIIndex index = CRAMCRAIIndexer.readIndex(GzipTestStreams.asSlowPipe(gzip));
        Assert.assertEquals(index.getCRAIEntries().size(), 3);
    }

    @Test
    public void readsEntriesFromEveryBlockOfBgzf() throws IOException {
        final byte[] bgzf = GzipTestStreams.multiBlockBgzf(ENTRY_1, ENTRY_2, ENTRY_3);
        final CRAIIndex index = CRAMCRAIIndexer.readIndex(GzipTestStreams.asSlowPipe(bgzf));
        Assert.assertEquals(index.getCRAIEntries().size(), 3);
    }
}
