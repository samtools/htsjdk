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
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.TestUtil;
import htsjdk.tribble.Tribble;
import htsjdk.tribble.readers.PositionalBufferedStream;
import htsjdk.utils.BcftoolsTestUtils;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.GenotypesContext;
import htsjdk.variant.variantcontext.LazyGenotypesContext;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.VariantContextTestProvider;
import htsjdk.variant.variantcontext.writer.*;
import htsjdk.variant.vcf.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
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
import org.testng.SkipException;
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

    @BeforeClass(alwaysRun = true)
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
                .setVCFVersion(VCFHeaderVersion.VCF4_5)
                .build()) {
            writer.writeHeader(header);
            writer.add(vc);
        }

        try (final VCFFileReader reader = new VCFFileReader(output, false)) {
            final VCFHeader headerRead = reader.getFileHeader();
            Assert.assertEquals(headerRead.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_5);
            Assert.assertEquals(headerRead.getFormatHeaderLine("LAD").getCountType(), VCFHeaderLineCount.LR);
            final VariantContext vcRead = reader.iterator().next().fullyDecode(headerRead, false);
            Assert.assertEquals(vcRead.getGenotype("s1").getExtendedAttribute("LAD"), List.of(10, 5));
            Assert.assertEquals(vcRead.getGenotype("s2").getExtendedAttribute("LAD"), List.of(0, 7, 3));
        }
    }

    // Header text encoding

    /** The header is stored as VCF text, which is UTF-8 whatever the platform's default charset. */
    @Test
    public void nonAsciiHeaderTextSurvivesABcfRoundTrip() throws IOException {
        final String description = "Fréquence → λ 日本 " + new String(Character.toChars(0x1F600));
        final String sample = "sample_λ→日本";
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "genotype"));
        lines.add(new VCFInfoHeaderLine("FREQ", 1, VCFHeaderLineType.Float, description));
        final VCFHeader header = new VCFHeader(lines, List.of(sample));
        header.setSequenceDictionary(createArtificialSequenceDictionary());

        final Path output = Files.createTempFile(tempDir, "utf8Header.", ".bcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
        }

        final String fileAsUtf8 = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        Assert.assertTrue(fileAsUtf8.contains(description), "the header bytes are not UTF-8");
        try (final VCFFileReader reader = new VCFFileReader(output, false)) {
            Assert.assertEquals(reader.getFileHeader().getInfoHeaderLine("FREQ").getDescription(), description);
            Assert.assertEquals(reader.getFileHeader().getSampleNamesInOrder(), List.of(sample));
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

    // The VCF version of the embedded header text

    private static VCFHeader oneSampleGtHeader(final VCFHeaderVersion version) {
        final VCFHeader header = oneSampleGtHeader();
        header.setVCFHeaderVersion(version);
        return header;
    }

    /** Writes only the header to a BCF and returns the file's bytes as text; the embedded header text is in there. */
    private String writeBcfHeader(final VCFHeader header, final VCFHeaderVersion explicitVersion) throws IOException {
        final Path output = Files.createTempFile(tempDir, "headerVersion.", ".bcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .setVCFVersion(explicitVersion)
                .build()) {
            writer.writeHeader(header);
        }
        return new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
    }

    @Test
    public void theEmbeddedHeaderTextCarriesTheBuilderVersion() throws IOException {
        final VCFHeader header = oneSampleGtHeader(VCFHeaderVersion.VCF4_2);
        final String file = writeBcfHeader(header, VCFHeaderVersion.VCF4_4);
        Assert.assertTrue(file.contains("##fileformat=VCFv4.4\n"), file);
        Assert.assertFalse(file.contains("VCFv4.2"), file);
        Assert.assertEquals(header.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_2, "the caller's header changed");
    }

    @Test
    public void theEmbeddedHeaderTextCarriesTheHeadersVersion() throws IOException {
        Assert.assertTrue(
                writeBcfHeader(oneSampleGtHeader(VCFHeaderVersion.VCF4_3), null).contains("##fileformat=VCFv4.3\n"));
    }

    @Test
    public void theEmbeddedHeaderTextVersionIsFlooredAt42() throws IOException {
        Assert.assertTrue(
                writeBcfHeader(oneSampleGtHeader(VCFHeaderVersion.VCF4_0), null).contains("##fileformat=VCFv4.2\n"));
        Assert.assertTrue(writeBcfHeader(oneSampleGtHeader(), null).contains("##fileformat=VCFv4.2\n"));
    }

    @Test
    public void aHeaderWithNumberLAIsRefusedAt42() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "genotype"));
        lines.add(new VCFFormatHeaderLine("LAD", VCFHeaderLineCount.LA, VCFHeaderLineType.Integer, "local depths"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(createArtificialSequenceDictionary());
        final IllegalStateException refusal =
                Assert.expectThrows(IllegalStateException.class, () -> writeBcfHeader(header, null));
        Assert.assertTrue(refusal.getMessage().contains("FORMAT=<ID=LAD,Number=LA,"), refusal.getMessage());
        Assert.assertTrue(writeBcfHeader(header, VCFHeaderVersion.VCF4_5).contains("##fileformat=VCFv4.5\n"));
    }

    // The first allele's phase bit

    private static final Allele REF_A = Allele.create("A", true);
    private static final Allele ALT_C = Allele.create("C");

    /** GT-only header over chr1 for samples s1..sN, declaring the given version. */
    private static VCFHeader gtHeader(final VCFHeaderVersion version, final int nSamples) {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final List<String> samples = new ArrayList<>();
        for (int i = 1; i <= nSamples; i++) samples.add("s" + i);
        final VCFHeader header = new VCFHeader(lines, samples);
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        header.setVCFHeaderVersion(version);
        return header;
    }

    /** {@code 0|1}, {@code 0/1}, haploid {@code 1}, haploid {@code |1}, {@code ./.} and haploid {@code .}. */
    private static VariantContext phasingSampler() {
        return new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(
                        new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                                .phased(true)
                                .make(),
                        new GenotypeBuilder("s2", List.of(REF_A, ALT_C)).make(),
                        new GenotypeBuilder("s3", List.of(ALT_C)).make(),
                        new GenotypeBuilder("s4", List.of(ALT_C)).phased(true).make(),
                        new GenotypeBuilder("s5", List.of(Allele.NO_CALL, Allele.NO_CALL)).make(),
                        new GenotypeBuilder("s6", List.of(Allele.NO_CALL)).make())
                .make();
    }

    private Path writeBcf(final VCFHeader header, final VariantContext vc) throws IOException {
        final Path output = Files.createTempFile(tempDir, "phase.", ".bcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .clearOptions()
                .setOutputPath(output)
                .setOutputFileType(VariantContextWriterBuilder.OutputType.BCF)
                .build()) {
            writer.writeHeader(header);
            writer.add(vc);
        }
        return output;
    }

    private static VariantContext readOne(final Path bcf) {
        try (final VCFFileReader reader = new VCFFileReader(bcf, false);
                final CloseableIterator<VariantContext> records = reader.iterator()) {
            final VariantContext vc = records.next();
            for (final Genotype g : vc.getGenotypes()) g.getAlleles();
            return vc;
        }
    }

    /** The undecoded genotype block of a file's first record. */
    private static byte[] genotypeBlockOf(final Path bcf) {
        try (final VCFFileReader reader = new VCFFileReader(bcf, false);
                final CloseableIterator<VariantContext> records = reader.iterator()) {
            final LazyGenotypesContext genotypes =
                    (LazyGenotypesContext) records.next().getGenotypes();
            return ((BCF2Codec.LazyData) genotypes.getUnparsedGenotypeData()).bytes;
        }
    }

    /**
     * The GT columns of a file's last record as bcftools prints them. bcftools 1.24 refuses the BCF 2.1 magic htsjdk
     * writes, so it is shown a copy labelled 2.2: the two versions differ only in the header's IDX attributes, which
     * bcftools assigns in line order when they are absent, as htsjdk numbered them.
     */
    private String gtColumnsByBcftools(final Path bcf) throws IOException {
        final byte[] bytes = Files.readAllBytes(bcf);
        bytes[4] = 2;
        final Path asBcf22 = Files.createTempFile(tempDir, "phase.as22.", ".bcf");
        asBcf22.toFile().deleteOnExit();
        Files.write(asBcf22, bytes);
        final List<String> lines = BcftoolsTestUtils.viewAsVcf(asBcf22);
        final String record = lines.get(lines.size() - 1);
        return record.substring(record.indexOf("\tGT\t") + 4);
    }

    @Test
    public void theFirstAllelesPhaseBitIsWrittenAsBcftoolsWritesIt() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        final Path vcf = Files.createTempFile(tempDir, "phase.", ".vcf");
        vcf.toFile().deleteOnExit();
        Files.write(
                vcf,
                List.of(
                        "##fileformat=VCFv4.4",
                        "##FORMAT=<ID=GT,Number=1,Type=String,Description=\"gt\">",
                        "##contig=<ID=chr1,length=1000>",
                        "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\ts1\ts2\ts3",
                        "chr1\t100\t.\tA\tC\t.\t.\t.\tGT\t0|1\t0/1\t./."),
                StandardCharsets.UTF_8);
        final Path byBcftools = Files.createTempFile(tempDir, "phase.bcftools.", ".bcf");
        byBcftools.toFile().deleteOnExit();
        BcftoolsTestUtils.executeBcftoolsForStdout(
                "view", "--no-version", "-Ou", "-o", byBcftools.toString(), vcf.toString());

        // the same three diploid genotypes written by htsjdk: GT is index 1 in both dictionaries
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(
                        new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                                .phased(true)
                                .make(),
                        new GenotypeBuilder("s2", List.of(REF_A, ALT_C)).make(),
                        new GenotypeBuilder("s3", List.of(Allele.NO_CALL, Allele.NO_CALL)).make())
                .make();
        final Path byHtsjdk = writeBcf(gtHeader(VCFHeaderVersion.VCF4_4, 3), vc);
        Assert.assertEquals(genotypeBlockOf(byHtsjdk), genotypeBlockOf(byBcftools));
        Assert.assertEquals(genotypeBlockOf(byHtsjdk), new byte[] {0x11, 0x01, 0x21, 3, 5, 2, 4, 0, 0});
    }

    @Test
    public void bcftoolsReadsBackTheGenotypesHtsjdkWrote() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        // Diploid genotypes only: htsjdk pads a shorter genotype with MISSING where htslib expects END_OF_VECTOR,
        // and bcftools prints that padding as an allele. At 4.4 bcftools writes a leading indicator wherever the
        // first allele's bit disagrees with the others, so the bit must agree.
        final VariantContext diploids = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(
                        new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                                .phased(true)
                                .make(),
                        new GenotypeBuilder("s2", List.of(REF_A, ALT_C)).make(),
                        new GenotypeBuilder("s3", List.of(ALT_C, ALT_C))
                                .phased(true)
                                .make(),
                        new GenotypeBuilder("s4", List.of(Allele.NO_CALL, Allele.NO_CALL)).make())
                .make();
        Assert.assertEquals(
                gtColumnsByBcftools(writeBcf(gtHeader(VCFHeaderVersion.VCF4_4, 4), diploids)), "0|1\t0/1\t1|1\t./.");
        Assert.assertEquals(
                gtColumnsByBcftools(writeBcf(gtHeader(VCFHeaderVersion.VCF4_2, 4), diploids)), "0|1\t0/1\t1|1\t./.");
    }

    @Test
    public void genotypesReadBackAsWrittenAt42() throws IOException {
        final VariantContext vc = readOne(writeBcf(gtHeader(VCFHeaderVersion.VCF4_2, 6), phasingSampler()));
        assertPhasing(vc.getGenotype("s1"), "A|C", true, false);
        assertPhasing(vc.getGenotype("s2"), "A/C", false, false);
        assertPhasing(vc.getGenotype("s3"), "C", false, false);
        // below 4.4 a haploid call has no phase to keep
        assertPhasing(vc.getGenotype("s4"), "C", false, false);
        assertPhasing(vc.getGenotype("s5"), "./.", false, false);
        assertPhasing(vc.getGenotype("s6"), ".", false, false);
    }

    @Test
    public void genotypesReadBackAsWrittenAt44() throws IOException {
        final VariantContext vc = readOne(writeBcf(gtHeader(VCFHeaderVersion.VCF4_4, 6), phasingSampler()));
        assertPhasing(vc.getGenotype("s1"), "A|C", true, false);
        assertPhasing(vc.getGenotype("s2"), "A/C", false, false);
        // a haploid call is unphased at every version, as the text reader reads a bare "1"
        assertPhasing(vc.getGenotype("s3"), "C", false, false);
        assertPhasing(vc.getGenotype("s4"), "C", false, false);
        assertPhasing(vc.getGenotype("s5"), "./.", false, false);
        assertPhasing(vc.getGenotype("s6"), ".", false, false);
    }

    @Test
    public void aGenotypeWithALeadingIndicatorIsStillRefused() {
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                        .allelePhasing(new boolean[] {true, false})
                        .make())
                .make();
        Assert.expectThrows(IllegalStateException.class, () -> writeBcf(gtHeader(VCFHeaderVersion.VCF4_4, 1), vc));
    }

    private static void assertPhasing(
            final Genotype g, final String gtString, final boolean phased, final boolean perAllele) {
        Assert.assertEquals(g.getGenotypeString(), gtString, g.getSampleName());
        Assert.assertEquals(g.isPhased(), phased, g.getSampleName() + " isPhased");
        Assert.assertEquals(g.hasPerAllelePhasing(), perAllele, g.getSampleName() + " hasPerAllelePhasing");
    }
}
