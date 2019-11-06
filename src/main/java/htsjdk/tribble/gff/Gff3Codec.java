package htsjdk.tribble.gff;

import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.LocationAware;

import htsjdk.samtools.util.Log;
import htsjdk.tribble.AbstractFeatureCodec;
import htsjdk.tribble.Feature;
import htsjdk.tribble.FeatureCodecHeader;
import htsjdk.tribble.SimpleFeature;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.annotation.Strand;
import htsjdk.tribble.readers.*;


import java.io.*;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Codec for parsing Gff3 files, as defined in https://github.com/The-Sequence-Ontology/Specifications/blob/master/gff3.md
 * Note that while spec states that all feature types must be defined in sequence ontology, this implementation makes no check on feature types, and allows any string as feature type
 */

public class Gff3Codec extends AbstractFeatureCodec<Gff3Feature, LineIterator> {

    public static final String FIELD_DELIMITER = "\t";
    private static final String GFF3_VERSION_REGEX="##gff-version 3(?:.\\d)*(?:\\.\\d)*$";

    private static final int NUM_FIELDS = 9;

    private static final int CHROMOSOME_NAME_INDEX = 0;
    private static final int ANNOTATION_SOURCE_INDEX = 1;
    private static final int FEATURE_TYPE_INDEX = 2;
    private static final int START_LOCATION_INDEX = 3;
    private static final int END_LOCATION_INDEX = 4;
    private static final int GENOMIC_STRAND_INDEX = 6;
    private static final int GENOMIC_PHASE_INDEX = 7;
    private static final int EXTRA_FIELDS_INDEX = 8;

    /**
     * accepted extensions for gff3 format
     */
    private final Set<String> FILE_EXTENSIONS = new HashSet<String>(Arrays.asList("gff", "gff3"));


    private static final String COMMENT_START = "#";

    private static final String DIRECTIVE_START = "##";

    private static final String PARENT_ATTRIBUTE_KEY = "Parent";
    private static final String IS_CIRCULAR_ATTRIBUTE_KEY = "Is_circular";

    private final LinkedList<Gff3Feature> activeTopLevelFeatures = new LinkedList<>();
    private final LinkedList<Gff3Feature> featuresToFlush = new LinkedList<>();
    /* discontinuous features can have multiple lines representing the same feature, with the same ID in GFF.
    For this implementation the discontinous features are split into separate features.
     */
    private final Map<String, Set<Gff3Feature>> activeFeaturesWithIDs = new HashMap<>();

    private int currentLineNum = 0;

    private final Map<String, SequenceRegion> sequenceRegionMap = new HashMap<>();

    private final static Log logger = Log.getInstance(Gff3Codec.class);

    private boolean reachedFasta = false;

    Gff3Codec() {
        super(Gff3Feature.class);
    }



    @Override
    public Gff3Feature decode(final LineIterator lineIterator) throws IOException {
        if(!lineIterator.hasNext()) {
            prepareToFlushFeatures(activeTopLevelFeatures);
            return featuresToFlush.poll();
        }

        final String line = lineIterator.next();
        currentLineNum++;

        if (reachedFasta) {
            prepareToFlushFeatures(activeTopLevelFeatures);
            return featuresToFlush.poll();
        }


        if (line.startsWith(">")) {
            //backwards compatability with Artemis is built into gff3 spec
            parseDirective(DIRECTIVE_START + "FASTA");
            return null;
        }

        if (line.startsWith(COMMENT_START) && !line.startsWith(DIRECTIVE_START)) {
            return null;
        }

        if (line.startsWith(DIRECTIVE_START)) {
            parseDirective(line);
            return null;
        }

        final String[] splitLine = line.split(FIELD_DELIMITER, -1);

        if (splitLine.length != NUM_FIELDS) {
            throw new TribbleException("Found an invalid number of columns in the given Gff3 file on line "
                    + currentLineNum + " - Given: " + splitLine.length + " Expected: " + NUM_FIELDS + " : " + line);
        }

        try {
            //final String contig = splitLine[CHROMOSOME_NAME_INDEX];
            final String contig = URLDecoder.decode(splitLine[CHROMOSOME_NAME_INDEX], "UTF-8");
            final String source = URLDecoder.decode(splitLine[ANNOTATION_SOURCE_INDEX], "UTF-8");
            final String type = URLDecoder.decode(splitLine[FEATURE_TYPE_INDEX], "UTF-8");
            final int start = Integer.valueOf(splitLine[START_LOCATION_INDEX]);
            final int end = Integer.valueOf(splitLine[END_LOCATION_INDEX]);
            final int phase = splitLine[GENOMIC_PHASE_INDEX].equals(".")? -1 : Integer.valueOf(splitLine[GENOMIC_PHASE_INDEX]);
            final Strand strand = Strand.decode(splitLine[GENOMIC_STRAND_INDEX]);
            final Map<String, String> attributes = parseAttributes(splitLine[EXTRA_FIELDS_INDEX]);
            final Gff3Feature thisFeature = new Gff3Feature(contig, source, type, start, end, strand, phase, attributes);
            final List<String> parentIDs = attributes.get(PARENT_ATTRIBUTE_KEY) != null? Arrays.asList(attributes.get(PARENT_ATTRIBUTE_KEY).split(",")) : new ArrayList<>();

            for (final String parentID : parentIDs) {
                final Set<Gff3Feature> parents = activeFeaturesWithIDs.get(parentID);
                if (parents == null) {
                    throw new TribbleException("Could not find feature with ID " + parentID);
                }

                for (final Gff3Feature parent : parents) {
                    parent.addChild(thisFeature);
                    thisFeature.addParent(parent);
                }
            }
            if (!thisFeature.hasParents()) {
                activeTopLevelFeatures.add(thisFeature);
            }
            final String id = thisFeature.getID();
            if (id != null) {
                if (activeFeaturesWithIDs.containsKey(id)) {
                    for (final Gff3Feature coFeature : activeFeaturesWithIDs.get(id)) {
                        coFeature.addCoFeature(thisFeature);
                        thisFeature.addCoFeature(coFeature);
                    }
                    activeFeaturesWithIDs.get(id).add(thisFeature);
                } else {
                    activeFeaturesWithIDs.put(id, new HashSet<>(Collections.singleton(thisFeature)));
                }
            }

            validateFeature(thisFeature);
            return featuresToFlush.poll();
        } catch (final NumberFormatException ex ) {
            throw new TribbleException("Cannot read integer value for start/end position!");
        }
    }

    protected Map<String,String> parseAttributes(final String attributesString) throws UnsupportedEncodingException {
        final Map<String, String> attributes = new LinkedHashMap<>();
        final String[] splitLine = attributesString.split(";");
        for(String attribute : splitLine) {
            final String[] key_value = attribute.split("=");
            if (key_value.length<2) {
                continue;
            }
            attributes.put(URLDecoder.decode(key_value[0].trim(), "UTF-8"), URLDecoder.decode(key_value[1].trim(), "UTF-8"));
        }
        return attributes;
    }

    private void validateFeature(final Gff3Feature feature) {
        if (sequenceRegionMap.containsKey(feature.getContig())) {
            final SequenceRegion region = sequenceRegionMap.get(feature.getContig());
            if (feature.getStart() == region.getStart() && feature.getEnd() == region.getEnd()) {
                //landmark feature
                final boolean isCircular = Boolean.parseBoolean(feature.getAttribute(IS_CIRCULAR_ATTRIBUTE_KEY));
                region.setCircular(isCircular);
            }
            if (region.isCircular()? !region.overlaps(feature) : !region.contains(feature)) {
                throw new TribbleException("feature at " + feature.getContig() + ":" + feature.getStart() + "-" + feature.getEnd() +
                        " not contained in specified sequence region (" + region.getContig() + ":" + region.getStart() + "-" + region.getEnd());
            }
        }
    }

    @Override
    public Feature decodeLoc(LineIterator lineIterator) {
        final String line = lineIterator.next();

        if (line.startsWith(COMMENT_START)) {
            return null;
        }

        final String[] splitLine = line.split(FIELD_DELIMITER, -1);

        try {
            return new SimpleFeature(splitLine[CHROMOSOME_NAME_INDEX], Integer.valueOf(splitLine[START_LOCATION_INDEX]), Integer.valueOf(splitLine[END_LOCATION_INDEX]));
        } catch (final NumberFormatException ex ) {
            throw new TribbleException("Cannot read integer value for start/end position!");
        }
    }

    @Override
    public boolean canDecode(final String inputFilePath) {
        boolean canDecode;
        try {
            // Simple file and name checks to start with:
            Path p = IOUtil.getPath(inputFilePath);
            canDecode = FILE_EXTENSIONS.stream().anyMatch( fe -> p.toString().endsWith(fe));

            if (canDecode) {

                // Crack open the file and look at the top of it:
                try ( BufferedReader br = new BufferedReader(new InputStreamReader(Files.newInputStream(p))) ) {

                    String line = br.readLine();

                    // First line must be GFF version directive
                    if (!Pattern.matches(GFF3_VERSION_REGEX, line)) {
                        return false;
                    }
                    while (line.startsWith(COMMENT_START)) {
                        line = br.readLine();
                        if ( line == null ) {
                            return false;
                        }
                    }

                    // make sure line conforms to gtf spec
                    final String[] fields = line.split(FIELD_DELIMITER);

                    canDecode &= fields.length == NUM_FIELDS;

                    if (canDecode) {
                        // check that start and end fields are integers
                        try {
                            final int start = Integer.parseInt(fields[3]);
                            final int end = Integer.parseInt(fields[4]);
                        } catch (NumberFormatException | NullPointerException nfe) {
                            return false;
                        }

                        // check for strand

                        canDecode &= fields[GENOMIC_STRAND_INDEX].equals(Strand.POSITIVE.toString()) ||
                                fields[GENOMIC_STRAND_INDEX].equals(Strand.NEGATIVE.toString()) ||
                                fields[GENOMIC_STRAND_INDEX].equals(Strand.NONE.toString()) ||
                                fields[GENOMIC_STRAND_INDEX].equals("?");
                    }
                }

            }
        }
        catch (FileNotFoundException ex) {
            logger.error(inputFilePath + " not found.");
            return false;
        }
        catch (final IOException ex) {
            return false;
        }

        return canDecode;
    }

    @Override
    public FeatureCodecHeader readHeader(LineIterator lineIterator) throws IOException {

        List<String> header = new ArrayList<>();
        while(lineIterator.hasNext()) {
            String line = lineIterator.peek();
            if (line.startsWith(COMMENT_START)) {
                header.add(line);
                lineIterator.next();
            } else {
                break;
            }
        }

        return new FeatureCodecHeader(header, FeatureCodecHeader.NO_HEADER_END);
    }

    private void parseDirective(final String directiveLine) throws IOException {
        final Gff3Directive directive = Gff3Directive.toDirective(directiveLine);
        if (directive != null) {
            switch (directive) {
                case VERSION3_DIRECTIVE:
                    break;

                case SEQUENCE_REGION_DIRECTIVE:
                    final SequenceRegion newRegion = (SequenceRegion) Gff3Directive.SEQUENCE_REGION_DIRECTIVE.decode(directiveLine);
                    if (sequenceRegionMap.containsKey(newRegion.getContig())) {
                        throw new TribbleException("directive for sequence-region " + newRegion.getContig() + " included more than once.");
                    }
                    sequenceRegionMap.put(newRegion.getContig(), newRegion);
                    break;

                case FLUSH_DIRECTIVE:
                    prepareToFlushFeatures(activeTopLevelFeatures);
                    break;

                case FASTA_DIRECTIVE:
                    reachedFasta = true;
                    break;

                default:
                    throw new IllegalArgumentException( "directive " + directive + " not handled by parser.");


            }
        } else {
            logger.warn("ignoring directive " + directiveLine);
        }
    }

    private void prepareToFlushFeatures(final Collection<Gff3Feature> features) {
        featuresToFlush.addAll(features);
        for (final Gff3Feature feature : features) {
            if (feature.getID() == null) {
                continue;
            }
            activeFeaturesWithIDs.get(feature.getID()).remove(feature);
            if (activeFeaturesWithIDs.get(feature.getID()).isEmpty()) {
                activeFeaturesWithIDs.remove(feature.getID());
            }
        }
        activeTopLevelFeatures.removeAll(features);
    }

    @Override
    public LineIterator makeSourceFromStream(final InputStream bufferedInputStream) {
        return new LineIteratorImpl(new SynchronousLineReader(bufferedInputStream));
    }

    @Override
    public LocationAware makeIndexableSourceFromStream(final InputStream bufferedInputStream) {
        return new AsciiLineReaderIterator(AsciiLineReader.from(bufferedInputStream));
    }

    @Override
    public boolean isDone(final LineIterator lineIterator) {
        return !lineIterator.hasNext() && activeTopLevelFeatures.isEmpty() && featuresToFlush.isEmpty();
    }

    @Override
    public void close(final LineIterator lineIterator) {
        CloserUtil.close(lineIterator);
    }

    private enum Gff3Directive {

        VERSION3_DIRECTIVE("##gff-version 3(?:.\\d)*(?:\\.\\d)*$") {
            @Override
            public Object decode(final String line) {
                //nothing to do
                return null;
            }
        },

        SEQUENCE_REGION_DIRECTIVE("##sequence-region .+ \\d+ \\d+$") {
            private int CONTIG_INDEX = 1;
            private int START_INDEX = 2;
            private int END_INDEX = 3;
            @Override
            public Object decode(final String line) throws IOException {
                final String[] splitLine = line.split(" +");
                final String contig = URLDecoder.decode(splitLine[CONTIG_INDEX], "UTF-8");
                final int start = Integer.valueOf(splitLine[START_INDEX]);
                final int end = Integer.valueOf(splitLine[END_INDEX]);
                return new SequenceRegion(contig, start, end);
            }
        },

        FLUSH_DIRECTIVE("###$") {
            @Override
            public Object decode(final String line) {
                return null;
            }
        },

        FASTA_DIRECTIVE("##FASTA$") {
            @Override
            public Object decode(final String line) { return null;}
        };


        private final String regexPattern;

        Gff3Directive(String regexPattern) {
            this.regexPattern = regexPattern;
        }

        public static Gff3Directive toDirective(final String line) throws IOException {
            for (final Gff3Directive directive : Gff3Directive.values()) {
                if(Pattern.matches(directive.regexPattern, line)) {
                    return directive;
                }
            }
            return null;
        }

        public abstract Object decode(final String line) throws IOException;
    }

}
