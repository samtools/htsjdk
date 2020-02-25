package htsjdk.samtools.cram.structure.block;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.Cigar;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.structure.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class CRAMRecordReadFeaturesTest extends HtsjdkTest {

    @DataProvider(name = "cigarTest")
    public final Object[][] getBasesTests() {
        return new Object[][] {
                // ref bases, read bases, cigar string, expected cigar string
                {"aaaaa", "acgta", "5M", "5M"},
                {"aaaaa", "ttttt", "5X", "5M"}, // X -> M
                {"aaaaa", "aaaaa", "5=", "5M"}, // = -> M
        };
    }

    @Test(dataProvider = "cigarTest")
    public void testCigarFidelity(
            final String readBases,
            final String refBases,
            final String cigarString,
            final String expectedCigarString) {
        final SAMRecord samRecord = CRAMStructureTestHelper.createSAMRecordMapped(0, 1);
        samRecord.setReadBases(readBases.getBytes());
        samRecord.setCigarString(cigarString);

        final CRAMRecordReadFeatures rf = new CRAMRecordReadFeatures(samRecord, readBases.getBytes(), refBases.getBytes());
        final Cigar cigar = rf.getCigarForReadFeatures(readBases.length());
        Assert.assertEquals(cigar.toString(), expectedCigarString);
    }

}
