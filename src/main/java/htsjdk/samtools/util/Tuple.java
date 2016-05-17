package htsjdk.samtools.util;

/**
 * A simple tuple class.
 *
 * @author mccowan
 */
public class Tuple<A, B> {
    public final A a;
    public final B b;

    public Tuple(final A a, final B b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Tuple<?, ?> tuple = (Tuple<?, ?>) o;

        if (a != null ? !a.equals(tuple.a) : tuple.a != null) return false;

        return !(b != null ? !b.equals(tuple.b) : tuple.b != null);
    }

    @Override
    public int hashCode() {
        int result = a != null ? a.hashCode() : 0;
        result = 31 * result + (b != null ? b.hashCode() : 0);

        return result;
    }

    @Override
    public String toString() {
        return "[" + a + ", " + b + "]";
    }
}
