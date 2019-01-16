package htsjdk.samtools;

import java.io.Closeable;

/**
 * Interface for managing index files and streams. Has to be implemented
 * by all classes that directly access index data.
 */
interface IndexFileBuffer extends Closeable {
    void readBytes(final byte[] bytes);
    int readInteger();
    long readLong();
    void skipBytes(final int count);
    void seek(final long position);
    long position();
    void close();
}