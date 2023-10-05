package htsjdk.beta.io.bundle;

import htsjdk.HtsjdkTest;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class BundleJSONTest extends HtsjdkTest {
    @DataProvider(name = "roundTripJSON")
    public Object[][] getRoundTripJSON() {
        final IOPathResource CUSTOM_RESOURCE =
                new IOPathResource(new HtsPath("file://myreads.CUSTOM"),"CUSTOM");

        return new Object[][]{
                // NOTE that these JSON strings contain the resources in the same order as they're serialized by
                // mjson so that we can validate conversions in both directions.
                //
                // The strings need to contain path strings that are full URIs, since that's what the JSON
                // serializer for bundles uses when serializing IOPaths as JSON.

                // json string, primary key, corresponding array of resources

                {
                        "{\n" +
                                "  \"schemaName\":\"htsbundle\",\n" +
                                "  \"schemaVersion\":\"0.1.0\",\n" +
                                "  \"primary\":\"ALIGNED_READS\",\n" +
                                "  \"ALIGNED_READS\":{\"path\":\"" +
                                getURIStringFromIOPath(BundleResourceTestData.readsWithFormat) +
                                "\",\"format\":\"BAM\"}\n" +
                                "}\n",
                        BundleResourceType.ALIGNED_READS,
                        Arrays.asList(BundleResourceTestData.readsWithFormat)
                },
                {
                        "{\n" +
                                "  \"schemaName\":\"htsbundle\",\n" +
                                "  \"schemaVersion\":\"0.1.0\",\n" +
                                "  \"primary\":\"ALIGNED_READS\",\n" +
                                "  \"ALIGNED_READS\":{\"path\":\"" +
                                getURIStringFromIOPath(BundleResourceTestData.readsNoFormat) + "\"}\n" +
                                "}\n",
                        BundleResourceType.ALIGNED_READS,
                        Arrays.asList(BundleResourceTestData.readsNoFormat)
                },
                {
                        "{\n" +
                                "  \"schemaName\":\"htsbundle\",\n" +
                                "  \"schemaVersion\":\"0.1.0\",\n" +
                                "  \"primary\":\"ALIGNED_READS\",\n" +
                                "  \"READS_INDEX\":{\"path\":\"" +
                                getURIStringFromIOPath(BundleResourceTestData.indexWithFormat) +
                                "\",\"format\":\"BAI\"},\n" +
                                "  \"ALIGNED_READS\":{\"path\":\"" +
                                getURIStringFromIOPath(BundleResourceTestData.readsWithFormat) +
                                "\",\"format\":\"BAM\"}\n" +
                                "}\n",
                        BundleResourceType.ALIGNED_READS,
                        Arrays.asList(
                                BundleResourceTestData.readsWithFormat,
                                BundleResourceTestData.indexWithFormat)
                },
                {
                        "{\n" +
                                "  \"schemaName\":\"htsbundle\",\n" +
                                "  \"schemaVersion\":\"0.1.0\",\n" +
                                "  \"primary\":\"ALIGNED_READS\",\n" +
                                "  \"READS_INDEX\":{\"path\":\"" +
                                getURIStringFromIOPath(BundleResourceTestData.indexWithFormat) +
                                "\",\"format\":\"BAI\"},\n" +
                                "  \"ALIGNED_READS\":{\"path\":\"" +
                                getURIStringFromIOPath(BundleResourceTestData.readsNoFormat) +
                                "\"}\n" +
                                "}\n",
                        BundleResourceType.ALIGNED_READS,
                        Arrays.asList(
                                BundleResourceTestData.readsNoFormat,
                                BundleResourceTestData.indexWithFormat)
                },
                {
                        "{\n" +
                                "  \"schemaName\":\"htsbundle\",\n" +
                                "  \"schemaVersion\":\"0.1.0\",\n" +
                                "  \"primary\":\"ALIGNED_READS\",\n" +
                                "  \"READS_INDEX\":{\"path\":\"" +
                                getURIStringFromIOPath(BundleResourceTestData.indexNoFormat) +
                                "\"},\n" +
                                "  \"ALIGNED_READS\":{\"path\":\"" +
                                getURIStringFromIOPath(BundleResourceTestData.readsWithFormat) +
                                "\",\"format\":\"BAM\"}\n" +
                                "}\n",
                        BundleResourceType.ALIGNED_READS,
                        Arrays.asList(
                                BundleResourceTestData.readsWithFormat,
                                BundleResourceTestData.indexNoFormat) },
                {
                        "{\n" +
                                "  \"schemaName\":\"htsbundle\",\n" +
                                "  \"schemaVersion\":\"0.1.0\",\n" +
                                "  \"primary\":\"ALIGNED_READS\",\n" +
                                "  \"READS_INDEX\":{\"path\":\"" +
                                getURIStringFromIOPath(BundleResourceTestData.indexNoFormat) + "\"},\n" +
                                "  \"ALIGNED_READS\":{\"path\":\"" +
                                getURIStringFromIOPath(BundleResourceTestData.readsNoFormat) + "\"}\n" +
                                "}\n",
                        BundleResourceType.ALIGNED_READS,
                        Arrays.asList(
                                BundleResourceTestData.readsNoFormat,
                                BundleResourceTestData.indexNoFormat)
                },

                // bundle with a single resource that has a custom content type
                {
                        "{\n" +
                                "  \"schemaName\":\"htsbundle\",\n" +
                                "  \"schemaVersion\":\"0.1.0\",\n" +
                                "  \"primary\":\"CUSTOM\",\n" +
                                "  \"CUSTOM\":{\"path\":\"" + getURIStringFromIOPath(CUSTOM_RESOURCE) + "\"}\n" +
                                "}\n",
                        "CUSTOM",
                        Arrays.asList(CUSTOM_RESOURCE)
                },

                // three resources, one of which is a custom content type
                {
                        "{\n" +
                                "  \"schemaName\":\"htsbundle\",\n" +
                                "  \"schemaVersion\":\"0.1.0\",\n" +
                                "  \"primary\":\"ALIGNED_READS\",\n" +
                                "  \"READS_INDEX\":{\"path\":\"" +
                                getURIStringFromIOPath(BundleResourceTestData.indexNoFormat) +
                                "\"},\n" +
                                "  \"ALIGNED_READS\":{\"path\":\"" +
                                getURIStringFromIOPath(BundleResourceTestData.readsNoFormat) +
                                "\"},\n" +
                                "  \"CUSTOM\":{\"path\":\"" + getURIStringFromIOPath(CUSTOM_RESOURCE) + "\"}\n" +
                                "}\n",
                        BundleResourceType.ALIGNED_READS,
                        Arrays.asList(
                                BundleResourceTestData.readsNoFormat,
                                BundleResourceTestData.indexNoFormat,
                                CUSTOM_RESOURCE)
                },
        };
    }

    @Test(dataProvider = "roundTripJSON")
    public void testRoundTripJSON(final String jsonString, final String primaryKey, final List<BundleResource> resources) {
        final Bundle bundleFromResources = new Bundle(primaryKey, resources);
        final String actualJSONString = BundleJSON.toJSON(bundleFromResources);
        Assert.assertEquals(actualJSONString, new JSONObject(jsonString).toString(1));

        // now recreate the bundle from JSON
        final Bundle bundleFromJSON = BundleJSON.toBundle(jsonString);

        Assert.assertNotNull(bundleFromJSON);
        Assert.assertEquals(bundleFromJSON.getPrimaryContentType(), primaryKey);

        resources.forEach(expectedResource -> {
            final Optional<BundleResource> jsonResource = bundleFromJSON.get(expectedResource.getContentType());
            Assert.assertTrue(jsonResource.isPresent());
            Assert.assertEquals(jsonResource.get(), expectedResource);
        });
    }

    @Test(dataProvider = "roundTripJSON")
    public void testFromJSONValidWithPathOverride(final String jsonString, final String primaryKey, final List<BundleResource> expectedResources) {
        final Bundle bundleFromJSON = BundleJSON.toBundle(jsonString, BundleResourceTestData.CustomHtsPath::new);
        Assert.assertNotNull(bundleFromJSON);
        Assert.assertEquals(bundleFromJSON.getPrimaryContentType(), primaryKey);
        expectedResources.forEach(expectedResource -> {
            final Optional<BundleResource> jsonResource = bundleFromJSON.get(expectedResource.getContentType());
            Assert.assertTrue(jsonResource.isPresent());
            //NOTE: we don't test the individual resources for equality here, since the expected resources
            // don't have a custom path type, so a resource equality test would fail because the HtsPath
            // equality test would fail. Instead we just verify that the classes resulting from JSON serialization
            // use our custom HtsPath-derived class.
            final IOPathResource ioPathResource = ((IOPathResource) jsonResource.get());
            Assert.assertTrue(ioPathResource.getIOPath().isPresent());
            final IOPath ioPath = ioPathResource.getIOPath().get();
            Assert.assertEquals(ioPath.getClass().getSimpleName(), BundleResourceTestData.CustomHtsPath.class.getSimpleName());
            // typecast just to make sure
            final BundleResourceTestData.CustomHtsPath subClass = (BundleResourceTestData.CustomHtsPath) ioPath;
        });
    }

    @DataProvider(name = "invalidBundleJSON")
    public Object[][] getInvalidBundleJSON() {
        return new Object[][]{
                { null, "cannot be null" },
                { "", "The string is empty" },

                // missing schema name
                { "{}" , "missing the required property schemaName" },

                // still missing schema name
                { "{\"schemaVersion\":\"0.1.0\"}", "missing the required property schemaName" },

                // incorrect schema name
                { "{\"schemaName\":\"bogusname\", \"schemaVersion\":\"0.1.0\"}", "Expected bundle schema name" },

                // missing schema version
                { "{\"schemaName\":\"htsbundle\"}", "missing the required property schemaVersion" },

                // incorrect schema version
                { "{\"schemaName\":\"htsbundle\", \"schemaVersion\":\"99.99.99\"}", "Expected bundle schema version" },

                // missing primary property
                { "{\"schemaVersion\":\"0.1.0\",\"schemaName\":\"htsbundle\",\"ALIGNED_READS\":{\"path\":\"myreads" +
                        ".bam\",\"format\":\"BAM\"}}",
                        "missing the required property primary"},

                // primary property is present, but the resource it specifies is not in the bundle
                { "{\"schemaVersion\":\"0.1.0\",\"schemaName\":\"htsbundle\",\"ALIGNED_READS\":{\"path\":\"myreads" +
                        ".bam\",\"format\":\"BAM\"},\"primary\":\"MISSING_RESOURCE\"}",
                        "not present in the bundle's resources"},

                // syntax error (missing quote in before schemaName
                { "{\"schemaVersion\":\"0.1.0\",schemaName\":\"htsbundle\",\"ALIGNED_READS\":{\"path\":\"myreads" +
                        ".bam\",\"format\":\"BAM\"},\"primary\":\"ALIGNED_READS\"}",
                        "Expected a ':' after a key at 36" },
                // no enclosing {} -> UnsupportedOperationException (no text message)
                {"\"schemaName\":\"htsbundle\", \"schemaVersion\":\"0.1.0\"",
                        "", },
        };
    }

    @Test(dataProvider = "invalidBundleJSON", expectedExceptions = IllegalArgumentException.class)
    public void testFromJSONInvalid(final String jsonString, final String expectedMessageFragment) {
        try {
            BundleJSON.toBundle(jsonString);
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains(expectedMessageFragment));
            throw e;
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testToJSONNonIOPath() throws IOException {
        try (final InputStream is = new ByteArrayInputStream(new byte[0])) {
            final Bundle bundle = new BundleBuilder()
                    .addPrimary(new InputStreamResource(is,"displayName","contentType"))
                    .build();
            // can't serialize a resource that isn't backed by an IOPath
            BundleJSON.toJSON(bundle);
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("Bundle resource requires a valid path to be serialized"));
            throw e;
        }
    }

    // get the URI String from an IOPath in an IOPath resource
    final private String getURIStringFromIOPath(final IOPathResource resource) {
        return resource.getIOPath().get().getURIString();
    }

}
