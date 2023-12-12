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
package htsjdk.samtools.seekablestream;

import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.tribble.TribbleException;

import java.io.IOException;
import java.net.URL;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Function;

/**
 * Singleton class for getting {@link SeekableStream}s from URL/paths
 * Applications using this library can set their own factory
 * @author jrobinso
 * @date Nov 30, 2009
 */
public class SeekableStreamFactory{

    private static final ISeekableStreamFactory DEFAULT_FACTORY;
    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final String FTP = "ftp";
    /**
     * the set of url schemes that have special support in htsjdk that isn't through a FileSystemProvider
     */
    private static final Set<String> URL_SCHEMES_WITH_LEGACY_SUPPORT = Set.of(HTTP, FTP, HTTPS);
    public static final String FILE_SCHEME = "file";
    private static ISeekableStreamFactory currentFactory;

    static{
        DEFAULT_FACTORY = new DefaultSeekableStreamFactory();
        currentFactory = DEFAULT_FACTORY;
    }

    private SeekableStreamFactory(){}

    public static void setInstance(final ISeekableStreamFactory factory){
        currentFactory = factory;
    }

    public static ISeekableStreamFactory getInstance(){
        return currentFactory;
    }

    /**
     * Does this path point to a regular file on disk and not something like a URL?
     * @param path the path to test
     * @return true if the path is to a file on disk
     * @deprecated this method is simplistic and no longer particularly useful since IOPath allows similar access to
     * various non-file data sources, internal use has been replaced with {@link #isBeingHandledByLegacyUrlSupport(String)}
     */
    @Deprecated
    public static boolean isFilePath(final String path) {
        return !canBeHandledByLegacyUrlSupport(path);
    }

    /**
     * is this path being handled by one of the legacy SeekableStream types (http(s) / ftp)
     *
     * @param path a path to check
     * @return if the path is not being handled by a FileSystemProvider and it can be read by legacy streams
     */
    public static boolean isBeingHandledByLegacyUrlSupport(final String path){
        return !new HtsPath(path).hasFileSystemProvider()  //if we have a provider for it that's what we'll use
                && canBeHandledByLegacyUrlSupport(path); // otherwise we fall back to the special handlers
    }

    //is this one of the url types that has legacy htsjdk support built in?
    public static boolean canBeHandledByLegacyUrlSupport(final String path) {
        return URL_SCHEMES_WITH_LEGACY_SUPPORT.stream().anyMatch(scheme-> path.startsWith(scheme +"://"));
    }

    private static class DefaultSeekableStreamFactory implements ISeekableStreamFactory {

        @Override
        public SeekableStream getStreamFor(final URL url) throws IOException {
            return getStreamFor(url.toExternalForm());
        }

        @Override
        public SeekableStream getStreamFor(final String path) throws IOException {
            return getStreamFor(path, null);
        }

        /**
         * The wrapper will only be applied to the stream if the stream is treated as a {@link Path}
         *
         * This currently means any uri with a scheme that is not http, https, ftp, or file will have the wrapper applied to it
         *
         * @param path    a uri like String representing a resource to open
         * @param wrapper a wrapper to apply to the stream allowing direct transformations on the byte stream to be applied
         */
        @Override
        public SeekableStream getStreamFor(final String path,
                                           Function<SeekableByteChannel, SeekableByteChannel> wrapper) throws IOException {
            return getStreamFor(new HtsPath(path), wrapper);
        }


        /**
         * The wrapper will only be applied to the stream if the stream is treated as a non file:// {@link Path}
         *
         * This has a fall back to htsjdk's built in http and ftp providers if no FileSystemProvder is available for them
         *
         * @param path    an IOPath to be opened
         * @param wrapper a wrapper to apply to the stream allowing direct transformations on the byte stream to be applied
         * @throws IOException
         */
        public static SeekableStream getStreamFor(final IOPath path, Function<SeekableByteChannel, SeekableByteChannel> wrapper) throws IOException {
            if(path.hasFileSystemProvider()) {
                return path.getScheme().equals(FILE_SCHEME)
                        ? new SeekableFileStream(path.toPath().toFile()) //don't apply the wrapper to local files
                        : new SeekablePathStream(path.toPath(), wrapper);
            } else {
               return switch(path.getScheme()){
                   case HTTP, HTTPS -> new SeekableHTTPStream(new URL(path.getRawInputString()));
                   case FTP -> new SeekableFTPStream((new URL(path.getRawInputString())));
                   default -> throw new TribbleException("Unknown path type. No FileSystemProvider available for " + path.getRawInputString());
               };
            }
        }

        @Override
        public SeekableStream getBufferedStream(SeekableStream stream){
            return getBufferedStream(stream, SeekableBufferedStream.DEFAULT_BUFFER_SIZE);
        }

        @Override
        public SeekableStream getBufferedStream(SeekableStream stream, int bufferSize){
            if (bufferSize == 0) return stream;
            else return new SeekableBufferedStream(stream, bufferSize);
        }

    }

}
