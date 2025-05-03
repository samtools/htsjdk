package htsjdk.testutil;

import org.testng.Assert;

public interface Expected<T> {
    void test(ThrowingSupplier<T> functionToTest);


    interface ThrowingConsumer<T> {
        void test(T a) throws Exception;
    }

    static <T> Expected<T> match(final T expected) {
        return new ComparisonExpected<>((T actual) -> Assert.assertEquals(actual, expected));
    }

    static <T> Expected<T> mismatch(final T expected) {
        return new ComparisonExpected<>((T actual) -> Assert.assertNotEquals(actual, expected));
    }

    static <T> Expected<T> exception(final Class<? extends Exception> exceptionClass) {
        return functionToTest -> Assert.assertThrows(exceptionClass, functionToTest::produce);
    }

    interface ThrowingSupplier<T> {
        T produce() throws Exception;
    }
}

final class ComparisonExpected<T> implements Expected<T> {
    private final ThrowingConsumer<T> test;

    @Override
    public void test(ThrowingSupplier<T> supplier) {
        try {
            test.test(supplier.produce());
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    ComparisonExpected(ThrowingConsumer<T> test) {
        this.test = test;
    }

}
