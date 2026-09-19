package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.VariantBaseTest;
import java.util.List;
import org.testng.annotations.Test;

public class VCFSimpleHeaderLineUnitTest extends VariantBaseTest {

    @Test(expectedExceptions = TribbleException.InvalidHeader.class)
    public void aLineReadFromAFileWithoutAnIdIsAnInvalidHeader() {
        new VCFSimpleHeaderLine("<Description=\"x\">", VCFHeaderVersion.VCF4_2, "FILTER", List.of("ID", "Description"));
    }

    @Test(expectedExceptions = TribbleException.InvalidHeader.class)
    public void aFilterLineWithoutAnIdIsAnInvalidHeader() {
        new VCFFilterHeaderLine("<Description=\"x\">", VCFHeaderVersion.VCF4_2);
    }
}
