package htsjdk.samtools.fastq;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.GzipTestStreams;
import htsjdk.samtools.util.IOUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

public class FastqReaderGzipTest extends HtsjdkTest {

    private static final String RECORD_1 = "@read1\nACGT\n+\nIIII\n";
    private static final String RECORD_2 = "@read2\nTTTT\n+\nIIII\n";
    private static final String RECORD_3 = "@read3\nGGGG\n+\nIIII\n";

    private static int countRecords(final byte[] compressedFastq) throws IOException {
        final Path fastq = Files.createTempFile("FastqReaderGzipTest.", ".fastq.gz");
        IOUtil.deleteOnExit(fastq);
        Files.write(fastq, compressedFastq);

        int count = 0;
        try (FastqReader reader = new FastqReader(fastq)) {
            for (final FastqRecord ignored : reader) {
                count++;
            }
        }
        return count;
    }

    @Test
    public void readsRecordsFromEveryMemberOfMultiMemberGzip() throws IOException {
        Assert.assertEquals(countRecords(GzipTestStreams.multiMemberGzip(RECORD_1, RECORD_2, RECORD_3)), 3);
    }

    @Test
    public void readsRecordsFromEveryBlockOfBgzf() throws IOException {
        Assert.assertEquals(countRecords(GzipTestStreams.multiBlockBgzf(RECORD_1, RECORD_2, RECORD_3)), 3);
    }
}
