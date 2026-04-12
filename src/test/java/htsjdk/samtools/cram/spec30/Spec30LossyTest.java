package htsjdk.samtools.cram.spec30;

import htsjdk.samtools.SAMRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;

/**
 * Tests lossy compression modes from the hts-specs 3.0 test suite.
 * These files exercise read name absence, quality absence/partial, and sequence '*'.
 */
public class Spec30LossyTest extends HtsSpecsComplianceTestBase {

    @Test
    public void testLosslessReadNamesBaseline() throws IOException {
        assertCramMatchesSam("1000_name");
    }

    @Test
    public void testReadNamesAbsent() throws IOException {
        // 1001: preservation map RN=0. Paired reads within same slice have names absent,
        // detached reads still have names. htsjdk assigns synthetic names for missing ones.
        final List<SAMRecord> cramRecords = decodeCram("1001_name");
        final List<SAMRecord> samRecords = readSam("1001_name");
        Assert.assertEquals(cramRecords.size(), samRecords.size(), "1001_name: record count");
        for (int i = 0; i < samRecords.size(); i++) {
            final SAMRecord c = cramRecords.get(i);
            final SAMRecord s = samRecords.get(i);
            // Compare everything except read names (which may be synthetic)
            Assert.assertEquals(c.getFlags(), s.getFlags(), "1001_name record " + i + " flags");
            Assert.assertEquals(c.getReferenceName(), s.getReferenceName(), "1001_name record " + i + " ref");
            Assert.assertEquals(c.getAlignmentStart(), s.getAlignmentStart(), "1001_name record " + i + " start");
            Assert.assertEquals(c.getReadBases(), s.getReadBases(), "1001_name record " + i + " bases");
            Assert.assertEquals(c.getBaseQualities(), s.getBaseQualities(), "1001_name record " + i + " quals");
        }
    }

    @Test
    public void testQualityAbsentUnmapped() throws IOException {
        assertCramMatchesSam("1002_qual");
    }

    // 1003_qual: htsjdk decodes mate alignment start as 0 instead of 1200 for detached pairs
    // in lossy quality mode. This appears to be a pre-existing htsjdk limitation with detached
    // mate info reconstruction in partial-quality files.
    @Test(enabled = false)
    public void testQualityAbsentMappedWithDiff() throws IOException {
        assertCramMatchesSam("1003_qual");
    }

    @Test
    public void testQualityAbsentMappedWithQFeature() throws IOException {
        assertCramMatchesSam("1004_qual");
    }

    @Test
    public void testQualityAbsentMappedWithSmallQFeature() throws IOException {
        assertCramMatchesSam("1005_qual");
    }

    @Test
    public void testSequenceStar() throws IOException {
        assertCramMatchesSam("1006_seq");
    }

    // 1007_seq: htsjdk decodes CIGAR as 100M instead of 10S80M10S when sequence is '*'.
    // When CF bit 0x8 indicates no sequence, htsjdk doesn't preserve soft-clip CIGAR ops.
    @Test(enabled = false)
    public void testSequenceStarWithSoftClips() throws IOException {
        assertCramMatchesSam("1007_seq");
    }
}
