package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.VariantBaseTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class VCF43ValidatorTest extends VariantBaseTest {

    // TODO: enable tests with keys containing '=' once parser properly supports them
    private static final VCFContigHeaderLine CONTIG_STARTING_WITH_ASTERISK =
        new VCFContigHeaderLine("<ID=*aa>", VCFHeaderVersion.VCF4_3, VCFHeader.CONTIG_KEY, 0);
//    private static final VCFContigHeaderLine CONTIG_STARTING_WITH_EQUALS =
//        new VCFContigHeaderLine("<ID==aa>", VCFHeaderVersion.VCF4_3, VCFHeader.CONTIG_KEY, 0);
    private static final VCFContigHeaderLine CONTIG_CONTAINING_INVALID_CHARCTERS =
        new VCFContigHeaderLine("<ID=\\\"\"`’()[]{}>", VCFHeaderVersion.VCF4_3, VCFHeader.CONTIG_KEY, 0);

    @DataProvider(name = "invalidContigHeaderLines")
    public Object[][] invalidContigHeaderLines() {
        return new Object[][]{
            {CONTIG_STARTING_WITH_ASTERISK},
//            {CONTIG_STARTING_WITH_EQUALS},
            {CONTIG_CONTAINING_INVALID_CHARCTERS},
        };
    }

    @Test(dataProvider = "invalidContigHeaderLines", expectedExceptions = {TribbleException.class})
    public void testInvalidContigHeaderLines(final VCFContigHeaderLine line) {
        VCF43Validator.validate(line);
    }

    private static final VCFContigHeaderLine CONTIG_CONTAINING_ASTERISK =
        new VCFContigHeaderLine("<ID=a*>", VCFHeaderVersion.VCF4_3, VCFHeader.CONTIG_KEY, 0);
    //    private static final VCFContigHeaderLine CONTIG_CONTAINING_EQUALS =
//        new VCFContigHeaderLine("<ID=a=>", VCFHeaderVersion.VCF4_3, VCFHeader.CONTIG_KEY, 0);
    // TODO: test '=' once parser supports it
    private static final VCFContigHeaderLine CONTIG_WITH_STRANGE_CHARACTERS =
        new VCFContigHeaderLine("<ID=09AZaz!#$%&*+./:;?@^_|~->", VCFHeaderVersion.VCF4_3, VCFHeader.CONTIG_KEY, 0);

    @DataProvider(name = "validContigHeaderLines")
    public Object[][] validContigHeaderLines() {
        return new Object[][]{
            {CONTIG_CONTAINING_ASTERISK},
//            {CONTIG_CONTAINING_EQUALS},
            {CONTIG_WITH_STRANGE_CHARACTERS},
        };
    }

    @Test(dataProvider = "validContigHeaderLines")
    public void testValidContigHeaderLines(final VCFContigHeaderLine line) {
        VCF43Validator.validate(line);
    }

    private static final VCFCompoundHeaderLine INFO_STARTING_WITH_NUMBER =
        new VCFInfoHeaderLine("0a", 1, VCFHeaderLineType.Integer, "Starting with number");
    private static final VCFCompoundHeaderLine INFO_STARTING_WITH_PERIOD =
        new VCFInfoHeaderLine(".a", 1, VCFHeaderLineType.Integer, "Starting with period");

    @DataProvider(name = "invalidInfoHeaderLines")
    public Object[][] invalidInfoHeaderLines() {
        return new Object[][]{
            {INFO_STARTING_WITH_NUMBER},
            {INFO_STARTING_WITH_PERIOD},
        };
    }

    @Test(dataProvider = "invalidInfoHeaderLines", expectedExceptions = {TribbleException.class})
    public void testInvalidInfoHeaderLines(final VCFInfoHeaderLine line) {
        VCF43Validator.validate(line);
    }

    private static final VCFCompoundHeaderLine VALID_INFO =
        new VCFInfoHeaderLine("_.AZaz09", 1, VCFHeaderLineType.Integer, "Valid info line");
    private static final VCFCompoundHeaderLine THOUSAND_GENOMES =
        new VCFInfoHeaderLine(VCFConstants.THOUSAND_GENOMES_KEY, 1, VCFHeaderLineType.Integer, "Thousand genomes key");

    @DataProvider(name = "validInfoHeaderLines")
    public Object[][] validInfoHeaderLines() {
        return new Object[][]{
            {VALID_INFO},
            {THOUSAND_GENOMES},
        };
    }

    @Test(dataProvider = "validInfoHeaderLines")
    public void testValidInfoHeaderLines(final VCFCompoundHeaderLine line) {
        VCF43Validator.validate(line);
    }

    private static final VCFSimpleHeaderLine EMPTY_ID =
        new VCFSimpleHeaderLine("ALT", "", "Empty ID");
    private static final VCFSimpleHeaderLine V4_2_PEDIGREE =
        new VCFSimpleHeaderLine(VCFConstants.PEDIGREE_HEADER_KEY, "Pedigree", "4.2 pedigree");

    @DataProvider(name = "invalidSimpleHeaderLines")
    public Object[][] invalidSimpleHeaderLines() {
        return new Object[][]{
            {EMPTY_ID},
            {V4_2_PEDIGREE},
        };
    }

    @Test(dataProvider = "invalidSimpleHeaderLines", expectedExceptions = {TribbleException.class})
    public void testInvalidSimpleHeaderLines(final VCFSimpleHeaderLine line) {
        VCF43Validator.validate(line);
    }

    private static final VCFSimpleHeaderLine VALID_SIMPLE_HEADER_LINE =
        new VCFSimpleHeaderLine("ALT", "id", "Has ID");
    private static final VCFSimpleHeaderLine V4_3_PEDIGREE =
        new VCFPedigreeHeaderLine("<ID=pedigree>", VCFHeaderVersion.VCF4_3);

    @DataProvider(name = "validSimpleHeaderLines")
    public Object[][] validSimpleHeaderLines() {
        return new Object[][]{
            {VALID_SIMPLE_HEADER_LINE},
            {V4_3_PEDIGREE},
        };
    }

    @Test(dataProvider = "validSimpleHeaderLines")
    public void testValidSimpleHeaderLines(final VCFSimpleHeaderLine line) {
        VCF43Validator.validate(line);
    }
}
