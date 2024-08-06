package htsjdk.beta.plugin.variants;

import htsjdk.HtsjdkTest;
import htsjdk.beta.io.IOPathUtils;
import htsjdk.beta.io.bundle.*;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Optional;

public class VariantsBundleTest extends HtsjdkTest {

    private final static String VCF_FILE = "a.vcf";
    private final static String VCF_INDEX_FILE = "a.vcf.idx";

    @Test
    public void testVariantsBundleVCFOnly() {
        final IOPath variantsPath = new HtsPath(VCF_FILE);
        final VariantsBundle variantsBundle = new VariantsBundle(variantsPath);

        Assert.assertTrue(variantsBundle.getVariants().getIOPath().isPresent());
        Assert.assertEquals(variantsBundle.getVariants().getIOPath().get(), variantsPath);
        Assert.assertFalse(variantsBundle.getIndex().isPresent());
    }

    @Test
    public void testVariantsBundleVCFWithIndex() {
        final IOPath variantsPath = new HtsPath(VCF_FILE);
        final IOPath indexPath = new HtsPath(VCF_INDEX_FILE);
        final VariantsBundle variantsBundle = new VariantsBundle(variantsPath, indexPath);

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
                    "schemaVersion":"%s",
                    "schemaName":"htsbundle",
                    "%s":{"path":"my.cram","format":"%s"},
                    "primary":"%s"
                }
                """.formatted(
                    BundleJSON.JSON_SCHEMA_VERSION,
                    BundleResourceType.CT_ALIGNED_READS,
                    BundleResourceType.FMT_READS_CRAM,
                    BundleResourceType.CT_ALIGNED_READS
                );
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
                .addPrimary(new IOPathResource(new HtsPath("notVariants.txt"), "NOT_VARIANTS"))
                .addSecondary(new IOPathResource(new HtsPath("alsoNotVariants.txt"), "ALSO_NOT_VARIANTS"))
                .build();
        new VariantsBundle(bundleWithNoVariants.getResources());
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
                                    "schemaVersion":"%s",
                                    "%s":{"path":"%s"},
                                    "primary":"%s"
                                }
                                """.formatted(
                                        BundleJSON.JSON_SCHEMA_VERSION,
                                        BundleResourceType.CT_VARIANT_CONTEXTS,
                                        VCF_FILE,
                                        BundleResourceType.CT_VARIANT_CONTEXTS),
                        new VariantsBundle(new HtsPath(VCF_FILE))
                },
                {
                        // vcf only, with format included
                        """
                                {
                                    "schemaVersion":"%s",
                                    "schemaName":"htsbundle",
                                    "%s":{"path":"%s","format":"%s"},
                                    "primary":"%s"
                                }
                                """.formatted(
                                    BundleJSON.JSON_SCHEMA_VERSION,
                                    BundleResourceType.CT_VARIANT_CONTEXTS, VCF_FILE, BundleResourceType.FMT_VARIANTS_VCF,
                                    BundleResourceType.CT_VARIANT_CONTEXTS),
                        // VariantsBundle doesn't automatically infer format, so create one manually
                        new VariantsBundle(
                                new BundleBuilder().addPrimary(
                                                new IOPathResource(
                                                        new HtsPath(VCF_FILE),
                                                        BundleResourceType.CT_VARIANT_CONTEXTS,
                                                        BundleResourceType.FMT_VARIANTS_VCF))
                                        .build().getResources())
                },
                {
                        // vcf with an index, with format included
                        """
                                {
                                    "schemaVersion":"%s",
                                    "schemaName":"htsbundle",
                                    "%s":{"path":"%s","format":"%s"},
                                    "%s":{"path":"%s"},
                                    "primary":"%s"
                                }
                                """.formatted(
                                    BundleJSON.JSON_SCHEMA_VERSION,
                                    BundleResourceType.CT_VARIANT_CONTEXTS, VCF_FILE, BundleResourceType.FMT_VARIANTS_VCF,
                                    BundleResourceType.CT_VARIANTS_INDEX, VCF_INDEX_FILE,
                                    BundleResourceType.CT_VARIANT_CONTEXTS),
                        // VariantsBundle doesn't automatically infer format, so create one manually
                        new VariantsBundle(
                                new BundleBuilder()
                                        .addPrimary(
                                                new IOPathResource(
                                                        new HtsPath(VCF_FILE),
                                                        BundleResourceType.CT_VARIANT_CONTEXTS,
                                                        BundleResourceType.FMT_VARIANTS_VCF))
                                        .addSecondary(
                                                new IOPathResource(
                                                        new HtsPath(VCF_INDEX_FILE),
                                                        BundleResourceType.CT_VARIANTS_INDEX))
                                        .build().getResources())
                },
        };
    }

    @Test(dataProvider = "roundTripJSONTestData")
    public void testVariantsWriteRoundTrip(
            final String jsonString,
            final VariantsBundle expectedVariantsBundle) {
        final VariantsBundle bundleFromJSON = VariantsBundle.getVariantsBundleFromString(jsonString);
        Assert.assertTrue(Bundle.equalsIgnoreOrder(bundleFromJSON, expectedVariantsBundle));
    }

    @Test(dataProvider = "roundTripJSONTestData")
    public void testGetVariantsBundleFromPath(
            final String jsonString,
            final VariantsBundle expectedVariantsBundle) {
        final IOPath jsonFilePath = IOPathUtils.createTempPath("variants", BundleJSON.BUNDLE_EXTENSION);
        IOPathUtils.writeStringToPath(jsonFilePath, jsonString);
        final VariantsBundle bundleFromPath = VariantsBundle.getVariantsBundleFromPath(jsonFilePath);

        Assert.assertTrue(Bundle.equalsIgnoreOrder(bundleFromPath, expectedVariantsBundle));
        Assert.assertTrue(bundleFromPath.getVariants().getIOPath().isPresent());
        Assert.assertEquals(bundleFromPath.getVariants().getIOPath().get(), expectedVariantsBundle.getVariants().getIOPath().get());
    }

    @DataProvider(name = "resolveIndexTestData")
    public Object[][] getResolveIndexTestData() {
        return new Object[][]{
                {
                        "build/resources/test/htsjdk/tribble/AbstractFeatureReaderTest/baseVariants.vcf.gz",
                        "build/resources/test/htsjdk/tribble/AbstractFeatureReaderTest/baseVariants.vcf.gz.tbi"
                },
                {
                        "build/resources/test/htsjdk/tribble/AbstractFeatureReaderTest/baseVariants.vcf",
                        "build/resources/test/htsjdk/tribble/AbstractFeatureReaderTest/baseVariants.vcf.idx"
                }
        };

    }

    @Test(dataProvider = "resolveIndexTestData")
    public void testResolveIndex(
            final String baseVCF,
            final String expectedIndex) {
        final Optional<IOPath> resolvedIndex = VariantsBundle.resolveIndex(new HtsPath(baseVCF));
        Assert.assertTrue(resolvedIndex.isPresent());
        Assert.assertEquals(resolvedIndex.get(), new HtsPath(expectedIndex));
    }

}