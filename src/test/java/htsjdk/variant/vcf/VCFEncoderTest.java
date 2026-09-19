package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.util.ParsingUtils;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.LazyGenotypesContext;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class VCFEncoderTest extends HtsjdkTest {

    @DataProvider(name = "VCFWriterDoubleFormatTestData")
    public Object[][] makeVCFWriterDoubleFormatTestData() {
        final List<Object[]> tests = new ArrayList<>();
        tests.add(new Object[] {1.0, "1.00"});
        tests.add(new Object[] {10.1, "10.10"});
        tests.add(new Object[] {10.01, "10.01"});
        tests.add(new Object[] {10.012, "10.01"});
        tests.add(new Object[] {10.015, "10.02"});
        tests.add(new Object[] {0.0, "0.00"});
        tests.add(new Object[] {0.5, "0.500"});
        tests.add(new Object[] {0.55, "0.550"});
        tests.add(new Object[] {0.555, "0.555"});
        tests.add(new Object[] {0.5555, "0.556"});
        tests.add(new Object[] {0.1, "0.100"});
        tests.add(new Object[] {0.050, "0.050"});
        tests.add(new Object[] {0.010, "0.010"});
        tests.add(new Object[] {0.012, "0.012"});
        tests.add(new Object[] {0.0012, "1.200e-03"});
        tests.add(new Object[] {1.2e-4, "1.200e-04"});
        tests.add(new Object[] {1.21e-4, "1.210e-04"});
        tests.add(new Object[] {1.212e-5, "1.212e-05"});
        tests.add(new Object[] {1.2123e-6, "1.212e-06"});
        tests.add(new Object[] {Double.POSITIVE_INFINITY, "Infinity"});
        tests.add(new Object[] {Double.NEGATIVE_INFINITY, "-Infinity"});
        tests.add(new Object[] {Double.NaN, "NaN"});
        return tests.toArray(new Object[][] {});
    }

    @Test(dataProvider = "VCFWriterDoubleFormatTestData")
    public void testVCFWriterDoubleFormatTestData(final double d, final String expected) {
        Assert.assertEquals(VCFEncoder.formatVCFDouble(d), expected, "Failed to pretty print double in VCFWriter");
    }

    /**
     * test for https://github.com/samtools/htsjdk/issues/1510
     */
    @Test(dataProvider = "VCFWriterDoubleFormatTestData")
    public void testWriteDoubleIsNotLocaleSensitive(final double d, final String expected) {
        final Locale originalDefault = Locale.getDefault();
        try {
            // Italy locale uses "," instead of "." which produces different results than desired.
            Locale.setDefault(Locale.ITALY);
            Assert.assertEquals(VCFEncoder.formatVCFDouble(d), expected, "Failed to pretty print double in VCFWriter");
        } finally {
            Locale.setDefault(originalDefault);
        }
    }

    /**
     * test for https://github.com/samtools/htsjdk/issues/1510
     */
    @Test
    public void testQualIsNotLocaleSensitive() {
        final Locale originalDefault = Locale.getDefault();
        try {
            // Italy locale uses "," instead of "." which produces different results than desired.
            Locale.setDefault(Locale.ITALY);
            final VariantContext vc = new VariantContextBuilder(
                            "test", "1", 100, 100, Arrays.asList(Allele.REF_A, Allele.ALT_C))
                    .log10PError(0.0)
                    .make();
            final VCFEncoder encoder =
                    new VCFEncoder(createSyntheticHeader(Collections.singletonList("Sample1")), true, false);
            final String encoded = encoder.encode(vc);
            Assert.assertEquals(encoded, "1\t100\t.\tA\tC\t0\t.\t.\tGT\t./.");
        } finally {
            Locale.setDefault(originalDefault);
        }
    }

    @DataProvider(name = "MissingFormatTestData")
    public Object[][] makeMissingFormatTestData() {
        final VCFHeader header = createSyntheticHeader(Collections.singletonList("Sample1"));

        final VCFEncoder dropMissing = new VCFEncoder(header, false, false);
        final VCFEncoder keepMissing = new VCFEncoder(header, false, true);
        final VariantContextBuilder baseVC = new VariantContextBuilder()
                .chr("1")
                .start(1)
                .stop(1)
                .noID()
                .passFilters()
                .log10PError(1)
                .alleles("A", "C");
        final GenotypeBuilder baseGT =
                new GenotypeBuilder("Sample1").alleles(Arrays.asList(Allele.NO_CALL, Allele.NO_CALL));
        final Map<Allele, String> alleleMap = new HashMap<>(3);
        final List<String> formatKeys = Arrays.asList("GT", "AA", "BB");
        alleleMap.put(Allele.NO_CALL, VCFConstants.EMPTY_ALLELE);
        alleleMap.put(Allele.create("A", true), "0");
        alleleMap.put(Allele.create("C", false), "1");

        final List<Object[]> tests = new ArrayList<>();

        VariantContext vc = baseVC.genotypes(baseGT.attribute("AA", "a").make()).make();
        tests.add(new Object[] {dropMissing, vc, "./.:a", alleleMap, formatKeys});
        tests.add(new Object[] {keepMissing, vc, "./.:a:.", alleleMap, formatKeys});
        baseGT.noAttributes();

        vc = baseVC.genotypes(baseGT.attribute("AA", "a").attribute("BB", 2).make())
                .make();
        tests.add(new Object[] {dropMissing, vc, "./.:a:2", alleleMap, formatKeys});
        tests.add(new Object[] {keepMissing, vc, "./.:a:2", alleleMap, formatKeys});
        baseGT.noAttributes();

        vc = baseVC.genotypes(baseGT.make()).make();
        tests.add(new Object[] {dropMissing, vc, "./.", alleleMap, formatKeys});
        tests.add(new Object[] {keepMissing, vc, "./.:.:.", alleleMap, formatKeys});
        baseGT.noAttributes();

        vc = baseVC.genotypes(baseGT.attribute("BB", 2).make()).make();
        tests.add(new Object[] {dropMissing, vc, "./.:.:2", alleleMap, formatKeys});
        tests.add(new Object[] {keepMissing, vc, "./.:.:2", alleleMap, formatKeys});
        baseGT.noAttributes();

        // check that we only produce a single . when writing attributes with multiple values instead of .,.
        vc = baseVC.genotypes(
                        baseGT.attribute("CC", VCFConstants.MISSING_VALUE_v4).make())
                .make();
        tests.add(new Object[] {keepMissing, vc, "./.:.", alleleMap, Arrays.asList("GT", "CC")});
        baseGT.noAttributes();

        return tests.toArray(new Object[][] {});
    }

    @Test
    public void testEncodeGT() {
        final VariantContextBuilder vcb = new VariantContextBuilder(
                "test", "chr?", 100, 100, Arrays.asList(Allele.REF_A, Allele.ALT_T, Allele.create("TC")));
        final Genotype g1 = new GenotypeBuilder("s1", Arrays.asList(Allele.REF_A, Allele.REF_A)).make();
        final Genotype g2 = new GenotypeBuilder("s2", Arrays.asList(Allele.ALT_T, Allele.create("TC"))).make();
        vcb.genotypes(g1, g2);
        final VariantContext vc = vcb.make();

        Assert.assertEquals(VCFEncoder.encodeGtField(vc, g1), "0/0");
        Assert.assertEquals(VCFEncoder.encodeGtField(vc, g2), "1/2");
    }

    @Test
    public void testsWriteGtField() throws IOException {
        final VariantContextBuilder vcb = new VariantContextBuilder(
                "test", "chr?", 100, 100, Arrays.asList(Allele.REF_A, Allele.ALT_T, Allele.create("TC")));
        final Genotype g1 = new GenotypeBuilder("s1", Arrays.asList(Allele.REF_A, Allele.REF_A)).make();
        final Genotype g2 = new GenotypeBuilder("s2", Arrays.asList(Allele.ALT_T, Allele.create("TC"))).make();
        vcb.genotypes(g1, g2);
        final VariantContext vc = vcb.make();
        final Map<Allele, String> alleleStringMap = VCFEncoder.buildAlleleStrings(vc);

        final StringBuilder b1 = new StringBuilder();
        VCFEncoder.writeGtField(alleleStringMap, b1, g1);
        Assert.assertEquals(b1.toString(), "0/0");

        final StringBuilder b2 = new StringBuilder();
        VCFEncoder.writeGtField(alleleStringMap, b2, g2);
        Assert.assertEquals(b2.toString(), "1/2");
    }

    @Test(dataProvider = "MissingFormatTestData")
    public void testMissingFormatFields(
            final VCFEncoder encoder,
            final VariantContext vc,
            final String expectedLastColumn,
            final Map<Allele, String> alleleMap,
            final List<String> genotypeFormatKeys) {
        final StringBuilder sb = new StringBuilder();
        final String[] columns = new String[5];

        encoder.addGenotypeData(vc, alleleMap, genotypeFormatKeys, sb);
        final int nCol = ParsingUtils.split(sb.toString(), columns, VCFConstants.FIELD_SEPARATOR_CHAR);
        Assert.assertEquals(
                columns[nCol - 1], expectedLastColumn, "Format fields don't handle missing data in the expected way");
    }

    private static Set<VCFHeaderLine> createSyntheticMetadata() {
        final Set<VCFHeaderLine> metaData = new TreeSet<>();

        metaData.add(new VCFContigHeaderLine(Collections.singletonMap("ID", "1"), 0));

        metaData.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "x"));
        metaData.add(new VCFFormatHeaderLine("AA", 1, VCFHeaderLineType.String, "aa"));
        metaData.add(new VCFFormatHeaderLine("BB", 1, VCFHeaderLineType.Integer, "bb"));
        metaData.add(new VCFFormatHeaderLine("CC", 3, VCFHeaderLineType.Integer, "CC"));
        return metaData;
    }

    private static VCFHeader createSyntheticHeader(final List<String> samples) {
        return new VCFHeader(createSyntheticMetadata(), samples);
    }

    // GT phasing

    private static final Allele GT_REF = Allele.create("A", true);
    private static final Allele GT_ALT1 = Allele.create("C");
    private static final Allele GT_ALT2 = Allele.create("G");

    private static String writtenGt(final Genotype g, final boolean leadingPhaseIndicatorAllowed) throws IOException {
        final VariantContext vc =
                new VariantContextBuilder("test", "chr1", 100, 100, Arrays.asList(GT_REF, GT_ALT1, GT_ALT2)).make();
        final StringBuilder gt = new StringBuilder();
        VCFEncoder.writeGtField(VCFEncoder.buildAlleleStrings(vc), gt, g, leadingPhaseIndicatorAllowed);
        return gt.toString();
    }

    @Test
    public void aGenotypeWithOnePhaseIsWrittenWithOneSeparator() throws IOException {
        final Genotype phased = new GenotypeBuilder("s", Arrays.asList(GT_REF, GT_ALT1))
                .phased(true)
                .make();
        final Genotype unphased = new GenotypeBuilder("s", Arrays.asList(GT_REF, GT_ALT1)).make();
        Assert.assertEquals(writtenGt(phased, false), "0|1");
        Assert.assertEquals(writtenGt(phased, true), "0|1");
        Assert.assertEquals(writtenGt(unphased, false), "0/1");
    }

    @Test
    public void aPhasedHaploidGenotypeIsWrittenWithoutAnIndicator() throws IOException {
        final Genotype g =
                new GenotypeBuilder("s", Arrays.asList(GT_ALT1)).phased(true).make();
        Assert.assertEquals(writtenGt(g, false), "1");
        Assert.assertEquals(writtenGt(g, true), "1");
    }

    @Test
    public void mixedSeparatorsAreWrittenForEveryVersion() throws IOException {
        final Genotype g = new GenotypeBuilder("s", Arrays.asList(GT_REF, GT_ALT1, GT_ALT2))
                .allelePhasing(new boolean[] {false, false, true})
                .make();
        Assert.assertEquals(writtenGt(g, false), "0/1|2");
        Assert.assertEquals(writtenGt(g, true), "0/1|2");
    }

    @Test
    public void aLeadingIndicatorIsWrittenWhereItIsAllowed() throws IOException {
        final Genotype phasedFirst = new GenotypeBuilder("s", Arrays.asList(GT_REF, GT_ALT1))
                .allelePhasing(new boolean[] {true, false})
                .make();
        final Genotype unphasedFirst = new GenotypeBuilder("s", Arrays.asList(GT_REF, GT_ALT1))
                .allelePhasing(new boolean[] {false, true})
                .make();
        final Genotype unphasedHaploid = new GenotypeBuilder("s", Arrays.asList(GT_ALT1))
                .allelePhasing(new boolean[] {false})
                .make();
        Assert.assertEquals(writtenGt(phasedFirst, true), "|0/1");
        Assert.assertEquals(writtenGt(unphasedFirst, true), "/0|1");
        Assert.assertEquals(writtenGt(unphasedHaploid, true), "/1");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void aLeadingIndicatorIsRefusedWhereItIsNotAllowed() throws IOException {
        final Genotype g = new GenotypeBuilder("s", Arrays.asList(GT_REF, GT_ALT1))
                .allelePhasing(new boolean[] {true, false})
                .make();
        writtenGt(g, false);
    }

    // Percent-encoding tests

    /** An encoder with scalar (XS, XF), list (XL, FL) and Character (XC, FC) INFO and FORMAT keys. */
    private static VCFEncoder encoderForVersion(final VCFHeaderVersion version) {
        final Set<VCFHeaderLine> lines = new TreeSet<>();
        lines.add(new VCFContigHeaderLine(Collections.singletonMap("ID", "1"), 0));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        lines.add(new VCFFormatHeaderLine("XF", 1, VCFHeaderLineType.String, "str field"));
        lines.add(new VCFFormatHeaderLine("FL", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "str list"));
        lines.add(new VCFFormatHeaderLine("FC", 1, VCFHeaderLineType.Character, "char field"));
        lines.add(new VCFInfoHeaderLine("XS", 1, VCFHeaderLineType.String, "str info"));
        lines.add(new VCFInfoHeaderLine("XL", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "str list"));
        lines.add(new VCFInfoHeaderLine("XC", 1, VCFHeaderLineType.Character, "char info"));
        lines.add(new VCFInfoHeaderLine("DP", 1, VCFHeaderLineType.Integer, "depth"));
        final VCFHeader header = new VCFHeader(lines, Collections.singletonList("s1"));
        return new VCFEncoder(header, true, false, version);
    }

    private static String encodeRecord(
            final VCFEncoder encoder,
            final String infoKey,
            final Object infoVal,
            final String fmtKey,
            final Object fmtVal) {
        final VariantContext vc = new VariantContextBuilder()
                .chr("1")
                .start(100)
                .stop(100)
                .alleles("A", "C")
                .attribute(infoKey, infoVal)
                .genotypes(new GenotypeBuilder("s1", Arrays.asList(Allele.REF_A, Allele.ALT_C))
                        .attribute(fmtKey, fmtVal)
                        .make())
                .make();
        return encoder.encode(vc);
    }

    private static String encodeRecord(final VCFEncoder encoder, final String infoVal, final String fmtVal) {
        return encodeRecord(encoder, "XS", infoVal, "XF", fmtVal);
    }

    /** The sample column of an encoded one-sample record. */
    private static String sampleColumn(final String encodedRecord) {
        return encodedRecord.substring(encodedRecord.lastIndexOf('\t') + 1);
    }

    @Test
    public void percentEncodesInfoStringValuesFor43() {
        final String encoded = encodeRecord(encoderForVersion(VCFHeaderVersion.VCF4_3), "a;b", "ok");
        Assert.assertTrue(encoded.contains("XS=a%3Bb"), "INFO value not encoded: " + encoded);
    }

    @Test
    public void percentEncodesFormatStringValuesFor43() {
        final String encoded = encodeRecord(encoderForVersion(VCFHeaderVersion.VCF4_3), "ok", "x:y");
        Assert.assertTrue(encoded.contains("x%3Ay"), "FORMAT value not encoded: " + encoded);
    }

    @Test
    public void noPercentEncodingFor42() {
        final String encoded = encodeRecord(encoderForVersion(VCFHeaderVersion.VCF4_2), "a;b", "x:y");
        Assert.assertTrue(encoded.contains("XS=a;b"), "INFO value should not be encoded for 4.2: " + encoded);
        Assert.assertTrue(encoded.contains("x:y"), "FORMAT value should not be encoded for 4.2: " + encoded);
    }

    @Test
    public void percentEncodesPercentSign() {
        final String encoded = encodeRecord(encoderForVersion(VCFHeaderVersion.VCF4_3), "50%", "ok");
        Assert.assertTrue(encoded.contains("XS=50%25"), "Percent sign not encoded: " + encoded);
    }

    @Test
    public void formatVCFFieldReturnsTheSameInstanceWhenNothingNeedsEncoding() {
        final String plain = "noSpecialChars";
        Assert.assertSame(VCFEncoder.formatVCFField(plain, VCFEncoder.ValueEncoding.SCALAR), plain);
        final String plainList = "a,b,c";
        Assert.assertSame(VCFEncoder.formatVCFField(plainList, VCFEncoder.ValueEncoding.LIST), plainList);
    }

    @Test
    public void percentEncodesTabCrLf() {
        final String encoded = encodeRecord(encoderForVersion(VCFHeaderVersion.VCF4_3), "a\tb\rc\n", "ok");
        Assert.assertTrue(encoded.contains("a%09b%0Dc%0A"), "Tab/CR/LF not encoded: " + encoded);
    }

    @Test
    public void percentEncodingSkipsIntegerInfoValues() {
        final VCFEncoder encoder = encoderForVersion(VCFHeaderVersion.VCF4_3);
        final VariantContext vc = new VariantContextBuilder()
                .chr("1")
                .start(100)
                .stop(100)
                .alleles("A", "C")
                .attribute("DP", 42)
                .genotypes(new GenotypeBuilder("s1", Arrays.asList(Allele.REF_A, Allele.ALT_C)).make())
                .make();
        final String encoded = encoder.encode(vc);
        Assert.assertTrue(encoded.contains("DP=42"), "Integer DP value should not be encoded: " + encoded);
    }

    @Test
    public void percentEncodesEachInfoListElementAndKeepsTheCommasBetweenThem() {
        final String encoded =
                encodeRecord(encoderForVersion(VCFHeaderVersion.VCF4_3), "XL", List.of("a,b", "c;d"), "XF", "ok");
        Assert.assertTrue(encoded.contains("XL=a%2Cb,c%3Bd"), "List elements not encoded on their own: " + encoded);
    }

    @Test
    public void percentEncodesEachFormatListElementAndKeepsTheCommasBetweenThem() {
        final String encoded =
                encodeRecord(encoderForVersion(VCFHeaderVersion.VCF4_3), "XS", "ok", "FL", List.of("a,b", "c:d"));
        Assert.assertEquals(sampleColumn(encoded), "0/1:a%2Cb,c%3Ad");
    }

    @Test
    public void aCommaInAStringValueOfAListTypedKeyIsADelimiter() {
        // the reader hands a multi-valued FORMAT value on as one comma-joined String
        final String encoded = encodeRecord(encoderForVersion(VCFHeaderVersion.VCF4_3), "XL", "a;b,c", "FL", "x,y:z");
        Assert.assertTrue(encoded.contains("XL=a%3Bb,c"), "INFO list commas were encoded: " + encoded);
        Assert.assertEquals(sampleColumn(encoded), "0/1:x,y%3Az");
    }

    @Test
    public void aCommaInAStringValueOfAScalarKeyIsEncoded() {
        final String encoded = encodeRecord(encoderForVersion(VCFHeaderVersion.VCF4_3), "a,b", "x,y");
        Assert.assertTrue(encoded.contains("XS=a%2Cb"), "INFO scalar comma not encoded: " + encoded);
        Assert.assertEquals(sampleColumn(encoded), "0/1:x%2Cy");
    }

    @Test
    public void percentEncodesCharacterTypedValues() {
        final String encoded = encodeRecord(encoderForVersion(VCFHeaderVersion.VCF4_3), "XC", ";", "FC", ":");
        Assert.assertTrue(encoded.contains("XC=%3B"), "INFO Character not encoded: " + encoded);
        Assert.assertEquals(sampleColumn(encoded), "0/1:%3A");
    }

    @Test
    public void aValueOfAKeyTheHeaderDoesNotDeclareIsEncodedWithItsCommasKept() {
        final String encoded = encodeRecord(encoderForVersion(VCFHeaderVersion.VCF4_3), "UNK", "a;b,c", "UNF", "x=y,z");
        Assert.assertTrue(encoded.contains("UNK=a%3Bb,c"), "Undeclared INFO key: " + encoded);
        Assert.assertEquals(sampleColumn(encoded), "0/1:x%3Dy,z");
    }

    @Test
    public void listElementsAreNotEncodedFor42() {
        final String encoded =
                encodeRecord(encoderForVersion(VCFHeaderVersion.VCF4_2), "XL", List.of("a;b", "c"), "FL", "x:y");
        Assert.assertTrue(encoded.contains("XL=a;b,c"), "4.2 output was encoded: " + encoded);
        Assert.assertEquals(sampleColumn(encoded), "0/1:x:y");
    }

    // Lazy genotype text: written as read only when the source and output versions allow it

    private static VCFHeader lazyTestHeader() {
        final Set<VCFHeaderLine> lines = new TreeSet<>();
        lines.add(new VCFContigHeaderLine(Collections.singletonMap("ID", "chr1"), 0));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        lines.add(new VCFFormatHeaderLine("XF", 1, VCFHeaderLineType.String, "str field"));
        return new VCFHeader(lines, Collections.singletonList("s1"));
    }

    /** Decodes a one-sample record as a reader of a file of the given version does: with its genotypes left as text. */
    private static VariantContext lazyRecord(final VCFHeaderVersion sourceVersion, final String sampleColumn) {
        final VCFCodec codec = new VCFCodec();
        codec.setVCFHeader(lazyTestHeader(), sourceVersion);
        final VariantContext vc = codec.decode("chr1\t100\t.\tA\tC\t.\t.\t.\tGT:XF\t" + sampleColumn);
        Assert.assertTrue(((LazyGenotypesContext) vc.getGenotypes()).isLazyWithData(), "decoded on read");
        return vc;
    }

    private static String sampleColumnWrittenAt(final VCFHeaderVersion outputVersion, final VariantContext vc) {
        return sampleColumn(new VCFEncoder(lazyTestHeader(), false, false, outputVersion).encode(vc));
    }

    private static boolean stillLazy(final VariantContext vc) {
        return ((LazyGenotypesContext) vc.getGenotypes()).isLazyWithData();
    }

    @Test
    public void lazyGenotypesAreWrittenAsReadAtTheSameVersion() {
        final VariantContext vc = lazyRecord(VCFHeaderVersion.VCF4_3, "0/1:a%3Ab");
        Assert.assertEquals(sampleColumnWrittenAt(VCFHeaderVersion.VCF4_3, vc), "0/1:a%3Ab");
        Assert.assertTrue(stillLazy(vc), "the genotypes were decoded");
    }

    @Test
    public void lazyGenotypesFromA44SourceAreWrittenAsReadAt45() {
        final VariantContext vc = lazyRecord(VCFHeaderVersion.VCF4_4, "|0/1:x");
        Assert.assertEquals(sampleColumnWrittenAt(VCFHeaderVersion.VCF4_5, vc), "|0/1:x");
        Assert.assertTrue(stillLazy(vc), "the genotypes were decoded");
    }

    @Test
    public void lazyGenotypesFromA42SourceAreDecodedAndEncodedAt43() {
        final VariantContext vc = lazyRecord(VCFHeaderVersion.VCF4_2, "0/1:5%ab");
        Assert.assertEquals(sampleColumnWrittenAt(VCFHeaderVersion.VCF4_3, vc), "0/1:5%25ab");
        Assert.assertFalse(stillLazy(vc), "the genotypes were written as read");
    }

    @Test
    public void lazyGenotypesFromA43SourceAreDecodedAt42() {
        final VariantContext vc = lazyRecord(VCFHeaderVersion.VCF4_3, "0/1:a%3Ab");
        Assert.assertEquals(sampleColumnWrittenAt(VCFHeaderVersion.VCF4_2, vc), "0/1:a:b");
    }

    @Test
    public void lazyGenotypesWithALeadingPhaseFromA44SourceAreRefusedAt42() {
        final VariantContext vc = lazyRecord(VCFHeaderVersion.VCF4_4, "|0/1:x");
        final IllegalStateException refusal = Assert.expectThrows(
                IllegalStateException.class, () -> sampleColumnWrittenAt(VCFHeaderVersion.VCF4_2, vc));
        Assert.assertTrue(refusal.getMessage().contains("first allele"), refusal.getMessage());
    }

    @Test
    public void lazyGenotypesWithoutALeadingPhaseFromA44SourceAreDecodedAt42() {
        final VariantContext vc = lazyRecord(VCFHeaderVersion.VCF4_4, "0|1:x");
        Assert.assertEquals(sampleColumnWrittenAt(VCFHeaderVersion.VCF4_2, vc), "0|1:x");
        Assert.assertFalse(stillLazy(vc), "the genotypes were written as read");
    }

    // LAA ordering tests

    @Test
    public void laaSortedAfterGtForVersion45() {
        final Set<VCFHeaderLine> lines = new TreeSet<>();
        lines.add(new VCFContigHeaderLine(Collections.singletonMap("ID", "1"), 0));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        lines.add(new VCFFormatHeaderLine("LAA", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "local alt"));
        lines.add(new VCFFormatHeaderLine("AD", VCFHeaderLineCount.R, VCFHeaderLineType.Integer, "allele depth"));
        final VCFHeader header = new VCFHeader(lines, Collections.singletonList("s1"));
        final VCFEncoder encoder = new VCFEncoder(header, true, false, VCFHeaderVersion.VCF4_5);

        final VariantContext vc = new VariantContextBuilder()
                .chr("1")
                .start(100)
                .stop(100)
                .alleles("A", "C")
                .genotypes(new GenotypeBuilder("s1", Arrays.asList(Allele.REF_A, Allele.ALT_C))
                        .attribute("LAA", Arrays.asList(1))
                        .AD(new int[] {10, 5})
                        .make())
                .make();
        final String encoded = encoder.encode(vc);
        // FORMAT should be GT:LAA:AD
        Assert.assertTrue(encoded.contains("GT:LAA:AD"), "LAA should be after GT for 4.5, got: " + encoded);
    }

    @Test
    public void laaSortingNotAppliedForVersion42() {
        final Set<VCFHeaderLine> lines = new TreeSet<>();
        lines.add(new VCFContigHeaderLine(Collections.singletonMap("ID", "1"), 0));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        lines.add(new VCFFormatHeaderLine("LAA", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "local alt"));
        lines.add(new VCFFormatHeaderLine("AD", VCFHeaderLineCount.R, VCFHeaderLineType.Integer, "allele depth"));
        final VCFHeader header = new VCFHeader(lines, Collections.singletonList("s1"));
        final VCFEncoder encoder = new VCFEncoder(header, true, false, VCFHeaderVersion.VCF4_2);

        final VariantContext vc = new VariantContextBuilder()
                .chr("1")
                .start(100)
                .stop(100)
                .alleles("A", "C")
                .genotypes(new GenotypeBuilder("s1", Arrays.asList(Allele.REF_A, Allele.ALT_C))
                        .attribute("LAA", Arrays.asList(1))
                        .AD(new int[] {10, 5})
                        .make())
                .make();
        final String encoded = encoder.encode(vc);
        // For 4.2, LAA should be alphabetically sorted: GT:AD:LAA
        Assert.assertTrue(
                encoded.contains("GT:AD:LAA"), "LAA should be alphabetically sorted for 4.2, got: " + encoded);
    }

    @Test
    public void gtFieldUsesLeadingPhaseFor44() {
        final Set<VCFHeaderLine> lines = new TreeSet<>();
        lines.add(new VCFContigHeaderLine(Collections.singletonMap("ID", "chr1"), 0));
        lines.add(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "gt"));
        final VCFHeader header = new VCFHeader(lines, Collections.singletonList("s1"));
        final VCFEncoder encoder = new VCFEncoder(header, true, false, VCFHeaderVersion.VCF4_4);

        final Genotype g = new GenotypeBuilder("s1", Arrays.asList(GT_REF, GT_ALT1))
                .allelePhasing(new boolean[] {true, false})
                .make();
        final VariantContext vc = new VariantContextBuilder()
                .chr("chr1")
                .start(100)
                .stop(100)
                .alleles(Arrays.asList(GT_REF, GT_ALT1, GT_ALT2))
                .genotypes(g)
                .make();
        final String encoded = encoder.encode(vc);
        Assert.assertTrue(encoded.contains("|0/1"), "Expected leading phase indicator for 4.4: " + encoded);
    }
}
