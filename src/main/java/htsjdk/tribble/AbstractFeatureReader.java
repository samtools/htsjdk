/*
 * Copyright (c) 2007-2010 by The Broad Institute, Inc. and the Massachusetts Institute of Technology.
 * All Rights Reserved.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL), Version 2.1 which
 * is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 *
 * THE SOFTWARE IS PROVIDED "AS IS." THE BROAD AND MIT MAKE NO REPRESENTATIONS OR WARRANTIES OF
 * ANY KIND CONCERNING THE SOFTWARE, EXPRESS OR IMPLIED, INCLUDING, WITHOUT LIMITATION, WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NONINFRINGEMENT, OR THE ABSENCE OF LATENT
 * OR OTHER DEFECTS, WHETHER OR NOT DISCOVERABLE.  IN NO EVENT SHALL THE BROAD OR MIT, OR THEIR
 * RESPECTIVE TRUSTEES, DIRECTORS, OFFICERS, EMPLOYEES, AND AFFILIATES BE LIABLE FOR ANY DAMAGES OF
 * ANY KIND, INCLUDING, WITHOUT LIMITATION, INCIDENTAL OR CONSEQUENTIAL DAMAGES, ECONOMIC
 * DAMAGES OR INJURY TO PROPERTY AND LOST PROFITS, REGARDLESS OF WHETHER THE BROAD OR MIT SHALL
 * BE ADVISED, SHALL HAVE OTHER REASON TO KNOW, OR IN FACT SHALL KNOW OF THE POSSIBILITY OF THE
 * FOREGOING.
 */

package htsjdk.tribble;

import htsjdk.samtools.seekablestream.SeekableStreamFactory;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.BlockCompressedStreamConstants;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import htsjdk.tribble.index.Index;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.tribble.util.ParsingUtils;
import htsjdk.tribble.util.TabixUtils;
import htsjdk.variant.bcf2.BCF2Codec;
import htsjdk.variant.bcf2.BCFCsiIndex;
import htsjdk.variant.bcf2.BCFFileReader;
import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * jrobinso
 * <p/>
 * the feature reader class, which uses indices and codecs to read in Tribble file formats.
 */
public abstract class AbstractFeatureReader<T extends Feature, SOURCE> implements FeatureReader<T> {
    // the logging destination for this source
    // private final static Logger log = Logger.getLogger("BasicFeatureSource");

    /**
     * The path to underlying data file, this must be the input path converted with {@link FeatureCodec#getPathToDataFile(String)}
     */
    String path;

    // a wrapper to apply to the raw stream of the Feature file to allow features like prefetching and caching to be
    // injected
    final Function<SeekableByteChannel, SeekableByteChannel> wrapper;
    // a wrapper to apply to the raw stream of the index file
    final Function<SeekableByteChannel, SeekableByteChannel> indexWrapper;

    // the query source, codec, and header
    // protected final QuerySource querySource;
    protected final FeatureCodec<T, SOURCE> codec;
    protected FeatureCodecHeader header;

    private static ComponentMethods methods = new ComponentMethods();

    private static final Log log = Log.getInstance(AbstractFeatureReader.class);

    /** @deprecated since June 2019 use {@link FileExtensions#BLOCK_COMPRESSED} instead. */
    @Deprecated
    public static final Set<String> BLOCK_COMPRESSED_EXTENSIONS = FileExtensions.BLOCK_COMPRESSED;

    /**
     * Calls {@link #getFeatureReader(String, FeatureCodec, boolean)} with {@code requireIndex} = true
     */
    public static <FEATURE extends Feature, SOURCE> AbstractFeatureReader<FEATURE, SOURCE> getFeatureReader(
            final String featureFile, final FeatureCodec<FEATURE, SOURCE> codec) throws TribbleException {
        return getFeatureReader(featureFile, codec, true);
    }

    /**
     * {@link #getFeatureReader(String, String, FeatureCodec, boolean, Function, Function)} with {@code null} for indexResource, wrapper, and indexWrapper
     * @throws TribbleException
     */
    public static <FEATURE extends Feature, SOURCE> AbstractFeatureReader<FEATURE, SOURCE> getFeatureReader(
            final String featureResource, final FeatureCodec<FEATURE, SOURCE> codec, final boolean requireIndex)
            throws TribbleException {
        return getFeatureReader(featureResource, null, codec, requireIndex, null, null);
    }

    /**
     * {@link #getFeatureReader(String, String, FeatureCodec, boolean, Function, Function)} with {@code null} for wrapper, and indexWrapper
     * @throws TribbleException
     */
    public static <FEATURE extends Feature, SOURCE> AbstractFeatureReader<FEATURE, SOURCE> getFeatureReader(
            final String featureResource,
            String indexResource,
            final FeatureCodec<FEATURE, SOURCE> codec,
            final boolean requireIndex)
            throws TribbleException {
        return getFeatureReader(featureResource, indexResource, codec, requireIndex, null, null);
    }

    /**
     * Opens a feature file with the reader for its format and index.
     *
     * <p>A BCF, read with a {@link BCF2Codec}, is told BGZF-compressed or not from its first bytes, whatever it is
     * named. A BGZF-compressed BCF is read by {@link BCFFileReader}, through a CSI index: the one named, or else
     * {@code x.bcf.csi} or {@code x.csi} beside {@code x.bcf}. Any other BCF is read by
     * {@link TribbleIndexedFeatureReader}, through a Tribble index ({@code x.bcf.idx} or {@code x.bcf.idx.gz}). A
     * named index, and a CSI found beside a BGZF-compressed BCF, are checked by their contents. One that can't address
     * the BCF (a TBI, a CSI over a BCF that is not BGZF-compressed, a Tribble index over one that is, or a file that
     * is no index at all) is refused if it is named; if it is found beside the BCF, where none of the right kind is, a
     * warning is logged and the BCF is read without an index, or refused if {@code requireIndex}.
     *
     * @param featureResource the feature file to create from
     * @param indexResource   the index for the feature file. If null, will auto-generate (if necessary)
     * @param codec           the codec to use to decode the individual features
     * @param requireIndex    whether an index is required for this file
     * @param wrapper         a wrapper to apply to the byte stream from the featureResource allowing injecting features
     *                        like caching and prefetching of the stream, may be null, will only be applied if featureResource
     *                        is a uri representing a {@link java.nio.file.Path}
     * @param indexWrapper    a wrapper to apply to the byte stream from the indexResource, may be null, will only be
     *                        applied if indexResource is a uri representing a {@link java.nio.file.Path}
     *
     * @throws TribbleException
     */
    public static <FEATURE extends Feature, SOURCE> AbstractFeatureReader<FEATURE, SOURCE> getFeatureReader(
            final String featureResource,
            String indexResource,
            final FeatureCodec<FEATURE, SOURCE> codec,
            final boolean requireIndex,
            Function<SeekableByteChannel, SeekableByteChannel> wrapper,
            Function<SeekableByteChannel, SeekableByteChannel> indexWrapper)
            throws TribbleException {
        try {
            // Note that we use pathToDataFile here when determining the file type, but featureResource when
            // constructing the readers.
            // This is because the reader's constructor will convert the path and it needs to be converted exactly once.
            final String pathToDataFile = codec.getPathToDataFile(featureResource);

            // A BCF's compression is told from its bytes, before the tabix test, which goes by name and is for text.
            // If they can't be read, the readers below report it as for any other file.
            if (codec instanceof BCF2Codec) {
                final Optional<Boolean> bgzf = isBgzf(pathToDataFile);
                if (bgzf.isPresent()) {
                    return openBcf(
                            featureResource,
                            pathToDataFile,
                            indexResource,
                            codec,
                            bgzf.get(),
                            requireIndex,
                            wrapper,
                            indexWrapper);
                }
            }

            // Test for tabix index.
            if (methods.isTabix(pathToDataFile, indexResource)) {
                if (!(codec instanceof AsciiFeatureCodec))
                    throw new TribbleException(
                            "Tabix indexed files only work with ASCII codecs, but received non-Ascii codec "
                                    + codec.getClass().getSimpleName());
                return new TabixFeatureReader<>(
                        featureResource, indexResource, (AsciiFeatureCodec) codec, wrapper, indexWrapper);
            }
            // Not tabix => tribble index file (might be gzipped, but not block gzipped)
            else {
                return new TribbleIndexedFeatureReader<>(
                        featureResource, indexResource, codec, requireIndex, wrapper, indexWrapper);
            }
        } catch (final IOException e) {
            throw new TribbleException.MalformedFeatureFile(
                    "Unable to create BasicFeatureReader using feature file ", featureResource, e);
        } catch (final TribbleException e) {
            e.setSource(featureResource);
            throw e;
        }
    }

    /**
     * Whether a file is BGZF-compressed, from its first bytes. They are read without the caller's channel wrapper,
     * which may prefetch far more than the few bytes needed.
     *
     * @return empty if the file can't be opened or read
     */
    private static Optional<Boolean> isBgzf(final String path) {
        try (InputStream in = new BufferedInputStream(
                SeekableStreamFactory.getInstance().getStreamFor(path, null),
                BlockCompressedStreamConstants.BLOCK_HEADER_LENGTH)) {
            return Optional.of(BlockCompressedInputStream.isValidFile(in));
        } catch (final IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Opens a BCF with the reader for its compression and an index of the kind that can address it: a
     * BGZF-compressed BCF with {@link BCFFileReader} and a CSI, any other with {@link TribbleIndexedFeatureReader} and
     * a Tribble index. An index of the other kind, or a TBI, is refused if it is named; if it is found beside the BCF,
     * where one of the right kind is not, the BCF is read without an index, unless one is required.
     *
     * @param bgzf whether the BCF is BGZF-compressed
     * @throws TribbleException if the index named can't address the BCF, or if an index is required and none that
     *     can is found
     */
    @SuppressWarnings("unchecked")
    private static <FEATURE extends Feature, SOURCE> AbstractFeatureReader<FEATURE, SOURCE> openBcf(
            final String featureResource,
            final String pathToDataFile,
            final String indexResource,
            final FeatureCodec<FEATURE, SOURCE> codec,
            final boolean bgzf,
            final boolean requireIndex,
            final Function<SeekableByteChannel, SeekableByteChannel> wrapper,
            final Function<SeekableByteChannel, SeekableByteChannel> indexWrapper)
            throws IOException {
        if (!bgzf) {
            if (indexResource != null) {
                checkNamedBcfIndex(pathToDataFile, indexResource, false, indexWrapper);
            } else if (findIndex(tribbleIndexNames(pathToDataFile)) == null) {
                checkForAnIndexOfTheWrongKind(pathToDataFile, false, requireIndex);
            }
            // It looks for x.bcf.idx itself, and refuses the BCF if an index is required and there is none
            return new TribbleIndexedFeatureReader<>(
                    featureResource, indexResource, codec, requireIndex, wrapper, indexWrapper);
        }

        final String csi;
        if (indexResource != null) {
            checkNamedBcfIndex(pathToDataFile, indexResource, true, indexWrapper);
            csi = indexResource;
        } else {
            final List<String> csiNames = csiIndexNames(pathToDataFile);
            final String found = findIndex(csiNames);
            final String notACsi = found == null ? null : whyNotACsi(found, indexWrapper);
            if (notACsi != null) {
                // A file under a CSI's name that is not one is an index of the wrong kind found beside the BCF
                rejectIndexFoundBeside(unusableIndexMessage(pathToDataFile, found, notACsi, true), requireIndex);
                csi = null;
            } else {
                csi = found;
            }
            if (csi == null && notACsi == null) {
                checkForAnIndexOfTheWrongKind(pathToDataFile, true, requireIndex);
                if (requireIndex) {
                    throw new TribbleException("An index is required, but no CSI index was found for the "
                            + "BGZF-compressed BCF " + pathToDataFile + " as " + String.join(" or ", csiNames)
                            + ". " + FIX_FOR_BGZF_BCF);
                }
            }
        }
        return (AbstractFeatureReader<FEATURE, SOURCE>) (AbstractFeatureReader<?, ?>)
                new BCFFileReader(featureResource, csi, (BCF2Codec) codec, wrapper, indexWrapper);
    }

    private static final String TBI_OVER_BCF =
            "it is a TBI index, and a BCF cannot be indexed with TBI (htslib refuses to build one)";
    private static final String CSI_OVER_RAW_BCF =
            "it is a CSI index, whose offsets are BGZF virtual offsets, and the BCF is not BGZF-compressed";
    private static final String TRIBBLE_OVER_BGZF_BCF = "it is a Tribble index, whose offsets are into the "
            + "decompressed data, so a query through it would find nothing in a BGZF-compressed BCF";
    private static final String FIX_FOR_BGZF_BCF = "Index the BCF with CSI, as bcftools index or "
            + "IndexFactory.createIndex with IndexType.CSI does, or as VariantContextWriterBuilder does with "
            + "Options.INDEX_ON_THE_FLY when it writes the BCF";
    private static final String FIX_FOR_RAW_BCF = "Index the BCF with a Tribble index "
            + "(IndexFactory.createDynamicIndex), or compress it to BGZF with bcftools view -Ob and index that with "
            + "bcftools index";

    /** The names a CSI index of the BCF at {@code path} is found by, in the order htslib looks for them. */
    private static List<String> csiIndexNames(final String path) {
        final String withExtension = ParsingUtils.appendToPath(path, FileExtensions.CSI);
        final String withoutExtension = ParsingUtils.appendToPath(withoutExtension(path), FileExtensions.CSI);
        return withExtension.equals(withoutExtension)
                ? List.of(withExtension)
                : List.of(withExtension, withoutExtension);
    }

    /** The names a Tribble index of the BCF at {@code path} is found by, as TribbleIndexedFeatureReader looks. */
    private static List<String> tribbleIndexNames(final String path) {
        final String tribbleIndex = Tribble.indexFile(path);
        return List.of(tribbleIndex, ParsingUtils.appendToPath(tribbleIndex, ".gz"));
    }

    /** {@code path} without the extension of its file name, if it has one; the query of a URL is kept. */
    private static String withoutExtension(final String path) {
        String filePath = path;
        try {
            filePath = new URL(path).getPath();
        } catch (final MalformedURLException e) {
            // not a URL that java.net knows, so the whole string is the path
        }
        final int dot = filePath.lastIndexOf('.');
        if (dot <= filePath.lastIndexOf('/') + 1) {
            return path;
        }
        // only the path's own occurrence, which is the last; the same text may come earlier, as in a host name
        final int at = path.lastIndexOf(filePath);
        return path.substring(0, at) + filePath.substring(0, dot) + path.substring(at + filePath.length());
    }

    /** @return the first of {@code names} that exists, or null if none does */
    private static String findIndex(final List<String> names) throws IOException {
        for (final String name : names) {
            if (ParsingUtils.resourceExists(name)) {
                return name;
            }
        }
        return null;
    }

    /**
     * Checks that the index named for a BCF, known by its first bytes, is of a kind that can address it. For a BCF
     * that is not BGZF-compressed, an index that can't be read is left to TribbleIndexedFeatureReader to report.
     *
     * @param bgzf whether the BCF is BGZF-compressed
     * @throws TribbleException if the index is of a kind that can't address the BCF, or if the BCF is
     *     BGZF-compressed and the index can't be read
     */
    private static void checkNamedBcfIndex(
            final String bcf,
            final String index,
            final boolean bgzf,
            final Function<SeekableByteChannel, SeekableByteChannel> indexWrapper) {
        final IndexFactory.IndexType type;
        try {
            type = readIndexType(index, indexWrapper);
        } catch (final IOException e) {
            if (bgzf) {
                throw new TribbleException("Unable to read " + index + ", the index of " + bcf, e);
            }
            return;
        } catch (final TribbleException e) {
            if (bgzf) {
                throw new TribbleException(
                        unusableIndexMessage(bcf, index, "it is not a CSI index (" + e.getMessage() + ")", true), e);
            }
            return;
        }
        final String reason =
                switch (type) {
                    case TABIX -> TBI_OVER_BCF;
                    case CSI -> bgzf ? null : CSI_OVER_RAW_BCF;
                    case LINEAR, INTERVAL_TREE -> bgzf ? TRIBBLE_OVER_BGZF_BCF : null;
                };
        if (reason != null) {
            throw new TribbleException(unusableIndexMessage(bcf, index, reason, bgzf));
        }
    }

    /**
     * Reads the type of an index from its magic number, decompressing it first if it is compressed: a TBI or CSI is
     * BGZF-compressed whatever it is named, and a Tribble index may be gzipped.
     *
     * @throws TribbleException if it is of no type htsjdk reads
     */
    private static IndexFactory.IndexType readIndexType(
            final String index, final Function<SeekableByteChannel, SeekableByteChannel> indexWrapper)
            throws IOException {
        try (BufferedInputStream raw = IOUtil.toBufferedStream(ParsingUtils.openInputStream(index, indexWrapper))) {
            if (!IOUtil.isGZIPInputStream(raw)) {
                return IndexFactory.IndexType.getIndexType(raw);
            }
            try (BufferedInputStream decompressed = IOUtil.toBufferedStream(IOUtil.openGzipOrBgzfStream(raw))) {
                return IndexFactory.IndexType.getIndexType(decompressed);
            }
        }
    }

    /**
     * Looks beside a BCF without an index of the right kind for one of a kind that can't address it: a Tribble index
     * beside a BGZF-compressed BCF, a CSI beside any other, or a TBI beside either. One found is not used.
     *
     * @param bgzf whether the BCF is BGZF-compressed
     * @throws TribbleException if one is found and an index is required; if one is not required, a warning is logged
     */
    private static void checkForAnIndexOfTheWrongKind(final String bcf, final boolean bgzf, final boolean requireIndex)
            throws IOException {
        String index = findIndex(bgzf ? tribbleIndexNames(bcf) : csiIndexNames(bcf));
        String reason = bgzf ? TRIBBLE_OVER_BGZF_BCF : CSI_OVER_RAW_BCF;
        if (index == null) {
            index = findIndex(List.of(ParsingUtils.appendToPath(bcf, FileExtensions.TABIX_INDEX)));
            reason = TBI_OVER_BCF;
        }
        if (index == null) {
            return;
        }
        rejectIndexFoundBeside(unusableIndexMessage(bcf, index, reason, bgzf), requireIndex);
    }

    /**
     * Refuses the BCF if an index is required, since the one found beside it can't be used; otherwise warns that it
     * is read without one.
     */
    private static void rejectIndexFoundBeside(final String message, final boolean requireIndex) {
        if (requireIndex) {
            throw new TribbleException(message);
        }
        log.warn(message, ". The BCF is read without an index.");
    }

    /** @return why {@code index} is not a CSI, or null if it is one */
    private static String whyNotACsi(
            final String index, final Function<SeekableByteChannel, SeekableByteChannel> indexWrapper) {
        try {
            final IndexFactory.IndexType type = readIndexType(index, indexWrapper);
            return switch (type) {
                case CSI -> null;
                case TABIX -> TBI_OVER_BCF;
                case LINEAR, INTERVAL_TREE -> TRIBBLE_OVER_BGZF_BCF;
            };
        } catch (final IOException | TribbleException e) {
            return "it is not an index htsjdk can read (" + e.getMessage() + ")";
        }
    }

    /** Says why {@code index} can't address {@code bcf}, and what to index it with instead. */
    private static String unusableIndexMessage(
            final String bcf, final String index, final String reason, final boolean bgzf) {
        return "Cannot use " + index + " as the index of " + bcf + ": " + reason + ". "
                + (bgzf ? FIX_FOR_BGZF_BCF : FIX_FOR_RAW_BCF);
    }

    /**
     * Return a reader with a supplied index.
     *
     * @param featureResource the path to the source file containing the features
     * @param codec used to decode the features
     * @param index index of featureResource; not a {@link BCFCsiIndex}, which is read from its file
     * @return a reader for this data
     * @throws TribbleException
     */
    public static <FEATURE extends Feature, SOURCE> AbstractFeatureReader<FEATURE, SOURCE> getFeatureReader(
            final String featureResource, final FeatureCodec<FEATURE, SOURCE> codec, final Index index)
            throws TribbleException {
        if (index instanceof BCFCsiIndex) {
            throw new TribbleException("A BCF's CSI index is not read from memory: write it with "
                    + "BCFCsiIndex.write or writeBasedOnFeaturePath, and open the BCF through getFeatureReader with "
                    + "the index's path, or with none to find it beside the BCF: " + featureResource);
        }
        try {
            return new TribbleIndexedFeatureReader<>(featureResource, codec, index);
        } catch (final IOException e) {
            throw new TribbleException.MalformedFeatureFile(
                    "Unable to create AbstractFeatureReader using feature file ", featureResource, e);
        }
    }

    protected AbstractFeatureReader(final String path, final FeatureCodec<T, SOURCE> codec) {
        this(path, codec, null, null);
    }

    protected AbstractFeatureReader(
            final String path,
            final FeatureCodec<T, SOURCE> codec,
            final Function<SeekableByteChannel, SeekableByteChannel> wrapper,
            final Function<SeekableByteChannel, SeekableByteChannel> indexWrapper) {
        this.path = codec.getPathToDataFile(path);
        this.codec = codec;
        this.wrapper = wrapper;
        this.indexWrapper = indexWrapper;
    }

    /**
     * Whether the reader has an index or not
     * Default implementation returns false
     * @return false
     */
    public boolean hasIndex() {
        return false;
    }

    /**
     * @return true if the reader has an index, which means that it can be queried.
     */
    @Override
    public boolean isQueryable() {
        return hasIndex();
    }

    public static void setComponentMethods(ComponentMethods methods) {
        AbstractFeatureReader.methods = methods;
    }

    /**
     * @deprecated use {@link IOUtil#hasBlockCompressedExtension(String)}.
     */
    @Deprecated
    public static boolean hasBlockCompressedExtension(final String fileName) {
        return IOUtil.hasBlockCompressedExtension(fileName);
    }

    /**
     * @deprecated use {@link IOUtil#hasBlockCompressedExtension(Path)}.
     */
    @Deprecated
    public static boolean hasBlockCompressedExtension(final Path path) {
        return IOUtil.hasBlockCompressedExtension(path.getFileName().toString());
    }

    /**
     * @deprecated use {@link IOUtil#hasBlockCompressedExtension(URI)}.
     */
    @Deprecated
    public static boolean hasBlockCompressedExtension(final URI uri) {
        String path = uri.getPath();
        return IOUtil.hasBlockCompressedExtension(path);
    }

    /**
     * get the header
     *
     * @return the header object we've read-in
     */
    @Override
    public Object getHeader() {
        return header.getHeaderValue();
    }

    /**
     * Closes each of {@code closeables} that is not null, every one of them even if some fail to close. The first
     * failure is then thrown, with any later ones suppressed into it.
     */
    static void closeAll(final List<? extends Closeable> closeables) throws IOException {
        Exception failure = null;
        for (final Closeable closeable : closeables) {
            try {
                if (closeable != null) closeable.close();
            } catch (final IOException | RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else if (failure != e) {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure instanceof IOException) throw (IOException) failure;
        if (failure != null) throw (RuntimeException) failure;
    }

    static class EmptyIterator<T extends Feature> implements CloseableTribbleIterator<T> {
        @Override
        public Iterator<T> iterator() {
            return this;
        }

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public T next() {
            return null;
        }

        @Override
        public void remove() {}

        @Override
        public void close() {}
    }

    /**
     * @return true if the resource is block-compressed and has a tabix index: the one named, or if none is named a
     *     CSI or TBI index beside it
     */
    public static boolean isTabix(String resourcePath, String indexPath) throws IOException {
        if (!IOUtil.hasBlockCompressedExtension(resourcePath)) {
            return false;
        }
        return indexPath == null ? TabixUtils.findIndex(resourcePath) != null : ParsingUtils.resourceExists(indexPath);
    }

    public static class ComponentMethods {

        public boolean isTabix(String resourcePath, String indexPath) throws IOException {
            return AbstractFeatureReader.isTabix(resourcePath, indexPath);
        }
    }
}
