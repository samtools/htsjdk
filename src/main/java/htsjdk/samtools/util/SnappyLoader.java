/*
 * The MIT License
 *
 * Copyright (c) 2011 The Broad Institute
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

import htsjdk.samtools.Defaults;
import htsjdk.samtools.SAMException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Checks if Snappy is available, and provides methods for wrapping InputStreams and OutputStreams with Snappy if it is.
 *
 * @implNote this class must not import Snappy code in order to prevent exceptions if the Snappy Library is not available.
 * Snappy code is handled by {@link SnappyLoaderInternal}.
 */
public class SnappyLoader {
    private static final Log logger = Log.getInstance(SnappyLoader.class);

    private final boolean snappyAvailable;

    public SnappyLoader() {
        this(Defaults.DISABLE_SNAPPY_COMPRESSOR);
    }

    SnappyLoader(boolean disableSnappy) {
        if (disableSnappy) {
            logger.debug("Snappy is disabled via system property.");
            snappyAvailable = false;
        } else {
            boolean tmpAvailable;
            try {
                //This triggers trying to import Snappy code, which causes an exception if the library is missing.
                tmpAvailable = SnappyLoaderInternal.tryToLoadSnappy();
            } catch (NoClassDefFoundError e){
                tmpAvailable = false;
                logger.error(e, "Snappy java library was requested but not found. If Snappy is " +
                        "intentionally missing, this message may be suppressed by setting " +
                        "-D"+ Defaults.SAMJDK_PREFIX + Defaults.DISABLE_SNAPPY_PROPERTY_NAME + "=true " );
            }
            snappyAvailable = tmpAvailable;
        }
    }

    /** Returns true if Snappy is available, false otherwise. */
    public boolean isSnappyAvailable() { return snappyAvailable; }

    /**
     * Wrap an InputStream in a SnappyInputStream.
     * @throws SAMException if Snappy is not available will throw an exception.
     */
    public InputStream wrapInputStream(final InputStream inputStream) {
        return wrapWithSnappyOrThrow(inputStream, SnappyLoaderInternal.getInputStreamWrapper());
    }

    /**
     * Wrap an OutputStream in a SnappyOutputStream.
     * @throws SAMException if Snappy is not available
     */
    public OutputStream wrapOutputStream(final OutputStream outputStream) {
        return wrapWithSnappyOrThrow(outputStream, SnappyLoaderInternal.getOutputStreamWrapper());
    }

    /**
     * Function which can throw IOExceptions
     */
    interface IOFunction<T,R> {
        R apply(T input) throws IOException;
    }

    private <T,R> R wrapWithSnappyOrThrow(T stream, IOFunction<T, R> wrapper){
        if (isSnappyAvailable()) {
            try {
                return wrapper.apply(stream);
            } catch (Exception e) {
                throw new SAMException("Error wrapping stream with snappy", e);
            }
        } else {
            final String errorMessage = Defaults.DISABLE_SNAPPY_COMPRESSOR
                    ? "Cannot wrap stream with snappy compressor because snappy was disabled via the "
                    + Defaults.DISABLE_SNAPPY_PROPERTY_NAME + " system property."
                    : "Cannot wrap stream with snappy compressor because we could not load the snappy library.";
            throw new SAMException(errorMessage);
        }
    }

}
