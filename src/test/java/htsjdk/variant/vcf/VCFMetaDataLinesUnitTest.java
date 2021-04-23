package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.TribbleException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;

public class VCFMetaDataLinesUnitTest extends HtsjdkTest {

    @DataProvider(name="keyCollisions")
    public Object[][] keyCollisions() {
        return new Object[][]{
                // Unstructured key collisions
                {       // same key and value
                        new VCFHeaderLine("key", "value"),
                        new VCFHeaderLine("key", "value"), true
                },
                {       // same key, different value
                        new VCFHeaderLine("key", "value"),
                        new VCFHeaderLine("key", "value1"), false
                },
                {       // different key, same value
                        new VCFHeaderLine("key1", "value"),
                        new VCFHeaderLine("key2", "value"), false
                },
                {       // different key, different value
                        new VCFHeaderLine("key1", "value1"),
                        new VCFHeaderLine("key2", "value2"), false
                },
                // Structured key collisions
                {       // same key, same ID
                        new VCFFilterHeaderLine("filterName", "unused description"),
                        new VCFFilterHeaderLine("filterName", "unused description"), true
                },
                {       // same key, different ID
                        new VCFFilterHeaderLine("filterName", "unused description"),
                        new VCFFilterHeaderLine("filterName2", "unused description"), false
                },
                {       // filter matches structured key/value
                        new VCFFilterHeaderLine("id", "unused description"),
                        new VCFSimpleHeaderLine("FILTER", Collections.singletonMap("ID", "id")), true
                },
                {       // structured key matches structured key/id
                        new VCFSimpleHeaderLine("FILTER", Collections.singletonMap("ID", "id")),
                        new VCFSimpleHeaderLine("FILTER", Collections.singletonMap("ID", "id")), true
                },
                // Mixed structured/unstructured
                {       // key overlaps key/value
                        new VCFFilterHeaderLine("id", "unused description"),
                        new VCFHeaderLine("FILTER:id", "unused description"), false
                },
                {       // unstructured key matches structured "FILTER" key/value
                        new VCFSimpleHeaderLine("FILTER", Collections.singletonMap("ID", "id")),
                        new VCFHeaderLine("FILTER:id", "some value"), false
                },
        };
    }

    @Test(dataProvider="keyCollisions")
    public void testKeyCollision(final VCFHeaderLine line1, final VCFHeaderLine line2, final boolean expectCollision)
    {
        VCFMetaDataLines mdLines = new VCFMetaDataLines();
        mdLines.addMetaDataLine(line1);
        mdLines.addMetaDataLine(line2);
        Assert.assertEquals(mdLines.getMetaDataInInputOrder().size(), expectCollision ? 1 : 2);
    }

    @Test
    public void testRetainFullHeaderLines() {
        VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        VCFMetaDataLines md = unitTestData.getFullMetaDataLines();

        Assert.assertEquals(md.getMetaDataInInputOrder().size(), unitTestData.getFullMetaDataLinesAsSet().size());
        Assert.assertEquals(md.getMetaDataInSortedOrder().size(), unitTestData.getFullMetaDataLinesAsSet().size());

        Assert.assertEquals(unitTestData.formatLines, md.getFormatHeaderLines());
        Assert.assertEquals(unitTestData.filterLines, md.getFilterLines());
        Assert.assertEquals(unitTestData.infoLines, md.getInfoHeaderLines());
        Assert.assertEquals(unitTestData.contigLines, md.getContigLines());
        Assert.assertEquals(unitTestData.filterLines, md.getFilterLines());

        Set<VCFHeaderLine> otherLines = new LinkedHashSet<>();
        otherLines.addAll(unitTestData.fileformatLines);
        otherLines.addAll(unitTestData.miscLines);
        Assert.assertEquals(otherLines, md.getOtherHeaderLines());
    }

    @Test
    public void testAddRemoveOtherMetaDataLine() {
        VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        VCFMetaDataLines md = unitTestData.getFullMetaDataLines();

        int beforeAllSize = md.getMetaDataInInputOrder().size();
        int beforeStructuredSize = md.getIDHeaderLines().size();
        int beforeOtherSize = md.getOtherHeaderLines().size();

        VCFHeaderLine newLine = new VCFHeaderLine("foo", "bar");

        // add one other line
        md.addMetaDataLine(newLine);
        Assert.assertEquals(md.getMetaDataInInputOrder().size(), beforeAllSize + 1);
        Assert.assertEquals(md.getIDHeaderLines().size(), beforeStructuredSize);  // remains the same
        Assert.assertEquals(md.getOtherHeaderLines().size(), beforeOtherSize + 1);

        // remove the other line and we're back to original size
        Assert.assertEquals(md.removeHeaderLine(newLine), newLine);
        Assert.assertEquals(md.getMetaDataInInputOrder().size(), beforeAllSize);
        Assert.assertEquals(md.getIDHeaderLines().size(), beforeStructuredSize);  // still remains the same
        Assert.assertEquals(md.getOtherHeaderLines().size(), beforeOtherSize);
    }

    @Test
    public void testAddRemoveUniqueStructuredLine() {
        VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        VCFMetaDataLines md = unitTestData.getFullMetaDataLines();

        int beforeAllSize = md.getMetaDataInInputOrder().size();
        int beforeStructuredSize = md.getIDHeaderLines().size();
        int beforeFilterSize = md.getFilterLines().size();
        int beforeOtherSize = md.getOtherHeaderLines().size();

        VCFFilterHeaderLine newLine = new VCFFilterHeaderLine("filterID", "unused desc");

        // add one structured line
        md.addMetaDataLine(newLine);
        Assert.assertEquals(md.getMetaDataInInputOrder().size(), beforeAllSize + 1);
        Assert.assertEquals(md.getIDHeaderLines().size(), beforeStructuredSize + 1);
        Assert.assertEquals(md.getFilterLines().size(), beforeFilterSize + 1);
        Assert.assertEquals(md.getOtherHeaderLines().size(), beforeOtherSize); // remains the same

        // remove the new line and we're back to original size
        Assert.assertEquals(md.removeHeaderLine(newLine), newLine);
        Assert.assertEquals(md.getMetaDataInInputOrder().size(), beforeAllSize);
        Assert.assertEquals(md.getIDHeaderLines().size(), beforeStructuredSize);
        Assert.assertEquals(md.getFilterLines().size(), beforeFilterSize);
        Assert.assertEquals(md.getOtherHeaderLines().size(), beforeOtherSize); // still remains the same
    }

    @Test
    public void testAddRemoveDuplicateStructuredLine() {
        VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        VCFMetaDataLines md = unitTestData.getFullMetaDataLines();

        int beforeAllSize = md.getMetaDataInInputOrder().size();
        int beforeStructuredSize = md.getIDHeaderLines().size();
        int beforeFilterSize = md.getFilterLines().size();

        VCFFilterHeaderLine newLine = new VCFFilterHeaderLine("filterID", "unused desc");

        // add one structured (filter) line
        md.addMetaDataLine(newLine);
        Assert.assertEquals(md.getMetaDataInInputOrder().size(), beforeAllSize + 1);
        Assert.assertEquals(md.getIDHeaderLines().size(), beforeStructuredSize + 1);
        Assert.assertEquals(md.getFilterLines().size(), beforeFilterSize + 1);

        // add the same structured line again, second is rejected, count remains the same
        md.addMetaDataLine(newLine);
        Assert.assertEquals(md.getMetaDataInInputOrder().size(), beforeAllSize + 1);
        Assert.assertEquals(md.getIDHeaderLines().size(), beforeStructuredSize + 1);
        Assert.assertEquals(md.getFilterLines().size(), beforeFilterSize + 1);
        Assert.assertEquals(md.getFilterHeaderLine("filterID"), newLine);

        // remove the first structured line and we're back to the original size
        Assert.assertEquals(md.removeHeaderLine(newLine), newLine);
        Assert.assertEquals(md.getMetaDataInInputOrder().size(), beforeAllSize);
        Assert.assertEquals(md.getIDHeaderLines().size(), beforeStructuredSize);
        Assert.assertEquals(md.getFilterLines().size(), beforeFilterSize);
    }

    @Test
    public void testGetEquivalentHeaderLine() {
        VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        VCFMetaDataLines md = unitTestData.getFullMetaDataLines();
        Assert.assertEquals(md.getFilterHeaderLine(
                unitTestData.filterLines.get(0).getID()),
                md.hasEquivalentHeaderLine(unitTestData.filterLines.get(0)));
    }

    @Test
    public void testGetMetaDataLine() {
        VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        VCFMetaDataLines md = unitTestData.getFullMetaDataLines();
        Assert.assertEquals(
                md.getFilterHeaderLine(unitTestData.filterLines.get(0).getID()),
                md.getMetaDataLine(unitTestData.filterLines.get(0).getKey()));
    }

    @Test
    public void testGetFilterHeaderLine() {
        VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        VCFMetaDataLines md = unitTestData.getFullMetaDataLines();
        Assert.assertEquals(md.getFilterHeaderLine(unitTestData.filterLines.get(0).getID()), unitTestData.filterLines.get(0));
    }

    @Test
    public void testGetInfoHeaderLine() {
        VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        VCFMetaDataLines md = unitTestData.getFullMetaDataLines();
        Assert.assertEquals(md.getInfoHeaderLine(unitTestData.infoLines.get(0).getID()), unitTestData.infoLines.get(0));
    }

    @Test
    public void testGetFormatHeaderLine() {
        VCFHeaderUnitTestData testData = new VCFHeaderUnitTestData();
        VCFMetaDataLines md = testData.getFullMetaDataLines();
        Assert.assertEquals(md.getFormatHeaderLine(testData.formatLines.get(0).getID()), testData.formatLines.get(0));
    }

    @DataProvider(name="conflictingVCFVersions")
    public Object[][] vcfVersions() {
        return new Object[][]{
                {VCFHeaderVersion.VCF4_0},
                {VCFHeaderVersion.VCF4_1},
                {VCFHeaderVersion.VCF4_2}
        };
    }

    @Test(dataProvider="conflictingVCFVersions", expectedExceptions = TribbleException.class)
    public void testValidateMetaDataLinesConflictingVersion(final VCFHeaderVersion vcfVersion) {
        VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        VCFMetaDataLines md = unitTestData.getFullMetaDataLines(); // contains a VCFv43 fileformat line
        md.validateMetaDataLines(vcfVersion);
    }

    @Test(dataProvider="conflictingVCFVersions", expectedExceptions = TribbleException.class)
    public void testValidateMetaDataLineConflictingVersion(final VCFHeaderVersion vcfVersion) {
        VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        VCFMetaDataLines md = unitTestData.getFullMetaDataLines(); // contains a VCFv43 fileformat line
        md.getMetaDataInInputOrder().forEach(hl -> md.validateMetaDataLine(vcfVersion, hl));
    }

    @Test
    public void testValidateMetaDataLinesValidVersion() {
        VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        VCFMetaDataLines md = unitTestData.getFullMetaDataLines(); // contains a VCFv43 fileformat line
        md.validateMetaDataLines(unitTestData.canonicalVersion);
    }

    @Test
    public void testValidateMetaDataLineVlidVersion() {
        VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        VCFMetaDataLines md = unitTestData.getFullMetaDataLines(); // contains a VCFv43 fileformat line
        md.getMetaDataInInputOrder().forEach(hl -> md.validateMetaDataLine(unitTestData.canonicalVersion, hl));
    }
}

