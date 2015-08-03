package htsjdk.samtools.util;

import htsjdk.samtools.Chunk;
import htsjdk.samtools.seekablestream.SeekableStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An input stream that wraps a {@link htsjdk.samtools.seekablestream.SeekableStream} to produce only bytes specified within coordinates.
 * Created by vadim on 25/03/2015.
 */
public class CoordSpanInputSteam extends InputStream {
    private SeekableStream delegate;
    private Iterator<Chunk> it;
    private Chunk current;
    private boolean eof = false;

    /**
     * Wrap {@link htsjdk.samtools.seekablestream.SeekableStream} to read only bytes within boundaries specified in the coords array.
     * The coords array consists of [inclusive; exclusive) pairs of long coordinates.
     * This constructor will throw exception if a start coordinate is beyond stream length.
     * End coordinates are capped at the stream length.
     */
    public CoordSpanInputSteam(SeekableStream delegate, long[] coords) throws IOException {
        this.delegate = delegate;

        List<Chunk> chunks = new ArrayList<Chunk>();
        for (int i = 0; i < coords.length; i += 2) {
            if (coords[i] > delegate.length()) throw new RuntimeException("Chunk start is passed EOF: " + coords[i]);
            Chunk chunk = new Chunk(coords[i], coords[i + 1] > delegate.length() ? delegate.length() : coords[i + 1]);
            chunks.add(chunk);
            System.err.printf("Adding chunk: %d - %d\n", chunk.getChunkStart(), chunk.getChunkEnd());
        }
        it = chunks.iterator();
        nextChunk();
    }

    private void nextChunk() throws IOException {
        if (eof || !it.hasNext()) {
            eof = true;
            return;
        }

        current = it.next();
        delegate.seek(current.getChunkStart());
    }

    @Override
    public int read() throws IOException {
        if (eof || delegate.eof()) {
            eof = true;
            return -1;
        }

        if (delegate.position() < current.getChunkEnd())
            return delegate.read();

        nextChunk();

        if (eof) return -1;
        return delegate.read();
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (eof || delegate.eof()) {
            eof = true;
            return -1;
        }

        int available = available();
        if (available > length) return delegate.read(buffer, offset, length);

        int read = delegate.read(buffer, offset, available);
        if (delegate.position() >= current.getChunkEnd())
            nextChunk();
        return read;
    }

    /**
     * Returns how many bytes are left in the current chunk.
     *
     * @return number of unread bytes in the current chunk.
     * @throws IOException
     */
    @Override
    public int available() throws IOException {
        return (int) (current.getChunkEnd() - delegate.position());
    }

    @Override
    public void close() throws IOException {
        delegate.close();
        it = null;
    }
}
