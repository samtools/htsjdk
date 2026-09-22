package htsjdk.variant.bcf2;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.TestUtil;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.FeatureCodecHeader;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.tribble.readers.PositionalBufferedStream;
import htsjdk.utils.BcftoolsTestUtils;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.LazyGenotypesContext;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.VariantContextTestProvider;
import htsjdk.variant.variantcontext.writer.BCF2Encoder;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFFilterHeaderLine;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineCount;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFHeaderVersion;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class BCFCodecTest extends VariantBaseTest {
    private static final String TEST_DATA_DIR = "src/test/resources/htsjdk/variant/";
    private static final Path BCF22_FIXTURE = Path.of(TEST_DATA_DIR, "BCFVersion22Uncompressed.bcf");

    private Path tempDir;

    @BeforeClass(alwaysRun = true)
    private void createTemporaryDirectory() {
        tempDir = TestUtil.getTempDirectoryAsPath("BCFCodecTest", "");
        tempDir.toFile().deleteOnExit();
    }

    // -- BCF version acceptance --

    @Test
    public void bcf22IsAccepted() throws IOException {
        try (final PositionalBufferedStream pbs = new PositionalBufferedStream(Files.newInputStream(BCF22_FIXTURE))) {
            final FeatureCodecHeader fch = new BCF2Codec().readHeader(pbs);
            final VCFHeader vcfHeader = (VCFHeader) fch.getHeaderValue();
            Assert.assertNotEquals(vcfHeader.getMetaDataInInputOrder().size(), 0);
        }
    }

    @Test
    public void rejectsBcfMajorVersion3() {
        final TribbleException e = Assert.expectThrows(TribbleException.class, () -> readAll(bcfWithVersion(3, 1)));
        Assert.assertTrue(e.getMessage().contains("major version 3"), e.getMessage());
    }

    @Test
    public void rejectsBcfMinorVersion3() {
        final TribbleException e = Assert.expectThrows(TribbleException.class, () -> readAll(bcfWithVersion(2, 3)));
        Assert.assertTrue(e.getMessage().contains("BCF 2.3"), e.getMessage());
    }

    @Test
    public void aSubclassMayStillOverrideTheVersionPolicy() {
        @SuppressWarnings("deprecation")
        final BCF2Codec codec = new BCF2Codec() {
            @Override
            protected void validateVersionCompatibility(final BCFVersion supported, final BCFVersion actual) {
                throw new TribbleException("refused by the subclass");
            }
        };
        final TribbleException e =
                Assert.expectThrows(TribbleException.class, () -> readAll(codec, bcfWithVersion(2, 2)));
        Assert.assertEquals(e.getMessage(), "refused by the subclass");
    }

    private static byte[] bcfWithVersion(final int major, final int minor) {
        return rawBcf(major, minor, ONE_SAMPLE_HEADER);
    }

    // -- canDecode --

    @Test
    public void canDecodeReturnsTrueForRawBcf() {
        Assert.assertTrue(new BCF2Codec().canDecode(BCF22_FIXTURE.toString()));
    }

    @Test
    public void canDecodeReturnsTrueForBgzfBcf() throws IOException {
        Assert.assertTrue(new BCF2Codec().canDecode(bgzfCopyOf(BCF22_FIXTURE).toString()));
    }

    @Test
    public void canDecodeReturnsFalseForVcf() {
        Assert.assertFalse(new BCF2Codec().canDecode(TEST_DATA_DIR + "VCF4HeaderTest.vcf"));
    }

    // -- BGZF-compressed BCF is read by content, whatever the path --

    @Test
    public void aBgzfCopyOfARawBcfReadsTheSameRecordsThroughVCFFileReader() throws IOException {
        final Path raw = writeBcf(headerWithGtAndAd(), twoRecords(headerWithGtAndAd()));
        assertSameRecords(readAll(bgzfCopyOf(raw)), readAll(raw));
    }

    @Test
    public void aBgzfBcfReadsThroughAbstractFeatureReader() throws IOException {
        final Path raw = writeBcf(headerWithGtAndAd(), twoRecords(headerWithGtAndAd()));
        final List<VariantContext> records = new ArrayList<>();
        try (final AbstractFeatureReader<VariantContext, ?> reader =
                AbstractFeatureReader.getFeatureReader(bgzfCopyOf(raw).toUri().toString(), new BCF2Codec(), false)) {
            for (final VariantContext vc : reader.iterator()) {
                records.add(decodeGenotypes(vc));
            }
        }
        assertSameRecords(records, readAll(raw));
    }

    @Test
    public void aBgzfBcfReadsThroughTheCodecGivenTheDecompressedStream() throws IOException {
        final Path raw = writeBcf(headerWithGtAndAd(), twoRecords(headerWithGtAndAd()));
        final BCF2Codec codec = new BCF2Codec();
        final List<VariantContext> records = new ArrayList<>();
        try (final PositionalBufferedStream pbs = decompressed(bgzfCopyOf(raw))) {
            final FeatureCodecHeader header = codec.readHeader(pbs);
            Assert.assertEquals(header.getHeaderEnd(), headerEndOf(raw), "an offset into the decompressed bytes");
            while (!codec.isDone(pbs)) {
                records.add(decodeGenotypes(codec.decode(pbs)));
            }
        }
        assertSameRecords(records, readAll(raw));
    }

    @Test
    public void aBgzfBcfReadsThroughTheCodecGivenSeparateDecompressedStreams() throws IOException {
        final Path raw = writeBcf(headerWithGtAndAd(), twoRecords(headerWithGtAndAd()));
        final Path bgzf = bgzfCopyOf(raw);
        final BCF2Codec codec = new BCF2Codec();
        final List<VariantContext> records = new ArrayList<>();
        final FeatureCodecHeader header;
        try (final PositionalBufferedStream headerStream = decompressed(bgzf)) {
            header = codec.readHeader(headerStream);
        }
        try (final PositionalBufferedStream recordStream = decompressed(bgzf)) {
            recordStream.skip(header.getHeaderEnd());
            while (!codec.isDone(recordStream)) {
                records.add(decodeGenotypes(codec.decode(recordStream)));
            }
        }
        assertSameRecords(records, readAll(raw));
    }

    @Test
    public void twoStreamsDecodedAlternatelyThroughOneCodecBothDecodeCorrectly() throws IOException {
        final Path raw = writeBcf(headerWithGtAndAd(), twoRecords(headerWithGtAndAd()));
        final BCF2Codec codec = new BCF2Codec();
        final List<VariantContext> expected = readAll(raw);

        final FeatureCodecHeader header;
        try (final PositionalBufferedStream headerStream = new PositionalBufferedStream(Files.newInputStream(raw))) {
            header = codec.readHeader(headerStream);
        }

        try (final PositionalBufferedStream streamA = new PositionalBufferedStream(Files.newInputStream(raw));
                final PositionalBufferedStream streamB = new PositionalBufferedStream(Files.newInputStream(raw))) {
            streamA.skip(header.getHeaderEnd());
            streamB.skip(header.getHeaderEnd());

            final VariantContext a1 = decodeGenotypes(codec.decode(streamA));
            final VariantContext b1 = decodeGenotypes(codec.decode(streamB));

            // close A; B is independent of it and must still decode
            codec.close(streamA);

            final VariantContext b2 = decodeGenotypes(codec.decode(streamB));
            Assert.assertTrue(codec.isDone(streamB));

            assertSameRecords(List.of(a1), List.of(expected.get(0)));
            assertSameRecords(List.of(b1, b2), expected);
            codec.close(streamB);
        }
    }

    // -- A compressed stream handed to the codec --

    @Test
    public void aCompressedStreamIsRefusedWithAMessageSayingSo() throws IOException {
        final Path bgzf = bgzfCopyOf(writeBcf(headerWithGtAndAd(), twoRecords(headerWithGtAndAd())));
        try (final PositionalBufferedStream pbs = new PositionalBufferedStream(Files.newInputStream(bgzf))) {
            final TribbleException e =
                    Assert.expectThrows(TribbleException.class, () -> new BCF2Codec().readHeader(pbs));
            Assert.assertTrue(e.getMessage().contains("must be decompressed"), e.getMessage());
        }
    }

    @Test
    public void aCompressedStreamIsRefusedForIndexingWithAMessageSayingSo() throws IOException {
        final Path bgzf = bgzfCopyOf(writeBcf(headerWithGtAndAd(), twoRecords(headerWithGtAndAd())));
        try (final InputStream in = Files.newInputStream(bgzf)) {
            final TribbleException e = Assert.expectThrows(
                    TribbleException.class, () -> new BCF2Codec().makeIndexableSourceFromStream(in));
            Assert.assertTrue(e.getMessage().contains("must be decompressed"), e.getMessage());
        }
    }

    @Test
    public void aBgzfBcfCannotBeIndexedWithATribbleIndex() throws IOException {
        final Path raw = writeBcf(headerWithGtAndAd(), twoRecords(headerWithGtAndAd()));
        final Path bgzf = bgzfCopyOf(raw);
        final TribbleException e = Assert.expectThrows(
                TribbleException.class, () -> IndexFactory.createLinearIndex(bgzf, new BCF2Codec()));
        Assert.assertTrue(e.getMessage().contains("must be decompressed"), e.getMessage());
        Assert.assertNotNull(IndexFactory.createLinearIndex(raw, new BCF2Codec()));
    }

    @Test
    public void aBcfFromBcftoolsReadsThroughVCFFileReader() throws IOException {
        final Path vcf = writeVcf(A42_VCF);
        assertSameRecords(readAll(bcftools(vcf, "view", "-Ob")), readAll(vcf));
    }

    @Test
    public void anUncompressedBcfFromBcftoolsReadsThroughVCFFileReader() throws IOException {
        final Path vcf = writeVcf(A42_VCF);
        assertSameRecords(readAll(bcftools(vcf, "view", "-Ou")), readAll(vcf));
    }

    // -- IDX --

    @Test
    public void idxIsAbsentFromTheExposedHeader() throws IOException {
        final VCFHeader header = headerOf(bcftools(writeVcf(A42_VCF), "view", "-Ob"));
        for (final VCFHeaderLine line : header.getMetaDataInInputOrder()) {
            Assert.assertNull(BCFDictionary.idxAttribute(line), line.toString());
        }
        Assert.assertEquals(header.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_2);
        Assert.assertEquals(header.getGenotypeSamples(), List.of("s1", "s2", "s3"));
        Assert.assertEquals(header.getContigLines().get(0).getID(), "chr1");
        Assert.assertEquals(header.getInfoHeaderLine("AF").getDescription(), "af");
        Assert.assertEquals(header.getFormatHeaderLine("FS").getType(), VCFHeaderLineType.String);
    }

    @Test
    public void theExposedHeaderLinesCanBeFoundAndReplaced() throws IOException {
        final VCFHeader header = headerOf(bcftools(writeVcf(A42_VCF), "view", "-Ob"));
        Assert.assertTrue(header.getMetaDataInInputOrder().contains(header.getInfoHeaderLine("DP")));
        Assert.assertTrue(header.getMetaDataInInputOrder()
                .contains(header.getFilterLines().get(0)));
        Assert.assertTrue(header.getMetaDataInInputOrder()
                .contains(header.getContigLines().get(0)));

        header.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chrZ", 10))));
        Assert.assertEquals(header.getContigLines().size(), 1);
        Assert.assertEquals(header.getContigLines().get(0).getID(), "chrZ");
        Assert.assertEquals(
                header.getMetaDataInInputOrder().stream()
                        .filter(l -> l.getKey().equals(VCFHeader.CONTIG_KEY))
                        .count(),
                1);
    }

    @Test
    public void aSparseIdxDictionaryDecodesKeysCorrectly() throws IOException {
        final Path bcf = bcftools(writeVcf(A42_VCF), "annotate", "-x", "INFO/DP,INFO/AF", "-Ob");
        final List<VariantContext> records = readAll(bcf);
        final VariantContext first = records.get(0);
        Assert.assertFalse(first.hasAttribute("DP"));
        Assert.assertFalse(first.hasAttribute("AF"));
        Assert.assertEquals(first.getAttribute("STR"), List.of("a", "b"));
        Assert.assertEquals(first.getAttribute("FLG"), true);
        Assert.assertEquals(first.getGenotype("s1").getDP(), -125);
        Assert.assertEquals(first.getGenotype("s1").getExtendedAttribute("FS"), "x");
        Assert.assertEquals(records.get(1).getFilters(), Set.of("q10"));
        assertSameRecords(records, readAll(bcftools(bcf, "view", "-Ov")));
    }

    // -- Values as the VCF text reader holds them --

    @Test
    public void aPercentEncodedStringInA43BcfIsDecodedAsTheVcfReaderDecodesIt() throws IOException {
        final Path vcf = writeVcf(
                "##fileformat=VCFv4.3",
                "##INFO=<ID=STR,Number=1,Type=String,Description=\"str\">",
                "##INFO=<ID=LST,Number=.,Type=String,Description=\"lst\">",
                "##FORMAT=<ID=GT,Number=1,Type=String,Description=\"gt\">",
                "##FORMAT=<ID=FS,Number=1,Type=String,Description=\"fs\">",
                "##contig=<ID=chr1,length=1000>",
                "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\ts1",
                "chr1\t100\t.\tA\tC\t.\t.\tSTR=a%3Bb;LST=x%2Cy,z\tGT:FS\t0/1:p%3Aq");
        final List<VariantContext> fromBcf = readAll(bcftools(vcf, "view", "-Ob"));
        Assert.assertEquals(fromBcf.get(0).getAttribute("STR"), "a;b");
        Assert.assertEquals(fromBcf.get(0).getAttribute("LST"), List.of("x,y", "z"));
        Assert.assertEquals(fromBcf.get(0).getGenotype("s1").getExtendedAttribute("FS"), "p:q");
        assertSameRecords(fromBcf, readAll(vcf));
    }

    @Test
    public void aPercentEncodedStringInA42BcfIsKeptAsItIs() throws IOException {
        final Path vcf = writeVcf(
                "##fileformat=VCFv4.2",
                "##INFO=<ID=STR,Number=1,Type=String,Description=\"str\">",
                "##contig=<ID=chr1,length=1000>",
                "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO",
                "chr1\t100\t.\tA\tC\t.\t.\tSTR=a%3Bb");
        Assert.assertEquals(readAll(bcftools(vcf, "view", "-Ob")).get(0).getAttribute("STR"), "a%3Bb");
    }

    @Test
    public void aMissingFormatStringFromBcftoolsIsAbsent() throws IOException {
        final Genotype s2 =
                readAll(bcftools(writeVcf(A42_VCF), "view", "-Ob")).get(0).getGenotype("s2");
        Assert.assertFalse(s2.hasExtendedAttribute("FS"));
        Assert.assertEquals(s2.getDP(), 7);
    }

    @Test
    public void aMissingFtFromBcftoolsIsNotAFilter() throws IOException {
        final Path vcf = writeVcf(
                "##fileformat=VCFv4.2",
                "##FILTER=<ID=q10,Description=\"q10\">",
                "##FORMAT=<ID=GT,Number=1,Type=String,Description=\"gt\">",
                "##FORMAT=<ID=FT,Number=1,Type=String,Description=\"ft\">",
                "##contig=<ID=chr1,length=1000>",
                "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\ts1\ts2",
                "chr1\t100\t.\tA\tC\t.\t.\t.\tGT:FT\t0/1:.\t0/1:q10");
        final VariantContext fromBcf = readAll(bcftools(vcf, "view", "-Ob")).get(0);
        Assert.assertFalse(fromBcf.getGenotype("s1").isFiltered());
        Assert.assertNull(fromBcf.getGenotype("s1").getFilters());
        Assert.assertEquals(fromBcf.getGenotype("s2").getFilters(), "q10");
        assertSameRecords(List.of(fromBcf), readAll(vcf));
    }

    @Test
    public void anAdWithAnInteriorMissingValueDecodesAsTheVcfReaderDecodesIt() throws IOException {
        final Path vcf = writeVcf(A42_VCF);
        final Genotype fromBcf = readAll(bcftools(vcf, "view", "-Ob")).get(0).getGenotype("s1");
        final Genotype fromVcf = readAll(vcf).get(0).getGenotype("s1");
        Assert.assertFalse(fromVcf.hasAD(), "the text reader cannot hold 10,.,5 in an int[]");
        Assert.assertFalse(fromBcf.hasAD());
        Assert.assertEquals(fromBcf.getDP(), -125, "the field after AD is still read from the right bytes");
    }

    @Test
    public void anAdShorterThanTheSitesAlleleCountDecodesToItsValues() throws IOException {
        final Path vcf = writeVcf(A42_VCF);
        final Genotype fromBcf = readAll(bcftools(vcf, "view", "-Ob")).get(0).getGenotype("s2");
        Assert.assertEquals(fromBcf.getAD(), new int[] {3, 4});
    }

    @Test
    public void anAdPaddedWithMissingByHtsjdkKeepsItsValues() throws IOException {
        final VCFHeader header = headerWithGtAndAd();
        final Allele ref = Allele.create("A", true);
        final Allele alt1 = Allele.create("C");
        final Allele alt2 = Allele.create("G");
        final VariantContext vc = new VariantContextBuilder("t", "1", 100, 100, List.of(ref, alt1, alt2))
                .genotypes(new GenotypeBuilder("s1", List.of(ref, alt1))
                        .AD(new int[] {10})
                        .make())
                .make();
        final Path bcf = writeBcf(header, List.of(vc));
        Assert.assertEquals(readAll(bcf).get(0).getGenotype("s1").getAD(), new int[] {10});
    }

    @Test
    public void anInfoFlagFromBcftoolsIsTrue() throws IOException {
        final VariantContext first =
                readAll(bcftools(writeVcf(A42_VCF), "view", "-Ob")).get(0);
        Assert.assertEquals(first.getAttribute("FLG"), true);
        Assert.assertEquals(first.getAttribute("STR"), List.of("a", "b"));
        Assert.assertEquals(first.getAttributeAsInt("DP", 0), -125);
    }

    // -- Genotypes: ploidy and phase --

    @Test
    public void mixedPloidyFromBcftoolsDecodes() throws IOException {
        final VariantContext second =
                readAll(bcftools(writeVcf(A42_VCF), "view", "-Ob")).get(1);
        final Genotype s1 = second.getGenotype("s1");
        Assert.assertEquals(s1.getGenotypeString(), "G/T|A");
        Assert.assertTrue(s1.hasPerAllelePhasing());
        final Genotype s2 = second.getGenotype("s2");
        Assert.assertEquals(s2.getPloidy(), 2);
        Assert.assertEquals(s2.getGenotypeString(), "A|G");
        Assert.assertTrue(s2.isPhased());
        final Genotype s3 = second.getGenotype("s3");
        Assert.assertEquals(s3.getPloidy(), 1);
        Assert.assertTrue(s3.getAllele(0).isNoCall());
        Assert.assertFalse(s3.isPhased());
    }

    @Test
    public void theFirstAllelesPhaseBitIsIgnoredBelow44() throws IOException {
        final Path vcf = writeVcf(gtOnlyVcf("VCFv4.2", "0|1", "0/1", "1", "."));
        final VariantContext vc = readAll(bcftools(vcf, "view", "-Ob")).get(0);
        assertPhasing(vc.getGenotype("s1"), "A|C", true, false);
        assertPhasing(vc.getGenotype("s2"), "A/C", false, false);
        assertPhasing(vc.getGenotype("s3"), "C", false, false);
        assertPhasing(vc.getGenotype("s4"), ".", false, false);
        assertSameRecords(List.of(vc), readAll(vcf));
    }

    @Test
    public void theFirstAllelesPhaseBitIsLiteralAt44() throws IOException {
        final Path vcf = writeVcf(gtOnlyVcf("VCFv4.4", "0|1", "0/1", "|0/1", "1", "|1", "/1", ".", "|."));
        final VariantContext vc = readAll(bcftools(vcf, "view", "-Ob")).get(0);
        final List<VariantContext> fromVcf = readAll(vcf);
        assertPhasing(vc.getGenotype("s1"), "A|C", true, false);
        assertPhasing(vc.getGenotype("s2"), "A/C", false, false);
        assertPhasing(vc.getGenotype("s3"), "|A/C", true, true);
        Assert.assertTrue(vc.getGenotype("s3").isAllelePhased(0));
        Assert.assertFalse(vc.getGenotype("s3").isAllelePhased(1));
        // a haploid call is unphased at every header version, as the text reader reads a bare "1"; the text reader
        // still distinguishes |1 (phased) from /1 (per-allele) from 1 (unphased), so s5, s6 and s8 differ between
        // the two containers until the text reader is revisited
        assertPhasing(vc.getGenotype("s4"), "C", false, false);
        assertPhasing(vc.getGenotype("s5"), "C", false, false);
        assertPhasing(vc.getGenotype("s6"), "C", false, false);
        assertPhasing(vc.getGenotype("s7"), ".", false, false);
        assertPhasing(vc.getGenotype("s8"), ".", false, false);
        for (final String sample : List.of("s1", "s2", "s3", "s4", "s7")) {
            VariantContextTestProvider.assertEquals(
                    vc.getGenotype(sample), fromVcf.get(0).getGenotype(sample));
        }
    }

    @Test
    public void endOfVectorInTheFirstAlleleOfAGtIsAnError() {
        // GT for the one sample is [END_OF_VECTOR, 2], which no writer produces
        final byte[] genotypes = bytes(0x11, 0x02, 0x21, 0x81, 0x02);
        final byte[] bcf = rawBcf(2, 2, ONE_SAMPLE_HEADER, record(sites(0, 99, 1, 0, 1, 1), genotypes));
        final TribbleException e = Assert.expectThrows(TribbleException.class, () -> readAll(bcf));
        Assert.assertTrue(e.getMessage().contains("END_OF_VECTOR"), e.getMessage());
    }

    @Test
    public void theLazyGenotypesCarryTheHeaderVersion() throws IOException {
        final Path bcf = writeBcf(headerWithGtAndAd(), twoRecords(headerWithGtAndAd()));
        try (final VCFFileReader reader = new VCFFileReader(bcf, false);
                final CloseableIterator<VariantContext> records = reader.iterator()) {
            final LazyGenotypesContext genotypes =
                    (LazyGenotypesContext) records.next().getGenotypes();
            Assert.assertEquals(genotypes.getHeaderVersion(), VCFHeaderVersion.VCF4_2);
        }
    }

    // -- Malformed records --

    @Test
    public void theSampleCountIsReadFromTwentyFourBits() {
        // bit 20 of n_sample is set: a 20-bit mask would read 1 sample and decode on, a 24-bit one reads 1048577
        final byte[] bcf = rawBcf(2, 2, ONE_SAMPLE_HEADER, record(sites(0, 99, 1, 0, 1, 0x100001), new byte[0]));
        final TribbleException e = Assert.expectThrows(TribbleException.class, () -> readAll(bcf));
        Assert.assertTrue(e.getMessage().contains("1048577 samples"), e.getMessage());
    }

    @Test
    public void aContigIndexOutsideTheHeaderIsAnError() {
        final byte[] bcf =
                rawBcf(2, 2, ONE_SAMPLE_HEADER, record(sites(7, 99, 1, 0, 0, 1), bytes(0x11, 0x02, 0x21, 2, 4)));
        final TribbleException e = Assert.expectThrows(TribbleException.class, () -> readAll(bcf));
        Assert.assertTrue(e.getMessage().contains("contig index 7"), e.getMessage());
    }

    @Test
    public void aDictionaryIndexOutsideTheHeaderIsAnError() {
        // one INFO field whose key is 9; the header's dictionary is PASS, DP, GT
        final byte[] info = bytes(0x11, 9, 0x11, 1);
        final byte[] bcf =
                rawBcf(2, 2, ONE_SAMPLE_HEADER, record(sites(0, 99, 1, info, 1, 1, 1), bytes(0x11, 0x02, 0x21, 2, 4)));
        final TribbleException e = Assert.expectThrows(TribbleException.class, () -> readAll(bcf));
        Assert.assertTrue(e.getMessage().contains("dictionary index 9"), e.getMessage());
    }

    @Test
    public void anErrorAfterTheSiteIsDecodedCitesItsChromAndPos() {
        // one INFO field whose key is 9; the header's dictionary is PASS, DP, GT
        final byte[] info = bytes(0x11, 9, 0x11, 1);
        final byte[] bcf =
                rawBcf(2, 2, ONE_SAMPLE_HEADER, record(sites(0, 99, 1, info, 1, 1, 1), bytes(0x11, 0x02, 0x21, 2, 4)));
        final TribbleException e = Assert.expectThrows(TribbleException.class, () -> readAll(bcf));
        Assert.assertTrue(e.getMessage().contains("chr1:100"), e.getMessage());
    }

    // -- Truncated records --

    @Test
    public void aTruncatedSitesBlockThrowsTribbleExceptionNotAioobe() {
        // The sites block declares an INT32 contig offset (4 bytes) but the block is only 2 bytes long
        final byte[] shortSites = bytes(0x11, 0x01);
        final byte[] bcf = rawBcf(2, 2, ONE_SAMPLE_HEADER, record(shortSites, bytes(0x11, 0x02, 0x21, 2, 4)));
        final TribbleException e = Assert.expectThrows(TribbleException.class, () -> readAll(bcf));
        Assert.assertTrue(e.getMessage().contains("truncated"), e.getMessage());
        Assert.assertTrue(e.getCause() instanceof ArrayIndexOutOfBoundsException);
    }

    @Test
    public void aTruncatedGenotypeBlockThrowsTribbleExceptionFromGetGenotypes() {
        // Valid sites block but genotype block is 1 byte (too short for a GT field)
        final byte[] shortGenotypes = bytes(0x11);
        final byte[] bcf = rawBcf(2, 2, ONE_SAMPLE_HEADER, record(sites(0, 99, 1, 0, 1, 1), shortGenotypes));
        final List<VariantContext> records = new ArrayList<>();
        try (final PositionalBufferedStream pbs = new PositionalBufferedStream(new ByteArrayInputStream(bcf))) {
            final BCF2Codec codec = new BCF2Codec();
            codec.readHeader(pbs);
            while (!codec.isDone(pbs)) {
                records.add(codec.decode(pbs));
            }
        }
        Assert.assertEquals(records.size(), 1);
        // getGenotype forces the lazy decode, which hits the truncated genotype block
        final TribbleException e =
                Assert.expectThrows(TribbleException.class, () -> records.get(0).getGenotype("s1"));
        Assert.assertTrue(e.getMessage().contains("truncated"), e.getMessage());
        Assert.assertTrue(e.getCause() instanceof ArrayIndexOutOfBoundsException);
    }

    @Test
    public void aRecordFromTheCurrentWriterRoundTrips() throws IOException {
        final VCFHeader header = headerWithGtAndAd();
        final List<VariantContext> written = twoRecords(header);
        assertSameRecords(readAll(writeBcf(header, written)), written);
    }

    // -- Fixtures --

    private static final String[] A42_VCF = {
        "##fileformat=VCFv4.2",
        "##FILTER=<ID=q10,Description=\"q10\">",
        "##INFO=<ID=DP,Number=1,Type=Integer,Description=\"dp\">",
        "##INFO=<ID=AF,Number=A,Type=Float,Description=\"af\">",
        "##INFO=<ID=STR,Number=1,Type=String,Description=\"str\">",
        "##INFO=<ID=FLG,Number=0,Type=Flag,Description=\"flag\">",
        "##FORMAT=<ID=GT,Number=1,Type=String,Description=\"gt\">",
        "##FORMAT=<ID=AD,Number=.,Type=Integer,Description=\"ad\">",
        "##FORMAT=<ID=FS,Number=1,Type=String,Description=\"fs\">",
        "##FORMAT=<ID=DP,Number=1,Type=Integer,Description=\"dp\">",
        "##contig=<ID=chr1,length=1000>",
        "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\ts1\ts2\ts3",
        "chr1\t100\t.\tA\tC\t50\tPASS\tDP=-125;AF=0.5;STR=a,b;FLG\tGT:AD:FS:DP\t0|1:10,.,5:x:-125\t1:3,4:.:7\t./.:.:y:.",
        "chr1\t200\t.\tG\tT,A\t.\tq10\t.\tGT\t0/1|2\t2|0\t."
    };

    /** A GT-only VCF of the given version with one record whose samples s1, s2, ... have the given genotypes. */
    private static String[] gtOnlyVcf(final String version, final String... genotypes) {
        final StringBuilder samples = new StringBuilder();
        final StringBuilder record = new StringBuilder("chr1\t100\t.\tA\tC\t.\t.\t.\tGT");
        for (int i = 0; i < genotypes.length; i++) {
            samples.append("\ts").append(i + 1);
            record.append('\t').append(genotypes[i]);
        }
        return new String[] {
            "##fileformat=" + version,
            "##FORMAT=<ID=GT,Number=1,Type=String,Description=\"gt\">",
            "##contig=<ID=chr1,length=1000>",
            "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT" + samples,
            record.toString()
        };
    }

    private static final String ONE_SAMPLE_HEADER = "##fileformat=VCFv4.2\n"
            + "##contig=<ID=chr1,length=1000>\n"
            + "##INFO=<ID=DP,Number=1,Type=Integer,Description=\"dp\">\n"
            + "##FORMAT=<ID=GT,Number=1,Type=String,Description=\"gt\">\n"
            + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\ts1\n";

    private static VCFHeader headerWithGtAndAd() {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "genotype"));
        lines.add(new VCFFormatHeaderLine("AD", VCFHeaderLineCount.R, VCFHeaderLineType.Integer, "depths"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1", "s2"));
        header.setSequenceDictionary(createArtificialSequenceDictionary());
        return header;
    }

    private static List<VariantContext> twoRecords(final VCFHeader header) {
        final Allele ref = Allele.create("A", true);
        final Allele alt = Allele.create("C");
        final VariantContext first = new VariantContextBuilder("t", "1", 100, 100, List.of(ref, alt))
                .genotypes(
                        new GenotypeBuilder("s1", List.of(ref, alt))
                                .phased(true)
                                .AD(new int[] {10, 5})
                                .make(),
                        new GenotypeBuilder("s2", List.of(alt, alt))
                                .AD(new int[] {0, 9})
                                .make())
                .make();
        final VariantContext second = new VariantContextBuilder("t", "2", 300, 300, List.of(ref, alt))
                .id("rs1")
                .genotypes(
                        new GenotypeBuilder("s1", List.of(ref)).make(),
                        new GenotypeBuilder("s2", List.of(Allele.NO_CALL, Allele.NO_CALL)).make())
                .make();
        return List.of(first, second);
    }

    // -- Helpers --

    private Path writeVcf(final String... lines) throws IOException {
        final Path vcf = Files.createTempFile(tempDir, "in", ".vcf");
        Files.write(vcf, List.of(lines), StandardCharsets.UTF_8);
        return vcf;
    }

    private Path writeBcf(final VCFHeader header, final List<VariantContext> records) throws IOException {
        final Path bcf = Files.createTempFile(tempDir, "out", ".bcf");
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .clearOptions()
                .setOutputPath(bcf)
                .setOutputFileType(VariantContextWriterBuilder.OutputType.BCF)
                .setBCFVersion(BCFVersion.BCF_2_1)
                .build()) {
            writer.writeHeader(header);
            records.forEach(writer::add);
        }
        return bcf;
    }

    private Path bgzfCopyOf(final Path raw) throws IOException {
        final Path bgzf = Files.createTempFile(tempDir, "bgzf", ".bcf");
        try (final OutputStream out = new BlockCompressedOutputStream(bgzf)) {
            out.write(Files.readAllBytes(raw));
        }
        return bgzf;
    }

    /** The decompressed bytes of a BGZF file, as a reader hands them to the codec. */
    private static PositionalBufferedStream decompressed(final Path bgzf) throws IOException {
        return new PositionalBufferedStream(IOUtil.openGzipOrBgzfStream(Files.newInputStream(bgzf)));
    }

    /** The header end the codec reports for an uncompressed BCF. */
    private static long headerEndOf(final Path rawBcf) throws IOException {
        try (final PositionalBufferedStream pbs = new PositionalBufferedStream(Files.newInputStream(rawBcf))) {
            return new BCF2Codec().readHeader(pbs).getHeaderEnd();
        }
    }

    /** Runs a bcftools command on {@code input} that writes to a new file whose extension matches the output type. */
    private Path bcftools(final Path input, final String command, final String... arguments) throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        final boolean vcfOut = List.of(arguments).contains("-Ov");
        final Path output = Files.createTempFile(tempDir, command, vcfOut ? ".vcf" : ".bcf");
        final List<String> args = new ArrayList<>(List.of(command, "--no-version"));
        args.addAll(List.of(arguments));
        args.addAll(List.of("-o", output.toString(), input.toString()));
        BcftoolsTestUtils.executeBcftoolsForStdout(args.toArray(new String[0]));
        return output;
    }

    private static List<VariantContext> readAll(final Path vcfOrBcf) {
        final List<VariantContext> records = new ArrayList<>();
        try (final VCFFileReader reader = new VCFFileReader(vcfOrBcf, false);
                final CloseableIterator<VariantContext> iterator = reader.iterator()) {
            while (iterator.hasNext()) {
                records.add(decodeGenotypes(iterator.next()));
            }
        }
        return records;
    }

    private static VCFHeader headerOf(final Path vcfOrBcf) {
        try (final VCFFileReader reader = new VCFFileReader(vcfOrBcf, false)) {
            return reader.getFileHeader();
        }
    }

    private static List<VariantContext> readAll(final byte[] rawBcf) {
        return readAll(new BCF2Codec(), rawBcf);
    }

    private static List<VariantContext> readAll(final BCF2Codec codec, final byte[] rawBcf) {
        final List<VariantContext> records = new ArrayList<>();
        try (final PositionalBufferedStream pbs = new PositionalBufferedStream(new ByteArrayInputStream(rawBcf))) {
            codec.readHeader(pbs);
            while (!codec.isDone(pbs)) {
                records.add(decodeGenotypes(codec.decode(pbs)));
            }
        }
        return records;
    }

    private static VariantContext decodeGenotypes(final VariantContext vc) {
        for (final Genotype genotype : vc.getGenotypes()) {
            genotype.getAlleles();
        }
        return vc;
    }

    /**
     * The two readers hold the same record alike except for the types of INFO values, which the text reader keeps
     * as Strings and the BCF reader as numbers, so INFO values are compared as text.
     */
    private static void assertSameRecords(final List<VariantContext> actual, final List<VariantContext> expected) {
        Assert.assertEquals(actual.size(), expected.size(), "record count");
        for (int i = 0; i < expected.size(); i++) {
            final VariantContext a = actual.get(i);
            final VariantContext e = expected.get(i);
            Assert.assertEquals(a.getContig(), e.getContig());
            Assert.assertEquals(a.getStart(), e.getStart());
            Assert.assertEquals(a.getEnd(), e.getEnd());
            Assert.assertEquals(a.getID(), e.getID());
            Assert.assertEquals(a.getAlleles(), e.getAlleles());
            assertEqualsDoubleSmart(a.getPhredScaledQual(), e.getPhredScaledQual());
            Assert.assertEquals(a.filtersWereApplied(), e.filtersWereApplied());
            Assert.assertEquals(a.getFilters(), e.getFilters());
            Assert.assertEquals(a.getAttributes().keySet(), e.getAttributes().keySet(), "INFO keys");
            for (final String key : e.getAttributes().keySet()) {
                Assert.assertEquals(asText(a.getAttribute(key)), asText(e.getAttribute(key)), "INFO " + key);
            }
            Assert.assertEquals(a.getSampleNamesOrderedByName(), e.getSampleNamesOrderedByName());
            for (final String sample : e.getSampleNames()) {
                VariantContextTestProvider.assertEquals(a.getGenotype(sample), e.getGenotype(sample));
            }
        }
    }

    private static Object asText(final Object value) {
        if (value instanceof List) {
            final List<String> texts = new ArrayList<>();
            for (final Object element : (List<?>) value) texts.add(String.valueOf(element));
            return texts;
        }
        return String.valueOf(value);
    }

    private static void assertPhasing(
            final Genotype g, final String gtString, final boolean phased, final boolean perAllele) {
        Assert.assertEquals(g.getGenotypeString(), gtString, g.getSampleName());
        Assert.assertEquals(g.isPhased(), phased, g.getSampleName() + " isPhased");
        Assert.assertEquals(g.hasPerAllelePhasing(), perAllele, g.getSampleName() + " hasPerAllelePhasing");
    }

    // ============================================================
    // Codec round-trip tests
    // ============================================================

    @Test
    public void aRecordWrittenAs22ReadsBackIdentically() throws IOException {
        final VCFHeader header = multiFieldHeader();
        final List<VariantContext> written = multiFieldRecords(header);
        final Path bcf = writeBcf22(header, written);
        assertMultiFieldRoundTrip(readAll(bcf), written);
    }

    @Test
    public void aRecordWrittenAs21ReadsBackIdentically() throws IOException {
        final VCFHeader header = multiFieldHeader();
        final List<VariantContext> written = multiFieldRecords(header);
        final Path bcf = writeBcf(header, written);
        assertMultiFieldRoundTrip(readAll(bcf), written);
    }

    private static void assertMultiFieldRoundTrip(
            final List<VariantContext> actual, final List<VariantContext> expected) {
        Assert.assertEquals(actual.size(), expected.size());
        for (int i = 0; i < expected.size(); i++) {
            final VariantContext a = actual.get(i);
            final VariantContext e = expected.get(i);
            Assert.assertEquals(a.getContig(), e.getContig());
            Assert.assertEquals(a.getStart(), e.getStart());
            Assert.assertEquals(a.getAlleles(), e.getAlleles());
            Assert.assertEquals(a.filtersWereApplied(), e.filtersWereApplied());
            Assert.assertEquals(a.getFilters(), e.getFilters());
            for (final String key : e.getAttributes().keySet()) {
                Assert.assertEquals(asText(a.getAttribute(key)), asText(e.getAttribute(key)), "INFO " + key);
            }
            for (final String sample : e.getSampleNames()) {
                final Genotype ga = a.getGenotype(sample);
                final Genotype ge = e.getGenotype(sample);
                Assert.assertEquals(ga.getGenotypeString(), ge.getGenotypeString(), sample + " GT");
                Assert.assertEquals(ga.getGQ(), ge.getGQ(), sample + " GQ");
                Assert.assertEquals(
                        String.valueOf(ga.getExtendedAttribute("FS")),
                        String.valueOf(ge.getExtendedAttribute("FS")),
                        sample + " FS");
                Assert.assertEquals(
                        asText(ga.getExtendedAttribute("XI")), asText(ge.getExtendedAttribute("XI")), sample + " XI");
            }
        }
    }

    @Test
    public void a22FileFromHtsjdkAndFromBcftoolsAreEquivalent() throws IOException {
        if (!BcftoolsTestUtils.isBcftoolsAvailable()) throw new SkipException("bcftools not available");
        // A simple VCF without fields that cause fullyDecode issues (STR=a,b with Number=1)
        final String[] simpleVcf = {
            "##fileformat=VCFv4.2",
            "##INFO=<ID=DP,Number=1,Type=Integer,Description=\"dp\">",
            "##FORMAT=<ID=GT,Number=1,Type=String,Description=\"gt\">",
            "##FORMAT=<ID=GQ,Number=1,Type=Integer,Description=\"gq\">",
            "##contig=<ID=chr1,length=1000>",
            "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\ts1",
            "chr1\t100\t.\tA\tC\t50\tPASS\tDP=42\tGT:GQ\t0/1:30"
        };
        final Path vcf = writeVcf(simpleVcf);
        final List<VariantContext> fromVcf = readAll(vcf);
        final Path bcf22 = writeBcf22(headerOf(vcf), fromVcf);
        final Path bcfBcftools = bcftools(vcf, "view", "-Ob");
        assertSameRecords(readAll(bcf22), readAll(bcfBcftools));
    }

    @Test
    public void aSparseIdxHeaderRoundTrips() throws IOException {
        // A header as bcftools annotate -x leaves it, with sparse IDX
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFInfoHeaderLine(
                "<ID=AF,Number=A,Type=Float,Description=\"af\",IDX=10>", VCFHeaderVersion.VCF4_2));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1"));
        header.setSequenceDictionary(createArtificialSequenceDictionary());
        final Allele ref = Allele.create("A", true);
        final Allele alt = Allele.create("C");
        final VariantContext vc = new VariantContextBuilder("t", "1", 100, 100, List.of(ref, alt))
                .attribute("AF", 0.5)
                .genotypes(new GenotypeBuilder("s1", List.of(ref, alt)).make())
                .make();
        final Path bcf = writeBcf22(header, List.of(vc));
        final List<VariantContext> read = readAll(bcf);
        Assert.assertEquals(read.size(), 1);
        Assert.assertTrue(read.get(0).hasAttribute("AF"));
    }

    // -- Multi-field fixtures for round-trip tests --

    private VCFHeader multiFieldHeader() {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(new VCFFilterHeaderLine("q10", "q10"));
        lines.add(new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "dp"));
        lines.add(new VCFInfoHeaderLine("FLG", 0, VCFHeaderLineType.Flag, "flag"));
        lines.add(new VCFInfoHeaderLine("STR", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "str"));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        lines.add(new VCFFormatHeaderLine("GQ", 1, VCFHeaderLineType.Integer, "gq"));
        lines.add(new VCFFormatHeaderLine("FS", 1, VCFHeaderLineType.String, "fs"));
        lines.add(new VCFFormatHeaderLine("XI", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "xi"));
        final VCFHeader header = new VCFHeader(lines, List.of("s1", "s2"));
        header.setSequenceDictionary(createArtificialSequenceDictionary());
        return header;
    }

    private List<VariantContext> multiFieldRecords(final VCFHeader header) {
        final Allele ref = Allele.create("A", true);
        final Allele alt = Allele.create("C");
        final VariantContext vc1 = new VariantContextBuilder("t", "1", 100, 100, List.of(ref, alt))
                .attribute("DP", 42)
                .attribute("FLG", true)
                .attribute("STR", List.of("a", "b"))
                .genotypes(
                        new GenotypeBuilder("s1", List.of(ref, alt))
                                .phased(true)
                                .GQ(30)
                                .attribute("FS", "x")
                                .attribute("XI", List.of(10, 5))
                                .make(),
                        new GenotypeBuilder("s2", List.of(alt, alt))
                                .GQ(20)
                                .attribute("XI", List.of(7, 3))
                                .make())
                .make();
        final VariantContext vc2 = new VariantContextBuilder("t", "2", 200, 200, List.of(ref, alt))
                .log10PError(-5.0)
                .genotypes(
                        new GenotypeBuilder("s1", List.of(ref)).make(),
                        new GenotypeBuilder("s2", List.of(Allele.NO_CALL, Allele.NO_CALL)).make())
                .make();
        return List.of(vc1, vc2);
    }

    private Path writeBcf22(final VCFHeader header, final List<VariantContext> records) throws IOException {
        final Path bcf = Files.createTempFile(tempDir, "out22", ".bcf");
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .clearOptions()
                .setOutputPath(bcf)
                .setOutputFileType(VariantContextWriterBuilder.OutputType.BCF)
                .build()) {
            writer.writeHeader(header);
            records.forEach(writer::add);
        }
        return bcf;
    }

    // -- Raw BCF assembly, for records no writer produces --

    private static byte[] bytes(final int... values) {
        final byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) bytes[i] = (byte) values[i];
        return bytes;
    }

    /** A raw BCF file: magic, version, the NUL-terminated header text and the given records. */
    private static byte[] rawBcf(final int major, final int minor, final String headerText, final byte[]... records) {
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(BCFVersion.MAGIC_HEADER_START);
            out.write(major);
            out.write(minor);
            final byte[] text = (headerText + "\0").getBytes(StandardCharsets.UTF_8);
            BCF2Type.INT32.write(text.length, out);
            out.write(text);
            for (final byte[] record : records) out.write(record);
            return out.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] record(final byte[] shared, final byte[] indiv) {
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            BCF2Type.INT32.write(shared.length, out);
            BCF2Type.INT32.write(indiv.length, out);
            out.write(shared);
            out.write(indiv);
            return out.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] sites(
            final int rid, final int pos0, final int rlen, final int nInfo, final int nFormat, final int nSample) {
        return sites(rid, pos0, rlen, new byte[0], nInfo, nFormat, nSample);
    }

    /** The shared block of a biallelic A/C site with no ID, no FILTER and the given encoded INFO fields. */
    private static byte[] sites(
            final int rid,
            final int pos0,
            final int rlen,
            final byte[] info,
            final int nInfo,
            final int nFormat,
            final int nSample) {
        try {
            final BCF2Encoder encoder = new BCF2Encoder();
            encoder.encodeRawInt(rid, BCF2Type.INT32);
            encoder.encodeRawInt(pos0, BCF2Type.INT32);
            encoder.encodeRawInt(rlen, BCF2Type.INT32);
            encoder.encodeRawBytes(BCF2Type.FLOAT.getMissingBytes(), BCF2Type.FLOAT);
            encoder.encodeRawInt((2 << 16) | nInfo, BCF2Type.INT32);
            encoder.encodeRawInt((nFormat << 24) | nSample, BCF2Type.INT32);
            encoder.encodeTypedMissing(BCF2Type.CHAR);
            encoder.encodeTypedString("A");
            encoder.encodeTypedString("C");
            encoder.encodeTypedMissing(BCF2Type.INT8);
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(encoder.getRecordBytes());
            out.write(info);
            return out.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
