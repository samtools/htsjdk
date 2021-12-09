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
import htsjdk.samtools.util.Tuple;
import htsjdk.tribble.Tribble;
import htsjdk.tribble.readers.PositionalBufferedStream;
import htsjdk.utils.BCFToolsTestUtils;
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
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * @author amila
 * <p/>
 * Class BCF2WriterUnitTest
 * <p/>
 * This class tests out the ability of the BCF writer to correctly write BCF files
 */
public class BCF2WriterUnitTest extends VariantBaseTest {

    private File tempDir;

    /**
     * create a fake header of known quantity
     *
     * @return a fake VCF header
     */
    private static VCFHeader createFakeHeader() {
        final SAMSequenceDictionary sequenceDict = VariantBaseTest.createArtificialSequenceDictionary();
        final Set<VCFHeaderLine> metaData = new HashSet<>();
        final Set<String> additionalColumns = new HashSet<>();
        metaData.add(VCFHeader.makeHeaderVersionLine(VCFHeader.DEFAULT_VCF_VERSION));
        metaData.add(new VCFHeaderLine("two", "2"));
        additionalColumns.add("extra1");
        additionalColumns.add("extra2");
        final VCFHeader header = new VCFHeader(metaData, additionalColumns);
        header.addMetaDataLine(new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "x"));
        header.addMetaDataLine(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "x"));
        header.addMetaDataLine(new VCFFormatHeaderLine("BB", 1, VCFHeaderLineType.String, "x"));
        header.addMetaDataLine(new VCFFormatHeaderLine("GQ", 1, VCFHeaderLineType.Integer, "x"));
        header.setSequenceDictionary(sequenceDict);
        return header;
    }

    @BeforeClass
    private void createTemporaryDirectory() {
        tempDir = TestUtil.getTempDirectory("BCFWriter", "StaleIndex");
        tempDir.deleteOnExit();
    }


    /**
     * test, using the writer and reader, that we can output and input BCF without problems
     */
    @Test
    public void testWriteAndReadBCF() throws IOException {
        final File bcfOutputFile = File.createTempFile("testWriteAndReadVCF.", ".bcf", tempDir);
        bcfOutputFile.deleteOnExit();
        final VCFHeader header = BCF2WriterUnitTest.createFakeHeader();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
            .setOutputFile(bcfOutputFile).setReferenceDictionary(header.getSequenceDictionary())
            .unsetOption(Options.INDEX_ON_THE_FLY)
            .build()
        ) {
            writer.writeHeader(header);
            writer.add(createVC(header));
            writer.add(createVC(header));
        }
        final VariantContextTestProvider.VariantContextContainer container = VariantContextTestProvider
            .readAllVCs(bcfOutputFile, new BCF2Codec());
        int counter = 0;
        for (final VariantContext ignored : container.getVCs()) {
            counter++;
        }
        Assert.assertEquals(counter, 2);
    }


    /**
     * test, with index-on-the-fly option, that we can output and input BCF without problems
     */
    @Test
    public void testWriteAndReadBCFWithIndex() throws IOException {
        final File bcfOutputFile = File.createTempFile("testWriteAndReadVCF.", ".bcf", tempDir);
        bcfOutputFile.deleteOnExit();
        Tribble.indexFile(bcfOutputFile).deleteOnExit();
        final VCFHeader header = BCF2WriterUnitTest.createFakeHeader();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
            .setOutputFile(bcfOutputFile).setReferenceDictionary(header.getSequenceDictionary())
            .setOptions(EnumSet.of(Options.INDEX_ON_THE_FLY))
            .build()
        ) {
            writer.writeHeader(header);
            writer.add(createVC(header));
            writer.add(createVC(header));
        }
        final VariantContextTestProvider.VariantContextContainer container = VariantContextTestProvider
            .readAllVCs(bcfOutputFile, new BCF2Codec());
        int counter = 0;
        for (final VariantContext ignored : container.getVCs()) {
            counter++;
        }
        Assert.assertEquals(counter, 2);
    }

    /**
     * test, using the writer and reader, that we can output and input a BCF body without header
     */
    @Test
    public void testWriteAndReadBCFHeaderless() throws IOException {
        final File bcfOutputFile = File.createTempFile("testWriteAndReadBCFWithHeader.", ".bcf", tempDir);
        bcfOutputFile.deleteOnExit();
        final File bcfOutputHeaderlessFile = File.createTempFile("testWriteAndReadBCFHeaderless.", ".bcf", tempDir);
        bcfOutputHeaderlessFile.deleteOnExit();

        final VCFHeader header = BCF2WriterUnitTest.createFakeHeader();
        // we write two files, bcfOutputFile with the header, and bcfOutputHeaderlessFile with just the body
        try (final VariantContextWriter fakeBCFFileWriter = new VariantContextWriterBuilder()
            .setOutputFile(bcfOutputFile).setReferenceDictionary(header.getSequenceDictionary())
            .unsetOption(Options.INDEX_ON_THE_FLY)
            .build()
        ) {
            fakeBCFFileWriter.writeHeader(header); // writes header
        }

        try (final VariantContextWriter fakeBCFBodyFileWriter = new VariantContextWriterBuilder()
            .setOutputFile(bcfOutputHeaderlessFile).setReferenceDictionary(header.getSequenceDictionary())
            .unsetOption(Options.INDEX_ON_THE_FLY)
            .build()
        ) {
            fakeBCFBodyFileWriter.setHeader(header); // does not write header
            fakeBCFBodyFileWriter.add(createVC(header));
            fakeBCFBodyFileWriter.add(createVC(header));
        }

        try (final PositionalBufferedStream headerPbs =
                 new PositionalBufferedStream(new GZIPInputStream(new FileInputStream(bcfOutputFile)));
             final PositionalBufferedStream bodyPbs =
                 new PositionalBufferedStream(new GZIPInputStream(new FileInputStream(bcfOutputHeaderlessFile)))
        ) {

            final BCF2Codec codec = new BCF2Codec();
            codec.readHeader(headerPbs);
            // we use the header information read from identical file with header+body to read just the body of second file

            int counter = 0;
            while (!bodyPbs.isDone()) {
                codec.decode(bodyPbs);
                counter++;
            }
            Assert.assertEquals(counter, 2);
        }
    }

    /**
     * test, using the writer and reader, that phased information is preserved in a round trip
     */
    @Test
    public void testReadAndWritePhasedBCF() throws IOException {
        final File vcfInputFile = new File("src/test/resources/htsjdk/variant/phased.vcf");
        final File bcfOutputFile = File.createTempFile("testWriteAndReadBCFHeaderless.", ".bcf", tempDir);
        bcfOutputFile.deleteOnExit();

        try (final VCFFileReader vcfFile = new VCFFileReader(vcfInputFile)) {
            try (final VariantContextWriter bcfWriter = new VariantContextWriterBuilder()
                .setOutputFile(bcfOutputFile)
                .setReferenceDictionary(vcfFile.getFileHeader().getSequenceDictionary())
                .build()
            ) {
                bcfWriter.writeHeader(vcfFile.getFileHeader());
                for (final VariantContext vc : vcfFile.iterator().toList()) {
                    Assert.assertEquals(vc.getGenotypes().stream().filter(Genotype::isPhased).count(), 2);
                    bcfWriter.add(vc);
                }
            }

            // Reading the VCF and writing it to a BCF
            final File vcfOutputFile = File.createTempFile("testWriteAndReadBCFHeaderless.", ".vcf", tempDir);
            vcfOutputFile.deleteOnExit();

            try (final PositionalBufferedStream headerPbs =
                     new PositionalBufferedStream(new GZIPInputStream(new FileInputStream(bcfOutputFile)));
                 final VariantContextWriter vcfWriter = new VariantContextWriterBuilder()
                     .setOutputFile(vcfOutputFile)
                     .setReferenceDictionary(vcfFile.getFileHeader().getSequenceDictionary())
                     .build()
            ) {
                vcfWriter.writeHeader(vcfFile.getFileHeader());

                final BCF2Codec codec = new BCF2Codec();
                codec.readHeader(headerPbs);
                // we use the header information read from identical file with header+body to read just the body of second file

                while (!headerPbs.isDone()) {
                    final VariantContext vc = codec.decode(headerPbs);
                    Assert.assertEquals(vc.getGenotypes().stream().filter(Genotype::isPhased).count(), 2);
                    vcfWriter.add(vc);
                }
            }

            try (final VCFFileReader vcfOutput = new VCFFileReader(vcfInputFile)) {
                for (final VariantContext vc : vcfOutput.iterator().toList()) {
                    Assert.assertEquals(vc.getGenotypes().stream().filter(Genotype::isPhased).count(), 2);
                }
            }
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testWriteHeaderTwice() throws IOException {
        final File bcfOutputFile = File.createTempFile("testWriteAndReadVCF.", ".bcf", tempDir);
        bcfOutputFile.deleteOnExit();

        final VCFHeader header = BCF2WriterUnitTest.createFakeHeader();
        // prevent writing header twice
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
            .setOutputFile(bcfOutputFile).setReferenceDictionary(header.getSequenceDictionary())
            .unsetOption(Options.INDEX_ON_THE_FLY)
            .build()
        ) {
            writer.writeHeader(header);
            writer.writeHeader(header);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testChangeHeaderAfterWritingHeader() throws IOException {
        final File bcfOutputFile = File.createTempFile("testWriteAndReadVCF.", ".bcf", tempDir);
        bcfOutputFile.deleteOnExit();

        final VCFHeader header = BCF2WriterUnitTest.createFakeHeader();
        // prevent changing header if it's already written
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
            .setOutputFile(bcfOutputFile).setReferenceDictionary(header.getSequenceDictionary())
            .unsetOption(Options.INDEX_ON_THE_FLY)
            .build()
        ) {
            writer.writeHeader(header);
            writer.setHeader(header);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testChangeHeaderAfterWritingBody() throws IOException {
        final File bcfOutputFile = File.createTempFile("testWriteAndReadVCF.", ".bcf", tempDir);
        bcfOutputFile.deleteOnExit();

        final VCFHeader header = BCF2WriterUnitTest.createFakeHeader();
        // prevent changing header if part of body is already written
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
            .setOutputFile(bcfOutputFile).setReferenceDictionary(header.getSequenceDictionary())
            .unsetOption(Options.INDEX_ON_THE_FLY)
            .build()
        ) {
            writer.setHeader(header);
            writer.add(createVC(header));
            writer.setHeader(header);
        }
    }

    /**
     * create a fake VCF record
     *
     * @param header the VCF header
     * @return a VCFRecord
     */
    private static VariantContext createVC(final VCFHeader header) {
        final List<Allele> alleles = new ArrayList<>();
        final Map<String, Object> attributes = new HashMap<>();
        final GenotypesContext genotypes = GenotypesContext.create(header.getGenotypeSamples().size());

        alleles.add(Allele.create("A", true));
        alleles.add(Allele.create("ACC", false));

        attributes.put("DP", "50");
        for (final String name : header.getGenotypeSamples()) {
            final Genotype gt = new GenotypeBuilder(name, alleles.subList(1, 2))
                .GQ(0).attribute("BB", "1")
                .phased(true)
                .make();
            genotypes.add(gt);
        }
        return new VariantContextBuilder("RANDOM", "1", 1, 1, alleles)
            .genotypes(genotypes).attributes(attributes).make();
    }

    @DataProvider
    public Object[][] bcftoolsReadsHtsjdkOutputProvider() {
        return new Object[][]{
            {"phased.vcf"},
            {"test1.vcf"},
            {"test2.vcf"},
            {"NA12891.vcf"},
            {"NA12891.fp.vcf"},
            {"structuralvariants.vcf"},
            {"ex2.vcf"},
            {"test.vcf.bgz"},
            {"vcf43/all43Features.utf8.vcf"}
        };
    }

    @Test(dataProvider = "bcftoolsReadsHtsjdkOutputProvider")
    public void testBCFToolsReadsHtsjdkOutput(final String testFile) throws IOException {
        // Take an input VCF and read it into memory as our expected output
        // Take the same VCF and write it out as a BCF using htsjdk's BCF2Writer, use bcftools to convert from
        // BCF back to VCF, and read the converted VCF into memory again as our actual output
        final Path path = new File(VariantBaseTest.variantTestDataRoot + testFile).toPath();
        final Tuple<VCFHeader, List<VariantContext>> expectedVCF = readEntireVCFIntoMemory(path);
        final VCFHeader header = expectedVCF.a;
        final List<VariantContext> expectedVariantContexts = expectedVCF.b;

        final File bcfOutputFile = File.createTempFile("testBCFToolsRoundTrip" + testFile, ".bcf", tempDir);
        bcfOutputFile.deleteOnExit();

        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
            .setOutputFile(bcfOutputFile).setReferenceDictionary(header.getSequenceDictionary())
            .unsetOption(Options.INDEX_ON_THE_FLY)
            .build()
        ) {
            writer.writeHeader(header);
            for (final VariantContext vc : expectedVariantContexts) {
                writer.add(vc.fullyDecode(header, false));
            }
        }

        final Path converted = BCFToolsTestUtils.BCFToVCF(bcfOutputFile, "").toPath();
        final Tuple<VCFHeader, List<VariantContext>> actualVCF = readEntireVCFIntoMemory(converted);
        final List<VariantContext> actualVariantContexts = actualVCF.b;

        // Don't compare the headers, since they might contain extraneous lines, and the BCF codec isn't responsible
        // for headers
        Assert.assertEquals(expectedVariantContexts.size(), actualVCF.b.size());
        final int length = expectedVariantContexts.size();
        for (int i = 0; i < length; i++) {
            // Fully decode both variant contexts so that we're comparing actual objects and not their string
            // representations, which can be different without affecting semantics, e.g. number of digits in a double
            VariantBaseTest.assertVariantContextsAreEqual(
                actualVariantContexts.get(i).fullyDecode(header, false),
                expectedVariantContexts.get(i).fullyDecode(header, false)
            );
        }
    }

    @DataProvider
    public Object[][] htsjdkReadsBCFToolsOutputProvider() {
        return new Object[][]{
            {"phased.vcf"},
            {"test1.vcf"},
            {"test2.vcf"},
            {"NA12891.vcf"},
            {"NA12891.fp.vcf"},
            {"structuralvariants.vcf"},
            {"ex2.vcf"},
            {"test.vcf.bgz"},
            // bcftools does not to decoding of percent encoded VCFs, so its BCF output contains the literal characters
//            {"vcf43/all43Features.utf8.vcf"}
        };
    }

    @Test(dataProvider = "htsjdkReadsBCFToolsOutputProvider")
    public void testHtsjdkReadsBCFToolsOutput(final String testFile) {
        // Take an input VCF and read it into memory as our expected output
        // Take the same VCF and convert it to BCF using bcftools, then read the BCF into memory again as our actual output
        final Path path = new File(VariantBaseTest.variantTestDataRoot + testFile).toPath();
        final Tuple<VCFHeader, List<VariantContext>> expectedVCF = readEntireVCFIntoMemory(path);
        final VCFHeader header = expectedVCF.a;
        final List<VariantContext> expectedVariantContexts = expectedVCF.b;

        final File converted = BCFToolsTestUtils.VCFtoBCF(path.toFile(), "");
        final VCFFileReader reader = new VCFFileReader(converted, false);

        final List<VariantContext> actualVariantContexts = reader.iterator().stream().collect(Collectors.toList());

        // Don't compare the headers, since they might contain extraneous lines, and the BCF codec isn't responsible
        // for headers
        Assert.assertEquals(expectedVariantContexts.size(), actualVariantContexts.size());
        final int length = expectedVariantContexts.size();
        for (int i = 0; i < length; i++) {
            // Fully decode both variant contexts so that we're comparing actual objects and not their string
            // representations, which can be different without affecting semantics, e.g. number of digits in a double
            VariantBaseTest.assertVariantContextsAreEqual(
                actualVariantContexts.get(i).fullyDecode(header, false),
                expectedVariantContexts.get(i).fullyDecode(header, false)
            );
        }
    }
}

