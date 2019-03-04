package htsjdk.testutil.streams;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A buffer backed channel that only reads 1 byte at a time on each read instance.  Used for testing that read operations
 * work correctly when read returns less than the desired number of bytes and it isn't because of reaching EOF.
 */
public class OneByteAtATimeChannel extends SeekableByteChannelFromBuffer {
    public OneByteAtATimeChannel(ByteBuffer buf) {
        super(buf);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        final ByteBuffer buf = getBuffer();
        if (buf.position() == buf.limit()) {
            // signal EOF
            return -1;
        }
        int before = dst.position();
        final byte oneByte = buf.get();
        dst.put(oneByte);
        return dst.position() - before;
    }
}
