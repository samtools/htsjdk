package htsjdk.tribble.gff;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import htsjdk.samtools.util.FileExtensions;


/**
 * A class to write out gtf files. and comments using {@link #addComment(String)}.
 * @author Pierre Lindenbaum
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
        boolean first = true;
        for (final String key : attributes.keySet()) {
            for (String value : attributes.get(key)) {
                if (!first)
                    out.write(GtfConstants.ATTRIBUTE_DELIMITER);
                out.write(escapeString(key).getBytes());
                out.write(GtfConstants.VALUE_DELIMITER);
                out.write('\"');
                out.write(escapeString(value).getBytes());
                out.write('\"');
                first = false;
            }
        }
    }
}
