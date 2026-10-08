package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import htsjdk.testutil.http.LocalHttpServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

public class HttpUtilsTest extends HtsjdkTest {
    private static final Path BAM = Path.of("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam");

    @Test
    public void getHeaderFieldReturnsTheContentLengthOfAServedFile() throws IOException {
        try (LocalHttpServer server = new LocalHttpServer().addFile("/data/index_test.bam", BAM)) {
            Assert.assertEquals(
                    HttpUtils.getHeaderField(server.url("/data/index_test.bam"), "Content-Length"),
                    Long.toString(Files.size(BAM)));
        }
    }

    @Test
    public void getETagReturnsTheETagOfAServedFile() throws IOException {
        try (LocalHttpServer server = new LocalHttpServer().addFile("/data/index_test.bam", BAM)) {
            Assert.assertNotNull(HttpUtils.getETag(server.url("/data/index_test.bam")));
        }
    }
}
