package htsjdk.samtools.cram.encoding.external;

import htsjdk.samtools.cram.io.CRAMByteReader;
import htsjdk.samtools.cram.io.CRAMByteWriter;
import htsjdk.samtools.cram.io.ITF8;

/**
 * Encode/decode integers (ITF8 encoded) using an External Data Block.
 */
final class ExternalIntegerCodec extends ExternalCodec<Integer> {

    public ExternalIntegerCodec(final CRAMByteReader inputReader, final CRAMByteWriter outputWriter) {
        super(inputReader, outputWriter);
    }

    @Override
    public Integer read() { return ITF8.readUnsignedITF8(inputReader); }

    @Override
    public void write(final Integer value) { ITF8.writeUnsignedITF8(value, outputWriter); }

    @Override
    public Integer read(final int length) { throw new RuntimeException("Not implemented."); }
}
