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

import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.readers.AsciiLineReader;
import htsjdk.tribble.readers.AsciiLineReaderIterator;
import htsjdk.tribble.readers.LineIteratorImpl;
import htsjdk.tribble.readers.LineReaderUtil;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.VariantContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

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
        checkMD5ofHeaderFile(header, "f05a57053a0c6a5bac15dba566f7f7ff");
    }

    @Test
    public void testVCF4ToVCF4_alternate() {
        VCFHeader header = createHeader(VCF4headerStrings_with_negativeOne);
        checkMD5ofHeaderFile(header, "b1d71cc94261053131f8d239d65a8c9f");
    }

    @Test
    public void testVCFHeaderSampleRenamingSingleSampleVCF() throws Exception {
        final VCFCodec codec = new VCFCodec();
        codec.setRemappedSampleName("FOOSAMPLE");
        final AsciiLineReaderIterator vcfIterator = new AsciiLineReaderIterator(new AsciiLineReader(IOUtil.getInputStream(IOUtil.getFile(variantTestDataRoot + "HiSeq.10000.vcf"))));
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
        final AsciiLineReaderIterator vcfIterator = new AsciiLineReaderIterator(new AsciiLineReader(IOUtil.getInputStream(IOUtil.getFile(variantTestDataRoot + "ex2.vcf"))));
        final VCFHeader header = (VCFHeader) codec.readHeader(vcfIterator).getHeaderValue();
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testVCFHeaderSampleRenamingSitesOnlyVCF() throws Exception {
        final VCFCodec codec = new VCFCodec();
        codec.setRemappedSampleName("FOOSAMPLE");
        final AsciiLineReaderIterator vcfIterator = new AsciiLineReaderIterator(new AsciiLineReader(IOUtil.getInputStream(IOUtil.getFile(variantTestDataRoot + "dbsnp_135.b37.1000.vcf"))));
        final VCFHeader header = (VCFHeader) codec.readHeader(vcfIterator).getHeaderValue();
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
            is = IOUtil.getInputStream(file);
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
            "##fileformat=VCFv4.0\n" +
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
            "##fileformat=VCFv4.0\n" +
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
