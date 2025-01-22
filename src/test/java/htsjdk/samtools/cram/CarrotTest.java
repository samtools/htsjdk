package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.beta.plugin.IOUtils;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.utils.SamtoolsTestUtils;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

// A Command line program for large-scale Carrot round-trip CRAM tests.
public class CarrotTest extends HtsjdkTest {
    private static String CONVERT_USAGE = "convert reads_file reference samtools_view_params";
    private static String COMPARE_USAGE = "compare reads_file1 reads_file2 reference";

    // supported CLP commands - the string representation of these values are the actual command strings
    public enum CLPCommand {
        CONVERT,        // convert an input to CRAM 3.1 using samtools
        COMPARE         // compare two crams for fidelity
    };

    @DataProvider(name = "carrotCLPConvertTestCases")
    public Object[][] carrotCLPConvertTestCases() {
        return new Object[][] {
                {
                        "src/test/resources/htsjdk/samtools/cram/CEUTrio.HiSeq.WGS.b37.NA12878.20.21.v3.0.samtools.cram",
                        "src/test/resources/htsjdk/samtools/reference/human_g1k_v37.20.21.fasta.gz",
                        "--output-fmt cram,version=3.1,archive"
                }
        };
    }

    @Test(dataProvider = "carrotCLPConvertTestCases")
    private void testCarrotCLPConvert(
            final String inputCRAM,
            final String referenceFASTA,
            final String samtoolsArgs) {
        final IOPath tempCRAMPath = IOUtils.createTempPath("carrotTemporaryCRAM", FileExtensions.CRAM);
        CarrotTest.main(new String[] {
                CLPCommand.CONVERT.toString(),
                inputCRAM,
                tempCRAMPath.getRawInputString(),
                referenceFASTA,
                samtoolsArgs});
        //TODO: Assert.assertTrue()
    }

    @DataProvider(name = "carrotCLPCompareTestCases")
    public Object[][] carrotCLPCompareTestCases() {
        return new Object[][] {
                {
                    // no-op test case just to test the CLP - compare a file to itself
                        "src/test/resources/htsjdk/samtools/cram/CEUTrio.HiSeq.WGS.b37.NA12878.20.21.v3.0.samtools.cram",
                        "src/test/resources/htsjdk/samtools/cram/CEUTrio.HiSeq.WGS.b37.NA12878.20.21.v3.0.samtools.cram",
                        "src/test/resources/htsjdk/samtools/reference/human_g1k_v37.20.21.fasta.gz",
                },
        };
    }

    @Test(dataProvider = "carrotCLPCompareTestCases")
    private void carrotCLPCompare(
            final String inputCRAM1,
            final String inputCRAM2,
            final String referenceFASTA) {
        CarrotTest.main(new String[] {CLPCommand.COMPARE.toString(), inputCRAM1, inputCRAM2, referenceFASTA});
    }

    // 1) convert to CRAM 3.1 using samtools:
    //  inputs:
    //      convert
    //      input reads file (bam or cram)
    //      output reads file
    //      reference
    //      samtools params
    // 2) compare:
    //  inputs:
    //      compare
    //      reads file 1 (bam or cram)
    //      reads file 2 (bam or cram)
    //      reference
    public static void main(final String[] args) {
        final String helpUsage = String.format("Usage: \"%s\" or \"%s\"", CONVERT_USAGE, COMPARE_USAGE);

        if (args.length < 1) {
            System.err.println(helpUsage);
        }

        switch (CLPCommand.valueOf(args[0])) {
            case CONVERT:
                if (args.length < 5) {
                    System.err.println(String.format("Usage: %s", CONVERT_USAGE));
                } else {
                    System.out.println("Converting to CRAM 3.1 using samtools...");
                    SamtoolsTestUtils.convertToCRAM(
                            new HtsPath(args[1]),
                            new HtsPath(args[2]),
                            new HtsPath(args[3]),
                            args[4]);
                }
                break;

            case COMPARE:
                if (args.length < 4) {
                    System.err.println(String.format("Usage: %s", COMPARE_USAGE));
                } else {
                    System.out.println("Comparing two CRAM files...");
                    doCRAMCompare(
                            new HtsPath(args[1]).toPath(),
                            new HtsPath(args[2]).toPath(),
                            new HtsPath(args[3]).toPath());
                }
                break;

            default:
                System.err.println(helpUsage);
        }
    }

    public static void doCRAMCompare(
            final Path path1,
            final Path path2,
            final Path referencePath
    ) {
        System.out.println("Comparing " + path1 + " to " + path2);
        int count = 0;
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
                count++;
                if (!rec1.equals(rec2)) {
                    diffCount++;
                }
                if (count % 10000000 == 0) {
                    System.out.println("Processed " + count + " records");
                }
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        if (diffCount > 0) {
            Assert.fail(String.format("%d Records differ", diffCount));
        } else {
            System.out.println("No differences found!");
        }
        System.out.println("Done comparing " + path1 + " to " + path2);
    }
}
