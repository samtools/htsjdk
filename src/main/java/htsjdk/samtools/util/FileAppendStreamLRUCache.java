/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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
package htsjdk.samtools.util;

import htsjdk.samtools.SAMException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * LRU cache of OutputStreams to handle situation in which it is necessary to have more open output streams
 * than resource limits will allow.  The least-recently-used stream is closed when it is pushed out of
 * the cache.  When adding a new element to the cache, the file is opened in append mode.
 *
 * Actual elements in the cache are usually BufferedOutputStreams wrapping the underlying file streams, but will
 * be the unbuffered streams if Defaults.BUFFER_SIZE = 0.
 *
 * @author alecw@broadinstitute.org
 */
public class FileAppendStreamLRUCache extends ResourceLimitedMap<Path, OutputStream> {
    public FileAppendStreamLRUCache(final int cacheSize) {
        super(cacheSize, new Functor());
    }

    private static class Functor implements ResourceLimitedMapFunctor<Path, OutputStream> {
        @Override
        public OutputStream makeValue(final Path path) {
            try {
                return IOUtil.maybeBufferOutputStream(
                        Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND));
            } catch (final IOException e) {
                // In case the file could not be opened because of too many file handles, try to force
                // file handles to be closed.
                System.gc();
                System.runFinalization();
                try {
                    return IOUtil.maybeBufferOutputStream(
                            Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND));
                } catch (final IOException e2) {
                    throw new SAMException(path + " not found", e2);
                }
            }
        }

        @Override
        public void finalizeValue(final Path path, final OutputStream out) {
            try {
                out.flush();
                out.close();
            } catch (final IOException e) {
                throw new SAMException("Exception closing OutputStream for " + path, e);
            }
        }
    }
}
