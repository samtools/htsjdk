package htsjdk.samtools.cram.encoding.external;

import htsjdk.samtools.cram.io.CRAMByteReader;
import htsjdk.samtools.cram.io.CRAMByteWriter;

/**
 * Encode/decode byte arrays delimited by a stop byte using an External Data Block.
 * The stop byte must not appear in the data.
 */
final class ByteArrayStopCodec extends ExternalCodec<byte[]> {
    private final int stop;

    /**
     * Create a codec that reads/writes byte arrays delimited by the given stop byte.
     *
     * @param inputReader reader for the external data block (may be null if only writing)
     * @param outputWriter writer for the external data block (may be null if only reading)
     * @param stopByte the delimiter byte that terminates each encoded value
     */
    public ByteArrayStopCodec(final CRAMByteReader inputReader,
                              final CRAMByteWriter outputWriter,
                              final byte stopByte) {
        super(inputReader, outputWriter);
        this.stop = 0xFF & stopByte;
    }

    @Override
    public byte[] read() {
        // Scan directly in the underlying byte[] for the stop byte instead of
        // reading one byte at a time into a ByteArrayOutputStream.
        final byte[] buf = inputReader.getBuffer();
        final int startPos = inputReader.getPosition();
        int scanPos = startPos;
        while (scanPos < buf.length && (buf[scanPos] & 0xFF) != stop) {
            scanPos++;
        }
        final int len = scanPos - startPos;
        final byte[] result = inputReader.readFully(len);
        inputReader.read(); // consume the stop byte
        return result;
    }

    @Override
    public byte[] read(final int length) {
        throw new RuntimeException("Not implemented.");
    }

    @Override
    public void write(final byte[] value) {
        outputWriter.write(value);
        outputWriter.write(stop);
    }
}
