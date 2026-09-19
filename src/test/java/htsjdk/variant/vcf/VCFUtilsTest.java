package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import java.util.*;
import java.util.stream.Collectors;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class VCFUtilsTest extends HtsjdkTest {

    private static VCFHeader headerOfVersion(final String version) {
        return new VCFHeader(VCFHeaderVersion.toHeaderVersion(version), Collections.emptySet(), Collections.emptySet());
    }

    private static VCFHeader merged(final String... versions) {
        final List<VCFHeader> headers = new ArrayList<>();
        for (final String version : versions) {
            headers.add(headerOfVersion(version));
        }
        return new VCFHeader(VCFUtils.smartMergeHeaders(headers, true));
    }

    @Test
    public void mergedHeaderTakesTheHighestVersion() {
        Assert.assertEquals(merged("VCFv4.0", "VCFv4.3").getVCFHeaderVersion(), VCFHeaderVersion.VCF4_3);
        Assert.assertEquals(merged("VCFv4.3", "VCFv4.0").getVCFHeaderVersion(), VCFHeaderVersion.VCF4_3);
        Assert.assertEquals(
                merged("VCFv4.2", "VCFv4.5", "VCFv4.1", "VCFv4.4").getVCFHeaderVersion(), VCFHeaderVersion.VCF4_5);
        Assert.assertEquals(merged("VCFv4.2", "VCFv4.2").getVCFHeaderVersion(), VCFHeaderVersion.VCF4_2);
    }

    @Test
    public void mergedHeaderOfVersionlessHeadersDeclaresNoVersion() {
        final List<VCFHeader> headers = List.of(new VCFHeader(), new VCFHeader());
        Assert.assertNull(new VCFHeader(VCFUtils.smartMergeHeaders(headers, true)).getVCFHeaderVersion());
    }

    @Test
    public void versionlessHeaderDoesNotLowerTheMergedVersion() {
        final List<VCFHeader> headers = List.of(new VCFHeader(), headerOfVersion("VCFv4.3"));
        Assert.assertEquals(
                new VCFHeader(VCFUtils.smartMergeHeaders(headers, true)).getVCFHeaderVersion(),
                VCFHeaderVersion.VCF4_3);
    }

    @Test
    public void mergedLinesCarryExactlyOneVersionLineFirst() {
        final Set<VCFHeaderLine> lines =
                VCFUtils.smartMergeHeaders(List.of(headerOfVersion("VCFv4.1"), headerOfVersion("VCFv4.3")), true);
        final List<VCFHeaderLine> versionLines = lines.stream()
                .filter(line -> VCFHeaderVersion.isFormatString(line.getKey()))
                .collect(Collectors.toList());
        Assert.assertEquals(versionLines.size(), 1);
        Assert.assertEquals(versionLines.get(0).getValue(), "VCFv4.3");
        Assert.assertEquals(lines.iterator().next(), versionLines.get(0));
    }

    @DataProvider(name = "caseIntolerantDoubles")
    public Object[][] getCaseIntolerantDoubles() {
        return new Object[][] {
            {Double.NaN, Arrays.asList("NaN", "nan", "+nan", "-nan")},
            {
                Double.POSITIVE_INFINITY,
                Arrays.asList("+Infinity", "+infinity", "+Inf", "+inf", "Infinity", "infinity", "Inf", "inf")
            },
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

    @Test
    public void rebuildingAVersionlessHeaderDoesNotChangeWhatAMergeDeclares() {
        final VCFHeader versionless = new VCFHeader(Collections.singleton(new VCFHeaderLine("source", "test")));
        final VCFHeader rebuilt = new VCFHeader(versionless.getMetaDataInInputOrder());
        final VCFHeader v40 = new VCFHeader(VCFHeaderVersion.VCF4_0, Collections.emptySet(), Collections.emptySet());
        Assert.assertEquals(
                new VCFHeader(VCFUtils.smartMergeHeaders(List.of(versionless, v40), false)).getVCFHeaderVersion(),
                VCFHeaderVersion.VCF4_0);
        Assert.assertEquals(
                new VCFHeader(VCFUtils.smartMergeHeaders(List.of(rebuilt, v40), false)).getVCFHeaderVersion(),
                VCFHeaderVersion.VCF4_0);
    }
}
