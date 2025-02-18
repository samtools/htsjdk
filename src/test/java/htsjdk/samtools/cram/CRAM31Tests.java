package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.beta.plugin.IOUtils;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

import htsjdk.utils.SamtoolsTestUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.Assert;

public class CRAM31Tests extends HtsjdkTest {
    private static final String TEST_DATA_DIR = "src/test/resources/htsjdk/samtools/cram/";

    // use samtools to convert to CRAM3.1 using samtools profiles fast, normanl, small, and archive, then
    // read with htsjdk and compare the results with the original

    @DataProvider(name="cram31FidelityTests")
    public Object[][] cram31FidelityTests() {
        return new Object[][] {
                // generated with samtools 1.19 from the gatk bam file CEUTrio.HiSeq.WGS.b37.NA12878.20.21.bam
                {
                        new HtsPath(TEST_DATA_DIR + "CEUTrio.HiSeq.WGS.b37.NA12878.20.21.v3.0.samtools.cram"),
                        new HtsPath("src/test/resources/htsjdk/samtools/reference/human_g1k_v37.20.21.fasta.gz"),
                        "--output-fmt cram,version=3.1,fast"
                },
                {
                        new HtsPath(TEST_DATA_DIR + "CEUTrio.HiSeq.WGS.b37.NA12878.20.21.v3.0.samtools.cram"),
                        new HtsPath("src/test/resources/htsjdk/samtools/reference/human_g1k_v37.20.21.fasta.gz"),
                        "--output-fmt cram,version=3.1,normal"
                },
                {
                        new HtsPath(TEST_DATA_DIR + "CEUTrio.HiSeq.WGS.b37.NA12878.20.21.v3.0.samtools.cram"),
                        new HtsPath("src/test/resources/htsjdk/samtools/reference/human_g1k_v37.20.21.fasta.gz"),
                        "--output-fmt cram,version=3.1,small"
                },
                {
                        new HtsPath(TEST_DATA_DIR + "CEUTrio.HiSeq.WGS.b37.NA12878.20.21.v3.0.samtools.cram"),
                        new HtsPath("src/test/resources/htsjdk/samtools/reference/human_g1k_v37.20.21.fasta.gz"),
                        "--output-fmt cram,version=3.1,archive"
                },

                // a test file with artificially small slices and containers to force multiple slices and containers
                {
                        new HtsPath(TEST_DATA_DIR + "NA12878.20.21.1-100.100-SeqsPerSlice.500-unMapped.cram"),
                        new HtsPath(TEST_DATA_DIR + "human_g1k_v37.20.21.1-100.fasta"),
                        "--output-fmt cram,version=3.1,fast"
                },
                {
                        new HtsPath(TEST_DATA_DIR + "NA12878.20.21.1-100.100-SeqsPerSlice.500-unMapped.cram"),
                        new HtsPath(TEST_DATA_DIR + "human_g1k_v37.20.21.1-100.fasta"),
                        "--output-fmt cram,version=3.1,normal"
                },
                {
                        new HtsPath(TEST_DATA_DIR + "NA12878.20.21.1-100.100-SeqsPerSlice.500-unMapped.cram"),
                        new HtsPath(TEST_DATA_DIR + "human_g1k_v37.20.21.1-100.fasta"),
                        "--output-fmt cram,version=3.1,small"
                },
                {
                        new HtsPath(TEST_DATA_DIR + "NA12878.20.21.1-100.100-SeqsPerSlice.500-unMapped.cram"),
                        new HtsPath(TEST_DATA_DIR + "human_g1k_v37.20.21.1-100.fasta"),
                        "--output-fmt cram,version=3.1,archive"
                },

                // a test file with only unmapped reads
                {
                        new HtsPath(TEST_DATA_DIR + "NA12878.unmapped.cram"),
                        new HtsPath(TEST_DATA_DIR + "human_g1k_v37.20.21.1-100.fasta"),
                        "--output-fmt cram,version=3.1,fast"
                },
                {
                        new HtsPath(TEST_DATA_DIR + "NA12878.unmapped.cram"),
                        new HtsPath(TEST_DATA_DIR + "human_g1k_v37.20.21.1-100.fasta"),
                        "--output-fmt cram,version=3.1,normal"
                },
                {
                        new HtsPath(TEST_DATA_DIR + "NA12878.unmapped.cram"),
                        new HtsPath(TEST_DATA_DIR + "human_g1k_v37.20.21.1-100.fasta"),
                        "--output-fmt cram,version=3.1,small"
                },
                {
                        new HtsPath(TEST_DATA_DIR + "NA12878.unmapped.cram"),
                        new HtsPath(TEST_DATA_DIR + "human_g1k_v37.20.21.1-100.fasta"),
                        "--output-fmt cram,version=3.1,archive"
                },
        };
    }

    @Test(dataProvider = "cram31FidelityTests")
    public void testCRAM31RoundTrip(
            final IOPath testInput,
            final IOPath testReference,
            final String samtoolsCommandLineArgs) {
        // use samtools to convert the input to CRAM 3.1
        final IOPath cram31Path = IOUtils.createTempPath("cram31Test", "cram");
        SamtoolsTestUtils.convertToCRAM(
                testInput,
                cram31Path,
                testReference,
                samtoolsCommandLineArgs);
        doCRAMCompare(testInput.toPath(), cram31Path.toPath(), testReference.toPath());
    }

    public static void doCRAMCompare(
            final Path path1,
            final Path path2,
            final Path referencePath
    ) {
        int diffCount = 0;
        try (final SamReader reader1 = SamReaderFactory.makeDefault()
                .referenceSequence(referencePath)
                .validationStringency((ValidationStringency.SILENT))
                .open(path1);
             final SamReader reader2 = SamReaderFactory.makeDefault()
                     .referenceSequence(referencePath)
                     .validationStringency((ValidationStringency.SILENT))
                     .open(path2);
        ) {
            final Iterator<SAMRecord> iterator2 = reader2.iterator();
            for (final SAMRecord rec1 : reader1) {
                final SAMRecord rec2 = iterator2.next();
                if (!rec1.equals(rec2)) {
                    System.err.println(String.format("%s%s", rec1.getReadUnmappedFlag() == true ? "unmapped: " : "", rec1.getSAMString()));
                    System.err.println(String.format("%s%s", rec2.getReadUnmappedFlag() == true ? "unmapped: " : "", rec2.getSAMString()));
                    diffCount++;
                }
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        Assert.assertEquals(diffCount, 0);
    }

}
