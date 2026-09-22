package htsjdk.variant.vcf;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.TestUtils;
import htsjdk.variant.bcf2.BCFVersion;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Created by farjoun on 10/12/17.
 */
public class VCFFileReaderTest extends HtsjdkTest {
    private static final Path TEST_DATA_DIR = Path.of("src/test/resources/htsjdk/variant/");

    @DataProvider(name = "queryableData")
    public Iterator<Object[]> queryableData() throws IOException {
        List<Object[]> tests = new ArrayList<>();
        tests.add(new Object[] {TEST_DATA_DIR.resolve("NA12891.fp.vcf"), false});
        tests.add(new Object[] {TEST_DATA_DIR.resolve("NA12891.vcf"), false});
        tests.add(new Object[] {
            VCFUtils.createTemporaryIndexedVcfFromInput(
                    TEST_DATA_DIR.resolve("NA12891.vcf"), "fingerprintcheckertest.tmp."),
            true
        });
        tests.add(new Object[] {
            VCFUtils.createTemporaryIndexedVcfFromInput(
                    TEST_DATA_DIR.resolve("NA12891.vcf.gz"), "fingerprintcheckertest.tmp."),
            true
        });

        return tests.iterator();
    }

    @Test(dataProvider = "queryableData")
    public void testIsQueriable(final Path vcf, final boolean expectedQueryable) throws Exception {
        Assert.assertEquals(new VCFFileReader(vcf, false).isQueryable(), expectedQueryable);
    }

    @DataProvider(name = "pathsData")
    Object[][] pathsData() {

        final String TEST_DATA_DIR = "src/test/resources/htsjdk/variant/";
        return new Object[][] {
            // various ways to refer to a local file
            {TEST_DATA_DIR + "VCF4HeaderTest.vcf", null, false, true},

            // this file is the same as VCF4HeaderTest.vcf, except the header is marked as VCF 4.4
            {TEST_DATA_DIR + "VCF4_4HeaderTest.vcf", null, false, true},

            //                // this is almost a vcf, but not quite it's missing the #CHROM line and it has no
            // content...
            {TEST_DATA_DIR + "Homo_sapiens_assembly38.tile_db_header.vcf", null, false, false},

            //                // test that have indexes
            {TEST_DATA_DIR + "test.vcf.bgz", TEST_DATA_DIR + "test.vcf.bgz.tbi", true, true},
            {TEST_DATA_DIR + "serialization_test.bcf", TEST_DATA_DIR + "serialization_test.bcf.idx", true, true},
            {TEST_DATA_DIR + "test1.vcf", TEST_DATA_DIR + "test1.vcf.idx", true, true},
            //
            //                // test that lack indexes (should succeed)
            {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf.gz", null, false, true},
            {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf", null, false, true},
            {TEST_DATA_DIR + "VcfThatLacksAnIndex but has a space.vcf", null, false, true},
            {TEST_DATA_DIR + "VcfThatLacksAnIndex.bcf", null, false, true},
            {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf.bgz", null, false, true},
            //
            //                // test that lack indexes should fail)
            {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf.gz", null, true, false},
            {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf", null, true, false},
            {TEST_DATA_DIR + "VcfThatLacksAnIndex.bcf", null, true, false},
            {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf.bgz", null, true, false},
            //
            //                // testing that v4.2 parses Source/Version fields, see issue #517
            {TEST_DATA_DIR + "Vcf4.2WithSourceVersionInfoFields.vcf", null, false, true},
            //
            // BCF 2.2 is now accepted (issue #946)
            {TEST_DATA_DIR + "BCFVersion22Uncompressed.bcf", null, false, true}
        };
    }

    @Test(dataProvider = "pathsData", timeOut = 60_000)
    public void testCanOpenVCFPathReader(
            final String file, final String index, final boolean requiresIndex, final boolean shouldSucceed)
            throws Exception {
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path tribbleFileInJimfs = TestUtils.getTribbleFileInJimfs(file, index, fs);
            try (final VCFFileReader reader = new VCFFileReader(tribbleFileInJimfs, requiresIndex)) {

                final VCFHeader header = reader.getFileHeader();
                reader.iterator().stream()
                        .forEach(v -> v.getGenotypes().stream().count());
            } catch (Exception e) {
                if (shouldSucceed) {
                    throw e;
                } else {
                    return;
                }
            }
        }
        // fail if a test that should have thrown didn't
        Assert.assertTrue(shouldSucceed, "Test should have failed but succeeded");
    }

    @Test
    public void testAcceptVCF4_4() {
        try (final VCFFileReader reader = new VCFFileReader(TEST_DATA_DIR.resolve("VCF4_4HeaderTest.vcf"), false)) {
            final VCFHeader header = reader.getFileHeader();
            Assert.assertEquals(header.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_4);
        }
    }

    @Test
    public void testAcceptVCF4_5() throws IOException {
        final Path vcf = Files.createTempFile("VCFFileReaderTest", ".vcf");
        vcf.toFile().deleteOnExit();
        Files.writeString(
                vcf,
                "##fileformat=VCFv4.5\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n"
                        + "chr1\t100\t.\tA\tC\t50\tPASS\t.\n");
        try (final VCFFileReader reader = new VCFFileReader(vcf, false)) {
            Assert.assertEquals(reader.getFileHeader().getVCFHeaderVersion(), VCFHeaderVersion.VCF4_5);
            Assert.assertEquals(reader.iterator().toList().size(), 1);
        }
    }

    @Test
    public void vcf3FileIsReadByVCFFileReader() throws IOException {
        final Path vcf = Files.createTempFile("VCFFileReaderTest", ".vcf");
        vcf.toFile().deleteOnExit();
        Files.writeString(
                vcf,
                "##fileformat=VCFv3.3\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n"
                        + "chr1\t100\t.\tA\tC\t50\tPASS\t.\n");
        try (final VCFFileReader reader = new VCFFileReader(vcf, false)) {
            Assert.assertEquals(reader.getFileHeader().getVCFHeaderVersion(), VCFHeaderVersion.VCF3_3);
            Assert.assertEquals(reader.iterator().toList().size(), 1);
        }
    }

    @Test
    public void vcf32FileIsReadByVCFFileReader() throws IOException {
        final Path vcf = Files.createTempFile("VCFFileReaderTest", ".vcf");
        vcf.toFile().deleteOnExit();
        Files.writeString(
                vcf,
                "##format=VCRv3.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n" + "chr1\t100\t.\tA\tC\t50\t0\t.\n");
        try (final VCFFileReader reader = new VCFFileReader(vcf, false)) {
            Assert.assertEquals(reader.getFileHeader().getVCFHeaderVersion(), VCFHeaderVersion.VCF3_2);
            Assert.assertEquals(reader.iterator().toList().size(), 1);
        }
    }

    @Test
    public void testTabixFileWithEmbeddedSpaces() throws IOException {
        final Path testVCF = TEST_DATA_DIR.resolve("HiSeq.10000.vcf.bgz");
        final Path testTBI = TEST_DATA_DIR.resolve("HiSeq.10000.vcf.bgz.tbi");

        // Copy the input files into a temporary directory with embedded spaces in the name.
        // This test needs to include the associated .tbi file because we want to force execution
        // of the tabix code path.
        final Path tempDir = IOUtil.createTempDir("test spaces");
        Assert.assertTrue(tempDir.toAbsolutePath().toString().contains(" "));
        tempDir.toFile().deleteOnExit();
        final Path inputVCF = tempDir.resolve("HiSeq.10000.vcf.bgz");
        inputVCF.toFile().deleteOnExit();
        final Path inputTBI = tempDir.resolve("HiSeq.10000.vcf.bgz.tbi");
        inputTBI.toFile().deleteOnExit();
        Files.copy(testVCF, inputVCF);
        Files.copy(testTBI, inputTBI);

        try (final VCFFileReader vcfFileReader = new VCFFileReader(inputVCF)) {
            Assert.assertNotNull(vcfFileReader.getFileHeader());
        }
    }

    // ============================================================
    // VCFFileReader routing tests for BCF
    // ============================================================

    private static VCFHeader bcfRoutingHeader() {
        final SAMSequenceDictionary dict = new SAMSequenceDictionary();
        dict.addSequence(new SAMSequenceRecord("chr1", 100000));
        dict.addSequence(new SAMSequenceRecord("chr2", 50000));
        final Set<VCFHeaderLine> meta = new HashSet<>();
        meta.add(new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "Depth"));
        meta.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "Genotype"));
        final VCFHeader header = new VCFHeader(meta, Collections.singletonList("sample1"));
        header.setSequenceDictionary(dict);
        return header;
    }

    @Test
    public void aBgzfBcfWithACsiIsOpenedThroughVCFFileReader() throws IOException {
        final VCFHeader header = bcfRoutingHeader();
        final Path bcf = Files.createTempFile("routing.", ".bcf");
        bcf.toFile().deleteOnExit();
        bcf.resolveSibling(bcf.getFileName() + FileExtensions.CSI).toFile().deleteOnExit();
        try (VariantContextWriter w = new VariantContextWriterBuilder()
                .setOutputPath(bcf)
                .setReferenceDictionary(header.getSequenceDictionary())
                .setOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            w.writeHeader(header);
            w.add(new VariantContextBuilder("test", "chr1", 100, 100, Arrays.asList(Allele.REF_A, Allele.ALT_C))
                    .genotypes(new GenotypeBuilder("sample1", Arrays.asList(Allele.REF_A, Allele.ALT_C)).make())
                    .attribute("DP", 30)
                    .make());
            w.add(new VariantContextBuilder("test", "chr2", 200, 200, Arrays.asList(Allele.REF_A, Allele.ALT_C))
                    .genotypes(new GenotypeBuilder("sample1", Arrays.asList(Allele.REF_A, Allele.ALT_C)).make())
                    .attribute("DP", 30)
                    .make());
        }
        try (VCFFileReader reader = new VCFFileReader(bcf, true)) {
            Assert.assertTrue(reader.isQueryable());
            final List<VariantContext> results = reader.query("chr2", 100, 300).toList();
            Assert.assertEquals(results.size(), 1);
        }
    }

    @Test
    public void aRawBcfWithAnIdxIsStillQueryable() throws IOException {
        final VCFHeader header = bcfRoutingHeader();
        final Path bcf = Files.createTempFile("raw.", ".bcf");
        bcf.toFile().deleteOnExit();
        htsjdk.tribble.Tribble.indexPath(bcf).toFile().deleteOnExit();
        try (VariantContextWriter w = new VariantContextWriterBuilder()
                .setOutputPath(bcf)
                .setReferenceDictionary(header.getSequenceDictionary())
                .setOption(Options.INDEX_ON_THE_FLY)
                .setBCFVersion(BCFVersion.BCF_2_1)
                .build()) {
            w.writeHeader(header);
            w.add(new VariantContextBuilder("test", "chr1", 100, 100, Arrays.asList(Allele.REF_A, Allele.ALT_C))
                    .genotypes(new GenotypeBuilder("sample1", Arrays.asList(Allele.REF_A, Allele.ALT_C)).make())
                    .attribute("DP", 30)
                    .make());
        }
        try (VCFFileReader reader = new VCFFileReader(bcf, true)) {
            Assert.assertTrue(reader.isQueryable());
            Assert.assertEquals(reader.query("chr1", 50, 150).toList().size(), 1);
        }
    }

    @Test
    public void bgzfBcfWithoutIndexAndRequireIndexFalseIteratesAllRecords() throws IOException {
        final VCFHeader header = bcfRoutingHeader();
        final Path bcf = Files.createTempFile("noindex.", ".bcf");
        bcf.toFile().deleteOnExit();
        try (VariantContextWriter w = new VariantContextWriterBuilder()
                .setOutputPath(bcf)
                .setReferenceDictionary(header.getSequenceDictionary())
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .build()) {
            w.writeHeader(header);
            w.add(new VariantContextBuilder("test", "chr1", 100, 100, Arrays.asList(Allele.REF_A, Allele.ALT_C))
                    .genotypes(new GenotypeBuilder("sample1", Arrays.asList(Allele.REF_A, Allele.ALT_C)).make())
                    .attribute("DP", 30)
                    .make());
            w.add(new VariantContextBuilder("test", "chr2", 200, 200, Arrays.asList(Allele.REF_A, Allele.ALT_C))
                    .genotypes(new GenotypeBuilder("sample1", Arrays.asList(Allele.REF_A, Allele.ALT_C)).make())
                    .attribute("DP", 30)
                    .make());
        }
        try (VCFFileReader reader = new VCFFileReader(bcf, false)) {
            int count = 0;
            for (final VariantContext ignored : reader) {
                count++;
            }
            Assert.assertEquals(count, 2);
        }
    }
}
