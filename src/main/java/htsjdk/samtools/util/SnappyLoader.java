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
import org.xerial.snappy.SnappyError;
import org.xerial.snappy.SnappyInputStream;
import org.xerial.snappy.SnappyOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Checks if Snappy is available, and provides methods for wrapping InputStreams and OutputStreams with Snappy if so.
 */
public class SnappyLoader {
    private static final int SNAPPY_BLOCK_SIZE = 32768;  // keep this as small as can be without hurting compression ratio.
    private static final Log logger = Log.getInstance(SnappyLoader.class);

    private final boolean snappyAvailable;

    public SnappyLoader() {
        this(Defaults.SNAPPY_EXTRA_VERBOSE);
    }

    /**
     * Constructs a new SnappyLoader which will check to see if snappy is available in the JVM/library path.
     * @param verbose if true output a small number of log messages
     */
    public SnappyLoader(final boolean verbose) {
        if (Defaults.DISABLE_SNAPPY_COMPRESSOR) {
            if(verbose) logger.info("Snappy is disabled via system property.");
            snappyAvailable = false;
        }
        else {
            boolean tmpSnappyAvailable = false;
            try (final OutputStream test = new SnappyOutputStream(new ByteArrayOutputStream(1000))){
                test.write("Hello World!".getBytes());
                tmpSnappyAvailable = true;
                if (verbose) logger.info("Snappy successfully loaded.");
            }
            /*
             * ExceptionInInitializerError: thrown by Snappy if native libs fail to load.
             * IllegalStateException: thrown within the `test.write` call above if no UTF-8 encoder is found.
             * IOException: potentially thrown by the `test.write` and `test.close` calls.
             * SnappyError: potentially thrown for a variety of reasons by Snappy.
             */
            catch (final ExceptionInInitializerError | IllegalStateException | IOException | SnappyError e) {
                if (verbose) logger.warn("Snappy native library failed to load: " + e.getMessage());
            }
            snappyAvailable = tmpSnappyAvailable;
        }
    }

    /** Returns true if Snappy is available, false otherwise. */
    public boolean isSnappyAvailable() { return snappyAvailable; }

    /**
     * Wrap an InputStream in a SnappyInputStream.
     * @throws SAMException if Snappy is not available will throw an exception.
     */
    public InputStream wrapInputStream(final InputStream inputStream) {
        return wrapWithSnappyOrThrow(inputStream, SnappyInputStream::new);
    }

    /**
     * Wrap an OutputStream in a SnappyOutputStream.
     * @throws SAMException if Snappy is not available
     */
    public OutputStream wrapOutputStream(final OutputStream outputStream) {
        return wrapWithSnappyOrThrow(outputStream, (stream) -> new SnappyOutputStream(stream, SNAPPY_BLOCK_SIZE));
    }

    private interface IOFunction<T,R> {
        R apply(T input) throws IOException;
    }

    private <T,R> R wrapWithSnappyOrThrow(T stream, IOFunction<T, R> wrapper){
        if (isSnappyAvailable()) {
            try {
                return wrapper.apply(stream);
            } catch (Exception e) {
                throw new SAMException("Error instantiating SnappyInputStream", e);
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
