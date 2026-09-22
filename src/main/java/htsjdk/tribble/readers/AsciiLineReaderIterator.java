package htsjdk.tribble.readers;

/**
 * @deprecated Use {@link Utf8LineReaderIterator} instead. This class will be removed in a future major release.
 */
@Deprecated
public class AsciiLineReaderIterator extends Utf8LineReaderIterator {

    public AsciiLineReaderIterator(final Utf8LineReader lineReader) {
        super(lineReader);
    }
}
