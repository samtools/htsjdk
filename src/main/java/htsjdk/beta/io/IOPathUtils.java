package htsjdk.beta.io;

import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class IOPathUtils {

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
     * Convert a Path to an IOPath, returning null if input was null.
     *
     * @param toConvert Path to convert to IOPath
     * @return a Path, or null if the input was null.
     */
    public static IOPath toIOPath(Path toConvert){
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
                    String.format("Failed to read from: %s", ioPath.getRawInputString()),
                    e);
        }
    }

    /**
     * Write a String to an IOPath.
     *
     * @param ioPath path where contents should be written
     * @param contents a UTF-8 string to be written
     */
    public static void writeStringToPath(final IOPath ioPath, final String contents) {
        try (final BufferedOutputStream bos = new BufferedOutputStream(ioPath.getOutputStream())) {
            bos.write(contents.getBytes());
        } catch (final IOException e) {
            throw new RuntimeException(
                    String.format("Failed to write to: %s", ioPath.getRawInputString()),
                    e);
        }
    }
}
