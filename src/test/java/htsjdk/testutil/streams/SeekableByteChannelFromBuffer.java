package htsjdk.testutil.streams;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;

/**
 * A buffer-backed SeekableByteChannel, for testing.
 */
public class SeekableByteChannelFromBuffer implements SeekableByteChannel {

  private final ByteBuffer buf;
  private boolean open = true;

  public SeekableByteChannelFromBuffer(ByteBuffer buf) {
    this.buf = buf;
  }

  @Override
  public int read(ByteBuffer dst) throws IOException {
    if (buf.position() == buf.limit()) {
      // signal EOF
      return -1;
    }
    int before = dst.position();
    dst.put(buf);
    return dst.position() - before;
  }

  @Override
  public int write(ByteBuffer src) throws IOException {
    throw new IOException("read-only channel");
  }

  @Override
  public long position() throws IOException {
    checkOpen();
    return buf.position();
  }

  @Override
  public SeekableByteChannel position(long newPosition) throws IOException {
    checkOpen();
    buf.position((int)newPosition);
    return this;
  }

  @Override
  public long size() throws IOException {
    checkOpen();
    return buf.limit();
  }

  @Override
  public SeekableByteChannel truncate(long size) throws IOException {
    checkOpen();
    if (size <0) {
      throw new IllegalArgumentException("negative size");
    }
    if (size > buf.limit()) {
      throw new IllegalArgumentException("size larger than current");
    }
    buf.limit((int)size);
    return null;
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void close() throws IOException {
    open = false;
  }

  ByteBuffer getBuffer() {
    return buf;
  }

  private void checkOpen() throws IOException {
    if (!open) {
      throw new ClosedChannelException();
    }
  }
}
