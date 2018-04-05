package htsjdk.tribble.util.ftp;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.ftp.FTPClient;
import htsjdk.samtools.util.ftp.FTPReply;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;

/**
* @author Jim Robinson
* @since 10/3/11
*/
public class FTPClientTest extends HtsjdkTest {

    final static String host = "ftp.broadinstitute.org";
    final static String file = "/pub/igv/TEST/test.txt";
    final static int fileSize = 27;
    final static byte[] expectedBytes = "abcdefghijklmnopqrstuvwxyz\n".getBytes();

    FTPClient client;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws IOException {
        System.out.println("SETUP-in");

        client = new FTPClient();
        System.out.println("SETUP-new");
        FTPReply reply = client.connect(host);
        System.out.println("SETUP-conenct");
        Assert.assertTrue(reply.isSuccess(), "connect");
        System.out.println("SETUP-success");
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        System.out.println("Disconnecting");
        client.disconnect();
    }

    @Test(groups = {"yossis_test"})
    public void testLogin() throws Exception {

    }

    @Test(groups={"yossis_test"},   invocationCount = 5)
    public void testPasv() throws Exception {
        try {
            System.out.println("IN function testPasv()");

            FTPReply reply = client.login("anonymous", "igv@broadinstitute.org");
            System.out.println("Logged-in");

            Assert.assertTrue(reply.isSuccess(), "login");
            System.out.println("Successfully");

            reply = client.pasv();
            System.out.println("Passive mode");

            Assert.assertTrue(reply.isSuccess(), "pasv");
            System.out.println("Successfully");

        } finally {
            System.out.println("IN FINALLY");

            client.closeDataStream();
            System.out.println("Closed DS");
        }
    }

    @Test
    public void testSize() throws Exception {

        FTPReply reply = client.login("anonymous", "igv@broadinstitute.org");
        Assert.assertTrue(reply.isSuccess());

        reply = client.binary();
        Assert.assertTrue(reply.isSuccess(), "binary");

        reply = client.size(file);
        String val = reply.getReplyString();
        int size = Integer.parseInt(val);
        Assert.assertEquals(fileSize, size, "size");
    }

    @Test(groups={"yossis_test"},   invocationCount = 5)
    public void testDownload() throws Exception {
        try {
            System.out.println("IN function testDownload()");

            FTPReply reply = client.login("anonymous", "igv@broadinstitute.org");
            System.out.println("Logged-in");

            Assert.assertTrue(reply.isSuccess(), "login");
            System.out.println("Successfully");

            Assert.assertTrue(reply.isSuccess(), "binary");
            System.out.println("Successfully");

            reply = client.binary();
            System.out.println("binary");

            reply = client.pasv();
            System.out.println("pasv");

            Assert.assertTrue(reply.isSuccess(), "pasv");
            System.out.println("Successfully");

            reply = client.retr(file);
            System.out.println("retrieve");

            Assert.assertEquals(reply.getCode(), 150, "retr");
            System.out.println("Successfully");

            InputStream is = client.getDataStream();
            System.out.println("got DS");

            int idx = 0;
            int b;
            while ((b = is.read()) >= 0) {
                System.out.println("reading");
                Assert.assertEquals(expectedBytes[idx], (byte) b,"reading from stream");
                System.out.println("expected amount");
                idx++;
            }

        } finally {
            System.out.println("in FINALLY");

            client.closeDataStream();
            System.out.println("closed DS");

            FTPReply reply = client.retr(file);
            System.out.println("got file");

            System.out.println(reply.getCode());
            System.out.println("Successfully");
            Assert.assertTrue(reply.isSuccess(), "close");
            System.out.println("is Success");

        }
    }

    @Test
    public void testRest() throws Exception {
        try {
            FTPReply reply = client.login("anonymous", "igv@broadinstitute.org");
            Assert.assertTrue(reply.isSuccess(), "login");

            reply = client.binary();
            Assert.assertTrue(reply.isSuccess(), "binary");

            reply = client.pasv();
            Assert.assertTrue(reply.isSuccess(), "pasv");

            final int restPosition = 5;
            client.setRestPosition(restPosition);

            reply = client.retr(file);
            Assert.assertEquals(reply.getCode(), 150, "retr");

            InputStream is = client.getDataStream();
            int idx = restPosition;
            int b;
            while ((b = is.read()) >= 0) {
                Assert.assertEquals(expectedBytes[idx], (byte) b, "reading from stream");
                idx++;
            }

        } finally {
            client.closeDataStream();
            FTPReply reply = client.retr(file);
            System.out.println(reply.getCode());
            Assert.assertTrue(reply.isSuccess(), "close");
        }
    }

    /**
     * Test accessing a non-existent file
     */
    @Test
    public void testNonExistentFile() throws Exception {

        String host = "ftp.broadinstitute.org";
        String file = "/pub/igv/TEST/fileDoesntExist.txt";
        FTPClient client = new FTPClient();

        FTPReply reply = client.connect(host);
        Assert.assertTrue(reply.isSuccess(), "connect");

        reply = client.login("anonymous", "igv@broadinstitute.org");
        Assert.assertTrue(reply.isSuccess(), "login");

        reply = client.binary();
        Assert.assertTrue(reply.isSuccess(), "binary");

        reply = client.executeCommand("size " + file);
        Assert.assertEquals(550, reply.getCode(), "size");

        client.disconnect();
    }

    /**
     * Test accessing a non-existent server
     */
    @Test
    public void testNonExistentServer() throws Exception {

        String host = "ftp.noSuchServer.org";
        String file = "/pub/igv/TEST/fileDoesntExist.txt";
        FTPClient client = new FTPClient();

        FTPReply reply = null;
        try {
            reply = client.connect(host);
        } catch (UnknownHostException e) {
            // This is expected
        }

        client.disconnect();
    }

    @Test
    public void testMultiplePasv() throws Exception {

        try {
            FTPReply reply = client.login("anonymous", "igv@broadinstitute.org");
            Assert.assertTrue(reply.isSuccess(), "login");

            reply = client.pasv();
            Assert.assertTrue(reply.isSuccess(), "pasv 1");
            client.closeDataStream();

            reply = client.pasv();
            Assert.assertTrue(reply.isSuccess(), "pasv 2");
            client.closeDataStream();
        }
        finally {

        }
    }

    @Test
    public void testMultipleRest() throws Exception {
        FTPReply reply = client.login("anonymous", "igv@broadinstitute.org");
        Assert.assertTrue(reply.isSuccess(), "login");

        reply = client.binary();
        Assert.assertTrue(reply.isSuccess(), "binary");

        restRetr(5, 10);
        restRetr(2, 10);
        restRetr(15, 10);
    }

    private void restRetr(int restPosition, int length) throws IOException {

        try {

            if (client.getDataStream() == null) {
                FTPReply reply = client.pasv();
                Assert.assertTrue(reply.isSuccess(), "pasv");
            }

            client.setRestPosition(restPosition);

            FTPReply reply = client.retr(file);
            //assertTrue(reply.getCode() == 150);

            InputStream is = client.getDataStream();

            byte[] buffer = new byte[length];
            is.read(buffer);

            for (int i = 0; i < length; i++) {
                System.out.print((char) buffer[i]);
                Assert.assertEquals(expectedBytes[i + restPosition], buffer[i], "reading from stream");
            }
            System.out.println();
        }

        finally {
            client.closeDataStream();
            FTPReply reply = client.getReply();  // <== MUST READ THE REPLY
            System.out.println(reply.getReplyString());
        }
    }
}
