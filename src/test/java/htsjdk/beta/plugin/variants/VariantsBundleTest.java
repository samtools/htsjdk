package htsjdk.beta.plugin.variants;

import htsjdk.HtsjdkTest;
import htsjdk.beta.io.IOPathUtils;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleBuilder;
import htsjdk.beta.io.bundle.BundleJSON;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.io.bundle.IOPathResource;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class VariantsBundleTest extends HtsjdkTest {

    private final static String VCF_FILE = "a.vcf";
    private final static String VCF_INDEX_FILE = "a.vcf.idx";

    @Test
    public void testVariantsBundleVCFOnly() {
        final IOPath variantsPath = new HtsPath(VCF_FILE);
        final VariantsBundle<IOPath> variantsBundle = new VariantsBundle<>(variantsPath);

        Assert.assertTrue(variantsBundle.getVariants().getIOPath().isPresent());
        Assert.assertEquals(variantsBundle.getVariants().getIOPath().get(), variantsPath);
        Assert.assertFalse(variantsBundle.getIndex().isPresent());
    }

    @Test
    public void testVariantsBundleVCFWithIndex() {
        final IOPath variantsPath = new HtsPath(VCF_FILE);
        final IOPath indexPath = new HtsPath(VCF_INDEX_FILE);
        final VariantsBundle<IOPath> variantsBundle = new VariantsBundle<>(variantsPath, indexPath);

        Assert.assertTrue(variantsBundle.getVariants().getIOPath().isPresent());
        Assert.assertEquals(variantsBundle.getVariants().getIOPath().get(), variantsPath);

        Assert.assertTrue(variantsBundle.getIndex().isPresent());
        Assert.assertTrue(variantsBundle.getIndex().get().getIOPath().isPresent());
        final IOPath actualIndexPath = variantsBundle.getIndex().get().getIOPath().get();
        Assert.assertEquals(actualIndexPath, indexPath);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNoVCFInSerializedBundle() {
        final String vcfJSON = """
            {
                "schemaVersion":"0.1.0",
                "schemaName":"htsbundle",
                "ALIGNED_READS":{"path":"my.cram","format":"READS_CRAM"},
                "primary":"ALIGNED_READS"
            }
            """.formatted();
        try {
            VariantsBundle.getVariantsBundleFromString(vcfJSON);
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("not present in the bundle's resources"));
            throw e;
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNoVCFInResources() {
        final Bundle bundleWithNoVariants = new BundleBuilder()
                .addPrimary(new IOPathResource(new HtsPath("notVariants.txt"), "NOT_READS"))
                .addSecondary(new IOPathResource(new HtsPath("alsoNotVariants.txt"), "ALSO_NOT_READS"))
                .build();
        new VariantsBundle<>(bundleWithNoVariants.getResources());
    }

    @DataProvider(name = "roundTripJSONTestData")
    public Object[][] getRoundTripJSONTestData() {
        return new Object[][]{
                // json string, primary key, corresponding array of resources
                {
                    // vcf only, without format included
                    """
                    {
                        "schemaName":"htsbundle",
                        "schemaVersion":"0.1.0",
                        "VARIANT_CONTEXTS":{"path":"%s"},
                        "primary":"VARIANT_CONTEXTS"
                    }
                    """.formatted(VCF_FILE),
                    new VariantsBundle<IOPath>(new HtsPath(VCF_FILE))
                },
                {
                    // vcf only, with format included
                    """
                    {
                        "schemaVersion":"0.1.0",
                        "schemaName":"htsbundle",
                        "VARIANT_CONTEXTS":{"path":"%s","format":"VCF"},
                        "primary":"VARIANT_CONTEXTS"
                    }
                    """.formatted(VCF_FILE),
                    // VariantsBundle doesn't automatically infer format, so create one manually
                    new VariantsBundle(
                            new BundleBuilder().addPrimary(
                                            new IOPathResource(
                                                    new HtsPath(VCF_FILE),
                                                    BundleResourceType.VARIANT_CONTEXTS,
                                                    BundleResourceType.VARIANTS_VCF))
                                    .build().getResources())
                },
                {
                    // vcf with an index, with format included
                    """
                    {
                        "schemaVersion":"0.1.0",
                        "schemaName":"htsbundle",
                        "VARIANT_CONTEXTS":{"path":"%s","format":"VCF"},
                        "VARIANTS_INDEX":{"path":"%s","format":"IDX"},
                        "primary":"VARIANT_CONTEXTS"
                    }
                    """.formatted(VCF_FILE, VCF_INDEX_FILE),
                    // VariantsBundle doesn't automatically infer format, so create one manually
                    new VariantsBundle(
                            new BundleBuilder()
                                    .addPrimary(
                                            new IOPathResource(
                                                    new HtsPath(VCF_FILE),
                                                    BundleResourceType.VARIANT_CONTEXTS,
                                                    BundleResourceType.VARIANTS_VCF))
                                    .addSecondary(
                                            new IOPathResource(
                                                new HtsPath(VCF_INDEX_FILE),
                                                BundleResourceType.VARIANTS_INDEX,
                                                "IDX"))
                                    .build().getResources())
                },
        };
    }

    @Test(dataProvider = "roundTripJSONTestData")
    public void testVariantsWriteRoundTrip(
            final String jsonString,
            final VariantsBundle<IOPath> expectedVariantsBundle) {
        final String bJSON = BundleJSON.toJSON(expectedVariantsBundle);
        final VariantsBundle<IOPath> bundleFromJSON = VariantsBundle.getVariantsBundleFromString(jsonString);
        Assert.assertEquals(bundleFromJSON, expectedVariantsBundle);
        Assert.assertEquals(bundleFromJSON.getPrimaryContentType(), expectedVariantsBundle.getPrimaryContentType());
        Assert.assertTrue(bundleFromJSON.getVariants().getIOPath().isPresent());
        Assert.assertEquals(bundleFromJSON.getVariants().getIOPath().get(), expectedVariantsBundle.getVariants().getIOPath().get());
    }

    @Test(dataProvider = "roundTripJSONTestData")
    public void testGetVariantsBundleFromPath(
            final String jsonString,
            final VariantsBundle<IOPath> expectedVariantsBundle) {
        final IOPath jsonFilePath = IOPathUtils.createTempPath("variants", BundleJSON.BUNDLE_EXTENSION);
        IOPathUtils.writeStringToPath(jsonFilePath, jsonString);
        final VariantsBundle<IOPath> bundleFromPath = VariantsBundle.getVariantsBundleFromPath(jsonFilePath);

        Assert.assertEquals(bundleFromPath, expectedVariantsBundle);
        Assert.assertEquals(bundleFromPath.getPrimaryContentType(), expectedVariantsBundle.getPrimaryContentType());
        Assert.assertTrue(bundleFromPath.getVariants().getIOPath().isPresent());
        Assert.assertEquals(bundleFromPath.getVariants().getIOPath().get(), expectedVariantsBundle.getVariants().getIOPath().get());
    }

}