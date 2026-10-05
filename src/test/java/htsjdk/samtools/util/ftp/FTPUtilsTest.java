package htsjdk.samtools.util.ftp;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.testutil.ftp.LocalFtpServer;
import java.io.IOException;
import java.net.URL;
import org.testng.Assert;
import org.testng.annotations.Test;

public class FTPUtilsTest extends HtsjdkTest {
    private static final byte[] CONTENTS = new byte[1234];

    @Test
    public void resourceAvailableIsTrueForAFileOnTheServer() throws IOException {
        try (final LocalFtpServer server = new LocalFtpServer().addFile("/pub/test.txt", CONTENTS)) {
            Assert.assertTrue(FTPUtils.resourceAvailable(server.url("/pub/test.txt")));
        }
    }

    @Test
    public void resourceAvailableIsFalseForAMissingFile() throws IOException {
        try (final LocalFtpServer server = new LocalFtpServer().addFile("/pub/test.txt", CONTENTS)) {
            Assert.assertFalse(FTPUtils.resourceAvailable(server.url("/pub/missing.txt")));
        }
    }

    @Test
    public void getContentLengthAsksTheServerOnTheUrlsPort() throws IOException {
        try (final LocalFtpServer server = new LocalFtpServer().addFile("/pub/test.txt", CONTENTS)) {
            Assert.assertEquals(FTPUtils.getContentLength(server.url("/pub/test.txt")), CONTENTS.length);
        }
    }

    @Test
    public void getContentLengthSendsAPercentEncodedNameDecoded() throws IOException {
        try (final LocalFtpServer server = new LocalFtpServer().addFile("/pub/reads #1.bam", CONTENTS)) {
            final URL url = server.url("/pub/reads #1.bam");
            Assert.assertTrue(url.toExternalForm().endsWith("/pub/reads%20%231.bam"), url.toExternalForm());
            // the server knows the file only by its decoded name
            Assert.assertEquals(FTPUtils.getContentLength(url), CONTENTS.length);
        }
    }

    @Test
    public void connectLogsInWithTheUrlsUserInfo() throws IOException {
        try (final LocalFtpServer server = new LocalFtpServer()) {
            final String hostAndPort = "@127.0.0.1:" + server.getPort() + "/pub/test.txt";
            FTPUtils.connect(
                            new URL("ftp://" + LocalFtpServer.USER + ":" + LocalFtpServer.PASSWORD + hostAndPort), null)
                    .disconnect();
            Assert.expectThrows(
                    RuntimeIOException.class,
                    () -> FTPUtils.connect(new URL("ftp://" + LocalFtpServer.USER + ":wrong" + hostAndPort), null));
        }
    }

    @Test
    public void getDecodedPathFallsBackToTheRawPathOfAUrlThatIsNotAUri() throws IOException {
        Assert.assertEquals(FTPUtils.getDecodedPath(new URL("ftp://host/pub/a%20b.txt")), "/pub/a b.txt");
        Assert.assertEquals(FTPUtils.getDecodedPath(new URL("ftp://host/pub/a b.txt")), "/pub/a b.txt");
    }
}
