package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.beta.plugin.IOUtils;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.utils.SamtoolsTestUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.Assert;

public class CRAM31Tests extends HtsjdkTest {
    private static final String TEST_DATA_DIR = "src/test/resources/htsjdk/samtools/cram/";

    // use samtools to convert to CRAM3.1 using samtools profiles fast, normal, small, and archive, then
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

    //because conversions are expensive, this tests both htsjdk cram 31.1 reading and writing together
    @Test(dataProvider = "cram31FidelityTests")
    public void testCRAM31SamtoolsFidelity(
            final IOPath testInput,
            final IOPath testReference,
            final String samtoolsCommandLineArgs) throws IOException {

        // for testing CRAM 3.1, we use CRAM 3.0 as our ground-truth for comparison, so check to make
        // sure that our input test file is CRAM 3.0 and not 3.1
        Assert.assertEquals(getCRAMVersion(testInput), CramVersions.CRAM_v3);

        // use samtools to convert the input to CRAM 3.1, then compare the result of that with the original
        final IOPath cram31Path = IOUtils.createTempPath("cram31Test", "cram");

        // test htsjdk cram 3.1 reading by using samtools to convert the input to CRAM 3.1, and then consuming it
        // with htsjdk and comparing the results to the original
        final IOPath cramSamtools31Path = IOUtils.createTempPath("cram31SamtoolsWriteTest", ".cram");
        SamtoolsTestUtils.convertToCRAM(
                testInput,
                cramSamtools31Path,
                testReference,
                samtoolsCommandLineArgs);
        Assert.assertEquals(getCRAMVersion(cramSamtools31Path), CramVersions.CRAM_v3_1);

        // compare the original CRAM 3.0 test input with the samtools-generated 3.1 output
        doCRAMCompare(testInput.toPath(), cramSamtools31Path.toPath(), testReference.toPath());

        // now test htsjdk CRAM 3.1 writing by using htsjdk to write CRAM 3.1 and use samtools to consume it and write
        // it back to another CRAM (3.1), and then read that result back in and compare it to the original
        final IOPath cramHtsjdk31Path = IOUtils.createTempPath("cram31HtsjdkWriteTest", ".cram");
        final SamReaderFactory samReaderFactory =
                SamReaderFactory.makeDefault()
                        .referenceSequence(testReference.toPath())
                        .validationStringency(ValidationStringency.LENIENT);
        try (final SamReader reader = samReaderFactory.open(testInput.toPath());
             final SAMFileWriter writer = new SAMFileWriterFactory()
                     .makeWriter(reader.getFileHeader().clone(), true, cramHtsjdk31Path.toPath(), testReference.toPath())) {
            for (final SAMRecord rec : reader) {
                writer.addAlignment(rec);
            }
        }
        Assert.assertEquals(getCRAMVersion(cramHtsjdk31Path), CramVersions.CRAM_v3_1);

        // compare the original test input with the htsjdk-generated 3.1 output
        doCRAMCompare(testInput.toPath(), cramHtsjdk31Path.toPath(), testReference.toPath());

        //  for completeness, compare the htsjdk-generated cram 3.1 output with the samtools-generated 3.1 output
        doCRAMCompare(cramHtsjdk31Path.toPath(), cramSamtools31Path.toPath(), testReference.toPath());
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

    private static CRAMVersion getCRAMVersion(final IOPath cramPath) {
        try (final InputStream fis = Files.newInputStream(cramPath.toPath())) {
            final CramHeader cramHeader = CramIO.readCramHeader(fis);
            return cramHeader.getCRAMVersion();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

}
