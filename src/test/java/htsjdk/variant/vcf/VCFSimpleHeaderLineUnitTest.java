package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.TribbleException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import java.util.LinkedHashMap;

public class VCFSimpleHeaderLineUnitTest extends HtsjdkTest {

    private VCFSimpleHeaderLine getStructuredHeaderLine() {
        return new VCFSimpleHeaderLine(
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
        final VCFSimpleHeaderLine hl = new VCFSimpleHeaderLine("testKey", "testId", "test description");
        Assert.assertEquals("testKey", hl.getKey());
        Assert.assertEquals("testId", hl.getID());
        Assert.assertEquals("test description", hl.getGenericFieldValue(VCFSimpleHeaderLine.DESCRIPTION_ATTRIBUTE));
        Assert.assertEquals("testKey=<ID=testId,Description=\"test description\">", hl.toStringEncoding());
    }

    @Test
    public void testConstructorFromEncodedLine() {
        final VCFSimpleHeaderLine hLine = new VCFSimpleHeaderLine("key", "<ID=id,attr1=value1>", VCFHeader.DEFAULT_VCF_VERSION);
        Assert.assertEquals(hLine.getKey(), "key");
        Assert.assertEquals(hLine.getID(), "id");
        Assert.assertEquals(hLine.getGenericFieldValue("ID"), "id");
        Assert.assertEquals(hLine.getGenericFieldValue("attr1"), "value1");
    }

    @Test
    public void testConstructorFromAttributeMap() {
        final VCFSimpleHeaderLine hLine = new VCFSimpleHeaderLine(
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
        new VCFSimpleHeaderLine("key", "<attr1=value1>", VCFHeader.DEFAULT_VCF_VERSION);
    }

    @Test(expectedExceptions=TribbleException.class)
    public void testRejectIdMissingFromAttributeMap() {
        new VCFSimpleHeaderLine(
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
        new VCFSimpleHeaderLine("key", headerLine, VCFHeader.DEFAULT_VCF_VERSION);
    }

    @Test
    public void testGetID() {
        Assert.assertEquals(getStructuredHeaderLine().getID(), "id");
    }

    @Test
    public void testIsIDLine() {
        Assert.assertTrue(getStructuredHeaderLine().isIDHeaderLine());
    }

    @Test
    public void testGetGenericFieldValue() {
        Assert.assertEquals(getStructuredHeaderLine().getGenericFieldValue("attr1"), "value1");
    }

    @Test
    public void testStringEncoding() {
        final VCFSimpleHeaderLine structuredHL = getStructuredHeaderLine();
        Assert.assertEquals(structuredHL.toStringEncoding(),"key=<ID=id,attr1=value1,attr2=value2>");
    }

    @Test
    public void testUnescapedQuotedStringEncoding() {
        final VCFSimpleHeaderLine unescapedHeaderLine =  new VCFSimpleHeaderLine(
                "key",
                new LinkedHashMap<String, String>() {{
                    put("ID", "id");
                    put(VCFSimpleHeaderLine.DESCRIPTION_ATTRIBUTE,
                            "filterName=[ANNOTATION] filterExpression=[ANNOTATION == \"NA\" || ANNOTATION <= 2.0]");
                    put(VCFSimpleHeaderLine.SOURCE_ATTRIBUTE,
                            "filterName=[ANNOTATION] filterExpression=[ANNOTATION == \"NA\" || ANNOTATION <= 2.0]");
                }}
        );

        final String encodedAttributes = unescapedHeaderLine.toStringEncoding();
        Assert.assertNotNull(encodedAttributes);

        final String expectedEncoding = "key=<ID=id,Description=\"filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]\",Source=\"filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]\">";
        Assert.assertEquals(encodedAttributes, expectedEncoding);
    }

    @Test
    public void testEscapedQuotedStringEncoding() {
        // test Source and Version attributes
        final VCFSimpleHeaderLine unescapedHeaderLine =  new VCFSimpleHeaderLine(
                "key",
                new LinkedHashMap<String, String>() {{
                    put("ID", "id");
                    put(VCFSimpleHeaderLine.DESCRIPTION_ATTRIBUTE,
                            "filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]");
                    put(VCFSimpleHeaderLine.SOURCE_ATTRIBUTE,
                            "filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]");
                }}
        );

        final String encodedAttributes = unescapedHeaderLine.toStringEncoding();
        Assert.assertNotNull(encodedAttributes);

        final String expectedEncoding = "key=<ID=id,Description=\"filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]\",Source=\"filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]\">";
        Assert.assertEquals(encodedAttributes, expectedEncoding);
    }

}
