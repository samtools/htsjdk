package htsjdk.samtools.util;

import htsjdk.annotations.InternalAPI;
import org.xerial.snappy.SnappyError;
import org.xerial.snappy.SnappyInputStream;
import org.xerial.snappy.SnappyOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This class is the only one which should actually import Snappy Classes.  It is separated from SnappyLoader to allow
 * snappy to be an optional dependency. Referencing snappy classes directly if the library is unavailable causes a
 * NoClassDefFoundError, so use this instead.
 *
 * This should only be referenced by {@link SnappyLoader} in order to prevent accidental imports of Snappy classes.
 *
 */
@InternalAPI
class SnappyLoaderInternal {
    private static final Log logger = Log.getInstance(SnappyLoaderInternal.class);
    private static final int SNAPPY_BLOCK_SIZE = 32768;  // keep this as small as can be without hurting compression ratio.

    /**
     * Try to load Snappy's native library.
     *
     * Note that calling this when snappy is not available will throw NoClassDefFoundError!
     *
     * @return true iff Snappy's native libraries are loaded and functioning.
     */
    static boolean tryToLoadSnappy() {
        final boolean snappyAvailable;
        boolean tmpSnappyAvailable = false;
        try (final OutputStream test = new SnappyOutputStream(new ByteArrayOutputStream(1000))){
            test.write("Hello World!".getBytes());
            tmpSnappyAvailable = true;
            logger.debug("Snappy successfully loaded.");
        }
        /*
         * ExceptionInInitializerError: thrown by Snappy if native libs fail to load.
         * IllegalStateException: thrown within the `test.write` call above if no UTF-8 encoder is found.
         * IOException: potentially thrown by the `test.write` and `test.close` calls.
         * SnappyError: potentially thrown for a variety of reasons by Snappy.
         */
        catch (final ExceptionInInitializerError | IllegalStateException | IOException | SnappyError e) {
            logger.warn(e, "Snappy native library failed to load.");
        }
        snappyAvailable = tmpSnappyAvailable;
        return snappyAvailable;
    }


    /**
     * @return a function which wraps an InputStream in a new SnappyInputStream
     */
    static SnappyLoader.IOFunction<InputStream, InputStream> getInputStreamWrapper(){
        return SnappyInputStream::new;
    }

    /**
     * @return a function which wraps an OutputStream in a new SnappyOutputStream with an appropriate block size
     */
    static SnappyLoader.IOFunction<OutputStream, OutputStream> getOutputStreamWrapper(){
        return (stream) -> new SnappyOutputStream(stream, SNAPPY_BLOCK_SIZE);
    }

}
