package htsjdk.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

/**
 * Implementation of a writer that does nothing
 */
public class NullWriter extends Writer {

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        // no op
    }

    @Override
    public void flush() throws IOException {
        // no op
    }

    @Override
    public void close() throws IOException {
        // no op
    }

    private NullWriter(){super();}

    // the only singlton instance of this class (no need for more!)
    public final static NullWriter NULL_WRITER = new NullWriter();

}
