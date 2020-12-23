package htsjdk.samtools.util;

import java.io.Closeable;
import java.io.Flushable;


public interface Writer<A> extends Closeable, Flushable {

    void write(A item);

    /**
     * Writes out one or more items in order.
     */
    default void write(final Iterable<A> items) {
        items.forEach(this::write);
    }
}
