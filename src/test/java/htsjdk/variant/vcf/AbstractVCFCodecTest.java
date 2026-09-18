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

    @Test
    public void TestSpanDelParseAlleles() {
        final List<Allele> list = VCF3Codec.parseAlleles("A", Allele.SPAN_DEL_STRING, 0);
    }

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
}
