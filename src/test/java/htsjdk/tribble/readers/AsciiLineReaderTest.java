package htsjdk.tribble.readers;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.tribble.TestUtils;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * User: jacob
 * Date: 2012/05/09
 */
public class AsciiLineReaderTest extends HtsjdkTest {
    /**
     * Test that we read the correct number of lines
     * from a file
     * @throws Exception
     */
    @Test
    public void testReadLines() throws Exception {
        Path filePath = Path.of(TestUtils.DATA_DIR, "gwas/smallp.gwas");
        InputStream is = Files.newInputStream(filePath);
        AsciiLineReader reader = AsciiLineReader.from(is);
        int actualLines = 0;
        int expectedNumber = 20;
        String nextLine = "";

        while ((nextLine = reader.readLine()) != null && actualLines < (expectedNumber + 5)) {
            actualLines++;
            // This particular test file has no empty lines
            assertTrue(nextLine.length() > 0);
        }

        assertEquals(expectedNumber, actualLines);
    }

    @Test
    public void voidTestLineEndingLength() throws Exception {
        final String input = "Hello\nThis\rIs A Silly Test\r\nSo There";
        final InputStream is = new ByteArrayInputStream(input.getBytes());
        final AsciiLineReader in = AsciiLineReader.from(is);

        Assert.assertEquals(in.getLineTerminatorLength(), -1);
        Assert.assertEquals(in.readLine(), "Hello");
        Assert.assertEquals(in.getLineTerminatorLength(), 1);
        Assert.assertEquals(in.readLine(), "This");
        Assert.assertEquals(in.getLineTerminatorLength(), 1);
        Assert.assertEquals(in.readLine(), "Is A Silly Test");
        Assert.assertEquals(in.getLineTerminatorLength(), 2);
        Assert.assertEquals(in.readLine(), "So There");
        Assert.assertEquals(in.getLineTerminatorLength(), 0);
    }

    @Test
    public void voidTestLineEndingLengthAtEof() throws Exception {
        final String input = "Hello\nWorld\r\n";
        final InputStream is = new ByteArrayInputStream(input.getBytes());
        final AsciiLineReader in = AsciiLineReader.from(is);

        Assert.assertEquals(in.getLineTerminatorLength(), -1);
        Assert.assertEquals(in.readLine(), "Hello");
        Assert.assertEquals(in.getLineTerminatorLength(), 1);
        Assert.assertEquals(in.readLine(), "World");
        Assert.assertEquals(in.getLineTerminatorLength(), 2);
    }

    @DataProvider(name = "fromStream")
    public Object[][] getFromStreamData() {
        return new Object[][] {
            {
                new BlockCompressedInputStream(new ByteArrayInputStream(new byte[10])),
                BlockCompressedAsciiLineReader.class
            },
            {new PositionalBufferedStream(new ByteArrayInputStream(new byte[10])), AsciiLineReader.class},
            {new ByteArrayInputStream(new byte[10]), AsciiLineReader.class}
        };
    }

    @Test(dataProvider = "fromStream")
    public void testFromStream(final InputStream inStream, final Class expectedClass) {
        AsciiLineReader alr = AsciiLineReader.from(inStream);
        Assert.assertEquals(alr.getClass(), expectedClass);
    }

    // UTF-8 decoding

    @Test
    public void twoByteUtf8DecodesCorrectly() throws Exception {
        // U+00E9 (e-acute) is 0xC3 0xA9 in UTF-8
        final byte[] utf8 = "café\n".getBytes(StandardCharsets.UTF_8);
        final AsciiLineReader reader = AsciiLineReader.from(new ByteArrayInputStream(utf8));
        Assert.assertEquals(reader.readLine(), "café");
        Assert.assertNull(reader.readLine());
    }

    @Test
    public void threeByteUtf8DecodesCorrectly() throws Exception {
        // U+20AC (euro sign) is 0xE2 0x82 0xAC in UTF-8
        final byte[] utf8 = "price=€10\n".getBytes(StandardCharsets.UTF_8);
        final AsciiLineReader reader = AsciiLineReader.from(new ByteArrayInputStream(utf8));
        Assert.assertEquals(reader.readLine(), "price=€10");
    }

    @Test
    public void fourByteUtf8DecodesCorrectly() throws Exception {
        // U+1F600 (grinning face) is outside the BMP: 0xF0 0x9F 0x98 0x80 in UTF-8
        final String smiley = new String(Character.toChars(0x1F600));
        final byte[] utf8 = ("hello" + smiley + "\n").getBytes(StandardCharsets.UTF_8);
        final AsciiLineReader reader = AsciiLineReader.from(new ByteArrayInputStream(utf8));
        Assert.assertEquals(reader.readLine(), "hello" + smiley);
    }

    @Test
    public void asciiAndNonAsciiLinesInOneFileEachDecodeCorrectly() throws Exception {
        // Each line is decoded on its own: an ASCII line, a line that starts with a multi-byte character, a line
        // whose only non-ASCII byte is its last, then an ASCII line again
        final String[] lines = {"chr1\t100\tA\tG", "€ first", "last é", "chr2\t200\tC\tT"};
        final byte[] utf8 = (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
        final AsciiLineReader reader = AsciiLineReader.from(new ByteArrayInputStream(utf8));
        for (final String line : lines) {
            Assert.assertEquals(reader.readLine(), line);
        }
        Assert.assertNull(reader.readLine());
    }

    @Test
    public void bareLatin1ByteDecodesAsReplacementCharacter() throws Exception {
        // A bare 0xE9 is not valid UTF-8; it should decode as U+FFFD
        final byte[] bytes = {'h', 'e', 'l', 'l', 'o', (byte) 0xE9, '\n'};
        final AsciiLineReader reader = AsciiLineReader.from(new ByteArrayInputStream(bytes));
        Assert.assertEquals(reader.readLine(), "hello�");
    }

    @Test
    public void lineExactlyAtBufferSizeDecodesCorrectly() throws Exception {
        // Build a line whose byte length is exactly the initial buffer size (10000)
        final byte[] filler = new byte[9998];
        Arrays.fill(filler, (byte) 'A');
        // append a 2-byte UTF-8 char to reach exactly 10000 bytes
        final byte[] utf8Char = "é".getBytes(StandardCharsets.UTF_8); // 0xC3 0xA9
        final byte[] line = new byte[10000 + 1]; // +1 for \n
        System.arraycopy(filler, 0, line, 0, filler.length);
        System.arraycopy(utf8Char, 0, line, filler.length, utf8Char.length);
        line[10000] = '\n';

        final AsciiLineReader reader = AsciiLineReader.from(new ByteArrayInputStream(line));
        final String result = reader.readLine();
        Assert.assertEquals(result.length(), 9999); // 9998 A's + 1 e-acute char
        Assert.assertTrue(result.endsWith("é"));
    }

    @Test
    public void multiByteSequenceStraddlingTheInputBufferRefillDecodesCorrectly() throws Exception {
        // With a 16-byte input buffer, a 3-byte sequence that starts at offset 14 has two bytes in the first fill
        // and its last byte in the second
        final int bufferSize = 16;
        final String line = "A".repeat(bufferSize - 2) + "€";
        final byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
        Assert.assertEquals(bytes.length, bufferSize + 2);

        final AsciiLineReader reader =
                AsciiLineReader.from(new PositionalBufferedStream(new ByteArrayInputStream(bytes), bufferSize));
        Assert.assertEquals(reader.readLine(), line);
        Assert.assertNull(reader.readLine());
    }

    @Test
    public void getPositionAfterMultiByteLineEqualsTheByteOffset() throws Exception {
        // "café" is 5 bytes in UTF-8 (c=1, a=1, f=1, e-acute=2) + 1 for \n = 6
        final byte[] utf8 = "café\n".getBytes(StandardCharsets.UTF_8);
        final AsciiLineReader reader = AsciiLineReader.from(new ByteArrayInputStream(utf8));
        reader.readLine();
        Assert.assertEquals(reader.getPosition(), 6);
    }
}
