package htsjdk.samtools.util;

import java.util.function.Supplier;

/**
 * Simple utility for building an on-demand (lazy) object-initializer.
 * 
 * Works by accepting an initializer describing how to build the on-demand object, which is only called once and only after the first
 * invocation of {@link #get()} (or it may not be called at all).
 * 
 * @author mccowan
 */
public class Lazy<T> {
    private final Supplier<T> initializer;
    private boolean isInitialized = false;
    private T instance;

    public Lazy(final Supplier<T> initializer) {
        this.initializer = initializer;
    }

    /** Returns the instance associated with this {@link Lazy}, initializing it if necessary. */
    public synchronized T get() {
        if (!isInitialized) {
            this.instance = initializer.get();
            isInitialized = true;
        }
        return instance;
    }

    /** Describes how to build the instance of the lazy object.
     * @deprecated since 1/2017 use a {@link Supplier} instead
     * */
    @FunctionalInterface
    @Deprecated
    public interface LazyInitializer<T> extends Supplier<T> {
        /** Returns the desired object instance. */
        T make();

        @Override
        default T get(){
            return make();
        }
    }

    public boolean isInitialized() {
        return isInitialized;
    }
}
