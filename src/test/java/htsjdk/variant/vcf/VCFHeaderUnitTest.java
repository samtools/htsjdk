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
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.TestUtil;
import htsjdk.testutil.LogCapture;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.readers.LineIteratorImpl;
import htsjdk.tribble.readers.SynchronousLineReader;
import htsjdk.tribble.readers.Utf8LineReader;
import htsjdk.tribble.readers.Utf8LineReaderIterator;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Created by IntelliJ IDEA.
 * User: aaron
 * Date: Jun 30, 2010
 * Time: 3:32:08 PM
 * To change this template use File | Settings | File Templates.
 */
public class VCFHeaderUnitTest extends VariantBaseTest {

    private Path tempDir;

    private VCFHeader createHeader(String headerStr) {
        VCFCodec codec = new VCFCodec();
        VCFHeader header = (VCFHeader)
                codec.readActualHeader(new LineIteratorImpl(new SynchronousLineReader(new StringReader(headerStr))));
        Assert.assertEquals(header.getMetaDataInInputOrder().size(), VCF4headerStringCount);
        return header;
    }

    @BeforeClass
    private void createTemporaryDirectory() {
        tempDir = TestUtil.getTempDirectoryAsPath("VCFHeader", "VCFHeaderTest");
    }

    @AfterClass
    private void deleteTemporaryDirectory() throws IOException {
        try (final Stream<Path> entries = Files.list(tempDir)) {
            for (final Path entry : (Iterable<Path>) entries::iterator) {
                Files.delete(entry);
            }
        }
        Files.delete(tempDir);
    }

    @Test
    public void testVCF4ToVCF4() {
        VCFHeader header = createHeader(VCF4headerStrings);
        checkMD5ofHeaderFile(header, "91c33dadb92e01ea349bd4bcdd02d6be");
    }

    @Test
    public void testVCF4ToVCF4_alternate() {
        VCFHeader header = createHeader(VCF4headerStrings_with_negativeOne);
        checkMD5ofHeaderFile(header, "39318d9713897d55be5ee32a2119853f");
    }

    @Test
    public void testVCFHeaderSampleRenamingSingleSampleVCF() throws Exception {
        final VCFCodec codec = new VCFCodec();
        codec.setRemappedSampleName("FOOSAMPLE");
        final Utf8LineReaderIterator vcfIterator = new Utf8LineReaderIterator(
                Utf8LineReader.from(Files.newInputStream(Path.of(variantTestDataRoot + "HiSeq.10000.vcf"))));
        final VCFHeader header = (VCFHeader) codec.readHeader(vcfIterator).getHeaderValue();

        Assert.assertEquals(header.getNGenotypeSamples(), 1, "Wrong number of samples in remapped header");
        Assert.assertEquals(
                header.getGenotypeSamples().get(0), "FOOSAMPLE", "Sample name in remapped header has incorrect value");

        int recordCount = 0;
        while (vcfIterator.hasNext() && recordCount < 10) {
            recordCount++;
            final VariantContext vcfRecord = codec.decode(vcfIterator.next());

            Assert.assertEquals(
                    vcfRecord.getSampleNames().size(), 1, "Wrong number of samples in vcf record after remapping");
            Assert.assertEquals(
                    vcfRecord.getSampleNames().iterator().next(),
                    "FOOSAMPLE",
                    "Wrong sample in vcf record after remapping");
        }
    }

    @DataProvider
    public Object[][] testVCFHeaderDictionaryMergingData() {
        return new Object[][] {
            {"diagnosis_targets_testfile.vcf"}, // numerically ordered contigs
            {"dbsnp_135.b37.1000.vcf"} // lexicographically ordered contigs
        };
    }

    @Test(dataProvider = "testVCFHeaderDictionaryMergingData")
    public void testVCFHeaderDictionaryMerging(final String vcfFileName) {
        final VCFHeader headerOne =
                new VCFFileReader(Path.of(variantTestDataRoot + vcfFileName), false).getFileHeader();
        final VCFHeader headerTwo = new VCFHeader(headerOne); // deep copy
        final List<String> sampleList = new ArrayList<String>();
        sampleList.addAll(headerOne.getSampleNamesInOrder());

        // Check that the two dictionaries start out the same
        headerOne.getSequenceDictionary().assertSameDictionary(headerTwo.getSequenceDictionary());

        // Run the merge command
        final VCFHeader mergedHeader =
                new VCFHeader(VCFUtils.smartMergeHeaders(Arrays.asList(headerOne, headerTwo), false), sampleList);

        // Check that the mergedHeader's sequence dictionary matches the first two
        mergedHeader.getSequenceDictionary().assertSameDictionary(headerOne.getSequenceDictionary());
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testVCFHeaderSampleRenamingMultiSampleVCF() throws Exception {
        final VCFCodec codec = new VCFCodec();
        codec.setRemappedSampleName("FOOSAMPLE");
        final Utf8LineReaderIterator vcfIterator = new Utf8LineReaderIterator(
                Utf8LineReader.from(Files.newInputStream(Path.of(variantTestDataRoot + "ex2.vcf"))));
        final VCFHeader header = (VCFHeader) codec.readHeader(vcfIterator).getHeaderValue();
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testVCFHeaderSampleRenamingSitesOnlyVCF() throws Exception {
        final VCFCodec codec = new VCFCodec();
        codec.setRemappedSampleName("FOOSAMPLE");
        final Utf8LineReaderIterator vcfIterator = new Utf8LineReaderIterator(
                Utf8LineReader.from(Files.newInputStream(Path.of(variantTestDataRoot + "dbsnp_135.b37.1000.vcf"))));
        final VCFHeader header = (VCFHeader) codec.readHeader(vcfIterator).getHeaderValue();
    }

    private VCFHeader getHiSeqVCFHeader() {
        final Path vcf = Path.of("src/test/resources/htsjdk/variant/HiSeq.10000.vcf");
        final VCFFileReader reader = new VCFFileReader(vcf, false);
        final VCFHeader header = reader.getFileHeader();
        reader.close();
        return header;
    }

    @Test
    public void testVCFHeaderAddInfoLine() {
        final VCFHeader header = getHiSeqVCFHeader();
        final VCFInfoHeaderLine infoLine = new VCFInfoHeaderLine(
                "TestInfoLine", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "test info line");
        header.addMetaDataLine(infoLine);

        Assert.assertTrue(
                header.getInfoHeaderLines().contains(infoLine), "TestInfoLine not found in info header lines");
        Assert.assertTrue(
                header.getMetaDataInInputOrder().contains(infoLine),
                "TestInfoLine not found in set of all header lines");
        Assert.assertNotNull(header.getInfoHeaderLine("TestInfoLine"), "Lookup for TestInfoLine by key failed");

        Assert.assertFalse(
                asCollectionOfVCFHeaderLine(header.getFormatHeaderLines()).contains(infoLine),
                "TestInfoLine present in format header lines");
        Assert.assertFalse(
                asCollectionOfVCFHeaderLine(header.getFilterLines()).contains(infoLine),
                "TestInfoLine present in filter header lines");
        Assert.assertFalse(
                asCollectionOfVCFHeaderLine(header.getContigLines()).contains(infoLine),
                "TestInfoLine present in contig header lines");
        Assert.assertFalse(
                header.getOtherHeaderLines().contains(infoLine), "TestInfoLine present in other header lines");
    }

    private static <T extends VCFHeaderLine> Collection<VCFHeaderLine> asCollectionOfVCFHeaderLine(
            Collection<T> headers) {
        // create a collection of VCFHeaderLine so that contains tests work correctly
        return headers.stream().map(h -> (VCFHeaderLine) h).collect(Collectors.toList());
    }

    @Test
    public void testVCFHeaderAddFormatLine() {
        final VCFHeader header = getHiSeqVCFHeader();
        final VCFFormatHeaderLine formatLine = new VCFFormatHeaderLine(
                "TestFormatLine", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "test format line");
        header.addMetaDataLine(formatLine);

        Assert.assertTrue(
                header.getFormatHeaderLines().contains(formatLine), "TestFormatLine not found in format header lines");
        Assert.assertTrue(
                header.getMetaDataInInputOrder().contains(formatLine),
                "TestFormatLine not found in set of all header lines");
        Assert.assertNotNull(header.getFormatHeaderLine("TestFormatLine"), "Lookup for TestFormatLine by key failed");

        Assert.assertFalse(
                asCollectionOfVCFHeaderLine(header.getInfoHeaderLines()).contains(formatLine),
                "TestFormatLine present in info header lines");
        Assert.assertFalse(
                asCollectionOfVCFHeaderLine(header.getFilterLines()).contains(formatLine),
                "TestFormatLine present in filter header lines");
        Assert.assertFalse(
                asCollectionOfVCFHeaderLine(header.getContigLines()).contains(formatLine),
                "TestFormatLine present in contig header lines");
        Assert.assertFalse(
                header.getOtherHeaderLines().contains(formatLine), "TestFormatLine present in other header lines");
    }

    @Test
    public void testVCFHeaderAddFilterLine() {
        final VCFHeader header = getHiSeqVCFHeader();
        final String filterDesc = "TestFilterLine Description";
        final VCFFilterHeaderLine filterLine = new VCFFilterHeaderLine("TestFilterLine", filterDesc);
        Assert.assertEquals(filterDesc, filterLine.getDescription());
        header.addMetaDataLine(filterLine);

        Assert.assertTrue(
                header.getFilterLines().contains(filterLine), "TestFilterLine not found in filter header lines");
        Assert.assertTrue(
                header.getMetaDataInInputOrder().contains(filterLine),
                "TestFilterLine not found in set of all header lines");
        Assert.assertNotNull(header.getFilterHeaderLine("TestFilterLine"), "Lookup for TestFilterLine by key failed");

        Assert.assertFalse(
                asCollectionOfVCFHeaderLine(header.getInfoHeaderLines()).contains(filterLine),
                "TestFilterLine present in info header lines");
        Assert.assertFalse(
                asCollectionOfVCFHeaderLine(header.getFormatHeaderLines()).contains(filterLine),
                "TestFilterLine present in format header lines");
        Assert.assertFalse(
                asCollectionOfVCFHeaderLine(header.getContigLines()).contains(filterLine),
                "TestFilterLine present in contig header lines");
        Assert.assertFalse(
                header.getOtherHeaderLines().contains(filterLine), "TestFilterLine present in other header lines");
    }

    @Test
    public void testVCFHeaderAddContigLine() {
        final VCFHeader header = getHiSeqVCFHeader();
        final VCFContigHeaderLine contigLine = new VCFContigHeaderLine(
                "<ID=chr1,length=1234567890,assembly=FAKE,md5=f126cdf8a6e0c7f379d618ff66beb2da,species=\"Homo sapiens\">",
                VCFHeaderVersion.VCF4_0,
                VCFHeader.CONTIG_KEY,
                0);
        header.addMetaDataLine(contigLine);

        Assert.assertTrue(
                header.getContigLines().contains(contigLine), "Test contig line not found in contig header lines");
        Assert.assertTrue(
                header.getMetaDataInInputOrder().contains(contigLine),
                "Test contig line not found in set of all header lines");

        Assert.assertFalse(
                asCollectionOfVCFHeaderLine(header.getInfoHeaderLines()).contains(contigLine),
                "Test contig line present in info header lines");
        Assert.assertFalse(
                asCollectionOfVCFHeaderLine(header.getFormatHeaderLines()).contains(contigLine),
                "Test contig line present in format header lines");
        Assert.assertFalse(
                asCollectionOfVCFHeaderLine(header.getFilterLines()).contains(contigLine),
                "Test contig line present in filter header lines");
        Assert.assertFalse(
                header.getOtherHeaderLines().contains(contigLine), "Test contig line present in other header lines");
    }

    @Test
    public void testVCFHeaderContigLineMissingLength() {
        final VCFHeader header = getHiSeqVCFHeader();
        final VCFContigHeaderLine contigLine =
                new VCFContigHeaderLine("<ID=chr1>", VCFHeaderVersion.VCF4_0, VCFHeader.CONTIG_KEY, 0);
        header.addMetaDataLine(contigLine);
        Assert.assertTrue(
                header.getContigLines().contains(contigLine), "Test contig line not found in contig header lines");
        Assert.assertTrue(
                header.getMetaDataInInputOrder().contains(contigLine),
                "Test contig line not found in set of all header lines");

        final SAMSequenceDictionary sequenceDictionary = header.getSequenceDictionary();
        Assert.assertNotNull(sequenceDictionary);
        Assert.assertEquals(
                sequenceDictionary.getSequence("chr1").getSequenceLength(), SAMSequenceRecord.UNKNOWN_SEQUENCE_LENGTH);
    }

    @Test
    public void testVCFHeaderHonorContigLineOrder() throws IOException {
        try (final VCFFileReader vcfReader =
                new VCFFileReader(Path.of(variantTestDataRoot + "dbsnp_135.b37.1000.vcf"), false)) {
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
                    VCFHeader.CONTIG_KEY,
                    0);
            orderedList.add(outrageousContigLine);
            // make sure the extra contig line is outrageous enough to not collide with a real contig ID
            Assert.assertTrue(orderedList.contains(outrageousContigLine));
            orderedList.addAll(originalHeaderList.subList(splitInTheMiddle, originalHeaderList.size()));
            Assert.assertEquals(originalHeaderList.size() + 1, orderedList.size());

            // crete a new header from the ordered list, and test that getContigLines honors the input order, the
            // header numbering the lines by position
            final VCFHeader orderedHeader = new VCFHeader();
            orderedList.forEach(hl -> orderedHeader.addMetaDataLine(hl));
            final List<VCFContigHeaderLine> contigLines = orderedHeader.getContigLines();
            Assert.assertEquals(contigIDs(contigLines), contigIDs(orderedList));
            for (int i = 0; i < contigLines.size(); i++) {
                Assert.assertEquals(contigLines.get(i).getContigIndex().intValue(), i);
            }
        }
    }

    @Test
    public void testVCFSimpleHeaderLineGenericFieldGetter() {
        VCFHeader header = createHeader(VCF4headerStrings);
        List<VCFFilterHeaderLine> filters = header.getFilterLines();
        VCFFilterHeaderLine filterHeaderLine = filters.get(0);
        Map<String, String> genericFields = filterHeaderLine.getGenericFields();
        Assert.assertEquals(genericFields.get("ID"), "NoQCALL");
        Assert.assertEquals(genericFields.get("Description"), "Variant called by Dindel but not confirmed by QCALL");
    }

    @Test
    public void testVCFHeaderAddOtherLine() {
        final VCFHeader header = getHiSeqVCFHeader();
        final VCFHeaderLine otherLine = new VCFHeaderLine("TestOtherLine", "val");
        header.addMetaDataLine(otherLine);

        Assert.assertTrue(
                header.getOtherHeaderLines().contains(otherLine), "TestOtherLine not found in other header lines");
        Assert.assertTrue(
                header.getMetaDataInInputOrder().contains(otherLine),
                "TestOtherLine not found in set of all header lines");
        Assert.assertNotNull(header.getOtherHeaderLine("TestOtherLine"), "Lookup for TestOtherLine by key failed");

        Assert.assertFalse(
                asCollectionOfVCFHeaderLine(header.getInfoHeaderLines()).contains(otherLine),
                "TestOtherLine present in info header lines");
        Assert.assertFalse(
                asCollectionOfVCFHeaderLine(header.getFormatHeaderLines()).contains(otherLine),
                "TestOtherLine present in format header lines");
        Assert.assertFalse(
                asCollectionOfVCFHeaderLine(header.getContigLines()).contains(otherLine),
                "TestOtherLine present in contig header lines");
        Assert.assertFalse(
                asCollectionOfVCFHeaderLine(header.getFilterLines()).contains(otherLine),
                "TestOtherLine present in filter header lines");
    }

    @Test
    public void testVCFHeaderAddMetaDataLineDoesNotDuplicateContigs() {
        Path input = Path.of("src/test/resources/htsjdk/variant/ex2.vcf");

        VCFFileReader reader = new VCFFileReader(input, false);
        VCFHeader header = reader.getFileHeader();

        final int numContigLinesBefore = header.getContigLines().size();

        VCFInfoHeaderLine newInfoField = new VCFInfoHeaderLine(
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
    public void testVCFHeaderAddDuplicateContigLine() {
        Path input = Path.of("src/test/resources/htsjdk/variant/ex2.vcf");

        VCFFileReader reader = new VCFFileReader(input, false);
        VCFHeader header = reader.getFileHeader();

        final int numContigLinesBefore = header.getContigLines().size();
        // try to readd the first contig line
        header.addMetaDataLine(header.getContigLines().get(0));
        final int numContigLinesAfter = header.getContigLines().size();

        // assert that we have the same number of contig lines before and after
        Assert.assertEquals(numContigLinesBefore, numContigLinesAfter);
    }

    @Test
    public void twoContigLinesGivenTheSameIndexAreBothKeptAndNumberedInTheOrderGiven() {
        final VCFHeader header = headerOf(contigLine("ID=chrB,length=200", 0), contigLine("ID=chrA,length=100", 0));

        Assert.assertEquals(contigIDs(header.getContigLines()), List.of("chrB", "chrA"));
        Assert.assertEquals(contigIndices(header.getContigLines()), List.of(0, 1));
        Assert.assertEquals(contigIDs(contigLinesAmong(header.getMetaDataInSortedOrder())), List.of("chrB", "chrA"));
    }

    @Test
    public void twoContigLinesGivenTheSameIndexAreBothWritten() throws IOException {
        final VCFHeader header = headerOf(contigLine("ID=chrB,length=200", 0), contigLine("ID=chrA,length=100", 0));

        Assert.assertEquals(
                writtenMetaDataLines(header, "##contig"),
                List.of("##contig=<ID=chrB,length=200>", "##contig=<ID=chrA,length=100>"));
    }

    @Test
    public void twoContigLinesGivenTheSameIndexSurviveABcfRoundTrip() throws IOException {
        final VCFHeader header = headerOf(contigLine("ID=chrA,length=1000", 0), contigLine("ID=chrB,length=1000", 0));
        final VariantContext onChrB = new VariantContextBuilder(
                        "test", "chrB", 10, 10, List.of(Allele.create("A", true), Allele.create("C")))
                .make();
        final Path dir = Files.createTempDirectory("VCFHeaderUnitTest.");
        try {
            final Path bcf = dir.resolve("contigs.bcf");
            try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                    .setOutputPath(bcf)
                    .unsetOption(Options.INDEX_ON_THE_FLY)
                    .build()) {
                writer.writeHeader(header);
                writer.add(onChrB);
            }
            try (final VCFFileReader reader = new VCFFileReader(bcf, false);
                    final CloseableIterator<VariantContext> records = reader.iterator()) {
                Assert.assertEquals(contigIDs(reader.getFileHeader().getContigLines()), List.of("chrA", "chrB"));
                Assert.assertEquals(records.next().getContig(), "chrB");
            }
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void contigIndicesWithGapsAreNumberedFromZero() {
        final VCFHeader header = headerOf(
                contigLine("ID=chr1,length=100", 5),
                contigLine("ID=chr2,length=200", 10),
                contigLine("ID=chr3,length=300", 7));

        Assert.assertEquals(contigIDs(header.getContigLines()), List.of("chr1", "chr3", "chr2"));
        Assert.assertEquals(contigIndices(header.getContigLines()), List.of(0, 1, 2));
    }

    @Test
    public void contigLinesInAHashSetAreTakenInTheOrderOfTheirIndices() {
        final List<String> ids = new ArrayList<>();
        final Set<VCFHeaderLine> lines = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            ids.add("contig" + i);
            lines.add(contigLine("ID=contig" + i + ",length=1000", i));
        }

        Assert.assertEquals(contigIDs(new VCFHeader(lines).getContigLines()), ids);
    }

    @Test
    public void getContigLinesTheSequenceDictionaryAndTheWrittenHeaderAgree() throws IOException {
        final VCFHeader header = headerOf(
                contigLine("ID=chr2,length=200", 1),
                contigLine("ID=chr1,length=100", 0),
                contigLine("ID=chr3,length=300", 1));
        final List<String> expected = List.of("chr1", "chr2", "chr3");

        Assert.assertEquals(contigIDs(header.getContigLines()), expected);
        Assert.assertEquals(
                header.getSequenceDictionary().getSequences().stream()
                        .map(SAMSequenceRecord::getSequenceName)
                        .collect(Collectors.toList()),
                expected);
        Assert.assertEquals(
                header.getSequenceDictionary().getSequences().stream()
                        .map(SAMSequenceRecord::getSequenceIndex)
                        .collect(Collectors.toList()),
                contigIndices(header.getContigLines()));
        Assert.assertEquals(contigIDs(contigLinesAmong(header.getMetaDataInSortedOrder())), expected);
        Assert.assertEquals(contigIDs(writeAndReadBack(header).getContigLines()), expected);
    }

    @Test
    public void addMetaDataLineAppendsANewContigAtTheNextIndex() {
        final VCFHeader header = headerOf(contigLine("ID=chr1,length=100", 0), contigLine("ID=chr2,length=200", 1));

        header.addMetaDataLine(contigLine("ID=chrX,length=300", 0));

        Assert.assertEquals(contigIDs(header.getContigLines()), List.of("chr1", "chr2", "chrX"));
        Assert.assertEquals(contigIndices(header.getContigLines()), List.of(0, 1, 2));
    }

    @Test
    public void anIdenticalDuplicateContigLineIsCollapsed() {
        final VCFHeader header = headerOf(contigLine("ID=chr1,length=100", 0), contigLine("ID=chr1,length=100", 1));
        header.addMetaDataLine(contigLine("ID=chr1,length=100", 2));

        Assert.assertEquals(header.getContigLines(), List.of(contigLine("ID=chr1,length=100", 0)));
        Assert.assertEquals(contigLinesAmong(header.getMetaDataInInputOrder()), header.getContigLines());
    }

    @Test
    public void aDuplicateContigLineWithMoreAttributesAddsThemToTheFirst() {
        final VCFHeader header = headerOf(contigLine("ID=1", 0), contigLine("ID=1,length=123456", 1));

        Assert.assertEquals(header.getContigLines(), List.of(contigLine("ID=1,length=123456", 0)));
    }

    @Test
    public void aDuplicateContigLineWithOtherAttributesGivesTheUnionInTheOrderSeen() {
        final VCFHeader header = headerOf(
                contigLine("ID=chr1,length=100,assembly=b37", 0), contigLine("ID=chr1,species=human,md5=abc", 1));

        Assert.assertEquals(header.getContigLines().size(), 1);
        Assert.assertEquals(
                header.getContigLines().get(0).toString(),
                "contig=<ID=chr1,length=100,assembly=b37,species=human,md5=abc>");
    }

    @Test
    public void aCompatibleDuplicateContigAddedLaterReplacesTheLineInItsPlace() {
        final VCFInfoHeaderLine before = new VCFInfoHeaderLine("A", 1, VCFHeaderLineType.Integer, "before");
        final VCFInfoHeaderLine after = new VCFInfoHeaderLine("B", 1, VCFHeaderLineType.Integer, "after");
        final VCFHeader header = headerOf(before, contigLine("ID=chr1", 0), after, contigLine("ID=chr2", 1));

        header.addMetaDataLine(contigLine("ID=chr1,length=100", 5));

        final VCFContigHeaderLine merged = contigLine("ID=chr1,length=100", 0);
        Assert.assertEquals(header.getContigLines(), List.of(merged, contigLine("ID=chr2", 1)));
        Assert.assertEquals(
                new ArrayList<>(header.getMetaDataInInputOrder()),
                List.of(before, merged, after, contigLine("ID=chr2", 1)));
    }

    @Test
    public void aDuplicateContigLineWithADifferentLengthIsRejectedByTheConstructor() {
        final TribbleException.InvalidHeader e = Assert.expectThrows(
                TribbleException.InvalidHeader.class,
                () -> headerOf(contigLine("ID=chr1,length=100", 0), contigLine("ID=chr1,length=200", 1)));

        Assert.assertTrue(e.getMessage().contains("chr1"), e.getMessage());
        Assert.assertTrue(e.getMessage().contains("length"), e.getMessage());
        Assert.assertTrue(e.getMessage().contains("100"), e.getMessage());
        Assert.assertTrue(e.getMessage().contains("200"), e.getMessage());
    }

    @Test
    public void aDuplicateContigLineWithADifferentLengthIsRejectedByAddMetaDataLine() {
        final VCFHeader header = headerOf(contigLine("ID=chr1,length=100", 0));

        Assert.assertThrows(
                TribbleException.InvalidHeader.class,
                () -> header.addMetaDataLine(contigLine("ID=chr1,length=200", 1)));
        Assert.assertEquals(header.getContigLines(), List.of(contigLine("ID=chr1,length=100", 0)));
    }

    @Test
    public void theVcf42SpecFileThatDeclaresContig1TwiceReadsAsOneContigWithItsLength() {
        final Path vcf = Path.of("src/test/resources/htsjdk/hts-specs/test/vcf/4.2/passed/passed_meta_contig.vcf");
        try (final VCFFileReader reader = new VCFFileReader(vcf, false)) {
            final List<VCFContigHeaderLine> contigs = reader.getFileHeader().getContigLines();

            Assert.assertEquals(
                    contigIDs(contigs), List.of("1", "1AC", "1.*", "ABcd123", "contig_url", "contig_accession"));
            Assert.assertEquals(contigs.get(0).getGenericFieldValue("length"), "123456");
        }
    }

    @Test
    public void testVCFHeaderAddDuplicateHeaderLine() {
        Path input = Path.of("src/test/resources/htsjdk/variant/ex2.vcf");

        VCFFileReader reader = new VCFFileReader(input, false);
        VCFHeader header = reader.getFileHeader();

        VCFHeaderLine newHeaderLine = new VCFHeaderLine("key", "value");
        // add this new header line
        header.addMetaDataLine(newHeaderLine);

        final int numHeaderLinesBefore = header.getOtherHeaderLines().size();
        // readd the same header line
        header.addMetaDataLine(newHeaderLine);
        final int numHeaderLinesAfter = header.getOtherHeaderLines().size();

        // assert that we have the same number of other header lines before and after
        Assert.assertEquals(numHeaderLinesBefore, numHeaderLinesAfter);
    }

    @Test
    public void addMetaDataLineKeepsStructuredLinesWithTheSameKeyAndDifferentIDs() {
        final VCFAltHeaderLine deletion =
                new VCFAltHeaderLine("<ID=DEL,Description=\"Deletion\">", VCFHeaderVersion.VCF4_2);
        final VCFAltHeaderLine insertion =
                new VCFAltHeaderLine("<ID=INS,Description=\"Insertion\">", VCFHeaderVersion.VCF4_2);
        final VCFHeader header = new VCFHeader();
        header.addMetaDataLine(deletion);
        header.addMetaDataLine(insertion);

        Assert.assertEquals(new ArrayList<>(header.getMetaDataInInputOrder()), List.of(deletion, insertion));
        Assert.assertEquals(header.getOtherHeaderLines("ALT"), List.of(deletion, insertion));
    }

    @Test
    public void addMetaDataLineKeepsTheFirstOfTwoStructuredLinesWithTheSameKeyAndID() {
        final VCFAltHeaderLine first =
                new VCFAltHeaderLine("<ID=DEL,Description=\"Deletion\">", VCFHeaderVersion.VCF4_2);
        final VCFAltHeaderLine second = new VCFAltHeaderLine(
                "<ID=DEL,Description=\"Deletion relative to the reference\">", VCFHeaderVersion.VCF4_2);
        final VCFHeader header = new VCFHeader();
        header.addMetaDataLine(first);
        header.addMetaDataLine(second);

        Assert.assertEquals(new ArrayList<>(header.getMetaDataInInputOrder()), List.of(first));
        Assert.assertEquals(new ArrayList<>(header.getOtherHeaderLines()), List.of(first));
    }

    @Test
    public void theConstructorKeepsTheFirstOfTwoInfoLinesWithTheSameID() throws IOException {
        final VCFInfoHeaderLine first = new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "first");
        final VCFInfoHeaderLine second = new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "second");
        final VCFHeader header = headerOf(first, second);

        Assert.assertSame(header.getInfoHeaderLine("DP"), first);
        Assert.assertEquals(new ArrayList<>(header.getMetaDataInInputOrder()), List.of(first));
        Assert.assertEquals(writtenMetaDataLines(header, "##INFO"), List.of("##" + first));
    }

    @Test
    public void theConstructorKeepsTheFirstOfTwoFormatLinesWithTheSameID() throws IOException {
        final VCFFormatHeaderLine first = new VCFFormatHeaderLine("AD", 1, VCFHeaderLineType.Integer, "first");
        final VCFFormatHeaderLine second = new VCFFormatHeaderLine("AD", 1, VCFHeaderLineType.Integer, "second");
        final VCFHeader header = headerOf(first, second);

        Assert.assertSame(header.getFormatHeaderLine("AD"), first);
        Assert.assertEquals(new ArrayList<>(header.getMetaDataInInputOrder()), List.of(first));
        Assert.assertEquals(writtenMetaDataLines(header, "##FORMAT"), List.of("##" + first));
    }

    @Test
    public void theConstructorKeepsTheFirstOfTwoFilterLinesWithTheSameID() throws IOException {
        final VCFFilterHeaderLine first = new VCFFilterHeaderLine("LowQual", "first");
        final VCFFilterHeaderLine second = new VCFFilterHeaderLine("LowQual", "second");
        final VCFHeader header = headerOf(first, second);

        Assert.assertSame(header.getFilterHeaderLine("LowQual"), first);
        Assert.assertEquals(header.getFilterLines(), List.of(first));
        Assert.assertEquals(new ArrayList<>(header.getMetaDataInInputOrder()), List.of(first));
        Assert.assertEquals(writtenMetaDataLines(header, "##FILTER"), List.of("##" + first));
    }

    @Test
    public void theConstructorKeepsTheFirstOfTwoAltLinesWithTheSameID() {
        final VCFAltHeaderLine first =
                new VCFAltHeaderLine("<ID=DEL,Description=\"Deletion\">", VCFHeaderVersion.VCF4_2);
        final VCFAltHeaderLine second = new VCFAltHeaderLine(
                "<ID=DEL,Description=\"Deletion relative to the reference\">", VCFHeaderVersion.VCF4_2);
        final VCFHeader header = headerOf(first, second);

        Assert.assertEquals(header.getOtherHeaderLines("ALT"), List.of(first));
        Assert.assertEquals(new ArrayList<>(header.getMetaDataInInputOrder()), List.of(first));
    }

    @Test
    public void aParsedHeaderKeepsTheFirstOfTwoInfoLinesWithTheSameID() throws IOException {
        final String text = "##fileformat=VCFv4.2\n"
                + "##INFO=<ID=DP,Number=1,Type=Integer,Description=\"first\">\n"
                + "##INFO=<ID=DP,Number=A,Type=Float,Description=\"second\">\n"
                + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n";
        final VCFHeader header = (VCFHeader) new VCFCodec()
                .readActualHeader(new LineIteratorImpl(new SynchronousLineReader(new StringReader(text))));

        Assert.assertEquals(header.getInfoHeaderLine("DP").getDescription(), "first");
        Assert.assertEquals(
                header.getMetaDataInInputOrder().stream()
                        .filter(line -> line instanceof VCFInfoHeaderLine)
                        .count(),
                1);
        Assert.assertEquals(
                writtenMetaDataLines(header, "##INFO"),
                List.of("##INFO=<ID=DP,Number=1,Type=Integer,Description=\"first\">"));
    }

    @Test
    public void aParsedHeaderWithTwoConflictingContigLinesIsRejected() {
        final String text = "##fileformat=VCFv4.2\n"
                + "##contig=<ID=chr1,length=100>\n"
                + "##contig=<ID=chr1,length=200>\n"
                + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n";

        final TribbleException.InvalidHeader thrown =
                Assert.expectThrows(TribbleException.InvalidHeader.class, () -> new VCFCodec()
                        .readActualHeader(new LineIteratorImpl(new SynchronousLineReader(new StringReader(text)))));
        Assert.assertTrue(thrown.getMessage().contains("chr1"), thrown.getMessage());
    }

    @Test
    public void droppingADuplicateLineThatDiffersLogsAWarningNamingBothLines() throws Exception {
        final VCFInfoHeaderLine first = new VCFInfoHeaderLine("DupWarnDP", 1, VCFHeaderLineType.Integer, "first");
        final VCFInfoHeaderLine second = new VCFInfoHeaderLine("DupWarnDP", 1, VCFHeaderLineType.Integer, "second");

        final List<String> warnings =
                LogCapture.linesLoggedContaining("DupWarnDP", Log.LogLevel.WARNING, () -> headerOf(first, second));

        Assert.assertEquals(warnings.size(), 1, warnings.toString());
        Assert.assertTrue(warnings.get(0).contains("INFO"), warnings.get(0));
        Assert.assertTrue(warnings.get(0).contains(first.toString()), warnings.get(0));
        Assert.assertTrue(warnings.get(0).contains(second.toString()), warnings.get(0));
    }

    @Test
    public void addingADuplicateLineThatDiffersLogsAWarning() throws Exception {
        final VCFAltHeaderLine first =
                new VCFAltHeaderLine("<ID=DupWarnDEL,Description=\"Deletion\">", VCFHeaderVersion.VCF4_2);
        final VCFAltHeaderLine second =
                new VCFAltHeaderLine("<ID=DupWarnDEL,Description=\"Another deletion\">", VCFHeaderVersion.VCF4_2);
        final VCFHeader header = headerOf(first);

        final List<String> warnings = LogCapture.linesLoggedContaining(
                "DupWarnDEL", Log.LogLevel.WARNING, () -> header.addMetaDataLine(second));

        Assert.assertEquals(warnings.size(), 1, warnings.toString());
    }

    @Test
    public void addingAnIdenticalDuplicateLineLogsNothing() throws Exception {
        final VCFHeader header = headerOf(new VCFInfoHeaderLine("DupSilentDP", 1, VCFHeaderLineType.Integer, "depth"));

        final List<String> warnings = LogCapture.linesLoggedContaining(
                "DupSilentDP",
                Log.LogLevel.WARNING,
                () -> header.addMetaDataLine(
                        new VCFInfoHeaderLine("DupSilentDP", 1, VCFHeaderLineType.Integer, "depth")));

        Assert.assertEquals(warnings, List.of());
    }

    @Test
    public void addMetaDataLineKeepsRepeatedUnstructuredKeys() {
        final VCFHeaderLine a = new VCFHeaderLine("source", "a");
        final VCFHeaderLine b = new VCFHeaderLine("source", "b");
        final VCFHeader header = new VCFHeader();
        header.addMetaDataLine(a);
        header.addMetaDataLine(b);

        Assert.assertEquals(new ArrayList<>(header.getMetaDataInInputOrder()), List.of(a, b));
        Assert.assertEquals(header.getOtherHeaderLine("source"), a);
        Assert.assertEquals(header.getOtherHeaderLines("source"), List.of(a, b));
    }

    @Test
    public void getOtherHeaderLinesFromAParsedHeaderHasEveryRepeatedLine() {
        final String text = "##fileformat=VCFv4.3\n"
                + "##source=a\n"
                + "##SAMPLE=<ID=NA1,Description=\"First\">\n"
                + "##source=b\n"
                + "##SAMPLE=<ID=NA2,Description=\"Second\">\n"
                + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n";
        final VCFHeader header = (VCFHeader) new VCFCodec()
                .readActualHeader(new LineIteratorImpl(new SynchronousLineReader(new StringReader(text))));
        final VCFHeaderLine sourceA = new VCFHeaderLine("source", "a");
        final VCFHeaderLine sourceB = new VCFHeaderLine("source", "b");
        final VCFSampleHeaderLine sample1 =
                new VCFSampleHeaderLine("<ID=NA1,Description=\"First\">", VCFHeaderVersion.VCF4_3);
        final VCFSampleHeaderLine sample2 =
                new VCFSampleHeaderLine("<ID=NA2,Description=\"Second\">", VCFHeaderVersion.VCF4_3);

        Assert.assertEquals(new ArrayList<>(header.getOtherHeaderLines()), List.of(sourceA, sample1, sourceB, sample2));
        Assert.assertEquals(header.getOtherHeaderLines("source"), List.of(sourceA, sourceB));
        Assert.assertEquals(header.getOtherHeaderLines("SAMPLE"), List.of(sample1, sample2));
    }

    @Test
    public void getOtherHeaderLinesIsUnmodifiable() {
        final VCFHeaderLine line = new VCFHeaderLine("source", "a");
        final VCFHeader header = new VCFHeader();
        header.addMetaDataLine(line);

        final Collection<VCFHeaderLine> otherLines = header.getOtherHeaderLines();
        Assert.assertThrows(UnsupportedOperationException.class, () -> otherLines.remove(line));
    }

    @Test
    public void getGenotypeSamplesIsUnmodifiable() {
        final List<String> samples = new VCFHeader(Collections.emptySet(), List.of("S1", "S2")).getGenotypeSamples();

        Assert.assertThrows(UnsupportedOperationException.class, () -> samples.add("S3"));
    }

    @Test
    public void getSampleNamesInOrderIsUnmodifiable() {
        final List<String> samples = new VCFHeader(Collections.emptySet(), List.of("S2", "S1")).getSampleNamesInOrder();

        Assert.assertEquals(samples, List.of("S1", "S2"));
        Assert.assertThrows(UnsupportedOperationException.class, () -> samples.add("S3"));
    }

    @Test
    public void getSampleNameToOffsetIsUnmodifiable() {
        final Map<String, Integer> offsets =
                new VCFHeader(Collections.emptySet(), List.of("S2", "S1")).getSampleNameToOffset();

        Assert.assertEquals(offsets, Map.of("S2", 0, "S1", 1));
        Assert.assertThrows(UnsupportedOperationException.class, () -> offsets.put("S3", 2));
    }

    @Test
    public void getInfoHeaderLinesIsUnmodifiableAndReflectsALaterAddedLine() {
        final VCFInfoHeaderLine depth = new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "depth");
        final VCFInfoHeaderLine frequency = new VCFInfoHeaderLine("AF", 1, VCFHeaderLineType.Float, "frequency");
        final VCFHeader header = headerOf(depth);
        final Collection<VCFInfoHeaderLine> infoLines = header.getInfoHeaderLines();

        Assert.assertThrows(UnsupportedOperationException.class, () -> infoLines.remove(depth));
        header.addMetaDataLine(frequency);
        Assert.assertEquals(new ArrayList<>(infoLines), List.of(depth, frequency));
    }

    @Test
    public void getFormatHeaderLinesIsUnmodifiableAndReflectsALaterAddedLine() {
        final VCFFormatHeaderLine depth = new VCFFormatHeaderLine("DP", 1, VCFHeaderLineType.Integer, "depth");
        final VCFFormatHeaderLine quality = new VCFFormatHeaderLine("GQ", 1, VCFHeaderLineType.Integer, "quality");
        final VCFHeader header = headerOf(depth);
        final Collection<VCFFormatHeaderLine> formatLines = header.getFormatHeaderLines();

        Assert.assertThrows(UnsupportedOperationException.class, () -> formatLines.remove(depth));
        header.addMetaDataLine(quality);
        Assert.assertEquals(new ArrayList<>(formatLines), List.of(depth, quality));
    }

    @Test
    public void getContigLinesIsUnmodifiableAndReflectsALaterAddedLine() {
        final VCFHeader header = headerOf(contigLine("ID=chr1,length=100", 0));
        final List<VCFContigHeaderLine> contigs = header.getContigLines();

        Assert.assertThrows(UnsupportedOperationException.class, () -> contigs.remove(0));
        header.addMetaDataLine(contigLine("ID=chr2,length=200", 1));
        Assert.assertEquals(contigIDs(contigs), List.of("chr1", "chr2"));
    }

    @Test
    public void twoAltLinesAddedInCodeAreWritten() throws IOException {
        final VCFAltHeaderLine deletion =
                new VCFAltHeaderLine("<ID=DEL,Description=\"Deletion\">", VCFHeaderVersion.VCF4_2);
        final VCFAltHeaderLine insertion =
                new VCFAltHeaderLine("<ID=INS,Description=\"Insertion\">", VCFHeaderVersion.VCF4_2);
        final VCFHeader header = new VCFHeader();
        header.addMetaDataLine(deletion);
        header.addMetaDataLine(insertion);

        Assert.assertEquals(writeAndReadBack(header).getOtherHeaderLines("ALT"), List.of(deletion, insertion));
    }

    @Test
    public void setSequenceDictionaryKeepsMd5UrlAndSpeciesThroughAWrittenHeader() throws IOException {
        final SAMSequenceRecord record = new SAMSequenceRecord("chr1", 248956422);
        record.setAssembly("GRCh38");
        record.setMd5("6aef897c3d6ff0c78aff06ac189178dd");
        record.setAttribute(SAMSequenceRecord.URI_TAG, "https://example.com/GRCh38.fa");
        record.setSpecies("Homo sapiens");
        final VCFHeader header = new VCFHeader();
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(record)));

        final SAMSequenceRecord readBack =
                writeAndReadBack(header).getSequenceDictionary().getSequence("chr1");

        Assert.assertEquals(readBack.getAssembly(), "GRCh38");
        Assert.assertEquals(readBack.getMd5(), "6aef897c3d6ff0c78aff06ac189178dd");
        Assert.assertEquals(readBack.getAttribute(SAMSequenceRecord.URI_TAG), "https://example.com/GRCh38.fa");
        Assert.assertEquals(readBack.getSpecies(), "Homo sapiens");
    }

    /** Writes a VCF of the header alone and reads its header back. */
    private static VCFHeader writeAndReadBack(final VCFHeader header) throws IOException {
        final Path dir = Files.createTempDirectory("VCFHeaderUnitTest.");
        try {
            final Path vcf = dir.resolve("header.vcf");
            // without INDEX_ON_THE_FLY, a default option that needs a sequence dictionary
            try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                    .setOutputPath(vcf)
                    .setOptions(EnumSet.of(Options.ALLOW_MISSING_FIELDS_IN_HEADER))
                    .build()) {
                writer.writeHeader(header);
            }
            try (final VCFFileReader reader = new VCFFileReader(vcf, false)) {
                return reader.getFileHeader();
            }
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    /** Writes a VCF of the header alone and returns the lines written that start with the given prefix. */
    private static List<String> writtenMetaDataLines(final VCFHeader header, final String prefix) throws IOException {
        final Path dir = Files.createTempDirectory("VCFHeaderUnitTest.");
        try {
            final Path vcf = dir.resolve("header.vcf");
            try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                    .setOutputPath(vcf)
                    .setOptions(EnumSet.of(Options.ALLOW_MISSING_FIELDS_IN_HEADER))
                    .build()) {
                writer.writeHeader(header);
            }
            return Files.readAllLines(vcf).stream()
                    .filter(line -> line.startsWith(prefix))
                    .collect(Collectors.toList());
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    /** A header of the given lines, in the order given. */
    private static VCFHeader headerOf(final VCFHeaderLine... lines) {
        return new VCFHeader(new LinkedHashSet<>(Arrays.asList(lines)));
    }

    /** A contig line of the attributes written between its angle brackets, carrying the given index. */
    private static VCFContigHeaderLine contigLine(final String attributes, final int index) {
        return new VCFContigHeaderLine("<" + attributes + ">", VCFHeaderVersion.VCF4_2, VCFHeader.CONTIG_KEY, index);
    }

    private static List<String> contigIDs(final List<VCFContigHeaderLine> contigs) {
        return contigs.stream().map(VCFContigHeaderLine::getID).collect(Collectors.toList());
    }

    private static List<Integer> contigIndices(final List<VCFContigHeaderLine> contigs) {
        return contigs.stream().map(VCFContigHeaderLine::getContigIndex).collect(Collectors.toList());
    }

    /** The contig lines among the given lines, in their order. */
    private static List<VCFContigHeaderLine> contigLinesAmong(final Collection<VCFHeaderLine> lines) {
        return lines.stream()
                .filter(line -> line instanceof VCFContigHeaderLine)
                .map(line -> (VCFContigHeaderLine) line)
                .collect(Collectors.toList());
    }

    @Test
    public void anyVersionMayFollowAnyOther() {
        // the version only says what the header is written as, so nothing stops a change in either direction
        final VCFHeader header = new VCFHeader(VCFHeaderVersion.VCF4_3, Collections.emptySet(), Collections.emptySet());
        header.setVCFHeaderVersion(VCFHeaderVersion.VCF4_0);
        Assert.assertEquals(header.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_0);
        header.setVCFHeaderVersion(VCFHeaderVersion.VCF4_5);
        Assert.assertEquals(header.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_5);
        header.setVCFHeaderVersion(null);
        Assert.assertNull(header.getVCFHeaderVersion());
    }

    @Test
    public void versionComesFromTheFileformatLineAmongTheMetaData() {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFHeaderLine("fileformat", "VCFv4.1"));
        lines.add(new VCFHeaderLine("source", "test"));
        final VCFHeader header = new VCFHeader(lines);
        Assert.assertEquals(header.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_1);
        // the line itself is not kept as metadata; it is regenerated from the version
        Assert.assertNull(header.getOtherHeaderLine("fileformat"));
        Assert.assertEquals(header.getMetaDataInInputOrder().iterator().next().getValue(), "VCFv4.1");
    }

    @Test
    public void aHeaderWithoutAFileformatLineDeclaresNoVersion() {
        final VCFHeader header = new VCFHeader(Collections.singleton(new VCFHeaderLine("source", "test")));
        Assert.assertNull(header.getVCFHeaderVersion());
        Assert.assertEquals(header.getMetaDataInInputOrder(), Set.of(new VCFHeaderLine("source", "test")));
    }

    @Test
    public void aHeaderThatDeclaresNoVersionStillDeclaresNoneWhenRebuiltFromItsLines() {
        final VCFHeader header = new VCFHeader(Collections.singleton(new VCFHeaderLine("source", "test")));
        Assert.assertNull(new VCFHeader(header.getMetaDataInInputOrder()).getVCFHeaderVersion());
        Assert.assertNull(new VCFHeader(header.getMetaDataInSortedOrder(), List.of("NA1")).getVCFHeaderVersion());
    }

    @Test
    public void theHighestOfSeveralFileformatLinesWinsWhateverTheirOrder() {
        final VCFHeaderLine v41 = new VCFHeaderLine("fileformat", "VCFv4.1");
        final VCFHeaderLine v43 = new VCFHeaderLine("fileformat", "VCFv4.3");
        Assert.assertEquals(
                new VCFHeader(new LinkedHashSet<>(List.of(v41, v43))).getVCFHeaderVersion(), VCFHeaderVersion.VCF4_3);
        Assert.assertEquals(
                new VCFHeader(new LinkedHashSet<>(List.of(v43, v41))).getVCFHeaderVersion(), VCFHeaderVersion.VCF4_3);
    }

    @Test
    public void linesGatheredFromAVersionedAndAVersionlessHeaderKeepTheVersion() {
        final VCFHeader versioned =
                new VCFHeader(VCFHeaderVersion.VCF4_3, Collections.emptySet(), Collections.emptySet());
        final VCFHeader versionless = new VCFHeader(Collections.singleton(new VCFHeaderLine("source", "test")));
        final Set<VCFHeaderLine> versionedFirst = new LinkedHashSet<>(versioned.getMetaDataInInputOrder());
        versionedFirst.addAll(versionless.getMetaDataInInputOrder());
        final Set<VCFHeaderLine> versionlessFirst = new LinkedHashSet<>(versionless.getMetaDataInInputOrder());
        versionlessFirst.addAll(versioned.getMetaDataInInputOrder());
        Assert.assertEquals(new VCFHeader(versionedFirst).getVCFHeaderVersion(), VCFHeaderVersion.VCF4_3);
        Assert.assertEquals(new VCFHeader(versionlessFirst).getVCFHeaderVersion(), VCFHeaderVersion.VCF4_3);
    }

    @Test
    public void theVcf32FormatLineSetsTheVersion() {
        // VCF 3.2 spelled its version line "##format=VCRv3.2"
        final VCFHeader header = new VCFHeader(Collections.singleton(new VCFHeaderLine(
                VCFHeaderVersion.VCF3_2.getFormatString(), VCFHeaderVersion.VCF3_2.getVersionString())));
        Assert.assertEquals(header.getVCFHeaderVersion(), VCFHeaderVersion.VCF3_2);
        Assert.assertNull(header.getOtherHeaderLine("format"));
    }

    @Test
    public void aFormatLineThatNamesNoVersionIsAnOrdinaryLine() {
        final VCFHeader header = new VCFHeader();
        header.addMetaDataLine(new VCFHeaderLine("format", "some text"));
        Assert.assertNull(header.getVCFHeaderVersion());
        Assert.assertEquals(header.getOtherHeaderLine("format").getValue(), "some text");
    }

    @Test
    public void explicitVersionWinsOverTheFileformatLine() {
        final VCFHeader header = new VCFHeader(
                VCFHeaderVersion.VCF4_3,
                Collections.singleton(new VCFHeaderLine("fileformat", "VCFv4.1")),
                Collections.emptySet());
        Assert.assertEquals(header.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_3);
    }

    @Test(expectedExceptions = TribbleException.InvalidHeader.class)
    public void unknownVersionInAFileformatLineIsRejected() {
        new VCFHeader(Collections.singleton(new VCFHeaderLine("fileformat", "VCFv4.9")));
    }

    @Test
    public void addingAFileformatLineSetsTheVersion() {
        final VCFHeader header = new VCFHeader();
        header.addMetaDataLine(new VCFHeaderLine("fileformat", "VCFv4.5"));
        Assert.assertEquals(header.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_5);
        Assert.assertNull(header.getOtherHeaderLine("fileformat"));
    }

    @Test
    public void versionSurvivesTheCopyConstructor() {
        final VCFHeader header = new VCFHeader(VCFHeaderVersion.VCF4_5, Collections.emptySet(), Collections.emptySet());
        Assert.assertEquals(new VCFHeader(header).getVCFHeaderVersion(), VCFHeaderVersion.VCF4_5);
    }

    @Test
    public void versionSurvivesRebuildingFromTheMetaData() {
        // the idiom downstream code uses to copy a header with different samples
        final VCFHeader header = new VCFHeader(VCFHeaderVersion.VCF4_0, Collections.emptySet(), Collections.emptySet());
        final VCFHeader rebuilt = new VCFHeader(header.getMetaDataInInputOrder(), List.of("NA1"));
        Assert.assertEquals(rebuilt.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_0);
        Assert.assertEquals(
                new VCFHeader(header.getMetaDataInSortedOrder()).getVCFHeaderVersion(), VCFHeaderVersion.VCF4_0);
    }

    @Test
    public void testVCFHeaderSerialization() throws Exception {
        final VCFFileReader reader =
                new VCFFileReader(Path.of("src/test/resources/htsjdk/variant/HiSeq.10000.vcf"), false);
        final VCFHeader originalHeader = reader.getFileHeader();
        reader.close();

        final VCFHeader deserializedHeader = TestUtil.serializeAndDeserialize(originalHeader);

        Assert.assertEquals(
                deserializedHeader.getMetaDataInInputOrder(),
                originalHeader.getMetaDataInInputOrder(),
                "Header metadata does not match before/after serialization");
        Assert.assertEquals(
                deserializedHeader.getContigLines(),
                originalHeader.getContigLines(),
                "Contig header lines do not match before/after serialization");
        Assert.assertEquals(
                deserializedHeader.getFilterLines(),
                originalHeader.getFilterLines(),
                "Filter header lines do not match before/after serialization");
        Assert.assertEquals(
                deserializedHeader.getFormatHeaderLines(),
                originalHeader.getFormatHeaderLines(),
                "Format header lines do not match before/after serialization");
        Assert.assertEquals(
                deserializedHeader.getIDHeaderLines(),
                originalHeader.getIDHeaderLines(),
                "ID header lines do not match before/after serialization");
        Assert.assertEquals(
                deserializedHeader.getInfoHeaderLines(),
                originalHeader.getInfoHeaderLines(),
                "Info header lines do not match before/after serialization");
        Assert.assertEquals(
                deserializedHeader.getOtherHeaderLines(),
                originalHeader.getOtherHeaderLines(),
                "Other header lines do not match before/after serialization");
        Assert.assertEquals(
                deserializedHeader.getGenotypeSamples(),
                originalHeader.getGenotypeSamples(),
                "Genotype samples not the same before/after serialization");
        Assert.assertEquals(
                deserializedHeader.samplesWereAlreadySorted(),
                originalHeader.samplesWereAlreadySorted(),
                "Sortedness of samples not the same before/after serialization");
        Assert.assertEquals(
                deserializedHeader.getSampleNamesInOrder(),
                originalHeader.getSampleNamesInOrder(),
                "Sorted list of sample names in header not the same before/after serialization");
        Assert.assertEquals(
                deserializedHeader.getSampleNameToOffset(),
                originalHeader.getSampleNameToOffset(),
                "Sample name to offset map not the same before/after serialization");
        Assert.assertEquals(
                deserializedHeader.toString(),
                originalHeader.toString(),
                "String representation of header not the same before/after serialization");
    }

    @Test
    public void aDeserializedHeadersViewsReflectItsOwnLines() throws Exception {
        final VCFHeader original = new VCFHeader(
                new LinkedHashSet<>(List.of(
                        new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "depth"),
                        new VCFFormatHeaderLine("GQ", 1, VCFHeaderLineType.Integer, "quality"),
                        contigLine("ID=chr1,length=100", 0))),
                List.of("S1"));
        final VCFHeader deserialized = TestUtil.serializeAndDeserialize(original);

        deserialized.addMetaDataLine(new VCFInfoHeaderLine("AF", 1, VCFHeaderLineType.Float, "frequency"));
        deserialized.addMetaDataLine(new VCFFormatHeaderLine("AD", 1, VCFHeaderLineType.Integer, "depths"));
        deserialized.addMetaDataLine(contigLine("ID=chr2,length=200", 1));

        Assert.assertEquals(deserialized.getInfoHeaderLines().size(), 2);
        Assert.assertEquals(deserialized.getFormatHeaderLines().size(), 2);
        Assert.assertEquals(contigIDs(deserialized.getContigLines()), List.of("chr1", "chr2"));
        Assert.assertEquals(deserialized.getGenotypeSamples(), List.of("S1"));
        Assert.assertEquals(original.getInfoHeaderLines().size(), 1);
        Assert.assertEquals(original.getFormatHeaderLines().size(), 1);
        Assert.assertEquals(contigIDs(original.getContigLines()), List.of("chr1"));
    }

    @Test
    public void testVCFHeaderQuoteEscaping() throws Exception {
        // this test ensures that the end-to-end process of quote escaping is stable when headers are
        // read and re-written; ie that quotes that are already escaped won't be re-escaped. It does
        // this by reading a test file, adding a header line with an unescaped quote, writing out a copy
        // of the file, reading it back in and writing a second copy, and finally reading back the second
        // copy and comparing it to the first.

        // read an existing VCF
        final VCFFileReader originalFileReader =
                new VCFFileReader(Path.of("src/test/resources/htsjdk/variant/VCF4HeaderTest.vcf"), false);
        final VCFHeader originalHeader = originalFileReader.getFileHeader();

        // add a header line with quotes to the header
        final Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("ID", "VariantFiltration");
        attributes.put(
                "CommandLineOptions",
                "filterName=[ANNOTATION] filterExpression=[ANNOTATION == \"NA\" || ANNOTATION <= 2.0]");
        final VCFSimpleHeaderLine addedHeaderLine = new VCFSimpleHeaderLine("GATKCommandLine.Test", attributes);
        originalHeader.addMetaDataLine(addedHeaderLine);

        final VCFFilterHeaderLine originalCopyAnnotationLine1 = originalHeader.getFilterHeaderLine("ANNOTATION");
        Assert.assertNotNull(originalCopyAnnotationLine1);
        Assert.assertEquals(
                originalCopyAnnotationLine1.getGenericFieldValue("Description"),
                "ANNOTATION != \"NA\" || ANNOTATION <= 0.01",
                originalCopyAnnotationLine1.toString());

        final VCFFilterHeaderLine originalCopyAnnotationLine2 = originalHeader.getFilterHeaderLine("ANNOTATION2");
        Assert.assertNotNull(originalCopyAnnotationLine2);
        Assert.assertEquals(
                originalCopyAnnotationLine2.getGenericFieldValue("Description"),
                "ANNOTATION with quote \" that is unmatched but escaped");

        final VCFInfoHeaderLine originalEscapingQuoteInfoLine = originalHeader.getInfoHeaderLine("EscapingQuote");
        Assert.assertNotNull(originalEscapingQuoteInfoLine);
        Assert.assertEquals(
                originalEscapingQuoteInfoLine.getDescription(), "This description has an escaped \" quote in it");

        final VCFInfoHeaderLine originalEscapingBackslashInfoLine =
                originalHeader.getInfoHeaderLine("EscapingBackslash");
        Assert.assertNotNull(originalEscapingBackslashInfoLine);
        Assert.assertEquals(
                originalEscapingBackslashInfoLine.getDescription(),
                "This description has an escaped \\ backslash in it");

        final VCFInfoHeaderLine originalEscapingNonQuoteOrBackslashInfoLine =
                originalHeader.getInfoHeaderLine("EscapingNonQuoteOrBackslash");
        Assert.assertNotNull(originalEscapingNonQuoteOrBackslashInfoLine);
        Assert.assertEquals(
                originalEscapingNonQuoteOrBackslashInfoLine.getDescription(),
                "This other value has a \\n newline in it");

        // write the file out into a new copy
        final Path firstCopyVCFFile = Files.createTempFile("testEscapeHeaderQuotes1.", FileExtensions.VCF);
        firstCopyVCFFile.toFile().deleteOnExit();

        final VariantContextWriter firstCopyWriter = new VariantContextWriterBuilder()
                .setOutputPath(firstCopyVCFFile)
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
        final VCFHeaderLine firstCopyNewHeaderLine = firstCopyHeader.getOtherHeaderLine("GATKCommandLine.Test");
        Assert.assertNotNull(firstCopyNewHeaderLine);

        final VCFFilterHeaderLine firstCopyAnnotationLine1 = firstCopyHeader.getFilterHeaderLine("ANNOTATION");
        Assert.assertNotNull(firstCopyAnnotationLine1);
        Assert.assertEquals(
                firstCopyAnnotationLine1.getGenericFieldValue("Description"),
                "ANNOTATION != \"NA\" || ANNOTATION <= 0.01");

        final VCFFilterHeaderLine firstCopyAnnotationLine2 = firstCopyHeader.getFilterHeaderLine("ANNOTATION2");
        Assert.assertNotNull(firstCopyAnnotationLine2);

        final VCFInfoHeaderLine firstCopyEscapingQuoteInfoLine = firstCopyHeader.getInfoHeaderLine("EscapingQuote");
        Assert.assertNotNull(firstCopyEscapingQuoteInfoLine);
        Assert.assertEquals(
                firstCopyEscapingQuoteInfoLine.getDescription(), "This description has an escaped \" quote in it");

        final VCFInfoHeaderLine firstCopyEscapingBackslashInfoLine =
                firstCopyHeader.getInfoHeaderLine("EscapingBackslash");
        Assert.assertNotNull(firstCopyEscapingBackslashInfoLine);
        Assert.assertEquals(
                firstCopyEscapingBackslashInfoLine.getDescription(),
                "This description has an escaped \\ backslash in it");

        final VCFInfoHeaderLine firstCopyEscapingNonQuoteOrBackslashInfoLine =
                firstCopyHeader.getInfoHeaderLine("EscapingNonQuoteOrBackslash");
        Assert.assertNotNull(firstCopyEscapingNonQuoteOrBackslashInfoLine);
        Assert.assertEquals(
                firstCopyEscapingNonQuoteOrBackslashInfoLine.getDescription(),
                "This other value has a \\n newline in it");

        // write one more copy to make sure things don't get double escaped
        final Path secondCopyVCFFile = Files.createTempFile("testEscapeHeaderQuotes2.", FileExtensions.VCF);
        secondCopyVCFFile.toFile().deleteOnExit();
        final VariantContextWriter secondCopyWriter = new VariantContextWriterBuilder()
                .setOutputPath(secondCopyVCFFile)
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

        final VCFHeaderLine secondCopyNewHeaderLine = secondCopyHeader.getOtherHeaderLine("GATKCommandLine.Test");
        Assert.assertNotNull(secondCopyNewHeaderLine);

        final VCFFilterHeaderLine secondCopyAnnotationLine1 = secondCopyHeader.getFilterHeaderLine("ANNOTATION");
        Assert.assertNotNull(secondCopyAnnotationLine1);

        final VCFFilterHeaderLine secondCopyAnnotationLine2 = secondCopyHeader.getFilterHeaderLine("ANNOTATION2");
        Assert.assertNotNull(secondCopyAnnotationLine2);

        Assert.assertEquals(firstCopyNewHeaderLine, secondCopyNewHeaderLine);
        Assert.assertEquals(
                firstCopyNewHeaderLine.toStringEncoding(),
                "GATKCommandLine.Test=<ID=VariantFiltration,CommandLineOptions=\"filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]\">");
        Assert.assertEquals(
                secondCopyNewHeaderLine.toStringEncoding(),
                "GATKCommandLine.Test=<ID=VariantFiltration,CommandLineOptions=\"filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]\">");

        Assert.assertEquals(firstCopyAnnotationLine1, secondCopyAnnotationLine1);
        Assert.assertEquals(
                secondCopyAnnotationLine1.getGenericFieldValue("Description"),
                "ANNOTATION != \"NA\" || ANNOTATION <= 0.01");
        Assert.assertEquals(firstCopyAnnotationLine2, secondCopyAnnotationLine2);
        Assert.assertEquals(
                secondCopyAnnotationLine2.getGenericFieldValue("Description"),
                "ANNOTATION with quote \" that is unmatched but escaped");

        final VCFInfoHeaderLine secondCopyEscapingQuoteInfoLine = secondCopyHeader.getInfoHeaderLine("EscapingQuote");
        Assert.assertNotNull(secondCopyEscapingQuoteInfoLine);
        Assert.assertEquals(
                secondCopyEscapingQuoteInfoLine.getDescription(), "This description has an escaped \" quote in it");

        final VCFInfoHeaderLine secondCopyEscapingBackslashInfoLine =
                secondCopyHeader.getInfoHeaderLine("EscapingBackslash");
        Assert.assertNotNull(secondCopyEscapingBackslashInfoLine);
        Assert.assertEquals(
                secondCopyEscapingBackslashInfoLine.getDescription(),
                "This description has an escaped \\ backslash in it");

        final VCFInfoHeaderLine secondCopyEscapingNonQuoteOrBackslashInfoLine =
                secondCopyHeader.getInfoHeaderLine("EscapingNonQuoteOrBackslash");
        Assert.assertNotNull(secondCopyEscapingNonQuoteOrBackslashInfoLine);
        Assert.assertEquals(
                secondCopyEscapingNonQuoteOrBackslashInfoLine.getDescription(),
                "This other value has a \\n newline in it");

        firstCopyReader.close();
        secondCopyReader.close();
    }

    @Test
    public void testVcf42Roundtrip() throws Exception {
        // this test ensures that source/version fields are round-tripped properly

        // read an existing VCF
        Path expectedFile = Path.of("src/test/resources/htsjdk/variant/Vcf4.2WithSourceVersionInfoFields.vcf");

        // write the file out into a new copy
        final Path actualFile = Files.createTempFile("testVcf4.2roundtrip.", FileExtensions.VCF);
        actualFile.toFile().deleteOnExit();

        try (final VCFFileReader originalFileReader = new VCFFileReader(expectedFile, false);
                final VariantContextWriter copyWriter = new VariantContextWriterBuilder()
                        .setOutputPath(actualFile)
                        .setReferenceDictionary(createArtificialSequenceDictionary())
                        .setOptions(EnumSet.of(Options.ALLOW_MISSING_FIELDS_IN_HEADER, Options.INDEX_ON_THE_FLY))
                        .build()) {
            final VCFHeader originalHeader = originalFileReader.getFileHeader();

            copyWriter.writeHeader(originalHeader);
            for (final VariantContext variantContext : originalFileReader) {
                copyWriter.add(variantContext);
            }
        }

        final String actualContents = new String(Files.readAllBytes(actualFile), StandardCharsets.UTF_8);
        final String expectedContents = new String(Files.readAllBytes(expectedFile), StandardCharsets.UTF_8);
        Assert.assertEquals(actualContents, expectedContents);
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
    private static String md5SumFile(Path file) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to find MD5 digest");
        }
        InputStream is;
        try {
            is = Files.newInputStream(file);
        } catch (IOException e) {
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
        Path myTempFile = null;
        PrintWriter pw = null;
        try {
            myTempFile = Files.createTempFile("VCFHeader", "vcf");
            myTempFile.toFile().deleteOnExit();
            pw = new PrintWriter(Files.newBufferedWriter(myTempFile));
        } catch (IOException e) {
            Assert.fail("Unable to make a temp file!");
        }
        for (VCFHeaderLine line : header.getMetaDataInSortedOrder()) pw.println(line);
        pw.close();
        Assert.assertEquals(md5SumFile(myTempFile), md5sum);
    }

    public static final int VCF4headerStringCount = 16;

    public static final String VCF4headerStrings = "##fileformat=VCFv4.2\n" + "##filedate=2010-06-21\n"
            + "##reference=NCBI36\n"
            + "##INFO=<ID=GC, Number=0, Type=Flag, Description=\"Overlap with Gencode CCDS coding sequence\">\n"
            + "##INFO=<ID=DP, Number=1, Type=Integer, Description=\"Total number of reads in haplotype window\">\n"
            + "##INFO=<ID=AF, Number=A, Type=Float, Description=\"Dindel estimated population allele frequency\">\n"
            + "##INFO=<ID=CA, Number=1, Type=String, Description=\"Pilot 1 callability mask\">\n"
            + "##INFO=<ID=HP, Number=1, Type=Integer, Description=\"Reference homopolymer tract length\">\n"
            + "##INFO=<ID=NS, Number=1, Type=Integer, Description=\"Number of samples with data\">\n"
            + "##INFO=<ID=DB, Number=0, Type=Flag, Description=\"dbSNP membership build 129 - type match and indel sequence length match within 25 bp\">\n"
            + "##INFO=<ID=NR, Number=1, Type=Integer, Description=\"Number of reads covering non-ref variant on reverse strand\">\n"
            + "##INFO=<ID=NF, Number=1, Type=Integer, Description=\"Number of reads covering non-ref variant on forward strand\">\n"
            + "##FILTER=<ID=NoQCALL, Description=\"Variant called by Dindel but not confirmed by QCALL\">\n"
            + "##FORMAT=<ID=GT, Number=1, Type=String, Description=\"Genotype\">\n"
            + "##FORMAT=<ID=HQ, Number=2, Type=Integer, Description=\"Haplotype quality\">\n"
            + "##FORMAT=<ID=GQ, Number=1, Type=Integer, Description=\"Genotype quality\">\n"
            + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n";

    public static final String VCF4headerStrings_with_negativeOne = "##fileformat=VCFv4.2\n" + "##filedate=2010-06-21\n"
            + "##reference=NCBI36\n"
            + "##INFO=<ID=GC, Number=0, Type=Flag, Description=\"Overlap with Gencode CCDS coding sequence\">\n"
            + "##INFO=<ID=YY, Number=., Type=Integer, Description=\"Some weird value that has lots of parameters\">\n"
            + "##INFO=<ID=AF, Number=A, Type=Float, Description=\"Dindel estimated population allele frequency\">\n"
            + "##INFO=<ID=CA, Number=1, Type=String, Description=\"Pilot 1 callability mask\">\n"
            + "##INFO=<ID=HP, Number=1, Type=Integer, Description=\"Reference homopolymer tract length\">\n"
            + "##INFO=<ID=NS, Number=1, Type=Integer, Description=\"Number of samples with data\">\n"
            + "##INFO=<ID=DB, Number=0, Type=Flag, Description=\"dbSNP membership build 129 - type match and indel sequence length match within 25 bp\">\n"
            + "##INFO=<ID=NR, Number=1, Type=Integer, Description=\"Number of reads covering non-ref variant on reverse strand\">\n"
            + "##INFO=<ID=NF, Number=1, Type=Integer, Description=\"Number of reads covering non-ref variant on forward strand\">\n"
            + "##FILTER=<ID=NoQCALL, Description=\"Variant called by Dindel but not confirmed by QCALL\">\n"
            + "##FORMAT=<ID=GT, Number=1, Type=String, Description=\"Genotype\">\n"
            + "##FORMAT=<ID=HQ, Number=2, Type=Integer, Description=\"Haplotype quality\">\n"
            + "##FORMAT=<ID=TT, Number=., Type=Integer, Description=\"Lots of TTs\">\n"
            + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n";
}
