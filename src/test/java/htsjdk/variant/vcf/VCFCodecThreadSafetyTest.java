package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.readers.LineIteratorImpl;
import htsjdk.tribble.readers.SynchronousLineReader;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * A codec whose header has been read may decode records on several threads, and the records it returns may decode
 * their genotypes on any thread. The generated file encodes each genotype's record and sample index in its values,
 * so genotypes leaking between records or samples show up as wrong values rather than as luck.
 */
public class VCFCodecThreadSafetyTest extends HtsjdkTest {
    private static final int RECORDS = 2000;
    private static final int SAMPLES = 200;
    private static final int THREADS = 8;

    private static final List<String> SAMPLE_NAMES = new ArrayList<>();

    static {
        // in sorted order, or the codec decodes genotypes eagerly to reorder them and nothing here would be lazy
        for (int i = 0; i < SAMPLES; i++) {
            SAMPLE_NAMES.add(String.format("s%03d", i));
        }
    }

    /** Header lines, then {@code records} records whose genotypes encode their record and sample indices. */
    private static String generateVcf(final int records) {
        final StringBuilder vcf = new StringBuilder();
        vcf.append("##fileformat=VCFv4.2\n");
        vcf.append("##contig=<ID=chr1,length=100000000>\n");
        vcf.append("##INFO=<ID=DP,Number=1,Type=Integer,Description=\"Depth\">\n");
        vcf.append("##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n");
        vcf.append("##FORMAT=<ID=AD,Number=R,Type=Integer,Description=\"Allele depths\">\n");
        vcf.append("##FORMAT=<ID=DP,Number=1,Type=Integer,Description=\"Depth\">\n");
        vcf.append("##FORMAT=<ID=GQ,Number=1,Type=Integer,Description=\"Genotype quality\">\n");
        vcf.append("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT");
        for (final String sample : SAMPLE_NAMES) {
            vcf.append('\t').append(sample);
        }
        vcf.append('\n');
        for (int r = 0; r < records; r++) {
            vcf.append("chr1\t")
                    .append(100 + r)
                    .append("\t.\tA\tC\t50\tPASS\tDP=")
                    .append(r)
                    .append("\tGT:AD:DP:GQ");
            for (int s = 0; s < SAMPLES; s++) {
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
    private static void checkGenotypes(final VariantContext vc) {
        final int record = vc.getAttributeAsInt("DP", -1);
        Assert.assertEquals(vc.getStart(), 100 + record, "record identity");
        final List<Allele> alleles = vc.getAlleles();
        int sample = 0;
        for (final Genotype genotype : vc.getGenotypes()) {
            final String where = "record " + record + " sample " + sample;
            Assert.assertEquals(genotype.getSampleName(), SAMPLE_NAMES.get(sample), where);
            Assert.assertEquals(genotype.getDP(), expectedDP(record, sample), where + " DP");
            Assert.assertEquals(genotype.getGQ(), expectedGQ(sample), where + " GQ");
            Assert.assertEquals(genotype.getAD(), new int[] {record, sample}, where + " AD");
            final List<Allele> expected = expectedGT(record, sample).equals("0/1")
                    ? List.of(alleles.get(0), alleles.get(1))
                    : List.of(alleles.get(1), alleles.get(1));
            Assert.assertEquals(genotype.getAlleles(), expected, where + " GT");
            sample++;
        }
        Assert.assertEquals(sample, SAMPLES, "record " + record + " sample count");
    }

    private static Path writeTempVcf(final String vcf) throws IOException {
        final Path path = Files.createTempFile("VCFCodecThreadSafetyTest", ".vcf");
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
        final Path vcf = writeTempVcf(generateVcf(RECORDS));
        final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try (final VCFFileReader reader = new VCFFileReader(vcf, false)) {
            final List<Future<?>> checks = new ArrayList<>();
            for (final VariantContext vc : reader) {
                checks.add(pool.submit(() -> checkGenotypes(vc)));
            }
            Assert.assertEquals(checks.size(), RECORDS);
            awaitAll(checks);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void decodeRunsConcurrentlyOnOneCodec() throws Exception {
        final String text = generateVcf(RECORDS);
        final VCFCodec codec = new VCFCodec();
        final List<String> recordLines = new ArrayList<>();
        try (final LineIteratorImpl lines = new LineIteratorImpl(
                new SynchronousLineReader(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))))) {
            codec.readActualHeader(lines);
            while (lines.hasNext()) {
                recordLines.add(lines.next());
            }
        }
        Assert.assertEquals(recordLines.size(), RECORDS);

        final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            final List<Future<?>> decodes = new ArrayList<>();
            for (final String line : recordLines) {
                decodes.add(pool.submit(() -> checkGenotypes(codec.decode(line))));
            }
            awaitAll(decodes);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void lazyDecodeErrorReportsTheRecordsOwnLine() throws IOException {
        // record 2 (file line 10) has a sample with more values than FORMAT keys
        final String[] lines = generateVcf(3).split("\n");
        final int recordTwo = lines.length - 2;
        lines[recordTwo] = lines[recordTwo] + ":99";
        final Path vcf = writeTempVcf(String.join("\n", lines) + "\n");

        try (final VCFFileReader reader = new VCFFileReader(vcf, false)) {
            final List<VariantContext> records = reader.iterator().toList();
            Assert.assertEquals(records.size(), 3);
            try {
                records.get(1).getGenotype(SAMPLE_NAMES.get(0));
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
        final Path vcf = writeTempVcf(generateVcf(200));
        final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
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
}
