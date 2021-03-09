package htsjdk.samtools.util;

import java.util.function.Supplier;

/**
 * Wrapper class used to suppress toString() serialization of large test cases such as
 * List&lt;SAMRecord&gt;, which when emitted into the CI server output log result in
 * excessive test output, exceeding the CI server's log output size.
 */
public class QuietTestWrapper<T> implements Supplier<T> {
    final T testData;

    public QuietTestWrapper(final T testCaseData) { this.testData = testCaseData; }

    @Override public T get() { return testData; }

    @Override public String toString() { return "Output suppressed by QuietWrapper"; }
}

