package htsjdk.samtools.cram.spec30;

import htsjdk.samtools.SAMRecord;
import java.io.IOException;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests CRAM 3.1 integration files from the hts-specs test suite.
 * All 4 level files should decode to identical content (same as the 3.0 level files).
 */
public class Spec31ComplianceTest extends HtsSpecsComplianceTestBase {

    @Test
    public void testLevel1() throws IOException {
        final List<SAMRecord> records =
                decodeCramFile(SPEC_31_DIR.resolve("level-1.cram").toFile());
        Assert.assertFalse(records.isEmpty(), "3.1 level-1 should have records");
    }

    @Test
    public void testLevel2() throws IOException {
        final List<SAMRecord> records =
                decodeCramFile(SPEC_31_DIR.resolve("level-2.cram").toFile());
        Assert.assertFalse(records.isEmpty(), "3.1 level-2 should have records");
    }

    @Test
    public void testLevel3() throws IOException {
        final List<SAMRecord> records =
                decodeCramFile(SPEC_31_DIR.resolve("level-3.cram").toFile());
        Assert.assertFalse(records.isEmpty(), "3.1 level-3 should have records");
    }

    @Test
    public void testLevel4() throws IOException {
        final List<SAMRecord> records =
                decodeCramFile(SPEC_31_DIR.resolve("level-4.cram").toFile());
        Assert.assertFalse(records.isEmpty(), "3.1 level-4 should have records");
    }

    @Test
    public void testAll31LevelsProduceSameOutput() throws IOException {
        final List<SAMRecord> level1 =
                decodeCramFile(SPEC_31_DIR.resolve("level-1.cram").toFile());
        final List<SAMRecord> level2 =
                decodeCramFile(SPEC_31_DIR.resolve("level-2.cram").toFile());
        final List<SAMRecord> level3 =
                decodeCramFile(SPEC_31_DIR.resolve("level-3.cram").toFile());
        final List<SAMRecord> level4 =
                decodeCramFile(SPEC_31_DIR.resolve("level-4.cram").toFile());
        assertRecordsMatch(level2, level1, "3.1 level-2 vs level-1");
        assertRecordsMatch(level3, level1, "3.1 level-3 vs level-1");
        assertRecordsMatch(level4, level1, "3.1 level-4 vs level-1");
    }

    @Test
    public void test31MatchesSame30Content() throws IOException {
        // 3.1 and 3.0 level files should decode to the same content
        final List<SAMRecord> v30 = decodeCram("level-1");
        final List<SAMRecord> v31 =
                decodeCramFile(SPEC_31_DIR.resolve("level-1.cram").toFile());
        assertRecordsMatch(v31, v30, "3.1 level-1 vs 3.0 level-1");
    }
}
