package htsjdk.tribble.readers;

import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;

/**
 * A collection of factories for generating {@link LineReader}s.
 *
 * @author mccowan
 */
public class LineReaderUtil {
    public static LineReader fromStringReader(final StringReader stringReader) {
        return new LineReader() {
            final LongLineBufferedReader reader = new LongLineBufferedReader(stringReader);

            @Override
            public String readLine() {
                try {
                    return reader.readLine();
                } catch (IOException e) {
                    throw new RuntimeIOException(e);
                }
            }

            @Override
            public void close() {
                CloserUtil.close(reader);
            }
        };
    }

    /**
     * Convenience factory for composing a LineReader from an InputStream.
     */
    public static LineReader fromBufferedStream(final InputStream bufferedStream) {
        final InputStreamReader bufferedInputStreamReader = new InputStreamReader(bufferedStream);
        return new LineReader() {
            final LongLineBufferedReader reader = new LongLineBufferedReader(bufferedInputStreamReader);

            @Override
            public String readLine() {
                try {
                    return reader.readLine();
                } catch (IOException e) {
                    throw new RuntimeIOException(e);
                }
            }

            @Override
            public void close() {
                CloserUtil.close(reader);
            }
        };
    }

}
