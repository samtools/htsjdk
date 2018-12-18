package htsjdk.samtools.cram.encoding;

/**
 * An interface that defines requirements for serializing/deserializing objects into and from a stream.
 *
 * @param <T> data series type to be read or written
 */
public interface CRAMCodec<T> {
    /**
     * Read a single object from the stream
     *
     * @return an object from the stream
     */
    T read();

    /**
     * Read a array of specified length from the stream
     *
     * @param length the number of elements to read
     * @return an object from the stream
     */
    T read(final int length);

    /**
     * Write an object to the stream
     * @param value the object to write
     */
    void write(final T value);
}