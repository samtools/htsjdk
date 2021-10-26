package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.TribbleException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;

public class VCFUtilsTest extends HtsjdkTest {

    @DataProvider(name="validHeaderVersionMerger")
    public Object[][] validHeaderMergerVersions() {

        // header version must be at least v4.2 to merge, result is always highest version
        return new Object[][] {
                // headers to merge, expected result version
                {Arrays.asList("VCFv4.2", "VCFv4.2"), VCFHeaderVersion.VCF4_2},
                {Arrays.asList("VCFv4.3", "VCFv4.3"), VCFHeaderVersion.VCF4_3},
                {Arrays.asList("VCFv4.2", "VCFv4.3"), VCFHeaderVersion.VCF4_3},
                {Arrays.asList("VCFv4.3", "VCFv4.2"), VCFHeaderVersion.VCF4_3},
                {Arrays.asList("VCFv4.2", "VCFv4.2"), VCFHeaderVersion.VCF4_2 },
                {Arrays.asList("VCFv4.2", "VCFv4.2", "VCFv4.3"), VCFHeaderVersion.VCF4_3},
                {Arrays.asList("VCFv4.3", "VCFv4.3", "VCFv4.2"), VCFHeaderVersion.VCF4_3},
                {Arrays.asList("VCFv4.3", "VCFv4.2", "VCFv4.3"), VCFHeaderVersion.VCF4_3},
        };
    }

    @DataProvider(name="invalidHeaderVersionMerger")
    public Object[][] invalidHeaderVersionMerger() {
        // header version must be at least v4.2 to merge
        return new Object[][] {
                {Arrays.asList("VCFv4.0", "VCFv4.2")},
                {Arrays.asList("VCFv4.1", "VCFv4.2")},
                {Arrays.asList("VCFv4.0", "VCFv4.1", "VCFv4.2", "VCFv4.3")},
                {Arrays.asList("VCFv4.3", "VCFv4.2", "VCFv4.1", "VCFv4.0")},
        };
    }

    @Test(dataProvider="validHeaderVersionMerger")
    public void testValidHeaderVersionMerger(final List<String> headerVersions, final VCFHeaderVersion expectedVersion) {
        final Set<VCFHeaderLine> mergedHeaderLines = doHeaderMerge(headerVersions);

        final VCFMetaDataLines metaDataLines = new VCFMetaDataLines();
        metaDataLines.addMetaDataLines(mergedHeaderLines);
        final VCFHeaderLine versionLine = metaDataLines.getExistingFileFormatLine();
        Assert.assertEquals(VCFHeaderVersion.toHeaderVersion(versionLine.getValue()), expectedVersion);
    }

    @Test(dataProvider="invalidHeaderVersionMerger", expectedExceptions = TribbleException.class)
    public void testInvalidHeaderVersionMerger(final List<String> headerVersions) {
        doHeaderMerge(headerVersions);
    }

    private Set<VCFHeaderLine> doHeaderMerge(final List<String> headerVersions) {
        final List<VCFHeader> headersToMerge = new ArrayList<>(headerVersions.size());
        headerVersions.forEach(hv -> headersToMerge.add(
                new VCFHeader(
                        VCFHeader.makeHeaderVersionLineSet(VCFHeaderVersion.toHeaderVersion(hv)),
                        Collections.emptySet()))
        );
        return VCFUtils.smartMergeHeaders(headersToMerge, true);
    }

    @DataProvider(name = "caseIntolerantDoubles")
    public Object[][] getCaseIntolerantDoubles() {
        return new Object[][]{
                {Double.NaN, Arrays.asList("NaN", "nan", "+nan", "-nan")},
                {Double.POSITIVE_INFINITY, Arrays.asList("+Infinity", "+infinity", "+Inf", "+inf", "Infinity", "infinity", "Inf", "inf")},
                {Double.NEGATIVE_INFINITY, Arrays.asList("-Infinity", "-infinity", "-Inf", "-inf")},
                {null, Arrays.asList("znan", "nanz", "zinf", "infz", "hello")},
        };
    }

    @Test(dataProvider = "caseIntolerantDoubles")
    public void testCaseIntolerantDoubles(Double value, final List<String> stringDoubles) {
        stringDoubles.forEach(sd -> {
                try {
                    Assert.assertEquals(VCFUtils.parseVcfDouble(sd), value);
                } catch (NumberFormatException e) {
                    Assert.assertNull(value);
                }
        });
    }

}
