package htsjdk.samtools.util;
import java.io.IOException;
import java.net.URL;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import htsjdk.HtsjdkTest;

public class HttpUtilsTest extends HtsjdkTest {
    @DataProvider(name = "existing_urls")
    public Object[][] testExistingURLsData() {
        return new Object[][]{
            {"http://broadinstitute.github.io/picard/testdata/index_test.bam"},
            {"http://ftp.1000genomes.ebi.ac.uk/vol1/ftp/current.tree"}
        };
    }

    @Test(dataProvider="existing_urls")
    public void testGetHeaderField(final String url) throws IOException {
        final String field = HttpUtils.getHeaderField(new URL(url), "Content-Length");
        Assert.assertNotNull(field);
        final long length = Long.parseLong(field);
        Assert.assertTrue(length > 0L);
    }

    @Test(dataProvider="existing_urls")
    public void testGetETag(final String url) throws IOException {
        final String field = HttpUtils.getETag(new URL(url));
        Assert.assertNotNull(field);
    }
}
