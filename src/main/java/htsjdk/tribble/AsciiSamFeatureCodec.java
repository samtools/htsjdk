/*
 * Copyright (c) 2019, The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */


package htsjdk.tribble;

import htsjdk.samtools.util.*;
import htsjdk.tribble.readers.AsciiLineReader;
import htsjdk.tribble.readers.AsciiLineReaderIterator;

import java.io.IOException;
import java.io.InputStream;

/**
 * A convenience base class for codecs that want to read in features from ASCII lines.
 * <p/>
 * This class overrides the general decode Features for streams and presents instead
 * Strings to decode(String) and readHeader(LineReader) functions.
 *
 * @param <T> The feature type this codec reads
 */
public abstract class AsciiSamFeatureCodec<T extends Feature> extends AbstractFeatureCodec<T, LineReader> {
    private static final Log log = Log.getInstance(AsciiSamFeatureCodec.class);

    protected AsciiSamFeatureCodec(final Class<T> myClass) {
        super(myClass);
    }

    @Override
    public void close(final LineReader lineIterator) {
        CloserUtil.close(lineIterator);
    }

    @Override
    public boolean isDone(final LineReader lineIterator) {
        return lineIterator.peek() == LineReader.EOF_VALUE;
    }

    @Override
    public LocationAware makeIndexableSourceFromStream(final InputStream inputStream) {
        return new AsciiLineReaderIterator(AsciiLineReader.from(inputStream));
    }

    @Override
    public LineReader makeSourceFromStream(final InputStream bufferedInputStream) {
        return new BufferedLineReader(bufferedInputStream);
    }

    /**
     * Convenience method.  Decoding in ASCII files operates line-by-line, so obviate the need to call
     * {@link LineReader#readLine()} in implementing classes and, instead, have them implement
     * {@link AsciiSamFeatureCodec#decode(String)}.
     */
    @Override
    public T decode(final LineReader lineIterator) {
        return decode(lineIterator.readLine());
    }

    /**
     * @see AsciiSamFeatureCodec#decode(LineReader)
     */
    public abstract T decode(String s);

    @Override
    public FeatureCodecHeader readHeader(final LineReader lineReader) throws IOException {
        // TODO: Track header end here, rather than assuming there isn't one...need to maintain length of header...
        final Object header = readActualHeader(lineReader);
        return new FeatureCodecHeader(header, FeatureCodecHeader.NO_HEADER_END);
    }

    /**
     * Read and return the header, or null if there is no header.
     *
     * @return the actual header data in the file, or null if none is available
     */
    abstract public Object readActualHeader(final LineReader reader);
}
