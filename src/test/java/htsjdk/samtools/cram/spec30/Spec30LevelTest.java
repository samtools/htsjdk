package htsjdk.samtools.cram.spec30;

import htsjdk.samtools.SAMRecord;
import java.io.IOException;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Tests compression level variants from the hts-specs 3.0 test suite.
 * level-1, level-2, and level-4 all encode the same data with different compression settings
 * and should decode to identical records.
 */
public class Spec30LevelTest extends HtsSpecsComplianceTestBase {

    @Test
    public void testLevel1() throws IOException {
        final List<SAMRecord> records = decodeCram("level-1");
        org.testng.Assert.assertFalse(records.isEmpty(), "level-1 should have records");
    }

    @Test
    public void testLevel2() throws IOException {
        final List<SAMRecord> records = decodeCram("level-2");
        org.testng.Assert.assertFalse(records.isEmpty(), "level-2 should have records");
    }

    @Test
    public void testLevel4() throws IOException {
        final List<SAMRecord> records = decodeCram("level-4");
        org.testng.Assert.assertFalse(records.isEmpty(), "level-4 should have records");
    }

    @Test
    public void testAllLevelsProduceSameOutput() throws IOException {
        final List<SAMRecord> level1 = decodeCram("level-1");
        final List<SAMRecord> level2 = decodeCram("level-2");
        final List<SAMRecord> level4 = decodeCram("level-4");
        assertRecordsMatch(level2, level1, "level-2 vs level-1");
        assertRecordsMatch(level4, level1, "level-4 vs level-1");
    }
}
