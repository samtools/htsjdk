package htsjdk.variant.vcf;

import htsjdk.samtools.util.GzipTestStreams;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.readers.LineIteratorImpl;
import htsjdk.tribble.readers.SynchronousLineReader;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.LazyGenotypesContext;
import htsjdk.variant.variantcontext.VariantContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class AbstractVCFCodecTest extends VariantBaseTest {

    @Test
    public void shouldPreserveSymbolicAlleleCase() {
        final VariantContext variant;
        try (final VCFFileReader reader =
                new VCFFileReader(Path.of(VariantBaseTest.variantTestDataRoot + "breakpoint.vcf"), false)) {
            variant = reader.iterator().next();
        }
        // VCF v4.1 s1.4.5
        // Tools processing VCF files are not required to preserve case in the allele String, except for IDs, which are
        // case sensitive.
        Assert.assertTrue(variant.getAlternateAllele(0).getDisplayString().contains("chr12"));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void TestSpanDelParseAlleles() {
        final List<Allele> list = VCF3Codec.parseAlleles("A", Allele.SPAN_DEL_STRING, 0);
    }

    @SuppressWarnings("deprecation")
    @Test(expectedExceptions = TribbleException.class)
    public void TestSpanDelParseAllelesException() {
        final List<Allele> list1 = VCF3Codec.parseAlleles(Allele.SPAN_DEL_STRING, "A", 0);
    }

    @DataProvider(name = "thingsToTryToDecode")
    public Object[][] getThingsToTryToDecode() {
        return new Object[][] {
            {"src/test/resources/htsjdk/tribble/tabix/testTabixIndex.vcf", true},
            {"src/test/resources/htsjdk/tribble/tabix/testTabixIndex.vcf.gz", true},
            {"src/test/resources/htsjdk/tribble/nonexistant.garbage", false},
            {"src/test/resources/htsjdk/tribble/testIntervalList.list", false}
        };
    }

    @Test(dataProvider = "thingsToTryToDecode")
    public void testCanDecodeFile(String potentialInput, boolean canDecode) {
        Assert.assertEquals(AbstractVCFCodec.canDecodeFile(potentialInput, VCFCodec.VCF4_MAGIC_HEADER), canDecode);
    }

    @Test
    public void canDecodeFileAcceptsPlainGzippedVcf() throws IOException {
        final Path vcf = Files.createTempFile("AbstractVCFCodecTest.", ".vcf.gz");
        IOUtil.deleteOnExit(vcf);
        Files.write(vcf, GzipTestStreams.multiMemberGzip("##fileformat=VCFv4.2\n"));
        Assert.assertTrue(AbstractVCFCodec.canDecodeFile(vcf.toString(), VCFCodec.VCF4_MAGIC_HEADER));
    }

    @Test
    public void canDecodeFileRejectsGzippedNonVcf() throws IOException {
        final Path notVcf = Files.createTempFile("AbstractVCFCodecTest.", ".txt.gz");
        IOUtil.deleteOnExit(notVcf);
        Files.write(notVcf, GzipTestStreams.multiMemberGzip("this is not a VCF\n"));
        Assert.assertFalse(AbstractVCFCodec.canDecodeFile(notVcf.toString(), VCFCodec.VCF4_MAGIC_HEADER));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetTabixFormat() {
        Assert.assertEquals(new VCFCodec().getTabixFormat(), TabixFormat.VCF);
        Assert.assertEquals(new VCF3Codec().getTabixFormat(), TabixFormat.VCF);
    }

    @Test
    public void testGLnotOverridePL() {
        final VariantContext variant;
        try (final VCFFileReader reader =
                new VCFFileReader(Path.of("src/test/resources/htsjdk/variant/test_withGLandPL.vcf"), false)) {
            variant = reader.iterator().next();
        }
        Assert.assertEquals(variant.getGenotype(0).getPL(), new int[] {45, 0, 50});
    }

    @DataProvider(name = "caseIntolerantDoubles")
    public Object[][] getCaseIntolerantDoubles() {
        return new Object[][] {
            {"src/test/resources/htsjdk/variant/test_withNanQual.vcf", Double.NaN},
            {"src/test/resources/htsjdk/variant/test_withPosInfQual.vcf", Double.POSITIVE_INFINITY},
            {"src/test/resources/htsjdk/variant/test_withNegInfQual.vcf", Double.NEGATIVE_INFINITY},
        };
    }

    @Test(dataProvider = "caseIntolerantDoubles")
    public void testCaseIntolerantDoubles(String vcfInput, double value) {
        try (final VCFFileReader reader = new VCFFileReader(Path.of(vcfInput), false)) {
            try {
                Iterator<VariantContext> iterator = reader.iterator();
                final VariantContext baseVariant = iterator.next(); // First row uses Java-style
                Assert.assertEquals(baseVariant.getPhredScaledQual(), value);
                iterator.forEachRemaining(v -> {
                    Assert.assertEquals(baseVariant.getPhredScaledQual(), v.getPhredScaledQual());
                    Assert.assertEquals(
                            baseVariant.getGenotype(0).getGQ(), v.getGenotype(0).getGQ());
                });
            } catch (TribbleException e) {
                Assert.assertEquals(value, Double.NEGATIVE_INFINITY); // QUAL cannot be negative
            }
        }
    }

    // concurrency: decode on several threads once the header is read, and lazy genotypes on any thread

    private static final int CONCURRENCY_RECORDS = 2000;
    private static final int CONCURRENCY_SAMPLES = 200;
    private static final int CONCURRENCY_THREADS = 8;

    private static final List<String> CONCURRENCY_SAMPLE_NAMES = new ArrayList<>();

    static {
        // in sorted order, or the codec decodes genotypes eagerly to reorder them and nothing here would be lazy
        for (int i = 0; i < CONCURRENCY_SAMPLES; i++) {
            CONCURRENCY_SAMPLE_NAMES.add(String.format("s%03d", i));
        }
    }

    /**
     * Header lines, then {@code records} records. Each genotype's values encode its record and sample index, so
     * genotypes leaking between records or samples show up as wrong values rather than as luck.
     */
    private static String generateVcfWithSelfDescribingGenotypes(final int records) {
        final StringBuilder vcf = new StringBuilder();
        vcf.append("##fileformat=VCFv4.2\n");
        vcf.append("##contig=<ID=chr1,length=100000000>\n");
        vcf.append("##INFO=<ID=DP,Number=1,Type=Integer,Description=\"Depth\">\n");
        vcf.append("##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n");
        vcf.append("##FORMAT=<ID=AD,Number=R,Type=Integer,Description=\"Allele depths\">\n");
        vcf.append("##FORMAT=<ID=DP,Number=1,Type=Integer,Description=\"Depth\">\n");
        vcf.append("##FORMAT=<ID=GQ,Number=1,Type=Integer,Description=\"Genotype quality\">\n");
        vcf.append("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT");
        for (final String sample : CONCURRENCY_SAMPLE_NAMES) {
            vcf.append('\t').append(sample);
        }
        vcf.append('\n');
        for (int r = 0; r < records; r++) {
            vcf.append("chr1\t")
                    .append(100 + r)
                    .append("\t.\tA\tC\t50\tPASS\tDP=")
                    .append(r)
                    .append("\tGT:AD:DP:GQ");
            for (int s = 0; s < CONCURRENCY_SAMPLES; s++) {
                vcf.append('\t')
                        .append(expectedGT(r, s))
                        .append(':')
                        .append(r)
                        .append(',')
                        .append(s);
                vcf.append(':').append(expectedDP(r, s)).append(':').append(expectedGQ(s));
            }
            vcf.append('\n');
        }
        return vcf.toString();
    }

    private static String expectedGT(final int record, final int sample) {
        return (record + sample) % 2 == 0 ? "0/1" : "1/1";
    }

    private static int expectedDP(final int record, final int sample) {
        return record * 1000 + sample;
    }

    private static int expectedGQ(final int sample) {
        return sample % 100;
    }

    /** Decodes every genotype of a record and checks each against the record it claims to belong to. */
    private static void checkSelfDescribingGenotypes(final VariantContext vc) {
        final int record = vc.getAttributeAsInt("DP", -1);
        Assert.assertEquals(vc.getStart(), 100 + record, "record identity");
        final List<Allele> alleles = vc.getAlleles();
        int sample = 0;
        for (final Genotype genotype : vc.getGenotypes()) {
            final String where = "record " + record + " sample " + sample;
            Assert.assertEquals(genotype.getSampleName(), CONCURRENCY_SAMPLE_NAMES.get(sample), where);
            Assert.assertEquals(genotype.getDP(), expectedDP(record, sample), where + " DP");
            Assert.assertEquals(genotype.getGQ(), expectedGQ(sample), where + " GQ");
            Assert.assertEquals(genotype.getAD(), new int[] {record, sample}, where + " AD");
            final List<Allele> expected = expectedGT(record, sample).equals("0/1")
                    ? List.of(alleles.get(0), alleles.get(1))
                    : List.of(alleles.get(1), alleles.get(1));
            Assert.assertEquals(genotype.getAlleles(), expected, where + " GT");
            sample++;
        }
        Assert.assertEquals(sample, CONCURRENCY_SAMPLES, "record " + record + " sample count");
    }

    private static Path writeTempVcf(final String vcf) throws IOException {
        final Path path = Files.createTempFile("AbstractVCFCodecTest", ".vcf");
        path.toFile().deleteOnExit();
        Files.writeString(path, vcf);
        return path;
    }

    private static void awaitAll(final List<Future<?>> futures) throws InterruptedException, ExecutionException {
        for (final Future<?> future : futures) {
            future.get();
        }
    }

    @Test
    public void lazyGenotypesDecodeOnManyThreadsWhileTheReaderAdvances() throws Exception {
        final Path vcf = writeTempVcf(generateVcfWithSelfDescribingGenotypes(CONCURRENCY_RECORDS));
        final ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY_THREADS);
        try (final VCFFileReader reader = new VCFFileReader(vcf, false)) {
            final List<Future<?>> checks = new ArrayList<>();
            for (final VariantContext vc : reader) {
                checks.add(pool.submit(() -> checkSelfDescribingGenotypes(vc)));
            }
            Assert.assertEquals(checks.size(), CONCURRENCY_RECORDS);
            awaitAll(checks);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void decodeRunsConcurrentlyOnOneCodec() throws Exception {
        final String text = generateVcfWithSelfDescribingGenotypes(CONCURRENCY_RECORDS);
        final VCFCodec codec = new VCFCodec();
        final List<String> recordLines = new ArrayList<>();
        try (final LineIteratorImpl lines = new LineIteratorImpl(
                new SynchronousLineReader(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))))) {
            codec.readActualHeader(lines);
            while (lines.hasNext()) {
                recordLines.add(lines.next());
            }
        }
        Assert.assertEquals(recordLines.size(), CONCURRENCY_RECORDS);

        final ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY_THREADS);
        try {
            final List<Future<?>> decodes = new ArrayList<>();
            for (final String line : recordLines) {
                decodes.add(pool.submit(() -> checkSelfDescribingGenotypes(codec.decode(line))));
            }
            awaitAll(decodes);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void lazyDecodeErrorReportsTheRecordsOwnLine() throws IOException {
        // record 2 (file line 10) has a sample with more values than FORMAT keys
        final String[] lines = generateVcfWithSelfDescribingGenotypes(3).split("\n");
        final int recordTwo = lines.length - 2;
        lines[recordTwo] = lines[recordTwo] + ":99";
        final Path vcf = writeTempVcf(String.join("\n", lines) + "\n");

        try (final VCFFileReader reader = new VCFFileReader(vcf, false)) {
            final List<VariantContext> records = reader.iterator().toList();
            Assert.assertEquals(records.size(), 3);
            try {
                records.get(1).getGenotype(CONCURRENCY_SAMPLE_NAMES.get(0));
                Assert.fail("expected the malformed genotype to be rejected");
            } catch (final TribbleException e) {
                Assert.assertTrue(
                        e.getMessage().contains("line number " + (recordTwo + 1) + ":"),
                        "should cite line " + (recordTwo + 1) + " but was: " + e.getMessage());
                Assert.assertTrue(e.getMessage().contains("too many keys"), e.getMessage());
            }
        }
    }

    @Test
    public void internedStringsAreOneInstanceAcrossThreads() throws Exception {
        final Path vcf = writeTempVcf(generateVcfWithSelfDescribingGenotypes(200));
        final ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY_THREADS);
        try (final VCFFileReader reader = new VCFFileReader(vcf, false)) {
            final List<Future<String>> contigs = new ArrayList<>();
            for (final VariantContext vc : reader) {
                contigs.add(pool.submit(vc::getContig));
            }
            final String first = contigs.get(0).get();
            for (final Future<String> contig : contigs) {
                Assert.assertSame(contig.get(), first);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** The one thing that is not thread-safe: reading the header. Pins that decode needs a header first. */
    @Test(expectedExceptions = TribbleException.class)
    public void decodeWithoutAHeaderIsRejected() {
        new VCFCodec().decode("chr1\t100\t.\tA\tC\t50\tPASS\tDP=1");
    }

    /**
     * Each distinct contig is interned once, so the cost of interning must not grow with the number already seen: a
     * call set on a fragmented assembly has hundreds of thousands of contigs. Generous enough for a slow machine, and
     * far short of what a cache that copies itself on every new string needs.
     */
    @Test(timeOut = 20_000)
    public void manyDistinctContigsDoNotSlowDecoding() {
        final String header = "##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n";
        final VCFCodec codec = new VCFCodec();
        codec.readActualHeader(new LineIteratorImpl(
                new SynchronousLineReader(new ByteArrayInputStream(header.getBytes(StandardCharsets.UTF_8)))));
        for (int i = 0; i < 100_000; i++) {
            final String contig = "scaffold_" + i;
            Assert.assertEquals(
                    codec.decode(contig + "\t100\t.\tA\tC\t50\tPASS\t.").getContig(), contig);
        }
    }

    // malformed genotype values are TribbleExceptions naming the sample, key and position

    private static final String ONE_SAMPLE_HEADER = "##fileformat=VCFv4.2\n"
            + "##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n"
            + "##FORMAT=<ID=DP,Number=1,Type=Integer,Description=\"Depth\">\n"
            + "##FORMAT=<ID=GQ,Number=1,Type=Integer,Description=\"Genotype quality\">\n"
            + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tNA1\n";

    private static VariantContext decodeUnderOneSampleHeader(final String record) {
        final VCFCodec codec = new VCFCodec();
        codec.readActualHeader(new LineIteratorImpl(new SynchronousLineReader(
                new ByteArrayInputStream(ONE_SAMPLE_HEADER.getBytes(StandardCharsets.UTF_8)))));
        return codec.decode(record);
    }

    @Test
    public void nonIntegerDpIsATribbleException() {
        final VariantContext vc = decodeUnderOneSampleHeader("chr1\t100\t.\tA\tC\t50\tPASS\t.\tGT:DP\t0/1:1.000");
        try {
            vc.getGenotype("NA1");
            Assert.fail("expected a TribbleException");
        } catch (final TribbleException e) {
            Assert.assertTrue(
                    e.getMessage().contains("Sample NA1 has a non-numeric DP value at chr1:100: 1.000"),
                    e.getMessage());
            Assert.assertTrue(e.getMessage().contains("line number 6"), e.getMessage());
        }
    }

    @Test
    public void nonNumericGqIsATribbleException() {
        final VariantContext vc = decodeUnderOneSampleHeader("chr1\t100\t.\tA\tC\t50\tPASS\t.\tGT:GQ\t0/1:high");
        try {
            vc.getGenotype("NA1");
            Assert.fail("expected a TribbleException");
        } catch (final TribbleException e) {
            Assert.assertTrue(e.getMessage().contains("non-numeric GQ value"), e.getMessage());
        }
    }

    @Test
    public void wellFormedValuesStillDecode() {
        final VariantContext vc = decodeUnderOneSampleHeader("chr1\t100\t.\tA\tC\t50\tPASS\t.\tGT:DP:GQ\t0/1:12:99");
        Assert.assertEquals(vc.getGenotype("NA1").getDP(), 12);
        Assert.assertEquals(vc.getGenotype("NA1").getGQ(), 99);
    }

    // GT phasing: one flag for nearly every GT, a phase per allele for mixed separators or a telling leading indicator

    private static Genotype genotypeOf(final String gt) {
        return decodeUnderOneSampleHeader("chr1\t100\t.\tA\tC,G\t50\tPASS\t.\tGT\t" + gt)
                .getGenotype("NA1");
    }

    @Test
    public void unphasedGtTakesTheSingleFlag() {
        final Genotype g = genotypeOf("0/1");
        Assert.assertFalse(g.isPhased());
        Assert.assertFalse(g.hasPerAllelePhasing());
    }

    @Test
    public void phasedGtTakesTheSingleFlag() {
        final Genotype g = genotypeOf("0|1");
        Assert.assertTrue(g.isPhased());
        Assert.assertFalse(g.hasPerAllelePhasing());
    }

    @Test
    public void aLeadingIndicatorThatTheSeparatorsImplyChangesNothing() {
        Assert.assertFalse(genotypeOf("/0/1").hasPerAllelePhasing());
        Assert.assertFalse(genotypeOf("/0/1").isPhased());
        Assert.assertFalse(genotypeOf("|0|1").hasPerAllelePhasing());
        Assert.assertTrue(genotypeOf("|0|1").isPhased());
        Assert.assertEquals(genotypeOf("|0|1").getPloidy(), 2);
    }

    @Test
    public void aPhasedLeadingIndicatorBeforeAnUnphasedSeparatorIsKept() {
        final Genotype g = genotypeOf("|0/1");
        Assert.assertTrue(g.hasPerAllelePhasing());
        Assert.assertTrue(g.isAllelePhased(0));
        Assert.assertFalse(g.isAllelePhased(1));
        Assert.assertTrue(g.needsLeadingPhaseIndicator());
        Assert.assertTrue(g.isPhased());
        Assert.assertEquals(g.getAlleles().size(), 2);
    }

    @Test
    public void anUnphasedLeadingIndicatorBeforeAPhasedSeparatorIsKept() {
        final Genotype g = genotypeOf("/0|1");
        Assert.assertTrue(g.hasPerAllelePhasing());
        Assert.assertFalse(g.isAllelePhased(0));
        Assert.assertTrue(g.isAllelePhased(1));
        Assert.assertTrue(g.needsLeadingPhaseIndicator());
    }

    @Test
    public void mixedSeparatorsAreKeptPerAllele() {
        final Genotype g = genotypeOf("0/1|2");
        Assert.assertTrue(g.hasPerAllelePhasing());
        Assert.assertFalse(g.isAllelePhased(0), "no leading indicator, and a / elsewhere, so unphased");
        Assert.assertFalse(g.isAllelePhased(1));
        Assert.assertTrue(g.isAllelePhased(2));
        Assert.assertFalse(g.needsLeadingPhaseIndicator());
        Assert.assertTrue(g.isPhased());
    }

    @Test
    public void aTetraploidGtKeepsEachSeparator() {
        Assert.assertEquals(genotypeOf("0/1|2/1").getGenotypeString(), "A/C|G/C");
        Assert.assertEquals(genotypeOf("0|1|2/1").getGenotypeString(), "A|C|G/C");
    }

    @Test
    public void aTellingLeadingIndicatorIsKeptBeforeMixedSeparators() {
        final Genotype g = genotypeOf("|0/1|2");
        Assert.assertTrue(g.needsLeadingPhaseIndicator());
        Assert.assertEquals(g.getGenotypeString(), "|A/C|G");
    }

    @Test
    public void aLeadingIndicatorThatMixedSeparatorsImplyIsDropped() {
        final Genotype g = genotypeOf("/0/1|2");
        Assert.assertTrue(g.hasPerAllelePhasing());
        Assert.assertFalse(g.needsLeadingPhaseIndicator());
        Assert.assertEquals(g.getGenotypeString(), "A/C|G");
    }

    @Test
    public void aGtWithMoreSeparatorsThanAllelesNeedTakesTheSingleFlag() {
        for (final String malformed : new String[] {"0|/1", "0|1/", "0/|1"}) {
            final Genotype g = genotypeOf(malformed);
            Assert.assertTrue(g.isPhased(), malformed);
            Assert.assertFalse(g.hasPerAllelePhasing(), malformed);
            Assert.assertEquals(g.getGenotypeString(), "A|C", malformed);
        }
    }

    @Test
    public void aHaploidGtWithoutAnIndicatorStaysUnphased() {
        final Genotype g = genotypeOf("1");
        Assert.assertFalse(g.isPhased());
        Assert.assertFalse(g.hasPerAllelePhasing());
    }

    @Test
    public void anExplicitlyUnphasedHaploidGtIsKept() {
        final Genotype g = genotypeOf("/1");
        Assert.assertTrue(g.hasPerAllelePhasing());
        Assert.assertFalse(g.isAllelePhased(0));
        Assert.assertTrue(g.needsLeadingPhaseIndicator());
        Assert.assertEquals(g.getPloidy(), 1);
    }

    @Test
    public void anExplicitlyPhasedHaploidGtTakesTheSingleFlag() {
        final Genotype g = genotypeOf("|1");
        Assert.assertTrue(g.isPhased());
        Assert.assertFalse(g.hasPerAllelePhasing());
        Assert.assertEquals(g.getPloidy(), 1);
    }

    @Test
    public void noCallsCarryPhasingLikeAnyOtherAllele() {
        Assert.assertFalse(genotypeOf(".").isPhased());
        Assert.assertFalse(genotypeOf("./.").isPhased());
        Assert.assertTrue(genotypeOf(".|.").isPhased());
        Assert.assertTrue(genotypeOf("|.").isPhased());
        Assert.assertFalse(genotypeOf("|.").hasPerAllelePhasing());
    }

    // the end of a record: the furthest of the REF allele, INFO END, SVLEN for the alleles it applies to, FORMAT LEN

    private static final String END_FIELDS_HEADER = "##fileformat=VCFv4.2\n"
            + "##INFO=<ID=END,Number=1,Type=Integer,Description=\"End\">\n"
            + "##INFO=<ID=SVLEN,Number=A,Type=Integer,Description=\"SV length\">\n"
            + "##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n"
            + "##FORMAT=<ID=LEN,Number=1,Type=Integer,Description=\"Reference block length\">\n"
            + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tNA1\n";

    /** The end of a record at position 100 with the given REF, ALT, INFO and, as GT:LEN, sample columns. */
    private static int endOf(final String ref, final String alt, final String info, final String sample) {
        final VCFCodec codec = new VCFCodec();
        codec.readActualHeader(new LineIteratorImpl(new SynchronousLineReader(
                new ByteArrayInputStream(END_FIELDS_HEADER.getBytes(StandardCharsets.UTF_8)))));
        return codec.decode("chr1\t100\t.\t" + ref + "\t" + alt + "\t.\t.\t" + info + "\tGT:LEN\t" + sample)
                .getEnd();
    }

    @Test
    public void theEndIsTheLastBaseOfTheReferenceAllele() {
        Assert.assertEquals(endOf("A", "C", ".", "0/1:."), 100);
        Assert.assertEquals(endOf("ACG", "A", ".", "0/1:."), 102);
    }

    @Test
    public void anInfoEndBeyondTheReferenceAlleleExtendsTheRecord() {
        Assert.assertEquals(endOf("A", "<*>", "END=150", "0/0:."), 150);
    }

    @Test
    public void anInfoEndShortOfTheReferenceAlleleIsOutdone() {
        Assert.assertEquals(endOf("ACGT", "A", "END=101", "0/1:."), 103);
    }

    @Test
    public void anInfoEndBeforePosOrMissingIsIgnored() {
        Assert.assertEquals(endOf("A", "C", "END=-1", "0/1:."), 100);
        Assert.assertEquals(endOf("A", "C", "END=99", "0/1:."), 100);
        Assert.assertEquals(endOf("A", "C", "END=100", "0/1:."), 100);
        Assert.assertEquals(endOf("A", "C", "END=.", "0/1:."), 100);
    }

    @Test
    public void aLengthTooLongForAnIntIsClamped() {
        Assert.assertEquals(endOf("A", "<DEL>", "SVLEN=2147483647", "0/1:."), Integer.MAX_VALUE);
        Assert.assertEquals(endOf("A", "<DEL>", "SVLEN=-9223372036854775808", "0/1:."), Integer.MAX_VALUE);
        Assert.assertEquals(endOf("A", "<DEL>", "SVLEN=9223372036854775807", "0/1:."), Integer.MAX_VALUE);
        Assert.assertEquals(endOf("A", "<*>", ".", "0/0:2147483647"), Integer.MAX_VALUE);
        Assert.assertEquals(endOf("A", "<*>", ".", "0/0:9223372036854775807"), Integer.MAX_VALUE);
    }

    @Test(expectedExceptions = TribbleException.class)
    public void anInfoEndThatIsNotANumberIsRejected() {
        endOf("A", "C", "END=soon", "0/1:.");
    }

    @Test
    public void svlenExtendsADeletionDuplicationCopyNumberVariantOrInversion() {
        Assert.assertEquals(endOf("A", "<DEL>", "SVLEN=-500", "0/1:."), 600);
        Assert.assertEquals(endOf("A", "<DEL:ME:ALU>", "SVLEN=300", "0/1:."), 400);
        Assert.assertEquals(endOf("A", "<DUP:TANDEM>", "SVLEN=300", "0/1:."), 400);
        Assert.assertEquals(endOf("A", "<CNV:TR>", "SVLEN=30", "0/1:."), 130);
        Assert.assertEquals(endOf("A", "<INV>", "SVLEN=50", "0/1:."), 150);
    }

    @Test
    public void svlenDoesNotExtendAnInsertionOrAnyOtherAllele() {
        Assert.assertEquals(endOf("A", "<INS>", "SVLEN=100", "0/1:."), 100);
        Assert.assertEquals(endOf("A", "<INS:ME>", "SVLEN=100", "0/1:."), 100);
        Assert.assertEquals(endOf("A", "<DELME>", "SVLEN=100", "0/1:."), 100);
        Assert.assertEquals(endOf("A", "ACCCC", "SVLEN=100", "0/1:."), 100);
    }

    @Test
    public void svlenIsMatchedToItsOwnAllele() {
        Assert.assertEquals(endOf("A", "<INS>,<DEL>", "SVLEN=1000,-20", "1/2:."), 120);
        Assert.assertEquals(endOf("A", "<DEL>,<DEL:ME>", "SVLEN=20,40", "1/2:."), 140);
        Assert.assertEquals(endOf("A", "<DEL>,<INS>", "SVLEN=20", "1/2:."), 120, "one value, first allele's");
        Assert.assertEquals(endOf("A", "<INS>,<DEL>", "SVLEN=1000", "1/2:."), 100, "one value, not the DEL's");
    }

    @Test
    public void aDeletionAndAReferenceBlockEachExtendTheRecord() {
        Assert.assertEquals(endOf("A", "<DEL>,<*>", "SVLEN=30", "1/2:100"), 199);
        Assert.assertEquals(endOf("A", "<DEL>,<*>", "SVLEN=300", "1/2:100"), 400);
    }

    @Test
    public void theFurthestOfEndAndSvlenWins() {
        Assert.assertEquals(endOf("A", "<DEL>", "END=150;SVLEN=-500", "0/1:."), 600);
        Assert.assertEquals(endOf("A", "<DEL>", "END=700;SVLEN=-500", "0/1:."), 700);
    }

    @Test
    public void anUnreadableSvlenIsIgnored() {
        Assert.assertEquals(endOf("A", "<DEL>", "SVLEN=.", "0/1:."), 100);
        Assert.assertEquals(endOf("A", "<DEL>", "SVLEN=long", "0/1:."), 100);
    }

    @Test
    public void aReferenceBlockWithoutEndTakesItsLengthFromFormatLen() {
        Assert.assertEquals(endOf("A", "<*>", ".", "0/0:14"), 113);
        Assert.assertEquals(endOf("A", "<NON_REF>", ".", "0/0:14"), 113);
        Assert.assertEquals(endOf("A", "<*>", ".", "0/0:."), 100);
    }

    @Test
    public void formatLenIsNotConsultedWhenEndIsPresent() {
        final VCFCodec codec = new VCFCodec();
        codec.readActualHeader(new LineIteratorImpl(new SynchronousLineReader(
                new ByteArrayInputStream(END_FIELDS_HEADER.getBytes(StandardCharsets.UTF_8)))));
        final VariantContext withEnd = codec.decode("chr1\t100\t.\tA\t<*>\t.\t.\tEND=110\tGT:LEN\t0/0:50");
        Assert.assertEquals(withEnd.getEnd(), 110);
        Assert.assertTrue(((LazyGenotypesContext) withEnd.getGenotypes()).isLazyWithData(), "not decoded");
        final VariantContext withoutEnd = codec.decode("chr1\t100\t.\tA\t<*>\t.\t.\t.\tGT:LEN\t0/0:50");
        Assert.assertEquals(withoutEnd.getEnd(), 149);
        Assert.assertFalse(((LazyGenotypesContext) withoutEnd.getGenotypes()).isLazyWithData(), "decoded for LEN");
    }

    @Test
    public void aRecordDecodedForItsLocationAloneGetsItsEndFromFormatLenToo() {
        // decodeLoc is what index building reads with
        final VCFCodec codec = new VCFCodec();
        codec.readActualHeader(new LineIteratorImpl(new SynchronousLineReader(
                new ByteArrayInputStream(END_FIELDS_HEADER.getBytes(StandardCharsets.UTF_8)))));
        Assert.assertEquals(
                codec.decodeLoc("chr1\t100\t.\tA\t<*>\t.\t.\t.\tGT:LEN\t0/0:50").getEnd(), 149);
        Assert.assertEquals(
                codec.decodeLoc("chr1\t100\t.\tA\t<DEL>\t.\t.\tSVLEN=30\tGT:LEN\t0/1:.")
                        .getEnd(),
                130);
    }

    @Test
    public void formatLenIsNotLookedForUnlessTheHeaderDeclaresIt() {
        // the variant records of a gVCF carry <NON_REF> without END: decoding each of them is expensive, for a LEN
        // that is not there
        final VariantContext vc = decodeUnderOneSampleHeader("chr1\t100\t.\tA\tC,<NON_REF>\t.\t.\t.\tGT:DP\t0/1:50");
        Assert.assertEquals(vc.getEnd(), 100);
        Assert.assertTrue(((LazyGenotypesContext) vc.getGenotypes()).isLazyWithData(), "not decoded");
    }

    // Whitespace in INFO (#1667)

    /** Decodes one sites-only record under a header of the given version declaring the String INFO key DESC. */
    private static VariantContext decodeSitesOnlyRecord(final VCFHeaderVersion version, final String record) {
        final String header = "##fileformat=" + version.getVersionString() + "\n"
                + "##INFO=<ID=DESC,Number=1,Type=String,Description=\"desc\">\n"
                + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n";
        final VCFCodec codec = new VCFCodec();
        codec.readActualHeader(new LineIteratorImpl(
                new SynchronousLineReader(new ByteArrayInputStream(header.getBytes(StandardCharsets.UTF_8)))));
        return codec.decode(record);
    }

    @Test
    public void aSpaceInAnInfoValueIsAcceptedAt43() {
        final VariantContext vc =
                decodeSitesOnlyRecord(VCFHeaderVersion.VCF4_3, "chr1\t100\t.\tA\tC\t.\t.\tDESC=hello world");
        Assert.assertEquals(vc.getAttribute("DESC"), "hello world");
    }

    @Test
    public void aSpaceInAnInfoValueIsAcceptedAt42() {
        // 4.2 says "no whitespace", but htslib has never enforced it and files carry spaces
        final VariantContext vc =
                decodeSitesOnlyRecord(VCFHeaderVersion.VCF4_2, "chr1\t100\t.\tA\tC\t.\t.\tDESC=hello world");
        Assert.assertEquals(vc.getAttribute("DESC"), "hello world");
    }

    @Test
    public void aNinthColumnUnderASitesOnlyHeaderIsReportedAsAnExtraColumn() {
        // a tab can only reach the INFO column as an extra column, which is a column-count error, not a value
        final TribbleException refusal = Assert.expectThrows(
                TribbleException.class,
                () -> decodeSitesOnlyRecord(VCFHeaderVersion.VCF4_3, "chr1\t100\t.\tA\tC\t.\t.\tDESC=x\tGT"));
        Assert.assertTrue(refusal.getMessage().contains("columns"), refusal.getMessage());
    }

    // VCF version acceptance: every known version decodes, unknown versions are rejected, 3.x through VCFCodec

    private static VariantContext decodeMinimalFile(final String versionLine) {
        final String vcf = versionLine + "\n"
                + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n"
                + "chr1\t100\t.\tA\tC\t50\tPASS\t.\n";
        final VCFCodec codec = new VCFCodec();
        codec.readActualHeader(new LineIteratorImpl(
                new SynchronousLineReader(new ByteArrayInputStream(vcf.getBytes(StandardCharsets.UTF_8)))));
        return codec.decode("chr1\t100\t.\tA\tC\t50\tPASS\t.");
    }

    @DataProvider(name = "allVersions")
    public Object[][] allVersions() {
        final VCFHeaderVersion[] versions = VCFHeaderVersion.values();
        final Object[][] data = new Object[versions.length][1];
        for (int i = 0; i < versions.length; i++) {
            data[i][0] = versions[i];
        }
        return data;
    }

    @Test(dataProvider = "allVersions")
    public void everyKnownVersionIsAccepted(final VCFHeaderVersion v) {
        final String versionLine = "##" + v.getFormatString() + "=" + v.getVersionString();
        final VariantContext vc = decodeMinimalFile(versionLine);
        Assert.assertNotNull(vc);
        Assert.assertEquals(vc.getContig(), "chr1");
    }

    @Test
    public void vcf32FileIsReadByVCFCodec() {
        final String vcf = "##format=VCRv3.2\n"
                + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n"
                + "chr1\t100\t.\tA\tC\t50\t0\t.\n";
        final VCFCodec codec = new VCFCodec();
        codec.readActualHeader(new LineIteratorImpl(
                new SynchronousLineReader(new ByteArrayInputStream(vcf.getBytes(StandardCharsets.UTF_8)))));
        Assert.assertEquals(codec.getVersion(), VCFHeaderVersion.VCF3_2);
        final VariantContext vc = codec.decode("chr1\t100\t.\tA\tC\t50\t0\t.");
        Assert.assertEquals(vc.getContig(), "chr1");
        Assert.assertEquals(vc.getStart(), 100);
    }

    @Test
    public void vcf33FileIsReadByVCFCodec() {
        final String vcf = "##fileformat=VCFv3.3\n"
                + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n"
                + "chr1\t200\t.\tG\tT\t30\tPASS\t.\n";
        final VCFCodec codec = new VCFCodec();
        codec.readActualHeader(new LineIteratorImpl(
                new SynchronousLineReader(new ByteArrayInputStream(vcf.getBytes(StandardCharsets.UTF_8)))));
        Assert.assertEquals(codec.getVersion(), VCFHeaderVersion.VCF3_3);
        final VariantContext vc = codec.decode("chr1\t200\t.\tG\tT\t30\tPASS\t.");
        Assert.assertEquals(vc.getContig(), "chr1");
        Assert.assertEquals(vc.getStart(), 200);
    }

    @Test
    public void filterZeroIsTreatedAsPassForVcf3() {
        final String vcf = "##fileformat=VCFv3.3\n"
                + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n"
                + "chr1\t100\t.\tA\tC\t50\t0\t.\n";
        final VCFCodec codec = new VCFCodec();
        codec.readActualHeader(new LineIteratorImpl(
                new SynchronousLineReader(new ByteArrayInputStream(vcf.getBytes(StandardCharsets.UTF_8)))));
        final VariantContext vc = codec.decode("chr1\t100\t.\tA\tC\t50\t0\t.");
        Assert.assertTrue(vc.getFilters().isEmpty());
        Assert.assertFalse(vc.isFiltered());
    }

    @Test(expectedExceptions = TribbleException.class)
    public void filterZeroIsRejectedForVcf4() {
        final String vcf = "##fileformat=VCFv4.0\n"
                + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n"
                + "chr1\t100\t.\tA\tC\t50\t0\t.\n";
        final VCFCodec codec = new VCFCodec();
        codec.readActualHeader(new LineIteratorImpl(
                new SynchronousLineReader(new ByteArrayInputStream(vcf.getBytes(StandardCharsets.UTF_8)))));
        codec.decode("chr1\t100\t.\tA\tC\t50\t0\t.");
    }

    @Test(expectedExceptions = TribbleException.InvalidHeader.class)
    public void unknownVersionIsRejected() {
        final String vcf = "##fileformat=VCFv4.6\n" + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n";
        final VCFCodec codec = new VCFCodec();
        codec.readActualHeader(new LineIteratorImpl(
                new SynchronousLineReader(new ByteArrayInputStream(vcf.getBytes(StandardCharsets.UTF_8)))));
    }

    @Test(expectedExceptions = TribbleException.InvalidHeader.class)
    public void unknownMajorVersionIsRejected() {
        final String vcf = "##fileformat=VCFv5.0\n" + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n";
        final VCFCodec codec = new VCFCodec();
        codec.readActualHeader(new LineIteratorImpl(
                new SynchronousLineReader(new ByteArrayInputStream(vcf.getBytes(StandardCharsets.UTF_8)))));
    }

    @Test
    public void aVcf44LeadingPhaseGtDecodesWithoutAnyFlag() {
        final String vcf = "##fileformat=VCFv4.4\n"
                + "##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n"
                + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tNA1\n";
        final VCFCodec codec = new VCFCodec();
        codec.readActualHeader(new LineIteratorImpl(
                new SynchronousLineReader(new ByteArrayInputStream(vcf.getBytes(StandardCharsets.UTF_8)))));
        final VariantContext vc = codec.decode("chr1\t100\t.\tA\tC\t50\tPASS\t.\tGT\t|0/1");
        final Genotype g = vc.getGenotype("NA1");
        Assert.assertTrue(g.hasPerAllelePhasing());
        Assert.assertTrue(g.isAllelePhased(0));
        Assert.assertFalse(g.isAllelePhased(1));
    }

    @Test
    public void aVcf45NumberLAHeaderDecodesWithoutAnyFlag() {
        final String vcf = "##fileformat=VCFv4.5\n"
                + "##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n"
                + "##FORMAT=<ID=LAA,Number=.,Type=Integer,Description=\"Local alternate alleles\">\n"
                + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tNA1\n";
        final VCFCodec codec = new VCFCodec();
        codec.readActualHeader(new LineIteratorImpl(
                new SynchronousLineReader(new ByteArrayInputStream(vcf.getBytes(StandardCharsets.UTF_8)))));
        Assert.assertEquals(codec.getVersion(), VCFHeaderVersion.VCF4_5);
        final VariantContext vc = codec.decode("chr1\t100\t.\tA\tC,G\t50\tPASS\t.\tGT:LAA\t0/1:1");
        Assert.assertEquals(vc.getGenotype("NA1").getExtendedAttribute("LAA"), "1");
    }

    // canDecode: recognises 3.2, 3.3 and 4.x; VCF3Codec claims nothing

    @Test
    public void canDecodeRecognisesAVcf32Header() throws IOException {
        final Path vcf = writeTempVcf("##format=VCRv3.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n");
        Assert.assertTrue(new VCFCodec().canDecode(vcf.toString()));
    }

    @Test
    public void canDecodeRecognisesAVcf33Header() throws IOException {
        final Path vcf = writeTempVcf("##fileformat=VCFv3.3\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n");
        Assert.assertTrue(new VCFCodec().canDecode(vcf.toString()));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void exactlyOneCodecClaimsAVcf33File() throws IOException {
        final Path vcf = writeTempVcf("##fileformat=VCFv3.3\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n");
        Assert.assertTrue(new VCFCodec().canDecode(vcf.toString()));
        Assert.assertFalse(new VCF3Codec().canDecode(vcf.toString()));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void exactlyOneCodecClaimsAVcf43File() throws IOException {
        final Path vcf = writeTempVcf("##fileformat=VCFv4.3\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n");
        Assert.assertTrue(new VCFCodec().canDecode(vcf.toString()));
        Assert.assertFalse(new VCF3Codec().canDecode(vcf.toString()));
    }

    // the deprecated VCF3Codec still works as a codec

    @SuppressWarnings("deprecation")
    @Test
    public void deprecatedVcf3CodecStillReadsVcf33() {
        final String vcf = "##fileformat=VCFv3.3\n"
                + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n"
                + "chr1\t200\t.\tG\tT\t30\tPASS\t.\n";
        final VCF3Codec codec = new VCF3Codec();
        codec.readActualHeader(new LineIteratorImpl(
                new SynchronousLineReader(new ByteArrayInputStream(vcf.getBytes(StandardCharsets.UTF_8)))));
        Assert.assertEquals(codec.getVersion(), VCFHeaderVersion.VCF3_3);
        final VariantContext vc = codec.decode("chr1\t200\t.\tG\tT\t30\tPASS\t.");
        Assert.assertEquals(vc.getContig(), "chr1");
        Assert.assertFalse(vc.isFiltered());
    }

    // FILTER=PASS in VCF 3.x now decodes as passing

    @Test
    public void filterPassIsTreatedAsPassForVcf3() {
        final String vcf = "##fileformat=VCFv3.3\n"
                + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n"
                + "chr1\t100\t.\tA\tC\t50\tPASS\t.\n";
        final VCFCodec codec = new VCFCodec();
        codec.readActualHeader(new LineIteratorImpl(
                new SynchronousLineReader(new ByteArrayInputStream(vcf.getBytes(StandardCharsets.UTF_8)))));
        final VariantContext vc = codec.decode("chr1\t100\t.\tA\tC\t50\tPASS\t.");
        Assert.assertTrue(vc.getFilters().isEmpty());
        Assert.assertFalse(vc.isFiltered());
    }
}
