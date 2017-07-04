/*
* Copyright (c) 2017 The Broad Institute
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

package htsjdk.variant.bcf2;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.TestUtil;
import htsjdk.tribble.FeatureCodecHeader;
import htsjdk.tribble.Tribble;
import htsjdk.tribble.readers.PositionalBufferedStream;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.GenotypesContext;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.VariantContextTestProvider;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author amila
 *         <p/>
 *         Class BCF2WriterUnitTest
 *         <p/>
 *         This class tests out the ability of the VCF writer to correctly write VCF files
 */
public class BCF2WriterUnitTest extends VariantBaseTest {

    /**
     * create a fake header of known quantity
     *
     * @param metaData          the header lines
     * @param additionalColumns the additional column names
     * @return a fake VCF header
     */
    public static VCFHeader createFakeHeader(final Set<VCFHeaderLine> metaData, final Set<String> additionalColumns,
                                             final SAMSequenceDictionary sequenceDict) {
        metaData.add(new VCFHeaderLine("two", "2"));
        additionalColumns.add("extra1");
        additionalColumns.add("extra2");
        final VCFHeader ret = new VCFHeader(metaData, additionalColumns);
        ret.addMetaDataLine(new VCFInfoHeaderLine("GT", 1, VCFHeaderLineType.String, "x"));
        ret.addMetaDataLine(new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.String, "x"));
        ret.addMetaDataLine(new VCFInfoHeaderLine("BB", 1, VCFHeaderLineType.String, "x"));
        ret.addMetaDataLine(new VCFInfoHeaderLine("GQ", 1, VCFHeaderLineType.String, "x"));
        ret.addMetaDataLine(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "x"));
        ret.addMetaDataLine(new VCFFormatHeaderLine("DP", 1, VCFHeaderLineType.String, "x"));
        ret.addMetaDataLine(new VCFFormatHeaderLine("BB", 1, VCFHeaderLineType.String, "x"));
        ret.addMetaDataLine(new VCFFormatHeaderLine("GQ", 1, VCFHeaderLineType.String, "x"));
        ret.setSequenceDictionary(sequenceDict);
        return ret;
    }

    @BeforeClass
    private void createTemporaryDirectory() {
        File tempDir = TestUtil.getTempDirectory("BCFWriter", "StaleIndex");
        tempDir.deleteOnExit();
    }


    /**
     * test, using the writer and reader, that we can output and input BCF without problems
     */
    @Test
    public void testWriteAndReadBCF() throws IOException {
        final File bcfOutputFile = VariantBaseTest.createTempFile("testWriteAndReadVCF.", ".bcf");

        Tribble.indexFile(bcfOutputFile).deleteOnExit();
        Set<VCFHeaderLine> metaData = new HashSet<VCFHeaderLine>();
        Set<String> additionalColumns = new HashSet<String>();
        final SAMSequenceDictionary sequenceDict = createArtificialSequenceDictionary();
        final VCFHeader header = createFakeHeader(metaData, additionalColumns, sequenceDict);
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputFile(bcfOutputFile).setReferenceDictionary(sequenceDict)
                .setOptions(EnumSet.of(Options.INDEX_ON_THE_FLY))
                .build()) {
            writer.writeHeader(header);
            writer.add(createVC(header));
            writer.add(createVC(header));
        }
        VariantContextTestProvider.VariantContextContainer container = VariantContextTestProvider
                .readAllVCs(bcfOutputFile, new BCF2Codec());
        int counter = 0;
        final Iterator<VariantContext> it = container.getVCs().iterator();
        while (it.hasNext()) {
            it.next();
            counter++;
        }
        Assert.assertEquals(counter, 2);
    }

    /**
     * test, using the writer and reader, that we can output and input a BCF body without header
     */
    @Test
    public void testWriteAndReadBCFHeaderless() throws IOException {
        final File bcfOutputFile = VariantBaseTest.createTempFile("testWriteAndReadBCFHeaderless.", ".bcf");
        Tribble.indexFile(bcfOutputFile).deleteOnExit();
        final File bcfOutputHeaderlessFile = VariantBaseTest.createTempFile("testWriteAndReadBCFHeaderless.", ".bcf");
        Tribble.indexFile(bcfOutputHeaderlessFile).deleteOnExit();

        Set<VCFHeaderLine> metaData = new HashSet<VCFHeaderLine>();
        Set<String> additionalColumns = new HashSet<String>();
        final SAMSequenceDictionary sequenceDict = createArtificialSequenceDictionary();
        final VCFHeader header = createFakeHeader(metaData, additionalColumns, sequenceDict);

        // we write two files, bcfOutputFile with the header and body, and bcfOutputHeaderlessFile with just the body
        try (final VariantContextWriter fakeBCFFileWriter = new VariantContextWriterBuilder()
                .setOutputFile(bcfOutputFile).setReferenceDictionary(sequenceDict)
                .build()) {
            fakeBCFFileWriter.writeHeader(header); // writes header
            fakeBCFFileWriter.add(createVC(header));
            fakeBCFFileWriter.add(createVC(header));
        }

        try (final VariantContextWriter fakeBCFBodyFileWriter = new VariantContextWriterBuilder()
                .setOutputFile(bcfOutputHeaderlessFile).setReferenceDictionary(sequenceDict)
                .build()) {
            fakeBCFBodyFileWriter.setVcfHeader(header); // does not write header
            fakeBCFBodyFileWriter.add(createVC(header));
            fakeBCFBodyFileWriter.add(createVC(header));
        }

        VariantContextTestProvider.VariantContextContainer container;

        try (final PositionalBufferedStream headerPbs = new PositionalBufferedStream(new FileInputStream(bcfOutputFile));
        final PositionalBufferedStream bodyPbs = new PositionalBufferedStream(new FileInputStream(bcfOutputHeaderlessFile))) {

            BCF2Codec codec = new BCF2Codec();
            FeatureCodecHeader readHeader = codec.readHeader(headerPbs);
            final VCFHeader vcfHeader = (VCFHeader) readHeader.getHeaderValue();
            // we use the header information read from identical file with header+body to read just the body of second file
            container =
                    new VariantContextTestProvider.VariantContextContainer(vcfHeader, new VariantContextTestProvider.VCIterable(codec, vcfHeader) {
                        @Override
                        public boolean hasNext() {
                            try {
                                return !bodyPbs.isDone();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public Object nextSource() {
                            return bodyPbs;
                        }
                    });

            int counter = 0;
            final Iterator<VariantContext> it = container.getVCs().iterator();
            while (it.hasNext()) {
                it.next();
                counter++;
            }
            Assert.assertEquals(counter, 2);
        }
    }

    /**
     * create a fake VCF record
     *
     * @param header the VCF header
     * @return a VCFRecord
     */
    private VariantContext createVC(final VCFHeader header) {

        return createVCGeneral(header, "1", 1);
    }

    private VariantContext createVCGeneral(final VCFHeader header, final String chrom, final int position) {
        final List<Allele> alleles = new ArrayList<Allele>();
        final Map<String, Object> attributes = new HashMap<String, Object>();
        final GenotypesContext genotypes = GenotypesContext.create(header.getGenotypeSamples().size());

        alleles.add(Allele.create("A", true));
        alleles.add(Allele.create("ACC", false));

        attributes.put("DP", "50");
        for (final String name : header.getGenotypeSamples()) {
            final Genotype gt = new GenotypeBuilder(name, alleles.subList(1, 2)).GQ(0).attribute("BB", "1").phased(true)
                    .make();
            genotypes.add(gt);
        }
        return new VariantContextBuilder("RANDOM", chrom, position, position, alleles)
                .genotypes(genotypes).attributes(attributes).make();
    }


}

