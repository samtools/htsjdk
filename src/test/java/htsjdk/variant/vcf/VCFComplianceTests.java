package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.beta.plugin.registry.HtsDefaultRegistry;
import htsjdk.beta.plugin.variants.VariantsDecoder;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.tribble.TribbleException;
import htsjdk.utils.ValidationUtils;
import htsjdk.variant.variantcontext.VariantContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests that validate htsjdk VCF compliance using the test files in
 * https://github.com/samtools/hts-specs/tree/master/test/vcf.
 *
 * To run the tests, clone https://github.com/samtools/hts-specs, and initialize
 * {@code HTS_SPECS_REPO_LOCATION} to the location of the local clone.
 *
 * The data providers here filter/exclude many test cases that currently fail, with a comment explaining
 * what causes the failure (WIP).
 */
public class VCFComplianceTests extends HtsjdkTest {
    // To run the tests, clone https://github.com/samtools/hts-specs and initialize
    // HTS_SPECS_REPO_LOCATION to the location of the local clone.
    private static final String HTS_SPECS_REPO_LOCATION = "../hts-specs/";

    // test case sub directories
    private static final String VCF_4_2_PASSED = "test/vcf/4.2/passed/";
    private static final String VCF_4_2_FAILED = "test/vcf/4.2/failed/";
    private static final String VCF_4_3_PASSED = "test/vcf/4.3/passed/";
    private static final String VCF_4_3_FAILED = "test/vcf/4.3/failed/";

    @DataProvider(name = VCF_4_2_PASSED)
    private Object[] getVCF42Passed() {
        // exclude test cases that should be accepted, but are rejected by htsjdk
        final Set<String> excludeList = new HashSet<String>() {{
            add("passed_meta_pedigree.vcf");    // has blank line/"empty variant" at the end (ends with a line feed)
        }};
        return Arrays.stream(getFilesInDir(HTS_SPECS_REPO_LOCATION, VCF_4_2_PASSED))
                .filter(file -> !excludeList.contains(file.getName()))
                .toArray();
    }

    @DataProvider(name = VCF_4_2_FAILED)
    private Object[] getVCF42Failed() {
        // exclude cases that should be rejected due to various defects/malformed constructs, but are
        // currently accepted by htsjdk
        final Set<String> excludeList = new HashSet<String>() {{
            add("failed_fileformat_000.vcf");   // empty file format: ##fileformat=
            add("failed_fileformat_001.vcf");   // missing =: ##fileformat=VCF v4.2
            add("failed_meta_000.vcf");         // metadata line is not a key=value pair: ##Some random plain text

            //TODO: BOGUS - this seems legal(??)
            add("failed_meta_003.vcf");         //##CauseOfFailure=Non-closed meta entry
            //TODO: BOGUS - this seems legal(??)
            add("failed_meta_009.vcf");         //##CauseOfFailure=Meta-data entry is not a key-value pair (empty value)

            //invalid alt prefix:##ALT=<ID=THIS:IS-NOT-VALID,Description="Supported prefix not present">
            add("failed_meta_alt_005.vcf");
            // ##CauseOfFailure=The ALT ID contains a whitespace
            //##ALT=<ID=DEL:A B,Description="Deletion with invalid suffix">
            add("failed_meta_alt_006.vcf");
            //##CauseOfFailure=The ALT ID contains a comma
            //##ALT=<ID=DEL:A,B,Description="Deletion with invalid suffix">
            add("failed_meta_alt_007.vcf");
            //##CauseOfFailure=The ALT ID contains an opening angle bracket
            //##ALT=<ID=DEL:A<B,Description="Deletion with invalid suffix">
            add("failed_meta_alt_008.vcf");
            //##CauseOfFailure=The ALT ID contains a closing angle bracket
            //##ALT=<ID=DEL:A>B,Description="Deletion with invalid suffix">
            add("failed_meta_alt_009.vcf");

            //##CauseOfFailure=Meta-data entry is not a key-value pair
            //##assembly=
            add("failed_meta_assembly_000.vcf");

            //TODO: BOGUS - this seems legal(??)
            //##CauseOfFailure=Meta-data entry is not a key-value pair
            //##assembly=ftp://8080:8080/not-valid/host/to/file.fastq
            add("failed_meta_assembly_001.vcf");

            //##CauseOfFailure=contig ID contains a whitespace
            //##contig=<ID=1 A>
            add("failed_meta_contig_001.vcf");
            //##CauseOfFailure=contig ID contains a comma
            //##contig=<ID=1,A,length=123456>
            add("failed_meta_contig_002.vcf");

            // These still need to be investigated
            add("failed_meta_format_002.vcf");
            add("failed_meta_format_004.vcf");
            add("failed_meta_format_005.vcf");
            add("failed_meta_format_006.vcf");
            add("failed_meta_format_007.vcf");
            add("failed_meta_format_008.vcf");
            add("failed_meta_format_009.vcf");
            add("failed_meta_format_010.vcf");
            add("failed_meta_format_011.vcf");
            add("failed_meta_format_012.vcf");
            add("failed_meta_format_013.vcf");
            add("failed_meta_format_014.vcf");
            add("failed_meta_format_015.vcf");
            add("failed_meta_format_016.vcf");
            add("failed_meta_format_017.vcf");
            add("failed_meta_format_020.vcf");
            add("failed_meta_format_021.vcf");
            add("failed_meta_format_022.vcf");
            add("failed_meta_format_023.vcf");
            add("failed_meta_format_024.vcf");
            add("failed_meta_format_026.vcf");
            add("failed_meta_format_029.vcf");
            add("failed_meta_format_018.vcf");
            add("failed_meta_format_019.vcf");
            add("failed_meta_format_027.vcf");
            add("failed_meta_format_025.vcf");

            add("failed_meta_info_002.vcf");
            add("failed_meta_info_004.vcf");
            add("failed_meta_info_005.vcf");
            add("failed_meta_info_006.vcf");
            add("failed_meta_info_007.vcf");
            add("failed_meta_info_008.vcf");
            add("failed_meta_info_009.vcf");
            add("failed_meta_info_010.vcf");
            add("failed_meta_info_011.vcf");
            add("failed_meta_info_012.vcf");
            add("failed_meta_info_013.vcf");
            add("failed_meta_info_014.vcf");
            add("failed_meta_info_015.vcf");
            add("failed_meta_info_017.vcf");
            add("failed_meta_info_018.vcf");
            add("failed_meta_info_021.vcf");
            add("failed_meta_info_023.vcf");
            add("failed_meta_info_025.vcf");
            add("failed_meta_info_026.vcf");
            add("failed_meta_info_027.vcf");
            add("failed_meta_info_028.vcf");
            add("failed_meta_info_029.vcf");
            add("failed_meta_info_030.vcf");
            add("failed_meta_info_032.vcf");
            add("failed_meta_info_034.vcf");
            add("failed_meta_info_036.vcf");

            add("failed_meta_sample_000.vcf");
            add("failed_meta_sample_001.vcf");

            add("failed_meta_pedigree_001.vcf");
            add("failed_meta_pedigree_000.vcf");

            add("failed_meta_pedigreedb_000.vcf");
            add("failed_meta_pedigreedb_001.vcf");
            add("failed_meta_pedigreedb_002.vcf");

            add("failed_body_alt_000.vcf");     //ALT has a non-valid base "R"

            add("failed_body_duplicated_000.vcf");
            add("failed_body_duplicated_001.vcf");
            add("failed_body_duplicated_002.vcf");
            add("failed_body_duplicated_003.vcf");

            add("failed_body_info_000.vcf");
            add("failed_body_info_001.vcf");
            add("failed_body_info_002.vcf");
            add("failed_body_info_003.vcf");
            add("failed_body_info_004.vcf");
            add("failed_body_info_005.vcf");
            add("failed_body_info_006.vcf");
            add("failed_body_info_007.vcf");
            add("failed_body_info_008.vcf");
            add("failed_body_info_009.vcf");
            add("failed_body_info_010.vcf");
            add("failed_body_info_011.vcf");
            add("failed_body_info_012.vcf");
            add("failed_body_info_013.vcf");
            add("failed_body_info_014.vcf");
            add("failed_body_info_015.vcf");
            add("failed_body_info_016.vcf"); // hits assert htsjdk.variant.variantcontext.VariantContext.validateStop
            add("failed_body_info_019.vcf");
            add("failed_body_info_020.vcf");
            add("failed_body_info_021.vcf");
            add("failed_body_info_022.vcf");
            add("failed_body_info_023.vcf");
            add("failed_body_info_024.vcf");
            add("failed_body_info_025.vcf");
            add("failed_body_info_026.vcf");
            add("failed_body_info_027.vcf");
            add("failed_body_info_029.vcf");
            add("failed_body_info_030.vcf");
            add("failed_body_info_031.vcf");
            add("failed_body_info_033.vcf");
            add("failed_body_info_036.vcf");

            add("failed_body_id_000.vcf");
            add("failed_body_id_001.vcf");
            add("failed_body_id_002.vcf");

            add("failed_body_alt_003.vcf");
            add("failed_body_alt_005.vcf");

            add("failed_body_format_001.vcf");
            add("failed_body_format_002.vcf");
            add("failed_body_format_003.vcf");
            add("failed_body_format_004.vcf");

            add("failed_body_contiguous_000.vcf");
            add("failed_body_contiguous_001.vcf");

            add("failed_body_sample_000.vcf");
            add("failed_body_sample_001.vcf");
            add("failed_body_sample_002.vcf");
            add("failed_body_sample_003.vcf");
            add("failed_body_sample_004.vcf");
            add("failed_body_sample_005.vcf");
            add("failed_body_sample_006.vcf");
            add("failed_body_sample_007.vcf");
            add("failed_body_sample_008.vcf");
            add("failed_body_sample_009.vcf");
            add("failed_body_sample_010.vcf");
            add("failed_body_sample_011.vcf");

            add("failed_body_unsorted_000.vcf");

            add("failed_body_samples_ploidy_000.vcf");
            add("failed_body_samples_ploidy_001.vcf");
            add("failed_body_samples_ploidy_002.vcf");
            add("failed_body_samples_ploidy_003.vcf");

            add("failed_body_chrom_000.vcf");
            add("failed_body_chrom_001.vcf");
            add("failed_body_chrom_002.vcf");
            add("failed_body_chrom_003.vcf");

            add("failed_body_filter_000.vcf");
            add("failed_body_filter_001.vcf");
            add("failed_body_filter_004.vcf");

            add("failed_body_pos_002.vcf");
        }};

        return Arrays.stream(getFilesInDir(HTS_SPECS_REPO_LOCATION, VCF_4_2_FAILED))
                .filter(file -> !excludeList.contains(file.getName()))
                .toArray();
    }

    @DataProvider(name = VCF_4_3_PASSED)
    private Object[] getVCF43Passed() {
        // exclude test cases that should be accepted, but are rejected by htsjdk
        final Set<String> excludeList = new HashSet<String>() {{
            add("passed_meta_pedigree.vcf");    // has blank line the end (ends with a line feed)

            // unclosed quote in header line ID value:
            // ##ALT=<ID=complexcustomcontig!"#$%&'()*+-./;=?@[\]^_`{|}~,Description="Valid ALT metadata custom ID with non-alphanumeric characters">
            add("passed_meta_alt.vcf");
        }};
        return Arrays.stream(getFilesInDir(HTS_SPECS_REPO_LOCATION, VCF_4_3_PASSED))
                .filter(file -> !excludeList.contains(file.getName()))
                .toArray();
    }

    @DataProvider(name = VCF_4_3_FAILED)
    private Object[] getVCF43Failed() {
        // exclude cases that should be rejected due to various defects/malformed constructs, but are
        // currently accepted by htsjdk
        final Set<String> excludeList = new HashSet<String>() {{
            add("failed_body_alt_003.vcf");
            add("failed_body_alt_005.vcf");

            add("failed_body_chrom_000.vcf");
            add("failed_body_chrom_001.vcf");
            add("failed_body_chrom_002.vcf");
            add("failed_body_chrom_003.vcf");
            add("failed_body_chrom_004.vcf");

            add("failed_body_contiguous_000.vcf");
            add("failed_body_contiguous_001.vcf");

            add("failed_body_duplicated_000.vcf");
            add("failed_body_duplicated_001.vcf");
            add("failed_body_duplicated_002.vcf");
            add("failed_body_duplicated_003.vcf");

            add("failed_body_filter_000.vcf");
            add("failed_body_filter_001.vcf");
            add("failed_body_filter_004.vcf");
            add("failed_body_filter_005.vcf");

            add("failed_body_format_001.vcf");
            add("failed_body_format_002.vcf");
            add("failed_body_format_003.vcf");
            add("failed_body_format_004.vcf");
            add("failed_body_format_005.vcf");
            add("failed_body_format_006.vcf");

            add("failed_body_id_000.vcf");
            add("failed_body_id_001.vcf");
            add("failed_body_id_002.vcf");
            add("failed_body_id_003.vcf");

            add("failed_body_info_000.vcf");
            add("failed_body_info_001.vcf");
            add("failed_body_info_002.vcf");
            add("failed_body_info_003.vcf");
            add("failed_body_info_004.vcf");
            add("failed_body_info_005.vcf");
            add("failed_body_info_006.vcf");
            add("failed_body_info_007.vcf");
            add("failed_body_info_008.vcf");
            add("failed_body_info_009.vcf");
            add("failed_body_info_010.vcf");
            add("failed_body_info_011.vcf");
            add("failed_body_info_012.vcf");
            add("failed_body_info_013.vcf");
            add("failed_body_info_014.vcf");
            add("failed_body_info_015.vcf");
            add("failed_body_info_016.vcf"); // hits Assertion, not exception
            add("failed_body_info_019.vcf");
            add("failed_body_info_020.vcf");
            add("failed_body_info_021.vcf");
            add("failed_body_info_022.vcf");
            add("failed_body_info_023.vcf");
            add("failed_body_info_024.vcf");
            add("failed_body_info_025.vcf");
            add("failed_body_info_026.vcf");
            add("failed_body_info_027.vcf");
            add("failed_body_info_029.vcf");
            add("failed_body_info_030.vcf");
            add("failed_body_info_031.vcf");
            add("failed_body_info_033.vcf");
            add("failed_body_info_036.vcf");

            add("failed_body_no_newline_000.vcf");
            add("failed_body_no_newline_001.vcf");
            add("failed_body_no_newline_002.vcf");
            add("failed_body_no_newline_003.vcf");
            add("failed_body_no_newline_004.vcf");

            add("failed_body_pos_002.vcf");

            add("failed_body_sample_000.vcf");
            add("failed_body_sample_001.vcf");
            add("failed_body_sample_002.vcf");
            add("failed_body_sample_003.vcf");
            add("failed_body_sample_004.vcf");
            add("failed_body_sample_005.vcf");
            add("failed_body_sample_006.vcf");
            add("failed_body_sample_007.vcf");
            add("failed_body_sample_008.vcf");
            add("failed_body_sample_009.vcf");
            add("failed_body_sample_010.vcf");
            add("failed_body_sample_011.vcf");

            add("failed_body_samples_ploidy_000.vcf");
            add("failed_body_samples_ploidy_001.vcf");
            add("failed_body_samples_ploidy_002.vcf");
            add("failed_body_samples_ploidy_003.vcf");

            add("failed_body_unsorted_000.vcf");

            add("failed_empty.vcf");

            add("failed_empty_sample.vcf");

            add("failed_fileformat_000.vcf");
            add("failed_fileformat_001.vcf");

            add("failed_meta_000.vcf");
            add("failed_meta_003.vcf");
            add("failed_meta_009.vcf");

            add("failed_meta_alt_005.vcf");
            add("failed_meta_alt_006.vcf");
            add("failed_meta_alt_007.vcf");
            add("failed_meta_alt_008.vcf");
            add("failed_meta_alt_009.vcf");

            add("failed_meta_assembly_000.vcf");
            add("failed_meta_assembly_001.vcf");

            add("failed_meta_contig_001.vcf");
            add("failed_meta_contig_002.vcf");
            add("failed_meta_contig_003.vcf");

            add("failed_meta_format_002.vcf");
            add("failed_meta_format_004.vcf");
            add("failed_meta_format_005.vcf");
            add("failed_meta_format_006.vcf");
            add("failed_meta_format_007.vcf");
            add("failed_meta_format_008.vcf");
            add("failed_meta_format_009.vcf");
            add("failed_meta_format_010.vcf");
            add("failed_meta_format_011.vcf");
            add("failed_meta_format_012.vcf");
            add("failed_meta_format_013.vcf");
            add("failed_meta_format_014.vcf");
            add("failed_meta_format_015.vcf");
            add("failed_meta_format_016.vcf");
            add("failed_meta_format_017.vcf");
            add("failed_meta_format_018.vcf");
            add("failed_meta_format_019.vcf");
            add("failed_meta_format_020.vcf");
            add("failed_meta_format_021.vcf");
            add("failed_meta_format_022.vcf");
            add("failed_meta_format_023.vcf");
            add("failed_meta_format_024.vcf");
            add("failed_meta_format_025.vcf");
            add("failed_meta_format_026.vcf");
            add("failed_meta_format_027.vcf");
            add("failed_meta_format_028.vcf");
            add("failed_meta_format_029.vcf");
            add("failed_meta_format_030.vcf");
            add("failed_meta_format_031.vcf");
            add("failed_meta_format_032.vcf");
            add("failed_meta_format_033.vcf");

            add("failed_meta_info_002.vcf");
            add("failed_meta_info_004.vcf");
            add("failed_meta_info_005.vcf");
            add("failed_meta_info_006.vcf");
            add("failed_meta_info_007.vcf");
            add("failed_meta_info_008.vcf");
            add("failed_meta_info_009.vcf");
            add("failed_meta_info_010.vcf");
            add("failed_meta_info_011.vcf");
            add("failed_meta_info_012.vcf");
            add("failed_meta_info_013.vcf");
            add("failed_meta_info_014.vcf");
            add("failed_meta_info_015.vcf");
            add("failed_meta_info_016.vcf");
            add("failed_meta_info_017.vcf");
            add("failed_meta_info_018.vcf");
            add("failed_meta_info_019.vcf");
            add("failed_meta_info_020.vcf");
            add("failed_meta_info_021.vcf");
            add("failed_meta_info_023.vcf");
            add("failed_meta_info_024.vcf");
            add("failed_meta_info_027.vcf");
            add("failed_meta_info_029.vcf");
            add("failed_meta_info_031.vcf");
            add("failed_meta_info_032.vcf");
            add("failed_meta_info_033.vcf");
            add("failed_meta_info_034.vcf");
            add("failed_meta_info_035.vcf");
            add("failed_meta_info_036.vcf");
            add("failed_meta_info_038.vcf");
            add("failed_meta_info_040.vcf");
            add("failed_meta_info_042.vcf");

            add("failed_meta_meta_000.vcf");
            add("failed_meta_meta_001.vcf");
            add("failed_meta_meta_002.vcf");
            add("failed_meta_meta_003.vcf");

            add("failed_meta_pedigree_000.vcf");
            add("failed_meta_pedigree_001.vcf");
            add("failed_meta_pedigree_002.vcf");

            add("failed_meta_pedigreedb_000.vcf");
            add("failed_meta_pedigreedb_001.vcf");
            add("failed_meta_pedigreedb_002.vcf");

            add("failed_meta_sample_001.vcf");
            add("failed_meta_sample_002.vcf");
            add("failed_meta_sample_003.vcf");
        }};
        return Arrays.stream(getFilesInDir(HTS_SPECS_REPO_LOCATION, VCF_4_3_FAILED))
                .filter(file -> !excludeList.contains(file.getName()))
                .toArray();
    }

    @Test(dataProvider = VCF_4_2_PASSED)
    public void testVCF42ReadCompliancePassed(final File vcfFileName) {
        doReadTest(vcfFileName);
    }

    @Test(dataProvider = VCF_4_2_FAILED, expectedExceptions = TribbleException.class)
    public void testVCF42ReadComplianceFailed(final File vcfFileName) {
        doReadTest(vcfFileName);
    }

    @Test(dataProvider = VCF_4_3_PASSED)
    public void testVCF43ReadCompliancePassed(final File vcfFileName) {
        doReadTest(vcfFileName);
    }

    @Test(dataProvider = VCF_4_3_FAILED, expectedExceptions = TribbleException.class)
    public void testVCF43ReadComplianceFailed(final File vcfFileName) {
        doReadTest(vcfFileName);
    }

    private void doReadTest(final File vcfFileName) {
        final IOPath inputVCFPath = new HtsPath(vcfFileName.getAbsolutePath());
        try (final VariantsDecoder variantsDecoder = HtsDefaultRegistry.getVariantsResolver().getVariantsDecoder(inputVCFPath)) {
            for (final VariantContext vc : variantsDecoder) {
            }
        }
    }

    private File[] getFilesInDir(final String dir, final String subdir) {
        ValidationUtils.nonNull(dir, "test dir");
        ValidationUtils.validateArg(dir.length() > 0, "test dir name is 0 length");
        ValidationUtils.nonNull(subdir, "test subdir");
        ValidationUtils.validateArg(subdir.length() > 0, "test subdir name is 0 length");

        final File testDir = new File(dir,subdir);
        if (!Files.exists(testDir.toPath())) {
            throw new IllegalArgumentException(
                    String.format("The test directory \"%s\" does not exist", testDir.getAbsolutePath()));
        }
        return Arrays.stream(testDir.list())
                .map(fn -> new File(dir + subdir + fn))
                .toArray(File[]::new);
    }

}
