package htsjdk.beta.codecs.variants.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.beta.codecs.variants.vcf.vcfv3_2.VCFCodecV3_2;
import htsjdk.beta.codecs.variants.vcf.vcfv3_3.VCFCodecV3_3;
import htsjdk.beta.codecs.variants.vcf.vcfv4_0.VCFCodecV4_0;
import htsjdk.beta.codecs.variants.vcf.vcfv4_1.VCFCodecV4_1;
import htsjdk.beta.codecs.variants.vcf.vcfv4_2.VCFCodecV4_2;
import htsjdk.beta.codecs.variants.vcf.vcfv4_3.VCFCodecV4_3;
import htsjdk.beta.exception.HtsjdkUnsupportedOperationException;
import htsjdk.beta.io.IOPathUtils;
import htsjdk.beta.io.bundle.OutputStreamResource;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.IOUtils;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleBuilder;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.io.bundle.IOPathResource;
import htsjdk.beta.plugin.interval.HtsQueryInterval;
import htsjdk.beta.plugin.interval.HtsQueryRule;
import htsjdk.beta.plugin.registry.HtsDefaultRegistry;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.variants.VariantsDecoder;
import htsjdk.beta.plugin.variants.VariantsEncoder;
import htsjdk.beta.plugin.variants.VariantsFormats;
import htsjdk.samtools.util.IOUtil;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Function;

public class HtsVCFCodecTest extends HtsjdkTest {
    final IOPath VARIANTS_TEST_DIR = new HtsPath("src/test/resources/htsjdk/");
    final IOPath TEST_VCF_WITH_INDEX = new HtsPath("src/test/resources/htsjdk/variant/HiSeq.10000.vcf.bgz");
    final IOPath TEST_VCF_INDEX = new HtsPath("src/test/resources/htsjdk/variant/HiSeq.10000.vcf.bgz.tbi");

    @DataProvider(name="vcfReadWriteTests")
    private Object[][] vcfReadWriteTests() {
        return new Object[][] {
                // one test case for each supported VCF version
                { new HtsPath(VARIANTS_TEST_DIR + "variant/vcfexampleV3.2.vcf"), VCFCodecV3_2.VCF_V32_VERSION },
                { new HtsPath(VARIANTS_TEST_DIR + "tribble/tabix/trioDup.vcf"), VCFCodecV3_3.VCF_V33_VERSION },
                { new HtsPath(VARIANTS_TEST_DIR + "variant/HiSeq.10000.vcf"), VCFCodecV4_0.VCF_V40_VERSION },
                { new HtsPath(VARIANTS_TEST_DIR + "variant/dbsnp_135.b37.1000.vcf"), VCFCodecV4_1.VCF_V41_VERSION },
                { new HtsPath(VARIANTS_TEST_DIR + "variant/vcf42HeaderLines.vcf"), VCFCodecV4_2.VCF_V42_VERSION },
                { new HtsPath(VARIANTS_TEST_DIR + "variant/NA12891.vcf.gz"), VCFCodecV4_2.VCF_V42_VERSION },
                { new HtsPath(VARIANTS_TEST_DIR + "variant/vcf43/all43FeaturesCompressed.vcf.gz"), VCFCodecV4_3.VCF_V43_VERSION }
        };
    }

    @Test(dataProvider = "vcfReadWriteTests")
    public void testRoundTripVCFThroughPath(final IOPath inputPath, final HtsVersion expectedDecoderVersion) {
        // some test files require "AllowMissingFields" options for writing
        final VariantsEncoderOptions variantsEncoderOptions = new VariantsEncoderOptions().setAllowFieldsMissingFromHeader(true);
        final IOPath outputPath = IOUtils.createTempPath("roundTripVCFThroughPath", ".vcf");

        try (final VariantsDecoder variantsDecoder = HtsDefaultRegistry.getVariantsResolver().getVariantsDecoder(inputPath);
             final VariantsEncoder variantsEncoder = HtsDefaultRegistry.getVariantsResolver().getVariantsEncoder(
                     outputPath,
                     variantsEncoderOptions)) {
            Assert.assertNotNull(variantsDecoder);
            Assert.assertTrue(variantsDecoder.getDisplayName().contains(inputPath.toString()));
            Assert.assertNotNull(variantsEncoder);
            Assert.assertTrue(variantsEncoder.getDisplayName().contains(outputPath.toString()));

            readWriteVCF(variantsDecoder, variantsEncoder, expectedDecoderVersion);
        }
    }

    @Test(dataProvider = "vcfReadWriteTests")
    public void testRoundTripVCFThroughStream(final IOPath inputPath, final HtsVersion expectedCodecVersion) throws IOException {
        // some test files require "AllowMissingFields" options for writing
        final VariantsEncoderOptions variantsEncoderOptions = new VariantsEncoderOptions().setAllowFieldsMissingFromHeader(true);
        final IOPath outputPath = IOUtils.createTempPath("roundTripVCFThroughStream", ".vcf");

        final String OUTPUT_DISPLAY_NAME = "testStream";
        try (final VariantsDecoder variantsDecoder = HtsDefaultRegistry.getVariantsResolver().getVariantsDecoder(inputPath);
             final OutputStream os = outputPath.getOutputStream()) {
            final Bundle outputBundle = new Bundle(
                    BundleResourceType.VARIANT_CONTEXTS,
                    Collections.singletonList(new OutputStreamResource(
                            os,
                            OUTPUT_DISPLAY_NAME,
                            BundleResourceType.VARIANT_CONTEXTS)));
             try (final VariantsEncoder variantsEncoder = HtsDefaultRegistry.getVariantsResolver().getVariantsEncoder(
                     outputBundle,
                     variantsEncoderOptions)) {
                Assert.assertTrue(variantsDecoder.getDisplayName().contains(inputPath.toString()));
                Assert.assertTrue(variantsEncoder.getDisplayName().contains(OUTPUT_DISPLAY_NAME));

                readWriteVCF(variantsDecoder, variantsEncoder, expectedCodecVersion);
            }
        }
    }

    @DataProvider(name="gzipSuffixTests")
    private Object[][] gzipSuffixTests() {
        return new Object[][] {
                { ".vcf.gz" },
                { ".vcf.bgz" }
        };
    }

    @Test(dataProvider = "gzipSuffixTests")
    public void testEnsureGZIPOutOnGZSuffix(final String suffix) throws IOException {
        final IOPath inputPath = new HtsPath(VARIANTS_TEST_DIR + "variant/vcf42HeaderLines.vcf");
        final IOPath outputPath = IOUtils.createTempPath("ensureGZIP", suffix);
        // the test input is v4.2, so the expected decoder is VCF_V42_VERSION
        readWriteVCFToPath(inputPath, outputPath, VCFCodecV4_2.VCF_V42_VERSION );

        // isGZIPInputStream requires mark support so use a BufferedInputStream
        try (final InputStream inputStream = outputPath.getInputStream();
             final BufferedInputStream bis = new BufferedInputStream(inputStream)) {
            Assert.assertTrue(IOUtil.isGZIPInputStream(bis));
        }
    }

    @DataProvider(name="queryMethodsCases")
    public Object[][] queryMethodsCases() {
        return new Object[][] {
                { (Function<VariantsDecoder, ?>) (VariantsDecoder vcfDecoder) -> vcfDecoder.queryStart("chr1", 177) },
                { (Function<VariantsDecoder, ?>) (VariantsDecoder vcfDecoder) -> vcfDecoder.query("chr1", 177, 178,
                        HtsQueryRule.OVERLAPPING) },
                { (Function<VariantsDecoder, ?>) (VariantsDecoder vcfDecoder) -> vcfDecoder.queryOverlapping("chr1", 177, 178) },
        };
    }

    @Test(dataProvider="queryMethodsCases")
    public void testAcceptIndexInBundle(final Function<VariantsDecoder, ?> queryFunction) {
        // use a vcf that is known to have an on-disk companion index to ensure that attempts to make
        // index queries are rejected if the index is not explicitly included in the input bundle
        final Bundle variantsBundle = new BundleBuilder()
                .addPrimary(new IOPathResource(TEST_VCF_WITH_INDEX, BundleResourceType.VARIANT_CONTEXTS))
                .addSecondary(new IOPathResource(TEST_VCF_INDEX, BundleResourceType.VARIANTS_INDEX))
                .build();

        try (final VariantsDecoder variantsDecoder =
                HtsDefaultRegistry.getVariantsResolver().getVariantsDecoder(variantsBundle)) {
            Assert.assertTrue(variantsDecoder.hasIndex());
            Assert.assertTrue(variantsDecoder.isQueryable());
            queryFunction.apply(variantsDecoder);
        }
    }

    @DataProvider(name="unsupportedQueryCases")
    public Object[][] unsupportedQueryCases() {
        return new Object[][] {
                // reject all multiple interval queries, or "contained" queries since these are not yet implemented
                { (Function<VariantsDecoder, ?>) (VariantsDecoder vcfDecoder) -> vcfDecoder.query(
                        Arrays.asList(
                                new HtsQueryInterval("chr1", 177, 178),
                                new HtsQueryInterval("chr1", 180, 181)),
                        HtsQueryRule.OVERLAPPING) },
                { (Function<VariantsDecoder, ?>) (VariantsDecoder vcfDecoder) -> vcfDecoder.query(
                        Arrays.asList(new HtsQueryInterval("chr1", 177, 178)),
                        HtsQueryRule.CONTAINED) },
                { (Function<VariantsDecoder, ?>) (VariantsDecoder vcfDecoder) -> vcfDecoder.queryContained(
                        Arrays.asList(new HtsQueryInterval("chr1", 177, 178))) },
                { (Function<VariantsDecoder, ?>) (VariantsDecoder vcfDecoder) -> vcfDecoder.query(
                        Arrays.asList(new HtsQueryInterval("chr1", 177, 178)),
                        HtsQueryRule.CONTAINED) },
        };
    }

    @Test(dataProvider="unsupportedQueryCases", expectedExceptions = HtsjdkUnsupportedOperationException.class)
    public void testRejectUnsupportedQueries(final Function<VariantsDecoder, ?> queryFunction) {
        final Bundle variantsBundle = new BundleBuilder()
                .addPrimary(new IOPathResource(TEST_VCF_WITH_INDEX, BundleResourceType.VARIANT_CONTEXTS))
                .addSecondary(new IOPathResource(TEST_VCF_INDEX, BundleResourceType.VARIANTS_INDEX))
                .build();

        try (final VariantsDecoder variantsDecoder =
                     HtsDefaultRegistry.getVariantsResolver().getVariantsDecoder(variantsBundle)) {
            Assert.assertTrue(variantsDecoder.hasIndex());
            Assert.assertTrue(variantsDecoder.isQueryable());
            queryFunction.apply(variantsDecoder);
        }
    }

    @Test(dataProvider="queryMethodsCases", expectedExceptions = IllegalArgumentException.class)
    public void testRejectIndexNotIncludedInBundle(final Function<VariantsDecoder, ?> queryFunction) {
        // use a bam that is known to have an on-disk companion index to ensure that attempts to make
        // index queries are rejected if the index is not explicitly included in the input bundle
        final Bundle variantsBundle = new BundleBuilder()
                .addPrimary(new IOPathResource(TEST_VCF_WITH_INDEX, BundleResourceType.VARIANT_CONTEXTS))
                .build();
        Assert.assertFalse(variantsBundle.get(BundleResourceType.VARIANTS_INDEX).isPresent());

        try (final VariantsDecoder variantsDecoder =
                     HtsDefaultRegistry.getVariantsResolver().getVariantsDecoder(variantsBundle)) {

            Assert.assertFalse(variantsDecoder.hasIndex());
            Assert.assertFalse(variantsDecoder.isQueryable());

            // now try every possible query method
            queryFunction.apply(variantsDecoder);
        }
    }

    @Test
    public void testGetDecoderForFormatAndVersion() {
        final IOPath tempOutputPath = IOPathUtils.createTempPath("testGetDecoderForFormatAndVersion", ".vcf");
        final Bundle outputBundle = new BundleBuilder()
                .addPrimary(new IOPathResource(tempOutputPath, BundleResourceType.VARIANT_CONTEXTS))
                .build();
        try (final VariantsEncoder variantsEncoder = HtsDefaultRegistry.getVariantsResolver().getVariantsEncoder(
                outputBundle,
                new VariantsEncoderOptions(),
                VariantsFormats.VCF,
                VCFCodecV4_2.VCF_V42_VERSION)) {
            Assert.assertEquals(variantsEncoder.getFileFormat(), VariantsFormats.VCF);
            Assert.assertEquals(variantsEncoder.getVersion(), VCFCodecV4_2.VCF_V42_VERSION);
        }
    }

    private void readWriteVCFToPath(final IOPath inputPath, final IOPath outputPath, final HtsVersion expectedDecoderVersion) {
        // some test files require "AllowMissingFields" options for writing
        final VariantsEncoderOptions variantsEncoderOptions = new VariantsEncoderOptions().setAllowFieldsMissingFromHeader(true);
        try (final VariantsDecoder variantsDecoder = HtsDefaultRegistry.getVariantsResolver().getVariantsDecoder(inputPath);
             final VariantsEncoder variantsEncoder = HtsDefaultRegistry.getVariantsResolver().getVariantsEncoder(
                     outputPath,
                     variantsEncoderOptions)) {

            Assert.assertNotNull(variantsDecoder);
            Assert.assertEquals(variantsDecoder.getFileFormat(), VariantsFormats.VCF);
            Assert.assertEquals(variantsDecoder.getVersion(), expectedDecoderVersion);
            Assert.assertTrue(variantsDecoder.getDisplayName().contains(inputPath.toString()));

            Assert.assertNotNull(variantsEncoder);
            Assert.assertEquals(variantsEncoder.getFileFormat(), VariantsFormats.VCF);
            Assert.assertEquals(variantsEncoder.getVersion(), VCFCodecV4_3.VCF_V43_VERSION);
            Assert.assertTrue(variantsEncoder.getDisplayName().contains(outputPath.toString()));

            final VCFHeader vcfHeader = variantsDecoder.getHeader();
            Assert.assertNotNull(vcfHeader);

            variantsEncoder.setHeader(vcfHeader);
            for (final VariantContext vc : variantsDecoder) {
                variantsEncoder.write(vc);
            }
        }
    }

    private void readWriteVCF(
        final VariantsDecoder variantsDecoder,
        final VariantsEncoder variantsEncoder,
        final HtsVersion expectedDecoderVersion) {
        Assert.assertEquals(variantsDecoder.getFileFormat(), VariantsFormats.VCF);
        Assert.assertEquals(variantsDecoder.getVersion(), expectedDecoderVersion);

        Assert.assertEquals(variantsEncoder.getFileFormat(), VariantsFormats.VCF);
        Assert.assertEquals(variantsEncoder.getVersion(), VCFCodecV4_3.VCF_V43_VERSION);

        final VCFHeader vcfHeader = variantsDecoder.getHeader();
        Assert.assertNotNull(vcfHeader);

        variantsEncoder.setHeader(vcfHeader);
        for (final VariantContext vc : variantsDecoder) {
            variantsEncoder.write(vc);
        }
    }

}
