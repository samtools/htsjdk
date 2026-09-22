package htsjdk.variant.variantcontext;

import htsjdk.variant.VariantBaseTest;
import java.util.List;
import java.util.Optional;
import org.testng.Assert;
import org.testng.annotations.Test;

public class StructuralVariantAlleleTest extends VariantBaseTest {

    @Test
    public void parseSimpleDel() {
        final Optional<StructuralVariantAllele> result = StructuralVariantAllele.parse("DEL");
        Assert.assertTrue(result.isPresent());
        Assert.assertEquals(result.get().getType(), StructuralVariantType.DEL);
        Assert.assertTrue(result.get().getSubtypes().isEmpty());
    }

    @Test
    public void parseDelMeAlu() {
        final Optional<StructuralVariantAllele> result = StructuralVariantAllele.parse("DEL:ME:ALU");
        Assert.assertTrue(result.isPresent());
        Assert.assertEquals(result.get().getType(), StructuralVariantType.DEL);
        Assert.assertEquals(result.get().getSubtypes(), List.of("ME", "ALU"));
    }

    @Test
    public void parseBracketedDelMe() {
        final Optional<StructuralVariantAllele> result = StructuralVariantAllele.parse("<DEL:ME>");
        Assert.assertTrue(result.isPresent());
        Assert.assertEquals(result.get().getType(), StructuralVariantType.DEL);
        Assert.assertEquals(result.get().getSubtypes(), List.of("ME"));
    }

    @Test
    public void parseBreakendPaired() {
        final Optional<StructuralVariantAllele> result = StructuralVariantAllele.parse("G]17:198982]");
        Assert.assertTrue(result.isPresent());
        Assert.assertEquals(result.get().getType(), StructuralVariantType.BND);
        Assert.assertTrue(result.get().getSubtypes().isEmpty());
    }

    @Test
    public void parseBreakendSingleDotFirst() {
        final Optional<StructuralVariantAllele> result = StructuralVariantAllele.parse(".A");
        Assert.assertTrue(result.isPresent());
        Assert.assertEquals(result.get().getType(), StructuralVariantType.BND);
    }

    @Test
    public void parseBreakendSingleDotLast() {
        final Optional<StructuralVariantAllele> result = StructuralVariantAllele.parse("A.");
        Assert.assertTrue(result.isPresent());
        Assert.assertEquals(result.get().getType(), StructuralVariantType.BND);
    }

    @Test
    public void parseNonSvSymbolicReturnsEmpty() {
        Assert.assertEquals(StructuralVariantAllele.parse("<NON_REF>"), Optional.empty());
    }

    @Test
    public void parseStarReturnsEmpty() {
        Assert.assertEquals(StructuralVariantAllele.parse("<*>"), Optional.empty());
    }

    @Test
    public void parseSequenceAlleleReturnsEmpty() {
        Assert.assertEquals(StructuralVariantAllele.parse("ACGT"), Optional.empty());
    }

    @Test
    public void parseNullReturnsEmpty() {
        Assert.assertEquals(StructuralVariantAllele.parse(null), Optional.empty());
    }

    @Test
    public void toStringRoundTrips() {
        final StructuralVariantAllele sva =
                StructuralVariantAllele.parse("DEL:ME:ALU").get();
        Assert.assertEquals(sva.toString(), "DEL:ME:ALU");
    }

    @Test
    public void toStringBareType() {
        Assert.assertEquals(
                StructuralVariantAllele.of(StructuralVariantType.INS).toString(), "INS");
    }

    @Test
    public void equalityByValue() {
        final StructuralVariantAllele a =
                StructuralVariantAllele.parse("INS:ME").get();
        final StructuralVariantAllele b =
                StructuralVariantAllele.parse("INS:ME").get();
        Assert.assertEquals(a, b);
        Assert.assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void inequalityDifferentSubtypes() {
        final StructuralVariantAllele a =
                StructuralVariantAllele.parse("DEL:ME").get();
        final StructuralVariantAllele b =
                StructuralVariantAllele.parse("DEL:TANDEM").get();
        Assert.assertNotEquals(a, b);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void ofMixedThrows() {
        StructuralVariantAllele.of(StructuralVariantType.MIXED);
    }

    @Test
    public void ofWithSubtypes() {
        final StructuralVariantAllele sva = StructuralVariantAllele.of(StructuralVariantType.DUP, "TANDEM");
        Assert.assertEquals(sva.getType(), StructuralVariantType.DUP);
        Assert.assertEquals(sva.getSubtypes(), List.of("TANDEM"));
    }

    @Test
    public void subtypeConstants() {
        Assert.assertEquals(StructuralVariantAllele.SUBTYPE_ME, "ME");
        Assert.assertEquals(StructuralVariantAllele.SUBTYPE_TR, "TR");
        Assert.assertEquals(StructuralVariantAllele.SUBTYPE_TANDEM, "TANDEM");
    }
}
