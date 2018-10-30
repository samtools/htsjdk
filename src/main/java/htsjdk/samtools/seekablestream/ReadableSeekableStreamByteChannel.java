package htsjdk.samtools.seekablestream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;

/**
 * A class to wrap a {@link SeekableStream} in a read-only {@link SeekableByteChannel}.
 */
public class ReadableSeekableStreamByteChannel implements SeekableByteChannel {

    private final SeekableStream seekableStream;
    private final ReadableByteChannel rbc;
    private long pos;

    public ReadableSeekableStreamByteChannel(SeekableStream seekableStream) {
        this.seekableStream = seekableStream;
        this.rbc = Channels.newChannel(seekableStream);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int n = rbc.read(dst);
        if (n > 0) {
            pos += n;
        }
        return n;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        throw new NonWritableChannelException();
    }

    @Override
    public long position() {
        return pos;
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        // ReadableByteChannel is not buffered, so it reads through
        seekableStream.seek(newPosition);
        pos = newPosition;
        return this;
    }

    @Override
    public long size() {
        return seekableStream.length();
    }

    @Override
    public SeekableByteChannel truncate(long size) {
        throw new NonWritableChannelException();
    }

    @Override
    public boolean isOpen() {
        return rbc.isOpen();
    }

    @Override
    public void close() throws IOException {
        rbc.close();
    }
}
