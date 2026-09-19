package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.VariantBaseTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class VCFAltHeaderLineUnitTest extends VariantBaseTest {

    @Test
    public void anAltLineIsReadInVcf4SyntaxWhateverVersionTheFileDeclares() {
        final VCFAltHeaderLine line =
                new VCFAltHeaderLine("<ID=DEL,Description=\"Deletion\">", VCFHeaderVersion.VCF3_3);
        Assert.assertEquals(line.getID(), "DEL");
        Assert.assertEquals(line.toString(), "ALT=<ID=DEL,Description=\"Deletion\">");
    }

    @Test(expectedExceptions = TribbleException.InvalidHeader.class)
    public void anAltLineWithoutAnIdIsAnInvalidHeader() {
        new VCFAltHeaderLine("<>", VCFHeaderVersion.VCF4_2);
    }

    @Test
    public void anIdHoldingStructureCharactersIsWrittenUnquotedAndReadsBack() {
        final VCFAltHeaderLine line = new VCFAltHeaderLine("<ID=a=b\"c,Description=\"x\">", VCFHeaderVersion.VCF4_3);
        Assert.assertEquals(line.getID(), "a=b\"c");
        Assert.assertEquals(line.toString(), "ALT=<ID=a=b\"c,Description=\"x\">");
        Assert.assertEquals(
                new VCFAltHeaderLine(line.toString().substring("ALT=".length()), VCFHeaderVersion.VCF4_3), line);
    }
}
