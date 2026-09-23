package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import java.io.StringWriter;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SAMTextHeaderCodecTest extends HtsjdkTest {

    private static String encode(final SAMFileHeader header) {
        final StringWriter text = new StringWriter();
        new SAMTextHeaderCodec().encode(text, header);
        return text.toString();
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

    @Test
    public void testCommentWithTabIsWritten() {
        final SAMFileHeader header = new SAMFileHeader();
        header.addComment("key\tvalue");
        Assert.assertTrue(encode(header).endsWith("@CO\tkey\tvalue\n"));
    }
}
