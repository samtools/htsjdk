package htsjdk.tribble.readers;

import java.io.InputStream;
import java.io.StringReader;

/**
 * A collection of factories for generating {@link LineReader}s.
 *
 * @Deprecated use {@link SynchronousLineReader} directly.
 * @author mccowan
 */
@Deprecated
public class LineReaderUtil {
    @Deprecated
    public enum LineReaderOption {
        ASYNCHRONOUS,   //Note: the asynchronous option has no effect - this class does not provide asynchronous reading anymore
        SYNCHRONOUS
    }

    /**
     * Creates a line reader from the given stream.
     * @Deprecated use <code>new SynchronousLineReader(stream);</code>
     */
    @Deprecated
    public static LineReader fromBufferedStream(final InputStream stream) {
        return new SynchronousLineReader(stream);
    }

    /**
     * Creates a line reader from the given string reader.
     * @Deprecated use <code>new SynchronousLineReader(stringReader);</code>
     */
    @Deprecated
    public static LineReader fromStringReader(final StringReader stringReader) {
        return new SynchronousLineReader(stringReader);
    }

    /**
     * Creates a line reader from the given string reader.
     * @Deprecated Asynchronous mode is not going to be supported. Use <code>new SynchronousLineReader(stringReader);</code>
     */
    @Deprecated
    public static LineReader fromStringReader(final StringReader stringReader, final Object ignored) {
        return new SynchronousLineReader(stringReader);
    }

    /**
     * Convenience factory for composing a LineReader from an InputStream.
     * @Deprecated Asynchronous mode is not going to be supported. Use <code>new SynchronousLineReader(bufferedStream);</code>
     */
    @Deprecated
    public static LineReader fromBufferedStream(final InputStream bufferedStream, final Object ignored) {
        return new SynchronousLineReader(bufferedStream);
    }

}
