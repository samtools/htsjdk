package htsjdk.tribble.gff;

import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.util.ParsingUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

public class Gff3Writer implements Closeable {

    final private PrintStream out;
    final static String version = "3.1.25";

    Gff3Writer(final Path path) throws IOException {
        if (!FileExtensions.GFF3.stream().anyMatch(path::endsWith)) {
            throw new TribbleException("File " + path + " does not have extension consistent with gff3");
        }

        final OutputStream outputStream = IOUtil.hasGzipFileExtension(path)? new GZIPOutputStream(Files.newOutputStream(path)) : Files.newOutputStream(path);
        out = new PrintStream(outputStream);
        //start with version directive
        out.println(Gff3Codec.Gff3Directive.VERSION3_DIRECTIVE.encode(version));
    }

    public void addFeature(final Gff3Feature feature) {
        try {
            final String lineNoAttributes = String.join("\t",
                    URLEncoder.encode(feature.getContig(), "UTF-8"),
                    URLEncoder.encode(feature.getSource(), "UTF-8"),
                    URLEncoder.encode(feature.getType(), "UTF-8"),
                    Integer.toString(feature.getStart()),
                    Integer.toString(feature.getEnd()),
                    feature.getScore() < 0 ? "." : Double.toString(feature.getScore()),
                    feature.getStrand().toString(),
                    feature.getPhase() < 0 ? "." : Integer.toString(feature.getPhase())
            );
            final List<String> attributesStrings = feature.getAttributes().entrySet().stream().map(e -> {
                        try {
                            return String.join("=", new String[]{encodeForNinthColumn(e.getKey()), encodeForNinthColumn(e.getValue())});
                        } catch (final URISyntaxException ex) {
                            throw new TribbleException("Exception writing out gff",ex);
                        }
                    }
            ).collect(Collectors.toList());
            final String attributesString = attributesStrings.isEmpty() ? "." : String.join(";", attributesStrings);

            final String lineString = lineNoAttributes + "\t" + attributesString;
            out.println(lineString);
        } catch(final UnsupportedEncodingException ex) {
            throw new TribbleException("Exception writing out gff",ex);
        }
    }

    private String encodeForNinthColumn(final String decodedString) throws URISyntaxException {
        //in the ninth column of Gff certain characters have special meaning
        final List<String> splitString = ParsingUtils.split(decodedString, ',');
        final List<String> encodedSplitString = new ArrayList<>();
        for (final String string : splitString) {
            final URI uri = new URI(string);
            encodedSplitString.add(uri.toASCIIString());
        }

        return String.join(",", encodedSplitString);
    }

    public void addFlushDirective() {
        out.println(Gff3Codec.Gff3Directive.FLUSH_DIRECTIVE.encode());
    }

    public void addSequenceRegionDirective(final SequenceRegion sequenceRegion) {
        out.println(Gff3Codec.Gff3Directive.SEQUENCE_REGION_DIRECTIVE.encode(sequenceRegion));
    }


    public void addComment(final String comment) {
        out.println("#" + comment);
    }

    @Override
    public void close() {
        out.close();
    }
}