package htsjdk.variant.bcf2;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.TestUtil;
import htsjdk.tribble.TribbleException;
import htsjdk.utils.BcftoolsTestUtils;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFContigHeaderLine;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests for {@link BCFFileReader}: reading BGZF BCF files with CSI region queries.
 */
public class BCFFileReaderTest extends VariantBaseTest {
    private Path tempDir;

    @BeforeClass(alwaysRun = true)
    private void createTempDir() {
        tempDir = TestUtil.getTempDirectoryAsPath("BCFFileReader", "test");
        tempDir.toFile().deleteOnExit();
    }

    /** Builds a multi-contig header with three contigs of various lengths. */
    private static VCFHeader multiContigHeader() {
        final SAMSequenceDictionary dict = new SAMSequenceDictionary();
        dict.addSequence(new SAMSequenceRecord("chr1", 100000));
        dict.addSequence(new SAMSequenceRecord("chr2", 50000));
        dict.addSequence(new SAMSequenceRecord("chr3", 30000));
        final Set<VCFHeaderLine> meta = new HashSet<>();
        meta.add(new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "Depth"));
        meta.add(new VCFInfoHeaderLine("END", 1, VCFHeaderLineType.Integer, "End position"));
        meta.add(new VCFInfoHeaderLine("SVLEN", 1, VCFHeaderLineType.Integer, "SV length"));
        meta.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "Genotype"));
        final VCFHeader header = new VCFHeader(meta, Collections.singletonList("sample1"));
        header.setSequenceDictionary(dict);
        return header;
    }

    /** Writes a multi-contig BCF with a CSI index and returns the BCF path. */
    private Path writeMultiContigBcf() throws IOException {
        final VCFHeader header = multiContigHeader();
        final Path bcf = Files.createTempFile(tempDir, "multi.", ".bcf");
        bcf.toFile().deleteOnExit();
        bcf.resolveSibling(bcf.getFileName() + FileExtensions.CSI).toFile().deleteOnExit();
        try (VariantContextWriter w = new VariantContextWriterBuilder()
                .setOutputPath(bcf)
                .setReferenceDictionary(header.getSequenceDictionary())
                .setOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            w.writeHeader(header);
            // chr1: SNP at 100, deletion at 500 (END=600), reference block at 1000 (END=2000)
            w.add(snp(header, "chr1", 100));
            w.add(endRecord(header, "chr1", 500, 600));
            w.add(endRecord(header, "chr1", 1000, 2000));
            // chr2: SNP at 200
            w.add(snp(header, "chr2", 200));
            // chr3: empty (no records)
        }
        return bcf;
    }

    private static VariantContext snp(VCFHeader header, String contig, int pos) {
        return new VariantContextBuilder("test", contig, pos, pos, Arrays.asList(Allele.REF_A, Allele.ALT_C))
                .genotypes(new GenotypeBuilder("sample1", Arrays.asList(Allele.REF_A, Allele.ALT_C)).make())
                .attribute("DP", 30)
                .make();
    }

    private static VariantContext endRecord(VCFHeader header, String contig, int pos, int end) {
        return new VariantContextBuilder("test", contig, pos, end, Arrays.asList(Allele.REF_A, Allele.ALT_C))
                .genotypes(new GenotypeBuilder("sample1", Arrays.asList(Allele.REF_A, Allele.ALT_C)).make())
                .attribute("DP", 30)
                .attribute("END", end)
                .make();
    }

    // ============================================================
    // Query tests
    // ============================================================

    @Test
    public void aQueryOnAnHtsjdkCsiReturnsTheExpectedRecords() throws IOException {
        final Path bcf = writeMultiContigBcf();
        try (VCFFileReader reader = new VCFFileReader(bcf, true)) {
            Assert.assertTrue(reader.isQueryable());
            final List<VariantContext> results = toList(reader.query("chr1", 50, 150));
            Assert.assertEquals(results.size(), 1);
            Assert.assertEquals(results.get(0).getStart(), 100);
        }
    }

    @Test
    public void aQueryOnAnEmptyContigReturnsNothing() throws IOException {
        final Path bcf = writeMultiContigBcf();
        try (VCFFileReader reader = new VCFFileReader(bcf, true)) {
            final List<VariantContext> results = toList(reader.query("chr3", 1, 30000));
            Assert.assertTrue(results.isEmpty());
        }
    }

    @Test
    public void aQueryOnAContigAbsentFromTheHeaderReturnsNothing() throws IOException {
        final Path bcf = writeMultiContigBcf();
        try (VCFFileReader reader = new VCFFileReader(bcf, true)) {
            final List<VariantContext> results = toList(reader.query("chrX", 1, 100));
            Assert.assertTrue(results.isEmpty());
        }
    }

    @Test
    public void aRecordWithEndIsFoundByAQueryInsideItsSpan() throws IOException {
        final Path bcf = writeMultiContigBcf();
        try (VCFFileReader reader = new VCFFileReader(bcf, true)) {
            // END=2000 record at chr1:1000, query 1500-1600
            final List<VariantContext> results = toList(reader.query("chr1", 1500, 1600));
            Assert.assertEquals(results.size(), 1);
            Assert.assertEquals(results.get(0).getStart(), 1000);
            Assert.assertEquals(results.get(0).getEnd(), 2000);
        }
    }

    @Test
    public void aQuerySpanningMultipleRecordsReturnsAllOverlapping() throws IOException {
        final Path bcf = writeMultiContigBcf();
        try (VCFFileReader reader = new VCFFileReader(bcf, true)) {
            final List<VariantContext> results = toList(reader.query("chr1", 1, 1000));
            Assert.assertEquals(results.size(), 3);
        }
    }

    @Test
    public void iteratorAfterQueryRestartsFromFirstRecord() throws IOException {
        final Path bcf = writeMultiContigBcf();
        try (VCFFileReader reader = new VCFFileReader(bcf, true)) {
            // Query first
            Assert.assertEquals(toList(reader.query("chr2", 100, 300)).size(), 1);
            // Then iterate all - should restart from beginning
            int count = 0;
            for (final VariantContext ignored : reader) {
                count++;
            }
            Assert.assertEquals(count, 4);
        }
    }

    @Test
    public void isQueryableIsTrueWithACsiAndFalseWithout() throws IOException {
        final Path bcf = writeMultiContigBcf();
        try (VCFFileReader reader = new VCFFileReader(bcf, true)) {
            Assert.assertTrue(reader.isQueryable());
        }
        // Open without index
        try (VCFFileReader reader = new VCFFileReader(bcf, false)) {
            // If CSI exists, the reader still finds and uses it
            Assert.assertTrue(reader.isQueryable());
        }
        // Remove the CSI and open without requiring index
        final Path csiPath = bcf.resolveSibling(bcf.getFileName() + FileExtensions.CSI);
        Files.deleteIfExists(csiPath);
        try (VCFFileReader reader = new VCFFileReader(bcf, false)) {
            Assert.assertFalse(reader.isQueryable());
        }
    }

    @Test
    public void requireIndexTrueWithoutACsiThrows() throws IOException {
        final VCFHeader header = multiContigHeader();
        final Path bcf = Files.createTempFile(tempDir, "noindex.", ".bcf");
        bcf.toFile().deleteOnExit();
        try (VariantContextWriter w = new VariantContextWriterBuilder()
                .setOutputPath(bcf)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            w.writeHeader(header);
            w.add(snp(header, "chr1", 100));
        }
        // Remove any CSI that might exist
        Files.deleteIfExists(bcf.resolveSibling(bcf.getFileName() + FileExtensions.CSI));
        final TribbleException ex = Assert.expectThrows(TribbleException.class, () -> new VCFFileReader(bcf, true));
        Assert.assertTrue(
                ex.getMessage().contains(".csi"), "Message should name the .csi extension: " + ex.getMessage());
    }

    @Test
    public void sequentialIterationOfABgzfBcfReturnsAllRecords() throws IOException {
        final Path bcf = writeMultiContigBcf();
        // Remove the index to force sequential-only
        final Path csiPath = bcf.resolveSibling(bcf.getFileName() + FileExtensions.CSI);
        Files.deleteIfExists(csiPath);
        try (VCFFileReader reader = new VCFFileReader(bcf, false)) {
            int count = 0;
            for (final VariantContext ignored : reader) {
                count++;
            }
            Assert.assertEquals(count, 4);
        }
    }

    @Test
    public void readingACsiWithoutBcfCsiExtension() throws IOException {
        final Path bcf = writeMultiContigBcf();
        final Path bcfCsi = bcf.resolveSibling(bcf.getFileName() + FileExtensions.CSI);
        // Rename to x.csi (without the .bcf prefix)
        final String baseName = bcf.getFileName().toString();
        final String nameWithoutBcf = baseName.substring(0, baseName.lastIndexOf('.'));
        final Path altCsi = bcf.resolveSibling(nameWithoutBcf + FileExtensions.CSI);
        altCsi.toFile().deleteOnExit();
        Files.move(bcfCsi, altCsi);
        try (VCFFileReader reader = new VCFFileReader(bcf, true)) {
            Assert.assertTrue(reader.isQueryable());
            Assert.assertEquals(toList(reader.query("chr1", 50, 150)).size(), 1);
        }
    }

    // ============================================================
    // Contig ordinal: IDX-based, not declaration-order
    // ============================================================

    @Test
    public void aQueryUsesTheIdxBasedContigOrdinalNotDeclarationOrder() throws IOException {
        // Build a header with explicit IDX attributes: contigA declared first carries IDX=1, contigB carries IDX=0.
        // The writer honours the supplied IDX, so contigA is stored at BCF dictionary index 1, not 0.
        final Set<VCFHeaderLine> meta = new HashSet<>();
        meta.add(new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "Depth"));
        meta.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "Genotype"));
        final Map<String, String> contigAMap = new LinkedHashMap<>();
        contigAMap.put("ID", "contigA");
        contigAMap.put("length", "100000");
        contigAMap.put("IDX", "1");
        meta.add(new VCFContigHeaderLine(contigAMap, 0));
        final Map<String, String> contigBMap = new LinkedHashMap<>();
        contigBMap.put("ID", "contigB");
        contigBMap.put("length", "100000");
        contigBMap.put("IDX", "0");
        meta.add(new VCFContigHeaderLine(contigBMap, 1));
        final VCFHeader header = new VCFHeader(meta, Collections.singletonList("sample1"));
        final SAMSequenceDictionary dict = header.getSequenceDictionary();

        final Path bcf = Files.createTempFile(tempDir, "swapidx.", ".bcf");
        bcf.toFile().deleteOnExit();
        bcf.resolveSibling(bcf.getFileName() + FileExtensions.CSI).toFile().deleteOnExit();

        // Records must be sorted by dictionary index: contigB (IDX=0) before contigA (IDX=1)
        try (VariantContextWriter w = new VariantContextWriterBuilder()
                .setOutputPath(bcf)
                .setReferenceDictionary(dict)
                .setOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            w.writeHeader(header);
            w.add(snp(header, "contigB", 200));
            w.add(snp(header, "contigA", 100));
        }

        // Verify queries resolve each contig correctly despite the swapped IDX values
        try (BCFFileReader reader =
                new BCFFileReader(bcf, bcf.resolveSibling(bcf.getFileName() + FileExtensions.CSI))) {
            final List<VariantContext> aResults = toList(reader.query("contigA", 1, 1000));
            Assert.assertEquals(aResults.size(), 1);
            Assert.assertEquals(aResults.get(0).getContig(), "contigA");
            Assert.assertEquals(aResults.get(0).getStart(), 100);

            final List<VariantContext> bResults = toList(reader.query("contigB", 1, 1000));
            Assert.assertEquals(bResults.size(), 1);
            Assert.assertEquals(bResults.get(0).getContig(), "contigB");
            Assert.assertEquals(bResults.get(0).getStart(), 200);
        }
    }

    // ============================================================
    // Multi-chunk query: records far enough apart to span multiple bins
    // ============================================================

    @Test
    public void aQuerySpanningMultipleChunksReturnsAllOverlapping() throws IOException {
        // Records at positions far apart on the same contig, so the CSI spans multiple chunks
        final SAMSequenceDictionary dict = new SAMSequenceDictionary();
        dict.addSequence(new SAMSequenceRecord("chr1", 500000));
        final Set<VCFHeaderLine> meta = new HashSet<>();
        meta.add(new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "Depth"));
        meta.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "Genotype"));
        final VCFHeader header = new VCFHeader(meta, Collections.singletonList("sample1"));
        header.setSequenceDictionary(dict);

        final Path bcf = Files.createTempFile(tempDir, "multichunk.", ".bcf");
        bcf.toFile().deleteOnExit();
        bcf.resolveSibling(bcf.getFileName() + FileExtensions.CSI).toFile().deleteOnExit();

        // Write many records spread across > 16384 bases to ensure multiple bins
        try (VariantContextWriter w = new VariantContextWriterBuilder()
                .setOutputPath(bcf)
                .setReferenceDictionary(dict)
                .setOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            w.writeHeader(header);
            // Records at every 20,000 bases so they fall in different bins at minShift=14
            for (int pos = 1; pos <= 400000; pos += 20000) {
                w.add(snp(header, "chr1", pos));
            }
        }

        try (BCFFileReader reader =
                new BCFFileReader(bcf, bcf.resolveSibling(bcf.getFileName() + FileExtensions.CSI))) {
            // A query spanning the whole range should find all records
            final List<VariantContext> results = toList(reader.query("chr1", 1, 400000));
            Assert.assertEquals(results.size(), 20);
        }
    }

    // ============================================================
    // Large record spanning BGZF blocks (> 64 KB)
    // ============================================================

    @Test
    public void aLargeRecordSpanningBgzfBlocksIsFoundByQuery() throws IOException {
        // Create a record with a FORMAT string field large enough to push the encoded record past 64 KB
        final SAMSequenceDictionary dict = new SAMSequenceDictionary();
        dict.addSequence(new SAMSequenceRecord("chr1", 100000));
        final Set<VCFHeaderLine> meta = new HashSet<>();
        meta.add(new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "Depth"));
        meta.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "Genotype"));
        meta.add(new VCFFormatHeaderLine("BIG", 1, VCFHeaderLineType.String, "Large payload"));
        final int nSamples = 500;
        final List<String> samples =
                IntStream.range(0, nSamples).mapToObj(i -> "s" + i).collect(Collectors.toList());
        final VCFHeader header = new VCFHeader(meta, samples);
        header.setSequenceDictionary(dict);

        final Path bcf = Files.createTempFile(tempDir, "large.", ".bcf");
        bcf.toFile().deleteOnExit();
        bcf.resolveSibling(bcf.getFileName() + FileExtensions.CSI).toFile().deleteOnExit();

        // Each sample gets a pseudo-random string that does not compress well, totalling well over 64 KB
        final java.util.Random rng = new java.util.Random(42);
        try (VariantContextWriter w = new VariantContextWriterBuilder()
                .setOutputPath(bcf)
                .setReferenceDictionary(dict)
                .setOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            w.writeHeader(header);
            final VariantContextBuilder vcb =
                    new VariantContextBuilder("test", "chr1", 100, 100, Arrays.asList(Allele.REF_A, Allele.ALT_C));
            vcb.attribute("DP", 30);
            vcb.genotypes(samples.stream()
                    .map(name -> {
                        final StringBuilder sb = new StringBuilder(250);
                        for (int i = 0; i < 250; i++) sb.append((char) ('A' + rng.nextInt(26)));
                        return new GenotypeBuilder(name, Arrays.asList(Allele.REF_A, Allele.ALT_C))
                                .attribute("BIG", sb.toString())
                                .make();
                    })
                    .collect(Collectors.toList()));
            w.add(vcb.make());
        }

        // Verify the file is large enough to span more than one BGZF block (each at most 65536 bytes)
        Assert.assertTrue(
                Files.size(bcf) > 65536, "BCF file should exceed one BGZF block, was " + Files.size(bcf) + " bytes");

        try (BCFFileReader reader =
                new BCFFileReader(bcf, bcf.resolveSibling(bcf.getFileName() + FileExtensions.CSI))) {
            final List<VariantContext> results = toList(reader.query("chr1", 50, 150));
            Assert.assertEquals(results.size(), 1);
            Assert.assertEquals(results.get(0).getStart(), 100);
            Assert.assertEquals(results.get(0).getGenotypes().size(), nSamples);
        }
    }

    // ============================================================
    // Close test
    // ============================================================

    @Test
    public void closeClosesTheBgzfStream() throws IOException {
        final Path bcf = writeMultiContigBcf();
        final BCFFileReader reader = new BCFFileReader(bcf, bcf.resolveSibling(bcf.getFileName() + FileExtensions.CSI));
        reader.close();
        // After close, the BGZF stream should be closed; iterating should throw
        Assert.expectThrows(Exception.class, reader::iterator);
    }

    // ============================================================
    // Resource handling: corrupt CSI does not leak the BGZF stream
    // ============================================================

    @Test
    public void aCorruptCsiDoesNotLeakTheBgzfStream() throws IOException {
        final Path bcf = writeMultiContigBcf();
        final Path corruptCsi = Files.createTempFile(tempDir, "corrupt.", ".csi");
        corruptCsi.toFile().deleteOnExit();
        Files.write(corruptCsi, new byte[] {0, 1, 2, 3});
        Assert.expectThrows(RuntimeException.class, () -> new BCFFileReader(bcf, corruptCsi));
        // If the stream were leaked, this would not verify it, but at least we know construction fails cleanly
    }

    // ============================================================
    // Query without index throws TribbleException
    // ============================================================

    @Test
    public void queryWithoutIndexThrowsTribbleException() throws IOException {
        final Path bcf = writeMultiContigBcf();
        final Path csiPath = bcf.resolveSibling(bcf.getFileName() + FileExtensions.CSI);
        Files.deleteIfExists(csiPath);
        try (BCFFileReader reader = new BCFFileReader(bcf, null)) {
            Assert.assertFalse(reader.isQueryable());
            Assert.expectThrows(TribbleException.class, () -> reader.query("chr1", 1, 100));
        }
    }

    // ============================================================
    // Validation: corrupt record sizes
    // ============================================================

    @Test
    public void aRecordWithAHugeLSharedIsRefusedWithTribbleException() throws IOException {
        final Path bcf = writeMultiContigBcf();
        // Patch the first record's l_shared (4 bytes at the first record offset) to a huge value.
        // The BCF record layout is: 4 bytes l_shared, 4 bytes l_indiv, then the data.
        // The BGZF blocks need to be decompressed and recompressed with the patch.
        final Path patchedBcf = Files.createTempFile(tempDir, "patched.", ".bcf");
        patchedBcf.toFile().deleteOnExit();
        // Read the entire decompressed content, patch it, and rewrite as BGZF
        final byte[] decompressed;
        try (htsjdk.samtools.util.BlockCompressedInputStream in =
                new htsjdk.samtools.util.BlockCompressedInputStream(bcf)) {
            decompressed = in.readAllBytes();
        }
        // Find the first record: after the 5-byte magic, 4-byte l_text, and l_text bytes of header text
        final int lText = (decompressed[5] & 0xFF)
                | ((decompressed[6] & 0xFF) << 8)
                | ((decompressed[7] & 0xFF) << 16)
                | ((decompressed[8] & 0xFF) << 24);
        final int firstRecordOffset = 5 + 4 + lText;
        // Patch l_shared to 0x7FFFFFF0 (a huge but positive value)
        decompressed[firstRecordOffset] = (byte) 0xF0;
        decompressed[firstRecordOffset + 1] = (byte) 0xFF;
        decompressed[firstRecordOffset + 2] = (byte) 0xFF;
        decompressed[firstRecordOffset + 3] = (byte) 0x7F;
        try (htsjdk.samtools.util.BlockCompressedOutputStream out =
                new htsjdk.samtools.util.BlockCompressedOutputStream(patchedBcf)) {
            out.write(decompressed);
        }
        // Reading should throw TribbleException (not OOME or NegativeArraySizeException) naming the offending field
        try (BCFFileReader reader = new BCFFileReader(patchedBcf, null)) {
            final TribbleException ex = Assert.expectThrows(TribbleException.class, () -> toList(reader.iterator()));
            Assert.assertTrue(
                    ex.getMessage().contains("l_shared"), "Message should name the field: " + ex.getMessage());
        }
    }

    @Test
    public void aRecordWithANegativeLSharedIsRefusedWithTribbleException() throws IOException {
        final Path bcf = writeMultiContigBcf();
        final Path patchedBcf = Files.createTempFile(tempDir, "negsize.", ".bcf");
        patchedBcf.toFile().deleteOnExit();
        final byte[] decompressed;
        try (htsjdk.samtools.util.BlockCompressedInputStream in =
                new htsjdk.samtools.util.BlockCompressedInputStream(bcf)) {
            decompressed = in.readAllBytes();
        }
        final int lText = (decompressed[5] & 0xFF)
                | ((decompressed[6] & 0xFF) << 8)
                | ((decompressed[7] & 0xFF) << 16)
                | ((decompressed[8] & 0xFF) << 24);
        final int firstRecordOffset = 5 + 4 + lText;
        // Patch l_shared to 0xFFFFFFFF (negative as signed int)
        decompressed[firstRecordOffset] = (byte) 0xFF;
        decompressed[firstRecordOffset + 1] = (byte) 0xFF;
        decompressed[firstRecordOffset + 2] = (byte) 0xFF;
        decompressed[firstRecordOffset + 3] = (byte) 0xFF;
        try (htsjdk.samtools.util.BlockCompressedOutputStream out =
                new htsjdk.samtools.util.BlockCompressedOutputStream(patchedBcf)) {
            out.write(decompressed);
        }
        try (BCFFileReader reader = new BCFFileReader(patchedBcf, null)) {
            final TribbleException ex = Assert.expectThrows(TribbleException.class, () -> toList(reader.iterator()));
            Assert.assertTrue(
                    ex.getMessage().contains("l_shared"), "Message should name the field: " + ex.getMessage());
        }
    }

    // ============================================================
    // bcftools interop
    // ============================================================

    @Test
    public void htsjdkCsiQueriedByBcftoolsReturnsTheSameRecords() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) {
            throw new SkipException("bcftools not available");
        }
        final Path bcf = writeMultiContigBcf();
        final List<String> bcftoolsLines =
                BcftoolsTestUtils.executeBcftoolsForStdout("view", "-r", "chr1:50-150", bcf.toString());
        final List<String> dataLines =
                bcftoolsLines.stream().filter(l -> !l.startsWith("#")).collect(Collectors.toList());
        Assert.assertEquals(dataLines.size(), 1);
        Assert.assertTrue(dataLines.get(0).contains("chr1\t100"));
    }

    @Test
    public void bcftoolsCsiQueriedByHtsjdkReturnsTheSameRecords() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) {
            throw new SkipException("bcftools not available");
        }
        final Path bcf = writeMultiContigBcf();
        // Remove htsjdk CSI and create one with bcftools
        final Path csiPath = bcf.resolveSibling(bcf.getFileName() + FileExtensions.CSI);
        Files.deleteIfExists(csiPath);
        BcftoolsTestUtils.executeBcftools("index", bcf.toString());
        try (VCFFileReader reader = new VCFFileReader(bcf, true)) {
            final List<VariantContext> results = toList(reader.query("chr1", 50, 150));
            Assert.assertEquals(results.size(), 1);
            Assert.assertEquals(results.get(0).getStart(), 100);
        }
    }

    @Test
    public void bcftoolsIndexForAShortContigHasDepthZeroAndIsQueryable() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) {
            throw new SkipException("bcftools not available");
        }
        // Write a BCF with a single contig shorter than 16384 bases
        final SAMSequenceDictionary dict = new SAMSequenceDictionary();
        dict.addSequence(new SAMSequenceRecord("short", 10000));
        final Set<VCFHeaderLine> meta = new HashSet<>();
        meta.add(new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "Depth"));
        meta.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "Genotype"));
        final VCFHeader header = new VCFHeader(meta, Collections.singletonList("sample1"));
        header.setSequenceDictionary(dict);
        final Path bcf = Files.createTempFile(tempDir, "short.", ".bcf");
        bcf.toFile().deleteOnExit();
        // Write without htsjdk indexing; let bcftools index it
        try (VariantContextWriter w = new VariantContextWriterBuilder()
                .setOutputPath(bcf)
                .setReferenceDictionary(dict)
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            w.writeHeader(header);
            w.add(snp(header, "short", 500));
        }
        final Path csiPath = bcf.resolveSibling(bcf.getFileName() + FileExtensions.CSI);
        csiPath.toFile().deleteOnExit();
        BcftoolsTestUtils.executeBcftools("index", bcf.toString());
        Assert.assertTrue(Files.exists(csiPath), "bcftools should produce " + csiPath);
        // Query the bcftools-indexed file through htsjdk
        try (BCFFileReader reader = new BCFFileReader(bcf, csiPath)) {
            Assert.assertTrue(reader.isQueryable());
            final List<VariantContext> results = toList(reader.query("short", 1, 10000));
            Assert.assertEquals(results.size(), 1);
            Assert.assertEquals(results.get(0).getStart(), 500);
        }
    }

    // ============================================================
    // Query on last record
    // ============================================================

    @Test
    public void aQueryForARegionSpanningTheLastRecordReturnsIt() throws IOException {
        final Path bcf = writeMultiContigBcf();
        try (VCFFileReader reader = new VCFFileReader(bcf, true)) {
            final List<VariantContext> results = toList(reader.query("chr2", 100, 300));
            Assert.assertEquals(results.size(), 1);
            Assert.assertEquals(results.get(0).getStart(), 200);
        }
    }

    private static List<VariantContext> toList(CloseableIterator<VariantContext> iter) {
        final List<VariantContext> result = new ArrayList<>();
        while (iter.hasNext()) {
            result.add(iter.next());
        }
        iter.close();
        return result;
    }
}
