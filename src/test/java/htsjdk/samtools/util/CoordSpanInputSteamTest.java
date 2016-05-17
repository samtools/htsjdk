package htsjdk.samtools.util;

import htsjdk.samtools.seekablestream.ByteArraySeekableStream;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

/**
 * Created by vadim on 25/03/2015.
 */
public class CoordSpanInputSteamTest {

    @Test
    public void test_first_3_bytes() throws IOException {
        byte[] data = new byte[1024 * 1024];
        new Random().nextBytes(data);

        long[] coords = new long[]{0, 1, 1, 2, 2, 3};

        CoordSpanInputSteam csis = new CoordSpanInputSteam(new ByteArraySeekableStream(data), coords);

        Assert.assertEquals(csis.read(), 0xFF & data[0]);
        Assert.assertEquals(csis.read(), 0xFF & data[1]);
        Assert.assertEquals(csis.read(), 0xFF & data[2]);

        Assert.assertEquals(csis.read(), -1);
    }

    @Test
    public void test_3_ranges_byte_single_read() throws IOException {
        byte[] data = new byte[1024 * 1024];
        new Random().nextBytes(data);

        long[] coords = new long[]{0, 100, 10, 20, 100, 200, data.length - 1, Long.MAX_VALUE};

        CoordSpanInputSteam csis = new CoordSpanInputSteam(new ByteArraySeekableStream(data), coords);

        for (int i = 0; i < coords.length; i += 2) {
            for (int c = (int) coords[i]; c < coords[i + 1]; c++) {
                int read = csis.read();
                if (c >= data.length) {
                    Assert.assertEquals(read, -1);
                    break;
                } else
                    Assert.assertEquals(read, 0xFF & data[c], String.format("At %d: read=%d, data=%d\n", c, read, data[c]));
            }
        }
    }

    @Test
    public void test_range_read() throws IOException {
        byte[] data = new byte[1024 * 1024];
        new Random().nextBytes(data);

        long[] coords = new long[]{0, 100, 10, 20, 100, 200, data.length - 1, Long.MAX_VALUE};

        CoordSpanInputSteam csis = new CoordSpanInputSteam(new ByteArraySeekableStream(data), coords);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        for (int i = 0; i < coords.length; i += 2) {
            for (int c = (int) coords[i]; c < coords[i + 1]; c++) {
                int read = csis.read();
                if (read == -1) break;
                baos.write(data[c]);
            }
        }
        byte[] contiguous = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(contiguous);
        csis = new CoordSpanInputSteam(new ByteArraySeekableStream(data), coords);

        byte[] buf1 = new byte[100];
        byte[] buf2 = new byte[100];
        DataInputStream dis1 = new DataInputStream(csis);
        DataInputStream dis2 = new DataInputStream(bais);

        Arrays.fill(buf1, (byte) 0);
        Arrays.fill(buf2, (byte) 0);
        dis1.readFully(buf1, 0, 10);
        dis2.readFully(buf2, 0, 10);
        Assert.assertEquals(buf1, buf2);

        int len = 11;
        while (true) {
            try {
                dis1.readFully(buf1, 0, len);
            } catch (EOFException e) {
                break;
            }
            dis2.readFully(buf2, 0, len);
            Assert.assertEquals(buf1, buf2);
        }
        try {
            dis2.readFully(buf2, 0, len);
        } catch (EOFException e) {

        }
        Assert.assertEquals(dis1.read(), -1);
        Assert.assertEquals(dis2.read(), -1);
    }

}
