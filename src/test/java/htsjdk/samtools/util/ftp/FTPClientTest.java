package htsjdk.samtools.util.ftp;

import htsjdk.HtsjdkTest;
import htsjdk.testutil.ftp.LocalFtpServer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** Drives {@link FTPClient} against an in-memory server that, like strict servers, accepts only CRLF commands. */
public class FTPClientTest extends HtsjdkTest {
    private static final String FILE = "/pub/test.txt";
    private static final byte[] CONTENTS = "abcdefghijklmnopqrstuvwxyz\n".getBytes(StandardCharsets.US_ASCII);

    private LocalFtpServer server;
    private FTPClient client;

    @BeforeMethod
    public void setUp() throws IOException {
        server = new LocalFtpServer().addFile(FILE, CONTENTS);
        client = new FTPClient();
    }

    @AfterMethod
    public void tearDown() {
        client.disconnect();
        server.close();
    }

    /** Connects on the server's port and logs in anonymously in binary mode. */
    private void login() throws IOException {
        Assert.assertTrue(client.connect("127.0.0.1", server.getPort()).isPositiveCompletion(), "connect");
        Assert.assertTrue(client.login("anonymous", "test@example.com").isPositiveCompletion(), "login");
        Assert.assertTrue(client.binary().isPositiveCompletion(), "binary");
    }

    private static String readAll(final InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.US_ASCII);
    }

    @Test
    public void connectGreetsOnTheGivenPort() throws IOException {
        Assert.assertEquals(client.connect("127.0.0.1", server.getPort()).getCode(), 220);
    }

    @Test
    public void sizeGivesTheLengthOfAFile() throws IOException {
        login();
        final FTPReply reply = client.size(FILE);
        Assert.assertEquals(reply.getCode(), 213);
        Assert.assertEquals(Integer.parseInt(reply.getReplyString()), CONTENTS.length);
    }

    @Test
    public void sizeOfAMissingFileIsRefused() throws IOException {
        login();
        Assert.assertEquals(client.size("/pub/missing.txt").getCode(), 550);
    }

    @Test
    public void retrAfterPasvSendsTheWholeFile() throws IOException {
        login();
        Assert.assertTrue(client.pasv().isPositiveCompletion(), "pasv");
        final FTPReply reply = client.retr(FILE);
        Assert.assertTrue(reply.isPositivePreliminary(), reply.getCode() + " " + reply.getReplyString());
        Assert.assertEquals(readAll(client.getDataStream()), new String(CONTENTS, StandardCharsets.US_ASCII));
        client.closeDataStream();
        Assert.assertEquals(client.getReply().getCode(), 226);
    }

    @Test
    public void retrOfAMissingFileIsRefused() throws IOException {
        login();
        Assert.assertTrue(client.pasv().isPositiveCompletion(), "pasv");
        final FTPReply reply = client.retr("/pub/missing.txt");
        Assert.assertFalse(reply.isPositivePreliminary());
        Assert.assertEquals(reply.getCode(), 550);
    }

    @Test
    public void aRestPositionStartsEachTransferAtThatOffset() throws IOException {
        login();
        for (final int offset : new int[] {5, 2, 15}) {
            Assert.assertTrue(client.pasv().isPositiveCompletion(), "pasv");
            client.setRestPosition(offset);
            Assert.assertTrue(client.retr(FILE).isPositivePreliminary(), "retr from " + offset);
            Assert.assertEquals(
                    readAll(client.getDataStream()),
                    new String(CONTENTS, offset, CONTENTS.length - offset, StandardCharsets.US_ASCII));
            client.closeDataStream();
            Assert.assertEquals(client.getReply().getCode(), 226, "end of the transfer from " + offset);
        }
    }
}
