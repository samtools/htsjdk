package htsjdk.samtools;

import java.io.Closeable;

interface IndexFileBuffer extends Closeable {
    void readBytes(final byte[] bytes);
    int readInteger();
    long readLong();
    void skipBytes(final int count);
    void seek(final int position);
    int position();
    void close();
}