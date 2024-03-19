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

    final IOPathResource CUSTOM_RESOURCE =
            new IOPathResource(new HtsPath("file://myreads.CUSTOM"),"CUSTOM");

    @DataProvider(name = "singleBundleTestCases")
    public Object[][] getSingleBundleTestCases() {
        return new Object[][]{
                // NOTE that these JSON strings contain the resources in the same order as they're serialized by
                // mjson so that we can validate conversions in both directions.
                //
                // The strings need to contain path strings that are full URIs, since that's what the JSON
                // serializer for bundles uses when serializing IOPaths as JSON.

                // json string, primary key, corresponding array of resources

                {
                    """
                    {
                        "schemaName":"htsbundle",
                        "schemaVersion":"0.1.0",
                        "primary":"ALIGNED_READS",
                        "ALIGNED_READS":{"path":"%s","format":"BAM"}
                    }""".formatted(getURIStringFromIOPath(BundleResourceTestData.readsWithFormat)),
                    BundleResourceType.ALIGNED_READS,
                    Arrays.asList(BundleResourceTestData.readsWithFormat)
                },
                {
                    """
                    {
                        "schemaName":"htsbundle",
                        "schemaVersion":"0.1.0",
                        "primary":"ALIGNED_READS",
                        "ALIGNED_READS":{"path":"%s"}
                    }""".formatted(getURIStringFromIOPath(BundleResourceTestData.readsNoFormat)),
                    BundleResourceType.ALIGNED_READS,
                    Arrays.asList(BundleResourceTestData.readsNoFormat)
                },
                {
                    """
                    {
                        "schemaName":"htsbundle",
                        "schemaVersion":"0.1.0",
                        "primary":"ALIGNED_READS",
                        "READS_INDEX":{"path":"%s","format":"BAI"},
                        "ALIGNED_READS":{"path":"%s","format":"BAM"}
                    }""".formatted(
                            getURIStringFromIOPath(BundleResourceTestData.indexWithFormat),
                            getURIStringFromIOPath(BundleResourceTestData.readsWithFormat)),
                    BundleResourceType.ALIGNED_READS,
                    Arrays.asList(
                            BundleResourceTestData.readsWithFormat,
                            BundleResourceTestData.indexWithFormat)
                },
                {
                    """
                    {
                        "schemaName":"htsbundle",
                        "schemaVersion":"0.1.0",
                        "primary":"ALIGNED_READS",
                        "READS_INDEX":{"path":"%s","format":"BAI"},
                        "ALIGNED_READS":{"path":"%s"}
                    }""".formatted(
                                getURIStringFromIOPath(BundleResourceTestData.indexWithFormat),
                                getURIStringFromIOPath(BundleResourceTestData.readsNoFormat)),
                    BundleResourceType.ALIGNED_READS,
                    Arrays.asList(
                            BundleResourceTestData.readsNoFormat,
                            BundleResourceTestData.indexWithFormat)
                },
                {
                    """
                    {
                        "schemaName":"htsbundle",
                        "schemaVersion":"0.1.0",
                        "primary":"ALIGNED_READS",
                        "READS_INDEX":{"path":"%s"},
                        "ALIGNED_READS":{"path":"%s","format":"BAM"}
                    }""".formatted(
                        getURIStringFromIOPath(BundleResourceTestData.indexNoFormat),
                        getURIStringFromIOPath(BundleResourceTestData.readsWithFormat)),
                    BundleResourceType.ALIGNED_READS,
                    Arrays.asList(
                            BundleResourceTestData.readsWithFormat,
                            BundleResourceTestData.indexNoFormat)
                },
                {
                    """
                    {   
                        "schemaName":"htsbundle",
                        "schemaVersion":"0.1.0",
                        "primary":"ALIGNED_READS",
                        "READS_INDEX":{"path":"%s"},
                        "ALIGNED_READS":{"path":"%s"}
                    }""".formatted(
                            getURIStringFromIOPath(BundleResourceTestData.indexNoFormat),
                            getURIStringFromIOPath(BundleResourceTestData.readsNoFormat)),
                    BundleResourceType.ALIGNED_READS,
                    Arrays.asList(
                            BundleResourceTestData.readsNoFormat,
                            BundleResourceTestData.indexNoFormat)
                },

                // bundle with a single resource that has a custom content type
                {
                    """
                    {   
                        "schemaName":"htsbundle",
                        "schemaVersion":"0.1.0",
                        "primary":"CUSTOM",
                        "CUSTOM":{"path":"%s"}
                    }""".formatted(getURIStringFromIOPath(CUSTOM_RESOURCE)),
                    "CUSTOM",
                    Arrays.asList(CUSTOM_RESOURCE)
                },

                // three resources, one of which is a custom content type
                {
                    """
                    {
                            "schemaName":"htsbundle",
                            "schemaVersion":"0.1.0",
                            "primary":"ALIGNED_READS",
                            "READS_INDEX":{"path":"%s"},
                            "ALIGNED_READS":{"path":"%s"},
                            "CUSTOM":{"path":"%s"}
                    }""".formatted(
                            getURIStringFromIOPath(BundleResourceTestData.indexNoFormat),
                            getURIStringFromIOPath(BundleResourceTestData.readsNoFormat),
                            getURIStringFromIOPath(CUSTOM_RESOURCE)),
                    BundleResourceType.ALIGNED_READS,
                    Arrays.asList(
                            BundleResourceTestData.readsNoFormat,
                            BundleResourceTestData.indexNoFormat,
                            CUSTOM_RESOURCE)
                },
        };
    }

    @Test(dataProvider = "singleBundleTestCases")
    public void testRoundTripJSONSingle(final String jsonString, final String primaryKey, final List<BundleResource> resources) {
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

    @Test(dataProvider = "singleBundleTestCases")
    public void testFromJSONValidWithPathOverride(final String jsonString, final String primaryKey, final List<BundleResource> expectedResources) {
        final Bundle bundleFromJSON = BundleJSON.toBundle(jsonString, BundleResourceTestData.CustomHtsPath::new);
        Assert.assertNotNull(bundleFromJSON);
        Assert.assertEquals(bundleFromJSON.getPrimaryContentType(), primaryKey);
        expectedResources.forEach(expectedResource -> {
            final Optional<BundleResource> jsonResource = bundleFromJSON.get(expectedResource.getContentType());
            Assert.assertTrue(jsonResource.isPresent());
            //NOTE: we don't test the individual resources for equality here, since the expected resources
            // don't have a custom path type, so a resource equality test would fail because the HtsPath
            // equality test would fail. Instead, we just verify that the classes resulting from JSON serialization
            // use our custom HtsPath-derived class.
            final IOPathResource ioPathResource = ((IOPathResource) jsonResource.get());
            Assert.assertTrue(ioPathResource.getIOPath().isPresent());
            final IOPath ioPath = ioPathResource.getIOPath().get();
            Assert.assertEquals(ioPath.getClass().getSimpleName(), BundleResourceTestData.CustomHtsPath.class.getSimpleName());
            // typecast just to make sure
            final BundleResourceTestData.CustomHtsPath subClass = (BundleResourceTestData.CustomHtsPath) ioPath;
        });
    }

    // Make sure that any JSON that is a valid single Bundle can also be parsed as a Bundle collection, so that users
    // can provide an individual bundle to any client that expects a collection of bundles.
    @Test(dataProvider = "singleBundleTestCases")
    public void testAcceptSingleBundleJSONAsCollection(
            final String jsonString,
            final String primaryKey,
            final List<BundleResource> resources) {
        final List<Bundle> expectedBundleCollection = Arrays.asList(new Bundle(primaryKey, resources));
        final List<Bundle> actualBundleCollection = BundleJSON.toBundleList(jsonString);
        Assert.assertEquals(actualBundleCollection, expectedBundleCollection);
    }

    // make sure single rejects collection with > 1
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectMultipleBundlesAsSingleBundle() {
       final String multipleBundles = """
          [{
              "schemaName":"htsbundle",
              "schemaVersion":"0.1.0",
              "primary":"ALIGNED_READS",
              "ALIGNED_READS":{"path":"%s","format":"BAM"}
          },
          {
              "schemaName":"htsbundle",
              "schemaVersion":"0.1.0",
              "primary":"ALIGNED_READS",
              "ALIGNED_READS":{"path":"%s"}
          }]""".formatted(
                        getURIStringFromIOPath(BundleResourceTestData.readsWithFormat),
                        getURIStringFromIOPath(BundleResourceTestData.readsNoFormat)
       );
        BundleJSON.toBundle(multipleBundles);
    }


    @DataProvider(name = "invalidBundleJSON")
    public Object[][] getInvalidBundleJSON() {
        return new Object[][]{
                {null, "cannot be null"},
                {"", "The string is empty"},

                // missing schema name
                {"{}", "missing the required property schemaName"},

                // still missing schema name
                {
                    """
                        {"schemaVersion":"0.1.0"}
                    """,
                    "missing the required property schemaName"
                },

                // incorrect schema name
                {
                    """
                        {"schemaName":"bogusname", "schemaVersion":"0.1.0"}
                    """,
                    "Expected bundle schema name"
                },

                // missing schema version
                {
                    """
                        { "schemaName":"htsbundle" }
                    """,
                    "missing the required property schemaVersion"
                },

                // incorrect schema version
                {
                    """
                        {"schemaName":"htsbundle", "schemaVersion":"99.99.99"}
                    """,
                    "Expected bundle schema version"
                },

                // missing primary property
                {
                    """
                        {
                            "schemaVersion":"0.1.0",
                            "schemaName":"htsbundle",
                            "ALIGNED_READS":{"path":"myreads.bam","format":"BAM"}
                        }
                   """,
                   "missing the required property primary"
                },

                // primary property is present, but the resource it specifies is not in the bundle
                {
                    """
                        {
                            "schemaVersion":"0.1.0",
                            "schemaName":"htsbundle",
                            "ALIGNED_READS":{"path":"myreads.bam","format":"BAM"},
                            "primary":"MISSING_RESOURCE"
                        }
                    """,
                    "not present in the bundle's resources"
                },

                // syntax error (missing quote before schemaName)
                {
                    """
                        {
                            "schemaVersion":"0.1.0",
                            schemaName":"htsbundle",
                            "ALIGNED_READS":{"path":"myreads.bam","format":"BAM"},
                            "primary":"ALIGNED_READS"
                        }
                    """,
                    "Expected a ':' after a key at 58 [character 19 line 3]"
                },

                // missing enclosing {} -> UnsupportedOperationException (no text message)
                {
                    """
                        "schemaName":"htsbundle",
                        "schemaVersion":"0.1.0",
                    """,
                    "A JSONObject text must begin with '{' at 5 [character 6 line 1]",
                },
        };
    }

    @Test(dataProvider = "invalidBundleJSON", expectedExceptions = IllegalArgumentException.class)
    public void testRejectInvalidJSON(final String jsonString, final String expectedMessageFragment) {
        try {
            BundleJSON.toBundle(jsonString);
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains(expectedMessageFragment));
            throw e;
        }
    }

    // make sure that any JSON bundle that is invalid as an individual bundle is also invalid as a bundle collection
    @Test(dataProvider = "invalidBundleJSON", expectedExceptions = IllegalArgumentException.class)
    public void testRejectInvalidJSONAsCollection(final String jsonString, final String expectedMessageFragment) {
        try {
            BundleJSON.toBundleList(jsonString);
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains(expectedMessageFragment));
            throw e;
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectNonIOPathToJSON() throws IOException {
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

    // make sure that a bundle collection that contains one bundle can also be deserialized as a single bundle
    @Test
    public void testJAcceptSONCollectionWithOneBundleAsSingleBundle() {
        final String jsonCollectionWithOneBundle =
                """
                [{
                    "schemaName":"htsbundle",
                    "schemaVersion":"0.1.0",
                    "primary":"ALIGNED_READS",
                    "ALIGNED_READS":{"path":"%s","format":"BAM"}
                }]""".formatted(getURIStringFromIOPath(BundleResourceTestData.readsWithFormat));

        // create a bundle collection from the JSON, make sure it has a valid Bundle
        final Bundle bundleFromJSON = BundleJSON.toBundle(jsonCollectionWithOneBundle);
        Assert.assertEquals(bundleFromJSON.getPrimaryContentType(), BundleResourceType.ALIGNED_READS);
        Assert.assertEquals(bundleFromJSON.getResources().size(), 1);
        Assert.assertEquals(bundleFromJSON.getPrimaryResource(), BundleResourceTestData.readsWithFormat);
    }

    @DataProvider(name = "bundleCollectionTestCases")
    public Object[][] getBundleCollectionTestCases() {

        return new Object[][]{
                // NOTE that these JSON strings contain the resources in the same order as they're serialized by
                // mjson so that we can validate conversions in both directions.
                //
                // The strings need to contain path strings that are full URIs, since that's what the JSON
                // serializer for bundles uses when serializing IOPaths as JSON.

                // json string, corresponding array of bundles
                {
                        // 1 bundle
                        """
                        [{
                            "schemaName":"htsbundle",
                            "schemaVersion":"0.1.0",
                            "primary":"ALIGNED_READS",
                            "ALIGNED_READS":{"path":"%s","format":"BAM"}
                        }]""".formatted(getURIStringFromIOPath(BundleResourceTestData.readsWithFormat)),
                        Arrays.asList(new Bundle( BundleResourceType.ALIGNED_READS, Arrays.asList(BundleResourceTestData.readsWithFormat)))
                },
                {
                        // 2 bundles
                        """
                        [{
                            "schemaName":"htsbundle",
                            "schemaVersion":"0.1.0",
                            "primary":"ALIGNED_READS",
                            "ALIGNED_READS":{"path":"%s","format":"BAM"}
                        },
                        {
                            "schemaName":"htsbundle",
                            "schemaVersion":"0.1.0",
                            "primary":"ALIGNED_READS",
                            "ALIGNED_READS":{"path":"%s"}
                        }]""".formatted(
                                getURIStringFromIOPath(BundleResourceTestData.readsWithFormat),
                                getURIStringFromIOPath(BundleResourceTestData.readsNoFormat)),
                        Arrays.asList(
                                new Bundle(BundleResourceType.ALIGNED_READS, Arrays.asList(BundleResourceTestData.readsWithFormat)),
                                new Bundle(BundleResourceType.ALIGNED_READS, Arrays.asList(BundleResourceTestData.readsNoFormat)))
                },
                {
                        // 3 bundles
                        """
                        [{
                            "schemaName":"htsbundle",
                            "schemaVersion":"0.1.0",
                            "primary":"ALIGNED_READS",
                            "ALIGNED_READS":{"path":"%s","format":"BAM"}
                        },
                        {
                            "schemaName":"htsbundle",
                            "schemaVersion":"0.1.0",
                            "primary":"ALIGNED_READS",
                            "ALIGNED_READS":{"path":"%s"}
                        },
                        {
                            "schemaName":"htsbundle",
                            "schemaVersion":"0.1.0",
                            "primary":"ALIGNED_READS",
                            "ALIGNED_READS":{"path":"%s","format":"BAM"}
                        }]""".formatted(
                                getURIStringFromIOPath(BundleResourceTestData.readsWithFormat),
                                getURIStringFromIOPath(BundleResourceTestData.readsNoFormat),
                                getURIStringFromIOPath(BundleResourceTestData.readsWithFormat)),
                        Arrays.asList(
                                new Bundle(BundleResourceType.ALIGNED_READS, Arrays.asList(BundleResourceTestData.readsWithFormat)),
                                new Bundle(BundleResourceType.ALIGNED_READS, Arrays.asList(BundleResourceTestData.readsNoFormat)),
                                new Bundle(BundleResourceType.ALIGNED_READS, Arrays.asList(BundleResourceTestData.readsWithFormat)))
                }
        };
    }

    @Test(dataProvider = "bundleCollectionTestCases")
    public void testRoundTripJSONCollection(
            final String jsonString,
            final List<Bundle> bundles) {
        // create a bundle collection from the input JSON, make sure if equals the test collection
        final List<Bundle> bundlesFromJSON = BundleJSON.toBundleList(jsonString);
        Assert.assertEquals(bundlesFromJSON, bundles);

        // now write the test collection of bundles to JSON, and then roundtrip it back to a bundle collection
        final String actualJSONString = BundleJSON.toJSON(bundles);
        final List<Bundle> bundlesFromRoundtripJSON = BundleJSON.toBundleList(actualJSONString);
        Assert.assertEquals(bundlesFromRoundtripJSON, bundles);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectEmptyCollection() {
        try {
            BundleJSON.toBundleList("[]");
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("JSON bundle collection must contain at least one bundle"));
            throw e;
        }
    }

    // get the URI String from an IOPath in an IOPath resource
    private String getURIStringFromIOPath(final IOPathResource resource) {
        return resource.getIOPath().get().getURIString();
    }

}
