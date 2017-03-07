package htsjdk.tribble.util;

import htsjdk.samtools.util.CloseableIterator;

import java.util.Iterator;
import java.util.function.Function;

/**
 * Created by farjoun on 2/16/17.
 */
public class ClosableConcurrentMappingIterator<T, R> extends ConcurrentMappingIterator<T, R> implements CloseableIterator<R> {

    public ClosableConcurrentMappingIterator(CloseableIterator<T> inputIterator, Function<T, R> map, int numberOfThreads, int sizeOfQueue) {
        super(inputIterator, map, numberOfThreads, sizeOfQueue);
    }

    @Override
    public void close() {
        executorService.shutdownNow();
        ((CloseableIterator<T>)encapsulatedIterator).close();
        futureQueue.clear();
    }
}
