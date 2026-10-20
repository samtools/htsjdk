package htsjdk.tribble.readers;

/**
 * @deprecated Use {@link Utf8LineReaderIterator} instead. This class will be removed in a future major release.
 */
@Deprecated
public class AsciiLineReaderIterator extends Utf8LineReaderIterator {

    public AsciiLineReaderIterator(final Utf8LineReader lineReader) {
        super(lineReader);
    }

    /** Keeps code compiled against htsjdk 5.x, where this was the only constructor, linking. */
    public AsciiLineReaderIterator(final AsciiLineReader lineReader) {
        super(lineReader);
    }
}
