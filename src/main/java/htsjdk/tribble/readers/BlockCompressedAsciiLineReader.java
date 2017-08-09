package htsjdk.tribble.readers;

import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.CloserUtil;

import java.io.IOException;

/**
 * An AsciiLineReader implementation that wraps a BlockCompressedInputStream and provides no additional buffering.
 * Useful for cases where we need to preserve virtual file pointers in the underlying stream, such as during indexing.
 */
class BlockCompressedAsciiLineReader extends AsciiLineReader {

    final private BlockCompressedInputStream bcs;

    public BlockCompressedAsciiLineReader(final BlockCompressedInputStream inputBlockCompressedStream) {
        bcs = inputBlockCompressedStream;
    }

    /**
     * Read a single line of input, advance the underlying stream only enough to read the line.
     */
    @Override
    public String readLine() throws IOException {
        return bcs.readLine();
    };

    @Override
    public String readLine(final PositionalBufferedStream stream) {
        throw new UnsupportedOperationException("A BlockCompressedAsciiLineReader class cannot be used to read from a PositionalBufferedStream");
    }

    @Override
    public void close() {
        if (bcs != null) {
            CloserUtil.close(bcs);
        }
    }

    @Override
    public long getPosition() {
        return bcs.getPosition();
    }
}
