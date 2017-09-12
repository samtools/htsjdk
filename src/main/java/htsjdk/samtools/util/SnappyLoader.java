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
    private final boolean SnappyAvailable;
    private final Log logger = Log.getInstance(this.getClass());

    private static final boolean DefaultVerbosity = Boolean.valueOf(System.getProperty("snappy.loader.verbosity", "false"));
    public static final boolean Disabled = Boolean.valueOf(System.getProperty("snappy.disable", "false"));

    public SnappyLoader() {
        this(DefaultVerbosity);
    }

    /**
     * Constructs a new SnappyLoader which will check to see if snappy is available in the JVM/library path.
     * @param verbose if true output a small number of log messages
     */
    public SnappyLoader(final boolean verbose) {
        if (Disabled) {
            logger.info("Snappy is disabled via system property.");
            SnappyAvailable = false;
        }
        else {
            boolean tmpSnappyAvailable = false;
            try {
                final OutputStream test = new SnappyOutputStream(new ByteArrayOutputStream(1000));
                test.write("Hello World!".getBytes());
                test.close();
                tmpSnappyAvailable = true;
                if (verbose) logger.info("Snappy successfully loaded.");
            }
            /*
             * ExceptionInInitializerError: thrown by Snappy if native libs fail to load.
             * IllegalStateException: thrown within the `test.write` call above if no UTF-8 encoder is found.
             * IOException: potentially thrown by the `test.write` and `test.close` calls.
             * SnappyError: potentially thrown for a variety of reasons by Snappy.
             */
            catch (ExceptionInInitializerError|IllegalStateException|IOException|SnappyError e) {
                if (verbose) logger.warn("Snappy native library failed to load: " + e.getMessage());
            }
            SnappyAvailable = tmpSnappyAvailable;
        }
    }

    /** Returns true if Snappy is available, false otherwise. */
    public boolean isSnappyAvailable() { return SnappyAvailable; }

    /** Wrap an InputStream in a SnappyInputStream. If Snappy is not available will throw an exception. */
    public InputStream wrapInputStream(final InputStream inputStream) {
        if (isSnappyAvailable()) {
            try {
                return new SnappyInputStream(inputStream);
            } catch (Exception e) {
                throw new SAMException("Error instantiating SnappyInputStream", e);
            }
        } else {
            throw new SAMException("Snappy not available");
        }
    }

    /** Wrap an InputStream in a SnappyInputStream. If Snappy is not available will throw an exception. */
    public OutputStream wrapOutputStream(final OutputStream outputStream) {
        if (isSnappyAvailable()) {
            try {
                return new SnappyOutputStream(outputStream, SNAPPY_BLOCK_SIZE);
            } catch (Exception e) {
                throw new SAMException("Error instantiating SnappyOutputStream", e);
            }
        } else {
            throw new SAMException("Snappy not available");
        }
    }
}
