package htsjdk.tribble.readers;

import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.Log;
import java.io.InputStream;

/**
 * @deprecated Use {@link Utf8LineReader} instead. This class will be removed in a future major release.
 */
@Deprecated
public class AsciiLineReader extends Utf8LineReader {
    private static final Log log = Log.getInstance(AsciiLineReader.class);

    protected AsciiLineReader() {}

    /** @deprecated use {@link Utf8LineReader#from} */
    @Deprecated
    public AsciiLineReader(final InputStream is) {
        super(is);
    }

    /** @deprecated use {@link Utf8LineReader#from} */
    @Deprecated
    public AsciiLineReader(final PositionalBufferedStream is) {
        super(is);
    }

    /**
     * @deprecated Use {@link Utf8LineReader#from} instead.
     */
    @Deprecated
    public static AsciiLineReader from(final InputStream inputStream) {
        if (inputStream instanceof BlockCompressedInputStream) {
            return new BlockCompressedAsciiLineReader((BlockCompressedInputStream) inputStream);
        } else if (inputStream instanceof PositionalBufferedStream) {
            return new AsciiLineReader((PositionalBufferedStream) inputStream);
        } else {
            log.warn("Creating an indexable source for an AsciiFeatureCodec using a stream that is "
                    + "neither a PositionalBufferedStream nor a BlockCompressedInputStream");
            return new AsciiLineReader(new PositionalBufferedStream(inputStream));
        }
    }
}
