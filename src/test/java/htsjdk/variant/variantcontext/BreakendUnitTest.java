package htsjdk.variant.variantcontext;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class BreakendUnitTest extends HtsjdkTest {

    @Test(dataProvider = "pairedBreakendsData")
    public void testDecodePaired(final String encoding,
                           final BreakendType expectedType,
                           final String expectedBases,
                           final String expectedMateContig,
                           final int expectedMatePosition,
                           final boolean extepectedIsAssemblyContig) {
        final Breakend subject = Breakend.decode(encoding);
        final Breakend subjectFromBytes = Breakend.decode(encoding.getBytes());
        // check consistency between using byte[] or String for the encoding:
        assertEqualPaired(subject, subjectFromBytes);
        // check expected value:
        Assert.assertEquals(subject.copyBases(), expectedBases.getBytes());
        Assert.assertEquals(subject.getType(), expectedType);
        Assert.assertEquals(subject.getMateContig(), expectedMateContig);
        Assert.assertEquals(subject.getMatePosition(), expectedMatePosition);
        Assert.assertSame(subject.mateIsOnAssemblyContig(), extepectedIsAssemblyContig);

        Assert.assertTrue(subject.isPaired());
        Assert.assertFalse(subject.isSingle());
    }

    @Test(dataProvider = "singleBreakendsData")
    public void testDecodeSingle(final String encoding,
                                 final BreakendType expectedType,
                                 final String expectedBases) {
        final Breakend subject = Breakend.decode(encoding);
        final Breakend subjectFromBytes = Breakend.decode(encoding.getBytes());
        // check consistency between using byte[] or String for the encoding:
        Assert.assertTrue(subject.isSingle());
        Assert.assertTrue(subjectFromBytes.isSingle());
        assertEqualSingle(subject, subjectFromBytes);
        // check expected value:
        Assert.assertEquals(subject.copyBases(), expectedBases.getBytes());
        Assert.assertEquals(subject.getType(), expectedType);
        Assert.assertNull(subject.getMateContig());
        Assert.assertEquals(subject.getMatePosition(), -1);
        Assert.assertSame(subject.mateIsOnAssemblyContig(), false);

        Assert.assertTrue(subject.isSingle());
        Assert.assertTrue(subjectFromBytes.isSingle());
        Assert.assertFalse(subject.isPaired());
        Assert.assertFalse(subjectFromBytes.isPaired());


    }

    private void assertEqualPaired(final Breakend a, final Breakend b) {
        Assert.assertEquals(a, b);
        Assert.assertEquals(a.hashCode(), b.hashCode());
        Assert.assertEquals(a.copyBases(), b.copyBases());
        Assert.assertEquals(a.getType(), b.getType());
        Assert.assertEquals(a.getMateContig(), b.getMateContig());
        Assert.assertEquals(a.getMatePosition(), b.getMatePosition());
        Assert.assertEquals(a.mateIsOnAssemblyContig(), b.mateIsOnAssemblyContig());
    }

    private void assertEqualSingle(final Breakend a, final Breakend b) {
        Assert.assertEquals(a, b);
        Assert.assertEquals(a.hashCode(), b.hashCode());
        Assert.assertEquals(a.copyBases(), b.copyBases());
        Assert.assertEquals(a.getType(), b.getType());
    }


    @DataProvider(name="pairedBreakendsData")
    private Object[][] pairedBreakendsData() {
        final List<Object[]> data = new ArrayList<>();
        data.add(new Object[] { "A[12:12121[", BreakendType.RIGHT_FORWARD, "A", "12", 12121, false});
        data.add(new Object[] { "N]<asm101>:1]", BreakendType.LEFT_REVERSE, "N", "asm101", 1, true});
        data.add(new Object[] { ".[chr13:45678[", BreakendType.RIGHT_FORWARD, "", "chr13", 45678, false});
        data.add(new Object[] { ".[chr13:45679[", BreakendType.RIGHT_FORWARD, "", "chr13", 45678, false});
        data.add(new Object[] { "]34:124113]CA", BreakendType.LEFT_FORWARD, "CAT", "34", 124113, false});
        data.add(new Object[] { "]34:124113]CAT", BreakendType.LEFT_FORWARD, "CAT", "34", 124113, false});
        data.add(new Object[] { "]34:124113]CATA", BreakendType.LEFT_FORWARD, "CATA", "34", 124113, false});
        data.add(new Object[] { "[<typ>:1314[A", BreakendType.RIGHT_REVERSE, "A", "typ", 1314, true });
        data.add(new Object[] { "[typ:1314[A", BreakendType.RIGHT_REVERSE, "A", "typ", 1314, false });

        return data.toArray(new Object[data.size()][]);
    }

    @DataProvider(name="singleBreakendsData")
    private Object[][] singleBreakendsData() {
        final List<Object[]> data = new ArrayList<>();
        data.add(new Object[] { "A.", BreakendType.SINGLE_FORK, "A" });
        data.add(new Object[] { "N.", BreakendType.SINGLE_FORK, "N"});
        data.add(new Object[] { ".CA", BreakendType.SINGLE_JOIN, "CA"});
        data.add(new Object[] { ".C", BreakendType.SINGLE_JOIN, "C"});
        return data.toArray(new Object[data.size()][]);
    }

    @Test(dataProvider = "allBreakendsPairsData")
    public void testEquals(final Breakend a, final Breakend b, final boolean theyAreEqual) {
       if (theyAreEqual) {
           Assert.assertEquals(a, b);
       } else {
           Assert.assertNotEquals(a, b);
       }
    }

    @Test(dataProvider = "allBreakendsEncodingsData")
    public void testAsAllele(final String encoding) {
        final Breakend breakend = Breakend.decode(encoding);
        final Allele allele = Allele.create(encoding);
        final Allele beAllele = breakend.asAllele();
        final Breakend alBreakend = allele.asBreakend();
        Assert.assertNotNull(alBreakend);
        Assert.assertEquals(alBreakend, breakend);
        Assert.assertEquals(allele, beAllele);

        Assert.assertTrue(beAllele.isBreakend());
        Assert.assertEquals(beAllele.copyBases(), breakend.copyBases());
        Assert.assertEquals(beAllele.isSingleBreakend(), breakend.isSingle());
        Assert.assertEquals(beAllele.isPairedBreakend(), breakend.isPaired());
        Assert.assertTrue(beAllele.isSymbolic());
        Assert.assertNull(beAllele.getSymbolicID()); // no sym-id for breakends.
        Assert.assertTrue(beAllele.isStructural());
        Assert.assertEquals(beAllele.getStructuralVariantType(), StructuralVariantType.BND);
        Assert.assertTrue(beAllele.isAlternative());

        new AlleleUnitTest().testConstraints(beAllele);
    }

    @DataProvider(name="allBreakendsEncodingsData")
    private Object[][] allBreakendEncodingsData() {
        final Object[][] paired = pairedBreakendsData();
        final Object[][] single = singleBreakendsData();
        final Object[][] result = new Object[paired.length + single.length][1];
        int i,j,k;
        for (i = 0, j = 0; j < paired.length; i++, j++) {
            result[i] =  new Object[] { paired[j][0] };
        }
        for (j = 0; j < single.length; i++, j++) {
            result[i] = new Object[] { single[j][0] };
        }
        return result;
    }

    @DataProvider(name="allBreakendsPairsData")
    private Object[][] allBreakendPairsData() {
        final Object[][] paired = pairedBreakendsData();
        final Object[][] single = singleBreakendsData();
        final Object[] all = new Object[paired.length + single.length];
        int i,j,k;
        for (i = 0, j = 0; j < paired.length; i++, j++) {
            all[i] =  Breakend.decode((String) paired[j][0]);
        }
        for (j = 0; j < single.length; i++, j++) {
            all[i] = Breakend.decode((String) single[j][0]);
        }
        final Object[][] result = new Object[all.length * all.length][3];
        for (i = 0, k = 0; i < all.length; i++) {
            for (j = 0; j < all.length; j++, k++) {
                result[k] = new Object[]{all[i], all[j], i == j};
            }
        }
        return result;
    }
}
