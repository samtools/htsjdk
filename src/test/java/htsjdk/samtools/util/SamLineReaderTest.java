package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SamLineReaderTest extends HtsjdkTest {

    private static SamLineReader readerFrom(final String content) {
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new SamLineReader(new ByteArrayInputStream(bytes));
    }

    private static SamLineReader readerFrom(final String content, final int bufferSize) {
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new SamLineReader(new ByteArrayInputStream(bytes), bufferSize);
    }

    @DataProvider
    public Object[][] lineTerminators() {
        return new Object[][] {{"\n"}, {"\r"}, {"\r\n"}};
    }

    @Test(dataProvider = "lineTerminators")
    public void testSingleLine(final String terminator) {
        try (final SamLineReader reader = readerFrom("hello" + terminator)) {
            Assert.assertEquals(reader.readLine(), "hello");
            Assert.assertNull(reader.readLine());
        }
    }

    @Test(dataProvider = "lineTerminators")
    public void testMultipleLines(final String terminator) {
        final String input = "line1" + terminator + "line2" + terminator + "line3" + terminator;
        try (final SamLineReader reader = readerFrom(input)) {
            Assert.assertEquals(reader.readLine(), "line1");
            Assert.assertEquals(reader.readLine(), "line2");
            Assert.assertEquals(reader.readLine(), "line3");
            Assert.assertNull(reader.readLine());
        }
    }

    @Test(dataProvider = "lineTerminators")
    public void testLastLineWithoutTerminator(final String terminator) {
        final String input = "line1" + terminator + "line2";
        try (final SamLineReader reader = readerFrom(input)) {
            Assert.assertEquals(reader.readLine(), "line1");
            Assert.assertEquals(reader.readLine(), "line2");
            Assert.assertNull(reader.readLine());
        }
    }

    @Test
    public void testEmptyInput() {
        try (final SamLineReader reader = readerFrom("")) {
            Assert.assertNull(reader.readLine());
        }
    }

    @Test(dataProvider = "lineTerminators")
    public void testEmptyLines(final String terminator) {
        final String input = "" + terminator + "hello" + terminator + "" + terminator;
        try (final SamLineReader reader = readerFrom(input)) {
            Assert.assertEquals(reader.readLine(), "");
            Assert.assertEquals(reader.readLine(), "hello");
            Assert.assertEquals(reader.readLine(), "");
            Assert.assertNull(reader.readLine());
        }
    }

    @Test
    public void testPeekReturnsFirstCharacter() {
        try (final SamLineReader reader = readerFrom("@HD\tVN:1.6\nread1\t0\t*\n")) {
            Assert.assertEquals(reader.peek(), '@');
            Assert.assertEquals(reader.readLine(), "@HD\tVN:1.6");
            Assert.assertEquals(reader.peek(), 'r');
        }
    }

    @Test
    public void testPeekAtEof() {
        try (final SamLineReader reader = readerFrom("hello\n")) {
            reader.readLine();
            Assert.assertEquals(reader.peek(), LineReader.EOF_VALUE);
        }
    }

    @Test
    public void testPeekOnEmptyInput() {
        try (final SamLineReader reader = readerFrom("")) {
            Assert.assertEquals(reader.peek(), LineReader.EOF_VALUE);
        }
    }

    @Test
    public void testPeekIsNonDestructive() {
        try (final SamLineReader reader = readerFrom("abc\n")) {
            Assert.assertEquals(reader.peek(), 'a');
            Assert.assertEquals(reader.peek(), 'a');
            Assert.assertEquals(reader.peek(), 'a');
            Assert.assertEquals(reader.readLine(), "abc");
        }
    }

    @Test
    public void testGetLineNumber() {
        try (final SamLineReader reader = readerFrom("@HD\tVN:1.6\nread1\t0\t*\nread2\t0\t*\n")) {
            Assert.assertEquals(reader.getLineNumber(), 0);
            reader.readLine();
            Assert.assertEquals(reader.getLineNumber(), 1);
            reader.readLine();
            Assert.assertEquals(reader.getLineNumber(), 2);
            reader.readLine();
            Assert.assertEquals(reader.getLineNumber(), 3);
        }
    }

    @Test
    public void testUtf8InHeaderLines() {
        // The spec allows UTF-8 in @CO, @SQ DS, @RG DS, @PG DS, and @PG CL.
        // "ñ" is U+00F1 = 0xC3 0xB1 in UTF-8.  If incorrectly decoded as
        // ISO-8859-1, it would produce two characters instead of one.
        final String utf8Value = "sample with ñ and über";
        final String input = "@HD\tVN:1.6\n@CO\t" + utf8Value + "\nread1\t0\t*\t0\t0\t*\t*\t0\t0\t*\t*\n";
        try (final SamLineReader reader = readerFrom(input)) {
            Assert.assertEquals(reader.readLine(), "@HD\tVN:1.6");
            final String coLine = reader.readLine();
            Assert.assertEquals(coLine, "@CO\t" + utf8Value);
            Assert.assertTrue(coLine.contains("ñ"));
            Assert.assertTrue(coLine.contains("ü"));
        }
    }

    @Test
    public void testAsciiAlignmentLines() {
        final String input = "read1\t0\t*\t0\t0\t*\t*\t0\t0\tACGT\tFFFF\tNM:i:0\n";
        try (final SamLineReader reader = readerFrom(input)) {
            Assert.assertEquals(reader.readLine(), "read1\t0\t*\t0\t0\t*\t*\t0\t0\tACGT\tFFFF\tNM:i:0");
            Assert.assertNull(reader.readLine());
        }
    }

    @Test
    public void testLineLongerThanBuffer() {
        final StringBuilder sb = new StringBuilder(100_001);
        for (int i = 0; i < 100_000; i++) {
            sb.append('A');
        }
        final String longLine = sb.toString();
        // Use a small buffer to force multiple refills
        try (final SamLineReader reader = readerFrom(longLine + "\n", 256)) {
            Assert.assertEquals(reader.readLine(), longLine);
            Assert.assertNull(reader.readLine());
        }
    }

    @Test
    public void testLineBoundaryAtBufferEdge() {
        // Place the line terminator right at buffer boundaries with a small buffer
        final String line1 = "ABCDE";
        final String line2 = "FGHIJ";
        for (int bufSize = line1.length() + 1; bufSize <= line1.length() + 3; bufSize++) {
            try (final SamLineReader reader = readerFrom(line1 + "\n" + line2 + "\n", bufSize)) {
                Assert.assertEquals(reader.readLine(), line1, "bufSize=" + bufSize);
                Assert.assertEquals(reader.readLine(), line2, "bufSize=" + bufSize);
                Assert.assertNull(reader.readLine());
            }
        }
    }

    @Test
    public void testCrLfSplitAcrossBufferBoundary() {
        // \r is the last byte in one buffer fill, \n is the first byte in the next
        final String line = "ABCD";
        final String input = line + "\r\n" + "next\n";
        // Buffer size = 5: holds "ABCD\r", then next fill has "\nnext\n"
        try (final SamLineReader reader = readerFrom(input, line.length() + 1)) {
            Assert.assertEquals(reader.readLine(), line);
            Assert.assertEquals(reader.readLine(), "next");
            Assert.assertNull(reader.readLine());
        }
    }

    @Test
    public void testHeaderThenAlignmentLines() {
        final String header = "@HD\tVN:1.6\n@SQ\tSN:chr1\tLN:1000\n";
        final String alignment = "read1\t0\tchr1\t100\t60\t50M\t*\t0\t0\tACGT\tFFFF\tNM:i:0\n";
        try (final SamLineReader reader = readerFrom(header + alignment)) {
            Assert.assertEquals(reader.peek(), '@');
            Assert.assertEquals(reader.readLine(), "@HD\tVN:1.6");
            Assert.assertEquals(reader.peek(), '@');
            Assert.assertEquals(reader.readLine(), "@SQ\tSN:chr1\tLN:1000");
            Assert.assertNotEquals(reader.peek(), '@');
            Assert.assertEquals(reader.readLine(), "read1\t0\tchr1\t100\t60\t50M\t*\t0\t0\tACGT\tFFFF\tNM:i:0");
            Assert.assertNull(reader.readLine());
        }
    }

    @Test
    public void testNoHeader() {
        final String input = "read1\t0\t*\t0\t0\t*\t*\t0\t0\t*\t*\n";
        try (final SamLineReader reader = readerFrom(input)) {
            Assert.assertNotEquals(reader.peek(), '@');
            Assert.assertEquals(reader.readLine(), "read1\t0\t*\t0\t0\t*\t*\t0\t0\t*\t*");
            Assert.assertNull(reader.readLine());
        }
    }

    @Test
    public void testOnlyHeaderLines() {
        final String input = "@HD\tVN:1.6\n@SQ\tSN:chr1\tLN:1000\n@RG\tID:rg1\n";
        try (final SamLineReader reader = readerFrom(input)) {
            Assert.assertEquals(reader.readLine(), "@HD\tVN:1.6");
            Assert.assertEquals(reader.readLine(), "@SQ\tSN:chr1\tLN:1000");
            Assert.assertEquals(reader.readLine(), "@RG\tID:rg1");
            Assert.assertNull(reader.readLine());
        }
    }

    @Test
    public void testMixedTerminators() {
        final String input = "line1\nline2\rline3\r\nline4";
        try (final SamLineReader reader = readerFrom(input)) {
            Assert.assertEquals(reader.readLine(), "line1");
            Assert.assertEquals(reader.readLine(), "line2");
            Assert.assertEquals(reader.readLine(), "line3");
            Assert.assertEquals(reader.readLine(), "line4");
            Assert.assertNull(reader.readLine());
        }
    }

    @Test
    public void testRepeatedReadLineAfterEof() {
        try (final SamLineReader reader = readerFrom("hello\n")) {
            Assert.assertEquals(reader.readLine(), "hello");
            Assert.assertNull(reader.readLine());
            Assert.assertNull(reader.readLine());
            Assert.assertNull(reader.readLine());
        }
    }

    @Test
    public void testSingleCharacterLines() {
        try (final SamLineReader reader = readerFrom("a\nb\nc\n")) {
            Assert.assertEquals(reader.readLine(), "a");
            Assert.assertEquals(reader.readLine(), "b");
            Assert.assertEquals(reader.readLine(), "c");
            Assert.assertNull(reader.readLine());
        }
    }

    @Test
    public void testSingleTerminator() {
        try (final SamLineReader reader = readerFrom("\n")) {
            Assert.assertEquals(reader.readLine(), "");
            Assert.assertNull(reader.readLine());
        }
    }

    // ----- byte-based API tests (readNextLine + getLineBuffer / getLineOffset / getLineLength) ---

    private static String currentLineAsString(final SamLineReader reader) {
        return new String(
                reader.getLineBuffer(), reader.getLineOffset(), reader.getLineLength(), StandardCharsets.ISO_8859_1);
    }

    @Test
    public void testReadNextLineReturnsTrueWhileLinesAvailable() {
        try (final SamLineReader reader = readerFrom("alpha\nbeta\ngamma\n")) {
            Assert.assertTrue(reader.readNextLine());
            Assert.assertEquals(currentLineAsString(reader), "alpha");
            Assert.assertTrue(reader.readNextLine());
            Assert.assertEquals(currentLineAsString(reader), "beta");
            Assert.assertTrue(reader.readNextLine());
            Assert.assertEquals(currentLineAsString(reader), "gamma");
            Assert.assertFalse(reader.readNextLine());
        }
    }

    @Test
    public void testReadNextLineOnEmptyInput() {
        try (final SamLineReader reader = readerFrom("")) {
            Assert.assertFalse(reader.readNextLine());
        }
    }

    @Test
    public void testReadNextLineExposesLineWithinMainBuffer() {
        // Two lines followed by trailing bytes so neither line ends at the buffer limit; in
        // that case the fast path exposes the main buffer directly. The trailing "extra\n"
        // ensures pos < limit after both readNextLine() calls.
        try (final SamLineReader reader = readerFrom("first\nsecond\nextra\n")) {
            Assert.assertTrue(reader.readNextLine());
            final byte[] firstBuf = reader.getLineBuffer();
            Assert.assertEquals(currentLineAsString(reader), "first");
            Assert.assertTrue(reader.readNextLine());
            Assert.assertSame(reader.getLineBuffer(), firstBuf, "expected the same main buffer on the fast path");
            Assert.assertEquals(currentLineAsString(reader), "second");
        }
    }

    @Test
    public void testReadNextLineSpillsToOverflowWhenLineCrossesBufferBoundary() {
        // Force the line to straddle two fills by using a buffer smaller than the line.
        final String line = "ABCDEFGHIJ";
        try (final SamLineReader reader = readerFrom(line + "\n", 4)) {
            Assert.assertTrue(reader.readNextLine());
            Assert.assertEquals(currentLineAsString(reader), line);
            Assert.assertFalse(reader.readNextLine());
        }
    }

    @Test
    public void testReadNextLineWithCrLfTerminator() {
        try (final SamLineReader reader = readerFrom("hello\r\nworld\r\n")) {
            Assert.assertTrue(reader.readNextLine());
            Assert.assertEquals(currentLineAsString(reader), "hello");
            Assert.assertTrue(reader.readNextLine());
            Assert.assertEquals(currentLineAsString(reader), "world");
            Assert.assertFalse(reader.readNextLine());
        }
    }

    @Test
    public void testReadNextLineCrLfSplitAcrossBufferBoundary() {
        // Buffer size 5 means the first fill holds "ABCD\r", and the \n shows up in the next fill.
        try (final SamLineReader reader = readerFrom("ABCD\r\nnext\n", 5)) {
            Assert.assertTrue(reader.readNextLine());
            Assert.assertEquals(currentLineAsString(reader), "ABCD");
            Assert.assertTrue(reader.readNextLine());
            Assert.assertEquals(currentLineAsString(reader), "next");
            Assert.assertFalse(reader.readNextLine());
        }
    }

    @Test
    public void testReadNextLineEmptyLines() {
        try (final SamLineReader reader = readerFrom("\nx\n\n")) {
            Assert.assertTrue(reader.readNextLine());
            Assert.assertEquals(reader.getLineLength(), 0);
            Assert.assertTrue(reader.readNextLine());
            Assert.assertEquals(currentLineAsString(reader), "x");
            Assert.assertTrue(reader.readNextLine());
            Assert.assertEquals(reader.getLineLength(), 0);
            Assert.assertFalse(reader.readNextLine());
        }
    }

    @Test
    public void testReadNextLineLastLineWithoutTerminator() {
        try (final SamLineReader reader = readerFrom("first\nlast")) {
            Assert.assertTrue(reader.readNextLine());
            Assert.assertEquals(currentLineAsString(reader), "first");
            Assert.assertTrue(reader.readNextLine());
            Assert.assertEquals(currentLineAsString(reader), "last");
            Assert.assertFalse(reader.readNextLine());
        }
    }

    @Test
    public void testGetLineNumberIncrementsWithByteApi() {
        try (final SamLineReader reader = readerFrom("a\nb\nc\n")) {
            Assert.assertEquals(reader.getLineNumber(), 0);
            reader.readNextLine();
            Assert.assertEquals(reader.getLineNumber(), 1);
            reader.readNextLine();
            Assert.assertEquals(reader.getLineNumber(), 2);
            reader.readNextLine();
            Assert.assertEquals(reader.getLineNumber(), 3);
        }
    }

    @Test
    public void testReadNextLineAndReadLineCanBeInterleaved() {
        // The two APIs share state, so calling readLine() should still work after readNextLine().
        try (final SamLineReader reader = readerFrom("byte-api\nstring-api\n")) {
            Assert.assertTrue(reader.readNextLine());
            Assert.assertEquals(currentLineAsString(reader), "byte-api");
            Assert.assertEquals(reader.readLine(), "string-api");
            Assert.assertNull(reader.readLine());
        }
    }

    @Test
    public void testReadNextLineThenAnotherReadNextLine() {
        // The contract says current-line accessors are invalidated by the next readNextLine().
        // This pins that the second call correctly exposes the second line's bytes.
        final String first = "ABCDE";
        final String second = "FGHIJ";
        try (final SamLineReader reader = readerFrom(first + "\n" + second + "\n", first.length() + 1)) {
            Assert.assertTrue(reader.readNextLine());
            Assert.assertEquals(currentLineAsString(reader), first);
            Assert.assertTrue(reader.readNextLine());
            Assert.assertEquals(currentLineAsString(reader), second);
        }
    }

    @Test
    public void testCloseIsIdempotent() throws java.io.IOException {
        final SamLineReader reader = readerFrom("hello\n");
        reader.close();
        reader.close(); // second close must not throw
    }
}
