package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.util.ParsingUtils;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class VCFEncoderTest extends HtsjdkTest {

	@DataProvider(name = "VCFWriterDoubleFormatTestData")
	public Object[][] makeVCFWriterDoubleFormatTestData() {
		final List<Object[]> tests = new ArrayList<Object[]>();
        // New spec test cases
        tests.add(new Object[]{0.0, "0.0"});
        tests.add(new Object[]{1.0, "1.0"});
        tests.add(new Object[]{1.12345, "1.12345"});
        tests.add(new Object[]{1.33333333333, "1.33333"});
        tests.add(new Object[]{0.1, "0.1"});
        tests.add(new Object[]{0.01, "0.01"});
        tests.add(new Object[]{0.001, "0.001"});
        tests.add(new Object[]{0.0001, "0.0001"});
        tests.add(new Object[]{0.00001, "0.00001"});
        tests.add(new Object[]{0.000001, "1.00000e-06"});
        tests.add(new Object[]{0.50000001, "0.5"});
        tests.add(new Object[]{0.012345, "1.23450e-02"});
        tests.add(new Object[]{0.0033333333, "3.33333e-03"});
		// Previous test cases
		tests.add(new Object[]{1.0, "1.0"});
		tests.add(new Object[]{10.1, "10.1"});
		tests.add(new Object[]{10.01, "10.01"});
		tests.add(new Object[]{10.012, "10.012"});
		tests.add(new Object[]{10.015, "10.015"});
		tests.add(new Object[]{0.0, "0.0"});
		tests.add(new Object[]{0.5, "0.5"});
		tests.add(new Object[]{0.55, "0.55"});
		tests.add(new Object[]{0.555, "0.555"});
		tests.add(new Object[]{0.5555, "0.5555"});
		tests.add(new Object[]{0.1, "0.1"});
		tests.add(new Object[]{0.050, "0.05"});
		tests.add(new Object[]{0.010, "0.01"});
		tests.add(new Object[]{0.012, "1.20000e-02"});
		tests.add(new Object[]{0.0012, "1.20000e-03"});
		tests.add(new Object[]{1.2e-4, "1.20000e-04"});
		tests.add(new Object[]{1.21e-4, "1.21000e-04"});
		tests.add(new Object[]{1.212e-5, "1.21200e-05"});
		tests.add(new Object[]{1.2123e-6, "1.21230e-06"});
		tests.add(new Object[]{Double.POSITIVE_INFINITY, "Infinity"});
		tests.add(new Object[]{Double.NEGATIVE_INFINITY, "-Infinity"});
		tests.add(new Object[]{Double.NaN, "NaN"});
        return tests.toArray(new Object[][]{});
	}

	@Test(dataProvider = "VCFWriterDoubleFormatTestData")
	public void testVCFWriterDoubleFormatTestData(final double d, final String expected) {
		Assert.assertEquals(VCFEncoder.formatVCFDouble(d), expected, "Failed to pretty print double in VCFWriter");
	}

    @DataProvider(name = "MissingFormatTestData")
    public Object[][] makeMissingFormatTestData() {
        final VCFHeader header = createSyntheticHeader(Arrays.asList("Sample1"));

        final VCFEncoder dropMissing = new VCFEncoder(header, false, false);
        final VCFEncoder keepMissing = new VCFEncoder(header, false, true);
        final VariantContextBuilder baseVC = new VariantContextBuilder().chr("1").start(1).stop(1).noID().passFilters().log10PError(1).alleles("A", "C");
        final GenotypeBuilder baseGT = new GenotypeBuilder("Sample1").alleles(Arrays.asList(Allele.NO_CALL, Allele.NO_CALL));
        final Map<Allele, String> alleleMap = new HashMap<Allele, String>(3);
        final List<String> formatKeys = Arrays.asList("GT", "AA", "BB");
        alleleMap.put(Allele.NO_CALL, VCFConstants.EMPTY_ALLELE);
        alleleMap.put(Allele.create("A", true), "0");
        alleleMap.put(Allele.create("C", false), "1");

        final List<Object[]> tests = new ArrayList<Object[]>();

        VariantContext vc = baseVC.genotypes(baseGT.attribute("AA", "a").make()).make();
        tests.add(new Object[]{dropMissing, vc, "./.:a", alleleMap, formatKeys});
        tests.add(new Object[]{keepMissing, vc, "./.:a:.", alleleMap, formatKeys});
        baseGT.noAttributes();

        vc = baseVC.genotypes(baseGT.attribute("AA", "a").attribute("BB", 2).make()).make();
        tests.add(new Object[]{dropMissing, vc, "./.:a:2", alleleMap, formatKeys});
        tests.add(new Object[]{keepMissing, vc, "./.:a:2", alleleMap, formatKeys});
        baseGT.noAttributes();

        vc = baseVC.genotypes(baseGT.make()).make();
        tests.add(new Object[]{dropMissing, vc, "./.", alleleMap, formatKeys});
        tests.add(new Object[]{keepMissing, vc, "./.:.:.", alleleMap, formatKeys});
        baseGT.noAttributes();

        vc = baseVC.genotypes(baseGT.attribute("BB", 2).make()).make();
        tests.add(new Object[]{dropMissing, vc, "./.:.:2", alleleMap, formatKeys});
        tests.add(new Object[]{keepMissing, vc, "./.:.:2", alleleMap, formatKeys});
        baseGT.noAttributes();

        return tests.toArray(new Object[][]{});
    }

    @Test(dataProvider = "MissingFormatTestData")
    public void testMissingFormatFields(final VCFEncoder encoder, final VariantContext vc, final String expectedLastColumn, final Map<Allele, String> alleleMap, final List<String> genotypeFormatKeys) {
        final StringBuilder sb = new StringBuilder();
        final String[] columns = new String[5];

        encoder.addGenotypeData(vc, alleleMap, genotypeFormatKeys, sb);
        final int nCol = ParsingUtils.split(sb.toString(), columns, VCFConstants.FIELD_SEPARATOR_CHAR);
        Assert.assertEquals(columns[nCol-1], expectedLastColumn, "Format fields don't handle missing data in the expected way");
    }

    private Set<VCFHeaderLine> createSyntheticMetadata() {
        final Set<VCFHeaderLine> metaData = new TreeSet<VCFHeaderLine>();

        metaData.add(new VCFContigHeaderLine(Collections.singletonMap("ID", "1"), 0));

        metaData.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "x"));
        metaData.add(new VCFFormatHeaderLine("AA", 1, VCFHeaderLineType.String, "aa"));
        metaData.add(new VCFFormatHeaderLine("BB", 1, VCFHeaderLineType.Integer, "bb"));
        return metaData;
    }

    private VCFHeader createSyntheticHeader() {
        return new VCFHeader(createSyntheticMetadata());
    }

    private VCFHeader createSyntheticHeader(final List<String> samples) {
        return new VCFHeader(createSyntheticMetadata(), samples);
    }


}
