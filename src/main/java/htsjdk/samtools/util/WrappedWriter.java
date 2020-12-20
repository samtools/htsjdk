package htsjdk.samtools.util;

import java.io.Closeable;


public interface WrappedWriter<A> extends Closeable {

    void write(A item);

    /** Writes out one or more items in order. */
    default void write(final Iterable<A> items) {
        items.forEach(this::write);
    }

    /** Writes an individual item and returns a reference to the Writer. */
    default WrappedWriter<A> append(A item) {
        this.write(item);
        return this;
    }

    /** Writes out one or more items in order and returns a reference to the writer. */
    default WrappedWriter<A> append(Iterable<A> items) {
       this.write(items);
       return this;
    }
}
