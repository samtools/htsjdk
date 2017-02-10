package htsjdk.samtools.util;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Hacky little class used to allow us to set the compression level on a GZIP output stream which, for some
 * bizarre reason, is not exposed in the standard API.
 *
 * @author Tim Fennell
 */
public class CustomGzipOutputStream extends GZIPOutputStream {
    public CustomGzipOutputStream(final OutputStream outputStream, final int bufferSize, final int compressionLevel) throws
            IOException {
        super(outputStream, bufferSize);
        this.def.setLevel(compressionLevel);
    }

    public CustomGzipOutputStream(final OutputStream outputStream, final int compressionLevel) throws IOException {
        super(outputStream);
        this.def.setLevel(compressionLevel);
    }
}
