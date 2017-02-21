package htsjdk.samtools.util;

import java.util.Iterator;

/**
 * Simple iterator class that delegates all method calls to an underlying iterator. Useful
 * for in-line subclassing to add behaviour to one or more methods.
 *
 * @author Tim Fennell
 */
public class DelegatingIterator<T> implements CloseableIterator<T> {
    private final Iterator<T> iterator;

    public DelegatingIterator(final Iterator<T> iterator) {
        this.iterator = iterator;
    }

    @Override
    public void close() {
        if (iterator instanceof CloseableIterator) {
            ((CloseableIterator) this.iterator).close();
        }
    }

    @Override
    public boolean hasNext() {
        return this.iterator.hasNext();
    }

    @Override
    public T next() {
        return this.iterator.next();
    }

    @Override
    public void remove() {
        this.iterator.remove();
    }
}
