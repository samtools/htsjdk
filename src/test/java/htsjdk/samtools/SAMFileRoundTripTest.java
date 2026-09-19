package htsjdk.samtools;

import htsjdk.*;
import htsjdk.samtools.util.*;
import org.testng.*;
import org.testng.annotations.*;
import java.io.*;

public class SAMFileRoundTripTest extends HtsjdkTest{
    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools");

    @DataProvider(name = "Utf8PositiveTestCases")
    public Object[][] Utf8PositiveTestCases() {
        SAMProgramRecord programRecordRoundTrip = new SAMProgramRecord("33");
        programRecordRoundTrip.setAttribute("CL","xy");
        programRecordRoundTrip.setAttribute("DS","description");

        SAMProgramRecord programRecordRoundTripUtf8 = new SAMProgramRecord("33");
        programRecordRoundTripUtf8.setAttribute("CL","äカ");
        programRecordRoundTripUtf8.setAttribute("DS","\uD83D\uDE00リ");

        SAMSequenceRecord sequenceRecordRoundTrip = new SAMSequenceRecord("chr3",101);
        sequenceRecordRoundTrip.setAttribute("DS","descriptionhere");
        sequenceRecordRoundTrip.setSequenceIndex(2);

        SAMSequenceRecord sequenceRecordRoundTripUtf8 = new SAMSequenceRecord("chr3",101);
        sequenceRecordRoundTripUtf8.setAttribute("DS","Emoji\uD83D\uDE0A");
        sequenceRecordRoundTripUtf8.setSequenceIndex(2);

        return new Object[][]{
                {"roundtrip.sam", programRecordRoundTrip, "@CO\tcomment here", sequenceRecordRoundTrip},
                {"roundtrip_with_utf8.sam", programRecordRoundTripUtf8, "@CO\tKanjiアメリカ\uD83D\uDE00リä", sequenceRecordRoundTripUtf8}
        };
    }

    @Test(dataProvider = "Utf8PositiveTestCases", description = "Test UTF-8 encoding present in permitted fields of a SAM file")
    public void Utf8RoundTripPositiveTests(final String inputFile, SAMProgramRecord programRecord,final String commentRecord, SAMSequenceRecord sequenceRecord) throws Exception {
        final File input = new File(TEST_DATA_DIR, inputFile);
        final File outputFile = File.createTempFile("roundtrip-utf8-out", ".sam");
        outputFile.delete();
        outputFile.deleteOnExit();
        final SAMFileWriterFactory factory = new SAMFileWriterFactory();
        try (SamReader reader = SamReaderFactory.makeDefault().open(input);
             SAMFileWriter writer = factory.makeSAMWriter(reader.getFileHeader(), false, new FileOutputStream(outputFile))) {
            for (SAMRecord rec : reader) {
                writer.addAlignment(rec);
            }
            SAMFileHeader head = reader.getFileHeader();
            Assert.assertEquals(head.getProgramRecords().get(0), programRecord);
            Assert.assertEquals(head.getComments().get(0),commentRecord );
            Assert.assertEquals(head.getSequence("chr3"),sequenceRecord);
        }

        final String originalsam;
        try (InputStream is = new FileInputStream(input)) {
            originalsam = IOUtil.readFully(is);
        }

        final String writtenSam;
        try (InputStream is = new FileInputStream(outputFile)) {
            writtenSam = IOUtil.readFully(is);
        }

        Assert.assertEquals(writtenSam, originalsam);
    }

    @DataProvider(name = "Utf8NegativeTestCases")
    public Object[][] Utf8NegativeTestCases() {
        return new Object[][]{
                {"roundtrip_with_utf8_bad_1.sam", "Invalid character in read bases"},
                {"roundtrip_with_utf8_bad_2.sam", "Non-numeric value in POS column"},
                {"roundtrip_with_utf8_bad_2.sam", "Non-numeric value in POS column"}
        };
    }

    @Test(dataProvider = "Utf8NegativeTestCases",description = "Test UTF-8 encoding present in unpermitted fields of a SAM file", expectedExceptions = {IllegalArgumentException.class, SAMFormatException.class })
    public void Utf8RoundTripNegativeTest(final String inputFile,final String exceptionString) throws Exception {
        final File input = new File(TEST_DATA_DIR, inputFile);
        final File outputFile = File.createTempFile("roundtrip-utf8-out", ".sam");
        outputFile.delete();
        outputFile.deleteOnExit();
        final SAMFileWriterFactory factory = new SAMFileWriterFactory();
        try (SamReader reader = SamReaderFactory.makeDefault().open(input);
             SAMFileWriter writer = factory.makeSAMWriter(reader.getFileHeader(), false, new FileOutputStream(outputFile))) {
            for (SAMRecord rec : reader) {
                writer.addAlignment(rec);
            }
        }
        catch (final Exception ex) {
            Assert.assertTrue(ex.getMessage().contains(exceptionString));
            throw ex;
        }
    }
}