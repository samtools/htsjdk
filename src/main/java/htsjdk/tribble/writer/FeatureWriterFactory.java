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

import htsjdk.samtools.Defaults;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.CustomGzipOutputStream;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Md5CalculatingOutputStream;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.Feature;
import htsjdk.tribble.Tribble;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.index.DynamicIndexCreator;
import htsjdk.tribble.index.IndexCreator;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.tribble.index.tabix.TabixIndexCreator;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Factory for creating {@link FeatureWriter}.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public final class FeatureWriterFactory {

    /** Default approach for create tribble indexes. */
    public static final IndexFactory.IndexBalanceApproach DEFAULT_INDEX_BALANCE_APPROACH = IndexFactory.IndexBalanceApproach.FOR_SEEK_TIME;

    // if the index should be created
    private boolean createIndex = Defaults.CREATE_INDEX;
    // the index balance approach if the index type is not set and/or is not a tabix output
    private IndexFactory.IndexBalanceApproach iba = DEFAULT_INDEX_BALANCE_APPROACH;
    // reference dictionary to use for indexing
    private SAMSequenceDictionary seqDict = null;

    // use multi-thread writing
    private boolean useAsyncIo = Defaults.USE_ASYNC_IO_WRITE_FOR_TRIBBLE;
    // creates an MD5 for the output
    private boolean createMd5 = Defaults.CREATE_MD5;
    // if true, use BlockCompressedOutputStream; otherwise use CustomGzipOutputStream
    // TODO - this should have a new Defaults (I believe)
    private boolean useBlockCompression = false;

    /**
     * Creates a FeatureWriterFactory with default values:
     *
     * - Create index: {@link Defaults#CREATE_INDEX}
     * - Create MD5: {@link Defaults#CREATE_MD5}
     * - Index Balance Approach: {@link #DEFAULT_INDEX_BALANCE_APPROACH}
     * - Use async IO: {@link Defaults#USE_ASYNC_IO_WRITE_FOR_TRIBBLE}
     * - Use block compression: {@code false}
     */
    public FeatureWriterFactory() { }

    /**
     * Copy constructor.
     */
    public FeatureWriterFactory(final FeatureWriterFactory other) {
        this.createIndex = other.createIndex;
        this.iba = other.iba;
        this.seqDict = other.seqDict;

        this.useAsyncIo = other.useAsyncIo;
        this.createMd5 = other.createMd5;
        this.useBlockCompression = other.useBlockCompression;
    }

    /**
     * Set to {@code true} if a index file should be created for the output; to {@code false} otherwise.
     */
    public FeatureWriterFactory setCreateIndex(final boolean createIndex) {
        this.createIndex = createIndex;
        return this;
    }

    /**
     * Set the Index Balance approach for creating a dynamic index for no tabix indexes.
     *
     * Note: it does not have any effect if there is no index creation.
     *
     * @param iba non-null index balance approach.
     */
    public FeatureWriterFactory setIndexBalanceApproach(
            final IndexFactory.IndexBalanceApproach iba) {
        if (iba == null) {
            throw new IllegalArgumentException("Index Balance Approach cannot be null");
        }
        this.iba = iba;
        return this;
    }

    /**
     * Set the sequence dictionary to include in the index.
     *
     * @param seqDict the sequence dictionary. May be {@code null}.
     */
    public FeatureWriterFactory setSequenceDictionary(final SAMSequenceDictionary seqDict) {
        this.seqDict = seqDict;
        return this;
    }

    /**
     * Set to {@code true} for asynchronous writing; to {@code false} otherwise.
     */
    public FeatureWriterFactory setUseAsyncIo(final boolean useAsyncIo) {
        this.useAsyncIo = useAsyncIo;
        return this;
    }
    
    /**
     * Set to {@code true} if a MD5 digest file should be created for the output; to {@code false} otherwise.
     */
    public FeatureWriterFactory setCreateMd5(final boolean createMd5) {
        this.createMd5 = createMd5;
        return this;
    }

    /**
     * If the output file ends with a block-compressed extension, use BGZF if this parameter is set
     * to {@code true}; otherwise, use conventional gzip.
     */
    public FeatureWriterFactory setUseBlockCompression(final boolean useBlockCompression) {
        this.useBlockCompression = useBlockCompression;
        return this;
    }

    /**
     * Creates a new {@link FeatureWriter} using the encoder and the output file name.
     *
     * Index file names will be generated with the {@link Tribble} methods, and the output may be
     * block-compressed if it has that compression.
     *
     * @param outputFileName the output file name.
     * @param encoder        the encoder to use.
     *
     * @return the feature writer.
     */
    public <F extends Feature> FeatureWriter<F> makeWriter(final String outputFileName, final FeatureEncoder<F> encoder) {
        try {
            // creates the output stream from the file
            final Path outputPath = IOUtil.getPath(outputFileName);
            OutputStream outputStream = Files.newOutputStream(outputPath);

            if (createMd5) {
                outputStream = new Md5CalculatingOutputStream(outputStream, IOUtil.getPath(outputFileName + ".md5"));
            }

            // wrap if it have a block-gzipped extension (block-compressed for use with tabix)
            final boolean blockCompressed = AbstractFeatureReader.hasBlockCompressedExtension(outputFileName);
            if (blockCompressed) {
                outputStream = (useBlockCompression)
                        // TODO - BGZIP should allow to set the location from a Path or String
                        ? BlockCompressedOutputStream.maybeBgzfWrapOutputStream(null, outputStream)
                        : new CustomGzipOutputStream(outputStream, Defaults.COMPRESSION_LEVEL);
            }

            // if we should not index in the fly
            if (!createIndex) {
                return maybeAsync(new FeatureWriterImpl<>(encoder, outputStream));
            }

            final IndexCreator idxCreator;
            final Path indexPath;
            if (blockCompressed) {
                idxCreator = new TabixIndexCreator(seqDict, encoder.getTabixFormat());
                indexPath = Tribble.tabixIndexPath(outputPath);
            } else {
                idxCreator = new DynamicIndexCreator(outputPath, iba);
                indexPath = Tribble.indexPath(outputPath);
            }
            
            return maybeAsync(new IndexingFeatureWriter<>(encoder, outputStream, idxCreator, indexPath, seqDict));

        } catch (final IOException e) {
            throw new TribbleException("Unable to create writer for " + outputFileName, e);
        }
    }

    // helper function to wrap a writer if useAsyncIo is used
    private <F extends Feature> FeatureWriter<F> maybeAsync(final FeatureWriter<F> writer) {
        return (useAsyncIo) ? new AsyncFeatureWriter<>(writer) : writer;
    }
}
