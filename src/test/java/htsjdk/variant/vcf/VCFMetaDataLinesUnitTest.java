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
        return new Object[][] {
                // line 1, line 2, expected to collide

                // Unstructured key collisions
                {       // same key, same value
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
                {       // same key, same ID, same (base VCFSimpleHeaderLine) class
                        new VCFSimpleHeaderLine("FILTER", Collections.singletonMap("ID", "id")),
                        new VCFSimpleHeaderLine("FILTER", Collections.singletonMap("ID", "id")), true
                },
                {       // same key, same ID, same (derived-VCFSimpleHeaderLine) class, same attributes
                        new VCFFilterHeaderLine("filterName", "unused description"),
                        new VCFFilterHeaderLine("filterName", "unused description"), true
                },
                {       // same key, same ID, same class, different attributes
                        new VCFFilterHeaderLine("filterName", "unused description"),
                        new VCFFilterHeaderLine("filterName", "different unused description"), true
                },
                {       // same key, different ID
                        new VCFFilterHeaderLine("filterName", "unused description"),
                        new VCFFilterHeaderLine("filterName2", "unused description"), false
                },
                {       // This is an unfortunate case that is allowed by the existing permissive VCFHeader
                        // APIs; two header lines that have identical content, one of which is modeled by the
                        // VCFSimpleHeaderLine base class, and one of which is modeled by the specialized ,
                        // derived VCFFilterHeaderLine class
                        new VCFFilterHeaderLine("id", "unused description"),
                        new VCFSimpleHeaderLine("FILTER", new LinkedHashMap<String, String>() {{
                            put("ID", "id");
                            put("Description", "unused description");
                        }}), true }
        };
    }

    @Test(dataProvider="keyCollisions")
    public void testKeyCollisions(final VCFHeaderLine line1, final VCFHeaderLine line2, final boolean expectCollision) {
        final VCFMetaDataLines mdLines = new VCFMetaDataLines();
        mdLines.addMetaDataLine(line1);
        mdLines.addMetaDataLine(line2);
        Assert.assertEquals(mdLines.getMetaDataInInputOrder().size(), expectCollision ? 1 : 2);
    }

    @DataProvider(name = "contigALTCollisions")
    public Object[][] contigALTCollisions() {
        return new Object[][] {
            {
                new VCFContigHeaderLine("<ID=X>", VCFHeader.DEFAULT_VCF_VERSION, 0), new VCFAltHeaderLine("<ID=X>", VCFHeader.DEFAULT_VCF_VERSION)
            },
            {
                new VCFAltHeaderLine("<ID=X>", VCFHeader.DEFAULT_VCF_VERSION), new VCFContigHeaderLine("<ID=X>", VCFHeader.DEFAULT_VCF_VERSION, 0)
            },
        };
    }

    @Test(dataProvider = "contigALTCollisions", expectedExceptions = IllegalStateException.class)
    public void testContigALTCollision(final VCFHeaderLine line1, final VCFHeaderLine line2) {
        final VCFMetaDataLines mdLines = new VCFMetaDataLines();
        mdLines.addMetaDataLine(line1);
        mdLines.addMetaDataLine(line2);
    }

    @Test
    public void testRetainFullHeaderLines() {
        final VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        final VCFMetaDataLines md = unitTestData.getTestMetaDataLines();

        Assert.assertEquals(md.getMetaDataInInputOrder().size(), unitTestData.getTestMetaDataLinesSet().size());
        Assert.assertEquals(md.getMetaDataInSortedOrder().size(), unitTestData.getTestMetaDataLinesSet().size());

        Assert.assertEquals(unitTestData.getTestFormatLines(), md.getFormatHeaderLines());
        Assert.assertEquals(unitTestData.getTestFilterLines(), md.getFilterLines());
        Assert.assertEquals(unitTestData.getTestInfoLines(), md.getInfoHeaderLines());
        Assert.assertEquals(unitTestData.getTestContigLines(), md.getContigLines());
        Assert.assertEquals(unitTestData.getTestFilterLines(), md.getFilterLines());

        final Set<VCFHeaderLine> otherLines = new LinkedHashSet<>();
        otherLines.addAll(unitTestData.getTestDefaultFileFormatLine());
        otherLines.addAll(unitTestData.getTestMiscellaneousLines());
        Assert.assertEquals(otherLines, md.getOtherHeaderLines());
    }

    @Test
    public void testAddRemoveOtherMetaDataLine() {
        final VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        final VCFMetaDataLines md = unitTestData.getTestMetaDataLines();

        int beforeAllSize = md.getMetaDataInInputOrder().size();
        int beforeStructuredSize = md.getIDHeaderLines().size();
        int beforeOtherSize = md.getOtherHeaderLines().size();

        final VCFHeaderLine newLine = new VCFHeaderLine("foo", "bar");

        // add one other line
        md.addMetaDataLine(newLine);
        Assert.assertEquals(md.getMetaDataInInputOrder().size(), beforeAllSize + 1);
        Assert.assertEquals(md.getIDHeaderLines().size(), beforeStructuredSize);  // remains the same
        Assert.assertEquals(md.getOtherHeaderLines().size(), beforeOtherSize + 1);

        // remove the other line and we're back to original size
        Assert.assertEquals(md.removeMetaDataLine(newLine), newLine);
        Assert.assertEquals(md.getMetaDataInInputOrder().size(), beforeAllSize);
        Assert.assertEquals(md.getIDHeaderLines().size(), beforeStructuredSize);  // still remains the same
        Assert.assertEquals(md.getOtherHeaderLines().size(), beforeOtherSize);
    }

    @Test
    public void testAddRemoveUniqueStructuredLine() {
        final VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        final VCFMetaDataLines md = unitTestData.getTestMetaDataLines();

        final int beforeAllSize = md.getMetaDataInInputOrder().size();
        final int beforeStructuredSize = md.getIDHeaderLines().size();
        final int beforeFilterSize = md.getFilterLines().size();
        final int beforeOtherSize = md.getOtherHeaderLines().size();

        // add a new, unique, structured line
        final VCFFilterHeaderLine newLine = new VCFFilterHeaderLine("filterID", "unused desc");
        md.addMetaDataLine(newLine);

        Assert.assertEquals(md.getMetaDataInInputOrder().size(), beforeAllSize + 1);
        Assert.assertEquals(md.getIDHeaderLines().size(), beforeStructuredSize + 1);
        Assert.assertEquals(md.getFilterLines().size(), beforeFilterSize + 1);
        Assert.assertEquals(md.getOtherHeaderLines().size(), beforeOtherSize); // remains the same

        // remove the new line and we're back to original size
        Assert.assertEquals(md.removeMetaDataLine(newLine), newLine);
        Assert.assertEquals(md.getMetaDataInInputOrder().size(), beforeAllSize);
        Assert.assertEquals(md.getIDHeaderLines().size(), beforeStructuredSize);
        Assert.assertEquals(md.getFilterLines().size(), beforeFilterSize);
        Assert.assertEquals(md.getOtherHeaderLines().size(), beforeOtherSize); // still remains the same
    }

    @Test
    public void testAddRemoveDuplicateStructuredLine() {
        final VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        final VCFMetaDataLines md = unitTestData.getTestMetaDataLines();

        final int beforeAllSize = md.getMetaDataInInputOrder().size();
        final int beforeStructuredSize = md.getIDHeaderLines().size();
        final int beforeFilterSize = md.getFilterLines().size();

        // add a new, unique, structured (filter) line
        final VCFFilterHeaderLine newLine = new VCFFilterHeaderLine("filterID", "unused desc");
        md.addMetaDataLine(newLine);

        Assert.assertEquals(md.getMetaDataInInputOrder().size(), beforeAllSize + 1);
        Assert.assertEquals(md.getIDHeaderLines().size(), beforeStructuredSize + 1);
        Assert.assertEquals(md.getFilterLines().size(), beforeFilterSize + 1);

        // now try to re-add the same structured filter line again, this second one is rejected, count remains the same
        md.addMetaDataLine(newLine);
        Assert.assertEquals(md.getMetaDataInInputOrder().size(), beforeAllSize + 1);
        Assert.assertEquals(md.getIDHeaderLines().size(), beforeStructuredSize + 1);
        Assert.assertEquals(md.getFilterLines().size(), beforeFilterSize + 1);
        Assert.assertEquals(md.getFilterHeaderLine("filterID"), newLine);

        // remove the first structured line and we're back to the original size
        Assert.assertEquals(md.removeMetaDataLine(newLine), newLine);
        Assert.assertEquals(md.getMetaDataInInputOrder().size(), beforeAllSize);
        Assert.assertEquals(md.getIDHeaderLines().size(), beforeStructuredSize);
        Assert.assertEquals(md.getFilterLines().size(), beforeFilterSize);
    }

    @Test
    public void testHasEquivalentHeaderLinePositive() {
        final VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        final VCFMetaDataLines sourceMetaDataLines = unitTestData.getTestMetaDataLines();

        // for each headerLine in the set, make sure findEquivalentHeaderLine returns it
        for (final VCFHeaderLine headerLine : sourceMetaDataLines.getMetaDataInInputOrder()) {
            final VCFHeaderLine equivalentLine = sourceMetaDataLines.findEquivalentHeaderLine(headerLine);
            Assert.assertTrue(equivalentLine.equals(headerLine));
        }
    }

    @Test
    public void testHasEquivalentHeaderLineNegative() {
        final VCFMetaDataLines metaDataLines = new VCFMetaDataLines();
        // add a few test lines
        metaDataLines.addMetaDataLine(new VCFHeaderLine("testkey1", "test value"));
        metaDataLines.addMetaDataLine(new VCFHeaderLine("testkey1", "other value"));
        metaDataLines.addMetaDataLine(new VCFHeaderLine("reference", "assembly37"));

        // for each other headerLine in the starting set, make another header line with the same key but a different
        // value, and ensure findEquivalentHeaderLine does NOT return it
        for (final VCFHeaderLine headerLine : metaDataLines.getMetaDataInInputOrder()) {
            final VCFHeaderLine equivalentLine = metaDataLines.findEquivalentHeaderLine(headerLine);
            Assert.assertTrue(equivalentLine.equals(headerLine));

            final VCFHeaderLine modifiedHeaderLine = new VCFHeaderLine(headerLine.getKey(), headerLine.getValue() + "zzz");
            Assert.assertNull(metaDataLines.findEquivalentHeaderLine(modifiedHeaderLine));
        }
    }

    @Test
    public void testGetFilterHeaderLine() {
        final VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        final VCFMetaDataLines md = unitTestData.getTestMetaDataLines();
        Assert.assertEquals(md.getFilterHeaderLine(unitTestData.getTestFilterLines().get(0).getID()), unitTestData.getTestFilterLines().get(0));
    }

    @Test
    public void testGetInfoHeaderLine() {
        final VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        final VCFMetaDataLines md = unitTestData.getTestMetaDataLines();
        Assert.assertEquals(md.getInfoHeaderLine(unitTestData.getTestInfoLines().get(0).getID()), unitTestData.getTestInfoLines().get(0));
    }

    @Test
    public void testGetFormatHeaderLine() {
        final VCFHeaderUnitTestData testData = new VCFHeaderUnitTestData();
        final VCFMetaDataLines md = testData.getTestMetaDataLines();
        Assert.assertEquals(md.getFormatHeaderLine(testData.getTestFormatLines().get(0).getID()), testData.getTestFormatLines().get(0));
    }

    @Test
    public void testAddRemoveVersionLine() {
        final VCFHeaderUnitTestData unitTestData = new VCFHeaderUnitTestData();
        final VCFMetaDataLines md = unitTestData.getTestMetaDataLines();
        Assert.assertEquals(md.getVCFVersion(), unitTestData.TEST_VERSION);

        final int originalMetaDataLineCount = md.getMetaDataInInputOrder().size();

        // now, remove the version line, make sure the removed line is actually the version line, that the
        // resulting metadataLines version is now null, and the line count drops by 1
        final VCFHeaderLine queryVersionLine = VCFHeader.makeHeaderVersionLine(unitTestData.TEST_VERSION);
        final VCFHeaderLine oldVersionLine = md.removeMetaDataLine(queryVersionLine);
        Assert.assertEquals(oldVersionLine, queryVersionLine);
        Assert.assertNull(md.getVCFVersion());
        Assert.assertEquals(md.getMetaDataInInputOrder().size(), originalMetaDataLineCount - 1);

        // now put it back...
        md.addMetaDataLine(oldVersionLine);
        Assert.assertEquals(md.getVCFVersion(), unitTestData.TEST_VERSION);
        Assert.assertEquals(md.getMetaDataInInputOrder().size(), originalMetaDataLineCount);
    }

    @Test
    public void testAddContigLineExactDuplicate() {
        final VCFMetaDataLines md = new VCFMetaDataLines();
        final Set<VCFHeaderLine> contigLines = new LinkedHashSet<>();

        final VCFContigHeaderLine vcfContigLine1 = new VCFContigHeaderLine(
                new LinkedHashMap<String, String>() {{
                    put("ID", "contig1");
                }}, 0);
        final VCFContigHeaderLine vcfContigLine2 = new VCFContigHeaderLine(
                new LinkedHashMap<String, String>() {{
                    put("ID", "contig2");
                }}, 1);

        contigLines.add(vcfContigLine1);
        contigLines.add(vcfContigLine2);
        md.addMetaDataLines(contigLines);
        Assert.assertEquals(md.getContigLines(), contigLines);

        // add in the duplicate line
        md.addMetaDataLine(vcfContigLine1);
        Assert.assertEquals(md.getContigLines(), contigLines);
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testAddContigLineConflicting() {
        final VCFMetaDataLines md = new VCFMetaDataLines();

        final Set<VCFHeaderLine> contigLines = new LinkedHashSet<>();
        contigLines.add(new VCFContigHeaderLine(
                new LinkedHashMap<String, String>() {{
                    put("ID", "contig1");
                }}, 0));
        contigLines.add(new VCFContigHeaderLine(
                new LinkedHashMap<String, String>() {{
                    put("ID", "contig2");
                }}, 1));

        md.addMetaDataLines(contigLines);
        Assert.assertEquals(md.getContigLines(), contigLines);

        // try to add a contg line with a duplicate index, but with a different name than the existing line with that index
        md.addMetaDataLine(new VCFContigHeaderLine(
                new LinkedHashMap<String, String>() {{
                    put("ID", "contig3");
                }}, 0));
    }

    @Test
    public void testRemoveAndReplaceContigLines() {
        final VCFMetaDataLines md = new VCFMetaDataLines();
        final Set<VCFHeaderLine> contigLines = new LinkedHashSet<>();

        final VCFContigHeaderLine vcfContigLine1 = new VCFContigHeaderLine(
                new LinkedHashMap<String, String>() {{
                    put("ID", "contig1");
                }}, 1);
        final VCFContigHeaderLine vcfContigLine2 = new VCFContigHeaderLine(
                new LinkedHashMap<String, String>() {{
                    put("ID", "contig2");
                }}, 2);

        contigLines.add(vcfContigLine1);
        contigLines.add(vcfContigLine2);
        md.addMetaDataLines(contigLines);
        Assert.assertEquals(md.getContigLines(), contigLines);

        //make sure the initial contig index order is honored; it happens to be the same as the input
        // order a this point, but check anyway
        final List<VCFContigHeaderLine> sortedLines1 = md.getContigLines();
        Assert.assertEquals(sortedLines1.get(0), vcfContigLine1);
        Assert.assertEquals(sortedLines1.get(1), vcfContigLine2);

        // now  remove the first contig line; only one should remain
        final VCFHeaderLine removedContigLine = md.removeMetaDataLine(vcfContigLine1);
        Assert.assertEquals(removedContigLine, vcfContigLine1);
        final List<VCFContigHeaderLine> sortedContigHeaderLines = md.getContigLines();
        Assert.assertEquals(sortedContigHeaderLines.size(), 1);

        // now add the first line back in, so the input order is different than the sorted order,
        // and make sure the order is honored
        md.addMetaDataLine(vcfContigLine1);
        final List<VCFContigHeaderLine> sortedLines2 = md.getContigLines();
        Assert.assertEquals(sortedLines2.get(0), vcfContigLine1);
        Assert.assertEquals(sortedLines2.get(1), vcfContigLine2);

        // now add in ANOTHER contig line at the end that has an index that puts it BEFORE the existing lines
        final VCFContigHeaderLine vcfContigLine3 = new VCFContigHeaderLine(
                new LinkedHashMap<String, String>() {{
                    put("ID", "contig3");
                }}, 0);
        md.addMetaDataLine(vcfContigLine3);
        final List<VCFContigHeaderLine> sortedLines3 = md.getContigLines();
        Assert.assertEquals(sortedLines3.size(), 3);
        Assert.assertEquals(sortedLines3.get(0), vcfContigLine3);
        Assert.assertEquals(sortedLines3.get(1), vcfContigLine1);
        Assert.assertEquals(sortedLines3.get(2), vcfContigLine2);
    }

    @Test
    public void testFileFormatLineFirstInSet() {
        final Set<VCFHeaderLine> orderedLineSet = new LinkedHashSet<>();
        orderedLineSet.addAll(VCFHeaderUnitTestData.getV42HeaderLinesWITHOUTFormatString());
        orderedLineSet.stream().forEach(l -> Assert.assertFalse(VCFHeaderVersion.isFormatString(l.getKey())));
        // add the file format line last
        orderedLineSet.add(VCFHeader.makeHeaderVersionLine(VCFHeader.DEFAULT_VCF_VERSION));
        final VCFMetaDataLines metaDataLines = new VCFMetaDataLines();
        metaDataLines.addMetaDataLines(orderedLineSet);

        final Collection<VCFHeaderLine> inputOrderLines = metaDataLines.getMetaDataInInputOrder();
        final Optional<VCFHeaderLine> optFirstInputOrderLine = inputOrderLines.stream().findFirst();
        Assert.assertTrue(optFirstInputOrderLine.isPresent());
        Assert.assertTrue(VCFHeaderVersion.isFormatString(optFirstInputOrderLine.get().getKey()));

        final Collection<VCFHeaderLine> sortedOrderLines = metaDataLines.getMetaDataInInputOrder();
        final Optional<VCFHeaderLine> optFirstSortedOrderLine = sortedOrderLines.stream().findFirst();
        Assert.assertTrue(optFirstSortedOrderLine.isPresent());
        Assert.assertTrue(VCFHeaderVersion.isFormatString(optFirstSortedOrderLine.get().getKey()));
    }

}

