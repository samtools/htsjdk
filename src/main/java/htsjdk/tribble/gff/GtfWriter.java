package htsjdk.tribble.gff;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import htsjdk.samtools.util.FileExtensions;
import htsjdk.tribble.TribbleException;


/**
 * A class to write out gtf files.
 * and comments using {@link #addComment(String)}.
 */
public class GtfWriter extends AbstractGxxWriter {


    public GtfWriter(final Path path) throws IOException {
        super(path, FileExtensions.GTF);
        }

    public GtfWriter(final OutputStream stream) {
        super(stream);
    }

    
    @Override
    protected void writeAttributes(final Map<String, List<String>> attributes) throws IOException {
        if (attributes.isEmpty()) {
            out.write(GtfConstants.UNDEFINED_FIELD_VALUE.getBytes());
        }

        writeJoinedByDelimiter(GtfConstants.ATTRIBUTE_DELIMITER, e ->  writeKeyValuePair(e.getKey(), e.getValue()), attributes.entrySet());
    }

   private void writeKeyValuePair(final String key, final List<String> values) {
        try {
            tryToWrite(key);
            out.write(GtfConstants.VALUE_DELIMITER);
            writeJoinedByDelimiter(Gff3Constants.VALUE_DELIMITER, v -> tryToWrite(escapeString(v)), values);
        } catch (final IOException ex) {
            throw new TribbleException("error writing out key value pair " + key + " " + values);
        }
    }
    
}