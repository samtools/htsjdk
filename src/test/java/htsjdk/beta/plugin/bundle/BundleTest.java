package htsjdk.beta.plugin.bundle;

import htsjdk.HtsjdkTest;
import htsjdk.io.HtsPath;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

// Example JSON :
//{
// "schemaName":"htsbundle",
// "schemaVersion":"0.1.0",
// "READS",
// "READS_INDEX":{"path":"myFile.bai","subtype":"NONE"},
// "READS":{"path":"myFile.bam","subtype":"NONE"}
// }

public class BundleTest extends HtsjdkTest {

    @Test
    public void testPrimaryResource() {
        final String primaryKey = BundleResourceType.READS;
        final IOPathResource ioPathResource = new IOPathResource(
                new HtsPath("somefile.bam"),
                BundleResourceType.READS);
        final Bundle bundle = new Bundle(primaryKey, Collections.singletonList(ioPathResource));
        Assert.assertEquals(bundle.getPrimaryContentType(), primaryKey);
        Assert.assertEquals(bundle.getPrimaryResource(), ioPathResource);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNullPrimaryResource() {
        new Bundle(null, Collections.singletonList(
                new IOPathResource(new HtsPath("somefile.bam"), BundleResourceType.READS)));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testPrimaryResourceNotInBundle() {
        // the primary resource is specified but the resource specified is not in the bundle
        final String primaryKey = "MISSING_RESOURCE";
        final IOPathResource ioPathResource = new IOPathResource(
                new HtsPath("somefile.bam"),
                BundleResourceType.READS);
        try {
            new Bundle(primaryKey, Collections.singletonList(ioPathResource));
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("not present in the resource list"));
            throw e;
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testDuplicateResource() {
        final String primaryKey = BundleResourceType.READS;
        final IOPathResource ioPathResource = new IOPathResource(
                new HtsPath("somefile.bam"),
                BundleResourceType.READS);
        try {
            new Bundle(primaryKey, Arrays.asList(ioPathResource, ioPathResource));
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("Attempt to add a duplicate resource"));
            throw e;
        }
    }

    @Test
    public void testResourceIterator() {
        final Bundle bundle =
                new BundleBuilder()
                        .addPrimary(BundleResourceTestData.readsWithContentSubType)
                        .addSecondary(BundleResourceTestData.indexNoContentSubType)
                        .build();
        final Iterator<BundleResource> it = bundle.iterator();
        while (it.hasNext()) {
            final BundleResource ir = it.next();
            if (ir.getContentType().equals(BundleResourceType.READS)) {
                Assert.assertEquals(ir, BundleResourceTestData.readsWithContentSubType);
            } else {
                Assert.assertEquals(ir, BundleResourceTestData.indexNoContentSubType);
            }
        }
    }

}