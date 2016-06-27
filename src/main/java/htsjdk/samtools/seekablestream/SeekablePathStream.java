package htsjdk.samtools.seekablestream;

import htsjdk.samtools.util.Log;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * An implementation of {@link SeekableStream} for {@link Path}.
 */
public class SeekablePathStream extends SeekableStream {

    private final static Log LOG = Log.getInstance(SeekablePathStream.class);

    /**
     * Collection of all open instances.  SeekablePathStream objects are usually open and kept open for the
     * duration of a session.  This collection supports a method to close them all.
     */
    private static final Collection<SeekablePathStream> ALL_INSTANCES =
            Collections.synchronizedCollection(new HashSet<SeekablePathStream>());

    private final Path path;
    private final SeekableByteChannel sbc;
    private final ByteBuffer oneByteBuf = ByteBuffer.allocate(1);

    public SeekablePathStream(final Path path) throws IOException {
        this.path = path;
        this.sbc = Files.newByteChannel(path);
        ALL_INSTANCES.add(this);
    }

    @Override
    public long length() {
        try {
            return sbc.size();
        } catch (IOException e) {
            LOG.error("Cannot find length of path: " + path, e);
            return 0; // consistent with java.io.File
        }
    }

    @Override
    public boolean eof() throws IOException {
        return length() == position();
    }

    @Override
    public void seek(final long position) throws IOException {
        sbc.position(position);
    }

    @Override
    public long position() throws IOException {
        return sbc.position();
    }

    @Override
    public long skip(long n) throws IOException {
        long initPos = position();
        sbc.position(initPos + n);
        return position() - initPos;
    }

    @Override
    public int read(final byte[] buffer, final int offset, final int length) throws IOException {
        if (length < 0) {
            throw new IndexOutOfBoundsException();
        }
        ByteBuffer buf = ByteBuffer.wrap(buffer, offset, length);
        int n = 0;
        while (n < length) {
            final int count = sbc.read(buf);
            if (count < 0) {
              if (n > 0) {
                return n;
              } else {
                return count;
              }
            }
            n += count;
        }
        return n;
    }

    @Override
    public int read() throws IOException {
        oneByteBuf.clear();
        int n = sbc.read(oneByteBuf);
        return n == 1 ? oneByteBuf.array()[0] & 0xff : n;
    }

    @Override
    public String getSource() {
        return path.toAbsolutePath().toString();
    }


    @Override
    public void close() throws IOException {
        ALL_INSTANCES.remove(this);
        sbc.close();
    }

    public static synchronized void closeAllInstances() {
        Collection<SeekablePathStream> clonedInstances = new HashSet<SeekablePathStream>();
        clonedInstances.addAll(ALL_INSTANCES);
        for (SeekablePathStream sfs : clonedInstances) {
            try {
                sfs.close();
            } catch (IOException e) {
                LOG.error("Error closing SeekablePathStream", e);
            }
        }
        ALL_INSTANCES.clear();
    }
}
