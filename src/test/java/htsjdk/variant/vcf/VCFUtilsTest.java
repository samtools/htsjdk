package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;

public class VCFUtilsTest extends HtsjdkTest {

    @DataProvider(name="validHeaderVersionMerger")
    public Object[][] validHeaderMergerVersions() {
        // v4.3 can only merge with v4.3, all other version mergers are allowed
        return new Object[][] {
                {Arrays.asList("VCFv4.0", "VCFv4.0")},
                {Arrays.asList("VCFv4.1", "VCFv4.1")},
                {Arrays.asList("VCFv4.2", "VCFv4.2")},
                {Arrays.asList("VCFv4.3", "VCFv4.3")},
                {Arrays.asList("VCFv4.2", "VCFv4.2")},
                {Arrays.asList("VCFv4.2", "VCFv4.2", "VCFv4.2")},
        };
    }

    @DataProvider(name="invalidHeaderVersionMerger")
    public Object[][] invalidHeaderVersionMerger() {
        // v4.3 can only merge with v4.3, all other version mergers are allowed
        return new Object[][] {
                {Arrays.asList("VCFv4.0", "VCFv4.3")},
                {Arrays.asList("VCFv4.1", "VCFv4.3")},
                {Arrays.asList("VCFv4.2", "VCFv4.3")},
                {Arrays.asList("VCFv4.0", "VCFv4.0", "VCFv4.2", "VCFv4.3")},
                {Arrays.asList("VCFv4.3", "VCFv4.0", "VCFv4.1", "VCFv4.2")},
        };
    }

    @Test(dataProvider="validHeaderVersionMerger")
    public void testValidHeaderVersionMerger(final List<String> headerVersions) {
        final List<VCFHeader> headersToMerge = new ArrayList<>(headerVersions.size());
        headerVersions.forEach(hv -> headersToMerge.add(
                new VCFHeader(VCFHeaderVersion.toHeaderVersion(hv), Collections.emptySet(), Collections.emptySet()))
        );
        final Set<VCFHeaderLine> resultHeaders = VCFUtils.smartMergeHeaders(headersToMerge, true);
    }

    @Test(dataProvider="invalidHeaderVersionMerger", expectedExceptions = IllegalArgumentException.class)
    public void testInvalidHeaderVersionMerger(final List<String> headerVersions) {
        final List<VCFHeader> headersToMerge = new ArrayList<>(headerVersions.size());
        headerVersions.forEach(hv -> headersToMerge.add(
                new VCFHeader(VCFHeaderVersion.toHeaderVersion(hv), Collections.emptySet(), Collections.emptySet()))
        );
        VCFUtils.smartMergeHeaders(headersToMerge, true);
    }

}
