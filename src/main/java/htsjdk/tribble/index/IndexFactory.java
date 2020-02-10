/*
 * The MIT License
 *
 * Copyright (c) 2013 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.tribble.index;

import htsjdk.samtools.Defaults;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.seekablestream.ISeekableStreamFactory;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.seekablestream.SeekableStreamFactory;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.LocationAware;
import htsjdk.tribble.CloseableTribbleIterator;
import htsjdk.tribble.Feature;
import htsjdk.tribble.FeatureCodec;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.index.interval.IntervalIndexCreator;
import htsjdk.tribble.index.interval.IntervalTreeIndex;
import htsjdk.tribble.index.linear.LinearIndex;
import htsjdk.tribble.index.linear.LinearIndexCreator;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.index.tabix.TabixIndex;
import htsjdk.tribble.index.tabix.TabixIndexCreator;
import htsjdk.tribble.readers.PositionalBufferedStream;
import htsjdk.tribble.util.LittleEndianInputStream;
import htsjdk.tribble.util.ParsingUtils;
import htsjdk.utils.ValidationUtils;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

/**
 * Factory class for creating indexes.  It is the responsibility of this class to determine and create the
 * correct index type from the input file or stream.  LinearIndex, IntervalTreeIndex, and TabixIndex are supported
 * by this factory.
 */
public class IndexFactory {
    /** We can optimize index-file-creation for different factors. As of this writing, those are index-file size or seeking time. */
    public enum IndexBalanceApproach {
        FOR_SIZE,
        FOR_SEEK_TIME
    }

    /**
     * an enum that contains all of the information about the index types, and how to create them
     */
    public enum IndexType {
        LINEAR(AbstractIndex.MAGIC_NUMBER, LinearIndex.INDEX_TYPE, true, LinearIndex::new, LinearIndexCreator.DEFAULT_BIN_WIDTH),
        INTERVAL_TREE(AbstractIndex.MAGIC_NUMBER, IntervalTreeIndex.INDEX_TYPE, true, IntervalTreeIndex::new, IntervalIndexCreator.DEFAULT_FEATURE_COUNT),
        // Tabix index initialization requires additional information, so generic construction won't work, thus indexCreatorClass is null.
        TABIX(TabixIndex.MAGIC_NUMBER, null, false, TabixIndex::new, -1) ;

        private final int magicNumber;
        private final Integer tribbleIndexType;
        private final int defaultBinSize;
        private final boolean canCreate;
        private final IndexFromStreamFunction createFromInputStream;

        public int getDefaultBinSize() {
            return defaultBinSize;
        }

        public boolean canCreate() {
            return canCreate;
        }

        private interface IndexFromStreamFunction {
            Index apply(InputStream t) throws IOException;
        }

        IndexType(final int magicNumber, final Integer tribbleIndexType, final boolean canCreate, final IndexFromStreamFunction createFromInputStream, final int defaultBinSize) {
            this.magicNumber = magicNumber;
            this.tribbleIndexType = tribbleIndexType;
            this.canCreate = canCreate;
            this.createFromInputStream = createFromInputStream;
            this.defaultBinSize = defaultBinSize;
        }

        public Integer getTribbleIndexType() {
            return tribbleIndexType;
        }

        public Index createIndex(final InputStream in) {
            try {
                return createFromInputStream.apply(in);
            } catch (IOException e) {
                throw new TribbleException("Failed to create index from stream.", e);
            }
        }

        public int getMagicNumber() { return magicNumber; }

        /**
         *
         * @param is InputStream of index.  This will be reset to location it was at when method was invoked.
         * @return The {@code IndexType} based on the {@code headerValue}
         * @throws TribbleException.UnableToCreateCorrectIndexType
         */
        public static IndexType getIndexType(final BufferedInputStream is) {
            // Currently only need 8 bytes, so this should be plenty
            is.mark(128);
            final LittleEndianInputStream dis = new LittleEndianInputStream(is);
            final int magicNumber;
            final int type;

            try {
                // Read the type and version,  then create the appropriate type
                magicNumber = dis.readInt();
                // This is not appropriate for all types, but it doesn't hurt to read it.
                type = dis.readInt();
                is.reset();

                for (final IndexType indexType : IndexType.values()) {
                    if (indexType.magicNumber == magicNumber &&
                            (indexType.tribbleIndexType == null || indexType.tribbleIndexType == type)) {
                        return indexType;
                    }
                }
            } catch (final IOException e) {
                throw new TribbleException("Problem detecting index type", e);
            }

            throw new TribbleException.UnableToCreateCorrectIndexType(
                    String.format("Unknown index type.  magic number: 0x%x; type %d", magicNumber, type));
        }
    }


    /**
     * Load in index from the specified file.   The type of index (LinearIndex or IntervalTreeIndex) is determined
     * at run time by reading the type flag in the file.
     *
     * @param indexFile from which to load the index
     */
    public static Index loadIndex(final String indexFile) {
        return loadIndex(indexFile, (Function<SeekableByteChannel, SeekableByteChannel>) null);
    }

    /**
     * Load in index from the specified file.   The type of index (LinearIndex or IntervalTreeIndex) is determined
     * at run time by reading the type flag in the file.
     *
     * @param indexFile from which to load the index
     * @param indexWrapper a wrapper to apply to the raw byte stream of the index file, only applied to uri's loaded as
     *                     {@link java.nio.file.Path}
     */
    public static Index loadIndex(final String indexFile, Function<SeekableByteChannel, SeekableByteChannel> indexWrapper) {
        try {
            return loadIndex(indexFile, indexFileInputStream(indexFile, indexWrapper));
        } catch (final IOException ex) {
            throw new TribbleException.UnableToReadIndexFile("Unable to read index file", indexFile, ex);
        }
    }

    /**
     * Load in index from the specified stream. Note that the caller is responsible for decompressing the stream if necessary.
     *
     * @param source the stream source (typically the file name)
     * @param inputStream the raw byte stream of the index
     */
    public static Index loadIndex(final String source, final InputStream inputStream) {
        // Must be buffered, because getIndexType uses mark and reset
        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream, Defaults.NON_ZERO_BUFFER_SIZE)) {
            return createIndex(bufferedInputStream);
        } catch (final EOFException ex) {
                throw new TribbleException.CorruptedIndexFile("Index file is corrupted", source, ex);
        } catch (final IOException ex) {
            throw new TribbleException.UnableToReadIndexFile("Failed to read index file", source, ex);
        }
    }

    private static Index createIndex(BufferedInputStream bufferedInputStream) throws IOException {
        return IndexType.getIndexType(bufferedInputStream).createIndex(bufferedInputStream);
    }

    private static InputStream indexFileInputStream(final String indexFile, Function<SeekableByteChannel, SeekableByteChannel> indexWrapper) throws IOException {
        final InputStream inputStreamInitial = ParsingUtils.openInputStream(indexFile, indexWrapper);
        if (indexFile.endsWith(".gz")) {
            return new GZIPInputStream(inputStreamInitial);
        }
        else if (indexFile.endsWith(FileExtensions.TABIX_INDEX)) {
            return new BlockCompressedInputStream(inputStreamInitial);
        }
        else {
            return inputStreamInitial;
        }
    }

    /**
     * a helper method for creating a linear binned index with default bin size
     *
     * @param inputFile the input file to load features from
     * @param codec     the codec to use for decoding records
     */
    public static  <FEATURE_TYPE extends Feature, SOURCE_TYPE> LinearIndex createLinearIndex(final File inputFile, final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE> codec) {
        return createLinearIndex(
                IOUtil.toPath(inputFile),
                codec,
                LinearIndexCreator.DEFAULT_BIN_WIDTH);
    }

    /**
     * a helper method for creating a linear binned index with default bin size
     *
     * @param inputPath the input file to load features from
     * @param codec     the codec to use for decoding records
     */
    public static <FEATURE_TYPE extends Feature, SOURCE_TYPE> LinearIndex createLinearIndex(final Path inputPath,
                                                                                            final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE>  codec) {
        return createLinearIndex(inputPath, codec, LinearIndexCreator.DEFAULT_BIN_WIDTH);
    }

    /**
     * a helper method for creating a linear binned index
     *
     * @param inputFile the input file to load features from
     * @param codec     the codec to use for decoding records
     * @param binSize   the bin size
     */
    public static <FEATURE_TYPE extends Feature, SOURCE_TYPE> LinearIndex createLinearIndex(final File inputFile,
                                                                                            final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE> codec,
                                                                                            final int binSize) {
        return createLinearIndex(IOUtil.toPath(inputFile), codec, binSize);
    }

    /**
     * a helper method for creating a linear binned index
     *
     * @param inputPath the input path to load features from
     * @param codec     the codec to use for decoding records
     * @param binSize   the bin size
     */
    public static <FEATURE_TYPE extends Feature, SOURCE_TYPE> LinearIndex createLinearIndex(final Path inputPath,
                                                                                            final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE> codec,
                                                                                            final int binSize) {
        ValidationUtils.nonNull(inputPath, "input path must be non-null");
        final LinearIndexCreator indexCreator = new LinearIndexCreator(inputPath, binSize);
        return (LinearIndex)createIndex(inputPath, new FeatureIterator<>(inputPath, codec), indexCreator);
    }

    /**
     * create an interval-tree index with the default features per bin count
     *
     * @param inputFile the file containing the features
     * @param codec to decode the features
     */
    public static <FEATURE_TYPE extends Feature, SOURCE_TYPE> IntervalTreeIndex createIntervalIndex(final File inputFile,
                                                                                                    final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE> codec) {
        return createIntervalIndex(IOUtil.toPath(inputFile), codec, IntervalIndexCreator.DEFAULT_FEATURE_COUNT);
    }

    /**
     * create an interval-tree index with the default features per bin count
     *
     * @param inputPath the file containing the features
     * @param codec to decode the features
     */
    public static <FEATURE_TYPE extends Feature, SOURCE_TYPE> IntervalTreeIndex createIntervalIndex(final Path inputPath,
                                                                                                    final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE> codec) {
        return createIntervalIndex(inputPath, codec, IntervalIndexCreator.DEFAULT_FEATURE_COUNT);
    }


    /**
     * a helper method for creating an interval-tree index
     *
     * @param inputFile the input file to load features from
     * @param codec     the codec to use for decoding records
     * @param featuresPerInterval
     */
    public static <FEATURE_TYPE extends Feature, SOURCE_TYPE> IntervalTreeIndex createIntervalIndex(final File inputFile,
                                                                                                    final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE> codec,
                                                                                                    final int featuresPerInterval) {
        return createIntervalIndex(IOUtil.toPath(inputFile), codec, featuresPerInterval);
    }

    /**
     * a helper method for creating an interval-tree index
     *
     * @param inputPath the input path to load features from
     * @param codec     the codec to use for decoding records
     * @param featuresPerInterval
     */
    public static <FEATURE_TYPE extends Feature, SOURCE_TYPE> IntervalTreeIndex createIntervalIndex(final Path inputPath,
                                                                                                    final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE> codec,
                                                                                                    final int featuresPerInterval) {
        ValidationUtils.nonNull(inputPath, "input path must be non-null");
        final IntervalIndexCreator indexCreator = new IntervalIndexCreator(inputPath, featuresPerInterval);
        return (IntervalTreeIndex)createIndex(inputPath, new FeatureIterator<>(inputPath, codec), indexCreator);
    }

    /**
     * Create a dynamic index with the default balancing approach
     *
     * @param inputFile the input file to load features from
     * @param codec     the codec to use for decoding records
     */
    public static <FEATURE_TYPE extends Feature, SOURCE_TYPE> Index createDynamicIndex(final File inputFile, final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE> codec) {
        return createDynamicIndex(IOUtil.toPath(inputFile), codec, IndexBalanceApproach.FOR_SEEK_TIME);
    }

    /**
     * Create a dynamic index with the default balancing approach
     *
     * @param inputPath the input path to load features from
     * @param codec     the codec to use for decoding records
     */
    public static <FEATURE_TYPE extends Feature, SOURCE_TYPE> Index createDynamicIndex(final Path inputPath, final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE> codec) {
        return createDynamicIndex(inputPath, codec, IndexBalanceApproach.FOR_SEEK_TIME);
    }


    /**
     * Create a index of the specified type with default binning parameters
     *
     * @param inputFile the input file to load features from
     * @param codec     the codec to use for decoding records
     * @param type      the type of index to create
     */
    public static <FEATURE_TYPE extends Feature, SOURCE_TYPE> Index createIndex(final File inputFile,
                                                                                final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE> codec,
                                                                                final IndexType type) {
        return createIndex(IOUtil.toPath(inputFile), codec, type, null);
    }

    /**
     * Create a index of the specified type with default binning parameters
     *
     * @param inputhPath the input file to load features from
     * @param codec     the codec to use for decoding records
     * @param type      the type of index to create
     */
    public static <FEATURE_TYPE extends Feature, SOURCE_TYPE> Index createIndex(final Path inputhPath,
                                                                                final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE> codec,
                                                                                final IndexType type) {
        return createIndex(inputhPath, codec, type, null);
    }

    /**
     * Create an index of the specified type with default binning parameters
     *
     * @param inputFile the input File to load features from
     * @param codec     the codec to use for decoding records
     * @param type      the type of index to create
     * @param sequenceDictionary May be null, but if present may reduce memory footprint for tabix index creation
     */
    public static <FEATURE_TYPE extends Feature, SOURCE_TYPE> Index createIndex(final File inputFile,
                                                                                final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE> codec,
                                                                                final IndexType type,
                                                                                final SAMSequenceDictionary sequenceDictionary) {
        return createIndex(IOUtil.toPath(inputFile), codec, type, sequenceDictionary);
    }

    /**
     * Create an index of the specified type with default binning parameters
     *
     * @param inputPath the input path to load features from
     * @param codec     the codec to use for decoding records
     * @param type      the type of index to create
     * @param sequenceDictionary May be null, but if present may reduce memory footprint for tabix index creation
     */
    public static <FEATURE_TYPE extends Feature, SOURCE_TYPE> Index createIndex(final Path inputPath,
                                                                                final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE> codec,
                                                                                final IndexType type,
                                                                                final SAMSequenceDictionary sequenceDictionary) {
        switch (type) {
            case INTERVAL_TREE: return createIntervalIndex(inputPath, codec);
            case LINEAR:        return createLinearIndex(inputPath, codec);
            case TABIX:         return createTabixIndex(inputPath, codec, sequenceDictionary);
            default: throw new IllegalArgumentException("Unrecognized IndexType " + type);
        }
    }

    /**
     * Write the index to a file; little endian.
     * @param idx
     * @param idxFile
     * @throws IOException
     * @deprecated use {@link Index#write(File)} instead
     */
    @Deprecated
    public static void writeIndex(final Index idx, final File idxFile) throws IOException {
        idx.write(idxFile);
    }

    /**
     * create a dynamic index, given an input file, codec, and balance approach
     *
     * @param inputFile the input file to load features from
     * @param codec     the codec to use for decoding records
     * @param iba       the index balancing approach
     */
    public static <FEATURE_TYPE extends Feature, SOURCE_TYPE> Index createDynamicIndex(final File inputFile,
                                                                                       final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE> codec,
                                                                                       final IndexBalanceApproach iba) {
        return createDynamicIndex(IOUtil.toPath(inputFile), codec, iba);
    }

    /**
     * create a dynamic index, given an input path, codec, and balance approach
     *
     * @param inputPath the input path to load features from
     * @param codec     the codec to use for decoding records
     * @param iba       the index balancing approach
     */
    public static <FEATURE_TYPE extends Feature, SOURCE_TYPE> Index createDynamicIndex(final Path inputPath,
                                                                                       final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE> codec,
                                                                                       final IndexBalanceApproach iba) {
        ValidationUtils.nonNull(inputPath, "input path must be non-null");
        // get a list of index creators
        final DynamicIndexCreator indexCreator = new DynamicIndexCreator(inputPath, iba);
        return createIndex(inputPath, new FeatureIterator<>(inputPath, codec), indexCreator);
    }

    /**
     * @param inputFile The file to be indexed.
     * @param codec Mechanism for reading inputFile.
     * @param tabixFormat Header fields for TabixIndex to be produced.
     * @param sequenceDictionary May be null, but if present may reduce memory footprint for index creation.  Features
     *                           in inputFile must be in the order defined by sequenceDictionary, if it is present.
     */
    public static <FEATURE_TYPE extends Feature, SOURCE_TYPE> TabixIndex createTabixIndex(final File inputFile,
                                                                                          final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE> codec,
                                                                                          final TabixFormat tabixFormat,
                                                                                          final SAMSequenceDictionary sequenceDictionary) {
        return createTabixIndex(IOUtil.toPath(inputFile), codec, tabixFormat, sequenceDictionary);
    }

    /**
     * @param inputPath The path to be indexed.
     * @param codec Mechanism for reading inputFile.
     * @param tabixFormat Header fields for TabixIndex to be produced.
     * @param sequenceDictionary May be null, but if present may reduce memory footprint for index creation.  Features
     *                           in inputFile must be in the order defined by sequenceDictionary, if it is present.
     */
    public static <FEATURE_TYPE extends Feature, SOURCE_TYPE> TabixIndex createTabixIndex(final Path inputPath,
                                                                                          final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE> codec,
                                                                                          final TabixFormat tabixFormat,
                                                                                          final SAMSequenceDictionary sequenceDictionary) {
        ValidationUtils.nonNull(inputPath, "input path must be non-null");
        final TabixIndexCreator indexCreator = new TabixIndexCreator(sequenceDictionary, tabixFormat);
        return (TabixIndex)createIndex(inputPath, new FeatureIterator<>(inputPath, codec), indexCreator);
    }

    /**
     * @param inputFile The file to be indexed.
     * @param codec the codec to use for decoding records
     * @param sequenceDictionary May be null, but if present may reduce memory footprint for index creation.  Features
     *                           in inputFile must be in the order defined by sequenceDictionary, if it is present.
     *
     */
    public static <FEATURE_TYPE extends Feature, SOURCE_TYPE> TabixIndex createTabixIndex(final File inputFile,
                                                                                          final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE> codec,
                                                                                          final SAMSequenceDictionary sequenceDictionary) {
        return createTabixIndex(IOUtil.toPath(inputFile), codec, codec.getTabixFormat(), sequenceDictionary);
    }

    /**
     * @param inputPath The path to be indexed.
     * @param codec the codec to use for decoding records
     * @param sequenceDictionary May be null, but if present may reduce memory footprint for index creation.  Features
     *                           in inputFile must be in the order defined by sequenceDictionary, if it is present.
     *
     */
    public static <FEATURE_TYPE extends Feature, SOURCE_TYPE> TabixIndex createTabixIndex(final Path inputPath,
                                                                                          final FeatureCodec<FEATURE_TYPE, SOURCE_TYPE> codec,
                                                                                          final SAMSequenceDictionary sequenceDictionary) {
        return createTabixIndex(inputPath, codec, codec.getTabixFormat(), sequenceDictionary);
    }

    private static Index createIndex(final Path inputPath, final FeatureIterator iterator, final IndexCreator creator) {
        Feature lastFeature = null;
        Feature currentFeature;
        final Map<String, Feature> visitedChromos = new HashMap<>(40);
        while (iterator.hasNext()) {
            final long position = iterator.getPosition();
            currentFeature = iterator.next();

            checkSorted(inputPath, lastFeature, currentFeature);
            //should only visit chromosomes once
            final String curChr = currentFeature.getContig();
            final String lastChr = lastFeature != null ? lastFeature.getContig() : null;
            if(!curChr.equals(lastChr)){
                if(visitedChromos.containsKey(curChr)){
                    String msg = "Input file must have contiguous chromosomes.";
                    msg += " Saw feature " + featToString(visitedChromos.get(curChr));
                    msg += " followed later by " + featToString(lastFeature);
                    msg += " and then " + featToString(currentFeature);
                    throw new TribbleException.MalformedFeatureFile(msg, inputPath.toString());
                }else{
                    visitedChromos.put(curChr, currentFeature);
                }
            }

            creator.addFeature(currentFeature, position);

            lastFeature = currentFeature;
        }

        // Get the end position of the last feature before closing the iterator
        long finalPosition = iterator.getPosition();
        iterator.close();
        return creator.finalizeIndex(finalPosition);
    }

    private static String featToString(final Feature feature){
        return feature.getContig() + ":" + feature.getStart() + "-" + feature.getEnd();
    }

    private static void checkSorted(final Path inputPath, final Feature lastFeature, final Feature currentFeature){
        // if the last currentFeature is after the current currentFeature, exception out
        if (lastFeature != null && currentFeature.getStart() < lastFeature.getStart() && lastFeature.getContig().equals(currentFeature.getContig()))
            throw new TribbleException.MalformedFeatureFile("Input file is not sorted by start position. \n" +
                    "We saw a record with a start of " + currentFeature.getContig() + ":" + currentFeature.getStart() +
                    " after a record with a start of " + lastFeature.getContig() + ":" + lastFeature.getStart(), inputPath.toString());
    }


    /**
     * Iterator for reading features from a file, given a {@code FeatureCodec}.
     */
    static class FeatureIterator<FEATURE_TYPE extends Feature, SOURCE> implements CloseableTribbleIterator<Feature> {
        // the stream we use to get features
        private final SOURCE source;
        // the next feature
        private Feature nextFeature;
        // our codec
        private final FeatureCodec<FEATURE_TYPE, SOURCE> codec;
        private final Path inputPath;

        // we also need cache our position
        private long cachedPosition;

        /**
         *
         * @param inputFile The file from which to read. Stream for reading is opened on construction. May not be null.
         * @param codec
         */
        public FeatureIterator(final File inputFile, final FeatureCodec<FEATURE_TYPE, SOURCE> codec) {
            this(IOUtil.toPath(inputFile), codec);
        }

        /**
         *
         * @param inputPath The path from which to read. Stream for reading is opened on construction. May not be null.
         * @param codec
         */
        public FeatureIterator(final Path inputPath, final FeatureCodec<FEATURE_TYPE, SOURCE> codec) {
            ValidationUtils.nonNull(inputPath, "FeatureIterator input path cannot be null");
            this.codec = codec;

            // We must call getPathToDataFile here to work with codecs that store their configuration and data separately
            final String filePath = codec.getPathToDataFile(inputPath.toUri().toString());

            try {
                this.inputPath = IOUtil.getPath(filePath);
            } catch (final IOException e) {
                throw new TribbleException("Failed while constructing a FeatureIterator due to a problem converting String to Path", e);
            }

            try {
                // Since we modified inputPath above, we MUST use this.inputPath for all checks and file creations
                // for the rest of this method!
                if (IOUtil.hasBlockCompressedExtension(this.inputPath)) {
                    final BlockCompressedInputStream bcs = initIndexableBlockCompressedStream(this.inputPath);
                    source = (SOURCE) codec.makeIndexableSourceFromStream(bcs);
                } else {
                    final PositionalBufferedStream ps = initIndexablePositionalStream(this.inputPath);
                    source = (SOURCE) codec.makeIndexableSourceFromStream(ps);
                }
                this.codec.readHeader(source);
                readNextFeature();
            } catch (final IOException e) {
                throw new TribbleException.InvalidHeader("Error reading header " + e.getMessage());
            }
        }

        private static PositionalBufferedStream initIndexablePositionalStream(final Path inputPath) {
            try {
                final InputStream fileStream = Files.newInputStream(inputPath);
                return new PositionalBufferedStream(fileStream);
            } catch (final IOException e) {
                throw new TribbleException.FeatureFileDoesntExist(
                        "Unable to open the input file, most likely the file doesn't exist.",
                        inputPath.toString());
            }
        }

        private static BlockCompressedInputStream initIndexableBlockCompressedStream(final Path inputPath) {
            // test that this is in fact a valid block compressed file
            try {
                if (!IOUtil.isBlockCompressed(inputPath, true)) {
                    throw new TribbleException.MalformedFeatureFile("Input file is not in valid block compressed format.",
                            inputPath.toString());
                }

                final ISeekableStreamFactory ssf = SeekableStreamFactory.getInstance();
                final SeekableStream seekableStream = ssf.getStreamFor(inputPath.toUri().toString());
                return new BlockCompressedInputStream(seekableStream);
            } catch (final FileNotFoundException e) {
                throw new TribbleException.FeatureFileDoesntExist("Unable to open the input file, most likely the file doesn't exist.",
                        inputPath.toString());
            } catch (final IOException e) {
                throw new TribbleException.MalformedFeatureFile("Error initializing stream", inputPath.toString(), e);
            }
        }

        @Override
        public boolean hasNext() {
            return nextFeature != null;
        }

        @Override
        public Feature next() {
            final Feature ret = nextFeature;
            readNextFeature();
            return ret;
        }

        /**
         * @throws UnsupportedOperationException
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException("We cannot remove");
        }


        /**
         * @return the file position from the underlying reader
         */
        public long getPosition() {
            return (hasNext()) ? cachedPosition : ((LocationAware) source).getPosition();
        }

        @Override
        public Iterator<Feature> iterator() {
            return this;
        }

        @Override
        public void close() {
            codec.close(source);
        }

        /**
         * Read the next feature from the stream
         * @throws TribbleException.MalformedFeatureFile
         */
        private void readNextFeature() {
            cachedPosition = ((LocationAware) source).getPosition();
            try {
                nextFeature = null;
                while (nextFeature == null && !codec.isDone(source)) {
                    nextFeature = codec.decodeLoc(source);
                }
            } catch (final IOException e) {
                throw new TribbleException.MalformedFeatureFile("Unable to read a line from the file", inputPath.toString(), e);
            }
        }
    }
}
