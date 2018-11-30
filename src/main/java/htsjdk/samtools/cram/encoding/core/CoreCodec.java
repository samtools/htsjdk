package htsjdk.samtools.cram.encoding.core;

import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

/**
 * Superclass of Codecs which operate on Core Block bit streams
 * Contrast with {@link htsjdk.samtools.cram.encoding.external.ExternalCodec} for External Block byte streams
 *
 * @param <T> data series type to be read or written
 */
public abstract class CoreCodec<T> implements CRAMCodec<T> {
    protected final BitInputStream coreBlockInputStream;
    protected final BitOutputStream coreBlockOutputStream;

    /**
     * Create a new CoreCodec with associated input and output bit streams
     *
     * @param coreBlockInputStream bit stream for reading input
     * @param coreBlockOutputStream bit stream for writing output
     */
    protected CoreCodec(final BitInputStream coreBlockInputStream, final BitOutputStream coreBlockOutputStream) {
        this.coreBlockInputStream = coreBlockInputStream;
        this.coreBlockOutputStream = coreBlockOutputStream;
    }
}