package htsjdk.tribble.gff;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import htsjdk.samtools.util.FileExtensions;
import htsjdk.tribble.TribbleException;


/**
 * A class to write out gff3 files.  Features are added using {@link #addFeature(Gff3Feature)}, directives using {@link #addDirective(Gff3Codec.Gff3Directive)},
 * and comments using {@link #addComment(String)}.  Note that the version 3 directive is automatically added at creation, so should not be added separately.
 */
public class Gff3Writer extends AbstractGxxWriter {

    private final static String version = "3.1.25";

    public Gff3Writer(final Path path) throws IOException {
        super(path, FileExtensions.GFF3);
        initialize();
        }

    public Gff3Writer(final OutputStream stream) {
        super(stream);
        initialize();
    }

    private void initialize() {
        try {
            writeWithNewLine(Gff3Codec.Gff3Directive.VERSION3_DIRECTIVE.encode(version));
        } catch (final IOException ex) {
            throw new TribbleException("Error writing version directive", ex);
        }
    }


    @Override
    protected void writeAttributes(final Map<String, List<String>> attributes) throws IOException {
        if (attributes.isEmpty()) {
            out.write(Gff3Constants.UNDEFINED_FIELD_VALUE.getBytes());
        }

        writeJoinedByDelimiter(Gff3Constants.ATTRIBUTE_DELIMITER, e ->  writeKeyValuePair(e.getKey(), e.getValue()), attributes.entrySet());
    }

    void writeKeyValuePair(final String key, final List<String> values) {
        try {
            tryToWrite(key);
            out.write(Gff3Constants.KEY_VALUE_SEPARATOR);
            writeJoinedByDelimiter(Gff3Constants.VALUE_DELIMITER, v -> tryToWrite(escapeString(v)), values);
        } catch (final IOException ex) {
            throw new TribbleException("error writing out key value pair " + key + " " + values);
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
}