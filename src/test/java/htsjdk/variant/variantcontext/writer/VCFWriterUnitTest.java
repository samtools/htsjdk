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

package htsjdk.variant.variantcontext.writer;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.TestUtil;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.FeatureReader;
import htsjdk.tribble.Tribble;
import htsjdk.tribble.readers.Utf8LineReader;
import htsjdk.tribble.readers.Utf8LineReaderIterator;
import htsjdk.utils.BcftoolsTestUtils;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.GenotypesContext;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineCount;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFHeaderVersion;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import htsjdk.variant.vcf.VCFStandardHeaderLines;
import htsjdk.variant.vcf.VCFUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author aaron
 *         <p/>
 *         Class VCFWriterUnitTest
 *         <p/>
 *         This class tests out the ability of the VCF writer to correctly write VCF files
 */
public class VCFWriterUnitTest extends VariantBaseTest {
    private Set<VCFHeaderLine> metaData;
    private Set<String> additionalColumns;
    private Path tempDir;

    @BeforeClass
    private void createTemporaryDirectory() {
        tempDir = TestUtil.getTempDirectoryAsPath("VCFWriter", "StaleIndex");
        tempDir.toFile().deleteOnExit();
    }

    /** test, using the writer and reader, that we can output and input a VCF file without problems */
    @Test(dataProvider = "vcfExtensionsDataProvider")
    public void testBasicWriteAndRead(final String extension) throws IOException {
        final Path fakeVCFPath = Files.createTempFile(tempDir, "testBasicWriteAndRead.", extension);
        fakeVCFPath.toFile().deleteOnExit();
        if (FileExtensions.COMPRESSED_VCF.equals(extension) || FileExtensions.COMPRESSED_VCF_BGZ.equals(extension)) {
            Path.of(fakeVCFPath.toAbsolutePath() + FileExtensions.VCF_INDEX);
        } else {
            Tribble.indexPath(fakeVCFPath).toFile().deleteOnExit();
        }
        metaData = new HashSet<>();
        additionalColumns = new HashSet<>();
        final SAMSequenceDictionary sequenceDict = createArtificialSequenceDictionary();
        final VCFHeader header = createFakeHeader(metaData, additionalColumns, sequenceDict);
        final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(fakeVCFPath)
                .setReferenceDictionary(sequenceDict)
                .setOptions(EnumSet.of(Options.ALLOW_MISSING_FIELDS_IN_HEADER, Options.INDEX_ON_THE_FLY))
                .build();
        writer.writeHeader(header);
        writer.add(createVC(header));
        writer.add(createVC(header));
        writer.close();
        final VCFCodec codec = new VCFCodec();
        final FeatureReader<VariantContext> reader = AbstractFeatureReader.getFeatureReader(
                fakeVCFPath.toAbsolutePath().toString(), codec, false);
        final VCFHeader headerFromFile = (VCFHeader) reader.getHeader();

        int counter = 0;

        // validate what we're reading in
        validateHeader(headerFromFile, sequenceDict);

        try {
            final Iterator<VariantContext> it = reader.iterator();
            while (it.hasNext()) {
                it.next();
                counter++;
            }
            Assert.assertEquals(counter, 2);
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /** test, using the writer and reader, that we can output and input a VCF body without problems */
    @Test(dataProvider = "vcfExtensionsDataProvider")
    public void testWriteAndReadVCFHeaderless(final String extension) throws IOException {
        final Path fakeVCFPath = Files.createTempFile(tempDir, "testWriteAndReadVCFHeaderless.", extension);
        fakeVCFPath.toFile().deleteOnExit();
        if (FileExtensions.COMPRESSED_VCF.equals(extension) || FileExtensions.COMPRESSED_VCF_BGZ.equals(extension)) {
            Path.of(fakeVCFPath.toAbsolutePath() + ".tbi");
        } else {
            Tribble.indexPath(fakeVCFPath).toFile().deleteOnExit();
        }
        metaData = new HashSet<>();
        additionalColumns = new HashSet<>();
        final SAMSequenceDictionary sequenceDict = createArtificialSequenceDictionary();
        final VCFHeader header = createFakeHeader(metaData, additionalColumns, sequenceDict);
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(fakeVCFPath)
                .setReferenceDictionary(sequenceDict)
                .setOptions(EnumSet.of(Options.ALLOW_MISSING_FIELDS_IN_HEADER, Options.INDEX_ON_THE_FLY))
                .build()) {
            writer.setHeader(header);
            writer.add(createVC(header));
            writer.add(createVC(header));
        }
        final VCFCodec codec = new VCFCodec();
        codec.setVCFHeader(header, VCFHeaderVersion.VCF4_2);

        try (BlockCompressedInputStream bcis = new BlockCompressedInputStream(fakeVCFPath);
                InputStream fis = Files.newInputStream(fakeVCFPath)) {
            Utf8LineReaderIterator iterator = new Utf8LineReaderIterator(new Utf8LineReader(
                    FileExtensions.COMPRESSED_VCF.equals(extension)
                                    || FileExtensions.COMPRESSED_VCF_BGZ.equals(extension)
                            ? bcis
                            : fis));
            int counter = 0;
            while (iterator.hasNext()) {
                VariantContext context = codec.decode(iterator.next());
                counter++;
            }
            Assert.assertEquals(counter, 2);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testWriteHeaderTwice() {
        final Path fakeVCFPath = VariantBaseTest.createTempFile("testBasicWriteAndRead.", FileExtensions.VCF);
        fakeVCFPath.toFile().deleteOnExit();
        final SAMSequenceDictionary sequenceDict = createArtificialSequenceDictionary();
        final VCFHeader header = createFakeHeader(metaData, additionalColumns, sequenceDict);
        // prevent writing header twice
        try (final VariantContextWriter writer1 = new VariantContextWriterBuilder()
                .setOutputPath(fakeVCFPath)
                .setReferenceDictionary(sequenceDict)
                .build()) {
            writer1.writeHeader(header);
            writer1.writeHeader(header);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testChangeHeaderAfterWritingHeader() {
        final Path fakeVCFPath = VariantBaseTest.createTempFile("testBasicWriteAndRead.", FileExtensions.VCF);
        fakeVCFPath.toFile().deleteOnExit();
        final SAMSequenceDictionary sequenceDict = createArtificialSequenceDictionary();
        final VCFHeader header = createFakeHeader(metaData, additionalColumns, sequenceDict);
        // prevent changing header if it's already written
        try (final VariantContextWriter writer2 = new VariantContextWriterBuilder()
                .setOutputPath(fakeVCFPath)
                .setReferenceDictionary(sequenceDict)
                .build()) {
            writer2.writeHeader(header);
            writer2.setHeader(header);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testChangeHeaderAfterWritingBody() {
        final Path fakeVCFPath = VariantBaseTest.createTempFile("testBasicWriteAndRead.", FileExtensions.VCF);
        fakeVCFPath.toFile().deleteOnExit();
        final SAMSequenceDictionary sequenceDict = createArtificialSequenceDictionary();
        final VCFHeader header = createFakeHeader(metaData, additionalColumns, sequenceDict);
        // prevent changing header if part of body is already written
        try (final VariantContextWriter writer3 = new VariantContextWriterBuilder()
                .setOutputPath(fakeVCFPath)
                .setReferenceDictionary(sequenceDict)
                .build()) {
            writer3.setHeader(header);
            writer3.add(createVC(header));
            writer3.setHeader(header);
        }
    }

    /**
     * create a fake header of known quantity
     * @param metaData           the header lines
     * @param additionalColumns  the additional column names
     * @return a fake VCF header
     */
    private static VCFHeader createFakeHeader(
            final Set<VCFHeaderLine> metaData,
            final Set<String> additionalColumns,
            final SAMSequenceDictionary sequenceDict) {
        metaData.add(new VCFHeaderLine(
                VCFHeaderVersion.VCF4_0.getFormatString(), VCFHeaderVersion.VCF4_0.getVersionString()));
        metaData.add(new VCFHeaderLine("two", "2"));
        additionalColumns.add("extra1");
        additionalColumns.add("extra2");
        final VCFHeader ret = new VCFHeader(metaData, additionalColumns);
        ret.setSequenceDictionary(sequenceDict);
        return ret;
    }

    /**
     * create a fake VCF record
     * @param header the VCF header
     * @return a VCFRecord
     */
    private VariantContext createVC(final VCFHeader header) {

        return createVCGeneral(header, "1", 1);
    }

    private VariantContext createVCGeneral(final VCFHeader header, final String chrom, final int position) {
        final List<Allele> alleles = new ArrayList<Allele>();
        final Map<String, Object> attributes = new HashMap<String, Object>();
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
        return new VariantContextBuilder("RANDOM", chrom, position, position, alleles)
                .genotypes(genotypes)
                .attributes(attributes)
                .make();
    }

    /**
     * validate a VCF header
     * @param header the header to validate
     */
    private void validateHeader(final VCFHeader header, final SAMSequenceDictionary sequenceDictionary) {
        // check the fields
        int index = 0;
        for (final VCFHeader.HEADER_FIELDS field : header.getHeaderFields()) {
            Assert.assertEquals(VCFHeader.HEADER_FIELDS.values()[index], field);
            index++;
        }
        Assert.assertEquals(header.getMetaDataInSortedOrder().size(), metaData.size() + sequenceDictionary.size());
        index = 0;
        for (final String key : header.getGenotypeSamples()) {
            Assert.assertTrue(additionalColumns.contains(key));
            index++;
        }
        Assert.assertEquals(index, additionalColumns.size());
    }

    @Test(dataProvider = "vcfExtensionsDataProvider")
    public void TestWritingLargeVCF(final String extension) throws IOException, InterruptedException {

        final Set<VCFHeaderLine> metaData = new HashSet<VCFHeaderLine>();
        final Set<String> Columns = new HashSet<String>();
        for (int i = 0; i < 123; i++) {

            Columns.add(String.format("SAMPLE_%d", i));
        }

        final SAMSequenceDictionary dict = createArtificialSequenceDictionary();
        final VCFHeader header = createFakeHeader(metaData, Columns, dict);

        final Path vcf = tempDir.resolve("test" + extension);
        final String indexExtension;
        if (extension.equals(FileExtensions.COMPRESSED_VCF) || extension.equals(FileExtensions.COMPRESSED_VCF_BGZ)) {
            indexExtension = FileExtensions.TABIX_INDEX;
        } else {
            indexExtension = FileExtensions.TRIBBLE_INDEX;
        }
        final Path vcfIndex = Path.of(vcf.toAbsolutePath() + indexExtension);
        vcfIndex.toFile().deleteOnExit();

        for (int count = 1; count < 2; count++) {
            final VariantContextWriter writer = new VariantContextWriterBuilder()
                    .setOutputPath(vcf)
                    .setReferenceDictionary(dict)
                    .setOptions(EnumSet.of(Options.ALLOW_MISSING_FIELDS_IN_HEADER, Options.INDEX_ON_THE_FLY))
                    .build();
            writer.writeHeader(header);

            for (int i = 1; i < 17; i++) { // write 17 chromosomes
                for (int j = 1; j < 10; j++) { // 10 records each
                    writer.add(createVCGeneral(header, String.format("%d", i), j * 100));
                }
            }
            writer.close();

            Assert.assertTrue(vcf.toFile().lastModified() <= vcfIndex.toFile().lastModified());
        }
    }

    @DataProvider(name = "vcfExtensionsDataProvider")
    public Object[][] vcfExtensionsDataProvider() {
        return new Object[][] {
            // TODO: BCF doesn't work because header is not properly constructed.
            // {".bcf"},
            {FileExtensions.VCF}, {FileExtensions.COMPRESSED_VCF}, {FileExtensions.COMPRESSED_VCF_BGZ}
        };
    }

    /**
     * A test to ensure that if we add a line to a VCFHeader it will persist through
     * a round-trip write/read cycle via VariantContextWriter/VCFFileReader
     */
    @Test
    public void testModifyHeader() {
        final Path originalVCF = Path.of("src/test/resources/htsjdk/variant/HiSeq.10000.vcf");
        final VCFFileReader reader = new VCFFileReader(originalVCF, false);
        final VCFHeader header = reader.getFileHeader();
        reader.close();

        header.addMetaDataLine(new VCFHeaderLine("FOOBAR", "foovalue"));

        final Path outputVCF = createTempFile("testModifyHeader", FileExtensions.VCF);
        final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(outputVCF)
                .setOptions(EnumSet.of(Options.ALLOW_MISSING_FIELDS_IN_HEADER))
                .build();
        writer.writeHeader(header);
        writer.close();

        final VCFFileReader roundtripReader = new VCFFileReader(outputVCF, false);
        final VCFHeader roundtripHeader = roundtripReader.getFileHeader();
        roundtripReader.close();

        Assert.assertNotNull(
                roundtripHeader.getOtherHeaderLine("FOOBAR"),
                "Could not find FOOBAR header line after a write/read cycle");
        Assert.assertEquals(
                roundtripHeader.getOtherHeaderLine("FOOBAR").getValue(),
                "foovalue",
                "Wrong value for FOOBAR header line after a write/read cycle");
    }

    /**
     *
     * A test to check that we can't write VCF with missing header.
     */
    @Test(dataProvider = "vcfExtensionsDataProvider", expectedExceptions = IllegalStateException.class)
    public void testWriteWithEmptyHeader(final String extension) throws IOException {
        final Path fakeVCFPath = Files.createTempFile(tempDir, "testWriteAndReadVCFHeaderless.", extension);
        metaData = new HashSet<>();
        additionalColumns = new HashSet<>();
        final SAMSequenceDictionary sequenceDict = createArtificialSequenceDictionary();
        final VCFHeader header = createFakeHeader(metaData, additionalColumns, sequenceDict);
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(fakeVCFPath)
                .setReferenceDictionary(sequenceDict)
                .setOptions(EnumSet.of(Options.ALLOW_MISSING_FIELDS_IN_HEADER, Options.INDEX_ON_THE_FLY))
                .build()) {
            writer.add(createVC(header));
        }
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
            final Path output = Files.createTempFile(tempDir, "from43.", ".vcf");
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

        final Path output = Files.createTempFile(tempDir, "numberLR.", ".vcf");
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

        final List<String> fileLines = Files.readAllLines(output);
        Assert.assertTrue(
                fileLines.stream().anyMatch(l -> l.contains("Number=LR")), "LR header line should be present");
        final String dataLine =
                fileLines.stream().filter(l -> !l.startsWith("#")).findFirst().orElseThrow();
        // FORMAT is GT:LAD; s1 gets 10,5 and s2 gets 0,7,3
        Assert.assertTrue(dataLine.contains("GT:LAD"), "FORMAT should include LAD");
        Assert.assertTrue(dataLine.contains("10,5"), "s1 LAD values should be present");
        Assert.assertTrue(dataLine.contains("0,7,3"), "s2 LAD values should be present");
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
        final Path output = Files.createTempFile(tempDir, "mixedPhasing.", ".vcf");
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
    public void aFirstAllelePhaseTheFormatCannotExpressIsRefused() throws IOException {
        final VCFHeader header = oneSampleGtHeader();
        final Path output = Files.createTempFile(tempDir, "leadingPhase.", ".vcf");
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
        final Path output = Files.createTempFile(tempDir, "refusedThenWritten.", ".vcf");
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
        final List<String> records = Files.readAllLines(output).stream()
                .filter(line -> !line.startsWith("#"))
                .collect(Collectors.toList());
        Assert.assertEquals(records, List.of("1\t100\t.\tA\tC,G\t.\t.\t.\tGT\t0/1|2"));
    }

    // UTF-8 round-trip tests

    /** Builds a minimal VCF header with one sample, a String INFO field, and a String FORMAT field. */
    private static VCFHeader utf8TestHeader(final String sampleName) {
        final SAMSequenceDictionary dict = new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 10000)));
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("NOTE", 1, VCFHeaderLineType.String, "A note"));
        lines.add(new VCFFormatHeaderLine("CMT", 1, VCFHeaderLineType.String, "Comment"));
        VCFStandardHeaderLines.addStandardFormatLines(lines, true, VCFConstants.GENOTYPE_KEY);
        final VCFHeader header = new VCFHeader(lines, List.of(sampleName));
        header.setSequenceDictionary(dict);
        return header;
    }

    /** Builds a VariantContext with a String INFO value and a String FORMAT value. */
    private static VariantContext utf8Variant(final String sampleName, final String infoValue, final String fmtValue) {
        return new VariantContextBuilder()
                .chr("chr1")
                .start(100)
                .stop(100)
                .alleles(List.of(Allele.REF_A, Allele.ALT_C))
                .attribute("NOTE", infoValue)
                .genotypes(new GenotypeBuilder(sampleName, List.of(Allele.REF_A, Allele.ALT_C))
                        .attribute("CMT", fmtValue)
                        .make())
                .make();
    }

    @Test
    public void utf8InInfoAndFormatRoundTripsViaPlainVcf() throws IOException {
        // Two- and three-byte sequences; the arrow, lambda and kanji lie outside Latin-1
        final String sample = "Sébastien→λ";
        final String infoVal = "café→日本";
        final String fmtVal = "résumé_λ";

        final VCFHeader header = utf8TestHeader(sample);
        final Path output = Files.createTempFile(tempDir, "utf8.", ".vcf");
        output.toFile().deleteOnExit();

        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
            writer.add(utf8Variant(sample, infoVal, fmtVal));
        }

        // Verify the on-disk bytes are UTF-8
        final byte[] fileBytes = Files.readAllBytes(output);
        final String text = new String(fileBytes, StandardCharsets.UTF_8);
        Assert.assertTrue(text.contains(sample), "Sample name not found in file");
        Assert.assertTrue(text.contains(infoVal), "INFO value not found in file");

        // Read back via VCFFileReader, which decodes through SynchronousLineReader
        try (final VCFFileReader reader = new VCFFileReader(output, false)) {
            Assert.assertTrue(reader.getFileHeader().getSampleNamesInOrder().contains(sample));
            final VariantContext vc = reader.iterator().next();
            Assert.assertEquals(vc.getAttribute("NOTE"), infoVal);
            Assert.assertEquals(vc.getGenotype(sample).getExtendedAttribute("CMT"), fmtVal);
        }
    }

    @Test
    public void utf8FourByteCharacterSurvivesRoundTrip() throws IOException {
        // U+1F600 (grinning face) - a character outside the BMP
        final String smiley = new String(Character.toChars(0x1F600));
        final String sample = "sample1";
        final VCFHeader header = utf8TestHeader(sample);
        final Path output = Files.createTempFile(tempDir, "utf8-4byte.", ".vcf");
        output.toFile().deleteOnExit();

        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
            writer.add(utf8Variant(sample, "face" + smiley, smiley + "ok"));
        }

        try (final VCFFileReader reader = new VCFFileReader(output, false)) {
            final VariantContext vc = reader.iterator().next();
            Assert.assertEquals(vc.getAttribute("NOTE"), "face" + smiley);
            Assert.assertEquals(vc.getGenotype(sample).getExtendedAttribute("CMT"), smiley + "ok");
        }
    }

    @Test
    public void utf8InHeaderDescriptionSurvivesRoundTrip() throws IOException {
        final String sample = "sample1";
        final SAMSequenceDictionary dict = new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 10000)));
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        final String description = "Fréquence d'échantillonnage → λ 日本"; // accented Latin-1 and characters outside it
        lines.add(new VCFInfoHeaderLine("FREQ", 1, VCFHeaderLineType.Float, description));
        final VCFHeader header = new VCFHeader(lines, List.of(sample));
        header.setSequenceDictionary(dict);

        final Path output = Files.createTempFile(tempDir, "utf8-desc.", ".vcf");
        output.toFile().deleteOnExit();

        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
        }

        try (final VCFFileReader reader = new VCFFileReader(output, false)) {
            final VCFInfoHeaderLine freq = reader.getFileHeader().getInfoHeaderLine("FREQ");
            Assert.assertEquals(freq.getDescription(), description);
        }
    }

    // Version resolution tests

    private static VCFHeader versionTestHeader(final VCFHeaderVersion version) {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        if (version != null) {
            lines.add(new VCFHeaderLine(version.getFormatString(), version.getVersionString()));
        }
        lines.add(new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "depth"));
        VCFStandardHeaderLines.addStandardFormatLines(lines, true, VCFConstants.GENOTYPE_KEY);
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(createArtificialSequenceDictionary());
        return header;
    }

    private static String firstLineOfFile(final Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8).get(0);
    }

    @Test
    public void writesResolvedVersionFromHeader() throws IOException {
        final VCFHeader header = versionTestHeader(VCFHeaderVersion.VCF4_3);
        final Path output = Files.createTempFile(tempDir, "ver43.", ".vcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
        }
        Assert.assertEquals(firstLineOfFile(output), "##fileformat=VCFv4.3");
    }

    @Test
    public void writesResolvedVersionFromBuilder() throws IOException {
        final VCFHeader header = versionTestHeader(VCFHeaderVersion.VCF4_2);
        final Path output = Files.createTempFile(tempDir, "ver44.", ".vcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .setVCFVersion(VCFHeaderVersion.VCF4_4)
                .build()) {
            writer.writeHeader(header);
        }
        Assert.assertEquals(firstLineOfFile(output), "##fileformat=VCFv4.4");
    }

    @Test
    public void writesVersionFloorOf42() throws IOException {
        final VCFHeader header = versionTestHeader(VCFHeaderVersion.VCF4_0);
        final Path output = Files.createTempFile(tempDir, "ver40.", ".vcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
        }
        Assert.assertEquals(firstLineOfFile(output), "##fileformat=VCFv4.2");
    }

    @Test
    public void versionlessHeaderWritesAs42() throws IOException {
        final VCFHeader header = versionTestHeader(null);
        final Path output = Files.createTempFile(tempDir, "versionless.", ".vcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
        }
        Assert.assertEquals(firstLineOfFile(output), "##fileformat=VCFv4.2");
    }

    @Test
    public void anExplicitVersionBelowTheHeadersIsHonoured() throws IOException {
        final VCFHeader header = versionTestHeader(VCFHeaderVersion.VCF4_3);
        final Path output = Files.createTempFile(tempDir, "ver40explicit.", ".vcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .setVCFVersion(VCFHeaderVersion.VCF4_0)
                .build()) {
            writer.writeHeader(header);
        }
        Assert.assertEquals(firstLineOfFile(output), "##fileformat=VCFv4.0");
    }

    @Test
    public void writingDoesNotChangeTheCallersHeaderVersion() throws IOException {
        final VCFHeader header = versionTestHeader(VCFHeaderVersion.VCF4_3);
        final VCFHeader versionless = versionTestHeader(null);
        final Path output = Files.createTempFile(tempDir, "callersHeader.", ".vcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .setVCFVersion(VCFHeaderVersion.VCF4_2)
                .build()) {
            writer.writeHeader(header);
        }
        Assert.assertEquals(firstLineOfFile(output), "##fileformat=VCFv4.2");
        Assert.assertEquals(header.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_3);

        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(versionless.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(versionless);
        }
        Assert.assertNull(versionless.getVCFHeaderVersion());
    }

    @Test
    public void headerVersionMatchesWrittenVersion() throws IOException {
        final VCFHeader header = versionTestHeader(VCFHeaderVersion.VCF4_3);
        final Path output = Files.createTempFile(tempDir, "readback43.", ".vcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
        }
        try (final VCFFileReader reader = new VCFFileReader(output, false)) {
            Assert.assertEquals(reader.getFileHeader().getVCFHeaderVersion(), VCFHeaderVersion.VCF4_3);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void headerWithNumberRRefusedFor40() {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine("AC", VCFHeaderLineCount.R, VCFHeaderLineType.Integer, "allele count"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(createArtificialSequenceDictionary());
        final Path output = createTempFile("refusedR40.", ".vcf");
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .setVCFVersion(VCFHeaderVersion.VCF4_0)
                .build()) {
            writer.writeHeader(header);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void headerWithNumberPRefusedFor42() {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(
                new VCFFormatHeaderLine("PSL", VCFHeaderLineCount.P, VCFHeaderLineType.Integer, "phased set lengths"));
        VCFStandardHeaderLines.addStandardFormatLines(lines, true, VCFConstants.GENOTYPE_KEY);
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(createArtificialSequenceDictionary());
        final Path output = createTempFile("refusedP42.", ".vcf");
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .setVCFVersion(VCFHeaderVersion.VCF4_2)
                .build()) {
            writer.writeHeader(header);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void headerWithNumberLARefusedFor44() {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFormatHeaderLine("LAD", VCFHeaderLineCount.LA, VCFHeaderLineType.Integer, "local depths"));
        VCFStandardHeaderLines.addStandardFormatLines(lines, true, VCFConstants.GENOTYPE_KEY);
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(createArtificialSequenceDictionary());
        final Path output = createTempFile("refusedLA44.", ".vcf");
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .setVCFVersion(VCFHeaderVersion.VCF4_4)
                .build()) {
            writer.writeHeader(header);
        }
    }

    @Test
    public void a45HeaderWithNumberLAIsRefusedWhenDowngradedTo43() {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFHeaderLine("fileformat", "VCFv4.5"));
        lines.add(new VCFFormatHeaderLine("LAD", VCFHeaderLineCount.LA, VCFHeaderLineType.Integer, "local depths"));
        VCFStandardHeaderLines.addStandardFormatLines(lines, true, VCFConstants.GENOTYPE_KEY);
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(createArtificialSequenceDictionary());
        final Path output = createTempFile("refusedLA43.", ".vcf");
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .setVCFVersion(VCFHeaderVersion.VCF4_3)
                .build()) {
            final IllegalStateException refusal =
                    Assert.expectThrows(IllegalStateException.class, () -> writer.writeHeader(header));
            Assert.assertTrue(refusal.getMessage().contains("VCFv4.3"), refusal.getMessage());
            Assert.assertTrue(refusal.getMessage().contains("FORMAT=<ID=LAD,Number=LA,"), refusal.getMessage());
            Assert.assertTrue(refusal.getMessage().contains("setVCFVersion(VCF4_5)"), refusal.getMessage());
        }
    }

    @Test
    public void headerWithNumberLAAcceptedFor45() throws IOException {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFormatHeaderLine("LAD", VCFHeaderLineCount.LA, VCFHeaderLineType.Integer, "local depths"));
        VCFStandardHeaderLines.addStandardFormatLines(lines, true, VCFConstants.GENOTYPE_KEY);
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(createArtificialSequenceDictionary());
        final Path output = Files.createTempFile(tempDir, "acceptedLA45.", ".vcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .setVCFVersion(VCFHeaderVersion.VCF4_5)
                .build()) {
            writer.writeHeader(header);
        }
        Assert.assertEquals(firstLineOfFile(output), "##fileformat=VCFv4.5");
    }

    @Test
    public void leadingPhaseRefusedForVersion42() throws IOException {
        final VCFHeader header = versionTestHeader(VCFHeaderVersion.VCF4_2);
        final VariantContext vc = triploidWithAllelePhasing(true, false, false);
        final Path output = Files.createTempFile(tempDir, "phaseRefused42.", ".vcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
            Assert.expectThrows(IllegalStateException.class, () -> writer.add(vc));
        }
    }

    @Test
    public void leadingPhaseAcceptedForVersion44() throws IOException {
        final VCFHeader header = versionTestHeader(VCFHeaderVersion.VCF4_4);
        final List<Allele> alleles = List.of(Allele.create("A", true), Allele.create("C"));
        final VariantContext vc = new VariantContextBuilder("test", "1", 100, 100, alleles)
                .genotypes(new GenotypeBuilder("s1", alleles)
                        .allelePhasing(new boolean[] {true, false})
                        .make())
                .make();
        final Path output = Files.createTempFile(tempDir, "phaseAccepted44.", ".vcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
            writer.add(vc);
        }
        final List<String> records = Files.readAllLines(output).stream()
                .filter(line -> !line.startsWith("#"))
                .collect(Collectors.toList());
        Assert.assertTrue(records.get(0).endsWith("|0/1"), "Expected leading phase indicator: " + records.get(0));
    }

    @Test
    public void partialRecordDiscardedOnPhaseRefusal() throws IOException {
        final VCFHeader header = versionTestHeader(VCFHeaderVersion.VCF4_2);
        final VariantContext badVc = triploidWithAllelePhasing(true, false, false);
        final List<Allele> alleles = List.of(Allele.create("A", true), Allele.create("C"));
        final VariantContext goodVc = new VariantContextBuilder("test", "1", 200, 200, alleles)
                .genotypes(new GenotypeBuilder("s1", alleles).phased(true).make())
                .make();
        final Path output = Files.createTempFile(tempDir, "partialDiscard.", ".vcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
            Assert.expectThrows(IllegalStateException.class, () -> writer.add(badVc));
            writer.add(goodVc);
        }
        final List<String> records = Files.readAllLines(output).stream()
                .filter(line -> !line.startsWith("#"))
                .collect(Collectors.toList());
        Assert.assertEquals(records.size(), 1, "Only the good record should be present");
        Assert.assertTrue(records.get(0).contains("200"), "The good record should be at position 200");
    }

    @Test
    public void utf8RoundTripsViaBgzippedVcf() throws IOException {
        final String sample = "Schön_日本";
        final String infoVal = "üäö→λ";

        final VCFHeader header = utf8TestHeader(sample);
        final Path output = Files.createTempFile(tempDir, "utf8.", ".vcf.gz");
        output.toFile().deleteOnExit();

        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
            writer.add(utf8Variant(sample, infoVal, "test"));
        }

        // Read back via VCFFileReader, which decodes the decompressed stream through SynchronousLineReader
        try (final VCFFileReader reader = new VCFFileReader(output, false)) {
            Assert.assertTrue(reader.getFileHeader().getSampleNamesInOrder().contains(sample));
            final VariantContext vc = reader.iterator().next();
            Assert.assertEquals(vc.getAttribute("NOTE"), infoVal);
        }
    }

    // bcftools interop tests

    private static VCFHeader interopHeader(final VCFHeaderVersion version) {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFHeaderLine(version.getFormatString(), version.getVersionString()));
        lines.add(new VCFInfoHeaderLine("NOTE", 1, VCFHeaderLineType.String, "A note"));
        VCFStandardHeaderLines.addStandardFormatLines(lines, true, VCFConstants.GENOTYPE_KEY);
        lines.add(new VCFFormatHeaderLine("CMT", 1, VCFHeaderLineType.String, "comment"));
        final SAMSequenceDictionary dict = new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 10000)));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(dict);
        return header;
    }

    private Path writeInteropVcf(final VCFHeader header, final VariantContext vc) throws IOException {
        final Path output = Files.createTempFile(tempDir, "interop.", ".vcf");
        output.toFile().deleteOnExit();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputPath(output)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            writer.writeHeader(header);
            writer.add(vc);
        }
        return output;
    }

    @Test
    public void bcftoolsAcceptsVersion43Output() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        final VCFHeader header = interopHeader(VCFHeaderVersion.VCF4_3);
        final VariantContext vc = new VariantContextBuilder()
                .chr("chr1")
                .start(100)
                .stop(100)
                .alleles(List.of(Allele.REF_A, Allele.ALT_C))
                .attribute("NOTE", "key=value;other")
                .genotypes(new GenotypeBuilder("s1", List.of(Allele.REF_A, Allele.ALT_C))
                        .attribute("CMT", "hello")
                        .make())
                .make();
        final Path output = writeInteropVcf(header, vc);
        final List<String> lines = BcftoolsTestUtils.viewAsVcf(output);
        Assert.assertTrue(
                lines.stream().anyMatch(l -> l.contains("key%3Dvalue%3Bother")),
                "bcftools should see percent-encoded value");
    }

    @Test
    public void bcftoolsAcceptsVersion44Output() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        final VCFHeader header = interopHeader(VCFHeaderVersion.VCF4_4);
        final List<Allele> alleles = List.of(Allele.create("A", true), Allele.create("C"));
        final VariantContext vc = new VariantContextBuilder("test", "chr1", 100, 100, alleles)
                .attribute("NOTE", "ok")
                .genotypes(new GenotypeBuilder("s1", alleles)
                        .allelePhasing(new boolean[] {true, false})
                        .attribute("CMT", "test")
                        .make())
                .make();
        final Path output = writeInteropVcf(header, vc);
        final List<String> lines = BcftoolsTestUtils.viewAsVcf(output);
        Assert.assertTrue(
                lines.stream().anyMatch(l -> l.contains("|0/1")), "bcftools should see leading phase indicator");
    }

    @Test
    public void bcftoolsAcceptsVersion45Output() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        final Set<VCFHeaderLine> lines45 = new LinkedHashSet<>();
        lines45.add(new VCFHeaderLine("fileformat", "VCFv4.5"));
        lines45.add(new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "depth"));
        VCFStandardHeaderLines.addStandardFormatLines(lines45, true, VCFConstants.GENOTYPE_KEY, VCFConstants.DEPTH_KEY);
        lines45.add(new VCFFormatHeaderLine("LAA", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "local"));
        final SAMSequenceDictionary dict = new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 10000)));
        final VCFHeader header = new VCFHeader(lines45, List.of("s1"));
        header.setSequenceDictionary(dict);

        final List<Allele> alleles = List.of(Allele.create("A", true), Allele.create("C"));
        final VariantContext vc = new VariantContextBuilder("test", "chr1", 100, 100, alleles)
                .attribute("DP", 50)
                .genotypes(new GenotypeBuilder("s1", alleles)
                        .attribute("LAA", List.of(1))
                        .DP(7)
                        .make())
                .make();
        final Path output = writeInteropVcf(header, vc);
        final List<String> stdout = BcftoolsTestUtils.viewAsVcf(output);
        final String dataLine =
                stdout.stream().filter(l -> !l.startsWith("#")).findFirst().orElseThrow();
        Assert.assertTrue(dataLine.endsWith("\tGT:LAA:DP\t0/1:1:7"), "LAA should follow GT: " + dataLine);
        // what bcftools wrote to stdout and stderr together is what it wrote to stdout alone: no warning
        final List<String> stdoutAndStderr =
                BcftoolsTestUtils.executeBcftools("view", "--no-version", "-Ov", output.toString());
        Assert.assertEquals(stdoutAndStderr, stdout, "bcftools warned about the 4.5 output");
    }

    @Test
    public void roundTripThroughBcftoolsPreservesPercentEncoding() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        final VCFHeader header = interopHeader(VCFHeaderVersion.VCF4_3);
        final String value = "key=value;colon:here";
        final VariantContext vc = new VariantContextBuilder()
                .chr("chr1")
                .start(100)
                .stop(100)
                .alleles(List.of(Allele.REF_A, Allele.ALT_C))
                .attribute("NOTE", value)
                .genotypes(new GenotypeBuilder("s1", List.of(Allele.REF_A, Allele.ALT_C))
                        .attribute("CMT", "ok")
                        .make())
                .make();
        final Path output = writeInteropVcf(header, vc);

        // Read the bcftools-rendered text
        final List<String> bcfLines = BcftoolsTestUtils.viewAsVcf(output);
        final String dataLine =
                bcfLines.stream().filter(l -> !l.startsWith("#")).findFirst().orElseThrow();
        Assert.assertTrue(
                dataLine.contains("key%3Dvalue%3Bcolon%3Ahere"),
                "Percent-encoded value should survive bcftools: " + dataLine);

        // Read back through htsjdk and verify decoding
        try (final VCFFileReader reader = new VCFFileReader(output, false)) {
            final VariantContext readBack = reader.iterator().next();
            Assert.assertEquals(readBack.getAttribute("NOTE"), value);
        }
    }
}
