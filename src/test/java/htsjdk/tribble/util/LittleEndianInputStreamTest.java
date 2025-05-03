package htsjdk.tribble.util;

import htsjdk.HtsjdkTest;
import htsjdk.testutil.Expected;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class LittleEndianInputStreamTest extends HtsjdkTest {


    @DataProvider
    public Object[][] testCases() {
        final String missingTerminator = "src/test/resources/htsjdk/tribble/util/string_with_extended_ascii_no_terminator.bin";
        final String extendedAsciiFile = "src/test/resources/htsjdk/tribble/util/string_with_extended_ascii_and_null_terminator.bin";
        final Object utf8File = "src/test/resources/htsjdk/tribble/util/string_with_utf8_emoji_and_null_terminator.txt";
        return new Object[][]{
                {missingTerminator, StandardCharsets.ISO_8859_1, Expected.exception(EOFException.class)},
                {missingTerminator, StandardCharsets.US_ASCII, Expected.exception(EOFException.class)},
                {missingTerminator, StandardCharsets.UTF_8, Expected.exception(EOFException.class)},
                {extendedAsciiFile, StandardCharsets.ISO_8859_1, Expected.match("very dràààààmatic and null terminated")},
                {extendedAsciiFile, StandardCharsets.US_ASCII, Expected.mismatch("very dràààààmatic and null terminated")},
                {extendedAsciiFile, StandardCharsets.UTF_8, Expected.mismatch("very dràààààmatic and null terminated")},
                {utf8File, StandardCharsets.UTF_8, Expected.match("🐋 UTF8 is Great 🐋")},
                {utf8File, StandardCharsets.ISO_8859_1, Expected.mismatch("🐋 UTF8 is Great 🐋")}
        };
    }

    @Test(dataProvider = "testCases")
    public void testAllCases(String filename, Charset charset, Expected<String> expected) {
        expected.test(() -> {
            try(final LittleEndianInputStream in = new LittleEndianInputStream(new BufferedInputStream(Files.newInputStream(Paths.get(filename))))){
                return in.readString(charset);
            }
        });
    }

}