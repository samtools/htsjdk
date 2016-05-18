package htsjdk.samtools.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author mccowan
 */
public class Iterables {
    private Iterables() {
        
    }

    public static <T> List<T> slurp(final Iterator<T> iterator) {
        final List<T> ts = new ArrayList<T>();
        while (iterator.hasNext()) ts.add(iterator.next());
        return ts;
    }

    public static <T> List<T> slurp(final Iterable<T> iterable) {
        return slurp(iterable.iterator());
    }
}
