package htsjdk.samtools.cram.spec30;

import htsjdk.samtools.SAMRecord;
import java.io.IOException;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

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

    // Records 2-4 in 1003_qual.sam have FLAG=0 (not paired) but PNEXT=1200 and TLEN=300.
    // CRAM doesn't store mate info for non-paired records, so PNEXT/TLEN are normalized to 0
    // on decode. This is expected CRAM behavior, not a bug. Compare with relaxed mate fields.
    @Test
    public void testQualityAbsentMappedWithDiff() throws IOException {
        final List<SAMRecord> cramRecords = decodeCram("1003_qual");
        final List<SAMRecord> samRecords = readSam("1003_qual");
        Assert.assertEquals(cramRecords.size(), samRecords.size(), "1003_qual: record count");
        for (int i = 0; i < samRecords.size(); i++) {
            final SAMRecord c = cramRecords.get(i);
            final SAMRecord s = samRecords.get(i);
            Assert.assertEquals(c.getReadName(), s.getReadName(), "1003_qual record " + i + " name");
            Assert.assertEquals(c.getFlags(), s.getFlags(), "1003_qual record " + i + " flags");
            Assert.assertEquals(c.getReferenceName(), s.getReferenceName(), "1003_qual record " + i + " ref");
            Assert.assertEquals(c.getAlignmentStart(), s.getAlignmentStart(), "1003_qual record " + i + " start");
            Assert.assertEquals(c.getCigarString(), s.getCigarString(), "1003_qual record " + i + " cigar");
            Assert.assertEquals(c.getReadBases(), s.getReadBases(), "1003_qual record " + i + " bases");
            // Paired records (FLAG 0x1 set) should preserve mate info
            if (c.getReadPairedFlag()) {
                Assert.assertEquals(
                        c.getMateReferenceName(), s.getMateReferenceName(), "1003_qual record " + i + " mateRef");
                Assert.assertEquals(
                        c.getMateAlignmentStart(), s.getMateAlignmentStart(), "1003_qual record " + i + " mateStart");
                Assert.assertEquals(
                        c.getInferredInsertSize(), s.getInferredInsertSize(), "1003_qual record " + i + " tlen");
            }
        }
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

    @Test
    public void testSequenceStarWithSoftClips() throws IOException {
        assertCramMatchesSam("1007_seq");
    }
}
