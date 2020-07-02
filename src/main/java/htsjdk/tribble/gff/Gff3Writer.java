package htsjdk.tribble.gff;

import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.FileExtensions;
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
import java.util.function.Consumer;


/**
 * A class to write out gff3 files.  Features are added using {@link #addFeature(Gff3Feature)}, directives using {@link #addDirective(Gff3Codec.Gff3Directive)},
 * and comments using {@link #addComment(String)}.  Note that the version 3 directive is automatically added at creation, so should not be added separately.
 */
public class Gff3Writer implements Closeable {

    private final OutputStream out;
    private final static String version = "3.1.25";

    public Gff3Writer(final Path path) throws IOException {
        if (FileExtensions.GFF3.stream().noneMatch(e -> path.toString().endsWith(e))) {
            throw new TribbleException("File " + path + " does not have extension consistent with gff3");
        }

        final OutputStream outputStream = IOUtil.hasGzipFileExtension(path)? new BlockCompressedOutputStream(path.toFile()) : Files.newOutputStream(path);
        out = new BufferedOutputStream(outputStream);
        //start with version directive
        initialize();
    }

    public Gff3Writer(final OutputStream stream) {
        out = stream;
        initialize();
    }

    private void initialize() {
        try {
            writeWithNewLine(Gff3Codec.Gff3Directive.VERSION3_DIRECTIVE.encode(version));
        } catch (final IOException ex) {
            throw new TribbleException("Error writing version directive", ex);
        }
    }

    private void writeWithNewLine(final String txt) throws IOException {
        out.write(txt.getBytes());
        out.write(Gff3Constants.END_OF_LINE_CHARACTER);
    }

    private void tryToWrite(final String string) {
        try {
            out.write(string.getBytes());
        } catch (final IOException ex) {
            throw new TribbleException("Error writing out string " + string, ex);
        }
    }

    private void writeFirstEightFields(final Gff3Feature feature) throws IOException {
        writeJoinedByDelimiter(Gff3Constants.FIELD_DELIMITER, this::tryToWrite, Arrays.asList(
                encodeString(feature.getContig()),
                encodeString(feature.getSource()),
                encodeString(feature.getType()),
                Integer.toString(feature.getStart()),
                Integer.toString(feature.getEnd()),
                feature.getScore() < 0 ? Gff3Constants.UNDEFINED_FIELD_VALUE : Double.toString(feature.getScore()),
                feature.getStrand().toString(),
                feature.getPhase() < 0 ? Gff3Constants.UNDEFINED_FIELD_VALUE : Integer.toString(feature.getPhase())
                )
        );
    }

    void writeAttributes(final Map<String, List<String>> attributes) throws IOException {
        if (attributes.isEmpty()) {
            out.write(Gff3Constants.UNDEFINED_FIELD_VALUE.getBytes());
        }

        writeJoinedByDelimiter(Gff3Constants.ATTRIBUTE_DELIMITER, e ->  writeKeyValuePair(e.getKey(), e.getValue()), attributes.entrySet());
    }

    void writeKeyValuePair(final String key, final List<String> values) {
        try {
            tryToWrite(key);
            out.write(Gff3Constants.KEY_VALUE_SEPARATOR);
            writeJoinedByDelimiter(Gff3Constants.VALUE_DELIMITER, v -> tryToWrite(encodeString(v)), values);
        } catch (final IOException ex) {
            throw new TribbleException("error writing out key value pair " + key + " " + values);
        }
    }

    private <T> void writeJoinedByDelimiter(final char delimiter, final Consumer<T> consumer, final Collection<T> fields) throws IOException {
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
    public void addFeature(final Gff3Feature feature) throws IOException {
        writeFirstEightFields(feature);
        out.write(Gff3Constants.FIELD_DELIMITER);
        writeAttributes(feature.getAttributes());
        out.write(Gff3Constants.END_OF_LINE_CHARACTER);
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
     * Add a directive with an object
     * @param directive the directive to be added
     * @param object the object to be encoded with the directive
     * @throws IOException
     */
    public void addDirective(final Gff3Codec.Gff3Directive directive, final Object object) throws IOException {
        if (directive == Gff3Codec.Gff3Directive.VERSION3_DIRECTIVE) {
            throw new TribbleException("VERSION3_DIRECTIVE is automatically added and should not be added manually.");
        }
        writeWithNewLine(directive.encode(object));
    }

    /**
     * Add a directive
     * @param directive the directive to be added
     * @throws IOException
     */
    public void addDirective(final Gff3Codec.Gff3Directive directive) throws IOException {
        if (directive == Gff3Codec.Gff3Directive.VERSION3_DIRECTIVE) {
            throw new TribbleException("VERSION3_DIRECTIVE is automatically added and should not be added manually.");
        }
        addDirective(directive, null);
    }

    /**
     * Add comment line
     * @param comment the comment line (not including leading #)
     * @throws IOException
     */
    public void addComment(final String comment) throws IOException {
        out.write(Gff3Constants.COMMENT_START.getBytes());
        writeWithNewLine(comment);
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}