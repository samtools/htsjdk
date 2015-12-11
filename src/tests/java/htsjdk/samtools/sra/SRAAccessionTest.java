package htsjdk.samtools.sra;

import htsjdk.samtools.sra.SRAAccession;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests for SRAAccession logic
 */
public class SRAAccessionTest {

    @DataProvider(name = "isValidAccData")
    public Object[][] getIsValidAccData() {
        return new Object[][] {
            { "SRR000123", true },
            { "DRR000001", true },
            { "SRR000000", false },
            { "testdata/htsjdk/samtools/sra/test_archive.sra", true },
            { "testdata/htsjdk/samtools/compressed.bam", false },
            { "testdata/htsjdk/samtools/uncompressed.sam", false },
        };
    }

    @Test(dataProvider = "isValidAccData")
    public void testIsValidAcc(String accession, boolean isValid) {
        if (!SRAAccession.isSupported()) return;

        Assert.assertEquals(isValid, SRAAccession.isValid(accession));
    }

}
