package htsjdk.tribble.gff;

import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.TribbleException;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;


/**
 * A class to write out gff3/gtf files.  Features are added using {@link #addFeature(Gff3Feature)}, directives using {@link #addDirective(Gff3Codec.Gff3Directive)},
 * and comments using {@link #addComment(String)}.  Note that the version 3 directive is automatically added at creation, so should not be added separately.
 */
public abstract class AbstractGxxWriter implements Closeable {

    protected final OutputStream out;

    protected AbstractGxxWriter(final Path path, final Set<String> fileExtensions) throws IOException {
        if (fileExtensions.stream().noneMatch(e -> path.toString().endsWith(e))) {
            throw new TribbleException("File " + path + " does not have extension consistent with gff3/gtf");
        }

        final OutputStream outputStream = IOUtil.hasGzipFileExtension(path)? new BlockCompressedOutputStream(path.toFile()) : Files.newOutputStream(path);
        out = new BufferedOutputStream(outputStream);
    }

    protected AbstractGxxWriter(final OutputStream stream) {
        out = stream;
    }

    protected final void writeWithNewLine(final String txt) throws IOException {
        out.write(txt.getBytes());
        out.write(AbstractGxxConstants.END_OF_LINE_CHARACTER);
    }

    protected final void tryToWrite(final String string) {
        try {
            out.write(string.getBytes());
        } catch (final IOException ex) {
            throw new TribbleException("Error writing out string " + string, ex);
        }
    }

    protected final void writeFirstEightFields(final Gff3Feature feature) throws IOException {
        writeJoinedByDelimiter(AbstractGxxConstants.FIELD_DELIMITER, this::tryToWrite, Arrays.asList(
                escapeString(feature.getContig()),
                escapeString(feature.getSource()),
                escapeString(feature.getType()),
                Integer.toString(feature.getStart()),
                Integer.toString(feature.getEnd()),
                feature.getScore() < 0 ? AbstractGxxConstants.UNDEFINED_FIELD_VALUE : Double.toString(feature.getScore()),
                feature.getStrand().toString(),
                feature.getPhase() < 0 ? AbstractGxxConstants.UNDEFINED_FIELD_VALUE : Integer.toString(feature.getPhase())
                )
        );
    }

    protected abstract void writeAttributes(final Map<String, List<String>> attributes) throws IOException;

    protected final <T> void writeJoinedByDelimiter(final char delimiter, final Consumer<T> consumer, final Collection<T> fields) throws IOException {
        boolean isNotFirstField = false;
        for (final T field : fields) {
            if (isNotFirstField) {
                out.write(delimiter);
            } else {
                isNotFirstField = true;
            }

            consumer.accept(field);
        }
    }

    /***
     * add a feature
     * @param feature the feature to be added
     * @throws IOException
     */
    public final void addFeature(final Gff3Feature feature) throws IOException {
        writeFirstEightFields(feature);
        out.write(AbstractGxxConstants.FIELD_DELIMITER);
        writeAttributes(feature.getAttributes());
        out.write(AbstractGxxConstants.END_OF_LINE_CHARACTER);
    }

    /***
     * escape a String.
     * Default behavior is to call {@link #encodeString(String)}
     * @param s the string to be escaped
     * @return the escaped string
     */
    protected String escapeString(final String s) {
        return encodeString(s);
    }

    static String encodeString(final String s) {
        try {
            //URLEncoder.encode is hardcoded to change all spaces to +, but we want spaces left unchanged so have to do this
            //+ is escaped to %2B, so no loss of information
            return URLEncoder.encode(s, "UTF-8").replace("+", " ");
        } catch (final UnsupportedEncodingException ex) {
            throw new TribbleException("Encoding failure", ex);
        }
    }

    /**
     * Add comment line
     * @param comment the comment line (not including leading #)
     * @throws IOException
     */
    public final void addComment(final String comment) throws IOException {
        out.write(AbstractGxxConstants.COMMENT_START.getBytes());
        writeWithNewLine(comment);
    }

    @Override
    public final void close() throws IOException {
        out.close();
    }
}