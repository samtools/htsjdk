package htsjdk.samtools.cram.encoding.external;

import htsjdk.samtools.cram.encoding.CRAMCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Superclass of Codecs which operate on External Block byte streams
 * Contrast with {@link htsjdk.samtools.cram.encoding.core.CoreCodec} for Core Block bit streams
 *
 * @param <T> data series type to be read or written
 */
public abstract class ExternalCodec<T> implements CRAMCodec<T> {
    protected final ByteArrayInputStream inputStream;
    protected final ByteArrayOutputStream outputStream;

    /**
     * Create new ExternalCodec with associated input and output byte streams
     *
     * @param inputStream byte stream for reading input
     * @param outputStream byte stream for writing output
     */
    ExternalCodec(final ByteArrayInputStream inputStream, final ByteArrayOutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }
}