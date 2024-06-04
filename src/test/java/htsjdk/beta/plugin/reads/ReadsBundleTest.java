package htsjdk.beta.plugin.reads;

import htsjdk.HtsjdkTest;
import htsjdk.beta.io.IOPathUtils;
import htsjdk.beta.io.bundle.*;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ReadsBundleTest extends HtsjdkTest {

    private final static String BAM_FILE = "reads.bam";
    private final static String INDEX_FILE = "reads.bai";

    @Test
    public void testReadsBundleReadsOnly() {
        final IOPath readsPath = new HtsPath(BAM_FILE);
        final ReadsBundle<IOPath> readsBundle = new ReadsBundle<>(readsPath);

        Assert.assertTrue(readsBundle.getReads().getIOPath().isPresent());
        Assert.assertEquals(readsBundle.getReads().getIOPath().get(), readsPath);
        Assert.assertFalse(readsBundle.getIndex().isPresent());
    }

    @Test
    public void testReadsBundleReadsAndIndex() {
        final IOPath readsPath = new HtsPath(BAM_FILE);
        final IOPath indexPath = new HtsPath(INDEX_FILE);
        final ReadsBundle<IOPath> readsBundle = new ReadsBundle<>(readsPath, indexPath);

        Assert.assertTrue(readsBundle.getReads().getIOPath().isPresent());
        Assert.assertEquals(readsBundle.getReads().getIOPath().get(), readsPath);

        Assert.assertTrue(readsBundle.getIndex().isPresent());
        Assert.assertTrue(readsBundle.getIndex().get().getIOPath().isPresent());
        final IOPath actualIndexPath = readsBundle.getIndex().get().getIOPath().get();
        Assert.assertEquals(actualIndexPath, indexPath);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNoReadsInSerializedBundle() {
        final String vcfJSON = """
            {
                "schemaVersion":"%s",
                "schemaName":"htsbundle",
                "%s":{"path":"my.vcf","format":"%s"},
                "primary":"%s"
            }"""
                .formatted(
                        BundleJSON.JSON_SCHEMA_VERSION,
                        BundleResourceType.CT_VARIANT_CONTEXTS,
                        BundleResourceType.FMT_VARIANTS_VCF,
                        BundleResourceType.CT_VARIANT_CONTEXTS
                );
        try {
            ReadsBundle.getReadsBundleFromString(vcfJSON);
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("not present in the bundle's resources"));
            throw e;
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNoReadsInResources() {
        final Bundle bundleWithNoReads = new BundleBuilder()
                .addPrimary(new IOPathResource(new HtsPath("notReads.txt"),"NOT_READS"))
                .addSecondary(new IOPathResource(new HtsPath("alsoNotReads.txt"),"ALSO_NOT_READS"))
                .build();
        new ReadsBundle<>(bundleWithNoReads.getResources());
    }

    @DataProvider(name = "roundTripJSONTestData")
    public Object[][] getRoundTripJSONTestData() {
        return new Object[][]{
                //NOTE that these JSON strings contain the resources in the same order that they're serialized by mjson
                // so that we can use these cases to validate in both directions

                // json string, primary key, corresponding array of resources
                {
                    // reads only, without format included
                    """
                    {
                        "schemaVersion":"%s",
                        "schemaName":"htsbundle",
                        "%s":{"path":"%s"},
                        "primary":"%s"
                    }""".formatted(
                            BundleJSON.JSON_SCHEMA_VERSION,
                            BundleResourceType.CT_ALIGNED_READS, BAM_FILE,
                            BundleResourceType.CT_ALIGNED_READS
                    ),
                    new ReadsBundle<IOPath>(new HtsPath(BAM_FILE))
                },
                {
                    // reads only, with format included
                    """
                    {
                        "schemaVersion":"%s",
                        "schemaName":"htsbundle",
                        "%s":{"path":"%s", "format":"%s"},
                        "primary":"%s"
                    }""".formatted(
                            BundleJSON.JSON_SCHEMA_VERSION,
                            BundleResourceType.CT_ALIGNED_READS, BAM_FILE, BundleResourceType.FMT_READS_BAM,
                            BundleResourceType.CT_ALIGNED_READS),
                    // ReadsBundle doesn't automatically infer format, so create one manually
                    new ReadsBundle(
                            new BundleBuilder()
                                    .addPrimary(
                                            new IOPathResource(new HtsPath(BAM_FILE),
                                            BundleResourceType.CT_ALIGNED_READS,
                                            BundleResourceType.FMT_READS_BAM)
                                    ).build().getResources()
                    )
                },
                {
                    // reads with index, with format included
                    """
                    {
                        "schemaVersion":"%s",
                        "schemaName":"htsbundle",
                        "%s":{"path":"%s", "format":"%s"},
                        "%s":{"path":"%s", "format":"%s"},
                        "primary":"%s"
                    }""".formatted(
                            BundleJSON.JSON_SCHEMA_VERSION,
                            BundleResourceType.CT_ALIGNED_READS, BAM_FILE, BundleResourceType.FMT_READS_BAM,
                            BundleResourceType.CT_READS_INDEX, INDEX_FILE, BundleResourceType.FMT_READS_INDEX_BAI,
                            BundleResourceType.CT_ALIGNED_READS
                    ),
                    // ReadsBundle doesn't automatically infer format, so create one manually
                    new ReadsBundle(
                            new BundleBuilder()
                                    .addPrimary(
                                            new IOPathResource(new HtsPath(BAM_FILE),
                                            BundleResourceType.CT_ALIGNED_READS,
                                            BundleResourceType.FMT_READS_BAM)
                                    ).addSecondary(
                                            new IOPathResource(new HtsPath(INDEX_FILE),
                                            BundleResourceType.CT_READS_INDEX,
                                            BundleResourceType.FMT_READS_INDEX_BAI)
                                    ).build().getResources()
                    )
                },
        };
    }

    @Test(dataProvider="roundTripJSONTestData")
    public void testReadWriteRoundTrip(
            final String jsonString,
            final ReadsBundle<IOPath> expectedReadsBundle)  {
        final ReadsBundle<IOPath> bundleFromJSON = ReadsBundle.getReadsBundleFromString(jsonString);
        Assert.assertTrue(Bundle.equalsIgnoreOrder(bundleFromJSON, expectedReadsBundle));
        Assert.assertTrue(bundleFromJSON.getReads().getIOPath().isPresent());
        Assert.assertEquals(bundleFromJSON.getReads().getIOPath().get(), expectedReadsBundle.getReads().getIOPath().get());
    }

    @Test(dataProvider="roundTripJSONTestData")
    public void testGetReadsBundleFromPath(
            final String jsonString,
            final ReadsBundle<IOPath> expectedReadsBundle)  {
        final IOPath jsonFilePath = IOPathUtils.createTempPath("reads", BundleJSON.BUNDLE_EXTENSION);
        IOPathUtils.writeStringToPath(jsonFilePath, jsonString);
        final ReadsBundle<IOPath> bundleFromPath = ReadsBundle.getReadsBundleFromPath(jsonFilePath);

        Assert.assertTrue(Bundle.equalsIgnoreOrder(bundleFromPath, expectedReadsBundle));
        Assert.assertTrue(bundleFromPath.getReads().getIOPath().isPresent());
        Assert.assertEquals(bundleFromPath.getReads().getIOPath().get(), expectedReadsBundle.getReads().getIOPath().get());
    }

}