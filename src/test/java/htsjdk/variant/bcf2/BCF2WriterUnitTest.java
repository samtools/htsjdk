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
import htsjdk.variant.variantcontext.writer.*;
import htsjdk.variant.vcf.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @author amila
 *         <p/>
 *         Class BCF2WriterUnitTest
 *         <p/>
 *         This class tests out the ability of the BCF writer to correctly write BCF files
 */
public class BCF2WriterUnitTest extends VariantBaseTest {

    private Path tempDir;

    /**
     * create a fake header of known quantity
     *
     * @return a fake VCF header
     */
    private static VCFHeader createFakeHeader() {
        final SAMSequenceDictionary sequenceDict = createArtificialSequenceDictionary();
        final Set<VCFHeaderLine> metaData = new HashSet<>();
        final Set<String> additionalColumns = new HashSet<>();
        metaData.add(new VCFHeaderLine("two", "2"));
        additionalColumns.add("extra1");
        additionalColumns.add("extra2");
        final VCFHeader header = new VCFHeader(metaData, additionalColumns);
        header.addMetaDataLine(new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.String, "x"));
        header.addMetaDataLine(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "x"));
        header.addMetaDataLine(new VCFFormatHeaderLine("BB", 1, VCFHeaderLineType.String, "x"));
        header.addMetaDataLine(new VCFFormatHeaderLine("GQ", 1, VCFHeaderLineType.String, "x"));
        header.setSequenceDictionary(sequenceDict);
        return header;
    }

    @BeforeClass
    private void createTemporaryDirectory() {
        tempDir = TestUtil.getTempDirectoryAsPath("BCFWriter", "StaleIndex");
        tempDir.toFile().deleteOnExit();
    }

    /**
     * test, using the writer and reader, that we can output and input BCF without problems
     */
    @Test
    public void testWriteAndReadBCF() throws IOException {
        final Path bcfOutputFile = Files.createTempFile(tempDir, "testWriteAndReadVCF.", ".bcf");
        bcfOutputFile.toFile().deleteOnExit();
        final VCFHeader header = createFakeHeader();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(bcfOutputFile)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
            writer.add(createVC(header));
            writer.add(createVC(header));
        }
        VariantContextTestProvider.VariantContextContainer container =
                VariantContextTestProvider.readAllVCs(bcfOutputFile, new BCF2Codec());
        int counter = 0;
        final Iterator<VariantContext> it = container.getVCs().iterator();
        while (it.hasNext()) {
            it.next();
            counter++;
        }
        Assert.assertEquals(counter, 2);
    }

    /**
     * test, with index-on-the-fly option, that we can output and input BCF without problems
     */
    @Test
    public void testWriteAndReadBCFWithIndex() throws IOException {
        final Path bcfOutputFile = Files.createTempFile(tempDir, "testWriteAndReadVCF.", ".bcf");
        bcfOutputFile.toFile().deleteOnExit();
        Tribble.indexPath(bcfOutputFile).toFile().deleteOnExit();
        final VCFHeader header = createFakeHeader();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(bcfOutputFile)
                .setReferenceDictionary(header.getSequenceDictionary())
                .setOptions(EnumSet.of(Options.INDEX_ON_THE_FLY))
                .build()) {
            writer.writeHeader(header);
            writer.add(createVC(header));
            writer.add(createVC(header));
        }
        VariantContextTestProvider.VariantContextContainer container =
                VariantContextTestProvider.readAllVCs(bcfOutputFile, new BCF2Codec());
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
        final Path bcfOutputFile = Files.createTempFile(tempDir, "testWriteAndReadBCFWithHeader.", ".bcf");
        bcfOutputFile.toFile().deleteOnExit();
        final Path bcfOutputHeaderlessFile = Files.createTempFile(tempDir, "testWriteAndReadBCFHeaderless.", ".bcf");
        bcfOutputHeaderlessFile.toFile().deleteOnExit();

        final VCFHeader header = createFakeHeader();
        // we write two files, bcfOutputFile with the header, and bcfOutputHeaderlessFile with just the body
        try (final VariantContextWriter fakeBCFFileWriter = new VariantContextWriterBuilder()
                .setOutputPath(bcfOutputFile)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            fakeBCFFileWriter.writeHeader(header); // writes header
        }

        try (final VariantContextWriter fakeBCFBodyFileWriter = new VariantContextWriterBuilder()
                .setOutputPath(bcfOutputHeaderlessFile)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            fakeBCFBodyFileWriter.setHeader(header); // does not write header
            fakeBCFBodyFileWriter.add(createVC(header));
            fakeBCFBodyFileWriter.add(createVC(header));
        }

        VariantContextTestProvider.VariantContextContainer container;

        try (final PositionalBufferedStream headerPbs =
                        new PositionalBufferedStream(Files.newInputStream(bcfOutputFile));
                final PositionalBufferedStream bodyPbs =
                        new PositionalBufferedStream(Files.newInputStream(bcfOutputHeaderlessFile))) {

            BCF2Codec codec = new BCF2Codec();
            codec.readHeader(headerPbs);
            // we use the header information read from identical file with header+body to read just the body of second
            // file

            int counter = 0;
            while (!bodyPbs.isDone()) {
                VariantContext vc = codec.decode(bodyPbs);
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
        final Path vcfInputFile = Path.of("src/test/resources/htsjdk/variant/phased.vcf");
        final Path bcfOutputFile = Files.createTempFile(tempDir, "testWriteAndReadBCFHeaderless.", ".bcf");
        bcfOutputFile.toFile().deleteOnExit();

        try (VCFFileReader vcfFile = new VCFFileReader(vcfInputFile);
                VariantContextWriter bcfWriter = new VariantContextWriterBuilder()
                        .setOutputPath(bcfOutputFile)
                        .setReferenceDictionary(vcfFile.getFileHeader().getSequenceDictionary())
                        .build(); ) {
            bcfWriter.writeHeader(vcfFile.getFileHeader());

            for (VariantContext vc : vcfFile.iterator().toList()) {
                Assert.assertEquals(
                        vc.getGenotypes().stream().filter(Genotype::isPhased).count(), 2);
                bcfWriter.add(vc);
            }
            bcfWriter.close();

            // Reading the VCF and writing it to a BCF
            final Path vcfOutputFile = Files.createTempFile(tempDir, "testWriteAndReadBCFHeaderless.", ".vcf");
            vcfOutputFile.toFile().deleteOnExit();

            try (final PositionalBufferedStream headerPbs =
                            new PositionalBufferedStream(Files.newInputStream(bcfOutputFile));
                    VariantContextWriter vcfWriter = new VariantContextWriterBuilder()
                            .setOutputPath(vcfOutputFile)
                            .setReferenceDictionary(vcfFile.getFileHeader().getSequenceDictionary())
                            .build(); ) {
                vcfWriter.writeHeader(vcfFile.getFileHeader());

                BCF2Codec codec = new BCF2Codec();
                codec.readHeader(headerPbs);
                // we use the header information read from identical file with header+body to read just the body of
                // second file

                while (!headerPbs.isDone()) {
                    VariantContext vc = codec.decode(headerPbs);
                    Assert.assertEquals(
                            vc.getGenotypes().stream()
                                    .filter(Genotype::isPhased)
                                    .count(),
                            2);
                    vcfWriter.add(vc);
                }
                vcfWriter.close();
            }

            try (VCFFileReader vcfOutput = new VCFFileReader(vcfInputFile); ) {
                for (VariantContext vc : vcfOutput.iterator().toList()) {
                    Assert.assertEquals(
                            vc.getGenotypes().stream()
                                    .filter(Genotype::isPhased)
                                    .count(),
                            2);
                }
            }
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testWriteHeaderTwice() throws IOException {
        final Path bcfOutputFile = Files.createTempFile(tempDir, "testWriteAndReadVCF.", ".bcf");
        bcfOutputFile.toFile().deleteOnExit();

        final VCFHeader header = createFakeHeader();
        // prevent writing header twice
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(bcfOutputFile)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
            writer.writeHeader(header);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testChangeHeaderAfterWritingHeader() throws IOException {
        final Path bcfOutputFile = Files.createTempFile(tempDir, "testWriteAndReadVCF.", ".bcf");
        bcfOutputFile.toFile().deleteOnExit();

        final VCFHeader header = createFakeHeader();
        // prevent changing header if it's already written
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(bcfOutputFile)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
            writer.setHeader(header);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testChangeHeaderAfterWritingBody() throws IOException {
        final Path bcfOutputFile = Files.createTempFile(tempDir, "testWriteAndReadVCF.", ".bcf");
        bcfOutputFile.toFile().deleteOnExit();

        final VCFHeader header = createFakeHeader();
        // prevent changing header if part of body is already written
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(bcfOutputFile)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
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
    private VariantContext createVC(final VCFHeader header) {
        final List<Allele> alleles = new ArrayList<>();
        final Map<String, Object> attributes = new HashMap<>();
        final GenotypesContext genotypes =
                GenotypesContext.create(header.getGenotypeSamples().size());

        alleles.add(Allele.create("A", true));
        alleles.add(Allele.create("ACC", false));

        attributes.put("DP", "50");
        for (final String name : header.getGenotypeSamples()) {
            final Genotype gt = new GenotypeBuilder(name, alleles.subList(1, 2))
                    .GQ(0)
                    .attribute("BB", "1")
                    .phased(true)
                    .make();
            genotypes.add(gt);
        }
        return new VariantContextBuilder("RANDOM", "1", 1, 1, alleles)
                .genotypes(genotypes)
                .attributes(attributes)
                .make();
    }

    /**
     * Headers keep the version they declare through copies, rebuilds and merges, so one derived from a 4.3 input
     * declares 4.3; it is written all the same.
     */
    @Test
    public void headersDerivedFromA43InputAreWritten() throws IOException {
        final VCFHeader in;
        try (final VCFFileReader reader =
                new VCFFileReader(Path.of("src/test/resources/htsjdk/variant/vcf43/all43Features.vcf"), false)) {
            in = reader.getFileHeader();
        }
        Assert.assertEquals(in.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_3);
        final List<VCFHeader> derived = List.of(
                in,
                new VCFHeader(in),
                new VCFHeader(in.getMetaDataInInputOrder(), in.getGenotypeSamples()),
                new VCFHeader(
                        VCFUtils.smartMergeHeaders(List.of(in, new VCFHeader(in)), false), in.getGenotypeSamples()));
        for (final VCFHeader header : derived) {
            Assert.assertEquals(header.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_3);
            final Path output = Files.createTempFile(tempDir, "from43.", ".bcf");
            output.toFile().deleteOnExit();
            try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                    .setOutputPath(output)
                    .setReferenceDictionary(createArtificialSequenceDictionary())
                    .unsetOption(Options.INDEX_ON_THE_FLY)
                    .setOption(Options.ALLOW_MISSING_FIELDS_IN_HEADER)
                    .build()) {
                writer.writeHeader(header);
            }
            try (final VCFFileReader reader = new VCFFileReader(output, false)) {
                Assert.assertEquals(
                        reader.getFileHeader().getInfoHeaderLines().size(),
                        in.getInfoHeaderLines().size());
            }
        }
    }

    /** A {@code Number=LR} FORMAT field, whose length differs between the samples, is written and read back. */
    @Test
    public void aFormatFieldDeclaredNumberLRSurvivesARoundTrip() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "genotype"));
        lines.add(new VCFFormatHeaderLine("LAD", VCFHeaderLineCount.LR, VCFHeaderLineType.Integer, "local depths"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1", "s2"));
        header.setSequenceDictionary(createArtificialSequenceDictionary());

        final Allele ref = Allele.create("A", true);
        final Allele alt1 = Allele.create("C");
        final Allele alt2 = Allele.create("G");
        final VariantContext vc = new VariantContextBuilder("test", "1", 100, 100, List.of(ref, alt1, alt2))
                .genotypes(
                        new GenotypeBuilder("s1", List.of(ref, alt2))
                                .attribute("LAD", List.of(10, 5))
                                .make(),
                        new GenotypeBuilder("s2", List.of(alt1, alt2))
                                .attribute("LAD", List.of(0, 7, 3))
                                .make())
                .make();

        final Path output = Files.createTempFile(tempDir, "numberLR.", ".bcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
            writer.add(vc);
        }

        try (final VCFFileReader reader = new VCFFileReader(output, false)) {
            final VCFHeader headerRead = reader.getFileHeader();
            Assert.assertEquals(headerRead.getFormatHeaderLine("LAD").getCountType(), VCFHeaderLineCount.LR);
            final VariantContext vcRead = reader.iterator().next().fullyDecode(headerRead, false);
            Assert.assertEquals(vcRead.getGenotype("s1").getExtendedAttribute("LAD"), List.of(10, 5));
            Assert.assertEquals(vcRead.getGenotype("s2").getExtendedAttribute("LAD"), List.of(0, 7, 3));
        }
    }

    // GT phasing

    private static VCFHeader oneSampleGtHeader() {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "genotype"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(createArtificialSequenceDictionary());
        return header;
    }

    private static VariantContext triploidWithAllelePhasing(final boolean... allelePhasing) {
        final List<Allele> alleles = List.of(Allele.create("A", true), Allele.create("C"), Allele.create("G"));
        return new VariantContextBuilder("test", "1", 100, 100, alleles)
                .genotypes(new GenotypeBuilder("s1", alleles)
                        .allelePhasing(allelePhasing)
                        .make())
                .make();
    }

    @Test
    public void mixedPhasingSurvivesARoundTrip() throws IOException {
        final VCFHeader header = oneSampleGtHeader();
        final Path output = Files.createTempFile(tempDir, "mixedPhasing.", ".bcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
            writer.add(triploidWithAllelePhasing(false, false, true));
        }
        try (final VCFFileReader reader = new VCFFileReader(output, false)) {
            final Genotype g = reader.iterator().next().getGenotype("s1");
            Assert.assertTrue(g.hasPerAllelePhasing());
            Assert.assertFalse(g.isAllelePhased(0));
            Assert.assertFalse(g.isAllelePhased(1));
            Assert.assertTrue(g.isAllelePhased(2));
        }
    }

    @Test
    public void mixedPhasingIsWrittenAsTheSpecificationEncodesIt() throws IOException {
        final VCFHeader header = oneSampleGtHeader();
        final Path output = Files.createTempFile(tempDir, "mixedPhasingBytes.", ".bcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
            writer.add(triploidWithAllelePhasing(false, false, true));
        }
        // The record's genotype block is the GT key as a typed int8 (0x11, key) and then 0/1|2 as a vector of three
        // int8 (type byte 0x31), each (allele + 1) << 1 with the low bit set when phased. The round trip alone would
        // not notice the reader and writer agreeing on something else.
        final ByteBuffer written = ByteBuffer.wrap(Files.readAllBytes(output)).order(ByteOrder.LITTLE_ENDIAN);
        written.position(BCF2Codec.SIZEOF_BCF_HEADER);
        written.position(written.position() + Integer.BYTES + written.getInt()); // header length, then the header
        final int sharedLength = written.getInt();
        final int genotypesLength = written.getInt();
        final byte[] genotypes = new byte[genotypesLength];
        written.position(written.position() + sharedLength);
        written.get(genotypes);
        Assert.assertEquals(genotypes.length, 6, "one typed key and a vector of three int8");
        Assert.assertEquals(genotypes[0], (byte) 0x11);
        Assert.assertEquals(Arrays.copyOfRange(genotypes, 2, 6), new byte[] {0x31, 0x02, 0x04, 0x07});
    }

    @Test
    public void aFirstAllelePhaseTheFormatCannotExpressIsRefused() throws IOException {
        final VCFHeader header = oneSampleGtHeader();
        final Path output = Files.createTempFile(tempDir, "leadingPhase.", ".bcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
            final IllegalStateException refusal = Assert.expectThrows(
                    IllegalStateException.class, () -> writer.add(triploidWithAllelePhasing(true, false, false)));
            Assert.assertTrue(refusal.getMessage().contains("first allele"), refusal.getMessage());
        }
    }

    @Test
    public void aRefusedRecordLeavesNothingInTheOutput() throws IOException {
        final VCFHeader header = oneSampleGtHeader();
        final Path output = Files.createTempFile(tempDir, "refusedThenWritten.", ".bcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
            Assert.expectThrows(
                    IllegalStateException.class, () -> writer.add(triploidWithAllelePhasing(true, false, false)));
            writer.add(triploidWithAllelePhasing(false, false, true));
        }
        try (final VCFFileReader reader = new VCFFileReader(output, false)) {
            final List<VariantContext> records = reader.iterator().toList();
            Assert.assertEquals(records.size(), 1);
            Assert.assertEquals(records.get(0).getGenotype("s1").getGenotypeString(), "A/C|G");
        }
    }
}
