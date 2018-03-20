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

import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.seekablestream.SeekableStreamFactory;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.tribble.index.Index;
import htsjdk.tribble.util.ParsingUtils;
import htsjdk.tribble.util.TabixUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.util.*;
import java.util.function.Function;

/**
 * jrobinso
 * <p/>
 * the feature reader class, which uses indices and codecs to read in Tribble file formats.
 */
public abstract class AbstractFeatureReader<T extends Feature, SOURCE> implements FeatureReader<T> {
    // the logging destination for this source
    //private final static Logger log = Logger.getLogger("BasicFeatureSource");

    // the path to underlying data source
    String path;

    // a wrapper to apply to the raw stream of the Feature file to allow features like prefetching and caching to be injected
    final Function<SeekableByteChannel, SeekableByteChannel> wrapper;
    // a wrapper to apply to the raw stream of the index file
    final Function<SeekableByteChannel, SeekableByteChannel> indexWrapper;

    // the query source, codec, and header
    // protected final QuerySource querySource;
    protected final FeatureCodec<T, SOURCE> codec;
    protected FeatureCodecHeader header;

    private static ComponentMethods methods = new ComponentMethods();

    public static final Set<String> BLOCK_COMPRESSED_EXTENSIONS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(".gz", ".gzip", ".bgz", ".bgzf")));

    /**
     * Calls {@link #getFeatureReader(String, FeatureCodec, boolean)} with {@code requireIndex} = true
     */
    public static <FEATURE extends Feature, SOURCE> AbstractFeatureReader<FEATURE, SOURCE> getFeatureReader(final String featureFile, final FeatureCodec<FEATURE, SOURCE> codec) throws TribbleException {
        return getFeatureReader(featureFile, codec, true);
    }

    /**
     * {@link #getFeatureReader(String, String, FeatureCodec, boolean, Function, Function)} with {@code null} for indexResource, wrapper, and indexWrapper
     * @throws TribbleException
     */
    public static <FEATURE extends Feature, SOURCE> AbstractFeatureReader<FEATURE, SOURCE> getFeatureReader(final String featureResource, final FeatureCodec<FEATURE, SOURCE> codec, final boolean requireIndex) throws TribbleException {
        return getFeatureReader(featureResource, null, codec, requireIndex, null, null);
    }


    /**
     * {@link #getFeatureReader(String, String, FeatureCodec, boolean, Function, Function)} with {@code null} for wrapper, and indexWrapper
     * @throws TribbleException
     */
    public static <FEATURE extends Feature, SOURCE> AbstractFeatureReader<FEATURE, SOURCE> getFeatureReader(final String featureResource, String indexResource, final FeatureCodec<FEATURE, SOURCE> codec, final boolean requireIndex) throws TribbleException {
        return getFeatureReader(featureResource, indexResource, codec, requireIndex, null, null);
    }

    /**
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
    public static <FEATURE extends Feature, SOURCE> AbstractFeatureReader<FEATURE, SOURCE> getFeatureReader(final String featureResource, String indexResource, final FeatureCodec<FEATURE, SOURCE> codec, final boolean requireIndex, Function<SeekableByteChannel, SeekableByteChannel> wrapper, Function<SeekableByteChannel, SeekableByteChannel> indexWrapper) throws TribbleException {
        try {
            // Test for BGZF formatted file
            if (methods.isTabix(featureResource, SeekableStreamFactory.getInstance().getBufferedStream(SeekableStreamFactory.getInstance().getStreamFor(featureResource, wrapper)), indexResource)) {
                if ( ! (codec instanceof AsciiFeatureCodec) )
                    throw new TribbleException("Tabix indexed files only work with ASCII codecs, but received non-Ascii codec " + codec.getClass().getSimpleName());
                return new TabixFeatureReader<>(featureResource, indexResource, (AsciiFeatureCodec) codec, wrapper, indexWrapper);
            }
            // Not tabix => tribble index file (might be gzipped, but not block gzipped)
            else {
                return new TribbleIndexedFeatureReader<>(featureResource, indexResource, codec, requireIndex, wrapper, indexWrapper);
            }
        } catch (final IOException e) {
            throw new TribbleException.MalformedFeatureFile("Unable to create BasicFeatureReader using feature file ", featureResource, e);
        } catch (final TribbleException e) {
            e.setSource(featureResource);
            throw e;
        }
    }

    /**
     * Return a reader with a supplied index.
     *
     * @param featureResource the path to the source file containing the features
     * @param codec used to decode the features
     * @param index index of featureResource
     * @return a reader for this data
     * @throws TribbleException
     */
    public static <FEATURE extends Feature, SOURCE> AbstractFeatureReader<FEATURE, SOURCE> getFeatureReader(final String featureResource, final FeatureCodec<FEATURE, SOURCE>  codec, final Index index) throws TribbleException {
        try {
            return new TribbleIndexedFeatureReader<>(featureResource, codec, index);
        } catch (final IOException e) {
            throw new TribbleException.MalformedFeatureFile("Unable to create AbstractFeatureReader using feature file ", featureResource, e);
        }

    }

    protected AbstractFeatureReader(final String path, final FeatureCodec<T, SOURCE> codec) {
        this(path, codec, null, null);
    }

    protected AbstractFeatureReader(final String path, final FeatureCodec<T, SOURCE> codec,
                                    final Function<SeekableByteChannel, SeekableByteChannel> wrapper,
                                    final Function<SeekableByteChannel, SeekableByteChannel> indexWrapper) {
        this.path = path;
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
    public boolean isQueryable(){
        return hasIndex();
    }

    public static void setComponentMethods(ComponentMethods methods){
        AbstractFeatureReader.methods = methods;
    }

    /**
     * Whether a filename ends in one of the BLOCK_COMPRESSED_EXTENSIONS
     * @param fileName
     * @return
     */
    public static boolean hasBlockCompressedExtension (final String fileName) {
        String cleanedPath = stripQueryStringIfPathIsAnHttpUrl(fileName);
        for (final String extension : BLOCK_COMPRESSED_EXTENSIONS) {
            if (cleanedPath.toLowerCase().endsWith(extension))
                return true;
        }
        return false;
    }

    /**
     * Remove http query before checking extension
     * Path might be a local file, in which case a '?' is a legal part of the filename.
     * @param path a string representing some sort of path, potentially an http url
     * @return path with no trailing queryString (ex: http://something.com/path.vcf?stuff=something => http://something.com/path.vcf)
     */
    private static String stripQueryStringIfPathIsAnHttpUrl(String path) {
        if(path.startsWith("http://") || path.startsWith("https://")) {
            int qIdx = path.indexOf('?');
            if (qIdx > 0) {
                return path.substring(0, qIdx);
            }
        }
        return path;
    }

    /**
     * Whether the name of a file ends in one of the BLOCK_COMPRESSED_EXTENSIONS
     * @param file
     * @return
     */
    public static boolean hasBlockCompressedExtension (final File file) {
        return hasBlockCompressedExtension(file.getName());
    }

    /**
     * Whether the path of a URI resource ends in one of the BLOCK_COMPRESSED_EXTENSIONS
     * @param uri a URI representing the resource to check
     * @return
     */
    public static boolean hasBlockCompressedExtension (final URI uri) {
        String path = uri.getPath();
        return hasBlockCompressedExtension(path);
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

    static class EmptyIterator<T extends Feature> implements CloseableTribbleIterator<T> {
        @Override public Iterator<T> iterator() { return this; }
        @Override public boolean hasNext() { return false; }
        @Override public T next() { return null; }
        @Override public void remove() { }
        @Override public void close() { }
    }

    public static boolean isTabix(String resourcePath, String indexPath) throws IOException {
        try (SeekableStream inputStream = SeekableStreamFactory.getInstance().getStreamFor(resourcePath)) {
            return isTabix(resourcePath, inputStream,indexPath);
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isTabix(String resourcePath, SeekableStream inputStream, String indexPath) throws IOException {
        if (indexPath == null) {
            indexPath = ParsingUtils.appendToPath(resourcePath, TabixUtils.STANDARD_INDEX_EXTENSION);
        }
        boolean isBGZF = isBGZFFile(inputStream);
        boolean hasIndex = ParsingUtils.resourceExists(indexPath);
        if (hasIndex && !isBGZF && indexPath.endsWith(TabixUtils.STANDARD_INDEX_EXTENSION)) {
            throw new TribbleException(String.format("Detected Tabix index for file %s, but the file does not appear to be compressed in BGZF format", resourcePath));
        }

        return hasBlockCompressedExtension(resourcePath) && hasIndex;
    }

    public static boolean isBGZFFile(SeekableStream inputStream) {
        try {
            return BlockCompressedInputStream.isValidFile(inputStream);
        } catch (IOException e) {
            return false;
        }
    }

    public static class ComponentMethods{

        public boolean isTabix(String resourcePath, String indexPath) throws IOException{
            return AbstractFeatureReader.isTabix(resourcePath, indexPath);
        }

        public boolean isTabix(String resourcePath, SeekableStream inputStream, String indexPath) throws IOException{
            return AbstractFeatureReader.isTabix(resourcePath, inputStream, indexPath);
        }
    }
}
