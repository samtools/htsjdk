package htsjdk.samtools.util;

/**
 * Describes API for getting current position in a stream, writer, or underlying file.
 *
 * <p>What the position means depends on the implementation:
 * <ul>
 *     <li>A BGZF-aware source ({@link BlockCompressedInputStream}, {@link BlockCompressedOutputStream}, and the
 *     line readers {@link htsjdk.tribble.readers.Utf8LineReader#from} makes for a {@link BlockCompressedInputStream})
 *     returns a BGZF virtual file pointer, not a byte offset.</li>
 *     <li>A wrapper ({@link htsjdk.tribble.readers.PositionalBufferedStream}, {@link PositionalOutputStream}, or a
 *     {@link htsjdk.tribble.readers.Utf8LineReader} reading through a {@code PositionalBufferedStream}) returns the
 *     number of bytes it has read or written: if you've written 50 bytes to it, the position is 50.  Over a BGZF
 *     stream that is an offset into the decompressed data, so a wrapper gives virtual file pointers only if it
 *     delegates {@link #getPosition()} to a BGZF-aware source.</li>
 *     <li>An iterator, or any producer-like object that doesn't map directly to a byte stream, returns the position
 *     in the underlying stream at the end of the most recently returned record, which is where the next record
 *     starts.  For example, {@link htsjdk.tribble.readers.Utf8LineReaderIterator#getPosition()} is the position at
 *     the end of the line most recently returned by {@link htsjdk.tribble.readers.Utf8LineReaderIterator#next()}.</li>
 * </ul>
 *
 * @author mccowan
 */
public interface LocationAware {
    /**
     * The current position of this stream, writer or file or, if this is an iterator/producer, the position at the
     * END of the most recently returned record (since a produced record corresponds to something that has been read
     * already).  See the class javadoc for when this is a byte offset and when it is a BGZF virtual file pointer.
     */
    public long getPosition();
}
