package htsjdk.samtools.util;

/**
 * A simple extension of the Tuple class that, for comparable Types, allows comparing Tuples of non-null elements.
 * <p>
 * The comparison will compare the first arguments and if equal (compareTo returns 0) compare the second arguments.
 *
 * @author farjoun
 */
public class ComparableTuple<A extends Comparable<A>, B extends Comparable<B>> extends Tuple<A, B> implements Comparable<ComparableTuple<A, B>> {

    public ComparableTuple(final A a, final B b) {
        super(a, b);

        if (a == null || b == null) {
            throw new IllegalArgumentException("ComparableTuple's behavior is undefined when containing a null.");
        }
    }

    @Override
    public int compareTo(final ComparableTuple<A, B> o) {
        int retval = a.compareTo(o.a);
        if (retval == 0) {
            retval = b.compareTo(o.b);
        }
        return retval;
    }
}
