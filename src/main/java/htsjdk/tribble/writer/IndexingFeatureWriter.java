/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Daniel Gomez-Sanchez
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package htsjdk.tribble.writer;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.LocationAware;
import htsjdk.samtools.util.PositionalOutputStream;
import htsjdk.tribble.Feature;
import htsjdk.tribble.index.Index;
import htsjdk.tribble.index.IndexCreator;
import htsjdk.tribble.index.tabix.TabixIndex;
import htsjdk.tribble.util.LittleEndianOutputStream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Feature writer class for indexing on the fly.
 *
 * @param <F> feature type.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
final class IndexingFeatureWriter<F extends Feature> extends FeatureWriterImpl<F> {

    private final LocationAware location;
    private final IndexCreator indexer;
    private final Path indexPath;
    private final SAMSequenceDictionary refDict;

    /**
     * Construct a new indexing feature writer.
     *
     * @param encoder      encoder for features.
     * @param outputStream the underlying output stream.
     * @param idxCreator   indexer.
     * @param indexPath    the path to write the index on.
     * @param refDict      dictionary to write in the index. May be {@code null}.
     */
    public IndexingFeatureWriter(final FeatureEncoder<F> encoder, final OutputStream outputStream,
            final IndexCreator idxCreator, final Path indexPath,
            final SAMSequenceDictionary refDict) {
        super(encoder, asLocationAwareStream(outputStream));
        this.indexer = idxCreator;
        this.indexPath = indexPath;
        this.refDict = refDict;
        // this is already a LocationAware
        this.location = (LocationAware) getOutputStream();
    }

    /** Wraps if necessary the output stream. */
    private static OutputStream asLocationAwareStream(final OutputStream outputStream) {
        return (outputStream instanceof LocationAware)
                ? outputStream : new PositionalOutputStream(outputStream);
    }

    /**
     * Adds the feature to the indexer, and then write using the {@link FeatureWriterImpl}
     * implementation.
     */
    @Override
    public void add(final F feature) throws IOException {
        // should be added to the indexer first
        indexer.addFeature(feature, location.getPosition());
        super.add(feature);
    }

    /** Adds the reference dictionary (if not {@code null}), finalize/write the index and close. */
    @Override
    public void close() throws IOException {
        if (refDict != null) {
            indexer.setIndexSequenceDictionary(refDict);
        }
        final Index index = indexer.finalizeIndex(location.getPosition());
        index.write(indexPath);
        super.close();
    }
}
