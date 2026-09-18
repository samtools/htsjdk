package htsjdk.variant;

import htsjdk.HtsjdkTest;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Keeps the benchmark working: generates tiny inputs and runs every phase over them. */
public class VariantCodecBenchmarkTest extends HtsjdkTest {
    private Path dir;

    @BeforeClass
    public void generateInputs() throws IOException {
        dir = Files.createTempDirectory("VariantCodecBenchmarkTest");
        Assert.assertEquals(
                VariantCodecBenchmark.run(
                        new String[] {"generate", "--dir", dir.toString(), "--records", "400", "--samples", "5"}),
                0);
    }

    @AfterClass
    public void deleteInputs() throws IOException {
        try (final var files = Files.list(dir)) {
            for (final Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
        }
        Files.deleteIfExists(dir);
    }

    @Test
    public void generatedFilesHaveTheRequestedShape() {
        Assert.assertEquals(countRecords(dir.resolve("single.vcf.gz")), 400);
        Assert.assertEquals(countRecords(dir.resolve("wide.vcf.gz")), 4);
        Assert.assertEquals(countRecords(dir.resolve("gvcf.vcf.gz")), 400);
        try (final VCFFileReader reader = new VCFFileReader(dir.resolve("wide.vcf.gz"), false)) {
            Assert.assertEquals(reader.getFileHeader().getNGenotypeSamples(), 5);
        }
    }

    @Test
    public void generationIsDeterministic() throws IOException {
        final Path again = Files.createTempDirectory("VariantCodecBenchmarkTest");
        try {
            VariantCodecBenchmark.generateFile(again.resolve("single.vcf.gz"), 400, 1, false, 42);
            Assert.assertEquals(
                    readRecordLines(again.resolve("single.vcf.gz")), readRecordLines(dir.resolve("single.vcf.gz")));
        } finally {
            Files.deleteIfExists(again.resolve("single.vcf.gz"));
            Files.deleteIfExists(again);
        }
    }

    @Test
    public void everyPhaseRunsOverEveryInput() throws IOException {
        for (final String input : List.of("single.vcf.gz", "wide.vcf.gz", "gvcf.vcf.gz")) {
            final List<VariantCodecBenchmark.Result> results = VariantCodecBenchmark.runPhases(
                    new String[] {"--vcf", dir.resolve(input).toString(), "--warmups", "0", "--iterations", "1"});
            Assert.assertNotNull(results, input);
            Assert.assertEquals(
                    results.stream().map(VariantCodecBenchmark.Result::phase).toList(),
                    VariantCodecBenchmark.ALL_PHASES,
                    input);
            final long expected = countRecords(dir.resolve(input));
            for (final VariantCodecBenchmark.Result result : results) {
                Assert.assertEquals(result.records(), expected, input + " " + result.phase());
                Assert.assertTrue(result.minMillis() >= 0, input + " " + result.phase());
            }
        }
    }

    @Test
    public void maxRecordsCapsEveryPhase() throws IOException {
        final List<VariantCodecBenchmark.Result> results = VariantCodecBenchmark.runPhases(new String[] {
            "--vcf",
            dir.resolve("single.vcf.gz").toString(),
            "--max-records",
            "7",
            "--warmups",
            "0",
            "--iterations",
            "1"
        });
        for (final VariantCodecBenchmark.Result result : results) {
            Assert.assertEquals(result.records(), 7, result.phase());
        }
    }

    @Test
    public void tsvOutputHasOneRowPerPhase() throws IOException {
        final Path tsv = dir.resolve("results.tsv");
        VariantCodecBenchmark.runPhases(new String[] {
            "--vcf",
            dir.resolve("single.vcf.gz").toString(),
            "--phases",
            "vcf-read,vcf-write",
            "--warmups",
            "0",
            "--iterations",
            "1",
            "--label",
            "first",
            "--tsv",
            tsv.toString()
        });
        VariantCodecBenchmark.runPhases(new String[] {
            "--vcf",
            dir.resolve("single.vcf.gz").toString(),
            "--phases",
            "vcf-read",
            "--warmups",
            "0",
            "--iterations",
            "1",
            "--label",
            "second",
            "--tsv",
            tsv.toString()
        });
        final List<String> lines = Files.readAllLines(tsv);
        Assert.assertEquals(lines.get(0), "label\tphase\tmin_ms\tmedian_ms\tmax_ms\trecords");
        Assert.assertEquals(lines.size(), 4);
        Assert.assertTrue(lines.get(1).startsWith("first\tvcf-read\t"), lines.get(1));
        Assert.assertTrue(lines.get(2).startsWith("first\tvcf-write\t"), lines.get(2));
        Assert.assertTrue(lines.get(3).startsWith("second\tvcf-read\t"), lines.get(3));
    }

    /** The writers copy undecoded genotypes through untouched, which would make the write phases measure nothing. */
    @Test
    public void writePhasesStartFromDecodedGenotypes() {
        try (final VCFFileReader reader = new VCFFileReader(dir.resolve("wide.vcf.gz"), false)) {
            final List<VariantContext> records = VariantCodecBenchmark.loadDecoded(reader, Long.MAX_VALUE);
            Assert.assertEquals(records.size(), 4);
            for (final VariantContext vc : records) {
                Assert.assertFalse(vc.getGenotypes().isLazyWithData(), vc.toString());
            }
        }
    }

    @Test
    public void unknownPhaseIsRejected() throws IOException {
        Assert.assertNull(VariantCodecBenchmark.runPhases(
                new String[] {"--vcf", dir.resolve("single.vcf.gz").toString(), "--phases", "vcf-read,nope"}));
    }

    private static long countRecords(final Path vcf) {
        try (final VCFFileReader reader = new VCFFileReader(vcf, false)) {
            return reader.iterator().stream().count();
        }
    }

    private static List<String> readRecordLines(final Path vcf) {
        try (final VCFFileReader reader = new VCFFileReader(vcf, false)) {
            return reader.iterator().stream().map(VariantContext::toString).toList();
        }
    }
}
