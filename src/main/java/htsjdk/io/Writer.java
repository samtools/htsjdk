package htsjdk.io;

import java.io.Closeable;

public interface Writer<A> extends Closeable {

    /**
     * Writes one item.
     *
     * @param item the item to write.
     */
    void write(A item);

    /**
     * Writes out one or more items in order.
     */
    default void write(final Iterable<A> items) {
        items.forEach(this::write);
    }
}
