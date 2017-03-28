package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class VCFStructuredHeaderLineUnitTest {

    private VCFStructuredHeaderLine getStructuredHeaderLine() {
        return new VCFStructuredHeaderLine(
                "key",
                new LinkedHashMap<String, String>() {{
                    put("ID", "id");
                    put("attr1", "value1");
                    put("attr2", "value2");
                }}
        );
    }

    @Test
    public void testConstructorFromStrings() {
        VCFStructuredHeaderLine hl = new VCFStructuredHeaderLine("testKey", "testId", "test description");
        Assert.assertEquals("testKey", hl.getKey());
        Assert.assertEquals("testId", hl.getID());
        Assert.assertEquals("test description", hl.getGenericFieldValue(VCFStructuredHeaderLine.DESCRIPTION_ATTRIBUTE));
        Assert.assertEquals("testKey=<ID=testId,Description=\"test description\">", hl.toStringEncoding());
    }

    @Test
    public void testConstructorFromEncodedLine() {
        VCFStructuredHeaderLine hLine = new VCFStructuredHeaderLine("key", "<ID=id,attr1=value1>", VCFHeader.DEFAULT_VCF_VERSION);
        Assert.assertEquals(hLine.getKey(), "key");
        Assert.assertEquals(hLine.getID(), "id");
        Assert.assertEquals(hLine.getGenericFieldValue("ID"), "id");
        Assert.assertEquals(hLine.getGenericFieldValue("attr1"), "value1");
    }

    @Test
    public void testConstructorFromAttributeMap() {
        VCFStructuredHeaderLine hLine = new VCFStructuredHeaderLine(
                "key",
                new LinkedHashMap<String, String>() {{
                    put("ID", "id");
                    put("attr1", "value1");
                    put("attr2", "value2");
                }});

        Assert.assertEquals(hLine.getKey(), "key");
        Assert.assertEquals(hLine.getID(), "id");
        Assert.assertEquals(hLine.getGenericFieldValue("ID"), "id");
        Assert.assertEquals(hLine.getGenericFieldValue("attr1"), "value1");
    }

    @Test(expectedExceptions=TribbleException.class)
    public void testRejectIdMissingFromEncodedLine() {
        new VCFStructuredHeaderLine("key", "<attr1=value1>", VCFHeader.DEFAULT_VCF_VERSION);
    }

    @Test(expectedExceptions=TribbleException.class)
    public void testRejectIdMissingFromAttributeMap() {
        new VCFStructuredHeaderLine(
                "key",
                new LinkedHashMap<String, String>() {{
                    put("attr1", "value1");
                    put("attr2", "value2");
                }});
    }

    @DataProvider(name = "violateIDRequirements")
    public Object[][] getViolateIDRequirements() {
        return new Object[][]{
                {"<ID>"},
                {"<ID="},
                {"<ID=\"\""},
                {"<ID>"},
                {"<attr1=value1>"},
                {"<attr1=value1,attr2=value2>"}
        };
    }

    @Test(dataProvider="violateIDRequirements",expectedExceptions=TribbleException.class)
    public void testViolateIDRequirements(final String headerLine) {
        new VCFStructuredHeaderLine("key", headerLine, VCFHeader.DEFAULT_VCF_VERSION);
    }

    @Test
    public void testGetID() {
        Assert.assertEquals(getStructuredHeaderLine().getID(), "id");
    }

    @Test
    public void testIsStructuredHeaderLine() {
        Assert.assertTrue(getStructuredHeaderLine().isStructuredHeaderLine());
    }

    @Test
    public void testGetGenericFieldValue() {
        Assert.assertEquals(getStructuredHeaderLine().getGenericFieldValue("attr1"), "value1");
    }

    @Test
    public void testStringEncoding() {
        final VCFStructuredHeaderLine structuredHL = getStructuredHeaderLine();
        Assert.assertEquals(structuredHL.toStringEncoding(),"key=<ID=id,attr1=value1,attr2=value2>");
    }

    @Test
    public void testUnescapedQuotedStringEncoding() {
        VCFStructuredHeaderLine unescapedHeaderLine =  new VCFStructuredHeaderLine(
                "key",
                new LinkedHashMap<String, String>() {{
                    put("ID", "id");
                    put(VCFStructuredHeaderLine.DESCRIPTION_ATTRIBUTE,
                            "filterName=[ANNOTATION] filterExpression=[ANNOTATION == \"NA\" || ANNOTATION <= 2.0]");
                    put(VCFStructuredHeaderLine.SOURCE_ATTRIBUTE,
                            "filterName=[ANNOTATION] filterExpression=[ANNOTATION == \"NA\" || ANNOTATION <= 2.0]");
                }}
        );

        final String encodedAttributes = unescapedHeaderLine.toStringEncoding();
        assertNotNull(encodedAttributes);

        final String expectedEncoding = "key=<ID=id,Description=\"filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]\",Source=\"filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]\">";
        assertEquals(encodedAttributes, expectedEncoding);
    }

    @Test
    public void testEscapedQuotedStringEncoding() {
        // test Source and Version attributes
        VCFStructuredHeaderLine unescapedHeaderLine =  new VCFStructuredHeaderLine(
                "key",
                new LinkedHashMap<String, String>() {{
                    put("ID", "id");
                    put(VCFStructuredHeaderLine.DESCRIPTION_ATTRIBUTE,
                            "filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]");
                    put(VCFStructuredHeaderLine.SOURCE_ATTRIBUTE,
                            "filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]");
                }}
        );

        final String encodedAttributes = unescapedHeaderLine.toStringEncoding();
        assertNotNull(encodedAttributes);

        final String expectedEncoding = "key=<ID=id,Description=\"filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]\",Source=\"filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]\">";
        assertEquals(encodedAttributes, expectedEncoding);
    }

}
