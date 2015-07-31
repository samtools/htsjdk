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

import htsjdk.samtools.util.TestUtil;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.readers.AsciiLineReader;
import htsjdk.tribble.readers.AsciiLineReaderIterator;
import htsjdk.tribble.readers.LineIteratorImpl;
import htsjdk.tribble.readers.LineReaderUtil;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.VariantContext;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: aaron
 * Date: Jun 30, 2010
 * Time: 3:32:08 PM
 * To change this template use File | Settings | File Templates.
 */
public class VCFHeaderUnitTest extends VariantBaseTest {

    private VCFHeader createHeader(String headerStr) {
        VCFCodec codec = new VCFCodec();
        VCFHeader header = (VCFHeader) codec.readActualHeader(new LineIteratorImpl(LineReaderUtil.fromStringReader(
                new StringReader(headerStr), LineReaderUtil.LineReaderOption.SYNCHRONOUS)));
        Assert.assertEquals(header.getMetaDataInInputOrder().size(), VCF4headerStringCount);
        return header;
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
        final AsciiLineReaderIterator vcfIterator = new AsciiLineReaderIterator(new AsciiLineReader(new FileInputStream(variantTestDataRoot + "HiSeq.10000.vcf")));
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

    @Test
    public void testVCFHeaderDictionaryMerging() {
        VCFHeader headerOne = new VCFFileReader(new File(variantTestDataRoot + "dbsnp_135.b37.1000.vcf"), false).getFileHeader();
        VCFHeader headerTwo = new VCFHeader(headerOne); // deep copy
        final List<String> sampleList = new ArrayList<String>();
        sampleList.addAll(headerOne.getSampleNamesInOrder());

        // Check that the two dictionaries start out the same
        headerOne.getSequenceDictionary().assertSameDictionary(headerTwo.getSequenceDictionary());

        // Run the merge command
        final VCFHeader mergedHeader = new VCFHeader(VCFUtils.smartMergeHeaders(Arrays.asList(headerOne, headerTwo), false), sampleList);

        // Check that the mergedHeader's sequence dictionary matches the first two
        mergedHeader.getSequenceDictionary().assertSameDictionary(headerOne.getSequenceDictionary());
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testVCFHeaderSampleRenamingMultiSampleVCF() throws Exception {
        final VCFCodec codec = new VCFCodec();
        codec.setRemappedSampleName("FOOSAMPLE");
        final AsciiLineReaderIterator vcfIterator = new AsciiLineReaderIterator(new AsciiLineReader(new FileInputStream(variantTestDataRoot + "ex2.vcf")));
        final VCFHeader header = (VCFHeader) codec.readHeader(vcfIterator).getHeaderValue();
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testVCFHeaderSampleRenamingSitesOnlyVCF() throws Exception {
        final VCFCodec codec = new VCFCodec();
        codec.setRemappedSampleName("FOOSAMPLE");
        final AsciiLineReaderIterator vcfIterator = new AsciiLineReaderIterator(new AsciiLineReader(new FileInputStream(variantTestDataRoot + "dbsnp_135.b37.1000.vcf")));
        final VCFHeader header = (VCFHeader) codec.readHeader(vcfIterator).getHeaderValue();
    }

    @DataProvider(name = "HiSeqVCFHeaderDataProvider")
    public Object[][] getHiSeqVCFHeaderData() {
        final File vcf = new File("testdata/htsjdk/variant/HiSeq.10000.vcf");
        final VCFFileReader reader = new VCFFileReader(vcf, false);
        final VCFHeader header = reader.getFileHeader();
        reader.close();

        return new Object[][] {
                { header }
        };
    }

    @Test(dataProvider = "HiSeqVCFHeaderDataProvider")
    public void testVCFHeaderAddInfoLine( final VCFHeader header ) {
        final VCFInfoHeaderLine infoLine = new VCFInfoHeaderLine("TestInfoLine", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "test info line");
        header.addMetaDataLine(infoLine);

        Assert.assertTrue(header.getInfoHeaderLines().contains(infoLine), "TestInfoLine not found in info header lines");
        Assert.assertTrue(header.getMetaDataInInputOrder().contains(infoLine), "TestInfoLine not found in set of all header lines");
        Assert.assertNotNull(header.getInfoHeaderLine("TestInfoLine"), "Lookup for TestInfoLine by key failed");

        Assert.assertFalse(header.getFormatHeaderLines().contains(infoLine), "TestInfoLine present in format header lines");
        Assert.assertFalse(header.getFilterLines().contains(infoLine), "TestInfoLine present in filter header lines");
        Assert.assertFalse(header.getContigLines().contains(infoLine), "TestInfoLine present in contig header lines");
        Assert.assertFalse(header.getOtherHeaderLines().contains(infoLine), "TestInfoLine present in other header lines");
    }

    @Test(dataProvider = "HiSeqVCFHeaderDataProvider")
    public void testVCFHeaderAddFormatLine( final VCFHeader header ) {
        final VCFFormatHeaderLine formatLine = new VCFFormatHeaderLine("TestFormatLine", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "test format line");
        header.addMetaDataLine(formatLine);

        Assert.assertTrue(header.getFormatHeaderLines().contains(formatLine), "TestFormatLine not found in format header lines");
        Assert.assertTrue(header.getMetaDataInInputOrder().contains(formatLine), "TestFormatLine not found in set of all header lines");
        Assert.assertNotNull(header.getFormatHeaderLine("TestFormatLine"), "Lookup for TestFormatLine by key failed");

        Assert.assertFalse(header.getInfoHeaderLines().contains(formatLine), "TestFormatLine present in info header lines");
        Assert.assertFalse(header.getFilterLines().contains(formatLine), "TestFormatLine present in filter header lines");
        Assert.assertFalse(header.getContigLines().contains(formatLine), "TestFormatLine present in contig header lines");
        Assert.assertFalse(header.getOtherHeaderLines().contains(formatLine), "TestFormatLine present in other header lines");
    }

    @Test(dataProvider = "HiSeqVCFHeaderDataProvider")
    public void testVCFHeaderAddFilterLine( final VCFHeader header ) {
        final VCFFilterHeaderLine filterLine = new VCFFilterHeaderLine("TestFilterLine");
        header.addMetaDataLine(filterLine);

        Assert.assertTrue(header.getFilterLines().contains(filterLine), "TestFilterLine not found in filter header lines");
        Assert.assertTrue(header.getMetaDataInInputOrder().contains(filterLine), "TestFilterLine not found in set of all header lines");
        Assert.assertNotNull(header.getFilterHeaderLine("TestFilterLine"), "Lookup for TestFilterLine by key failed");

        Assert.assertFalse(header.getInfoHeaderLines().contains(filterLine), "TestFilterLine present in info header lines");
        Assert.assertFalse(header.getFormatHeaderLines().contains(filterLine), "TestFilterLine present in format header lines");
        Assert.assertFalse(header.getContigLines().contains(filterLine), "TestFilterLine present in contig header lines");
        Assert.assertFalse(header.getOtherHeaderLines().contains(filterLine), "TestFilterLine present in other header lines");
    }

    @Test(dataProvider = "HiSeqVCFHeaderDataProvider")
    public void testVCFHeaderAddContigLine( final VCFHeader header ) {
        final VCFContigHeaderLine contigLine = new VCFContigHeaderLine("<ID=chr1,length=1234567890,assembly=FAKE,md5=f126cdf8a6e0c7f379d618ff66beb2da,species=\"Homo sapiens\">", VCFHeaderVersion.VCF4_0, "chr1", 0);
        header.addMetaDataLine(contigLine);

        Assert.assertTrue(header.getContigLines().contains(contigLine), "Test contig line not found in contig header lines");
        Assert.assertTrue(header.getMetaDataInInputOrder().contains(contigLine), "Test contig line not found in set of all header lines");

        Assert.assertFalse(header.getInfoHeaderLines().contains(contigLine), "Test contig line present in info header lines");
        Assert.assertFalse(header.getFormatHeaderLines().contains(contigLine), "Test contig line present in format header lines");
        Assert.assertFalse(header.getFilterLines().contains(contigLine), "Test contig line present in filter header lines");
        Assert.assertFalse(header.getOtherHeaderLines().contains(contigLine), "Test contig line present in other header lines");
    }

    @Test(dataProvider = "HiSeqVCFHeaderDataProvider")
    public void testVCFHeaderAddOtherLine( final VCFHeader header ) {
        final VCFHeaderLine otherLine = new VCFHeaderLine("TestOtherLine", "val");
        header.addMetaDataLine(otherLine);

        Assert.assertTrue(header.getOtherHeaderLines().contains(otherLine), "TestOtherLine not found in other header lines");
        Assert.assertTrue(header.getMetaDataInInputOrder().contains(otherLine), "TestOtherLine not found in set of all header lines");
        Assert.assertNotNull(header.getOtherHeaderLine("TestOtherLine"), "Lookup for TestOtherLine by key failed");

        Assert.assertFalse(header.getInfoHeaderLines().contains(otherLine), "TestOtherLine present in info header lines");
        Assert.assertFalse(header.getFormatHeaderLines().contains(otherLine), "TestOtherLine present in format header lines");
        Assert.assertFalse(header.getContigLines().contains(otherLine), "TestOtherLine present in contig header lines");
        Assert.assertFalse(header.getFilterLines().contains(otherLine), "TestOtherLine present in filter header lines");
    }

    @Test
    public void testVCFHeaderAddMetaDataLineDoesNotDuplicateContigs() {
        File input = new File("testdata/htsjdk/variant/ex2.vcf");

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
        File input = new File("testdata/htsjdk/variant/ex2.vcf");

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
    public void testVCFHeaderAddDuplicateHeaderLine() {
        File input = new File("testdata/htsjdk/variant/ex2.vcf");

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
    public void testVCFHeaderSerialization() throws Exception {
        final VCFFileReader reader = new VCFFileReader(new File("testdata/htsjdk/variant/HiSeq.10000.vcf"), false);
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
        PrintWriter pw = null;
        try {
            myTempFile = File.createTempFile("VCFHeader", "vcf");
            myTempFile.deleteOnExit();
            pw = new PrintWriter(myTempFile);
        } catch (IOException e) {
            Assert.fail("Unable to make a temp file!");
        }
        for (VCFHeaderLine line : header.getMetaDataInSortedOrder())
            pw.println(line);
        pw.close();
        Assert.assertEquals(md5SumFile(myTempFile), md5sum);
    }

    public static int VCF4headerStringCount = 16;

    public static String VCF4headerStrings =
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


    public static String VCF4headerStrings_with_negativeOne =
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
