package htsjdk.samtools.util;

/**
 * Use this class to enable using try-with-resources on objects that do not implement AutoCloseable but
 * do have a close() method.
 *
 * <pre>{@code
 * try (AutoClose<Connection> auto = new AutoClose<>(makeConnection()) {
 *     Connection conection = auto.object;
 *     ...
 * }}
 *</pre>
 */
public class AutoClose<T> implements AutoCloseable {

    public final T object;

    public AutoClose(T object) {
        this.object = object;
    }

    @Override
    public void close() {
        CloserUtil.close(object);
    }
}
