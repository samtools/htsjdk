package htsjdk.tribble.gff;

import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.TribbleException;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;


public class Gff3Writer implements Closeable {

    private final PrintStream out;
    private final static String version = "3.1.25";

    public Gff3Writer(final Path path) throws IOException {
        if (!FileExtensions.GFF3.stream().anyMatch(e -> path.toString().endsWith(e))) {
            throw new TribbleException("File " + path + " does not have extension consistent with gff3");
        }

        final OutputStream outputStream = IOUtil.hasGzipFileExtension(path)? new GZIPOutputStream(Files.newOutputStream(path)) : Files.newOutputStream(path);
        out = new PrintStream(outputStream);
        //start with version directive
        initialize();
    }

    public Gff3Writer(final PrintStream stream) {
        out = stream;
        initialize();
    }

    private void initialize() {
        out.println(Gff3Codec.Gff3Directive.VERSION3_DIRECTIVE.encode(version));
    }

    public void addFeature(final Gff3Feature feature) {
        final String lineNoAttributes = String.join("\t",
                encodeString(feature.getContig()),
                encodeString(feature.getSource()),
                encodeString(feature.getType()),
                Integer.toString(feature.getStart()),
                Integer.toString(feature.getEnd()),
                feature.getScore() < 0 ? "." : Double.toString(feature.getScore()),
                feature.getStrand().toString(),
                feature.getPhase() < 0 ? "." : Integer.toString(feature.getPhase())
        );
        final List<String> attributesStrings = new ArrayList<>();

        for (final Map.Entry<String, List<String>> entry : feature.getAttributes().entrySet()) {
            final String attributeString = String.join("=", new String[]{encodeString(entry.getKey()), encodeForNinthColumn(entry.getValue())});
            attributesStrings.add(attributeString);
        }
        final String attributesString = attributesStrings.isEmpty() ? "." : String.join(";", attributesStrings);

        final String lineString = lineNoAttributes + "\t" + attributesString;
        out.println(lineString);
    }


    static String encodeForNinthColumn(final List<String> values) {
        final List<String> encodedValues = values.stream().map(Gff3Writer::encodeString).collect(Collectors.toList());

        return String.join(",", encodedValues);
    }


    static String encodeString(final String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", " ");
        } catch (final UnsupportedEncodingException ex) {
            throw new TribbleException("Encoding failure", ex);
        }
    }

    public void addDirective(final Gff3Codec.Gff3Directive directive, final Object object) {
        out.println(directive.encode(object));
    }

    public void addDirective(final Gff3Codec.Gff3Directive directive) {
        addDirective(directive, null);
    }


    public void addComment(final String comment) {
        out.println("#" + comment);
    }

    @Override
    public void close() {
        out.close();
    }
}