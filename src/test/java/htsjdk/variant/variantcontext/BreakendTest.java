package htsjdk.variant.variantcontext;

import htsjdk.HtsjdkTest;
import java.util.Optional;
import java.util.OptionalInt;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Tests {@link Breakend} on the breakend examples of the VCF 4.5 specification. */
public class BreakendTest extends HtsjdkTest {

    private static Breakend parse(final String allele) {
        return Breakend.parse(allele).orElseThrow(() -> new AssertionError("did not parse: " + allele));
    }

    // -- The four paired forms --

    @Test
    public void joinedAfterWithTheMateOnTheNegativeStrand() {
        final Breakend b = parse("G]17:198982]");
        Assert.assertTrue(b.isJoinedAfter());
        Assert.assertFalse(b.isJoinedBefore());
        Assert.assertEquals(b.getMateContig(), Optional.of("17"));
        Assert.assertEquals(b.getMatePosition(), OptionalInt.of(198982));
        Assert.assertTrue(b.isMateNegativeStrand());
        Assert.assertFalse(b.isMatePositiveStrand());
    }

    @Test
    public void joinedBeforeWithTheMateOnThePositiveStrand() {
        final Breakend b = parse("]13:123456]T");
        Assert.assertTrue(b.isJoinedBefore());
        Assert.assertEquals(b.getMateContig(), Optional.of("13"));
        Assert.assertEquals(b.getMatePosition(), OptionalInt.of(123456));
        Assert.assertTrue(b.isMatePositiveStrand());
    }

    @Test
    public void joinedAfterWithTheMateOnThePositiveStrand() {
        final Breakend b = parse("C[2:321682[");
        Assert.assertTrue(b.isJoinedAfter());
        Assert.assertTrue(b.isMatePositiveStrand());
    }

    @Test
    public void joinedBeforeWithTheMateOnTheNegativeStrand() {
        final Breakend b = parse("[17:198983[A");
        Assert.assertTrue(b.isJoinedBefore());
        Assert.assertTrue(b.isMateNegativeStrand());
    }

    @Test
    public void aPairedBreakendHasTheRefBaseAndNoInsertedBases() {
        final Breakend b = parse("G]17:198982]");
        Assert.assertEquals(b.getBases(), "G");
        Assert.assertEquals(b.getInsertedBases(), "");
        Assert.assertFalse(b.isSingle());
        Assert.assertFalse(b.isMateAssemblyContig());
    }

    // -- Inserted bases --

    @Test
    public void insertedBasesFollowTheRefBaseWhenJoinedAfter() {
        final Breakend b = parse("CAGTNNNNNCA[2:321682[");
        Assert.assertEquals(b.getBases(), "CAGTNNNNNCA");
        Assert.assertEquals(b.getInsertedBases(), "AGTNNNNNCA");
    }

    @Test
    public void insertedBasesPrecedeTheRefBaseWhenJoinedBefore() {
        final Breakend b = parse("]13:123456]AGTNNNNNCAT");
        Assert.assertEquals(b.getBases(), "AGTNNNNNCAT");
        Assert.assertEquals(b.getInsertedBases(), "AGTNNNNNCA");
    }

    // -- The mate's contig --

    @Test
    public void anAssemblyContigIsNamedWithoutItsAngleBrackets() {
        final Breakend b = parse("C[<ctg1>:1[");
        Assert.assertEquals(b.getMateContig(), Optional.of("ctg1"));
        Assert.assertTrue(b.isMateAssemblyContig());
        Assert.assertEquals(b.getMatePosition(), OptionalInt.of(1));
    }

    @Test
    public void aContigNameMayContainColons() {
        final Breakend b = parse("A[HLA-A*01:01:01:01:100[");
        Assert.assertEquals(b.getMateContig(), Optional.of("HLA-A*01:01:01:01"));
        Assert.assertEquals(b.getMatePosition(), OptionalInt.of(100));
    }

    // -- Telomeres --

    @Test
    public void aBreakendAtATelomereHasNoBases() {
        final Breakend b = parse(".[13:123457[");
        Assert.assertEquals(b.getBases(), "");
        Assert.assertEquals(b.getInsertedBases(), "");
        Assert.assertTrue(b.isJoinedAfter());
        Assert.assertTrue(b.isMatePositiveStrand());
    }

    @Test
    public void aMateAtAVirtualTelomericPositionIsAccepted() {
        Assert.assertEquals(parse("]1:0]A").getMatePosition(), OptionalInt.of(0));
    }

    // -- Single breakends --

    @Test
    public void aSingleBreakendJoinedAfterItsBase() {
        final Breakend b = parse("G.");
        Assert.assertTrue(b.isSingle());
        Assert.assertTrue(b.isJoinedAfter());
        Assert.assertEquals(b.getBases(), "G");
        Assert.assertEquals(b.getInsertedBases(), "");
    }

    @Test
    public void aSingleBreakendJoinedBeforeItsBase() {
        final Breakend b = parse(".A");
        Assert.assertTrue(b.isSingle());
        Assert.assertTrue(b.isJoinedBefore());
        Assert.assertEquals(b.getBases(), "A");
    }

    @Test
    public void aSingleBreakendHasNoMate() {
        final Breakend b = parse("G.");
        Assert.assertEquals(b.getMateContig(), Optional.empty());
        Assert.assertEquals(b.getMatePosition(), OptionalInt.empty());
        Assert.assertFalse(b.isMateAssemblyContig());
        Assert.assertFalse(b.isMatePositiveStrand());
        Assert.assertFalse(b.isMateNegativeStrand());
    }

    @Test
    public void aSingleBreakendMayCarryInsertedBases() {
        Assert.assertEquals(parse("TCC.").getInsertedBases(), "CC");
        Assert.assertEquals(parse(".TGCA").getInsertedBases(), "TGC");
    }

    // -- Malformed notation --

    @Test
    public void mismatchedBracketsAreNotABreakend() {
        Assert.assertEquals(Breakend.parse("G[chr1:100]"), Optional.empty());
    }

    @Test
    public void aPositionThatIsNotANumberIsNotABreakend() {
        Assert.assertEquals(Breakend.parse("G]chr1:x]"), Optional.empty());
        Assert.assertEquals(Breakend.parse("G]chr1:-5]"), Optional.empty());
    }

    @Test
    public void basesOnBothSidesAreNotABreakend() {
        Assert.assertEquals(Breakend.parse("G[chr1:100[C"), Optional.empty());
    }

    @Test
    public void aMateWithoutAContigIsNotABreakend() {
        Assert.assertEquals(Breakend.parse("G[:100["), Optional.empty());
        Assert.assertEquals(Breakend.parse("G[100["), Optional.empty());
    }

    @Test
    public void somethingOtherThanBasesIsNotABreakend() {
        Assert.assertEquals(Breakend.parse("<DEL>"), Optional.empty());
        Assert.assertEquals(Breakend.parse("ACGT"), Optional.empty());
        Assert.assertEquals(Breakend.parse(".A."), Optional.empty());
        Assert.assertEquals(Breakend.parse("X."), Optional.empty());
    }

    // -- Round trip --

    @Test
    public void toStringWritesTheNotationItWasParsedFrom() {
        for (final String allele : new String[] {
            "G]17:198982]", "]13:123456]T", "C[2:321682[", "[17:198983[A", "CAGTNNNNNCA[2:321682[",
            "C[<ctg1>:1[", ".[13:123457[", "]1:0]A", "G.", ".TGCA"
        }) {
            Assert.assertEquals(parse(allele).toString(), allele);
        }
    }

    @Test
    public void breakendsParsedFromTheSameTextAreEqual() {
        Assert.assertEquals(parse("C[2:321682["), parse("C[2:321682["));
        Assert.assertEquals(
                parse("C[2:321682[").hashCode(), parse("C[2:321682[").hashCode());
        Assert.assertNotEquals(parse("C[2:321682["), parse("C]2:321682]"));
    }
}
