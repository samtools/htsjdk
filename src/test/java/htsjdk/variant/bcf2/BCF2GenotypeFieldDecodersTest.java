package htsjdk.variant.bcf2;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderVersion;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Decodes hand-assembled genotype field bytes, sample by sample, through the decoder a field gets. */
public class BCF2GenotypeFieldDecodersTest extends VariantBaseTest {
    private static final Allele REF = Allele.create("A", true);
    private static final Allele ALT = Allele.create("C");
    private static final Allele ALT2 = Allele.create("G");
    private static final List<Allele> BIALLELIC = List.of(REF, ALT);
    private static final List<Allele> TRIALLELIC = List.of(REF, ALT, ALT2);

    private static final int MISSING = 0x80;
    private static final int EOV = 0x81;

    // -- GT below VCF 4.4: the first allele's bit is ignored --

    @Test
    public void aDiploidGtTakesItsPhaseFromTheSecondAlleleBelow44() {
        final Genotype[] gts = decodeGT(VCFHeaderVersion.VCF4_2, BIALLELIC, 2, 3, 5, 2, 4, 3, 4, 2, 5);
        assertPhasing(gts[0], "A|C", true, false);
        assertPhasing(gts[1], "A/C", false, false);
        assertPhasing(gts[2], "A/C", false, false);
        assertPhasing(gts[3], "A|C", true, false);
    }

    @Test
    public void aHaploidGtIsUnphasedBelow44WhateverItsBit() {
        final Genotype[] gts = decodeGT(VCFHeaderVersion.VCF4_2, BIALLELIC, 2, 5, EOV, 4, MISSING, 1, EOV);
        assertPhasing(gts[0], "C", false, false);
        assertPhasing(gts[1], "C", false, false);
        assertPhasing(gts[2], ".", false, false);
    }

    @Test
    public void aVersionlessHeaderReadsAsBelow44() {
        final Genotype[] gts = decodeGT(null, BIALLELIC, 2, 3, 4, 5, EOV);
        assertPhasing(gts[0], "A/C", false, false);
        assertPhasing(gts[1], "C", false, false);
    }

    // -- GT at VCF 4.4: the first allele's bit is the leading indicator --

    @Test
    public void aLeadingIndicatorIsKeptAt44() {
        final Genotype[] gts = decodeGT(VCFHeaderVersion.VCF4_4, BIALLELIC, 2, 3, 4, 2, 5, 3, 5, 2, 4);
        assertPhasing(gts[0], "|A/C", true, true);
        Assert.assertTrue(gts[0].isAllelePhased(0));
        Assert.assertFalse(gts[0].isAllelePhased(1));
        assertPhasing(gts[1], "/A|C", true, true);
        Assert.assertFalse(gts[1].isAllelePhased(0));
        Assert.assertTrue(gts[1].isAllelePhased(1));
        assertPhasing(gts[2], "A|C", true, false);
        assertPhasing(gts[3], "A/C", false, false);
    }

    @Test
    public void aHaploidCallIsUnphasedAt44WhateverItsBit() {
        final Genotype[] gts = decodeGT(VCFHeaderVersion.VCF4_4, BIALLELIC, 2, 5, EOV, 4, MISSING);
        assertPhasing(gts[0], "C", false, false);
        assertPhasing(gts[1], "C", false, false);
    }

    @Test
    public void aHaploidNoCallIsUnphasedAt44() {
        final Genotype[] gts = decodeGT(VCFHeaderVersion.VCF4_4, BIALLELIC, 2, 0, EOV, 1, EOV);
        assertPhasing(gts[0], ".", false, false);
        assertPhasing(gts[1], ".", false, false);
    }

    // -- GT of any ploidy --

    @Test
    public void mixedSeparatorsGiveEachAlleleItsPhase() {
        final Genotype[] gts = decodeGT(VCFHeaderVersion.VCF4_2, TRIALLELIC, 3, 2, 4, 7);
        assertPhasing(gts[0], "A/C|G", true, true);
        Assert.assertFalse(gts[0].isAllelePhased(0));
        Assert.assertFalse(gts[0].isAllelePhased(1));
        Assert.assertTrue(gts[0].isAllelePhased(2));
    }

    @Test
    public void uniformSeparatorsOfAPolyploidGtGiveOneFlag() {
        final Genotype[] gts = decodeGT(VCFHeaderVersion.VCF4_2, TRIALLELIC, 3, 2, 5, 7, 2, 4, 6);
        assertPhasing(gts[0], "A|C|G", true, false);
        assertPhasing(gts[1], "A/C/G", false, false);
    }

    @Test
    public void aLeadingIndicatorOnAPolyploidGtIsKeptAt44() {
        final Genotype[] gts = decodeGT(VCFHeaderVersion.VCF4_4, TRIALLELIC, 3, 3, 4, 6, 2, 5, 7);
        assertPhasing(gts[0], "|A/C/G", true, true);
        assertPhasing(gts[1], "/A|C|G", true, true);
    }

    @Test
    public void aShorterGtAmongLongerOnesIsPaddedWithEndOfVectorOrMissing() {
        final Genotype[] gts =
                decodeGT(VCFHeaderVersion.VCF4_2, TRIALLELIC, 3, 6, 3, EOV, 2, MISSING, MISSING, 0, EOV, EOV);
        Assert.assertEquals(gts[0].getGenotypeString(), "G|A");
        Assert.assertEquals(gts[0].getPloidy(), 2);
        Assert.assertEquals(gts[1].getGenotypeString(), "A");
        Assert.assertEquals(gts[1].getPloidy(), 1);
        Assert.assertEquals(gts[2].getPloidy(), 1);
        Assert.assertTrue(gts[2].getAllele(0).isNoCall());
    }

    @Test
    public void allMissingIsASampleWithoutAGt() {
        final Genotype[] gts = decodeGT(VCFHeaderVersion.VCF4_2, BIALLELIC, 2, MISSING, MISSING);
        Assert.assertEquals(gts[0].getPloidy(), 0);
        final Genotype[] triploid = decodeGT(VCFHeaderVersion.VCF4_2, TRIALLELIC, 3, MISSING, MISSING, MISSING);
        Assert.assertEquals(triploid[0].getPloidy(), 0);
    }

    @Test
    public void endOfVectorInTheFirstAlleleIsAnErrorOnEitherPath() {
        Assert.expectThrows(TribbleException.class, () -> decodeGT(VCFHeaderVersion.VCF4_2, BIALLELIC, 2, EOV, 2));
        Assert.expectThrows(TribbleException.class, () -> decodeGT(VCFHeaderVersion.VCF4_2, TRIALLELIC, 3, EOV, 2, 2));
    }

    // -- Other fields --

    @Test
    public void aMissingStringValueIsNotAnAttribute() throws IOException {
        final GenotypeBuilder[] gbs = decode(VCFHeaderVersion.VCF4_2, "FS", BCF2Type.CHAR, 1, 2, '.', 'x');
        Assert.assertFalse(gbs[0].make().hasExtendedAttribute("FS"));
        Assert.assertEquals(gbs[1].make().getExtendedAttribute("FS"), "x");
    }

    @Test
    public void aMissingFtIsNotAFilter() throws IOException {
        final GenotypeBuilder[] gbs =
                decode(VCFHeaderVersion.VCF4_2, "FT", BCF2Type.CHAR, 3, 2, '.', 0, 0, 'q', '1', '0');
        Assert.assertFalse(gbs[0].make().isFiltered());
        Assert.assertNull(gbs[0].make().getFilters());
        Assert.assertEquals(gbs[1].make().getFilters(), "q10");
    }

    @Test
    public void aStringValueIsPercentDecodedFrom43() throws IOException {
        final int[] encoded = {'a', '%', '3', 'B', 'b'};
        Assert.assertEquals(
                decode(VCFHeaderVersion.VCF4_3, "FS", BCF2Type.CHAR, 5, 1, encoded)[0]
                        .make()
                        .getExtendedAttribute("FS"),
                "a;b");
        Assert.assertEquals(
                decode(VCFHeaderVersion.VCF4_2, "FS", BCF2Type.CHAR, 5, 1, encoded)[0]
                        .make()
                        .getExtendedAttribute("FS"),
                "a%3Bb");
    }

    @Test
    public void aStringValueWithACommaStaysWhole() throws IOException {
        final GenotypeBuilder[] gbs = decode(VCFHeaderVersion.VCF4_2, "FS", BCF2Type.CHAR, 3, 1, 'a', ',', 'b');
        Assert.assertEquals(gbs[0].make().getExtendedAttribute("FS"), "a,b");
    }

    @Test
    public void anIntegerVectorPrunedToOneValueIsThatValue() throws IOException {
        final GenotypeBuilder[] gbs = decode(VCFHeaderVersion.VCF4_2, "XX", BCF2Type.INT8, 2, 2, 7, MISSING, 1, 2);
        Assert.assertEquals(gbs[0].make().getExtendedAttribute("XX"), 7);
        Assert.assertEquals(gbs[1].make().getExtendedAttribute("XX"), List.of(1, 2));
    }

    @Test
    public void adAndDpUseTheirOwnSlots() throws IOException {
        final GenotypeBuilder[] ad = decode(VCFHeaderVersion.VCF4_2, "AD", BCF2Type.INT8, 2, 1, 10, 5);
        Assert.assertEquals(ad[0].make().getAD(), new int[] {10, 5});
        final GenotypeBuilder[] dp = decode(VCFHeaderVersion.VCF4_2, "DP", BCF2Type.INT8, 1, 2, 12, MISSING);
        Assert.assertEquals(dp[0].make().getDP(), 12);
        Assert.assertEquals(dp[1].make().getDP(), -1);
    }

    // -- Helpers --

    /** Decodes one GT field of {@code ploidy} INT8 values per sample for as many samples as the values fill. */
    private static Genotype[] decodeGT(
            final VCFHeaderVersion version, final List<Allele> siteAlleles, final int ploidy, final int... values) {
        try {
            final GenotypeBuilder[] gbs =
                    decode(version, siteAlleles, "GT", BCF2Type.INT8, ploidy, values.length / ploidy, values);
            final Genotype[] genotypes = new Genotype[gbs.length];
            for (int i = 0; i < gbs.length; i++) genotypes[i] = gbs[i].make();
            return genotypes;
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static GenotypeBuilder[] decode(
            final VCFHeaderVersion version,
            final String field,
            final BCF2Type type,
            final int numElements,
            final int nSamples,
            final int... values)
            throws IOException {
        return decode(version, BIALLELIC, field, type, numElements, nSamples, values);
    }

    private static GenotypeBuilder[] decode(
            final VCFHeaderVersion version,
            final List<Allele> siteAlleles,
            final String field,
            final BCF2Type type,
            final int numElements,
            final int nSamples,
            final int... values)
            throws IOException {
        final byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) bytes[i] = (byte) values[i];
        final GenotypeBuilder[] gbs = new GenotypeBuilder[nSamples];
        for (int i = 0; i < nSamples; i++) gbs[i] = new GenotypeBuilder("s" + (i + 1));

        final VCFHeader header = new VCFHeader(new LinkedHashSet<>(), List.of());
        header.setVCFHeaderVersion(version);
        final BCF2Decoder decoder = new BCF2Decoder(bytes);
        final byte typeDescriptor = BCF2Utils.encodeTypeDescriptor(numElements, type);
        new BCF2GenotypeFieldDecoders(header)
                .getDecoder(field)
                .decode(siteAlleles, field, decoder, typeDescriptor, numElements, gbs);
        Assert.assertTrue(decoder.blockIsFullyDecoded(), "every byte is consumed");
        return gbs;
    }

    private static void assertPhasing(
            final Genotype g, final String gtString, final boolean phased, final boolean perAllele) {
        Assert.assertEquals(g.getGenotypeString(), gtString, g.getSampleName());
        Assert.assertEquals(g.isPhased(), phased, g.getSampleName() + " isPhased");
        Assert.assertEquals(g.hasPerAllelePhasing(), perAllele, g.getSampleName() + " hasPerAllelePhasing");
    }
}
