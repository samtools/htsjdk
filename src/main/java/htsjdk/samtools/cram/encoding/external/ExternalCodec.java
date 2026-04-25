package htsjdk.samtools.cram.encoding.external;

import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.io.CRAMByteReader;
import htsjdk.samtools.cram.io.CRAMByteWriter;

/**
 * Superclass of Codecs which operate on External Block byte streams.
 * Uses unsynchronized {@link CRAMByteReader}/{@link CRAMByteWriter} instead of
 * ByteArrayInputStream/ByteArrayOutputStream for performance.
 *
 * <p>Contrast with {@link htsjdk.samtools.cram.encoding.core.CoreCodec} for Core Block bit streams.
 *
 * @param <T> data series type to be read or written
 */
abstract class ExternalCodec<T> implements CRAMCodec<T> {
    protected final CRAMByteReader inputReader;
    protected final CRAMByteWriter outputWriter;

    /**
     * Create new ExternalCodec with associated input and output byte streams.
     *
     * @param inputReader reader for decoding input (may be null if only writing)
     * @param outputWriter writer for encoding output (may be null if only reading)
     */
    ExternalCodec(final CRAMByteReader inputReader, final CRAMByteWriter outputWriter) {
        this.inputReader = inputReader;
        this.outputWriter = outputWriter;
    }
}
