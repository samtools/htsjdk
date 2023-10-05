package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.htsget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Arrays;

public class HtsgetRequestUnitTest extends HtsjdkTest {

    private static final String endpoint = "http://localhost:3000/reads/";
    private static final URI testRead = URI.create(endpoint + "1");

    @Test
    public void testOnlyId() {
        final HtsgetRequest req = new HtsgetRequest(testRead);
        Assert.assertEquals(req.toURI(), URI.create("http://localhost:3000/reads/1"));
    }

    @Test
    public void testBasicFields() {
        final HtsgetRequest req = new HtsgetRequest(testRead)
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
    public void testCompositeFields() {
        final HtsgetRequest req = new HtsgetRequest(testRead)
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
    public Object[][] invalidParams() {
        return new Object[][]{
            // class=header while interval also specified
            {new HtsgetRequest(testRead)
                .withDataClass(HtsgetClass.header)
                .withInterval(new Interval("chr1", 1, 16))},
            // class=header while field also specified
            {new HtsgetRequest(testRead)
                .withDataClass(HtsgetClass.header)
                .withField(HtsgetRequestField.QNAME)},
            // class=header while tag also specified
            {new HtsgetRequest(testRead)
                .withDataClass(HtsgetClass.header)
                .withTag("NH")},
            // class=header while notag also specified
            {new HtsgetRequest(testRead)
                .withDataClass(HtsgetClass.header)
                .withNotag("NH")},
            // tags and notags overlap
            {new HtsgetRequest(testRead)
                .withDataClass(HtsgetClass.body)
                .withTag("NH")
                .withNotag("NH")},
            // .bam file requested while format is variant
            {new HtsgetRequest(URI.create(endpoint + "example.bam"))
                .withFormat(HtsgetFormat.VCF)
            },
            // .vcf file requested while format is read
            {new HtsgetRequest(URI.create(endpoint + "example.vcf"))
                .withFormat(HtsgetFormat.BAM)
            }
        };
    }

    // Expect a validation failure for invalid combinations of query parameters
    @Test(dataProvider = "invalidParams", expectedExceptions = IllegalArgumentException.class)
    public void testValidationFailure(final HtsgetRequest query) {
        query.validateRequest();
    }

    @Test(dataProvider = "invalidParams", expectedExceptions = IllegalArgumentException.class)
    public void testPOSTValidationFailure(final HtsgetRequest req) {
        new HtsgetPOSTRequest(req).validateRequest();
    }

    @Test
    public void testPOSTjsonBody() {
        final HtsgetPOSTRequest req = new HtsgetPOSTRequest(testRead)
            .withFormat(HtsgetFormat.BAM)
            .withDataClass(HtsgetClass.header)

            .withField(HtsgetRequestField.QNAME)
            .withField(HtsgetRequestField.CIGAR)

            .withTag("tag1")
            .withTag("tag3")
            .withNotag("tag2")

            .withInterval(new Interval("chr1", 1, 16))
            .withInterval(new Interval("chr1", 17, 32))
            .withIntervals(Arrays.asList(
                new Interval("chrM", 1, 16),
                new Interval("chrM", 17, 32))
            );

        final JSONObject postBody = req.queryBody();

        Assert.assertEquals(postBody.getString("format"), "BAM");
        Assert.assertEquals(postBody.getString("class"), "header");

        Assert.assertEqualsNoOrder(
            postBody.getJSONArray("fields").toList().stream().map(Object::toString).toArray(String[]::new),
            new String[]{"QNAME", "CIGAR"}
        );
        Assert.assertEqualsNoOrder(
            postBody.getJSONArray("tags").toList().stream().map(Object::toString).toArray(String[]::new),
            new String[]{"tag1", "tag3"}
        );
        Assert.assertEqualsNoOrder(
            postBody.getJSONArray("notags").toList().stream().map(Object::toString).toArray(String[]::new),
            new String[]{"tag2"}
        );

        final JSONArray expectedRegions = new JSONArray()
            .put(new JSONObject("{referenceName: \"chr1\", start: 0, end: 16}"))
            .put(new JSONObject("{referenceName: \"chr1\", start: 16, end: 32}"))
            .put(new JSONObject("{referenceName: \"chrM\", start: 0, end: 16}"))
            .put(new JSONObject("{referenceName: \"chrM\", start: 16, end: 32}"));

        Assert.assertEqualsNoOrder(
            postBody.getJSONArray("regions").toList(),
            expectedRegions.toList()
        );
    }
}