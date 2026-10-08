package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.BufferedLineReader;
import htsjdk.samtools.util.Log;
import htsjdk.testutil.LogCapture;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SAMTextHeaderCodecTest extends HtsjdkTest {

    private static String encode(final SAMFileHeader header) {
        final StringWriter text = new StringWriter();
        new SAMTextHeaderCodec().encode(text, header);
        return text.toString();
    }

    private static String readGroupLineWithRunDate(final String runDate) {
        return "@RG\tID:rg1\tSM:sample1\tDT:" + runDate;
    }

    /** Decodes a header with one read group, rg1, whose DT is {@code runDate}. */
    private static SAMFileHeader decodeWithRunDate(final String runDate, final ValidationStringency stringency) {
        final SAMTextHeaderCodec codec = new SAMTextHeaderCodec();
        codec.setValidationStringency(stringency);
        final String headerText = "@HD\tVN:1.6\n" + readGroupLineWithRunDate(runDate) + "\n";
        return codec.decode(BufferedLineReader.fromString(headerText), null);
    }

    /** A DT value that no date format parses, and that no other test logs. */
    private static String uniqueUnparseableRunDate() {
        return "not a date " + UUID.randomUUID();
    }

    /**
     * Reads a header whose read group has {@code runDate} as DT under STRICT validation, and asserts that the read
     * group and the header written back out keep the text exactly, whatever the JVM's time zone.
     */
    private static void assertRunDateIsKeptAsWritten(final String runDate) {
        final SAMFileHeader header = decodeWithRunDate(runDate, ValidationStringency.STRICT);
        Assert.assertEquals(header.getReadGroup("rg1").getAttribute(SAMReadGroupRecord.DATE_RUN_PRODUCED_TAG), runDate);
        final String encoded = encode(header);
        Assert.assertTrue(encoded.contains("\n" + readGroupLineWithRunDate(runDate) + "\n"), encoded);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testReadGroupAttributeWithTabIsRejected() {
        final SAMFileHeader header = new SAMFileHeader();
        final SAMReadGroupRecord readGroup = new SAMReadGroupRecord("rg1");
        readGroup.setDescription("text with INJECTION\tPI:123");
        header.addReadGroup(readGroup);
        encode(header);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testReadGroupTagNameWithTabIsRejected() {
        final SAMFileHeader header = new SAMFileHeader();
        final SAMReadGroupRecord readGroup = new SAMReadGroupRecord("rg1");
        readGroup.setAttribute("DS:ok\tPI", "123");
        header.addReadGroup(readGroup);
        encode(header);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testReadGroupIdWithTabIsRejected() {
        final SAMFileHeader header = new SAMFileHeader();
        header.addReadGroup(new SAMReadGroupRecord("rg1\tPI:123"));
        encode(header);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testProgramIdWithLineFeedIsRejected() {
        final SAMFileHeader header = new SAMFileHeader();
        header.addProgramRecord(new SAMProgramRecord("pg1\n@RG\tID:injected"));
        encode(header);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testProgramCommandLineWithLineFeedIsRejected() {
        final SAMFileHeader header = new SAMFileHeader();
        final SAMProgramRecord program = new SAMProgramRecord("pg1");
        program.setCommandLine("tool --arg 'first line\nsecond line'");
        header.addProgramRecord(program);
        encode(header);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testHeaderLineAttributeWithCarriageReturnIsRejected() {
        final SAMFileHeader header = new SAMFileHeader();
        header.setAttribute("XX", "value\r");
        encode(header);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCommentWithLineFeedIsRejected() {
        final SAMFileHeader header = new SAMFileHeader();
        header.addComment("first line\n@RG\tID:injected");
        encode(header);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCommentWithCarriageReturnIsRejected() {
        final SAMFileHeader header = new SAMFileHeader();
        header.addComment("comment\r");
        encode(header);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testReadGroupAttributeWithNulIsRejected() {
        final SAMFileHeader header = new SAMFileHeader();
        final SAMReadGroupRecord readGroup = new SAMReadGroupRecord("rg1");
        readGroup.setSample("sample\0");
        header.addReadGroup(readGroup);
        encode(header);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCommentWithNulIsRejected() {
        final SAMFileHeader header = new SAMFileHeader();
        header.addComment("comment\0");
        encode(header);
    }

    @Test
    public void testNonAsciiHeaderValueIsWritten() {
        final SAMFileHeader header = new SAMFileHeader();
        final SAMReadGroupRecord readGroup = new SAMReadGroupRecord("rg1");
        readGroup.setDescription("Universität č");
        header.addReadGroup(readGroup);
        Assert.assertTrue(encode(header).contains("\tDS:Universität č"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testHeaderValueWithUnpairedSurrogateIsRejected() {
        final SAMFileHeader header = new SAMFileHeader();
        final SAMReadGroupRecord readGroup = new SAMReadGroupRecord("rg1");
        readGroup.setDescription("half \uD83D pair");
        header.addReadGroup(readGroup);
        encode(header);
    }

    @Test
    public void testHeaderValueWithSurrogatePairIsWritten() {
        final SAMFileHeader header = new SAMFileHeader();
        header.addComment("smile \uD83D\uDE00");
        Assert.assertTrue(encode(header).endsWith("@CO\tsmile \uD83D\uDE00\n"));
    }

    @Test
    public void testCommentWithTabIsWritten() {
        final SAMFileHeader header = new SAMFileHeader();
        header.addComment("key\tvalue");
        Assert.assertTrue(encode(header).endsWith("@CO\tkey\tvalue\n"));
    }

    @Test
    public void aDateOnlyRunDateIsKeptAsWritten() {
        assertRunDateIsKeptAsWritten("2016-01-01");
    }

    @Test
    public void aRunDateWithMillisecondsAndAnOffsetIsKeptAsWritten() {
        assertRunDateIsKeptAsWritten("2016-01-01T12:34:56.789+05:30");
    }

    @Test
    public void aRunDateWithASpaceBeforeTheTimeIsKeptAsWritten() {
        assertRunDateIsKeptAsWritten("2000-01-01 12:00:00");
    }

    @Test
    public void aRunDateWithASpaceBeforeTheTimeIsNotAValidationError() {
        final SAMFileHeader header = decodeWithRunDate("2000-01-01 12:00:00", ValidationStringency.STRICT);
        Assert.assertEquals(header.getValidationErrors(), List.of());
    }

    @Test
    public void anUnparseableRunDateIsKeptAsWritten() {
        assertRunDateIsKeptAsWritten(uniqueUnparseableRunDate());
    }

    @Test
    public void anUnparseableRunDateUnderStrictValidationIsAWarningNotAFailure() throws Exception {
        final String runDate = uniqueUnparseableRunDate();
        final SAMFileHeader[] header = new SAMFileHeader[1];
        final List<String> lines = LogCapture.linesLoggedContaining(
                runDate, Log.LogLevel.INFO, () -> header[0] = decodeWithRunDate(runDate, ValidationStringency.STRICT));
        Assert.assertEquals(lines.size(), 1, lines.toString());
        Assert.assertTrue(lines.get(0).startsWith(Log.LogLevel.WARNING.name()), lines.get(0));
        Assert.assertTrue(lines.get(0).contains("read group rg1"), lines.get(0));
        Assert.assertEquals(header[0].getValidationErrors().size(), 1);
        Assert.assertEquals(
                header[0].getValidationErrors().get(0).getType(), SAMValidationError.Type.INVALID_DATE_STRING);
    }

    @Test
    public void anUnparseableRunDateUnderLenientValidationLogsOneWarning() throws Exception {
        final String runDate = uniqueUnparseableRunDate();
        final List<String> lines = LogCapture.linesLoggedContaining(
                runDate, Log.LogLevel.INFO, () -> decodeWithRunDate(runDate, ValidationStringency.LENIENT));
        Assert.assertEquals(lines.size(), 1, lines.toString());
        Assert.assertTrue(lines.get(0).startsWith(Log.LogLevel.WARNING.name()), lines.get(0));
    }

    @Test
    public void anUnparseableRunDateUnderSilentValidationIsAValidationErrorButNotLogged() throws Exception {
        final String runDate = uniqueUnparseableRunDate();
        final SAMFileHeader[] header = new SAMFileHeader[1];
        final List<String> lines = LogCapture.linesLoggedContaining(
                runDate, Log.LogLevel.INFO, () -> header[0] = decodeWithRunDate(runDate, ValidationStringency.SILENT));
        Assert.assertEquals(lines, List.of());
        Assert.assertEquals(header[0].getValidationErrors().size(), 1);
        Assert.assertEquals(
                header[0].getValidationErrors().get(0).getType(), SAMValidationError.Type.INVALID_DATE_STRING);
    }
}
