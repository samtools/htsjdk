package htsjdk.beta.plugin.bundle;

import htsjdk.HtsjdkTest;
import htsjdk.io.HtsPath;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class BundleResourceTest extends HtsjdkTest {

    @DataProvider(name="inputOutputTestData")
    public Object[][] getInputOutputTestData() {
        return new Object[][]{
                // bundle resource, isInput, isOutput
                { BundleResourceTestData.readsWithFormat, true, true},
                { new InputStreamResource(BundleResourceTestData.fakeInputStream, "displayName", BundleResourceType.ALIGNED_READS), true, false},
                { new OutputStreamResource(BundleResourceTestData.fakeOutputStream, "displayName", BundleResourceType.ALIGNED_READS), false, true},
        };
    }

    @Test(dataProvider = "inputOutputTestData")
    public void testIsInputOutput(final BundleResource resource, final boolean expectedIsInput, final boolean expectedIsOutput) {
        Assert.assertEquals(resource.hasInputType(), expectedIsInput);
        Assert.assertEquals(resource.hasOutputType(), expectedIsOutput);
    }

    @DataProvider(name="resourceEqualityTestData")
    public Object[][] getResourceEqualityTestData() {
        return new Object[][]{

                // equal
                {
                        BundleResourceTestData.readsWithFormat,
                        BundleResourceTestData.readsWithFormat,
                        true
                },
                {
                        // force two separate IOPath instances for which == is false
                        new IOPathResource(
                                BundleResourceTestData.READS_FILE,
                                BundleResourceType.ALIGNED_READS,
                                BundleResourceType.READS_BAM),
                        new IOPathResource(
                                new HtsPath(BundleResourceTestData.READS_FILE.getRawInputString()),
                                BundleResourceType.ALIGNED_READS,
                                BundleResourceType.READS_BAM),
                        true
                },
                {
                        BundleResourceTestData.readsNoFormat,
                        BundleResourceTestData.readsNoFormat,
                        true
                },
                {
                        new IOPathResource(BundleResourceTestData.READS_FILE, BundleResourceType.ALIGNED_READS),
                        new IOPathResource(BundleResourceTestData.READS_FILE, BundleResourceType.ALIGNED_READS),
                        true
                },
                {
                        new InputStreamResource(BundleResourceTestData.fakeInputStream, "displayName",
                                BundleResourceType.ALIGNED_READS),
                        new InputStreamResource(BundleResourceTestData.fakeInputStream, "displayName",
                                BundleResourceType.ALIGNED_READS),
                        true
                },
                {
                        new OutputStreamResource(BundleResourceTestData.fakeOutputStream, "displayName",
                                BundleResourceType.ALIGNED_READS),
                        new OutputStreamResource(BundleResourceTestData.fakeOutputStream, "displayName",
                                BundleResourceType.ALIGNED_READS),
                        true
                },

                // not equal
                {
                        new IOPathResource(BundleResourceTestData.READS_FILE, BundleResourceType.ALIGNED_READS),
                        new IOPathResource(BundleResourceTestData.READS_FILE, "NOTREADS"),
                        false
                },
                {
                        BundleResourceTestData.readsWithFormat,
                        BundleResourceTestData.readsNoFormat,
                        false
                },
                {
                        BundleResourceTestData.indexWithFormat,
                        BundleResourceTestData.readsNoFormat,
                        false
                },

                // not equal inputstreams
                {
                        new InputStreamResource(BundleResourceTestData.fakeInputStream, "displayName",
                                BundleResourceType.ALIGNED_READS),
                        new InputStreamResource(BundleResourceTestData.fakeInputStream, "differentDisplayName",
                                BundleResourceType.ALIGNED_READS),
                        false
                },
                {
                        new InputStreamResource(BundleResourceTestData.fakeInputStream, "displayName",
                                BundleResourceType.ALIGNED_READS, BundleResourceType.READS_BAM),
                        new InputStreamResource(BundleResourceTestData.fakeInputStream, "displayName",
                                BundleResourceType.ALIGNED_READS),
                        false
                },
                {
                        new InputStreamResource(BundleResourceTestData.fakeInputStream, "displayName",
                                BundleResourceType.ALIGNED_READS, BundleResourceType.READS_BAM),
                        new InputStreamResource(BundleResourceTestData.fakeInputStream, "displayName",
                                BundleResourceType.ALIGNED_READS, BundleResourceType.READS_CRAM),
                        false
                },
                {
                        new InputStreamResource(BundleResourceTestData.fakeInputStream, "displayName",
                                BundleResourceType.ALIGNED_READS),
                        new InputStreamResource(BundleResourceTestData.fakeInputStream, "displayName",
                                BundleResourceType.VARIANT_CONTEXTS),
                        false
                },

                // not equal outputstreams
                {
                        new OutputStreamResource(BundleResourceTestData.fakeOutputStream, "displayName",
                                BundleResourceType.ALIGNED_READS),
                        new OutputStreamResource(BundleResourceTestData.fakeOutputStream, "differentDisplayName",
                                BundleResourceType.ALIGNED_READS),
                        false
                },
                {
                        new OutputStreamResource(BundleResourceTestData.fakeOutputStream, "displayName",
                                BundleResourceType.ALIGNED_READS, BundleResourceType.READS_BAM),
                        new OutputStreamResource(BundleResourceTestData.fakeOutputStream, "displayName",
                                BundleResourceType.ALIGNED_READS),
                        false
                },
                {
                        new OutputStreamResource(BundleResourceTestData.fakeOutputStream, "displayName",
                                BundleResourceType.ALIGNED_READS, BundleResourceType.READS_BAM),
                        new OutputStreamResource(BundleResourceTestData.fakeOutputStream, "displayName",
                                BundleResourceType.ALIGNED_READS, BundleResourceType.READS_CRAM),
                        false
                },
                {
                        new OutputStreamResource(BundleResourceTestData.fakeOutputStream, "displayName",
                                BundleResourceType.ALIGNED_READS),
                        new OutputStreamResource(BundleResourceTestData.fakeOutputStream, "displayName",
                                BundleResourceType.VARIANT_CONTEXTS),
                        false
                },
        };
    }

    @Test(dataProvider="resourceEqualityTestData")
    public void testInputResourceEquality(
            final BundleResource inputResource1,
            final BundleResource inputResource2,
            final boolean expectedEquals) {
        Assert.assertEquals(inputResource1.equals(inputResource2), expectedEquals);
        Assert.assertEquals(inputResource2.equals(inputResource1), expectedEquals);
    }

    @DataProvider(name="toStringTestData")
    public Object[][] getToStringTestData() {
        return new Object[][]{
                {BundleResourceTestData.readsWithFormat, "IOPathResource (file://myreads.bam): ALIGNED_READS/BAM"},
                {BundleResourceTestData.readsNoFormat, "IOPathResource (file://myreads.bam): ALIGNED_READS/NONE"},
                {BundleResourceTestData.indexNoFormat, "IOPathResource (file://myreads.bai): READS_INDEX/NONE"},
                {BundleResourceTestData.indexWithFormat, "IOPathResource (file://myreads.bai): READS_INDEX/BAI"},
                {new InputStreamResource(BundleResourceTestData.fakeInputStream, "displayName", BundleResourceType.ALIGNED_READS),
                        "InputStreamResource (displayName): ALIGNED_READS/NONE"},
                {new InputStreamResource(BundleResourceTestData.fakeInputStream, "displayName", BundleResourceType.ALIGNED_READS, BundleResourceType.READS_BAM),
                        "InputStreamResource (displayName): ALIGNED_READS/BAM"},
                {new OutputStreamResource(BundleResourceTestData.fakeOutputStream, "displayName", BundleResourceType.ALIGNED_READS),
                        "OutputStreamResource (displayName): ALIGNED_READS/NONE"},
                {new OutputStreamResource(BundleResourceTestData.fakeOutputStream, "displayName", BundleResourceType.ALIGNED_READS, BundleResourceType.READS_BAM),
                        "OutputStreamResource (displayName): ALIGNED_READS/BAM"},
        };
    }

    @Test(dataProvider = "toStringTestData")
    public void testToString(final BundleResource resource, final String expectedString) {
        Assert.assertTrue(resource.toString().contains(expectedString));
    }

}
