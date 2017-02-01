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

    default SeekableStream getStreamFor(String path, Function<SeekableByteChannel, SeekableByteChannel> wrapper) throws IOException {
        if(wrapper != null) {
            throw new UnsupportedOperationException("This factory doesn't support adding wrappers");
        } else {
            return this.getStreamFor(path);
        }
    }
}
