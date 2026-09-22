package htsjdk.variant.variantcontext;

import htsjdk.variant.VariantBaseTest;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.testng.Assert;
import org.testng.annotations.Test;

public class StructuralVariantTypeTest extends VariantBaseTest {

    @Test
    public void parseBareDel() {
        Assert.assertEquals(StructuralVariantType.parse("DEL"), Optional.of(StructuralVariantType.DEL));
    }

    @Test
    public void parseWithSubtypes() {
        Assert.assertEquals(StructuralVariantType.parse("DEL:ME:ALU"), Optional.of(StructuralVariantType.DEL));
    }

    @Test
    public void parseAngleBracketed() {
        Assert.assertEquals(StructuralVariantType.parse("<DEL:ME:ALU>"), Optional.of(StructuralVariantType.DEL));
    }

    @Test
    public void parseNonRefReturnsEmpty() {
        Assert.assertEquals(StructuralVariantType.parse("<NON_REF>"), Optional.empty());
    }

    @Test
    public void parseStarReturnsEmpty() {
        Assert.assertEquals(StructuralVariantType.parse("<*>"), Optional.empty());
    }

    @Test
    public void parseArbitrarySymbolicReturnsEmpty() {
        Assert.assertEquals(StructuralVariantType.parse("<FOO>"), Optional.empty());
    }

    @Test
    public void parseSvtypeDelMe() {
        Assert.assertEquals(StructuralVariantType.parse("DEL:ME"), Optional.of(StructuralVariantType.DEL));
    }

    @Test
    public void parseBnd() {
        Assert.assertEquals(StructuralVariantType.parse("BND"), Optional.of(StructuralVariantType.BND));
    }

    @Test
    public void parseBndAngleBracketed() {
        Assert.assertEquals(StructuralVariantType.parse("<BND>"), Optional.of(StructuralVariantType.BND));
    }

    @Test
    public void parseNullReturnsEmpty() {
        Assert.assertEquals(StructuralVariantType.parse((String) null), Optional.empty());
    }

    @Test
    public void parseBytesOfASymbolicAlleleWithSubtypes() {
        Assert.assertEquals(StructuralVariantType.parse(bytes("<DUP:TANDEM>")), Optional.of(StructuralVariantType.DUP));
    }

    @Test
    public void parseBytesOfAnSvtypeValue() {
        Assert.assertEquals(StructuralVariantType.parse(bytes("CNV")), Optional.of(StructuralVariantType.CNV));
    }

    @Test
    public void parseBytesOfANonStructuralSymbolicAlleleReturnsEmpty() {
        Assert.assertEquals(StructuralVariantType.parse(bytes("<NON_REF>")), Optional.empty());
    }

    @Test
    public void parseBytesOfAnUnclosedSymbolicAlleleReturnsEmpty() {
        Assert.assertEquals(StructuralVariantType.parse(bytes("<DEL")), Optional.empty());
    }

    @Test
    public void parseBytesOfANameThatOnlyStartsLikeATypeReturnsEmpty() {
        Assert.assertEquals(StructuralVariantType.parse(bytes("<DELETION>")), Optional.empty());
    }

    @Test
    public void parseEmptyBytesReturnsEmpty() {
        Assert.assertEquals(StructuralVariantType.parse(new byte[0]), Optional.empty());
    }

    private static byte[] bytes(final String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    public void parseEmptyReturnsEmpty() {
        Assert.assertEquals(StructuralVariantType.parse(""), Optional.empty());
    }

    @Test
    public void parseMixedTextReturnsEmpty() {
        Assert.assertEquals(StructuralVariantType.parse("MIXED"), Optional.empty());
    }

    @Test
    public void parseSequenceTextReturnsEmpty() {
        Assert.assertEquals(StructuralVariantType.parse("ACGT"), Optional.empty());
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void toSymbolicAltAlleleMixedThrows() {
        StructuralVariantType.MIXED.toSymbolicAltAllele();
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void toSymbolicAltAlleleBndThrows() {
        StructuralVariantType.BND.toSymbolicAltAllele();
    }

    @Test
    public void toSymbolicAltAlleleDelWorks() {
        Assert.assertEquals(StructuralVariantType.DEL.toSymbolicAltAllele().getDisplayString(), "<DEL>");
    }
}
