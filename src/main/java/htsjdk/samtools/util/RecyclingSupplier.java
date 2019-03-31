package htsjdk.samtools.util;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * Supplier wrapper that we reuse instances that have been released back to this supplier
 * in preference to creating a new instance from the underlying
 * @param <T> the type of results supplied by this supplier
 */
public class RecyclingSupplier<T> implements Supplier<T> {
    private final Supplier<T> factory;
    private final Queue<T> available = new ConcurrentLinkedQueue<>();

    public RecyclingSupplier(Supplier<T> factory) {
        this.factory = factory;
    }

    /**
     * Recycle the given instance to be made available
     * @param object instance to recycle
     */
    public void recycle(T object) {
        available.add(object);
    }

    @Override
    public T get() {
        T t = available.poll();
        return (t != null) ? t : factory.get();
    }
}
