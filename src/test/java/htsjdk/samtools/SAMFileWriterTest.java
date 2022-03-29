package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.CollectionUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class SAMFileWriterTest extends HtsjdkTest {
    @Test public void testWritingPresortedData() throws Exception {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.queryname);
        builder.addFrag("q1", 0, 1000, false);
        builder.addFrag("q2", 0, 1000, false);
        builder.addFrag("q3", 0, 1000, false);
        builder.addFrag("q4", 0, 1000, false);
        builder.addFrag("q5", 0, 1000, false);
        builder.addFrag("q6", 0, 1000, false);
        builder.addFrag("q7", 0, 1000, false);
        builder.addFrag("q8", 0, 1000, false);
        builder.addFrag("q9", 0, 1000, false);
        builder.addFrag("q10", 0, 1000, false);

        // First check that it all fails when writing without turning off checking
        for (final String ext : CollectionUtil.makeList(".sam", ".bam")) {
            final File file = File.createTempFile("test.", ext);
            final SAMFileWriter writer = new SAMFileWriterFactory().makeSAMOrBAMWriter(builder.getHeader(), true, file);

            try {
                builder.forEach(writer::addAlignment);
                writer.close();
                Assert.fail("Should have thrown an exception on out of order records to a " + ext + " file.");
            }
            catch (IllegalArgumentException e) {
                Assert.assertTrue(e.getMessage().contains("Alignments added out of order"));
            }
        }

        // Then test that it succeeds with checking turned off
        for (final String ext : CollectionUtil.makeList(".sam", ".bam")) {
            final File file = File.createTempFile("test.", ext);
            final SAMFileWriter writer = new SAMFileWriterFactory().makeSAMOrBAMWriter(builder.getHeader(), true, file);
            writer.setSortOrderChecking(false);
            builder.forEach(writer::addAlignment);
            writer.close();

            // Read the file back and ensure the records are in the right order
            final SamReader in = SamReaderFactory.makeDefault().open(file);
            final List<SAMRecord> recs = in.iterator().stream().collect(Collectors.toList());
            Assert.assertEquals(recs, builder.getRecords());
            Assert.assertEquals(recs.get(0).getReadName(), "q1");
            Assert.assertEquals(recs.get(9).getReadName(), "q10");
        }
    }
}
