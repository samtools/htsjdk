package htsjdk.samtools.cram.encoding.external;

import htsjdk.samtools.cram.io.CRAMByteReader;
import htsjdk.samtools.cram.io.CRAMByteWriter;

/**
 * Encode/decode byte arrays using an External Data Block.
 */
public final class ExternalByteArrayCodec extends ExternalCodec<byte[]> {

    public ExternalByteArrayCodec(final CRAMByteReader inputReader, final CRAMByteWriter outputWriter) {
        super(inputReader, outputWriter);
    }

    @Override
    public byte[] read(final int length) {
        if (length == 0) return new byte[0];
        return inputReader.readFully(length);
    }

    @Override
    public void write(final byte[] object) { outputWriter.write(object); }

    @Override
    public byte[] read() { throw new RuntimeException("Cannot read byte array of unknown length."); }
}
