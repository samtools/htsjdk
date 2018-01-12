package htsjdk.samtools.seekablestream;

import java.io.IOException;
import java.net.URL;
import java.nio.channels.SeekableByteChannel;
import java.util.function.Function;

/**
 * Factory for creating {@link SeekableStream}s based on URLs/paths.
 * Implementations can be set as the default with {@link SeekableStreamFactory#setInstance(ISeekableStreamFactory)}
 * @author jacob
 * @date 2013-Oct-24
 */
public interface ISeekableStreamFactory {

    public SeekableStream getStreamFor(URL url) throws IOException;

    public SeekableStream getStreamFor(String path) throws IOException;

    /**
     * Return a buffered {@code SeekableStream} which wraps the input {@code stream}
     * using the default buffer size
     * @param stream
     * @return
     */
    public SeekableStream getBufferedStream(SeekableStream stream);

    /**
     * Return a buffered {@code SeekableStream} which wraps the input {@code stream}
     * @param stream
     * @param bufferSize
     * @return
     */
    public SeekableStream getBufferedStream(SeekableStream stream, int bufferSize);

    /**
     * Open a stream from the input path, applying the wrapper to the stream.
     *
     * The wrapper allows applying operations directly to the byte stream so that things like caching, prefetching, or decryption
     * can be done at the raw byte level.
     *
     * The default implementation throws if wrapper != null, but implementations may support this wrapping operation
     *
     * @param path    a uri like String representing a resource to open
     * @param wrapper a wrapper to apply to the stream
     * @return a stream opened path
     */
    default SeekableStream getStreamFor(String path, Function<SeekableByteChannel, SeekableByteChannel> wrapper) throws IOException {
        if(wrapper != null) {
            throw new UnsupportedOperationException("This factory doesn't support adding wrappers");
        } else {
            return this.getStreamFor(path);
        }
    }

    /**
     * Open a stream from the input path, with support for {@link java.io.InputStream#mark(int)}.
     *
     * <p>Default implementation wraps the result of {@link #getStreamFor(String)} into a {@link MarkSeekableStream}.
     *
     * @param path a uri like String representing a resource to open.
     * @return a stream opened path with mark support.
     */
    default SeekableStream getMarkSupportedStreamFor(String path) throws IOException {
        return new MarkSeekableStream(this.getStreamFor(path));
    }

    /**
     * Open a stream from the input path, with support for {@link java.io.InputStream#mark(int)} and applying the wrapper to the stream.
     *
     * <p>The wrapper allows applying operations directly to the byte stream so that things like caching, prefetching, or decryption
     * can be done at the raw byte level.
     *
     * <p>Default implementation wraps the result of {@link #getStreamFor(String, Function)} into a {@link MarkSeekableStream}.
     *
     * @param path a uri like String representing a resource to open.
     * @param wrapper a wrapper to apply to the stream
     * @return a stream opened path with mark support.
     */
    default SeekableStream getMarkSupportedStreamFor(String path, Function<SeekableByteChannel, SeekableByteChannel> wrapper) throws IOException {
        return new MarkSeekableStream(this.getStreamFor(path, wrapper));
    }
}
