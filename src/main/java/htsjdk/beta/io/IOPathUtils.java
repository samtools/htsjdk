package htsjdk.beta.io;

import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.utils.ValidationUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;
import java.util.function.Function;

public class IOPathUtils {

    /**
     * Create a temporary file using a given name prefix and name suffix and return a {@link java.nio.file.Path}.
     * @param prefix file name prefix to use
     * @param suffix file name suffix to use
     * @return IOPath for a temporary file that will be deleted on exit
     * @throws IOException in IO failures
     */
    public static IOPath createTempPath(final String prefix, final String suffix) {
        try {
            final File tempFile = File.createTempFile(prefix, suffix);
            tempFile.deleteOnExit();
            return new HtsPath(tempFile.getAbsolutePath());
        } catch (final IOException e) {
            throw new HtsjdkIOException(e);
        }
    }

    /**
     * Get the entire contents of an IOPath file as a string.
     *
     * @param ioPath ioPath to consume
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
            throw new HtsjdkIOException(
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
            throw new HtsjdkIOException(
                    String.format("Failed to write to: %s", ioPath.getRawInputString()),
                    e);
        }
    }

    /**
     * Takes an IOPath and returns a new IOPath object that keeps the same basename as the original but has
     * a new extension. If append is set to false, only the last component of an extension will be replaced.
     * e.g. "my.fasta.gz" -> "my.fasta.tmp"
     *
     * If the input IOPath was created from a rawInputString that specifies a relative local path, the new path will
     * have a rawInputString that specifies an absolute path.
     *
     * Examples:
     *     - (test_na12878.bam, .bai) -> test_na12878.bai (append = false)
     *     - (test_na12878.bam, .md5) -> test_na12878.bam.md5 (append = true)
     *
     * @param path The original path
     * @param newExtension A new file extension. Must include the leading dot e.g. ".txt", ".bam"
     * @param append If set to true, append the new extension to the original basename. If false, replace the original extension
     *               with the new extension. If append = false and the original name has no extension, an exception will be thrown.
     * @param ioPathConstructor a function that takes a string and returns an IOPath-derived class of type <T>
     * @return A new IOPath object with the new extension
     */
    public static <T extends IOPath> T replaceExtension(
            final IOPath path,
            final String newExtension,
            final boolean append,
            final Function<String, T> ioPathConstructor){
        ValidationUtils.validateArg(newExtension.startsWith("."), "newExtension must start with a dot '.'");

        final String oldFileName = path.toPath().getFileName().toString();

        String newFileName;
        if (append){
            newFileName = oldFileName + newExtension;
        } else {
            final Optional<String> oldExtension = path.getExtension();
            if (oldExtension.isEmpty()){
                throw new RuntimeException("The original path must have an extension when append = false: " + path.getURIString());
            }
            newFileName = oldFileName.replaceAll(oldExtension.get() + "$", newExtension);
        }
        return ioPathConstructor.apply(path.toPath().resolveSibling(newFileName).toUri().toString());
    }
}
