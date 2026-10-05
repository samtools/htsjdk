package htsjdk.tribble;

import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.LocationAware;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.readers.PositionalBufferedStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Implements common methods of {@link FeatureCodec}s that read from {@link htsjdk.tribble.readers.PositionalBufferedStream}s.
 * @author mccowan
 */
public abstract class BinaryFeatureCodec<T extends Feature> implements FeatureCodec<T, PositionalBufferedStream> {
    @Override
    public PositionalBufferedStream makeSourceFromStream(final InputStream bufferedInputStream) {
        if (bufferedInputStream instanceof PositionalBufferedStream)
            return (PositionalBufferedStream) bufferedInputStream;
        else return new PositionalBufferedStream(bufferedInputStream);
    }

    /**
     * {@link PositionalBufferedStream} is already {@link LocationAware}, but its position counts the bytes read through
     * it, which over a {@link BlockCompressedInputStream} are offsets into the decompressed data that no index can use.
     *
     * @throws TribbleException if the stream is a {@link BlockCompressedInputStream}
     */
    @Override
    public LocationAware makeIndexableSourceFromStream(final InputStream bufferedInputStream) {
        if (bufferedInputStream instanceof BlockCompressedInputStream) {
            throw new TribbleException("A BGZF-compressed file cannot be indexed through "
                    + getClass().getSimpleName()
                    + ", whose positions are offsets into the decompressed data rather than into the file");
        }
        return makeSourceFromStream(bufferedInputStream);
    }

    @Override
    public void close(final PositionalBufferedStream source) {
        CloserUtil.close(source);
    }

    @Override
    public boolean isDone(final PositionalBufferedStream source) {
        try {
            return source.isDone();
        } catch (final IOException e) {
            throw new RuntimeIOException("Failure reading from stream.", e);
        }
    }

    /**
     * Marked as final because binary features could not be tabix indexed
     */
    @Override
    public final TabixFormat getTabixFormat() {
        throw new TribbleException("Binary codecs does not support tabix");
    }
}
