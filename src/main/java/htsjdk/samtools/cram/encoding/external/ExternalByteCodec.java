package htsjdk.samtools.cram.encoding.external;

import htsjdk.samtools.cram.io.CRAMByteReader;
import htsjdk.samtools.cram.io.CRAMByteWriter;

/**
 * Encode/decode single bytes using an External Data Block.
 */
final class ExternalByteCodec extends ExternalCodec<Byte> {

    public ExternalByteCodec(final CRAMByteReader inputReader, final CRAMByteWriter outputWriter) {
        super(inputReader, outputWriter);
    }

    @Override
    public Byte read() { return (byte) inputReader.read(); }

    @Override
    public void write(final Byte object) { outputWriter.write(object); }

    @Override
    public Byte read(final int length) { throw new RuntimeException("Not implemented."); }
}
