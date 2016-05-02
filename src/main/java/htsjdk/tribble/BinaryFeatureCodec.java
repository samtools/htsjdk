package htsjdk.tribble;

import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.LocationAware;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.tribble.readers.PositionalBufferedStream;

import java.io.IOException;
import java.io.InputStream;

/**
 * Implements common methods of {@link FeatureCodec}s that read from {@link htsjdk.tribble.readers.PositionalBufferedStream}s.
 * @author mccowan
 */
abstract public class BinaryFeatureCodec<T extends Feature> implements FeatureCodec<T, PositionalBufferedStream> {
    @Override
    public PositionalBufferedStream makeSourceFromStream(final InputStream bufferedInputStream) {
        if (bufferedInputStream instanceof PositionalBufferedStream)
            return (PositionalBufferedStream) bufferedInputStream;
        else
            return new PositionalBufferedStream(bufferedInputStream);
    }

    /** {@link PositionalBufferedStream} is already {@link LocationAware}. */
    @Override
    public LocationAware makeIndexableSourceFromStream(final InputStream bufferedInputStream) {
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
}
