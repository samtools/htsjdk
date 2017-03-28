package htsjdk.variant.vcf;

import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.tribble.TribbleException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

public class VCFContigHeaderLineUnitTest {

    @DataProvider(name = "allowedIDs")
    public Object[][] getAllowedIDs() {
        return new Object[][]{
                {"<ID=1>",                              "1"},
                {"<ID=10>",                             "10"},
                {"<ID=X>",                              "X"},
                {"<ID=Y>",                              "Y"},
                {"<ID=MT>",                             "MT"},
                {"<ID=NC_007605>",                      "NC_007605"},
                {"<ID=GL000191.1>",                     "GL000191.1"},
                {"<ID=HLA-A*01:01:01:01,length=100>",   "HLA-A*01:01:01:01"}, //https://github.com/samtools/hts-specs/issues/124
       };
    }

    @Test(dataProvider= "allowedIDs")
    public void testAllowedIDs(final String lineString, final String expectedIDString) {
        final VCFContigHeaderLine headerline = new VCFContigHeaderLine(lineString, VCFHeader.DEFAULT_VCF_VERSION, 0);
        Assert.assertEquals(headerline.getID(), expectedIDString);
    }

    @Test(expectedExceptions=TribbleException.class)
    public void testRejectNegativeIndex() {
        new VCFContigHeaderLine("<ID=contig1,length=100>", VCFHeader.DEFAULT_VCF_VERSION, -1);
    }

    @DataProvider(name = "allowedAttributes")
    public Object[][] getAllowedAttributes() {
        return new Object[][] {
                {"<ID=contig1>", "ID", "contig1"},  // https://github.com/samtools/htsjdk/issues/389 (no length)
                {"<ID=contig1,length=100>", "length", "100"},
                {"<ID=contig1,length=100,taxonomy=\"Homo sapiens\">", "taxonomy", "Homo sapiens"},
                {"<ID=contig1,length=100,assembly=b37>", "assembly", "b37"},
                {"<ID=contig1,length=100,assembly=b37,md5=1a258fe76dfc8abd926f81f0e9b82ed7>", "md5", "1a258fe76dfc8abd926f81f0e9b82ed7"},
                {"<ID=contig1,length=100,assembly=b37,md5=1a258fe76dfc8abd926f81f0e9b82ed7,URL=http://www.refserve.org:8080/path/>",
                        "URL", "http://www.refserve.org:8080/path/"},
                {"<ID=contig1,length=100,assembly=b37,md5=1a258fe76dfc8abd926f81f0e9b82ed7,URL=http://www.refserve.org:8080/path/,species=\"Homo sapiens\">",
                        "species", "Homo sapiens"},
        };
    }

    @Test(dataProvider= "allowedAttributes")
    public void testAllowedAttributes(final String lineString, final String attribute, final String expectedValue) {
        final VCFContigHeaderLine headerline = new VCFContigHeaderLine(lineString, VCFHeader.DEFAULT_VCF_VERSION, 0);
        Assert.assertEquals(headerline.getGenericFieldValue(attribute), expectedValue);
    }

    @Test
    public void testRoundTripThroughSequenceRecord() {
        final VCFContigHeaderLine contigLine = new VCFContigHeaderLine(
                "<ID=contig1,length=100,assembly=b37,md5=1a258fe76dfc8abd926f81f0e9b82ed7,URL=http://www.refserve.org:8080/path/,species=\"Homo sapiens\">",
                VCFHeader.DEFAULT_VCF_VERSION,
                0);

        String lengthString = "100";
        String assemblyString = "b37";
        String md5String = "1a258fe76dfc8abd926f81f0e9b82ed7";
        String URLString = "http://www.refserve.org:8080/path/";
        String speciesString = "Homo sapiens";

        SAMSequenceRecord sequenceRecord = contigLine.getSAMSequenceRecord();

        Assert.assertEquals(Integer.toString(sequenceRecord.getSequenceLength()), lengthString);
        Assert.assertEquals(contigLine.getGenericFieldValue(VCFContigHeaderLine.LENGTH_ATTRIBUTE), lengthString);

        Assert.assertEquals(sequenceRecord.getAssembly(), assemblyString);
        Assert.assertEquals(contigLine.getGenericFieldValue(VCFContigHeaderLine.ASSEMBLY_ATTRIBUTE), assemblyString);

        Assert.assertEquals(sequenceRecord.getMd5(), md5String);
        Assert.assertEquals(contigLine.getGenericFieldValue(VCFContigHeaderLine.MD5_ATTRIBUTE), md5String);

        Assert.assertEquals(sequenceRecord.getAttribute(SAMSequenceRecord.URI_TAG), URLString);
        Assert.assertEquals(contigLine.getGenericFieldValue(VCFContigHeaderLine.URL_ATTRIBUTE), URLString);

        Assert.assertEquals(sequenceRecord.getAttribute(SAMSequenceRecord.SPECIES_TAG), speciesString);
        Assert.assertEquals(contigLine.getGenericFieldValue(VCFContigHeaderLine.SPECIES_ATTRIBUTE), speciesString);

        // now turn the SAMSequenceRecord back into a contig line, and compare the result to the
        // original contig line
        Assert.assertEquals(
                new VCFContigHeaderLine(sequenceRecord, assemblyString),
                contigLine);
    }

    @DataProvider (name = "hashEqualsCompareData")
    public Object[][] getHashEqualsCompareData() {
        return new Object[][] {

                // For contig lines, equals and hash depend on the id, all other attributes, and the contig index,
                // but compareTo only cares about the index.

                // line, index, line, line, index  -> expected hash equals, expected equals, expected compare,
                {"<ID=chr1>", 0,    "<ID=chr1>", 0,             true,           true,           0  },   // identical
                {"<ID=chr1>", 0,    "<ID=chr1>", 1,             false,          false,          -1 },   // identical except contig index
                {"<ID=chr1>", 1,    "<ID=chr1>", 0,             false,          false,          1  },   // identical except contig index

                {"<ID=chr1, length=10>", 0,    "<ID=chr1>", 0,  false,          false,          0  },   // identical except attributes
                {"<ID=chr1, length=10>", 0,    "<ID=chr1>", 1,  false,          false,         -1  },   // different attributes, different index

                {"<ID=chr1>", 0,    "<ID=chr2>", 0,             false,          false,          0  },   // identical except ID
                // different ID, same attributes and index, -> not equal, different hash, compare==0
                {"<ID=chr1>", 0,    "<ID=chr2,length=10>", 0,   false,          false,          0  },   // different ID, attributes, same index
        };
    }

    @Test(dataProvider = "hashEqualsCompareData")
    public void testHashEqualsCompare(
            final String line1,
            final int index1,
            final String line2,
            final int index2,
            final boolean expectedHashEquals,
            final boolean expectedEquals,
            final int expectedCompare)
    {
        final VCFContigHeaderLine headerLine1 = new VCFContigHeaderLine(line1, VCFHeader.DEFAULT_VCF_VERSION, index1);
        final VCFContigHeaderLine headerLine2 = new VCFContigHeaderLine(line2, VCFHeader.DEFAULT_VCF_VERSION, index2);

        Assert.assertEquals(headerLine1.hashCode() == headerLine2.hashCode(), expectedHashEquals);
        Assert.assertEquals(headerLine1.equals(headerLine2), expectedEquals);
        Assert.assertEquals(headerLine1.compareTo(headerLine2), expectedCompare);
    }

    @Test
    public void testSortOrder() {

        final List<VCFContigHeaderLine> expectedLineOrder = new ArrayList<VCFContigHeaderLine>() {{
            add(new VCFContigHeaderLine("<ID=1>", VCFHeader.DEFAULT_VCF_VERSION, 1));
            add(new VCFContigHeaderLine("<ID=2>", VCFHeader.DEFAULT_VCF_VERSION, 2));
            add(new VCFContigHeaderLine("<ID=10>", VCFHeader.DEFAULT_VCF_VERSION, 10));
            add(new VCFContigHeaderLine("<ID=20>", VCFHeader.DEFAULT_VCF_VERSION, 20));
        }};

        final TreeSet<VCFContigHeaderLine> sortedLines = new TreeSet<>(
                new ArrayList<VCFContigHeaderLine>() {{
                    add(new VCFContigHeaderLine("<ID=20>", VCFHeader.DEFAULT_VCF_VERSION, 20));
                    add(new VCFContigHeaderLine("<ID=10>", VCFHeader.DEFAULT_VCF_VERSION, 10));
                    add(new VCFContigHeaderLine("<ID=1>", VCFHeader.DEFAULT_VCF_VERSION, 1));
                    add(new VCFContigHeaderLine("<ID=2>", VCFHeader.DEFAULT_VCF_VERSION, 2));
                }}
        );

        final Iterator<VCFContigHeaderLine> sortedIt = sortedLines.iterator();
        for (VCFContigHeaderLine cl : expectedLineOrder) {
            Assert.assertTrue(sortedIt.hasNext());
            Assert.assertEquals(cl, sortedIt.next());
        }
    }

}
