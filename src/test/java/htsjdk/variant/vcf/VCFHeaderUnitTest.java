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
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.TestUtil;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.readers.AsciiLineReader;
import htsjdk.tribble.readers.AsciiLineReaderIterator;
import htsjdk.tribble.readers.LineIteratorImpl;
import htsjdk.tribble.readers.SynchronousLineReader;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VCF42To43VersionTransitionPolicy;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder.NO_OPTIONS;

/**
 * Created by IntelliJ IDEA.
 * User: aaron
 * Date: Jun 30, 2010
 * Time: 3:32:08 PM
 * To change this template use File | Settings | File Templates.
 */
public class VCFHeaderUnitTest extends VariantBaseTest {

    private File tempDir;

    private VCFHeader createHeader(String headerStr) {
        VCFCodec codec = new VCFCodec();
        VCFHeader header = (VCFHeader) codec.readActualHeader(new LineIteratorImpl(new SynchronousLineReader(
                new StringReader(headerStr))));
        //the "#CHROM..." header line isn't returned by getMetaDataInInputOrder
        Assert.assertEquals(header.getMetaDataInInputOrder().size(), VCF4headerStringCount);
        return header;
    }

    private Set<VCFHeaderLine> getV42HeaderLinesWithNoFormatString() {
        // precondition - create a v42 header and make sure its v42
        VCFHeader header = createHeader(VCF42headerStrings);
        Set<VCFHeaderLine> metaDataSet = new LinkedHashSet<>(header.getMetaDataInInputOrder());
        VCFHeaderLine versionLine = VCFHeader.getVersionLineFromHeaderLineSet(metaDataSet);
        // precondition - make sure its v42 to start with
        Assert.assertEquals(
                VCFHeaderVersion.toHeaderVersion(versionLine.getValue()),
                VCFHeaderVersion.VCF4_2);

        // remove the 4.2 version line from the original set, verify, and return the set with no fileformat string
        metaDataSet.remove(versionLine);
        Assert.assertNull(VCFHeader.getVersionLineFromHeaderLineSet(metaDataSet));
        return metaDataSet;
    }

    @BeforeClass
    private void createTemporaryDirectory() {
        tempDir = TestUtil.getTempDirectory("VCFHeader", "VCFHeaderTest");
    }

    @AfterClass
    private void deleteTemporaryDirectory() {
        for (File f : tempDir.listFiles()) {
            f.delete();
        }
        tempDir.delete();
    }

    @Test
    public void testVCF4ToVCF4() throws IOException {
        VCFHeader header = createHeader(VCF42headerStrings);
        Set<VCFHeaderLine> roundTripped = getRoundTripEncoded(header);
        Assert.assertTrue(roundTripped.equals(header.getMetaDataInSortedOrder()));
    }

    @Test
    public void testVCF4ToVCF4_alternate() throws IOException {
        VCFHeader header = createHeader(VCF4headerStrings_with_negativeOne);
        Set<VCFHeaderLine> roundTripped = getRoundTripEncoded(header);
        Assert.assertTrue(roundTripped.equals(header.getMetaDataInSortedOrder()));
    }

    @Test
    public void testVCFHeaderSampleRenamingSingleSampleVCF() throws Exception {
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

    @DataProvider
    public Object[][] testVCFHeaderDictionaryMergingData() {
        return new Object[][]{
                {"diagnosis_targets_testfile.vcf"},  // numerically ordered contigs
                {"dbsnp_135.b37.1000.vcf"}          // lexicographically ordered contigs
        };
    }

    @Test(dataProvider = "testVCFHeaderDictionaryMergingData")
    public void testVCFHeaderDictionaryMerging(final String vcfFileName) {
        final VCFHeader headerOne = new VCFFileReader(new File(variantTestDataRoot + vcfFileName), false).getFileHeader();
        final VCFHeader headerTwo = new VCFHeader(headerOne); // deep copy
        final List<String> sampleList = new ArrayList<String>();
        sampleList.addAll(headerOne.getSampleNamesInOrder());

        // Check that the two dictionaries start out the same
        headerOne.getSequenceDictionary().assertSameDictionary(headerTwo.getSequenceDictionary());

        // Run the merge command
        final VCFHeader mergedHeader = new VCFHeader(
                VCFUtils.smartMergeHeaders(Arrays.asList(headerOne, headerTwo),false),
                sampleList
        );

        // Check that the mergedHeader's sequence dictionary matches the first two
        mergedHeader.getSequenceDictionary().assertSameDictionary(headerOne.getSequenceDictionary());
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testVCFHeaderSampleRenamingMultiSampleVCF() throws Exception {
        final VCFCodec codec = new VCFCodec();
        codec.setRemappedSampleName("FOOSAMPLE");
        final AsciiLineReaderIterator vcfIterator = new AsciiLineReaderIterator(AsciiLineReader.from(new FileInputStream(variantTestDataRoot + "ex2.vcf")));
        final VCFHeader header = (VCFHeader) codec.readHeader(vcfIterator).getHeaderValue();
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testVCFHeaderSampleRenamingSitesOnlyVCF() throws Exception {
        final VCFCodec codec = new VCFCodec();
        codec.setRemappedSampleName("FOOSAMPLE");
        final AsciiLineReaderIterator vcfIterator = new AsciiLineReaderIterator(AsciiLineReader.from(new FileInputStream(variantTestDataRoot + "dbsnp_135.b37.1000.vcf")));
        final VCFHeader header = (VCFHeader) codec.readHeader(vcfIterator).getHeaderValue();
    }

    private VCFHeader getHiSeqVCFHeader() {
        final File vcf = new File("src/test/resources/htsjdk/variant/HiSeq.10000.vcf");
        final VCFFileReader reader = new VCFFileReader(vcf, false);
        final VCFHeader header = reader.getFileHeader();
        reader.close();
        return header;
    }

    @Test
    public void testVCFHeaderAddInfoLine() {
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

    private static <T extends VCFHeaderLine> Collection<VCFHeaderLine> asCollectionOfVCFHeaderLine(Collection<T> headers) {
        // create a collection of VCFHeaderLine so that contains tests work correctly
        return headers.stream().map(h -> (VCFHeaderLine) h).collect(Collectors.toList());
    }

    @Test
    public void testVCFHeaderAddFormatLine() {
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
    public void testVCFHeaderAddFilterLine() {
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
    public void testVCFHeaderAddContigLine() {
        final VCFHeader header = getHiSeqVCFHeader();
        //TODO: Note: This test was previously adding a "contig" header line with key "chr1", which
        //would roundtrip (through a file) as a VCFHeaderLine, not a VCFContigHeaderLine
        final VCFContigHeaderLine contigLine = new VCFContigHeaderLine("<ID=chr1,length=1234567890,assembly=FAKE,md5=f126cdf8a6e0c7f379d618ff66beb2da,species=\"Homo sapiens\">", VCFHeaderVersion.VCF4_0, 0);
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


    //TODO: This is a new test, but it passes in both the old and new implementations ? Should this be allowed ?
    // It seems wrong, VCFHeader allows two contig lines with the same contig index to reside in the header
    @Test
    public void testVCFHeaderAddContigLineWithSameIndex() {
        final VCFHeader header = new VCFHeader();
        final VCFContigHeaderLine contigLine1 = new VCFContigHeaderLine("<ID=chr1,length=10>", VCFHeaderVersion.VCF4_2, 0);
        final VCFContigHeaderLine contigLine2 = new VCFContigHeaderLine("<ID=chr2,length=10>", VCFHeaderVersion.VCF4_2, 0);

        header.addMetaDataLine(contigLine1);
        header.addMetaDataLine(contigLine2);

        Assert.assertTrue(header.getContigLines().contains(contigLine1));
        Assert.assertTrue(header.getContigLines().contains(contigLine2));
    }

    @Test
    public void testVCFHeaderContigLineMissingLength() {
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
    public void testVCFHeaderHonorContigLineOrder() throws IOException {
        try (final VCFFileReader vcfReader = new VCFFileReader(new File(variantTestDataRoot + "dbsnp_135.b37.1000.vcf"), false)) {
            // start with a header with a bunch of contig lines
            final VCFHeader header = vcfReader.getFileHeader();
            final List<VCFContigHeaderLine> originalHeaderList = header.getContigLines();
            Assert.assertTrue(originalHeaderList.size() > 0);

            // copy the contig lines to a new list, sticking an extra contig line in the middle
            final List<VCFContigHeaderLine> orderedList = new ArrayList<>();
            final int splitInTheMiddle = originalHeaderList.size() / 2;
            orderedList.addAll(originalHeaderList.subList(0, splitInTheMiddle));
            final VCFContigHeaderLine outrageousContigLine = new VCFContigHeaderLine(
                    "<ID=outrageousID,length=1234567890,assembly=FAKE,md5=f126cdf8a6e0c7f379d618ff66beb2da,species=\"Homo sapiens\">",
                    VCFHeaderVersion.VCF4_2,
                    0);
            orderedList.add(outrageousContigLine);
            // make sure the extra contig line is outrageous enough to not collide with a real contig ID
            Assert.assertTrue(orderedList.contains(outrageousContigLine));
            orderedList.addAll(originalHeaderList.subList(splitInTheMiddle, originalHeaderList.size()));
            Assert.assertEquals(originalHeaderList.size() + 1, orderedList.size());

            // crete a new header from the ordered list, and test that getContigLines honors the input order
            final VCFHeader orderedHeader = new VCFHeader();
            orderedList.forEach(hl -> orderedHeader.addMetaDataLine(hl));
            Assert.assertEquals(orderedList, orderedHeader.getContigLines());
        }
    }

    @Test
    public void testVCFSimpleHeaderLineGenericFieldGetter() {
        VCFHeader header = createHeader(VCF42headerStrings);
        List<VCFFilterHeaderLine> filters = header.getFilterLines();
        VCFFilterHeaderLine filterHeaderLine = filters.get(0);
        Assert.assertEquals(filterHeaderLine.getGenericFieldValue("ID"),"NoQCALL");
        Assert.assertEquals(filterHeaderLine.getGenericFieldValue("Description"),"Variant called by Dindel but not confirmed by QCALL");
    }

    @Test
    public void testVCFHeaderAddOtherLine() {
        final VCFHeader header = getHiSeqVCFHeader();
        final VCFHeaderLine otherLine = new VCFHeaderLine("TestOtherLine", "val");
        header.addMetaDataLine(otherLine);

        Assert.assertTrue(header.getOtherHeaderLines().contains(otherLine), "TestOtherLine not found in other header lines");
        Assert.assertTrue(header.getMetaDataInInputOrder().contains(otherLine), "TestOtherLine not found in set of all header lines");
        Assert.assertNotNull(header.getOtherHeaderLine("TestOtherLine"), "Lookup for TestOtherLine by key failed");

        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getInfoHeaderLines()).contains(otherLine), "TestOtherLine present in info header lines");
        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getFormatHeaderLines()).contains(otherLine), "TestOtherLine present in format header lines");
        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getContigLines()).contains(otherLine), "TestOtherLine present in contig header lines");
        Assert.assertFalse(asCollectionOfVCFHeaderLine(header.getFilterLines()).contains(otherLine), "TestOtherLine present in filter header lines");
    }

    @Test
    public void testVCFHeaderAddMetaDataLineDoesNotDuplicateContigs() {
        File input = new File("src/test/resources/htsjdk/variant/ex2.vcf");

        VCFFileReader reader = new VCFFileReader(input, false);
        VCFHeader header = reader.getFileHeader();

        final int numContigLinesBefore = header.getContigLines().size();

        VCFInfoHeaderLine newInfoField = new VCFInfoHeaderLine("test", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "test info field");
        header.addMetaDataLine(newInfoField);

        // getting the sequence dictionary was failing due to duplicating contigs in issue #214,
        // we expect this to not throw an exception
        header.getSequenceDictionary();

        final int numContigLinesAfter = header.getContigLines().size();
        // assert that we have the same number of contig lines before and after
        Assert.assertEquals(numContigLinesBefore, numContigLinesAfter);
    }

    @Test
    public void testVCFHeaderAddDuplicateContigLine() {
        File input = new File("src/test/resources/htsjdk/variant/ex2.vcf");

        VCFFileReader reader = new VCFFileReader(input, false);
        VCFHeader header = reader.getFileHeader();


        final int numContigLinesBefore = header.getContigLines().size();
        // try to read the first contig line
        header.addMetaDataLine(header.getContigLines().get(0));
        final int numContigLinesAfter = header.getContigLines().size();

        // assert that we have the same number of contig lines before and after
        Assert.assertEquals(numContigLinesBefore, numContigLinesAfter);
    }

    @Test
    public void testVCFHeaderAddDuplicateKeyValueHeaderLine() {
        File input = new File("src/test/resources/htsjdk/variant/ex2.vcf");

        VCFFileReader reader = new VCFFileReader(input, false);
        VCFHeader header = reader.getFileHeader();

        VCFHeaderLine newHeaderLine = new VCFHeaderLine("key", "value");
        // add this new header line
        header.addMetaDataLine(newHeaderLine);

        final int numHeaderLinesBefore = header.getOtherHeaderLines().size();
        // add the same header line again
        header.addMetaDataLine(newHeaderLine);
        final int numHeaderLinesAfter = header.getOtherHeaderLines().size();

        // TODO: Note: This change assumes we don't allow duplicate unstructured
        // lines with the same key unless they have different content
        // assert that we have the one more other header line after
        Assert.assertEquals(numHeaderLinesBefore, numHeaderLinesAfter);
    }

    @DataProvider(name="validHeaderVersionTransitions")
    public Object[][] validHeaderVersionTransitions() {
        // v4.3 can never transition, all other version transitions are allowed
        return new Object[][] {
                {VCFHeaderVersion.VCF4_0, VCFHeaderVersion.VCF4_0},
                {VCFHeaderVersion.VCF4_0, VCFHeaderVersion.VCF4_1},
                {VCFHeaderVersion.VCF4_0, VCFHeaderVersion.VCF4_2},
                {VCFHeaderVersion.VCF4_1, VCFHeaderVersion.VCF4_1},
                {VCFHeaderVersion.VCF4_1, VCFHeaderVersion.VCF4_2},
                {VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF4_2},
                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_3}
        };
    }

    @DataProvider(name="invalidHeaderVersionTransitions")
    public Object[][] invalidHeaderVersionTransitions() {
        // v4.3 can never be transitioned down to pre v4.3
        // Pre v4.3 might be able to be transitioned to 4.3, and this is tested in VCFCodec43FeaturesTest
        return new Object[][] {
                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_0},
                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_1},
                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_2},
        };
    }

    @Test(dataProvider="validHeaderVersionTransitions")
    public void testValidHeaderVersionTransition(final VCFHeaderVersion fromVersion, final VCFHeaderVersion toVersion) {
        doHeaderTransition(fromVersion, toVersion);
    }

    @Test(dataProvider="invalidHeaderVersionTransitions", expectedExceptions = TribbleException.class)
    public void testInvalidHeaderVersionTransition(final VCFHeaderVersion fromVersion, final VCFHeaderVersion toVersion) {
        doHeaderTransition(fromVersion, toVersion);
    }

    private void doHeaderTransition(final VCFHeaderVersion fromVersion, final VCFHeaderVersion toVersion) {
        final VCFHeader vcfHeader =
                fromVersion == null ?
                        new VCFHeader() :
                        new VCFHeader(fromVersion, Collections.EMPTY_SET, Collections.EMPTY_SET);
        vcfHeader.setVCFHeaderVersion(toVersion);
    }

    @Test
    public void testVCFHeaderSerialization() throws Exception {
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

    @Test(dataProvider = "vcfVersions")
    public void testCreateHeaderWithNoFileFormatLine(final VCFHeaderVersion vcfVersion) {
        Set<VCFHeaderLine> metaDataSet = getV42HeaderLinesWithNoFormatString(); // this (4.2) header is compatible with all 4.x versions

        // create a new versioned header from this set (containing no fileformat line)
        // which should always default to 4.2
        VCFHeader vcfHeader = new VCFHeader(vcfVersion, metaDataSet, Collections.EMPTY_SET);
        Assert.assertEquals(vcfHeader.getVCFHeaderVersion(), vcfVersion);
    }

    @Test(dataProvider = "vcfVersions")
    public void testCreateHeaderWithMatchingFileFormatLine(final VCFHeaderVersion vcfVersion) {
        Set<VCFHeaderLine> metaDataSet = getV42HeaderLinesWithNoFormatString(); // 4.2 header is compatible with all 4.x versions

        // add in the corresponding fileformat line; create a new versioned header
        // since the version requested in the constructor and the format lines are in sync, there is
        // no conflict, and the resulting header's version should always match the requested version
        metaDataSet.add(new VCFHeaderLine(vcfVersion.getFormatString(), vcfVersion.getVersionString()));
        VCFHeader vcfHeader = new VCFHeader(vcfVersion, metaDataSet, Collections.EMPTY_SET);
        Assert.assertEquals(vcfHeader.getVCFHeaderVersion(), vcfVersion);
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testCreateHeaderWithMultipleFileFormatLines() {
        Set<VCFHeaderLine> metaDataSet = getV42HeaderLinesWithNoFormatString(); // this (4.2) header is compatible with all 4.x versions
        int beforeSize = metaDataSet.size();

        metaDataSet.add(new VCFHeaderLine(VCFHeaderVersion.VCF4_2.getFormatString(), VCFHeaderVersion.VCF4_2.getVersionString()));
        metaDataSet.add(new VCFHeaderLine(VCFHeaderVersion.VCF4_1.getFormatString(), VCFHeaderVersion.VCF4_1.getVersionString()));
        Assert.assertEquals(metaDataSet.size(), beforeSize + 2);

        // create a new versioned header from this set (containing no fileformat line)
        // which should always default to 4.2
        new VCFHeader(metaDataSet, Collections.EMPTY_SET);
    }

    @Test(dataProvider = "vcfVersions")
    public void testSetHeaderVersionWithFileFormatLine(final VCFHeaderVersion vcfVersion) {
        Set<VCFHeaderLine> metaDataSet = getV42HeaderLinesWithNoFormatString(); // 4.2 header is compatible with all 4.x versions

        // don't request a version; let the header derive it from the embedded format line;
        // the resulting header version should match the format line we embedded
        metaDataSet.add(new VCFHeaderLine(vcfVersion.getFormatString(), vcfVersion.getVersionString()));
        VCFHeader vcfHeader = new VCFHeader(metaDataSet, Collections.EMPTY_SET); //defaults to v4.2
        Assert.assertEquals(vcfHeader.getVCFHeaderVersion(), vcfVersion);
        vcfHeader.setHeaderVersion(vcfVersion);
    }

    @Test(dataProvider = "vcfVersions")
    public void testSetHeaderVersionWithNoFileFormatLine(final VCFHeaderVersion vcfVersion) {
        Set<VCFHeaderLine> metaDataSet = getV42HeaderLinesWithNoFormatString(); // 4.2 header is compatible with all 4.x versions

        // create a new header from this set (containing no fileformat line), no requested version in constructor
        VCFHeader vcfHeader = new VCFHeader(metaDataSet, Collections.EMPTY_SET); //defaults to v4.2
        vcfHeader.setHeaderVersion(vcfVersion);
        Assert.assertEquals(vcfHeader.getVCFHeaderVersion(), VCFHeader.DEFAULT_VCF_VERSION);
    }

    @DataProvider(name = "conflictingHeaderVersionPairs")
    public Object[][] vcfConflictingVersionLines() {
        return new Object[][]{
                {VCFHeaderVersion.VCF3_2, VCFHeaderVersion.VCF4_3},
                {VCFHeaderVersion.VCF3_3, VCFHeaderVersion.VCF4_3},
                {VCFHeaderVersion.VCF4_0, VCFHeaderVersion.VCF4_3},
                {VCFHeaderVersion.VCF4_1, VCFHeaderVersion.VCF4_3},
                {VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF4_3},

                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF3_2},
                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF3_3},
                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_0},
                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_1},
                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_2},
        };
    }

    @Test(dataProvider = "conflictingHeaderVersionPairs", expectedExceptions = IllegalArgumentException.class)
    public void testCreateHeaderWithConflictingFileFormatLine(
            final VCFHeaderVersion vcfVersion,
            final VCFHeaderVersion conflictingVersion) {
        Set<VCFHeaderLine> metaDataSet = getV42HeaderLinesWithNoFormatString(); // 4.2 header is compatible with all 4.x versions

        //add in the fileformat line; create a new header requesting conflicting version
        metaDataSet.add(new VCFHeaderLine(vcfVersion.getFormatString(), vcfVersion.getVersionString()));
        new VCFHeader(conflictingVersion, metaDataSet, Collections.EMPTY_SET);
    }

    @Test(dataProvider = "conflictingHeaderVersionPairs", expectedExceptions = TribbleException.class)
    public void testSetHeaderWithConflictingVersion(final VCFHeaderVersion vcfVersion, final VCFHeaderVersion conflictingVersion) {
        Set<VCFHeaderLine> metaDataSet = getV42HeaderLinesWithNoFormatString(); // 4.2 header is compatible with all 4.x versions

        //add in a fileformat line; create a new header; setHeader with a conflicting version
        metaDataSet.add(new VCFHeaderLine(vcfVersion.getFormatString(), vcfVersion.getVersionString()));
        VCFHeader vcfHeader = new VCFHeader(metaDataSet, Collections.EMPTY_SET);
        vcfHeader.setHeaderVersion(conflictingVersion);
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testAddSecondFileFormatLine() {
        Set<VCFHeaderLine> metaDataSet = getV42HeaderLinesWithNoFormatString(); // 4.2 header is compatible with all 4.x versions

        //add in a fileformat line that matches the default version; create a new header
        metaDataSet.add(new VCFHeaderLine(VCFHeader.DEFAULT_VCF_VERSION.getFormatString(), VCFHeader.DEFAULT_VCF_VERSION.getVersionString()));
        VCFHeader vcfHeader = new VCFHeader(metaDataSet, Collections.EMPTY_SET);
        Assert.assertEquals(vcfHeader.getVCFHeaderVersion(), VCFHeader.DEFAULT_VCF_VERSION);

        // try to add another identical fileformat header line
        vcfHeader.addMetaDataLine(new VCFHeaderLine(VCFHeaderVersion.VCF4_2.getFormatString(), VCFHeader.DEFAULT_VCF_VERSION.getVersionString()));
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testAddConflictingFileFormatLine() {
        Set<VCFHeaderLine> metaDataSet = getV42HeaderLinesWithNoFormatString(); // 4.2 header is compatible with all 4.x versions

        //add in a fileformat line that matches the default version; create a new header
        metaDataSet.add(new VCFHeaderLine(VCFHeader.DEFAULT_VCF_VERSION.getFormatString(), VCFHeader.DEFAULT_VCF_VERSION.getVersionString()));
        VCFHeader vcfHeader = new VCFHeader(metaDataSet, Collections.EMPTY_SET);
        Assert.assertEquals(vcfHeader.getVCFHeaderVersion(), VCFHeader.DEFAULT_VCF_VERSION);

        // now add a conflicting fileformat header line
        vcfHeader.addMetaDataLine(new VCFHeaderLine(VCFHeaderVersion.VCF4_1.getFormatString(), VCFHeaderVersion.VCF4_1.getVersionString()));
    }

    @Test
    public void testSilentlyRejectDuplicateContigLines() {
        // Note: This is testing a case that failed with the previous implementation, when both of these
        // lines were added to the master list, but only one was added to the contig line list. The two
        // lines have identical key/ID values, but because they have different attributes, they have
        // different hashCodes, and so can both reside in a Set.
        VCFContigHeaderLine contigOneNoAssembly = new VCFContigHeaderLine(
                new LinkedHashMap<String, String>() {{
                    put("ID", "1");
                    put("length", "123");
                }},
                0);
        VCFContigHeaderLine contigOneWithAssembly = new VCFContigHeaderLine(
                new LinkedHashMap<String, String>() {{
                    put("ID", "1");
                    put("length", "123");
                    put("assembly", "b37");
                }},
                0);
        Assert.assertNotEquals(contigOneNoAssembly.hashCode(), contigOneWithAssembly.hashCode());

        Set<VCFHeaderLine> headerLineSet = new LinkedHashSet<>();
        headerLineSet.add(contigOneNoAssembly);
        headerLineSet.add(contigOneWithAssembly); // silently dropped since it has the same id
        Assert.assertEquals(headerLineSet.size(), 2);

        VCFHeader vcfHeader = new VCFHeader(headerLineSet);
        Set<VCFHeaderLine> allMetaDataInput = vcfHeader.getMetaDataInInputOrder();
        Assert.assertEquals(allMetaDataInput.size(), 1);

        Set<VCFHeaderLine> allMetaDataSorted = vcfHeader.getMetaDataInSortedOrder();
        Assert.assertEquals(allMetaDataSorted.size(), 1);

        List<VCFContigHeaderLine> allContigLines = vcfHeader.getContigLines();
        Assert.assertEquals(allContigLines.size(), 1);      // one contig
        Assert.assertNull(allContigLines.get(0).getGenericFieldValue("assembly"));
    }

    @Test(dataProvider = "conflictingHeaderVersionPairs", expectedExceptions = TribbleException.class)
    public void test_MergeHeadersAcrossVersions(
            final VCFHeaderVersion vcfVersion,
            final VCFHeaderVersion conflictingVersion)
    {
        Set<VCFHeaderLine> metaDataSet = getV42HeaderLinesWithNoFormatString();
        metaDataSet.add(new VCFHeaderLine(vcfVersion.getFormatString(), vcfVersion.getVersionString()));
        VCFHeader header = new VCFHeader(metaDataSet);
        Assert.assertEquals(header.getVCFHeaderVersion(), vcfVersion);

        Set<VCFHeaderLine> conflictSet = getV42HeaderLinesWithNoFormatString();
        conflictSet.add(new VCFHeaderLine(conflictingVersion.getFormatString(), conflictingVersion.getVersionString()));
        VCFHeader conflictingHeader = new VCFHeader(conflictSet);
        Assert.assertEquals(conflictingHeader.getVCFHeaderVersion(), conflictingVersion);

        List<VCFHeader> headerList = new ArrayList<VCFHeader>(2);
        headerList.add(header);
        headerList.add(conflictingHeader);

        // smartMergeHeaders strips out fileformat lines and returns the remaining merged header lines
        Set<VCFHeaderLine> mergedSet = VCFUtils.smartMergeHeaders(headerList, false);

        // create a header from the merged set, which should defautl to the default version
        VCFHeader mergedHeader = new VCFHeader(mergedSet);
        Assert.assertEquals(mergedHeader.getVCFHeaderVersion(), VCFHeader.DEFAULT_VCF_VERSION);

        // all the header lines in the merged set are also in the resulting header
        Assert.assertEquals(mergedHeader.getMetaDataInInputOrder(), mergedSet);

        // since we merged two headers that are identical except for the fileformat line, assert that all
        // the original header lines are in the resulting header
        metaDataSet.add(new VCFHeaderLine(VCFHeader.DEFAULT_VCF_VERSION.getFormatString(), VCFHeader.DEFAULT_VCF_VERSION.getVersionString()));
        Assert.assertEquals(mergedHeader.getMetaDataInInputOrder(), mergedSet);
    }

    private LinkedHashSet<VCFHeaderLine> getHeaderLineListWithoutLine(
            final LinkedHashSet<VCFHeaderLine> inputSet,
            final int n) {
        List<VCFHeaderLine> headerLineList = new ArrayList<>(inputSet);
        headerLineList.remove(n);
        return new LinkedHashSet<>(headerLineList);
    }

    @DataProvider(name = "mergeHeaderData")
    public Iterator<Object[]> mergeHeaderData()
    {
        List<VCFHeaderLine> headerLineList = new ArrayList<>(new VCFHeaderUnitTestData().getFullMetaDataLinesAsSet());
        Collection<Object[]> mergeTestCase = new ArrayList<>();
        for (int i = 0; i < headerLineList.size(); i++) {
            mergeTestCase.add(
                    new Object[] {
                            new VCFHeader(new VCFHeaderUnitTestData().getFullMetaDataLinesAsSet()),
                            new VCFHeader(getHeaderLineListWithoutLine(new VCFHeaderUnitTestData().getFullMetaDataLinesAsSet(), i))
            });
        }

        return mergeTestCase.iterator();
    }

    @Test(dataProvider = "mergeHeaderData")
    public void testMergeHeaders(
            final VCFHeader fullHeader,
            final VCFHeader subsetHeader)
    {
        List<VCFHeader> headerList = new ArrayList<VCFHeader>() {{
            add(fullHeader);
            add(subsetHeader);
        }};
        LinkedHashSet<VCFHeaderLine> mergedList = new LinkedHashSet(VCFHeader.getMergedHeaderLines(headerList, false));

        // We want to compare the set returned from the merger with the original set, but merging removes
        // fileformat lines, so we need to remove the same fileformat line from the original set for comparison purposes
        LinkedHashSet<VCFHeaderLine> fullHeaderListWithoutFileFormatLine = new LinkedHashSet(fullHeader.getMetaDataInInputOrder());
        if (false == fullHeaderListWithoutFileFormatLine.remove(fullHeader.getOtherHeaderLine("fileformat"))) {
            // one of the test cases has the fileformat line removed from the subsetted list; make sure this is it
            Assert.assertNull(fullHeader.getOtherHeaderLine("fileformat"));
        } else {
            Assert.assertNotNull(fullHeader.getOtherHeaderLine("fileformat"));
        }

        Assert.assertEquals(new TreeSet<>(fullHeaderListWithoutFileFormatLine), new TreeSet<>(mergedList));
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

        SAMSequenceDictionary samDict = new SAMSequenceDictionary();

        final SAMSequenceRecord seqRec1 = new SAMSequenceRecord("1", 1);
        seqRec1.setAssembly(assemblyString);
        seqRec1.setMd5(md5String);
        seqRec1.setAttribute(SAMSequenceRecord.URI_TAG, urlString);
        seqRec1.setSpecies(speciesString);
        final SAMSequenceRecord seqRec2 = new SAMSequenceRecord("2", 1);
        samDict.addSequence(seqRec1);
        samDict.addSequence(seqRec2);

        VCFHeader vcfHeader = new VCFHeader();
        vcfHeader.setSequenceDictionary(samDict);
        SAMSequenceDictionary roundTrippedDict = vcfHeader.getSequenceDictionary();

        final SAMSequenceRecord rtRec1 = roundTrippedDict.getSequence("1");
        Assert.assertEquals(assemblyString, rtRec1.getAssembly());
        Assert.assertEquals(md5String, rtRec1.getMd5());
        Assert.assertEquals(urlString, rtRec1.getAttribute(SAMSequenceRecord.URI_TAG));
        Assert.assertEquals(speciesString, rtRec1.getSpecies());

        Assert.assertEquals(seqRec1, roundTrippedDict.getSequence("1")); // somewhat redundant check on full record
        Assert.assertEquals(seqRec2, roundTrippedDict.getSequence("2"));
    }

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
        final CloseableIterator<VariantContext> firstCopyVariantIterator = originalFileReader.iterator();
        while (firstCopyVariantIterator.hasNext()) {
            VariantContext variantContext = firstCopyVariantIterator.next();
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
        final CloseableIterator<VariantContext> secondCopyVariantIterator = firstCopyReader.iterator();
        while (secondCopyVariantIterator.hasNext()) {
            VariantContext variantContext = secondCopyVariantIterator.next();
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

    @Test
    public void testVcf42Roundtrip() throws Exception {
        // this test ensures that source/version fields are round-tripped properly

        // read an existing VCF
        File expectedFile = new File("src/test/resources/htsjdk/variant/Vcf4.2WithSourceVersionInfoFields.vcf");

        // write the file out into a new copy
        final File actualFile = File.createTempFile("testVcf4.2roundtrip.", FileExtensions.VCF);
        actualFile.deleteOnExit();

        try (final VCFFileReader originalFileReader = new VCFFileReader(expectedFile, false);
             final VariantContextWriter copyWriter = new VariantContextWriterBuilder()
                     .setOutputFile(actualFile)
                     .setReferenceDictionary(createArtificialSequenceDictionary())
                     .setOptions(EnumSet.of(Options.ALLOW_MISSING_FIELDS_IN_HEADER, Options.INDEX_ON_THE_FLY))
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
        Assert.assertEquals(actualContents.substring(actualContents.indexOf('\n')), expectedContents.substring(actualContents.indexOf('\n')));
    }


    /**
     * a little utility function for all tests to md5sum a file
     * Shameless taken from:
     * <p/>
     * http://www.javalobby.org/java/forums/t84420.html
     *
     * @param file the file
     * @return a string
     */
    private static String md5SumFile(File file) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to find MD5 digest");
        }
        InputStream is;
        try {
            is = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Unable to open file " + file);
        }
        byte[] buffer = new byte[8192];
        int read;
        try {
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] md5sum = digest.digest();
            BigInteger bigInt = new BigInteger(1, md5sum);
            return bigInt.toString(16);

        } catch (IOException e) {
            throw new RuntimeException("Unable to process file for MD5", e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                throw new RuntimeException("Unable to close input stream for MD5 calculation", e);
            }
        }
    }

    private void checkMD5ofHeaderFile(VCFHeader header, String md5sum) {
        File myTempFile = null;
        try {
            myTempFile = File.createTempFile("VCFHeader", "vcf");
            myTempFile.deleteOnExit();
            new PrintWriter(myTempFile);
        } catch (IOException e) {
            Assert.fail("Unable to make a temp file!");
        }
    }

    // Serialize/encode the header to a file, read metaData back in
    private Set<VCFHeaderLine> getRoundTripEncoded(VCFHeader header) throws IOException {
        File myTempFile = File.createTempFile("VCFHeader", "vcf");
        try (final VariantContextWriter vcfWriter =
                     new VariantContextWriterBuilder()
                             .setOutputFile(myTempFile)
                             .setOutputFileType(VariantContextWriterBuilder.OutputType.VCF)
                             .setOptions(NO_OPTIONS)
                             .setVCF42to43TransitionPolicy(VCF42To43VersionTransitionPolicy.DO_NOT_TRANSITION)
                             .build()) {
            vcfWriter.writeHeader(header);
        }
        VCFHeader vcfHeader = (VCFHeader) new VCFCodec().readActualHeader(new LineIteratorImpl(
                new SynchronousLineReader(new FileReader(myTempFile.getAbsolutePath()))));
        return vcfHeader.getMetaDataInSortedOrder();
    }

    public static final int VCF4headerStringCount = 16; // 17 -1 for the #CHROM... line

    public static final String VCF42headerStrings =
            "##fileformat=VCFv4.2\n" +
             "##filedate=2010-06-21\n" +
                    "##reference=NCBI36\n" +
                    "##INFO=<ID=GC, Number=0, Type=Flag, Description=\"Overlap with Gencode CCDS coding sequence\">\n" +
                    "##INFO=<ID=DP, Number=1, Type=Integer, Description=\"Total number of reads in haplotype window\">\n" +
                    "##INFO=<ID=AF, Number=A, Type=Float, Description=\"Dindel estimated population allele frequency\">\n" +
                    "##INFO=<ID=CA, Number=1, Type=String, Description=\"Pilot 1 callability mask\">\n" +
                    "##INFO=<ID=HP, Number=1, Type=Integer, Description=\"Reference homopolymer tract length\">\n" +
                    "##INFO=<ID=NS, Number=1, Type=Integer, Description=\"Number of samples with data\">\n" +
                    "##INFO=<ID=DB, Number=0, Type=Flag, Description=\"dbSNP membership build 129 - type match and indel sequence length match within 25 bp\">\n" +
                    "##INFO=<ID=NR, Number=1, Type=Integer, Description=\"Number of reads covering non-ref variant on reverse strand\">\n" +
                    "##INFO=<ID=NF, Number=1, Type=Integer, Description=\"Number of reads covering non-ref variant on forward strand\">\n" +
                    "##FILTER=<ID=NoQCALL, Description=\"Variant called by Dindel but not confirmed by QCALL\">\n" +
                    "##FORMAT=<ID=GT, Number=1, Type=String, Description=\"Genotype\">\n" +
                    "##FORMAT=<ID=HQ, Number=2, Type=Integer, Description=\"Haplotype quality\">\n" +
                    "##FORMAT=<ID=GQ, Number=1, Type=Integer, Description=\"Genotype quality\">\n" +
                    "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n";


    public static final String VCF4headerStrings_with_negativeOne =
            "##fileformat=VCFv4.2\n" +
                    "##filedate=2010-06-21\n" +
                    "##reference=NCBI36\n" +
                    "##INFO=<ID=GC, Number=0, Type=Flag, Description=\"Overlap with Gencode CCDS coding sequence\">\n" +
                    "##INFO=<ID=YY, Number=., Type=Integer, Description=\"Some weird value that has lots of parameters\">\n" +
                    "##INFO=<ID=AF, Number=A, Type=Float, Description=\"Dindel estimated population allele frequency\">\n" +
                    "##INFO=<ID=CA, Number=1, Type=String, Description=\"Pilot 1 callability mask\">\n" +
                    "##INFO=<ID=HP, Number=1, Type=Integer, Description=\"Reference homopolymer tract length\">\n" +
                    "##INFO=<ID=NS, Number=1, Type=Integer, Description=\"Number of samples with data\">\n" +
                    "##INFO=<ID=DB, Number=0, Type=Flag, Description=\"dbSNP membership build 129 - type match and indel sequence length match within 25 bp\">\n" +
                    "##INFO=<ID=NR, Number=1, Type=Integer, Description=\"Number of reads covering non-ref variant on reverse strand\">\n" +
                    "##INFO=<ID=NF, Number=1, Type=Integer, Description=\"Number of reads covering non-ref variant on forward strand\">\n" +
                    "##FILTER=<ID=NoQCALL, Description=\"Variant called by Dindel but not confirmed by QCALL\">\n" +
                    "##FORMAT=<ID=GT, Number=1, Type=String, Description=\"Genotype\">\n" +
                    "##FORMAT=<ID=HQ, Number=2, Type=Integer, Description=\"Haplotype quality\">\n" +
                    "##FORMAT=<ID=TT, Number=., Type=Integer, Description=\"Lots of TTs\">\n" +
                    "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n";

}
