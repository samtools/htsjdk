package htsjdk.samtools.util;

import java.util.function.Supplier;

/**
 * Wrapper class used to suppress toString() serialization of large test cases such as
 * List&lt;SAMRecord&gt;, which when emitted into the CI server output log result in
 * excessive test output, exceeding the CI server's log output size.
 *
 * <p>Supports both eager and lazy initialization. Use the {@link #lazy(Supplier)} factory
 * method to defer computation of the test data until {@link #get()} is first called. This
 * is useful when the data provider returns an {@code Iterator<Object[]>} and each row's
 * data should only be materialized when TestNG actually consumes that row.</p>
 */
public class QuietTestWrapper<T> implements Supplier<T> {
    private final Supplier<T> supplier;
    private T cached;
    private boolean evaluated;

    /** Eagerly wraps an already-computed value. */
    public QuietTestWrapper(final T testCaseData) {
        this.supplier = null;
        this.cached = testCaseData;
        this.evaluated = true;
    }

    /** Private constructor for lazy evaluation. Use {@link #lazy(Supplier)}. */
    private QuietTestWrapper(final Supplier<T> supplier, @SuppressWarnings("unused") boolean lazy) {
        this.supplier = supplier;
        this.cached = null;
        this.evaluated = false;
    }

    /** Creates a lazy wrapper that defers computation until {@link #get()} is called. */
    public static <T> QuietTestWrapper<T> lazy(final Supplier<T> supplier) {
        return new QuietTestWrapper<>(supplier, true);
    }

    @Override
    public T get() {
        if (!evaluated) {
            cached = supplier.get();
            evaluated = true;
        }
        return cached;
    }

    @Override public String toString() { return "Output suppressed by QuietWrapper"; }
}

