package htsjdk.samtools.cram.encoding.external;

import htsjdk.samtools.cram.io.CRAMByteReader;
import htsjdk.samtools.cram.io.CRAMByteWriter;
import htsjdk.samtools.cram.io.LTF8;

/**
 * Encode/decode longs (LTF8 encoded) using an External Data Block.
 */
final class ExternalLongCodec extends ExternalCodec<Long> {

    public ExternalLongCodec(final CRAMByteReader inputReader, final CRAMByteWriter outputWriter) {
        super(inputReader, outputWriter);
    }

    @Override
    public Long read() {
        return LTF8.readUnsignedLTF8(inputReader);
    }

    @Override
    public void write(final Long value) {
        LTF8.writeUnsignedLTF8(value, outputWriter);
    }

    @Override
    public Long read(final int length) {
        throw new RuntimeException("Not implemented.");
    }
}
