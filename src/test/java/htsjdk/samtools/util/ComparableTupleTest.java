package htsjdk.samtools.util;

import htsjdk.variant.variantcontext.Allele;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Created by farjoun on 1/28/16.
 */
public class ComparableTupleTest {

    private enum Tenum {
        Hi,
        Bye,
        Ciao
    }

    private Allele A = Allele.create("A", false);
    private Allele Aref = Allele.create("A", true);
    private Allele G = Allele.create("G", false);

    @DataProvider(name = "testComparableTupleData")
    public Object[][] testComparableTupleData() {
        return new Object[][]{
                new Object[]{new ComparableTuple<>(1, 2), new ComparableTuple<>(1, 1), 2 - 1},
                new Object[]{new ComparableTuple<>(1, 2), new ComparableTuple<>(2, 2), 1 - 2},
                new Object[]{new ComparableTuple<>(1, 2), new ComparableTuple<>(1, 2), 0},

                new Object[]{new ComparableTuple<>(1, "hi"), new ComparableTuple<>(1, "bye"), "hi".compareTo("bye")},
                new Object[]{new ComparableTuple<>(1, "hi"), new ComparableTuple<>(2, "bye"), 1 - 2},
                new Object[]{new ComparableTuple<>(1, "hi"), new ComparableTuple<>(1, "hi"), 0},

                new Object[]{new ComparableTuple<>(A, Tenum.Hi), new ComparableTuple<>(Aref, Tenum.Bye), A.compareTo(Aref)},
                new Object[]{new ComparableTuple<>(Aref, Tenum.Hi), new ComparableTuple<>(Aref, Tenum.Bye), Tenum.Hi.compareTo(Tenum.Bye)},
                new Object[]{new ComparableTuple<>(Aref, Tenum.Hi), new ComparableTuple<>(Aref, Tenum.Hi), 0},
                new Object[]{new ComparableTuple<>(Aref, Tenum.Hi), new ComparableTuple<>(G, Tenum.Ciao), Aref.compareTo(G)},
                new Object[]{new ComparableTuple<>(A, Tenum.Ciao), new ComparableTuple<>(G, Tenum.Hi), A.compareTo(G)}
        };
    }

    @Test(dataProvider = "testComparableTupleData")
    public <T extends Comparable<T>, R extends Comparable<R>> void testComparableTuple(final ComparableTuple<T,R> lhs, final ComparableTuple<T,R> rhs, final int result) {
        Assert.assertEquals(lhs.compareTo(rhs), result);
    }


    @DataProvider(name = "testComparableTupleNullData")
    public Object[][] testComparableTupleNullData() {
        return new Object[][]{
                new Object[]{null, 2},
                new Object[]{null, null},
                new Object[]{"string", null}
        };
    }

    @Test(dataProvider = "testComparableTupleNullData", expectedExceptions = IllegalArgumentException.class)
    public void testComparableTupleNullData(String left, Integer right) {
        new ComparableTuple<>(left, right);
    }
}
