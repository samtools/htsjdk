package htsjdk.samtools.sra;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests for SRAAccession logic
 */
public class SRAAccessionTest extends AbstractSRATest {

    @DataProvider(name = "isValidAccData")
    private Object[][] getIsValidAccData() {
        return new Object[][] {
            { "SRR000123", true },
            { "DRR000001", true },
            { "SRR000000", false },
            { "src/test/resources/htsjdk/samtools/sra/test_archive.sra", true },
            { "src/test/resources/htsjdk/samtools/compressed.bam", false },
            { "src/test/resources/htsjdk/samtools/uncompressed.sam", false },
        };
    }

    @Test(dataProvider = "isValidAccData")
    public void testIsValidAcc(String accession, boolean isValid) {
        Assert.assertEquals(isValid, SRAAccession.isValid(accession));
    }

}
