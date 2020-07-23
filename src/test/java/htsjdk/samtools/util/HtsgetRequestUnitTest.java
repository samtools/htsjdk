package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.htsget.HtsgetClass;
import htsjdk.samtools.util.htsget.HtsgetFormat;
import htsjdk.samtools.util.htsget.HtsgetRequest;
import htsjdk.samtools.util.htsget.HtsgetRequestField;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.URISyntaxException;

public class HtsgetRequestUnitTest extends HtsjdkTest {

    private static final String endpoint = "http://localhost:3000/reads/";

    @Test
    public void testOnlyId() throws URISyntaxException {
        final HtsgetRequest req = new HtsgetRequest(new URI(endpoint + 1));
        Assert.assertEquals(req.toURI(), new URI("http://localhost:3000/reads/1"));
    }

    @Test
    public void testBasicFields() throws URISyntaxException {
        final HtsgetRequest req = new HtsgetRequest(new URI(endpoint + 1))
            .withFormat(HtsgetFormat.BAM)
            .withDataClass(HtsgetClass.body)
            .withInterval(new Interval("chr1", 1, 16));
        final String query = req.toURI().toString();
        Assert.assertTrue(query.contains("format=BAM"));
        Assert.assertTrue(query.contains("class=body"));
        Assert.assertTrue(query.contains("referenceName=chr1"));
        Assert.assertTrue(query.contains("start=0"));
        Assert.assertTrue(query.contains("end=16"));
    }

    @Test
    public void testCompositeFields() throws URISyntaxException {
        final HtsgetRequest req = new HtsgetRequest(new URI(endpoint + 1))
            .withField(HtsgetRequestField.QNAME)
            .withField(HtsgetRequestField.FLAG)
            .withTag("tag1")
            .withTag("tag2")
            .withNotag("tag3")
            .withNotag("tag4");
        final String query = req.toURI().toString();
        Assert.assertTrue(query.contains("fields=QNAME,FLAG") || query.contains("fields=FLAG,QNAME"));
        Assert.assertTrue(query.contains("tags=tag1,tag2") || query.contains("tags=tag2,tag1"));
        Assert.assertTrue(query.contains("notags=tag3,tag4") || query.contains("notags=tag4,tag3"));
    }

    @DataProvider(name = "invalidParams")
    public Object[][] invalidParams() throws URISyntaxException {
        return new Object[][]{
            // class=header while interval also specified
            {new HtsgetRequest(new URI(endpoint + 1))
                .withDataClass(HtsgetClass.header)
                .withInterval(new Interval("chr1", 1, 16))},
            // class=header while field also specified
            {new HtsgetRequest(new URI(endpoint + 1))
                .withDataClass(HtsgetClass.header)
                .withField(HtsgetRequestField.QNAME)},
            // class=header while tag also specified
            {new HtsgetRequest(new URI(endpoint + 1))
                .withDataClass(HtsgetClass.header)
                .withTag("NH")},
            // class=header while notag also specified
            {new HtsgetRequest(new URI(endpoint + 1))
                .withDataClass(HtsgetClass.header)
                .withNotag("NH")},
            // tags and notags overlap
            {new HtsgetRequest(new URI(endpoint + 1))
                .withDataClass(HtsgetClass.header)
                .withTag("NH")
                .withNotag("NH")},
            // .bam file requested while format is variant
            {new HtsgetRequest(new URI(endpoint + "example.bam"))
                .withFormat(HtsgetFormat.VCF)
            },
            // .vcf file requested while format is read
            {new HtsgetRequest(new URI(endpoint + "example.vcf"))
                .withFormat(HtsgetFormat.BAM)
            }
        };
    }

    // Expect a validation failure for invalid combinations of query parameters
    @Test(dataProvider = "invalidParams", expectedExceptions = IllegalArgumentException.class)
    public void testValidationFailure(final HtsgetRequest query) {
        query.toURI();
    }
}