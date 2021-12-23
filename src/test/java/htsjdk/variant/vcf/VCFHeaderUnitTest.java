/*
* Copyright (c) 2012 The Broad Institute
* 
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
* 
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package htsjdk.variant.vcf;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.TestUtil;
import htsjdk.samtools.util.Tuple;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.readers.AsciiLineReader;
import htsjdk.tribble.readers.AsciiLineReaderIterator;
import htsjdk.tribble.readers.LineIteratorImpl;
import htsjdk.tribble.readers.SynchronousLineReader;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VCFVersionUpgradePolicy;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class VCFHeaderUnitTest extends VariantBaseTest {

    @DataProvider(name="headerRoundTrip")
    private Object[][] getHeaderRoundTrip() {
        return new Object[][] {
                { VCFHeaderUnitTestData.getVCFV42TestHeaderString() },
                { VCFHeaderUnitTestData.VCF42headerStrings_with_negativeOne }
        };
    }

    @Test(dataProvider = "headerRoundTrip")
    public void test42HeaderRoundTrip(final String headerString) throws IOException {
        final VCFHeader header = VCFHeaderUnitTestData.createHeaderFromString(headerString);
        Assert.assertEquals(header.getMetaDataInSortedOrder(), getRoundTripEncoded(header));
    }

    @Test
    public void test42FileRoundtrip() throws Exception {
        // this test validates that source/version fields are round-tripped properly

        // read an existing VCF
        final File expectedFile = new File("src/test/resources/htsjdk/variant/Vcf4.2WithSourceVersionInfoFields.vcf");

        // write the file out into a new copy
        final File actualFile = File.createTempFile("testVcf4.2roundtrip.", FileExtensions.VCF);
        actualFile.deleteOnExit();

        try (final VCFFileReader originalFileReader = new VCFFileReader(expectedFile.toPath(), false, VCFVersionUpgradePolicy.DO_NOT_UPGRADE);
             final VariantContextWriter copyWriter = new VariantContextWriterBuilder()
                     .setOutputFile(actualFile)
                     .setReferenceDictionary(createArtificialSequenceDictionary())
                     .setOptions(EnumSet.of(Options.ALLOW_MISSING_FIELDS_IN_HEADER, Options.INDEX_ON_THE_FLY))
                     .setVersionUpgradePolicy(VCFVersionUpgradePolicy.DO_NOT_UPGRADE)
                     .build()
        ) {
            final VCFHeader originalHeader = originalFileReader.getFileHeader();

            copyWriter.writeHeader(originalHeader);
            for (final VariantContext variantContext : originalFileReader) {
                copyWriter.add(variantContext);
            }
        }

        final String actualContents = new String(Files.readAllBytes(actualFile.toPath()), StandardCharsets.UTF_8);
        final String expectedContents = new String(Files.readAllBytes(expectedFile.toPath()), StandardCharsets.UTF_8);
        Assert.assertEquals(actualContents, expectedContents);
    }

    @Test
    public void testSampleRenamingSingleSample() throws Exception {
        final VCFCodec codec = new VCFCodec();
        codec.setRemappedSampleName("FOOSAMPLE");
        final AsciiLineReaderIterator vcfIterator = new AsciiLineReaderIterator(AsciiLineReader.from(new FileInputStream(variantTestDataRoot + "HiSeq.10000.vcf")));
        final VCFHeader header = (VCFHeader) codec.readHeader(vcfIterator).getHeaderValue();

        Assert.assertEquals(header.getNGenotypeSamples(), 1, "Wrong number of samples in remapped header");
        Assert.assertEquals(header.getGenotypeSamples().get(0), "FOOSAMPLE", "Sample name in remapped header has incorrect value");

        int recordCount = 0;
        while (vcfIterator.hasNext() && recordCount < 10) {
            recordCount++;
            final VariantContext vcfRecord = codec.decode(vcfIterator.next());

            Assert.assertEquals(vcfRecord.getSampleNames().size(), 1, "Wrong number of samples in vcf record after remapping");
            Assert.assertEquals(vcfRecord.getSampleNames().iterator().next(), "FOOSAMPLE", "Wrong sample in vcf record after remapping");
        }
    }

    @DataProvider(name="testSampleRenamingFailsTests")
    public Object[][] testSampleRenamingFailsTests() {
        return new Object[][]{
                {variantTestDataRoot + "ex2.vcf"},                  // multi sample vcf
                {variantTestDataRoot + "dbsnp_135.b37.1000.vcf"}    // sites only vcf
        };
    }

    @Test(dataProvider = "testSampleRenamingFailsTests", expectedExceptions = TribbleException.class)
    public void testSampleRenamingFails(final String fileName) throws IOException {
        final VCFCodec codec = new VCFCodec();
        codec.setRemappedSampleName("FOOSAMPLE");
        final AsciiLineReaderIterator vcfIterator = new AsciiLineReaderIterator(
                AsciiLineReader.from(new FileInputStream(fileName)));
        codec.readHeader(vcfIterator).getHeaderValue();
    }

    @Test
    public void testAddInfoLine() {
        final VCFHeader header = getHiSeqVCFHeader();
        final VCFInfoHeaderLine infoLine = new VCFInfoHeaderLine("TestInfoLine", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "test info line");
        header.addMetaDataLine(infoLine);

        Assert.assertTrue(header.getInfoHeaderLines().contains(infoLine), "TestInfoLine not found in info header lines");
        Assert.assertTrue(header.getMetaDataInInputOrder().contains(infoLine), "TestInfoLine not found in set of all header lines");
        Assert.assertNotNull(header.getInfoHeaderLine("TestInfoLine"), "Lookup for TestInfoLine by key failed");

        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getFormatHeaderLines()).contains(infoLine), "TestInfoLine present in format header lines");
        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getFilterLines()).contains(infoLine), "TestInfoLine present in filter header lines");
        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getContigLines()).contains(infoLine), "TestInfoLine present in contig header lines");
        Assert.assertFalse(header.getOtherHeaderLines().contains(infoLine), "TestInfoLine present in other header lines");
    }

    @Test
    public void testAddFormatLine() {
        final VCFHeader header = getHiSeqVCFHeader();
        final VCFFormatHeaderLine formatLine = new VCFFormatHeaderLine("TestFormatLine", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "test format line");
        header.addMetaDataLine(formatLine);

        Assert.assertTrue(header.getFormatHeaderLines().contains(formatLine), "TestFormatLine not found in format header lines");
        Assert.assertTrue(header.getMetaDataInInputOrder().contains(formatLine), "TestFormatLine not found in set of all header lines");
        Assert.assertNotNull(header.getFormatHeaderLine("TestFormatLine"), "Lookup for TestFormatLine by key failed");

        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getInfoHeaderLines()).contains(formatLine), "TestFormatLine present in info header lines");
        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getFilterLines()).contains(formatLine), "TestFormatLine present in filter header lines");
        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getContigLines()).contains(formatLine), "TestFormatLine present in contig header lines");
        Assert.assertFalse(header.getOtherHeaderLines().contains(formatLine), "TestFormatLine present in other header lines");
    }

    @Test
    public void testAddFilterLine() {
        final VCFHeader header = getHiSeqVCFHeader();
        final String filterDesc = "TestFilterLine Description";
        final VCFFilterHeaderLine filterLine = new VCFFilterHeaderLine("TestFilterLine", filterDesc);
        Assert.assertEquals(filterDesc, filterLine.getDescription());
        header.addMetaDataLine(filterLine);

        Assert.assertTrue(header.getFilterLines().contains(filterLine), "TestFilterLine not found in filter header lines");
        Assert.assertTrue(header.getMetaDataInInputOrder().contains(filterLine), "TestFilterLine not found in set of all header lines");
        Assert.assertNotNull(header.getFilterHeaderLine("TestFilterLine"), "Lookup for TestFilterLine by key failed");

        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getInfoHeaderLines()).contains(filterLine), "TestFilterLine present in info header lines");
        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getFormatHeaderLines()).contains(filterLine), "TestFilterLine present in format header lines");
        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getContigLines()).contains(filterLine), "TestFilterLine present in contig header lines");
        Assert.assertFalse(header.getOtherHeaderLines().contains(filterLine), "TestFilterLine present in other header lines");
    }

    @Test
    public void testAddContigLine() {
        final VCFHeader header = getHiSeqVCFHeader();
        // no contig lines in this header
        Assert.assertTrue(header.getContigLines().isEmpty());

        final VCFContigHeaderLine contigLine = new VCFContigHeaderLine(
                "<ID=chr1,length=1234567890,assembly=FAKE,md5=f126cdf8a6e0c7f379d618ff66beb2da,species=\"Homo sapiens\">", VCFHeaderVersion.VCF4_0, 0);
        Assert.assertEquals(contigLine.getKey(), VCFHeader.CONTIG_KEY);
        Assert.assertEquals(contigLine.getID(), "chr1");
        header.addMetaDataLine(contigLine);

        Assert.assertTrue(header.getContigLines().contains(contigLine), "Test contig line not found in contig header lines");
        Assert.assertTrue(header.getMetaDataInInputOrder().contains(contigLine), "Test contig line not found in set of all header lines");

        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getInfoHeaderLines()).contains(contigLine), "Test contig line present in info header lines");
        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getFormatHeaderLines()).contains(contigLine), "Test contig line present in format header lines");
        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getFilterLines()).contains(contigLine), "Test contig line present in filter header lines");
        Assert.assertFalse(header.getOtherHeaderLines().contains(contigLine), "Test contig line present in other header lines");
    }

    @Test
    public void testAddContigLineExactDuplicateSilentlyDropped() {
        final File input = new File("src/test/resources/htsjdk/variant/ex2.vcf");

        final VCFFileReader reader = new VCFFileReader(input, false);
        final VCFHeader header = reader.getFileHeader();

        final int numContigLinesBefore = header.getContigLines().size();
        // try to read the first contig line
        header.addMetaDataLine(header.getContigLines().get(0));
        final int numContigLinesAfter = header.getContigLines().size();

        // assert that we have the same number of contig lines before and after
        Assert.assertEquals(numContigLinesBefore, numContigLinesAfter);
    }

    @Test
    public void testAddContigLineWithDifferentAttributesSilentlyDropped() {
        final VCFContigHeaderLine contigOneNoAssembly = new VCFContigHeaderLine(
                new LinkedHashMap<String, String>() {{
                    put("ID", "1");
                    put("length", "123");
                }},
                0);
        final VCFContigHeaderLine contigOneWithAssembly = new VCFContigHeaderLine(
                new LinkedHashMap<String, String>() {{
                    put("ID", "1");
                    put("length", "123");
                    put("assembly", "b37");
                }},
                1);
        Assert.assertNotEquals(contigOneNoAssembly.hashCode(), contigOneWithAssembly.hashCode());

        final Set<VCFHeaderLine> headerLineSet = VCFHeader.makeHeaderVersionLineSet(VCFHeader.DEFAULT_VCF_VERSION);
        headerLineSet.add(contigOneNoAssembly);
        headerLineSet.add(contigOneWithAssembly);
        Assert.assertEquals(headerLineSet.size(), 3); // one fileformat line, plus 2 contig lines

        // silently drops contigOneNoAssembly since it has the same ID AND contig index as contigOneWithAssembly
        final VCFHeader vcfHeader = new VCFHeader(headerLineSet);
        final Set<VCFHeaderLine> allMetaDataInput = vcfHeader.getMetaDataInInputOrder();
        Assert.assertEquals(allMetaDataInput.size(), 2);
        final Set<VCFHeaderLine> allMetaDataSorted = vcfHeader.getMetaDataInSortedOrder();
        Assert.assertEquals(allMetaDataSorted.size(), 2);
        final List<VCFContigHeaderLine> allContigLines = vcfHeader.getContigLines();
        Assert.assertEquals(allContigLines.size(), 1);      // one contig
        Assert.assertEquals(allContigLines.get(0).getGenericFieldValue("assembly"), "b37");
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testAddContigLineRejectDuplicateContigIndex() {
        final VCFHeader header = new VCFHeader();
        // add two contig lines that share an index, but have different IDs and represetn different contifs
        final VCFContigHeaderLine contigLine1 = new VCFContigHeaderLine("<ID=chr1,length=10>", VCFHeaderVersion.VCF4_2, 0);
        final VCFContigHeaderLine contigLine2 = new VCFContigHeaderLine("<ID=chr2,length=10>", VCFHeaderVersion.VCF4_2, 0);

        header.addMetaDataLine(contigLine1);
        header.addMetaDataLine(contigLine2);
    }

    @Test
    public void testAddContigLineMissingLength() {
        final VCFHeader header = getHiSeqVCFHeader();
        final VCFContigHeaderLine contigLine = new VCFContigHeaderLine(
                "<ID=chr1>", VCFHeaderVersion.VCF4_0, 0);
        header.addMetaDataLine(contigLine);
        Assert.assertTrue(header.getContigLines().contains(contigLine), "Test contig line not found in contig header lines");
        Assert.assertTrue(header.getMetaDataInInputOrder().contains(contigLine), "Test contig line not found in set of all header lines");

        final SAMSequenceDictionary sequenceDictionary = header.getSequenceDictionary();
        Assert.assertNotNull(sequenceDictionary);
        Assert.assertEquals(sequenceDictionary.getSequence("chr1").getSequenceLength(), SAMSequenceRecord.UNKNOWN_SEQUENCE_LENGTH);
    }

    @Test
    public void testGetContigLinesHonorsSortOrder() {
        // NOTE: this test file has *lexicographically* ordered contigs
        try (final VCFFileReader vcfReader = new VCFFileReader(new File(variantTestDataRoot + "dbsnp_135.b37.1000.vcf"), false)) {
            // start with a header with a bunch of contig lines
            final VCFHeader header = vcfReader.getFileHeader();
            final List<VCFContigHeaderLine> originalContigsInSortedOrder = header.getContigLines();
            Assert.assertTrue(originalContigsInSortedOrder.size() > 0);

            // copy the contig lines to a new list
            final int midPoint = originalContigsInSortedOrder.size() / 2;
            final List<VCFContigHeaderLine> confoundedList = new ArrayList<>(originalContigsInSortedOrder.subList(
                0,
                midPoint
            ));

            // deliberately stick an extra contig line in the middle of the list, but using a contig index
            // that will cause the line to sort to the end
            final String newContigID = "newContigID";
            final int newContigIndex = originalContigsInSortedOrder.size();
            final VCFContigHeaderLine newContigLine = new VCFContigHeaderLine(
                    String.format(
                    "<ID=%s,length=1234567890,assembly=FAKE,md5=f126cdf8a6e0c7f379d618ff66beb2da,species=\"Homo sapiens\">", newContigID),
                    VCFHeaderVersion.VCF4_2,
                    newContigIndex);
            confoundedList.add(newContigLine);
            confoundedList.addAll(originalContigsInSortedOrder.subList(midPoint, originalContigsInSortedOrder.size()));

            // make sure the new contig line was actually added
            Assert.assertEquals(originalContigsInSortedOrder.size() + 1, confoundedList.size());
            Assert.assertTrue(confoundedList.contains(newContigLine));

            // create a new header from the confounded list, call getContigLines() on the header, and validate
            // that the new line is included in the resulting list, and is at the end
            final VCFHeader newHeader = new VCFHeader();
            confoundedList.forEach(newHeader::addMetaDataLine);
            final List<VCFContigHeaderLine> roundTrippedLines = newHeader.getContigLines();
            Assert.assertEquals(roundTrippedLines.size(), originalContigsInSortedOrder.size() + 1);
            Assert.assertEquals(roundTrippedLines.get(roundTrippedLines.size() - 1), newContigLine);

            // make sure the sequence dictionary has the contig with the correct contig index, and in
            // the same relative location in the dictionary (at the end of the list)
            final SAMSequenceDictionary orderedSeqDict = newHeader.getSequenceDictionary();
            Assert.assertEquals(
                    orderedSeqDict.getSequence(newContigID).getSequenceIndex(),
                    roundTrippedLines.size() - 1);
            Assert.assertEquals(
                    orderedSeqDict.getSequences().get(newHeader.getContigLines().size() - 1).getSequenceName(),
                    newContigID);
        }
    }

    @Test
    public void testAddOtherLine() {
        final VCFHeader header = getHiSeqVCFHeader();
        final VCFHeaderLine otherLine = new VCFHeaderLine("TestOtherLine", "val");
        header.addMetaDataLine(otherLine);

        Assert.assertTrue(header.getOtherHeaderLines().contains(otherLine), "TestOtherLine not found in other header lines");
        Assert.assertTrue(header.getMetaDataInInputOrder().contains(otherLine), "TestOtherLine not found in set of all header lines");

        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getInfoHeaderLines()).contains(otherLine), "TestOtherLine present in info header lines");
        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getFormatHeaderLines()).contains(otherLine), "TestOtherLine present in format header lines");
        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getContigLines()).contains(otherLine), "TestOtherLine present in contig header lines");
        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getFilterLines()).contains(otherLine), "TestOtherLine present in filter header lines");
    }

    @Test
    public void testAddMetaDataLineDoesNotDuplicateContigs() {
        final File input = new File("src/test/resources/htsjdk/variant/ex2.vcf");

        final VCFFileReader reader = new VCFFileReader(input, false);
        final VCFHeader header = reader.getFileHeader();

        final int numContigLinesBefore = header.getContigLines().size();

        final VCFInfoHeaderLine newInfoField = new VCFInfoHeaderLine(
                "test", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "test info field");
        header.addMetaDataLine(newInfoField);

        // getting the sequence dictionary was failing due to duplicating contigs in issue #214,
        // we expect this to not throw an exception
        header.getSequenceDictionary();

        final int numContigLinesAfter = header.getContigLines().size();
        // assert that we have the same number of contig lines before and after
        Assert.assertEquals(numContigLinesBefore, numContigLinesAfter);
    }

    @Test
    public void testAddDuplicateKeyValueHeaderLine() {
        final File input = new File("src/test/resources/htsjdk/variant/ex2.vcf");

        final VCFFileReader reader = new VCFFileReader(input, false);
        final VCFHeader header = reader.getFileHeader();

        final VCFHeaderLine newHeaderLine = new VCFHeaderLine("key", "value");
        // add this new header line
        header.addMetaDataLine(newHeaderLine);

        final int numHeaderLinesBefore = header.getOtherHeaderLines().size();
        // add the same header line again
        header.addMetaDataLine(newHeaderLine);
        final int numHeaderLinesAfter = header.getOtherHeaderLines().size();

        // Note: we don't allow duplicate unstructured lines with the same key unless they have
        // different content
        // assert that we have the one more other header line after
        Assert.assertEquals(numHeaderLinesBefore, numHeaderLinesAfter);
    }

    @Test
    public void testSimpleHeaderLineGenericFieldGetter() {
        final VCFHeader header = VCFHeaderUnitTestData.createHeaderFromString(VCFHeaderUnitTestData.getVCFV42TestHeaderString());
        final List<VCFFilterHeaderLine> filters = header.getFilterLines();
        final VCFFilterHeaderLine filterHeaderLine = filters.get(0);
        final Map<String,String> genericFields = filterHeaderLine.getGenericFields();
        Assert.assertEquals(genericFields.get("ID"),"NoQCALL");
        Assert.assertEquals(genericFields.get("Description"),"Variant called by Dindel but not confirmed by QCALL");
    }

    @Test
    public void testSerialization() throws Exception {
        final VCFFileReader reader = new VCFFileReader(new File("src/test/resources/htsjdk/variant/HiSeq.10000.vcf"), false);
        final VCFHeader originalHeader = reader.getFileHeader();
        reader.close();

        final VCFHeader deserializedHeader = TestUtil.serializeAndDeserialize(originalHeader);

        Assert.assertEquals(deserializedHeader.getMetaDataInInputOrder(), originalHeader.getMetaDataInInputOrder(), "Header metadata does not match before/after serialization");
        Assert.assertEquals(deserializedHeader.getContigLines(), originalHeader.getContigLines(), "Contig header lines do not match before/after serialization");
        Assert.assertEquals(deserializedHeader.getFilterLines(), originalHeader.getFilterLines(), "Filter header lines do not match before/after serialization");
        Assert.assertEquals(deserializedHeader.getFormatHeaderLines(), originalHeader.getFormatHeaderLines(), "Format header lines do not match before/after serialization");
        Assert.assertEquals(deserializedHeader.getIDHeaderLines(), originalHeader.getIDHeaderLines(), "ID header lines do not match before/after serialization");
        Assert.assertEquals(deserializedHeader.getInfoHeaderLines(), originalHeader.getInfoHeaderLines(), "Info header lines do not match before/after serialization");
        Assert.assertEquals(deserializedHeader.getOtherHeaderLines(), originalHeader.getOtherHeaderLines(), "Other header lines do not match before/after serialization");
        Assert.assertEquals(deserializedHeader.getGenotypeSamples(), originalHeader.getGenotypeSamples(), "Genotype samples not the same before/after serialization");
        Assert.assertEquals(deserializedHeader.samplesWereAlreadySorted(), originalHeader.samplesWereAlreadySorted(), "Sortedness of samples not the same before/after serialization");
        Assert.assertEquals(deserializedHeader.getSampleNamesInOrder(), originalHeader.getSampleNamesInOrder(), "Sorted list of sample names in header not the same before/after serialization");
        Assert.assertEquals(deserializedHeader.getSampleNameToOffset(), originalHeader.getSampleNameToOffset(), "Sample name to offset map not the same before/after serialization");
        Assert.assertEquals(deserializedHeader.toString(), originalHeader.toString(), "String representation of header not the same before/after serialization");
    }

    @DataProvider(name="validHeaderVersionTransitions")
    public Object[][] validHeaderVersionTransitions() {
        // all (forward) version transitions are allowed
        return new Object[][] {
                {VCFHeaderVersion.VCF3_2, VCFHeaderVersion.VCF3_3},
                {VCFHeaderVersion.VCF3_2, VCFHeaderVersion.VCF4_0},
                {VCFHeaderVersion.VCF3_2, VCFHeaderVersion.VCF4_1},
                {VCFHeaderVersion.VCF3_2, VCFHeaderVersion.VCF4_2},
                {VCFHeaderVersion.VCF3_2, VCFHeaderVersion.VCF4_3},

                {VCFHeaderVersion.VCF3_3, VCFHeaderVersion.VCF4_0},
                {VCFHeaderVersion.VCF3_3, VCFHeaderVersion.VCF4_1},
                {VCFHeaderVersion.VCF3_3, VCFHeaderVersion.VCF4_2},
                {VCFHeaderVersion.VCF3_3, VCFHeaderVersion.VCF4_3},

                {VCFHeaderVersion.VCF4_0, VCFHeaderVersion.VCF4_0},
                {VCFHeaderVersion.VCF4_0, VCFHeaderVersion.VCF4_1},
                {VCFHeaderVersion.VCF4_0, VCFHeaderVersion.VCF4_2},
                {VCFHeaderVersion.VCF4_0, VCFHeaderVersion.VCF4_3},

                {VCFHeaderVersion.VCF4_1, VCFHeaderVersion.VCF4_1},
                {VCFHeaderVersion.VCF4_1, VCFHeaderVersion.VCF4_2},
                {VCFHeaderVersion.VCF4_1, VCFHeaderVersion.VCF4_3},

                {VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF4_2},
                {VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF4_3},

                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_3}
        };
    }

    @DataProvider(name="invalidHeaderVersionTransitions")
    public Object[][] invalidHeaderVersionTransitions() {
        // v4.3 can never be transitioned down to pre v4.3
        // Pre v4.3 might be able to be transitioned to 4.3, and this is tested in VCFCodec43FeaturesTest
        return new Object[][] {
                //reject any attempt to go backwards in time
                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_2},
                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_1},
                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_0},
                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF3_3},
                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF3_2},

                {VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF4_1},
                {VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF4_0},
                {VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF3_3},
                {VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF3_2},

                {VCFHeaderVersion.VCF4_1, VCFHeaderVersion.VCF4_0},
                {VCFHeaderVersion.VCF4_1, VCFHeaderVersion.VCF3_3},
                {VCFHeaderVersion.VCF4_1, VCFHeaderVersion.VCF3_2},

                {VCFHeaderVersion.VCF4_0, VCFHeaderVersion.VCF3_3},
                {VCFHeaderVersion.VCF4_0, VCFHeaderVersion.VCF3_2},

                {VCFHeaderVersion.VCF3_3, VCFHeaderVersion.VCF3_2},
        };
    }

    @Test(dataProvider="validHeaderVersionTransitions")
    public void testAddVersionLineValidTransition(final VCFHeaderVersion fromVersion, final VCFHeaderVersion toVersion) {
        final VCFHeader vcfHeader = new VCFHeader(VCFHeader.makeHeaderVersionLineSet(fromVersion), Collections.emptySet());
        vcfHeader.addMetaDataLine(VCFHeader.makeHeaderVersionLine(toVersion));
        Assert.assertEquals(vcfHeader.getVCFHeaderVersion(), toVersion);
    }

    @Test(dataProvider="invalidHeaderVersionTransitions", expectedExceptions = TribbleException.class)
    public void testAddVersionInvalidTransition(final VCFHeaderVersion fromVersion, final VCFHeaderVersion toVersion) {
        new VCFHeader(VCFHeader.makeHeaderVersionLineSet(fromVersion), Collections.emptySet())
                .addMetaDataLine(VCFHeader.makeHeaderVersionLine(toVersion));
    }

    @DataProvider(name = "vcfVersions")
    public Object[][] vcfVersions() {
        return new Object[][]{
                {VCFHeaderVersion.VCF3_2},
                {VCFHeaderVersion.VCF3_3},
                {VCFHeaderVersion.VCF4_0},
                {VCFHeaderVersion.VCF4_1},
                {VCFHeaderVersion.VCF4_2},
                {VCFHeaderVersion.VCF4_3}
        };
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testVersionUpgradeWithValidationFailure() {
        // test mixing header versions where the old version header has a line that fails validation
        // using the resulting (newer) version

        // create a 4.2 header with a 4.2 style pedigree line (one that has no ID)
        final VCFHeader vcfHeader = new VCFHeader(VCFHeader.makeHeaderVersionLineSet(VCFHeaderVersion.VCF4_2));
        vcfHeader.addMetaDataLine(new VCFHeaderLine(VCFConstants.PEDIGREE_HEADER_KEY, "<Name_0=G0-ID,Name_1=G1-ID>"));

        // now try to force a version upgrade to 4.3, old style pedigree line should cause a failure
        vcfHeader.addMetaDataLine(VCFHeader.makeHeaderVersionLine(VCFHeaderVersion.VCF4_3));
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testConstructorRequiresFileFormatLine() {
        final Set<VCFHeaderLine> metaDataSet = VCFHeaderUnitTestData.getV42HeaderLinesWITHOUTFormatString(); // 4.2 header is compatible with all 4.x versions
        // create a new header from this set (containing no fileformat line), no requested version in constructor
        new VCFHeader(metaDataSet, Collections.emptySet()); //defaults to v4.2
    }

    @Test(dataProvider = "vcfVersions")
    public void testConstructorWithSingleFileFormatLine(final VCFHeaderVersion vcfVersion) {
        final Set<VCFHeaderLine> metaDataSet = VCFHeaderUnitTestData.getV42HeaderLinesWITHOUTFormatString(); // 4.2 header is compatible with all 4.x versions

        // add in the corresponding fileformat line; create a new versioned header
        // since the version requested in the constructor and the format lines are in sync, there is
        // no conflict, and the resulting header's version should always match the requested version
        metaDataSet.add(VCFHeader.makeHeaderVersionLine(vcfVersion));
        final VCFHeader vcfHeader = new VCFHeader(metaDataSet, Collections.emptySet());
        Assert.assertEquals(vcfHeader.getVCFHeaderVersion(), vcfVersion);
    }

    @Test
    public void testConstructorWithMultipleFileFormatLines() {
        final Set<VCFHeaderLine> metaDataSet = VCFHeaderUnitTestData.getV42HeaderLinesWITHOUTFormatString(); // this (4.2) header is compatible with all 4.x versions
        final int beforeSize = metaDataSet.size();

        // multiple version lines will be ignored, with only the last one retained
        metaDataSet.add(VCFHeader.makeHeaderVersionLine(VCFHeaderVersion.VCF4_2));
        metaDataSet.add(VCFHeader.makeHeaderVersionLine(VCFHeaderVersion.VCF4_1));
        Assert.assertEquals(metaDataSet.size(), beforeSize + 2);

        // create a new versioned header from this set
        final VCFHeader vcfHeader = new VCFHeader(metaDataSet, Collections.emptySet());
        Assert.assertEquals(vcfHeader.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_1);
    }

    @Test(expectedExceptions = TribbleException.VersionValidationFailure.class)
    public void testConstructorWithInvalidLineForVersion() {
        final Set<VCFHeaderLine> metaDataSet = VCFHeaderUnitTestData.getV42HeaderLinesWITHOUTFormatString(); // this (4.2) header is compatible with all 4.x versions
        metaDataSet.add(VCFHeader.makeHeaderVersionLine(VCFHeaderVersion.VCF4_2));
        final Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("ID", "id");
        metaDataSet.add(new VCFPedigreeHeaderLine(attributes));
        new VCFHeader(metaDataSet, Collections.emptySet());
    }

    @Test(expectedExceptions = TribbleException.VersionValidationFailure.class)
    public void testAddMetaDataLineInvalidForVersion() {
        final Set<VCFHeaderLine> metaDataSet = VCFHeaderUnitTestData.getV42HeaderLinesWITHOUTFormatString(); // this (4.2) header is compatible with all 4.x versions
        metaDataSet.add(VCFHeader.makeHeaderVersionLine(VCFHeaderVersion.VCF4_2));
        final VCFHeader header = new VCFHeader(metaDataSet, Collections.emptySet());
        Assert.assertEquals(header.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_2);
        final Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("ID", "id");
        header.addMetaDataLine(new VCFPedigreeHeaderLine(attributes));
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testAddMetaDataLineWithValidationFailure() {
        // create a 4.3 header, and then try to add an old-style pedigree line (one that has no ID)
        // which should cause a failure
        final VCFHeader vcfHeader = new VCFHeader(VCFHeader.makeHeaderVersionLineSet(VCFHeaderVersion.VCF4_3));
        vcfHeader.addMetaDataLine(new VCFHeaderLine(VCFConstants.PEDIGREE_HEADER_KEY, "<Name_0=G0-ID,Name_1=G1-ID>"));
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testAddMetaDataLineFileFormat() {
        final Set<VCFHeaderLine> metaDataSet = VCFHeaderUnitTestData.getV42HeaderLinesWITHOUTFormatString(); // this (4.2) header is compatible with all 4.x versions
        final int beforeSize = metaDataSet.size();

        metaDataSet.add(VCFHeader.makeHeaderVersionLine(VCFHeaderVersion.VCF4_1));
        Assert.assertEquals(metaDataSet.size(), beforeSize + 1);

        // create a new versioned header from this set
        final VCFHeader vcfHeader = new VCFHeader(metaDataSet, Collections.emptySet());
        Assert.assertEquals(vcfHeader.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_1);

        // add a new line that uses the same header version already established
        vcfHeader.addMetaDataLine(VCFHeader.makeHeaderVersionLine(VCFHeaderVersion.VCF4_1));

        // add a new line that tries to move the version forward
        vcfHeader.addMetaDataLine(VCFHeader.makeHeaderVersionLine(VCFHeaderVersion.VCF4_2));

        // now try to go backwards (throws)
        vcfHeader.addMetaDataLine(VCFHeader.makeHeaderVersionLine(VCFHeaderVersion.VCF4_1));
    }

    @Test
    public void testFileFormatLineFirstInSet() {
        final Set<VCFHeaderLine> orderedLineSet = new LinkedHashSet<>(VCFHeaderUnitTestData.getV42HeaderLinesWITHOUTFormatString());
        orderedLineSet.forEach(l -> Assert.assertFalse(VCFHeaderVersion.isFormatString(l.getKey())));
        // add the file format line last
        orderedLineSet.add(VCFHeader.makeHeaderVersionLine(VCFHeader.DEFAULT_VCF_VERSION));
        final VCFHeader vcfHeader = new VCFHeader(orderedLineSet, Collections.emptySet());

        final Collection<VCFHeaderLine> inputOrderLines = vcfHeader.getMetaDataInInputOrder();
        final Optional<VCFHeaderLine> optFirstInputOrderLine = inputOrderLines.stream().findFirst();
        Assert.assertTrue(optFirstInputOrderLine.isPresent());
        Assert.assertTrue(VCFHeaderVersion.isFormatString(optFirstInputOrderLine.get().getKey()));

        final Collection<VCFHeaderLine> sortedOrderLines = vcfHeader.getMetaDataInSortedOrder();
        final Optional<VCFHeaderLine> optFirstSortedOrderLine = sortedOrderLines.stream().findFirst();
        Assert.assertTrue(optFirstSortedOrderLine.isPresent());
        Assert.assertTrue(VCFHeaderVersion.isFormatString(optFirstSortedOrderLine.get().getKey()));
    }

    @Test
    public void testPreserveSequenceDictionaryAttributes() {
        // Round trip a SAMSequenceDictionary with attributes, through a VCFHeader, and back
        // to a SAMSequenceDictionary with the same attributes.
        // https://github.com/samtools/htsjdk/issues/730

        final String assemblyString = "hg37";
        final String md5String = "68b329da9893e34099c7d8ad5cb9c940";
        final String speciesString = "Home Sapiens";
        final String urlString = "http://www.refserve.org:8080/path/";

        final SAMSequenceDictionary samDict = new SAMSequenceDictionary();

        final SAMSequenceRecord seqRec1 = new SAMSequenceRecord("1", 1);
        seqRec1.setAssembly(assemblyString);
        seqRec1.setMd5(md5String);
        seqRec1.setAttribute(SAMSequenceRecord.URI_TAG, urlString);
        seqRec1.setSpecies(speciesString);
        final SAMSequenceRecord seqRec2 = new SAMSequenceRecord("2", 1);
        samDict.addSequence(seqRec1);
        samDict.addSequence(seqRec2);

        final VCFHeader vcfHeader = new VCFHeader();
        vcfHeader.setSequenceDictionary(samDict);
        final SAMSequenceDictionary roundTrippedDict = vcfHeader.getSequenceDictionary();

        final SAMSequenceRecord rtRec1 = roundTrippedDict.getSequence("1");
        Assert.assertEquals(assemblyString, rtRec1.getAssembly());
        Assert.assertEquals(md5String, rtRec1.getMd5());
        Assert.assertEquals(urlString, rtRec1.getAttribute(SAMSequenceRecord.URI_TAG));
        Assert.assertEquals(speciesString, rtRec1.getSpecies());

        Assert.assertEquals(seqRec1, roundTrippedDict.getSequence("1")); // somewhat redundant check on full record
        Assert.assertEquals(seqRec2, roundTrippedDict.getSequence("2"));
    }

    /////////////////////////////////////////////////////////////////
    ////////////////************************* End new tests block...
    /////////////////////////////////////////////////////////////////

    @Test
    public void testVCFHeaderQuoteEscaping() throws Exception {
        // this test ensures that the end-to-end process of quote escaping is stable when headers are
        // read and re-written; ie that quotes that are already escaped won't be re-escaped. It does
        // this by reading a test file, adding a header line with an unescaped quote, writing out a copy
        // of the file, reading it back in and writing a second copy, and finally reading back the second
        // copy and comparing it to the first.

        // read an existing VCF
        final VCFFileReader originalFileReader = new VCFFileReader(new File("src/test/resources/htsjdk/variant/VCF4HeaderTest.vcf"), false);
        final VCFHeader originalHeader = originalFileReader.getFileHeader();

        // add a header line with quotes to the header
        final VCFSimpleHeaderLine addedHeaderLine = new VCFFilterHeaderLine(
                "FakeFilter",
                "filterName=[ANNOTATION] filterExpression=[ANNOTATION == \"NA\" || ANNOTATION <= 2.0]");
        originalHeader.addMetaDataLine(addedHeaderLine);

        final VCFFilterHeaderLine originalCopyAnnotationLine1 = originalHeader.getFilterHeaderLine("ANNOTATION");
        Assert.assertNotNull(originalCopyAnnotationLine1);
        Assert.assertEquals(originalCopyAnnotationLine1.getGenericFieldValue("Description"), "ANNOTATION != \"NA\" || ANNOTATION <= 0.01", originalCopyAnnotationLine1.toString());

        final VCFFilterHeaderLine originalCopyAnnotationLine2 = originalHeader.getFilterHeaderLine("ANNOTATION2");
        Assert.assertNotNull(originalCopyAnnotationLine2);
        Assert.assertEquals(originalCopyAnnotationLine2.getGenericFieldValue("Description"), "ANNOTATION with quote \" that is unmatched but escaped");

        final VCFInfoHeaderLine originalEscapingQuoteInfoLine = originalHeader.getInfoHeaderLine("EscapingQuote");
        Assert.assertNotNull(originalEscapingQuoteInfoLine);
        Assert.assertEquals(originalEscapingQuoteInfoLine.getDescription(), "This description has an escaped \" quote in it");

        final VCFInfoHeaderLine originalEscapingBackslashInfoLine = originalHeader.getInfoHeaderLine("EscapingBackslash");
        Assert.assertNotNull(originalEscapingBackslashInfoLine);
        Assert.assertEquals(originalEscapingBackslashInfoLine.getDescription(), "This description has an escaped \\ backslash in it");

        final VCFInfoHeaderLine originalEscapingNonQuoteOrBackslashInfoLine = originalHeader.getInfoHeaderLine("EscapingNonQuoteOrBackslash");
        Assert.assertNotNull(originalEscapingNonQuoteOrBackslashInfoLine);
        Assert.assertEquals(originalEscapingNonQuoteOrBackslashInfoLine.getDescription(), "This other value has a \\n newline in it");

        // write the file out into a new copy
        final File firstCopyVCFFile = File.createTempFile("testEscapeHeaderQuotes1.", FileExtensions.VCF);
        firstCopyVCFFile.deleteOnExit();

        final VariantContextWriter firstCopyWriter = new VariantContextWriterBuilder()
                .setOutputFile(firstCopyVCFFile)
                .setReferenceDictionary(createArtificialSequenceDictionary())
                .setOptions(EnumSet.of(Options.ALLOW_MISSING_FIELDS_IN_HEADER, Options.INDEX_ON_THE_FLY))
                .build();
        firstCopyWriter.writeHeader(originalHeader);
        for (final VariantContext variantContext : originalFileReader) {
            firstCopyWriter.add(variantContext);
        }
        originalFileReader.close();
        firstCopyWriter.close();

        // read the copied file back in
        final VCFFileReader firstCopyReader = new VCFFileReader(firstCopyVCFFile, false);
        final VCFHeader firstCopyHeader = firstCopyReader.getFileHeader();
        final VCFFilterHeaderLine firstCopyNewHeaderLine = firstCopyHeader.getFilterHeaderLine("FakeFilter");
        Assert.assertNotNull(firstCopyNewHeaderLine);

        final VCFFilterHeaderLine firstCopyAnnotationLine1 = firstCopyHeader.getFilterHeaderLine("ANNOTATION");
        Assert.assertNotNull(firstCopyAnnotationLine1);
        Assert.assertEquals(firstCopyAnnotationLine1.getGenericFieldValue("Description"), "ANNOTATION != \"NA\" || ANNOTATION <= 0.01");

        final VCFFilterHeaderLine firstCopyAnnotationLine2 = firstCopyHeader.getFilterHeaderLine("ANNOTATION2");
        Assert.assertNotNull(firstCopyAnnotationLine2);

        final VCFInfoHeaderLine firstCopyEscapingQuoteInfoLine = firstCopyHeader.getInfoHeaderLine("EscapingQuote");
        Assert.assertNotNull(firstCopyEscapingQuoteInfoLine);
        Assert.assertEquals(firstCopyEscapingQuoteInfoLine.getDescription(), "This description has an escaped \" quote in it");

        final VCFInfoHeaderLine firstCopyEscapingBackslashInfoLine = firstCopyHeader.getInfoHeaderLine("EscapingBackslash");
        Assert.assertNotNull(firstCopyEscapingBackslashInfoLine);
        Assert.assertEquals(firstCopyEscapingBackslashInfoLine.getDescription(), "This description has an escaped \\ backslash in it");

        final VCFInfoHeaderLine firstCopyEscapingNonQuoteOrBackslashInfoLine = firstCopyHeader.getInfoHeaderLine("EscapingNonQuoteOrBackslash");
        Assert.assertNotNull(firstCopyEscapingNonQuoteOrBackslashInfoLine);
        Assert.assertEquals(firstCopyEscapingNonQuoteOrBackslashInfoLine.getDescription(), "This other value has a \\n newline in it");


        // write one more copy to make sure things don't get double escaped
        final File secondCopyVCFFile = File.createTempFile("testEscapeHeaderQuotes2.", FileExtensions.VCF);
        secondCopyVCFFile.deleteOnExit();
        final VariantContextWriter secondCopyWriter = new VariantContextWriterBuilder()
                .setOutputFile(secondCopyVCFFile)
                .setReferenceDictionary(createArtificialSequenceDictionary())
                .setOptions(EnumSet.of(Options.ALLOW_MISSING_FIELDS_IN_HEADER, Options.INDEX_ON_THE_FLY))
                .build();
        secondCopyWriter.writeHeader(firstCopyHeader);
        for (final VariantContext variantContext : firstCopyReader) {
            secondCopyWriter.add(variantContext);
        }
        secondCopyWriter.close();

        // read the second copy back in and verify that the two files have the same header line
        final VCFFileReader secondCopyReader = new VCFFileReader(secondCopyVCFFile, false);
        final VCFHeader secondCopyHeader = secondCopyReader.getFileHeader();

        final VCFFilterHeaderLine secondCopyNewHeaderLine = secondCopyHeader.getFilterHeaderLine("FakeFilter");
        Assert.assertNotNull(secondCopyNewHeaderLine);

        final VCFFilterHeaderLine secondCopyAnnotationLine1 = secondCopyHeader.getFilterHeaderLine("ANNOTATION");
        Assert.assertNotNull(secondCopyAnnotationLine1);

        final VCFFilterHeaderLine secondCopyAnnotationLine2 = secondCopyHeader.getFilterHeaderLine("ANNOTATION2");
        Assert.assertNotNull(secondCopyAnnotationLine2);

        Assert.assertEquals(firstCopyNewHeaderLine, secondCopyNewHeaderLine);
        Assert.assertEquals(firstCopyNewHeaderLine.toStringEncoding(), "FILTER=<ID=FakeFilter,Description=\"filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]\">");
        Assert.assertEquals(secondCopyNewHeaderLine.toStringEncoding(), "FILTER=<ID=FakeFilter,Description=\"filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]\">");

        Assert.assertEquals(firstCopyAnnotationLine1, secondCopyAnnotationLine1);
        Assert.assertEquals(secondCopyAnnotationLine1.getGenericFieldValue("Description"), "ANNOTATION != \"NA\" || ANNOTATION <= 0.01");
        Assert.assertEquals(firstCopyAnnotationLine2, secondCopyAnnotationLine2);
        Assert.assertEquals(secondCopyAnnotationLine2.getGenericFieldValue("Description"), "ANNOTATION with quote \" that is unmatched but escaped");

        final VCFInfoHeaderLine secondCopyEscapingQuoteInfoLine = secondCopyHeader.getInfoHeaderLine("EscapingQuote");
        Assert.assertNotNull(secondCopyEscapingQuoteInfoLine);
        Assert.assertEquals(secondCopyEscapingQuoteInfoLine.getDescription(), "This description has an escaped \" quote in it");

        final VCFInfoHeaderLine secondCopyEscapingBackslashInfoLine = secondCopyHeader.getInfoHeaderLine("EscapingBackslash");
        Assert.assertNotNull(secondCopyEscapingBackslashInfoLine);
        Assert.assertEquals(secondCopyEscapingBackslashInfoLine.getDescription(), "This description has an escaped \\ backslash in it");

        final VCFInfoHeaderLine secondCopyEscapingNonQuoteOrBackslashInfoLine = secondCopyHeader.getInfoHeaderLine("EscapingNonQuoteOrBackslash");
        Assert.assertNotNull(secondCopyEscapingNonQuoteOrBackslashInfoLine);
        Assert.assertEquals(secondCopyEscapingNonQuoteOrBackslashInfoLine.getDescription(), "This other value has a \\n newline in it");

        firstCopyReader.close();
        secondCopyReader.close();

    }

    /////////////////////////////////////////////////////////////////////
    // Private helper methods
    /////////////////////////////////////////////////////////////////////

    // Serialize/encode the header to a file, read metaData back in
    private static Set<VCFHeaderLine> getRoundTripEncoded(final VCFHeader header) throws IOException {
        final File myTempFile = File.createTempFile("VCFHeader", "vcf");
        try (final VariantContextWriter vcfWriter = new VariantContextWriterBuilder()
                .setOutputFile(myTempFile)
                .setOutputFileType(VariantContextWriterBuilder.OutputType.VCF)
                .setOptions(VariantContextWriterBuilder.NO_OPTIONS)
                .setVersionUpgradePolicy(VCFVersionUpgradePolicy.DO_NOT_UPGRADE)
                .build()
        ) {
            vcfWriter.writeHeader(header);
        }
        final VCFCodec codec = new VCFCodec();
        final VCFHeader vcfHeader = (VCFHeader) codec.readActualHeader(new LineIteratorImpl(
                new SynchronousLineReader(new FileReader(myTempFile.getAbsolutePath()))));
        return vcfHeader.getMetaDataInSortedOrder();
    }

    @Test
    public void testVcf42Roundtrip() throws Exception {
        // this test ensures that source/version fields are round-tripped properly

        // read an existing VCF
        final File expectedFile = new File("src/test/resources/htsjdk/variant/Vcf4.2WithSourceVersionInfoFields.vcf");
        final Tuple<VCFHeader, List<VariantContext>> expectedVCF = readEntireVCFIntoMemory(expectedFile.toPath());

        // write the file out into a new copy
        final File actualFile = File.createTempFile("testVcf4.2roundtrip.", FileExtensions.VCF);
        actualFile.deleteOnExit();

        try (final VariantContextWriter copyWriter = new VariantContextWriterBuilder()
                 .setOutputFile(actualFile)
                 .setReferenceDictionary(createArtificialSequenceDictionary())
                 .setOptions(EnumSet.of(Options.ALLOW_MISSING_FIELDS_IN_HEADER, Options.INDEX_ON_THE_FLY))
                 .build()
        ) {
            copyWriter.writeHeader(expectedVCF.a);
            for (final VariantContext variantContext : expectedVCF.b) {
                copyWriter.add(variantContext);
            }
        }

        final Tuple<VCFHeader, List<VariantContext>> actualVCF = readEntireVCFIntoMemory(actualFile.toPath());

        Assert.assertEquals(actualVCF.b.size(), expectedVCF.b.size());
        for (int i = 0; i < actualVCF.b.size(); i++) {
            VariantBaseTest.assertVariantContextsAreEqual(
                actualVCF.b.get(i).fullyDecode(actualVCF.a, false),
                expectedVCF.b.get(i).fullyDecode(expectedVCF.a, false)
            );
        }
    }

    private static final int VCF4headerStringCount = 16; // 17 -1 for the #CHROM... line


    private static VCFHeader getHiSeqVCFHeader() {
        final File vcf = new File("src/test/resources/htsjdk/variant/HiSeq.10000.vcf");
        final VCFFileReader reader = new VCFFileReader(vcf, false);
        final VCFHeader header = reader.getFileHeader();
        reader.close();
        return header;
    }

    private static <T extends VCFHeaderLine> Collection<VCFHeaderLine> asCollectionOfVCFHeaderLine(final Collection<T> headers) {
        // create a collection of VCFHeaderLine so that contains tests work correctly
        return headers.stream().map(h -> (VCFHeaderLine) h).collect(Collectors.toList());
    }

    @DataProvider(name="duplicateHeaderLineCases")
    private Object[][] getDuplicateHeaderLineCases() {
        return new Object[][] {

                // these tests use VCFAltHeaderLine to test structured/ID lines, but the behavior should be the same
                // for any header ID line

                // duplicate IDs, duplicate description; line is dropped due to duplicate ID
                { new VCFAltHeaderLine("X", "description1"),
                        new VCFAltHeaderLine("X", "description1"), false },
                // duplicate IDs, different descriptions;  line is dropped due to duplicate ID
                { new VCFAltHeaderLine("X", "description1"),
                        new VCFAltHeaderLine("X", "description2"), false },
                // different IDs, different descriptions;  line is retained
                { new VCFAltHeaderLine("X", "description1"),
                        new VCFAltHeaderLine("Y", "description2"), true },
                // different IDs, duplicate descriptions;  line is retained
                { new VCFAltHeaderLine("X", "description"),
                        new VCFAltHeaderLine("Y", "description"), true },

                // .......unstructured header lines........

                // duplicate key, duplicate value, line is dropped
                { new VCFHeaderLine("CommandLine", "command"), new VCFHeaderLine("CommandLine", "command"), false },
                // duplicate key, different value, line is retained
                { new VCFHeaderLine("CommandLine", "command1"), new VCFHeaderLine("CommandLine", "command2"), true },

                ///////////////////////////////////////////////////////////////////////////////////////////
                // since the VCFHeaderLine constructor is public, it can be used erroneously to model header
                // lines that have structured syntax, but which will not obey structured header line rules,
                // since those are enabled via VCFSimpleHeaderLine, and VCFHeaderLine is intended to be used
                // for non-structured lines. so include some tests that simulate this

                // duplicate key, duplicate value (...duplicate ID), line is dropped
                { new VCFHeaderLine("KEY", "<ID=ID1>"), new VCFHeaderLine("KEY", "<ID=ID1>"), false },
                // duplicate key, different value (different ID), line is retained
                { new VCFHeaderLine("KEY", "<ID=ID1>"), new VCFHeaderLine("KEY", "<ID=ID2>"), true },

                //NOTE: this case illustrates how its possible to use the API to cause two structured lines
                // with duplicate IDs to be retained if they are not modeled as VCFStructuredHeaderLines
                // duplicate key, different value (but IDENTICAL ID), line is RETAINED
                { new VCFHeaderLine("KEY", "<ID=ID1>"), new VCFHeaderLine("KEY", "<ID=ID1,ATTRIBUTE=23>"), true },

                // different key, duplicate value, line is retained
                { new VCFHeaderLine("KEY1", "<ID=ID1>"), new VCFHeaderLine("KEY2", "<ID=ID1>"), true },
                // different key, different value, line is retained
                { new VCFHeaderLine("KEY1", "<ID=ID1>"), new VCFHeaderLine("KEY2", "<ID=ID2>"), true },
        };
    }

    @Test(dataProvider = "duplicateHeaderLineCases")
    private void testDuplicateHeaderLine(final VCFHeaderLine hl1, final VCFHeaderLine hl2, final boolean expectHL2Retained) {
        final Set<VCFHeaderLine> lineSet = VCFHeader.makeHeaderVersionLineSet(VCFHeaderVersion.VCF4_2);
        lineSet.add(hl1);
        lineSet.add(hl2);
        final VCFHeader vcfHeader = new VCFHeader(lineSet);

        Assert.assertEquals(vcfHeader.getMetaDataInInputOrder().size(), expectHL2Retained ? 3 : 2);
    }

    @Test
    public void testAddOtherHeaderLineUnique() {
        final String TEST_KEY = "testKey";
        final VCFHeader vcfHeader = new VCFHeader(VCFHeader.makeHeaderVersionLineSet(VCFHeader.DEFAULT_VCF_VERSION));
        final VCFHeaderLine otherLine1 = new VCFHeaderLine(TEST_KEY, "Test Value 1");
        vcfHeader.addMetaDataLine(otherLine1);
        final List<VCFHeaderLine> otherLines1 = vcfHeader.getOtherHeaderLines(TEST_KEY);
        Assert.assertEquals(otherLines1.size(), 1);
        Assert.assertTrue(otherLines1.contains(otherLine1));

        // now add a second line
        final VCFHeaderLine otherLine2 = new VCFHeaderLine(TEST_KEY, "Test Value 2");
        vcfHeader.addMetaDataLine(otherLine2);
        final List<VCFHeaderLine> otherLines2 = vcfHeader.getOtherHeaderLines(TEST_KEY);
        Assert.assertEquals(otherLines2.size(), 2);
        Assert.assertTrue(otherLines2.contains(otherLine1));
        Assert.assertTrue(otherLines2.contains(otherLine2));

        // now call addOtherHeaderLineUnique with a 3rd line, the first two should be removed
        final VCFHeaderLine otherLine3= new VCFHeaderLine(TEST_KEY, "Test Value 3");
        vcfHeader.addOtherHeaderLineUnique(otherLine3);
        final List<VCFHeaderLine> otherLines3 = vcfHeader.getOtherHeaderLines(TEST_KEY);
        Assert.assertEquals(otherLines3.size(), 1);
        Assert.assertFalse(otherLines3.contains(otherLine1));
        Assert.assertFalse(otherLines3.contains(otherLine2));
        Assert.assertTrue(otherLines3.contains(otherLine3));
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testAddOtherHeaderLineUniqueRejectsIDLines() {
        final VCFHeader vcfHeader = new VCFHeader(VCFHeader.makeHeaderVersionLineSet(VCFHeader.DEFAULT_VCF_VERSION));
        final VCFSimpleHeaderLine simpleHeaderLine = new VCFSimpleHeaderLine("testKey", "testID","test description");
        vcfHeader.addOtherHeaderLineUnique(simpleHeaderLine);
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testGetOtherHeaderLineUnique() {
        final String TEST_KEY = "testKey";
        final VCFHeader vcfHeader = new VCFHeader(VCFHeader.makeHeaderVersionLineSet(VCFHeader.DEFAULT_VCF_VERSION));

        // now add two lines with the same key
        final VCFHeaderLine otherLine1 = new VCFHeaderLine(TEST_KEY, "Test Value 1");
        vcfHeader.addMetaDataLine(otherLine1);
        final VCFHeaderLine otherLine2 = new VCFHeaderLine(TEST_KEY, "Test Value 2");
        vcfHeader.addMetaDataLine(otherLine2);

        final List<VCFHeaderLine> otherLines = vcfHeader.getOtherHeaderLines(TEST_KEY);
        Assert.assertEquals(otherLines.size(), 2);
        Assert.assertTrue(otherLines.contains(otherLine1));
        Assert.assertTrue(otherLines.contains(otherLine2));

        // now call getOtherHeaderLineUnique, should throw
        vcfHeader.getOtherHeaderLineUnique(TEST_KEY);
    }

}
