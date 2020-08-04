package htsjdk.beta.plugin;

import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public class IOUtils {

    /**
     * Create a temporary file using a given name prefix and name suffix and return a {@link java.nio.file.Path}.
     * @param prefix
     * @param suffix
     * @return temp File that will be deleted on exit
     * @throws IOException
     */
    public static IOPath createTempPath(final String prefix, final String suffix) {
        try {
            final File tempFile = File.createTempFile(prefix, suffix);
            tempFile.deleteOnExit();
            return new HtsPath(tempFile.getAbsolutePath());
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Convert Path to GATKPath, returning null if input was null.
     *
     * @param toConvert Path to convert to GATKPath
     * @return a Path, or null if the input was null.
     */
    public static HtsPath toHtsPath(Path toConvert){
        return null == toConvert ? null : new HtsPath(toConvert.toUri().toString());
    }

    /**
     * Return the entire contents of an IOPath as a string.
     *
     * @param ioPath
     * @return a UTF-8 string representation of the file contents
     */
    public static String getStringFromPath(final IOPath ioPath) {
        try {
            final StringWriter stringWriter = new StringWriter();
            //TODO: the UTF-8 encoding of these should be codified somewhere else...
            Files.lines(ioPath.toPath(), StandardCharsets.UTF_8).forEach(
                    line -> {
                        stringWriter.write(line);
                        stringWriter.append("\n");
                    });
            return stringWriter.toString();
        } catch (final IOException e) {
            throw new RuntimeException(
                    String.format("Failed to load reads bundle json from: %s", ioPath.getRawInputString()),
                    e);
        }
    }

    /**
     * Write a String to an IOPath.
     *
     * @param ioPath path where contents should be written
     * @param contents a UTF-8 string to be written
     * @param gzipOutput if true, gzip output
     */
    public static void writeStringToPath(final IOPath ioPath, final String contents, final boolean gzipOutput) {
         if (gzipOutput) {
             try (final BufferedOutputStream bos = new BufferedOutputStream(ioPath.getOutputStream());
                  final BlockCompressedOutputStream bcos = new BlockCompressedOutputStream(bos, ioPath.toPath())) {
                 bcos.write(contents.getBytes());
             } catch (final IOException e) {
                 throw new RuntimeException(
                         String.format("Failed to load reads bundle json from: %s", ioPath.getRawInputString()), e);
             }
         } else {
             try (final BufferedOutputStream bos = new BufferedOutputStream(ioPath.getOutputStream())) {
                 bos.write(contents.getBytes());
             } catch (final IOException e) {
                 throw new RuntimeException(
                         String.format("Failed to load reads bundle json from: %s", ioPath.getRawInputString()), e);
             }
         }
    }
}
