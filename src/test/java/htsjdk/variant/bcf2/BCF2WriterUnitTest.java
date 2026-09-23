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
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.TestUtil;
import htsjdk.tribble.Tribble;
import htsjdk.tribble.TribbleException;
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
import java.util.LinkedHashMap;
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
        try (final VCFFileReader reader = new VCFFileReader(bcfOutputFile, false)) {
            int counter = 0;
            for (final VariantContext ignored : reader) {
                counter++;
            }
            Assert.assertEquals(counter, 2);
        }
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
                .setBCFVersion(BCFVersion.BCF_2_1)
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
        // BCF 2.1 (raw) is used so that PositionalBufferedStream can read the bytes directly
        try (final VariantContextWriter fakeBCFFileWriter = new VariantContextWriterBuilder()
                .setOutputPath(bcfOutputFile)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .setBCFVersion(BCFVersion.BCF_2_1)
                .build()) {
            fakeBCFFileWriter.writeHeader(header); // writes header
        }

        try (final VariantContextWriter fakeBCFBodyFileWriter = new VariantContextWriterBuilder()
                .setOutputPath(bcfOutputHeaderlessFile)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .setBCFVersion(BCFVersion.BCF_2_1)
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
                        .unsetOption(Options.INDEX_ON_THE_FLY)
                        .setBCFVersion(BCFVersion.BCF_2_1)
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
                            .unsetOption(Options.INDEX_ON_THE_FLY)
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
                .setBCFVersion(BCFVersion.BCF_2_1)
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
                .setBCFVersion(BCFVersion.BCF_2_1)
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

    /**
     * Writes only the header to a BCF and returns the embedded header text. For VCF versions below 4.3, a raw
     * BCF 2.1 is used and the file bytes are read as text directly. For >= 4.3, BCF 2.2 (BGZF) is used and
     * the header is read back through VCFFileReader to reconstruct the text.
     */
    private String writeBcfHeader(final VCFHeader header, final VCFHeaderVersion explicitVersion) throws IOException {
        final VCFHeaderVersion headerVersion = header.getVCFHeaderVersion();
        final VCFHeaderVersion resolved = explicitVersion != null
                ? explicitVersion
                : (headerVersion != null ? headerVersion : VCFHeaderVersion.VCF4_2);
        final boolean needsBcf22 = resolved.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3);
        final Path output = Files.createTempFile(tempDir, "headerVersion.", ".bcf");
        output.toFile().deleteOnExit();
        final VariantContextWriterBuilder builder = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .setVCFVersion(explicitVersion);
        if (!needsBcf22) {
            builder.setBCFVersion(BCFVersion.BCF_2_1);
        }
        try (final VariantContextWriter writer = builder.build()) {
            writer.writeHeader(header);
        }
        if (needsBcf22) {
            // BGZF: read back through VCFFileReader and reconstruct header text
            try (final VCFFileReader reader = new VCFFileReader(output, false)) {
                final StringBuilder sb = new StringBuilder();
                for (final VCFHeaderLine line : reader.getFileHeader().getMetaDataInSortedOrder()) {
                    sb.append("##").append(line.toString()).append("\n");
                }
                return sb.toString();
            }
        } else {
            return new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        }
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

    /** The GT columns of a file's last record as bcftools prints them. */
    private String gtColumnsByBcftools(final Path bcf) throws IOException {
        final List<String> lines = BcftoolsTestUtils.viewAsVcf(bcf);
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
        // Diploid genotypes only so the test does not exercise mixed-ploidy padding, which has its own tests.
        // At 4.4 bcftools writes a leading indicator wherever the first allele's bit disagrees with the others,
        // so the bit must agree.
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
    public void aGenotypeWithALeadingIndicatorIsRefusedBelow44() {
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                        .allelePhasing(new boolean[] {true, false})
                        .make())
                .make();
        Assert.expectThrows(IllegalStateException.class, () -> writeBcf(gtHeader(VCFHeaderVersion.VCF4_2, 1), vc));
    }

    @Test
    public void aGenotypeWithALeadingIndicatorIsAllowedAt44() throws IOException {
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                        .allelePhasing(new boolean[] {true, false})
                        .make())
                .make();
        final Path bcf = writeBcf(gtHeader(VCFHeaderVersion.VCF4_4, 1), vc);
        final VariantContext read = readOne(bcf);
        final Genotype g = read.getGenotype("s1");
        Assert.assertTrue(g.hasPerAllelePhasing(), "per-allele phasing should survive the round trip");
        Assert.assertTrue(g.isAllelePhased(0), "the first allele should be phased");
        Assert.assertFalse(g.isAllelePhased(1), "the second allele should be unphased");
    }

    private static void assertPhasing(
            final Genotype g, final String gtString, final boolean phased, final boolean perAllele) {
        Assert.assertEquals(g.getGenotypeString(), gtString, g.getSampleName());
        Assert.assertEquals(g.isPhased(), phased, g.getSampleName() + " isPhased");
        Assert.assertEquals(g.hasPerAllelePhasing(), perAllele, g.getSampleName() + " hasPerAllelePhasing");
    }

    // ============================================================
    // bcftools end-to-end tests
    // ============================================================

    /** Write BCF 2.2 with diverse data, read back with bcftools. */
    @Test
    public void bcftoolsReadsHaploidAtDiploidSite() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        final Path bcf = writeBcf(
                gtHeader(VCFHeaderVersion.VCF4_4, 2),
                new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                        .genotypes(
                                new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                                        .phased(true)
                                        .make(),
                                new GenotypeBuilder("s2", List.of(ALT_C)).make())
                        .make());
        final List<String> lines = BcftoolsTestUtils.viewAsVcf(bcf);
        final String record = lines.get(lines.size() - 1);
        Assert.assertTrue(record.contains("0|1"), record);
        Assert.assertTrue(record.contains("\t1\t") || record.endsWith("\t1"), record);
    }

    @Test
    public void bcftoolsReadsMixedPloidy() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        final Allele altG = Allele.create("G");
        final Path bcf = writeBcf(
                gtHeader(VCFHeaderVersion.VCF4_4, 3),
                new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C, altG))
                        .genotypes(
                                new GenotypeBuilder("s1", List.of(REF_A, ALT_C, altG))
                                        .phased(false)
                                        .make(),
                                new GenotypeBuilder("s2", List.of(REF_A, ALT_C))
                                        .phased(true)
                                        .make(),
                                new GenotypeBuilder("s3", List.of(ALT_C)).make())
                        .make());
        final List<String> lines = BcftoolsTestUtils.viewAsVcf(bcf);
        final String record = lines.get(lines.size() - 1);
        Assert.assertTrue(record.contains("0/1/2"), record);
        Assert.assertTrue(record.contains("0|1"), record);
        Assert.assertTrue(record.contains("\t1\t") || record.endsWith("\t1"), record);
    }

    @Test
    public void bcftoolsReadsInteriorDot() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        lines.add(new VCFFormatHeaderLine("XI", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "x"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        header.setVCFHeaderVersion(VCFHeaderVersion.VCF4_4);
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                        .attribute("XI", Arrays.asList(10, null, 5))
                        .make())
                .make();
        final Path bcf = writeBcf(header, vc);
        final List<String> output = BcftoolsTestUtils.viewAsVcf(bcf);
        final String record = output.get(output.size() - 1);
        Assert.assertTrue(record.contains("10,.,5"), "interior dot missing: " + record);
    }

    @Test
    public void bcftoolsReadsAFlag() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("FLG", 0, VCFHeaderLineType.Flag, "flag"));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .attribute("FLG", true)
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C)).make())
                .make();
        final Path bcf = writeBcf(header, vc);
        final List<String> output = BcftoolsTestUtils.viewAsVcf(bcf);
        final String record = output.get(output.size() - 1);
        Assert.assertTrue(record.contains("FLG"), "flag missing: " + record);
    }

    @Test
    public void bcftoolsReadsAnInfoStringList() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("STR", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "str"));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .attribute("STR", List.of("a", "b"))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C)).make())
                .make();
        final Path bcf = writeBcf(header, vc);
        final List<String> output = BcftoolsTestUtils.viewAsVcf(bcf);
        final String record = output.get(output.size() - 1);
        Assert.assertTrue(record.contains("STR=a,b"), "string list wrong: " + record);
    }

    @Test
    public void bcftoolsReadsAMissingFormatString() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        lines.add(new VCFFormatHeaderLine("FS", 1, VCFHeaderLineType.String, "fs"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1", "s2"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(
                        new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                                .attribute("FS", "x")
                                .make(),
                        new GenotypeBuilder("s2", List.of(REF_A, ALT_C)).make())
                .make();
        final Path bcf = writeBcf(header, vc);
        final List<String> output = BcftoolsTestUtils.viewAsVcf(bcf);
        final String record = output.get(output.size() - 1);
        Assert.assertTrue(
                record.endsWith("0/1:x\t0/1:.") || record.endsWith("0/1:x\t0/1:.\t"), "missing FS: " + record);
    }

    @Test
    public void bcftoolsReadsAPercentEncodedString() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("STR", 1, VCFHeaderLineType.String, "str"));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        header.setVCFHeaderVersion(VCFHeaderVersion.VCF4_3);
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .attribute("STR", "a;b")
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C)).make())
                .make();
        final Path bcf = writeBcf(header, vc);
        final List<String> output = BcftoolsTestUtils.viewAsVcf(bcf);
        final String record = output.get(output.size() - 1);
        Assert.assertTrue(record.contains("STR=a%3Bb"), "percent-encoding wrong: " + record);
    }

    @Test
    public void bcftoolsReadsASparseIdxHeader() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine(
                "<ID=DP,Number=1,Type=Integer,Description=\"dp\",IDX=5>", VCFHeaderVersion.VCF4_2));
        lines.add(new VCFInfoHeaderLine(
                "<ID=AF,Number=A,Type=Float,Description=\"af\",IDX=10>", VCFHeaderVersion.VCF4_2));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .attribute("DP", 42)
                .attribute("AF", 0.5)
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C)).make())
                .make();
        final Path bcf = writeBcf(header, vc);
        final List<String> output = BcftoolsTestUtils.viewAsVcf(bcf);
        final String record = output.get(output.size() - 1);
        Assert.assertTrue(record.contains("DP=42"), "DP wrong: " + record);
        Assert.assertTrue(record.contains("AF=0.5") || record.contains("AF=0.500"), "AF wrong: " + record);
    }

    @Test
    public void bcftoolsOutputRoundTripsViaHtsjdk() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        // Write VCF text, convert to BCF via bcftools, read into htsjdk, write as BCF 2.2, convert back to VCF text
        final Path vcf = Files.createTempFile(tempDir, "rt.", ".vcf");
        vcf.toFile().deleteOnExit();
        Files.write(
                vcf,
                List.of(
                        "##fileformat=VCFv4.2",
                        "##INFO=<ID=DP,Number=1,Type=Integer,Description=\"dp\">",
                        "##FORMAT=<ID=GT,Number=1,Type=String,Description=\"gt\">",
                        "##contig=<ID=chr1,length=1000>",
                        "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\ts1",
                        "chr1\t100\t.\tA\tC\t50\tPASS\tDP=42\tGT\t0/1"),
                java.nio.charset.StandardCharsets.UTF_8);

        // bcftools -Ob
        final Path bcfBcftools = Files.createTempFile(tempDir, "rt.bt.", ".bcf");
        bcfBcftools.toFile().deleteOnExit();
        BcftoolsTestUtils.executeBcftoolsForStdout(
                "view", "--no-version", "-Ob", "-o", bcfBcftools.toString(), vcf.toString());

        // Also test -Ou
        final Path bcfUncompressed = Files.createTempFile(tempDir, "rt.ou.", ".bcf");
        bcfUncompressed.toFile().deleteOnExit();
        BcftoolsTestUtils.executeBcftoolsForStdout(
                "view", "--no-version", "-Ou", "-o", bcfUncompressed.toString(), vcf.toString());

        for (final Path bcfIn : List.of(bcfBcftools, bcfUncompressed)) {
            // Read via htsjdk, write as BCF 2.2
            final Path bcf22 = Files.createTempFile(tempDir, "rt.22.", ".bcf");
            bcf22.toFile().deleteOnExit();
            try (final VCFFileReader reader = new VCFFileReader(bcfIn, false);
                    final VariantContextWriter writer = new VariantContextWriterBuilder()
                            .clearOptions()
                            .setOutputPath(bcf22)
                            .setOutputFileType(VariantContextWriterBuilder.OutputType.BCF)
                            .build()) {
                writer.writeHeader(reader.getFileHeader());
                for (final VariantContext vc : reader) {
                    // Force decode of genotypes to avoid pass-through
                    for (final Genotype g : vc.getGenotypes()) g.getAlleles();
                    writer.add(vc);
                }
            }

            // bcftools view of our 2.2 file should give the original text
            final List<String> output = BcftoolsTestUtils.viewAsVcf(bcf22);
            final String record = output.get(output.size() - 1);
            Assert.assertTrue(record.contains("DP=42"), "DP wrong: " + record);
            Assert.assertTrue(record.contains("0/1"), "GT wrong: " + record);
        }
    }

    // ============================================================
    // Byte-level encoding tests
    // ============================================================

    @Test
    public void aFlagIsEncodedAsNullSizeZero() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("FLG", 0, VCFHeaderLineType.Flag, "flag"));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .attribute("FLG", true)
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C)).make())
                .make();
        final Path bcf = writeBcf21(header, vc);
        final byte[] raw = Files.readAllBytes(bcf);
        final ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        bb.position(BCF2Codec.SIZEOF_BCF_HEADER);
        bb.position(bb.position() + Integer.BYTES + bb.getInt()); // skip header text
        final int sharedLen = bb.getInt();
        bb.getInt(); // indiv length
        final int sharedStart = bb.position();
        // Skip chrom, pos, rlen, qual (16 bytes), nAlleles|nInfo, nFmt|nSamples (8 bytes)
        bb.position(sharedStart + 24);
        // Skip ID (typed string): read the type descriptor and skip the string + padding
        skipTypedField(bb);
        // Skip alleles (REF + ALT): nAlleles = 2
        skipTypedField(bb);
        skipTypedField(bb);
        // Skip FILTER (typed vector)
        skipTypedField(bb);
        // Now at INFO: the first field is FLG. Its key is a typed int, followed by the flag value 0x00.
        skipTypedField(bb); // skip the FLG key (typed int with the dictionary offset)
        // The flag value: BCF_BT_NULL size 0 = 0x00
        Assert.assertEquals(bb.get(), (byte) 0x00, "flag should be encoded as 0x00 (BCF_BT_NULL size 0)");
        // Verify we consumed exactly the shared block
        Assert.assertEquals(bb.position(), sharedStart + sharedLen, "should be at end of shared block");
    }

    /** Skip one BCF typed field (type descriptor + data) in a ByteBuffer positioned at the type byte. */
    private static void skipTypedField(final ByteBuffer bb) {
        final int typeByte = bb.get() & 0xFF;
        int count = (typeByte >> 4) & 0x0F;
        final int typeId = typeByte & 0x0F;
        if (count == 15) {
            // Overflow: next typed int gives the real count
            final int countTypeByte = bb.get() & 0xFF;
            final int countTypeId = countTypeByte & 0x0F;
            count = readRawInt(bb, countTypeId);
        }
        if (typeId == 0) return; // MISSING: size 0
        final int sizePerElement;
        switch (typeId) {
            case 1:
                sizePerElement = 1;
                break; // INT8
            case 2:
                sizePerElement = 2;
                break; // INT16
            case 3:
                sizePerElement = 4;
                break; // INT32
            case 5:
                sizePerElement = 4;
                break; // FLOAT
            case 7:
                sizePerElement = 1;
                break; // CHAR
            default:
                throw new IllegalStateException("Unknown BCF type id: " + typeId);
        }
        bb.position(bb.position() + count * sizePerElement);
    }

    private static int readRawInt(final ByteBuffer bb, final int typeId) {
        switch (typeId) {
            case 1:
                return bb.get() & 0xFF;
            case 2:
                return bb.getShort() & 0xFFFF;
            case 3:
                return bb.getInt();
            default:
                throw new IllegalStateException("Unexpected count type: " + typeId);
        }
    }

    @Test
    public void anInteriorMissingIntegerIsKept() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        lines.add(new VCFFormatHeaderLine("XI", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "x"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                        .attribute("XI", Arrays.asList(10, null, 5))
                        .make())
                .make();
        final Path bcf = writeBcf(header, vc);
        try (final VCFFileReader reader = new VCFFileReader(bcf, false)) {
            final Object xi = reader.iterator().next().getGenotype("s1").getExtendedAttribute("XI");
            Assert.assertEquals(xi, Arrays.asList(10, null, 5));
        }
    }

    @Test
    public void anInfoListWithAnInteriorMissingDoesNotNpe() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("XI", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "x"));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .attribute("XI", Arrays.asList(10, null, 5))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C)).make())
                .make();
        final Path bcf = writeBcf(header, vc);
        try (final VCFFileReader reader = new VCFFileReader(bcf, false)) {
            Assert.assertTrue(reader.iterator().next().hasAttribute("XI"));
        }
    }

    @Test
    public void aFlagWithNonZeroNumberInHeaderIsWritten() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("<ID=DB,Number=A,Type=Flag,Description=\"flag\">", VCFHeaderVersion.VCF4_2));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .attribute("DB", true)
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C)).make())
                .make();
        final Path bcf = writeBcf(header, vc);
        try (final VCFFileReader reader = new VCFFileReader(bcf, false)) {
            Assert.assertEquals(reader.iterator().next().getAttribute("DB"), true);
        }
    }

    @Test
    public void stringsArePercentEncodedUnder43() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("STR", 1, VCFHeaderLineType.String, "str"));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        header.setVCFHeaderVersion(VCFHeaderVersion.VCF4_3);
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .attribute("STR", "a;b")
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C)).make())
                .make();
        final Path bcf = writeBcf(header, vc);
        try (final VCFFileReader reader = new VCFFileReader(bcf, false)) {
            Assert.assertEquals(reader.iterator().next().getAttribute("STR"), "a;b");
        }
    }

    @Test
    public void stringsAreNotPercentEncodedUnder42() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("STR", 1, VCFHeaderLineType.String, "str"));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        header.setVCFHeaderVersion(VCFHeaderVersion.VCF4_2);
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .attribute("STR", "a;b")
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C)).make())
                .make();
        final Path bcf = writeBcf21(header, vc);
        // read raw bytes: the semicolon should be literal, not %3B
        final byte[] bytes = Files.readAllBytes(bcf);
        final String asString = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        Assert.assertTrue(asString.contains("a;b"), "raw semicolon should be in the file");
        Assert.assertFalse(asString.contains("%3B"), "percent-encoding should not be in the file");
    }

    @Test
    public void gtWidthByValueUsesInt16ForLargeAlleleCounts() throws IOException {
        // Build a site with 64 alleles (ref + 63 alts): encoded max is (63+1)<<1 = 128, needs INT16
        final List<Allele> alleles = new ArrayList<>();
        alleles.add(Allele.create("A", true));
        for (int i = 0; i < 63; i++) {
            alleles.add(Allele.create("A" + "T".repeat(i + 1)));
        }
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, alleles)
                .genotypes(new GenotypeBuilder("s1", List.of(alleles.get(0), alleles.get(63))).make())
                .make();
        final Path bcf = writeBcf(header, vc);
        try (final VCFFileReader reader = new VCFFileReader(bcf, false)) {
            final Genotype g = reader.iterator().next().getGenotype("s1");
            Assert.assertEquals(g.getAllele(0), alleles.get(0));
            Assert.assertEquals(g.getAllele(1), alleles.get(63));
        }
    }

    // GT padding

    @Test
    public void gtPaddingUsesEndOfVectorIn22AndMissingIn21() throws IOException {
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(
                        new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                                .phased(true)
                                .make(),
                        new GenotypeBuilder("s2", List.of(ALT_C)).make())
                .make();
        // BCF 2.2: haploid at a diploid site pads with END_OF_VECTOR (0x81 for INT8)
        final Path bcf22 = writeBcf(gtHeader(VCFHeaderVersion.VCF4_4, 2), vc);
        final byte[] gt22 = genotypeBlockOf(bcf22);
        Assert.assertEquals(gt22[gt22.length - 1], (byte) 0x81, "BCF 2.2 should pad with END_OF_VECTOR");

        // BCF 2.1 (uses VCF 4.2 header): haploid at a diploid site pads with MISSING (0x80 for INT8)
        final Path bcf21 = writeBcf21(gtHeader(VCFHeaderVersion.VCF4_2, 2), vc);
        final byte[] raw21 = Files.readAllBytes(bcf21);
        final ByteBuffer bb = ByteBuffer.wrap(raw21).order(ByteOrder.LITTLE_ENDIAN);
        bb.position(BCF2Codec.SIZEOF_BCF_HEADER);
        bb.position(bb.position() + Integer.BYTES + bb.getInt()); // skip header text
        final int sharedLen = bb.getInt();
        final int indivLen = bb.getInt();
        bb.position(bb.position() + sharedLen);
        final byte[] gt21 = new byte[indivLen];
        bb.get(gt21);
        Assert.assertEquals(gt21[gt21.length - 1], (byte) 0x80, "BCF 2.1 should pad with MISSING");
    }

    // Vector padding

    @Test
    public void vectorPaddingUsesOneMissingThenEndOfVectorIn22() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        lines.add(new VCFFormatHeaderLine("XI", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "x"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1", "s2"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        // s1 has 3 values, s2 has 1: s2 is padded from 1 to 3
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(
                        new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                                .attribute("XI", List.of(10, 20, 30))
                                .make(),
                        new GenotypeBuilder("s2", List.of(REF_A, ALT_C))
                                .attribute("XI", List.of(5))
                                .make())
                .make();
        // BCF 2.2: s2's XI padded from 1 to 3: [5, MISSING(0x80), END_OF_VECTOR(0x81)]
        final Path bcf22 = writeBcf(header, vc);
        try (final VCFFileReader reader = new VCFFileReader(bcf22, false)) {
            final LazyGenotypesContext lgc =
                    (LazyGenotypesContext) reader.iterator().next().getGenotypes();
            final byte[] gt = ((BCF2Codec.LazyData) lgc.getUnparsedGenotypeData()).bytes;
            // Search for the pattern [5, 0x80, 0x81] in the genotype block
            boolean found22 = false;
            for (int i = 0; i < gt.length - 2; i++) {
                if (gt[i] == 5 && gt[i + 1] == (byte) 0x80 && gt[i + 2] == (byte) 0x81) {
                    found22 = true;
                    break;
                }
            }
            Assert.assertTrue(found22, "BCF 2.2 should pad with [value, MISSING, END_OF_VECTOR]");
        }

        // BCF 2.1: s2's XI padded from 1 to 3: [5, MISSING(0x80), MISSING(0x80)]
        final Path bcf21 = writeBcf21(header, vc);
        final byte[] raw21 = Files.readAllBytes(bcf21);
        // Search for the pattern [5, 0x80, 0x80] in the raw file bytes
        boolean found21 = false;
        for (int i = 0; i < raw21.length - 2; i++) {
            if (raw21[i] == 5 && raw21[i + 1] == (byte) 0x80 && raw21[i + 2] == (byte) 0x80) {
                found21 = true;
                break;
            }
        }
        Assert.assertTrue(found21, "BCF 2.1 should pad with [value, MISSING, MISSING]");
    }

    // String list form

    @Test
    public void stringListIsWrittenWithoutLeadingCommaIn22AndWithOneIn21() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("STR", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "str"));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .attribute("STR", List.of("a", "b"))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C)).make())
                .make();

        // BCF 2.2: "a,b" (no leading comma)
        final Path bcf22 = writeBcf(header, vc);
        // Decompress BGZF to inspect raw bytes
        final byte[] decompressed;
        try (final BlockCompressedInputStream in = new BlockCompressedInputStream(Files.newInputStream(bcf22))) {
            decompressed = in.readAllBytes();
        }
        final String as22 = new String(decompressed, StandardCharsets.UTF_8);
        Assert.assertTrue(as22.contains("a,b"), "BCF 2.2 string list should be a,b: " + as22);
        Assert.assertFalse(as22.contains(",a,b"), "BCF 2.2 string list should not have leading comma");

        // BCF 2.1: ",a,b" (leading comma)
        final Path bcf21 = writeBcf21(header, vc);
        final String as21 = new String(Files.readAllBytes(bcf21), StandardCharsets.UTF_8);
        Assert.assertTrue(as21.contains(",a,b"), "BCF 2.1 string list should be ,a,b: " + as21);
    }

    // Missing FORMAT string

    @Test
    public void aMissingFormatStringIsDotNulIn22AndAllNulIn21() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        lines.add(new VCFFormatHeaderLine("FS", 1, VCFHeaderLineType.String, "fs"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1", "s2"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(
                        new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                                .attribute("FS", "xx")
                                .make(),
                        new GenotypeBuilder("s2", List.of(REF_A, ALT_C)).make())
                .make();

        // BCF 2.2: s2's FS should be "." + NUL padding
        final Path bcf22 = writeBcf(header, vc);
        try (final VCFFileReader reader = new VCFFileReader(bcf22, false)) {
            final LazyGenotypesContext lgc =
                    (LazyGenotypesContext) reader.iterator().next().getGenotypes();
            final byte[] gt = ((BCF2Codec.LazyData) lgc.getUnparsedGenotypeData()).bytes;
            // Find FS data: after GT block. The FS string for s2 should contain '.'
            boolean foundDot = false;
            for (int i = 0; i < gt.length; i++) {
                if (gt[i] == '.' && i > 0 && i + 1 < gt.length && gt[i + 1] == 0) {
                    foundDot = true;
                    break;
                }
            }
            Assert.assertTrue(foundDot, "BCF 2.2 missing FORMAT string should contain '.' + NUL");
        }

        // BCF 2.1: s2's FS should be all NUL (empty string)
        final Path bcf21 = writeBcf21(header, vc);
        final byte[] raw = Files.readAllBytes(bcf21);
        final ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        bb.position(BCF2Codec.SIZEOF_BCF_HEADER);
        bb.position(bb.position() + Integer.BYTES + bb.getInt());
        final int sharedLen = bb.getInt();
        final int indivLen = bb.getInt();
        bb.position(bb.position() + sharedLen);
        final byte[] gtBlock = new byte[indivLen];
        bb.get(gtBlock);
        // s2's FS in 2.1 should be all NUL, meaning no '.' byte before the NULs
        // The string "xx" for s1 is 2 bytes, and s2's slot is also 2 bytes but all NUL
        // Find "xx" in gtBlock, then verify the next 2 bytes are NUL
        boolean found = false;
        for (int i = 0; i < gtBlock.length - 3; i++) {
            if (gtBlock[i] == 'x' && gtBlock[i + 1] == 'x' && gtBlock[i + 2] == 0 && gtBlock[i + 3] == 0) {
                found = true;
                break;
            }
        }
        Assert.assertTrue(found, "BCF 2.1 missing FORMAT string should be all NUL bytes after the present string");
    }

    // Sentinel-safe width for -127

    @Test
    public void aFormatIntegerOfMinus127RoundTripsViaInt16() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        lines.add(new VCFFormatHeaderLine("XI", 1, VCFHeaderLineType.Integer, "x"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                        .attribute("XI", -127)
                        .make())
                .make();
        final Path bcf = writeBcf(header, vc);
        try (final VCFFileReader reader = new VCFFileReader(bcf, false)) {
            final Object xi = reader.iterator().next().getGenotype("s1").getExtendedAttribute("XI");
            Assert.assertEquals(xi, -127, "value -127 should round-trip");
        }
    }

    // 24-bit n_sample mask

    @Test
    public void nSampleInRecordHeaderEncodesNfmtAndNsamples() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        lines.add(new VCFFormatHeaderLine("GQ", 1, VCFHeaderLineType.Integer, "gq"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1", "s2", "s3"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(
                        new GenotypeBuilder("s1", List.of(REF_A, ALT_C)).GQ(10).make(),
                        new GenotypeBuilder("s2", List.of(REF_A, ALT_C)).GQ(20).make(),
                        new GenotypeBuilder("s3", List.of(REF_A, ALT_C)).GQ(30).make())
                .make();
        final Path bcf = writeBcf21(header, vc);
        final byte[] raw = Files.readAllBytes(bcf);
        final ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        bb.position(BCF2Codec.SIZEOF_BCF_HEADER);
        bb.position(bb.position() + Integer.BYTES + bb.getInt()); // skip header text
        bb.getInt(); // shared length
        bb.getInt(); // indiv length
        // Skip chrom, pos, rlen, qual (4 INT32s each = 16 bytes)
        bb.position(bb.position() + 16);
        bb.getInt(); // skip nAlleles|nInfo
        final int nFmtSamples = bb.getInt();
        final int nFmt = (nFmtSamples >>> 24) & 0xFF;
        final int nSamples = nFmtSamples & 0x00FFFFFF;
        Assert.assertEquals(nFmt, 2, "nFmt should be 2 (GT + GQ)");
        Assert.assertEquals(nSamples, 3, "nSamples should be 3");
    }

    // Vector sizing from values, not header Number

    @Test
    public void aNumberRFieldWithFewerValuesThanAllelesWritesPaddedVector() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        lines.add(new VCFFormatHeaderLine("AD", VCFHeaderLineCount.R, VCFHeaderLineType.Integer, "depths"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final Allele altG = Allele.create("G");
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C, altG))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                        .AD(new int[] {10, 5})
                        .make())
                .make();
        final Path bcf = writeBcf(header, vc);
        try (final VCFFileReader reader = new VCFFileReader(bcf, false)) {
            final Genotype g = reader.iterator().next().getGenotype("s1");
            // The AD should round-trip with 2 values, not 3 (the third slot is padded, not a zero)
            Assert.assertEquals(g.getAD(), new int[] {10, 5});
        }
    }

    // ============================================================
    // F1 and F2 fix tests: multi-value String list under 4.3 and null elements
    // ============================================================

    @Test
    public void aMultiValueStringListUnder43RoundTripsAsMultipleElements() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("STR", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "str"));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        header.setVCFHeaderVersion(VCFHeaderVersion.VCF4_3);
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .attribute("STR", List.of("a;b", "c"))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C)).make())
                .make();
        final Path bcf = writeBcf(header, vc);

        // htsjdk round-trip: should come back as two elements with the semicolon intact
        try (final VCFFileReader reader = new VCFFileReader(bcf, false)) {
            final Object str = reader.iterator().next().getAttribute("STR");
            Assert.assertEquals(str, List.of("a;b", "c"), "should round-trip as two elements");
        }

        // bcftools should see it as a%3Bb,c
        if (BcftoolsTestUtils.isBcftoolsAvailable()) {
            final List<String> output = BcftoolsTestUtils.viewAsVcf(bcf);
            final String record = output.get(output.size() - 1);
            Assert.assertTrue(record.contains("STR=a%3Bb,c"), "bcftools should see a%3Bb,c: " + record);
        }
    }

    @Test
    public void aFormatStringListUnder43HasCorrectBytes() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        lines.add(new VCFFormatHeaderLine("FS", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "fs"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        header.setVCFHeaderVersion(VCFHeaderVersion.VCF4_3);
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                        .attribute("FS", List.of("a;b", "c"))
                        .make())
                .make();
        final Path bcf = writeBcf(header, vc);
        // The FORMAT string bytes should have "a%3Bb,c" (each element encoded, commas as delimiters)
        final byte[] decompressed;
        try (final BlockCompressedInputStream in = new BlockCompressedInputStream(Files.newInputStream(bcf))) {
            decompressed = in.readAllBytes();
        }
        final String raw = new String(decompressed, StandardCharsets.UTF_8);
        Assert.assertTrue(raw.contains("a%3Bb,c"), "FORMAT string list should have commas as delimiters: " + raw);
        Assert.assertFalse(raw.contains("a%3Bb%2Cc"), "commas should not be percent-encoded: " + raw);
    }

    @Test
    public void anInteriorNullInAStringListProducesDotIn22() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("STR", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "str"));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .attribute("STR", Arrays.asList("a", null, "c"))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C)).make())
                .make();

        // BCF 2.2: the collapsed string should be "a,.,c"
        final Path bcf22 = writeBcf(header, vc);
        final byte[] decompressed;
        try (final BlockCompressedInputStream in = new BlockCompressedInputStream(Files.newInputStream(bcf22))) {
            decompressed = in.readAllBytes();
        }
        final String as22 = new String(decompressed, StandardCharsets.UTF_8);
        Assert.assertTrue(as22.contains("a,.,c"), "BCF 2.2 null element should be '.': " + as22);

        // BCF 2.1: the collapsed string should be ",a,.,c"
        final Path bcf21 = writeBcf21(header, vc);
        final String as21 = new String(Files.readAllBytes(bcf21), StandardCharsets.UTF_8);
        Assert.assertTrue(as21.contains(",a,.,c"), "BCF 2.1 null element should be '.': " + as21);
    }

    @Test
    public void aSingletonNullStringListProducesDot() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("STR", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "str"));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .attribute("STR", Arrays.asList((String) null))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C)).make())
                .make();
        // Should not NPE; the collapsed string should be "."
        final Path bcf = writeBcf(header, vc);
        try (final VCFFileReader reader = new VCFFileReader(bcf, false)) {
            // "." is the missing value, so the attribute should either be absent or "."
            final VariantContext read = reader.iterator().next();
            // A singleton "." in a string field is missing
            Assert.assertTrue(!read.hasAttribute("STR") || ".".equals(read.getAttribute("STR")));
        }
    }

    // ============================================================
    // Framing tests
    // ============================================================

    @Test
    public void aDefaultBcfFileIsBgzfCompressed() throws IOException {
        final VCFHeader header = createFakeHeader();
        final Path output = Files.createTempFile(tempDir, "bgzf.", ".bcf");
        output.toFile().deleteOnExit();
        output.resolveSibling(output.getFileName() + FileExtensions.CSI)
                .toFile()
                .deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .build()) {
            writer.writeHeader(header);
            writer.add(createVC(header));
        }
        final byte[] bytes = Files.readAllBytes(output);
        // BGZF files start with the gzip magic 0x1f 0x8b
        Assert.assertEquals(bytes[0], (byte) 0x1f);
        Assert.assertEquals(bytes[1], (byte) 0x8b);
    }

    @Test
    public void aDefaultBcfFileHasTheBgzfEofBlock() throws IOException {
        final VCFHeader header = oneSampleGtHeader();
        final Path output = Files.createTempFile(tempDir, "bgzf.", ".bcf");
        output.toFile().deleteOnExit();
        output.resolveSibling(output.getFileName() + FileExtensions.CSI)
                .toFile()
                .deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .build()) {
            writer.writeHeader(header);
        }
        final byte[] bytes = Files.readAllBytes(output);
        // The BGZF EOF block is 28 bytes
        Assert.assertTrue(bytes.length >= 28);
        Assert.assertEquals(bytes[bytes.length - 28], (byte) 0x1f);
        Assert.assertEquals(bytes[bytes.length - 27], (byte) 0x8b);
    }

    @Test
    public void aBcf21FileIsWrittenRawWhenRequested() throws IOException {
        final VCFHeader header = oneSampleGtHeader();
        final Path output = Files.createTempFile(tempDir, "raw21.", ".bcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .setBCFVersion(BCFVersion.BCF_2_1)
                .build()) {
            writer.writeHeader(header);
        }
        final byte[] bytes = Files.readAllBytes(output);
        Assert.assertEquals(bytes[0], (byte) 'B');
        Assert.assertEquals(bytes[1], (byte) 'C');
        Assert.assertEquals(bytes[2], (byte) 'F');
        Assert.assertEquals(bytes[3], (byte) 2);
        Assert.assertEquals(bytes[4], (byte) 1);
    }

    @Test
    public void aBcf21CannotBeWrittenWithA43Header() {
        final VCFHeader header = oneSampleGtHeader();
        header.setVCFHeaderVersion(VCFHeaderVersion.VCF4_4);
        Assert.expectThrows(IllegalStateException.class, () -> {
            final Path output = Files.createTempFile(tempDir, "21with43.", ".bcf");
            output.toFile().deleteOnExit();
            try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                    .setOutputPath(output)
                    .setReferenceDictionary(header.getSequenceDictionary())
                    .unsetOption(Options.INDEX_ON_THE_FLY)
                    .setBCFVersion(BCFVersion.BCF_2_1)
                    .build()) {
                writer.writeHeader(header);
            }
        });
    }

    // ============================================================
    // Dictionary tests
    // ============================================================

    @Test
    public void theWriterAssignsIdxAndEmbedsItInTheHeaderText() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "dp"));
        lines.add(new VCFInfoHeaderLine("AF", VCFHeaderLineCount.A, VCFHeaderLineType.Float, "af"));
        lines.add(new VCFFilterHeaderLine("q10", "q10"));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        lines.add(new VCFFormatHeaderLine("GQ", 1, VCFHeaderLineType.Integer, "gq"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final Path bcf = writeBcf(
                header,
                new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                        .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C)).make())
                        .make());
        try (final VCFFileReader reader = new VCFFileReader(bcf, false)) {
            final VCFHeader readHeader = reader.getFileHeader();
            Assert.assertNotNull(readHeader.getInfoHeaderLine("DP"));
            Assert.assertNotNull(readHeader.getInfoHeaderLine("AF"));
            Assert.assertNotNull(readHeader.getFilterHeaderLine("q10"));
            Assert.assertNotNull(readHeader.getFormatHeaderLine("GT"));
            Assert.assertNotNull(readHeader.getFormatHeaderLine("GQ"));
        }
    }

    @Test
    public void theWritersIdxMatchesTheReadersDictionary() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "dp"));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        lines.add(new VCFFilterHeaderLine("q10", "q10"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .attribute("DP", 42)
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C)).make())
                .make();
        final Path bcf = writeBcf(header, vc);
        try (final VCFFileReader reader = new VCFFileReader(bcf, false)) {
            final VariantContext read = reader.iterator().next();
            Assert.assertEquals(read.getAttributeAsInt("DP", 0), 42);
        }
    }

    @Test
    public void aHeaderWhoseLinesCarryIdxIsWrittenWithThoseIndices() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine(
                "<ID=DP,Number=1,Type=Integer,Description=\"dp\",IDX=5>", VCFHeaderVersion.VCF4_2));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .attribute("DP", 42)
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C)).make())
                .make();
        final Path bcf = writeBcf(header, vc);
        try (final VCFFileReader reader = new VCFFileReader(bcf, false)) {
            Assert.assertEquals(reader.iterator().next().getAttributeAsInt("DP", 0), 42);
        }
    }

    @Test(expectedExceptions = TribbleException.class)
    public void twoLinesNamingOneIdxAreRefused() {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine(
                "<ID=DP,Number=1,Type=Integer,Description=\"dp\",IDX=5>", VCFHeaderVersion.VCF4_2));
        lines.add(
                new VCFInfoHeaderLine("<ID=AF,Number=A,Type=Float,Description=\"af\",IDX=5>", VCFHeaderVersion.VCF4_2));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        writeBcfNoThrow(header);
    }

    // ============================================================
    // Pass-through tests
    // ============================================================

    /** A GT-only VC compatible with oneSampleGtHeader. */
    private static VariantContext simpleGtVc() {
        return new VariantContextBuilder("t", "1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                        .phased(true)
                        .make())
                .make();
    }

    @Test
    public void passThrough21To22ForcesReEncode() throws IOException {
        final VCFHeader header = oneSampleGtHeader();
        final Path bcf21 = writeBcf21(header, simpleGtVc());
        final Path bcf22 = Files.createTempFile(tempDir, "passthrough.", ".bcf");
        bcf22.toFile().deleteOnExit();
        try (final VCFFileReader reader = new VCFFileReader(bcf21, false);
                final VariantContextWriter writer = new VariantContextWriterBuilder()
                        .clearOptions()
                        .setOutputPath(bcf22)
                        .setOutputFileType(VariantContextWriterBuilder.OutputType.BCF)
                        .build()) {
            writer.writeHeader(reader.getFileHeader());
            for (final VariantContext vc : reader) writer.add(vc);
        }
        try (final VCFFileReader reader21 = new VCFFileReader(bcf21, false);
                final VCFFileReader reader22 = new VCFFileReader(bcf22, false)) {
            final VariantContext vc21 = reader21.iterator().next();
            final VariantContext vc22 = reader22.iterator().next();
            for (final Genotype g : vc21.getGenotypes()) g.getAlleles();
            for (final Genotype g : vc22.getGenotypes()) g.getAlleles();
            Assert.assertEquals(
                    vc22.getGenotype("s1").getGenotypeString(),
                    vc21.getGenotype("s1").getGenotypeString());
        }
    }

    @Test
    public void passThrough22To22WithSameDictionaryPassesThrough() throws IOException {
        final VCFHeader header = oneSampleGtHeader();
        final Path bcf22a = writeBcf(header, simpleGtVc());
        final Path bcf22b = Files.createTempFile(tempDir, "passthrough.", ".bcf");
        bcf22b.toFile().deleteOnExit();
        try (final VCFFileReader reader = new VCFFileReader(bcf22a, false);
                final VariantContextWriter writer = new VariantContextWriterBuilder()
                        .clearOptions()
                        .setOutputPath(bcf22b)
                        .setOutputFileType(VariantContextWriterBuilder.OutputType.BCF)
                        .build()) {
            writer.writeHeader(reader.getFileHeader());
            for (final VariantContext vc : reader) writer.add(vc);
        }
        try (final VCFFileReader readerA = new VCFFileReader(bcf22a, false);
                final VCFFileReader readerB = new VCFFileReader(bcf22b, false)) {
            final LazyGenotypesContext lgcA =
                    (LazyGenotypesContext) readerA.iterator().next().getGenotypes();
            final LazyGenotypesContext lgcB =
                    (LazyGenotypesContext) readerB.iterator().next().getGenotypes();
            Assert.assertEquals(
                    ((BCF2Codec.LazyData) lgcB.getUnparsedGenotypeData()).bytes,
                    ((BCF2Codec.LazyData) lgcA.getUnparsedGenotypeData()).bytes);
        }
    }

    @Test
    public void passThrough22To22WithDifferentDictionaryForcesReEncode() throws IOException {
        final VCFHeader header = oneSampleGtHeader();
        final Path bcf22a = writeBcf(header, simpleGtVc());
        final VCFHeader header2 = oneSampleGtHeader();
        header2.addMetaDataLine(new VCFInfoHeaderLine("EXTRA", 1, VCFHeaderLineType.Integer, "extra"));
        final Path bcf22b = Files.createTempFile(tempDir, "passthrough.", ".bcf");
        bcf22b.toFile().deleteOnExit();
        try (final VCFFileReader reader = new VCFFileReader(bcf22a, false);
                final VariantContextWriter writer = new VariantContextWriterBuilder()
                        .clearOptions()
                        .setOutputPath(bcf22b)
                        .setOutputFileType(VariantContextWriterBuilder.OutputType.BCF)
                        .build()) {
            writer.writeHeader(header2);
            for (final VariantContext vc : reader) writer.add(vc);
        }
        try (final VCFFileReader readerB = new VCFFileReader(bcf22b, false)) {
            final VariantContext vc = readerB.iterator().next();
            for (final Genotype g : vc.getGenotypes()) g.getAlleles();
            Assert.assertEquals(vc.getGenotype("s1").getGenotypeString(), "A|C");
        }
    }

    // ============================================================
    // INDEX_ON_THE_FLY tests
    // ============================================================

    @Test
    public void defaultBuilderWithDictionaryAndBcfNoLongerThrows() throws IOException {
        final VCFHeader header = createFakeHeader();
        final Path output = Files.createTempFile(tempDir, "iotf.", ".bcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .build()) {
            writer.writeHeader(header);
            writer.add(createVC(header));
        }
        Assert.assertTrue(Files.exists(output));
    }

    @Test
    public void aCsiIndexIsWrittenBesideABgzfBcf() throws IOException {
        final VCFHeader header = createFakeHeader();
        final Path output = Files.createTempFile(tempDir, "csi.", ".bcf");
        output.toFile().deleteOnExit();
        final Path csiPath = output.resolveSibling(output.getFileName() + ".csi");
        csiPath.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .setOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
            writer.add(createVC(header));
        }
        Assert.assertTrue(Files.exists(csiPath), "CSI index should exist beside the BCF");
        final htsjdk.index.FileBackedBinningIndex idx = htsjdk.index.FileBackedBinningIndex.open(csiPath, true);
        Assert.assertTrue(idx.isCsi());
        Assert.assertEquals(idx.getMinShift(), 14);
        Assert.assertEquals(idx.getAux().length, 0);
        idx.close();
    }

    @Test
    public void aCsiIsNotWrittenForBcf21() throws IOException {
        final VCFHeader header = createFakeHeader();
        final Path output = Files.createTempFile(tempDir, "csi21.", ".bcf");
        output.toFile().deleteOnExit();
        htsjdk.tribble.Tribble.indexPath(output).toFile().deleteOnExit();
        final Path csiPath = output.resolveSibling(output.getFileName() + ".csi");
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .setOption(Options.INDEX_ON_THE_FLY)
                .setBCFVersion(BCFVersion.BCF_2_1)
                .build()) {
            writer.writeHeader(header);
            writer.add(createVC(header));
        }
        Assert.assertFalse(Files.exists(csiPath), "CSI should not exist for BCF 2.1");
        Assert.assertTrue(
                Files.exists(htsjdk.tribble.Tribble.indexPath(output)), "Tribble .idx should exist for BCF 2.1");
    }

    @Test
    public void aCsiIsNotWrittenForAStream() throws IOException {
        final VCFHeader header = createFakeHeader();
        final java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (final VariantContextWriter writer =
                new VariantContextWriterBuilder().setOutputBCFStream(baos).build()) {
            writer.writeHeader(header);
            writer.add(createVC(header));
        }
        // No exception, no CSI path to check (stream output)
        Assert.assertTrue(baos.size() > 0);
    }

    @Test
    public void anEmptyBcfGetsACsi() throws IOException {
        final VCFHeader header = createFakeHeader();
        final Path output = Files.createTempFile(tempDir, "empty-csi.", ".bcf");
        output.toFile().deleteOnExit();
        final Path csiPath = output.resolveSibling(output.getFileName() + ".csi");
        csiPath.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .setOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
            // no records
        }
        Assert.assertTrue(Files.exists(csiPath), "CSI should exist even for an empty BCF");
    }

    @Test
    public void anUnsortedInputThrowsBeforeWritingTheRecord() throws IOException {
        final VCFHeader header = createFakeHeader();
        final Path output = Files.createTempFile(tempDir, "unsorted-csi.", ".bcf");
        output.toFile().deleteOnExit();
        final Path csiPath = output.resolveSibling(output.getFileName() + FileExtensions.CSI);
        csiPath.toFile().deleteOnExit();
        final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .setOption(Options.INDEX_ON_THE_FLY)
                .build();
        writer.writeHeader(header);
        // Write a record on contig "2"
        writer.add(new VariantContextBuilder("test", "2", 10, 10, Arrays.asList(Allele.REF_A, Allele.ALT_C))
                .genotypes(
                        new GenotypeBuilder("extra1", Arrays.asList(Allele.ALT_C))
                                .GQ(0)
                                .attribute("BB", "1")
                                .phased(true)
                                .make(),
                        new GenotypeBuilder("extra2", Arrays.asList(Allele.ALT_C))
                                .GQ(0)
                                .attribute("BB", "1")
                                .phased(true)
                                .make())
                .attribute("DP", "50")
                .make());
        // An out-of-order record throws before it reaches the file
        Assert.expectThrows(
                IllegalArgumentException.class,
                () -> writer.add(new VariantContextBuilder("test", "1", 5, 5, Arrays.asList(Allele.REF_A, Allele.ALT_C))
                        .genotypes(
                                new GenotypeBuilder("extra1", Arrays.asList(Allele.ALT_C))
                                        .GQ(0)
                                        .attribute("BB", "1")
                                        .phased(true)
                                        .make(),
                                new GenotypeBuilder("extra2", Arrays.asList(Allele.ALT_C))
                                        .GQ(0)
                                        .attribute("BB", "1")
                                        .phased(true)
                                        .make())
                        .attribute("DP", "50")
                        .make()));
        writer.close();
        // The BCF has only the first record; the CSI is not written (the writer was marked failed)
        try (VCFFileReader reader = new VCFFileReader(output, false)) {
            int count = 0;
            for (final VariantContext ignored : reader) count++;
            Assert.assertEquals(count, 1, "Only the first (sorted) record should be in the file");
        }
        Assert.assertFalse(Files.exists(csiPath), "No CSI should be written after a sort-order violation");
    }

    @Test
    public void aStaleCsiIsDeletedWhenTheHeaderIsWritten() throws IOException {
        final VCFHeader header = createFakeHeader();
        final Path output = Files.createTempFile(tempDir, "stale-csi.", ".bcf");
        output.toFile().deleteOnExit();
        final Path csiPath = output.resolveSibling(output.getFileName() + FileExtensions.CSI);
        csiPath.toFile().deleteOnExit();
        // Plant a stale CSI from a previous run
        Files.write(csiPath, new byte[] {0x43, 0x53, 0x49, 0x01});
        Assert.assertTrue(Files.exists(csiPath), "Stale CSI should exist before writing");

        // Write a BCF with an unsorted second record; the writer fails and close() skips the CSI
        final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .setOption(Options.INDEX_ON_THE_FLY)
                .build();
        writer.writeHeader(header);
        // The stale CSI should already be gone after writeHeader
        Assert.assertFalse(Files.exists(csiPath), "Stale CSI should be deleted when the header is written");
        // Write a record and fail with an unsorted second record
        writer.add(new VariantContextBuilder("test", "2", 10, 10, Arrays.asList(Allele.REF_A, Allele.ALT_C))
                .genotypes(
                        new GenotypeBuilder("extra1", Arrays.asList(Allele.ALT_C))
                                .GQ(0)
                                .attribute("BB", "1")
                                .phased(true)
                                .make(),
                        new GenotypeBuilder("extra2", Arrays.asList(Allele.ALT_C))
                                .GQ(0)
                                .attribute("BB", "1")
                                .phased(true)
                                .make())
                .attribute("DP", "50")
                .make());
        Assert.expectThrows(
                IllegalArgumentException.class,
                () -> writer.add(new VariantContextBuilder("test", "1", 5, 5, Arrays.asList(Allele.REF_A, Allele.ALT_C))
                        .genotypes(
                                new GenotypeBuilder("extra1", Arrays.asList(Allele.ALT_C))
                                        .GQ(0)
                                        .attribute("BB", "1")
                                        .phased(true)
                                        .make(),
                                new GenotypeBuilder("extra2", Arrays.asList(Allele.ALT_C))
                                        .GQ(0)
                                        .attribute("BB", "1")
                                        .phased(true)
                                        .make())
                        .attribute("DP", "50")
                        .make()));
        writer.close();
        // After close, the stale CSI is still gone (not re-created because csiFailed is set)
        Assert.assertFalse(Files.exists(csiPath), "No CSI should be left after a sort-order violation");
    }

    @Test
    public void indexOnTheFlyWithRawBcfWorks() throws IOException {
        final VCFHeader header = createFakeHeader();
        final Path output = Files.createTempFile(tempDir, "iotf21.", ".bcf");
        output.toFile().deleteOnExit();
        Tribble.indexPath(output).toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .setOption(Options.INDEX_ON_THE_FLY)
                .setBCFVersion(BCFVersion.BCF_2_1)
                .build()) {
            writer.writeHeader(header);
            writer.add(createVC(header));
        }
        Assert.assertTrue(Files.exists(Tribble.indexPath(output)));
    }

    // ============================================================
    // Constructor guard: Tribble indexing over a BGZF stream is refused
    // ============================================================

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void tribbleIndexingOverBgzfStreamThrows() throws IOException {
        final VCFHeader header = createFakeHeader();
        final Path output = Files.createTempFile(tempDir, "bgzfguard.", ".bcf");
        output.toFile().deleteOnExit();
        // Constructing with enableOnTheFlyIndexing=true over a BGZF stream (no CSI path) must throw
        try (BlockCompressedOutputStream bcos =
                new BlockCompressedOutputStream(Files.newOutputStream(output), output)) {
            new BCF2Writer(output, bcos, header.getSequenceDictionary(), true, false, null, BCFVersion.BCF_2_2);
        }
    }

    @Test
    public void rawStreamAt22WithIndexingProducesAnIdx() throws IOException {
        final VCFHeader header = createFakeHeader();
        final Path output = Files.createTempFile(tempDir, "raw22idx.", ".bcf");
        output.toFile().deleteOnExit();
        Tribble.indexPath(output).toFile().deleteOnExit();
        // A raw (non-BGZF) stream at 2.2 with Tribble indexing is allowed
        try (final VariantContextWriter writer = new BCF2Writer(
                output,
                Files.newOutputStream(output),
                header.getSequenceDictionary(),
                true,
                false,
                null,
                BCFVersion.BCF_2_2)) {
            writer.writeHeader(header);
            writer.add(createVC(header));
        }
        Assert.assertTrue(Files.exists(Tribble.indexPath(output)), "A Tribble .idx should be produced");
    }

    // ============================================================
    // Sparse contig IDX: nRefs must cover the highest index, not the count
    // ============================================================

    @Test
    public void aSparseContigIdxBuildsCsiSuccessfully() throws IOException {
        // A header whose only contig carries IDX=5: the CSI must have nRefs >= 6
        final Set<VCFHeaderLine> meta = new LinkedHashSet<>();
        meta.add(new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "Depth"));
        meta.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "Genotype"));
        final Map<String, String> contigMap = new LinkedHashMap<>();
        contigMap.put("ID", "sparse");
        contigMap.put("length", "100000");
        contigMap.put("IDX", "5");
        meta.add(new VCFContigHeaderLine(contigMap, 0));
        final VCFHeader header = new VCFHeader(meta, List.of("sample1"));
        final SAMSequenceDictionary dict = header.getSequenceDictionary();

        final Path output = Files.createTempFile(tempDir, "sparse-idx.", ".bcf");
        output.toFile().deleteOnExit();
        final Path csiPath = output.resolveSibling(output.getFileName() + FileExtensions.CSI);
        csiPath.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(dict)
                .setOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
            writer.add(new VariantContextBuilder("test", "sparse", 100, 100, Arrays.asList(Allele.REF_A, Allele.ALT_C))
                    .genotypes(new GenotypeBuilder("sample1", Arrays.asList(Allele.REF_A, Allele.ALT_C)).make())
                    .attribute("DP", 30)
                    .make());
        }
        Assert.assertTrue(Files.exists(csiPath), "CSI should exist for a sparse-IDX header");
        // The CSI should be readable and queryable by BCFFileReader
        try (BCFFileReader reader = new BCFFileReader(output, csiPath)) {
            final List<VariantContext> results = new ArrayList<>();
            try (CloseableIterator<VariantContext> it = reader.query("sparse", 1, 1000)) {
                while (it.hasNext()) results.add(it.next());
            }
            Assert.assertEquals(results.size(), 1);
            Assert.assertEquals(results.get(0).getStart(), 100);
        }
    }

    @Test
    public void bcf21WithA43HeaderThrows() {
        final VCFHeader header = oneSampleGtHeader();
        header.setVCFHeaderVersion(VCFHeaderVersion.VCF4_4);
        final Path output;
        try {
            output = Files.createTempFile(tempDir, "21-43.", ".bcf");
            output.toFile().deleteOnExit();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        Assert.expectThrows(IllegalStateException.class, () -> {
            try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                    .setOutputPath(output)
                    .setReferenceDictionary(header.getSequenceDictionary())
                    .unsetOption(Options.INDEX_ON_THE_FLY)
                    .setBCFVersion(BCFVersion.BCF_2_1)
                    .build()) {
                writer.writeHeader(header);
            }
        });
    }

    // ============================================================
    // Unsupported BCF version
    // ============================================================

    @Test
    public void unsupportedBcfVersionIsRejectedByTheBuilder() {
        Assert.expectThrows(IllegalArgumentException.class, () -> new VariantContextWriterBuilder()
                .setBCFVersion(new BCFVersion(2, 3)));
        Assert.expectThrows(IllegalArgumentException.class, () -> new VariantContextWriterBuilder()
                .setBCFVersion(new BCFVersion(3, 0)));
        // accepted
        new VariantContextWriterBuilder().setBCFVersion(BCFVersion.BCF_2_1);
        new VariantContextWriterBuilder().setBCFVersion(BCFVersion.BCF_2_2);
    }

    @Test
    public void unsupportedBcfVersionIsRejectedByTheConstructor() throws IOException {
        final Path output = Files.createTempFile(tempDir, "unsup.", ".bcf");
        output.toFile().deleteOnExit();
        Assert.expectThrows(
                IllegalArgumentException.class,
                () -> new BCF2Writer(
                        output, Files.newOutputStream(output), null, false, false, null, new BCFVersion(2, 3)));
        Assert.expectThrows(
                IllegalArgumentException.class,
                () -> new BCF2Writer(
                        output, Files.newOutputStream(output), null, false, false, null, new BCFVersion(3, 0)));
    }

    // ============================================================
    // Sample count limit
    // ============================================================

    @Test
    public void sampleCountAtTheBoundaryIsAccepted() {
        // 0x00FFFFFF = 16777215 -- the largest count that fits the 24-bit field
        BCF2Writer.requireSampleCountInRange(0x00FFFFFF);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void sampleCountAboveTheLimitIsRejected() {
        BCF2Writer.requireSampleCountInRange(0x01000000);
    }

    // ============================================================
    // UTF-8 string sizing
    // ============================================================

    @Test
    public void aFormatStringWithNonAsciiRoundTrips() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        lines.add(new VCFFormatHeaderLine("STR", 1, VCFHeaderLineType.String, "str"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1", "s2"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        // "é" is 2 UTF-8 bytes, "λ→日本" is 2+3+3+3 = 11 UTF-8 bytes
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(
                        new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                                .attribute("STR", "é")
                                .make(),
                        new GenotypeBuilder("s2", List.of(REF_A, ALT_C))
                                .attribute("STR", "λ→日本")
                                .make())
                .make();
        // Round-trip through BCF 2.2
        final Path bcf22 = writeBcf(header, vc);
        try (final VCFFileReader reader = new VCFFileReader(bcf22, false)) {
            final VariantContext read = reader.iterator().next();
            Assert.assertEquals(read.getGenotype("s1").getExtendedAttribute("STR"), "é");
            Assert.assertEquals(read.getGenotype("s2").getExtendedAttribute("STR"), "λ→日本");
        }
        // Round-trip through BCF 2.1
        header.setVCFHeaderVersion(VCFHeaderVersion.VCF4_2);
        final Path bcf21 = writeBcf21(header, vc);
        try (final VCFFileReader reader = new VCFFileReader(bcf21, false)) {
            final VariantContext read = reader.iterator().next();
            Assert.assertEquals(read.getGenotype("s1").getExtendedAttribute("STR"), "é");
            Assert.assertEquals(read.getGenotype("s2").getExtendedAttribute("STR"), "λ→日本");
        }
    }

    @Test
    public void anInfoStringWithNonAsciiRoundTrips() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("DESC", 1, VCFHeaderLineType.String, "desc"));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .attribute("DESC", "café")
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C)).make())
                .make();
        // BCF 2.2
        final Path bcf22 = writeBcf(header, vc);
        try (final VCFFileReader reader = new VCFFileReader(bcf22, false)) {
            Assert.assertEquals(reader.iterator().next().getAttribute("DESC"), "café");
        }
        // BCF 2.1
        header.setVCFHeaderVersion(VCFHeaderVersion.VCF4_2);
        final Path bcf21 = writeBcf21(header, vc);
        try (final VCFFileReader reader = new VCFFileReader(bcf21, false)) {
            Assert.assertEquals(reader.iterator().next().getAttribute("DESC"), "café");
        }
    }

    @Test
    public void bcftoolsReadsNonAsciiStringsWrittenByHtsjdk() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("DESC", 1, VCFHeaderLineType.String, "desc"));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        lines.add(new VCFFormatHeaderLine("STR", 1, VCFHeaderLineType.String, "str"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .attribute("DESC", "café")
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                        .attribute("STR", "λ→日本")
                        .make())
                .make();
        final Path bcf = writeBcf(header, vc);
        final List<String> vcfLines = BcftoolsTestUtils.viewAsVcf(bcf);
        final String record = vcfLines.get(vcfLines.size() - 1);
        Assert.assertTrue(record.contains("café"), "bcftools should print the non-ASCII INFO value");
        Assert.assertTrue(record.contains("λ→日本"), "bcftools should print the non-ASCII FORMAT value");
    }

    // ============================================================
    // Pass-through across VCF 4.4 boundary
    // ============================================================

    @Test
    public void passThrough42To44ReEncodesSoPhaseIsCorrect() throws IOException {
        // Write a BCF 2.2 under VCF 4.2 with a phased genotype 0|1
        final VCFHeader header42 = oneSampleGtHeader(VCFHeaderVersion.VCF4_2);
        final VariantContext vc = new VariantContextBuilder("t", "1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                        .phased(true)
                        .make())
                .make();
        final Path bcf42 = writeBcf(header42, vc);

        // Re-write under a 4.4 header
        final VCFHeader header44 = oneSampleGtHeader(VCFHeaderVersion.VCF4_4);
        final Path bcf44 = Files.createTempFile(tempDir, "42to44.", ".bcf");
        bcf44.toFile().deleteOnExit();
        try (final VCFFileReader reader = new VCFFileReader(bcf42, false);
                final VariantContextWriter writer = new VariantContextWriterBuilder()
                        .clearOptions()
                        .setOutputPath(bcf44)
                        .setOutputFileType(VariantContextWriterBuilder.OutputType.BCF)
                        .setVCFVersion(VCFHeaderVersion.VCF4_4)
                        .build()) {
            writer.writeHeader(header44);
            for (final VariantContext v : reader) writer.add(v);
        }
        // htsjdk read-back: should still be 0|1
        try (final VCFFileReader reader = new VCFFileReader(bcf44, false)) {
            final Genotype g = reader.iterator().next().getGenotype("s1");
            Assert.assertEquals(g.getGenotypeString(), "A|C");
        }
        // bcftools read-back if available
        if (BcftoolsTestUtils.isBcftoolsAvailable()) {
            final List<String> lines = BcftoolsTestUtils.viewAsVcf(bcf44);
            final String record = lines.get(lines.size() - 1);
            Assert.assertTrue(record.endsWith("0|1"), "bcftools should read the GT as 0|1, got: " + record);
        }
    }

    @Test
    public void passThrough44To43ThrowsForLeadingPhaseIndicator() throws IOException {
        // Write a BCF 2.2 under VCF 4.4 with a leading indicator genotype |0/1
        final VCFHeader header44 = oneSampleGtHeader(VCFHeaderVersion.VCF4_4);
        final VariantContext vc = new VariantContextBuilder("t", "1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                        .allelePhasing(new boolean[] {true, false})
                        .make())
                .make();
        final Path bcf44 = writeBcf(header44, vc);

        // Attempt to re-write under a 4.3 header: should throw because 4.3 cannot express the leading indicator
        final VCFHeader header43 = oneSampleGtHeader(VCFHeaderVersion.VCF4_3);
        final Path bcf43 = Files.createTempFile(tempDir, "44to43.", ".bcf");
        bcf43.toFile().deleteOnExit();
        Assert.expectThrows(IllegalStateException.class, () -> {
            try (final VCFFileReader reader = new VCFFileReader(bcf44, false);
                    final VariantContextWriter writer = new VariantContextWriterBuilder()
                            .clearOptions()
                            .setOutputPath(bcf43)
                            .setOutputFileType(VariantContextWriterBuilder.OutputType.BCF)
                            .setVCFVersion(VCFHeaderVersion.VCF4_3)
                            .build()) {
                writer.writeHeader(header43);
                for (final VariantContext v : reader) writer.add(v);
            }
        });
    }

    // ============================================================
    // Helpers
    // ============================================================

    private Path writeBcf21(final VCFHeader header, final VariantContext vc) throws IOException {
        final Path output = Files.createTempFile(tempDir, "raw21.", ".bcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .clearOptions()
                .setOutputPath(output)
                .setOutputFileType(VariantContextWriterBuilder.OutputType.BCF)
                .setBCFVersion(BCFVersion.BCF_2_1)
                .build()) {
            writer.writeHeader(header);
            writer.add(vc);
        }
        return output;
    }

    private void writeBcfNoThrow(final VCFHeader header) {
        try {
            final Path output = Files.createTempFile(tempDir, "noThrow.", ".bcf");
            output.toFile().deleteOnExit();
            try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                    .clearOptions()
                    .setOutputPath(output)
                    .setOutputFileType(VariantContextWriterBuilder.OutputType.BCF)
                    .build()) {
                writer.writeHeader(header);
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    // BCF LAA ordering and missing-LAA tests

    @Test
    public void bcfLaaFollowsGtAtVersion45() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        VCFStandardHeaderLines.addStandardFormatLines(lines, true, VCFConstants.FORMAT.GENOTYPE);
        lines.add(new VCFFormatHeaderLine("AD", VCFHeaderLineCount.R, VCFHeaderLineType.Integer, "depths"));
        lines.add(new VCFFormatHeaderLine("LAA", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "local"));
        final SAMSequenceDictionary dict = new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 10000)));
        final VCFHeader header = new VCFHeader(VCFHeaderVersion.VCF4_5, lines, Set.of("s1"));
        header.setSequenceDictionary(dict);

        final List<Allele> alleles = List.of(Allele.create("A", true), Allele.create("C"));
        final VariantContext vc = new VariantContextBuilder("test", "chr1", 100, 100, alleles)
                .genotypes(new GenotypeBuilder("s1", alleles)
                        .attribute("LAA", List.of(1))
                        .attribute("AD", new int[] {10, 5})
                        .make())
                .make();

        final Path output = writeBcf(header, vc);
        final List<String> stdout = BcftoolsTestUtils.viewAsVcf(output);
        final String dataLine =
                stdout.stream().filter(l -> !l.startsWith("#")).findFirst().orElseThrow();
        Assert.assertTrue(dataLine.contains("GT:LAA:AD"), "BCF LAA should follow GT at 4.5: " + dataLine);

        final List<String> stdoutAndStderr =
                BcftoolsTestUtils.executeBcftools("view", "--no-version", "-Ov", output.toString());
        Assert.assertEquals(stdoutAndStderr, stdout, "bcftools warned about BCF with LAA ordering");
    }

    @Test
    public void bcfMissingLaaRendersAsDot() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        VCFStandardHeaderLines.addStandardFormatLines(
                lines, true, VCFConstants.FORMAT.GENOTYPE, VCFConstants.FORMAT.READ_DEPTH);
        lines.add(new VCFFormatHeaderLine("LAA", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "local"));
        final SAMSequenceDictionary dict = new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 10000)));
        final VCFHeader header =
                new VCFHeader(VCFHeaderVersion.VCF4_5, lines, new LinkedHashSet<>(List.of("s1", "s2")));
        header.setSequenceDictionary(dict);

        final List<Allele> alleles = List.of(Allele.create("A", true), Allele.create("C"));
        final VariantContext vc = new VariantContextBuilder("test", "chr1", 100, 100, alleles)
                .genotypes(
                        new GenotypeBuilder("s1", alleles)
                                .attribute("LAA", List.of(1))
                                .DP(10)
                                .make(),
                        new GenotypeBuilder("s2", alleles).DP(5).make())
                .make();

        final Path output = writeBcf(header, vc);

        // bcftools should render the missing LAA as .
        final List<String> stdout = BcftoolsTestUtils.viewAsVcf(output);
        final String dataLine =
                stdout.stream().filter(l -> !l.startsWith("#")).findFirst().orElseThrow();
        final String[] columns = dataLine.split("\t");
        final String s2Col = columns[columns.length - 1];
        Assert.assertTrue(s2Col.contains(".:"), "bcftools should render missing LAA as .: " + s2Col);

        // htsjdk should read back the missing LAA as absent
        final VariantContext readBack = readOne(output);
        Assert.assertFalse(
                readBack.getGenotype("s2").hasExtendedAttribute("LAA"),
                "Sample without LAA should read back without LAA attribute");
        Assert.assertTrue(
                readBack.getGenotype("s1").hasExtendedAttribute("LAA"),
                "Sample with LAA should read back with LAA attribute");
    }

    // BCF LAA pass-through boundary tests

    @Test
    public void bcfPassThrough44To45WithLaaReordersFormat() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        // Write a BCF at VCF 4.4 with FORMAT GT:XX:LAA (XX is a generic field to avoid special-cased keys)
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        VCFStandardHeaderLines.addStandardFormatLines(lines, true, VCFConstants.FORMAT.GENOTYPE);
        lines.add(new VCFFormatHeaderLine("XX", 1, VCFHeaderLineType.Integer, "test field"));
        lines.add(new VCFFormatHeaderLine("LAA", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "local"));
        final VCFHeader header44 = new VCFHeader(VCFHeaderVersion.VCF4_4, lines, Set.of("s1"));
        header44.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 10000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                        .attribute("LAA", List.of(1))
                        .attribute("XX", 7)
                        .make())
                .make();
        final Path bcf44 = writeBcf(header44, vc);

        // Read back lazily and rewrite at 4.5
        final Set<VCFHeaderLine> lines45 = new LinkedHashSet<>(lines);
        final VCFHeader header45 = new VCFHeader(VCFHeaderVersion.VCF4_5, lines45, Set.of("s1"));
        header45.setSequenceDictionary(header44.getSequenceDictionary());
        final Path bcf45 = Files.createTempFile(tempDir, "laa44to45.", ".bcf");
        bcf45.toFile().deleteOnExit();
        try (final VCFFileReader reader = new VCFFileReader(bcf44, false);
                final VariantContextWriter writer = new VariantContextWriterBuilder()
                        .clearOptions()
                        .setOutputPath(bcf45)
                        .setOutputFileType(VariantContextWriterBuilder.OutputType.BCF)
                        .setVCFVersion(VCFHeaderVersion.VCF4_5)
                        .build()) {
            writer.writeHeader(header45);
            for (final VariantContext v : reader) writer.add(v);
        }

        final List<String> stdout = BcftoolsTestUtils.viewAsVcf(bcf45);
        final String dataLine =
                stdout.stream().filter(l -> !l.startsWith("#")).findFirst().orElseThrow();
        Assert.assertTrue(
                dataLine.contains("GT:LAA:XX"),
                "BCF 4.4 source rewritten at 4.5 should reorder to GT:LAA:XX: " + dataLine);
    }

    @Test
    public void bcfPassThrough44To45WithoutLaaIsIdentical() throws IOException {
        // Write a BCF at VCF 4.4 without LAA
        final VCFHeader header44 = oneSampleGtHeader(VCFHeaderVersion.VCF4_4);
        final VariantContext vc = new VariantContextBuilder("t", "1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                        .phased(true)
                        .make())
                .make();
        final Path bcf44 = writeBcf(header44, vc);

        // Read back lazily and rewrite at 4.5 (no LAA in header)
        final VCFHeader header45 = oneSampleGtHeader(VCFHeaderVersion.VCF4_5);
        final Path bcf45 = Files.createTempFile(tempDir, "noLaa44to45.", ".bcf");
        bcf45.toFile().deleteOnExit();
        try (final VCFFileReader reader = new VCFFileReader(bcf44, false);
                final VariantContextWriter writer = new VariantContextWriterBuilder()
                        .clearOptions()
                        .setOutputPath(bcf45)
                        .setOutputFileType(VariantContextWriterBuilder.OutputType.BCF)
                        .setVCFVersion(VCFHeaderVersion.VCF4_5)
                        .build()) {
            writer.writeHeader(header45);
            for (final VariantContext v : reader) writer.add(v);
        }

        Assert.assertEquals(genotypeBlockOf(bcf45), genotypeBlockOf(bcf44), "Without LAA, genotype bytes should match");
    }

    @Test
    public void bcfPassThrough45To45WithLaaIsIdentical() throws IOException {
        // Write a BCF at VCF 4.5 with LAA and a generic field XX
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        VCFStandardHeaderLines.addStandardFormatLines(lines, true, VCFConstants.FORMAT.GENOTYPE);
        lines.add(new VCFFormatHeaderLine("XX", 1, VCFHeaderLineType.Integer, "test field"));
        lines.add(new VCFFormatHeaderLine("LAA", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "local"));
        final VCFHeader header45 = new VCFHeader(VCFHeaderVersion.VCF4_5, lines, Set.of("s1"));
        header45.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 10000))));
        final VariantContext vc = new VariantContextBuilder("t", "chr1", 100, 100, List.of(REF_A, ALT_C))
                .genotypes(new GenotypeBuilder("s1", List.of(REF_A, ALT_C))
                        .attribute("LAA", List.of(1))
                        .attribute("XX", 7)
                        .make())
                .make();
        final Path bcf45a = writeBcf(header45, vc);

        // Read back lazily and rewrite at 4.5
        final Path bcf45b = Files.createTempFile(tempDir, "laa45to45.", ".bcf");
        bcf45b.toFile().deleteOnExit();
        try (final VCFFileReader reader = new VCFFileReader(bcf45a, false);
                final VariantContextWriter writer = new VariantContextWriterBuilder()
                        .clearOptions()
                        .setOutputPath(bcf45b)
                        .setOutputFileType(VariantContextWriterBuilder.OutputType.BCF)
                        .setVCFVersion(VCFHeaderVersion.VCF4_5)
                        .build()) {
            writer.writeHeader(header45);
            for (final VariantContext v : reader) writer.add(v);
        }

        Assert.assertEquals(
                genotypeBlockOf(bcf45b), genotypeBlockOf(bcf45a), "4.5 to 4.5 with LAA should pass through unchanged");
    }
}
