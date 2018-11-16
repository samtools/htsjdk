package htsjdk.samtools.cram.encoding.external;

import htsjdk.samtools.cram.encoding.CRAMCodec;

import java.io.InputStream;
import java.io.OutputStream;

public abstract class ExternalCodec<T> implements CRAMCodec<T> {
    protected final InputStream inputStream;
    protected final OutputStream outputStream;

    ExternalCodec(final InputStream inputStream, final OutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }
}