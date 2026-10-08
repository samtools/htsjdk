package htsjdk.samtools.seekablestream;

import htsjdk.HtsjdkTest;
import htsjdk.testutil.http.LocalHttpServer;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Arrays;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SeekableHTTPStreamTest extends HtsjdkTest {
    private static final String PATH = "/data/bytes.bin";

    /** 1000 bytes, each different from its neighbours, so a read from the wrong offset can't match. */
    private static byte[] contents() {
        final byte[] bytes = new byte[1000];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 7);
        }
        return bytes;
    }

    private static LocalHttpServer serving(final byte[] contents) throws IOException {
        return new LocalHttpServer().addFile(PATH, contents);
    }

    @Test
    public void lengthIsTheContentLengthOfTheFile() throws IOException {
        try (LocalHttpServer server = serving(contents());
                SeekableHTTPStream stream = new SeekableHTTPStream(server.url(PATH))) {
            Assert.assertEquals(stream.length(), 1000);
        }
    }

    @Test
    public void aReadReturnsTheBytesAtThePosition() throws IOException {
        final byte[] contents = contents();
        try (LocalHttpServer server = serving(contents);
                SeekableHTTPStream stream = new SeekableHTTPStream(server.url(PATH))) {
            final byte[] buffer = new byte[100];
            Assert.assertEquals(stream.read(buffer, 0, 100), 100);
            Assert.assertEquals(buffer, Arrays.copyOfRange(contents, 0, 100));
            Assert.assertEquals(stream.position(), 100);
        }
    }

    @Test
    public void aReadAfterASeekReturnsTheBytesFromTheNewPosition() throws IOException {
        final byte[] contents = contents();
        try (LocalHttpServer server = serving(contents);
                SeekableHTTPStream stream = new SeekableHTTPStream(server.url(PATH))) {
            final byte[] buffer = new byte[50];
            stream.seek(600);
            Assert.assertEquals(stream.read(buffer, 0, 50), 50);
            Assert.assertEquals(buffer, Arrays.copyOfRange(contents, 600, 650));
        }
    }

    @Test
    public void aReadRunningPastTheEndReturnsOnlyTheBytesBeforeIt() throws IOException {
        final byte[] contents = contents();
        try (LocalHttpServer server = serving(contents);
                SeekableHTTPStream stream = new SeekableHTTPStream(server.url(PATH))) {
            final byte[] buffer = new byte[100];
            stream.seek(950);
            Assert.assertEquals(stream.read(buffer, 0, 100), 50);
            Assert.assertEquals(Arrays.copyOf(buffer, 50), Arrays.copyOfRange(contents, 950, 1000));
            Assert.assertTrue(stream.eof());
        }
    }

    @Test
    public void aReadAtTheEndReturnsMinusOne() throws IOException {
        try (LocalHttpServer server = serving(contents());
                SeekableHTTPStream stream = new SeekableHTTPStream(server.url(PATH))) {
            stream.seek(1000);
            Assert.assertEquals(stream.read(new byte[10], 0, 10), -1);
        }
    }

    // the server answers a range starting past the end with 416, which the stream takes as the end of the file
    @Test
    public void aReadFromBeyondTheEndReturnsMinusOne() throws IOException {
        try (LocalHttpServer server = serving(contents());
                SeekableHTTPStream stream = new SeekableHTTPStream(server.url(PATH))) {
            stream.seek(1010);
            Assert.assertEquals(stream.read(new byte[10], 0, 10), -1);
        }
    }

    // the stream doesn't check the status of its HEAD, so it takes the length of the server's 404 page
    @Test
    public void aMissingFileHasTheLengthOfTheServersErrorResponse() throws IOException {
        try (LocalHttpServer server = serving(contents());
                SeekableHTTPStream stream = new SeekableHTTPStream(server.url("/data/missing.bin"))) {
            final HttpURLConnection head =
                    (HttpURLConnection) server.url("/data/missing.bin").openConnection();
            head.setRequestMethod("HEAD");
            Assert.assertEquals(head.getResponseCode(), HttpURLConnection.HTTP_NOT_FOUND);
            Assert.assertEquals(stream.length(), head.getContentLengthLong());
            head.disconnect();
        }
    }
}
