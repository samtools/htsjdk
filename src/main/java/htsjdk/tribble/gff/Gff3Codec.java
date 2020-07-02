package htsjdk.tribble.gff;

import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.LocationAware;

import htsjdk.samtools.util.Log;
import htsjdk.tribble.AbstractFeatureCodec;
import htsjdk.tribble.Feature;
import htsjdk.tribble.FeatureCodecHeader;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.annotation.Strand;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.readers.*;
import htsjdk.tribble.util.ParsingUtils;



import java.io.*;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Codec for parsing Gff3 files, as defined in https://github.com/The-Sequence-Ontology/Specifications/blob/31f62ad469b31769b43af42e0903448db1826925/gff3.md
 * Note that while spec states that all feature types must be defined in sequence ontology, this implementation makes no check on feature types, and allows any string as feature type
 *
 * Each feature line in the Gff3 file will be emitted as a separate feature.  Features linked together through the "Parent" attribute will be linked through {@link Gff3Feature#getParents()}, {@link Gff3Feature#getChildren()},
 * {@link Gff3Feature#getAncestors()}, {@link Gff3Feature#getDescendents()}, amd {@link Gff3Feature#flatten()}.  This linking is not guaranteed to be comprehensive when the file is read for only features overlapping a particular
 * region, using a tribble index.  In this case, a particular feature will only be linked to the subgroup of features it is linked to in the input file which overlap the given region.
 */

public class Gff3Codec extends AbstractFeatureCodec<Gff3Feature, LineIterator> {



    private static final int NUM_FIELDS = 9;

    private static final int CHROMOSOME_NAME_INDEX = 0;
    private static final int ANNOTATION_SOURCE_INDEX = 1;
    private static final int FEATURE_TYPE_INDEX = 2;
    private static final int START_LOCATION_INDEX = 3;
    private static final int END_LOCATION_INDEX = 4;
    private static final int SCORE_INDEX = 5;
    private static final int GENOMIC_STRAND_INDEX = 6;
    private static final int GENOMIC_PHASE_INDEX = 7;
    private static final int EXTRA_FIELDS_INDEX = 8;


    private static final String IS_CIRCULAR_ATTRIBUTE_KEY = "Is_circular";

    private static final String ARTEMIS_FASTA_MARKER = ">";

    private final Queue<Gff3FeatureImpl> activeFeatures = new ArrayDeque<>();
    private final Queue<Gff3FeatureImpl> featuresToFlush = new ArrayDeque<>();
    private final Map<String, Set<Gff3FeatureImpl>> activeFeaturesWithIDs = new HashMap<>();
    private final Map<String, Set<Gff3FeatureImpl>> activeParentIDs = new HashMap<>();

    private final Map<String, SequenceRegion> sequenceRegionMap = new LinkedHashMap<>();
    private final Map<Integer, String> commentsWithLineNumbers = new LinkedHashMap<>();

    private final static Log logger = Log.getInstance(Gff3Codec.class);

    private boolean reachedFasta = false;

    private DecodeDepth decodeDepth;

    private int currentLine = 0;

    public Gff3Codec() {
        this(DecodeDepth.DEEP);
    }

    public Gff3Codec(final DecodeDepth decodeDepth) {
        super(Gff3Feature.class);
        this.decodeDepth = decodeDepth;
    }

    public enum DecodeDepth {
        DEEP ,
        SHALLOW
    }

    @Override
    public Gff3Feature decode(final LineIterator lineIterator) throws IOException {
        return decode(lineIterator, decodeDepth);
    }

    private Gff3Feature decode(final LineIterator lineIterator, final DecodeDepth depth) throws IOException {
        currentLine++;
        /*
        Basic strategy: Load features into deque, create maps from a features ID to it, and from a features parents' IDs to it.  For each feature, link to parents using these maps.
        When reaching flush directive, fasta, or end of file, prepare to flush features by moving all active features to deque of features to flush, and clearing
        list of active features and both maps.  Always poll featuresToFlush to return any completed top level features.
         */
        if (!lineIterator.hasNext()) {
            //no more lines, flush whatever is active
            prepareToFlushFeatures();
            return featuresToFlush.poll();
        }

        final String line = lineIterator.next();

        if (reachedFasta) {
            //previously reached fasta, flush whatever is active
            prepareToFlushFeatures();
            return featuresToFlush.poll();
        }

        if (line.startsWith(ARTEMIS_FASTA_MARKER)) {
            //backwards compatability with Artemis is built into gff3 spec
            processDirective(Gff3Directive.FASTA_DIRECTIVE, null);
            return featuresToFlush.poll();
        }

        if (line.startsWith(Gff3Constants.COMMENT_START) && !line.startsWith(Gff3Constants.DIRECTIVE_START)) {
            commentsWithLineNumbers.put(currentLine, line.substring(Gff3Constants.COMMENT_START.length()));
            return featuresToFlush.poll();
        }

        if (line.startsWith(Gff3Constants.DIRECTIVE_START)) {
            parseDirective(line);
            return featuresToFlush.poll();
        }



        final Gff3FeatureImpl thisFeature = new Gff3FeatureImpl(parseLine(line, currentLine));
        activeFeatures.add(thisFeature);
        if (depth == DecodeDepth.DEEP) {
            //link to parents/children/co-features
            final List<String> parentIDs = thisFeature.getAttribute(Gff3Constants.PARENT_ATTRIBUTE_KEY);
            final String id = thisFeature.getID();

            for (final String parentID : parentIDs) {

                final Set<Gff3FeatureImpl> theseParents = activeFeaturesWithIDs.get(parentID);
                if (theseParents != null) {
                    for (final Gff3FeatureImpl parent : theseParents) {
                        thisFeature.addParent(parent);
                    }
                }
                if (activeParentIDs.containsKey(parentID)) {
                    activeParentIDs.get(parentID).add(thisFeature);
                } else {
                    activeParentIDs.put(parentID, new HashSet<>(Collections.singleton(thisFeature)));
                }
            }

            if (id != null) {
                if (activeFeaturesWithIDs.containsKey(id)) {
                    for (final Gff3FeatureImpl coFeature : activeFeaturesWithIDs.get(id)) {
                        thisFeature.addCoFeature(coFeature);
                    }
                    activeFeaturesWithIDs.get(id).add(thisFeature);
                } else {
                    activeFeaturesWithIDs.put(id, new HashSet<>(Collections.singleton(thisFeature)));
                }
            }

            if (activeParentIDs.containsKey(thisFeature.getID())) {
                for (final Gff3FeatureImpl child : activeParentIDs.get(thisFeature.getID())) {
                    child.addParent(thisFeature);
                }
            }
        }

        validateFeature(thisFeature);
        if (depth == DecodeDepth.SHALLOW) {
            //flush all features immediatly
            prepareToFlushFeatures();
        }
        return featuresToFlush.poll();
    }


    /**
     * Parse attributes field for gff3 feature
     * @param attributesString attributes field string from line in gff3 file
     * @return map of keys to values for attributes of this feature
     * @throws UnsupportedEncodingException
     */
    static private Map<String, List<String>> parseAttributes(final String attributesString) throws UnsupportedEncodingException {
        if (attributesString.equals(Gff3Constants.UNDEFINED_FIELD_VALUE)) {
            return Collections.emptyMap();
        }
        final Map<String, List<String>> attributes = new LinkedHashMap<>();
        final List<String> splitLine = ParsingUtils.split(attributesString,Gff3Constants.ATTRIBUTE_DELIMITER);
        for(String attribute : splitLine) {
            final List<String> key_value = ParsingUtils.split(attribute,Gff3Constants.KEY_VALUE_SEPARATOR);
            if (key_value.size() != 2) {
                throw new TribbleException("Attribute string " + attributesString + " is invalid");
            }
            attributes.put(URLDecoder.decode(key_value.get(0).trim(), "UTF-8"), decodeAttributeValue(key_value.get(1).trim()));
        }
        return attributes;
    }

    static private Gff3BaseData parseLine(final String line, final int currentLine) {
        final List<String> splitLine = ParsingUtils.split(line, Gff3Constants.FIELD_DELIMITER);

        if (splitLine.size() != NUM_FIELDS) {
            throw new TribbleException("Found an invalid number of columns in the given Gff3 file at line + " + currentLine + " - Given: " + splitLine.size() + " Expected: " + NUM_FIELDS + " : " + line);
        }

        try {
            final String contig = URLDecoder.decode(splitLine.get(CHROMOSOME_NAME_INDEX), "UTF-8");
            final String source = URLDecoder.decode(splitLine.get(ANNOTATION_SOURCE_INDEX), "UTF-8");
            final String type = URLDecoder.decode(splitLine.get(FEATURE_TYPE_INDEX), "UTF-8");
            final int start = Integer.parseInt(splitLine.get(START_LOCATION_INDEX));
            final int end = Integer.parseInt(splitLine.get(END_LOCATION_INDEX));
            final double score = splitLine.get(SCORE_INDEX).equals(Gff3Constants.UNDEFINED_FIELD_VALUE) ? -1 : Double.parseDouble(splitLine.get(SCORE_INDEX));
            final int phase = splitLine.get(GENOMIC_PHASE_INDEX).equals(Gff3Constants.UNDEFINED_FIELD_VALUE) ? -1 : Integer.parseInt(splitLine.get(GENOMIC_PHASE_INDEX));
            final Strand strand = Strand.decode(splitLine.get(GENOMIC_STRAND_INDEX));
            final Map<String, List<String>> attributes = parseAttributes(splitLine.get(EXTRA_FIELDS_INDEX));
            return new Gff3BaseData(contig, source, type, start, end, score, strand, phase, attributes);
        } catch (final NumberFormatException ex ) {
            throw new TribbleException("Cannot read integer value for start/end position from line " + currentLine + ".  Line is: " + line, ex);
        } catch (final IOException ex) {
            throw new TribbleException("Cannot decode feature info from line " + currentLine + ".  Line is: " + line, ex);
        }
    }

    /**
     * Get list of sequence regions parsed by the codec.
     * @return list of sequence regions
     */
    public List<SequenceRegion> getSequenceRegions() {
        return Collections.unmodifiableList(new ArrayList<>(sequenceRegionMap.values()));
    }

    /**
     * Gets map from line number to comment found on that line.  The text of the comment EXCLUDES the leading # which indicates a comment line.
     * @return Map from line number to comment found on line
     */
    public Map<Integer, String> getCommentsWithLineNumbers() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(commentsWithLineNumbers));
    }

    /**
     * Gets list of comments parsed by the codec.  Excludes leading # which indicates a comment line.
     * @return
     */
    public List<String> getCommentTexts() {
        return Collections.unmodifiableList(new ArrayList<>(commentsWithLineNumbers.values()));
    }

    /**
     * If sequence region of feature's contig has been specified with sequence region directive, validates that
     * feature's coordinates are within the specified sequence region.  TribbleException is thrown if invalid.
     * @param feature
     */
    private void validateFeature(final Gff3Feature feature) {
        if (sequenceRegionMap.containsKey(feature.getContig())) {
            final SequenceRegion region = sequenceRegionMap.get(feature.getContig());
            if (feature.getStart() == region.getStart() && feature.getEnd() == region.getEnd()) {
                //landmark feature
                final boolean isCircular = Boolean.parseBoolean(extractSingleAttribute(feature.getAttribute(IS_CIRCULAR_ATTRIBUTE_KEY)));
                region.setCircular(isCircular);
            }
            if (region.isCircular()? !region.overlaps(feature) : !region.contains(feature)) {
                throw new TribbleException("feature at " + feature.getContig() + ":" + feature.getStart() + "-" + feature.getEnd() +
                        " not contained in specified sequence region (" + region.getContig() + ":" + region.getStart() + "-" + region.getEnd());
            }
        }
    }

    @Override
    public Feature decodeLoc(LineIterator lineIterator) throws IOException {
        return decode(lineIterator, DecodeDepth.SHALLOW);
    }

    @Override
    public boolean canDecode(final String inputFilePath) {
        boolean canDecode;
        try {
            // Simple file and name checks to start with:
            Path p = IOUtil.getPath(inputFilePath);
            canDecode = FileExtensions.GFF3.stream().anyMatch(fe -> p.toString().endsWith(fe));

            if (canDecode) {

                // Crack open the file and look at the top of it:
                final InputStream inputStream = IOUtil.hasGzipFileExtension(p)? new GZIPInputStream(Files.newInputStream(p)) : Files.newInputStream(p);

                try ( BufferedReader br = new BufferedReader(new InputStreamReader(inputStream)) ) {

                    String line = br.readLine();

                    // First line must be GFF version directive
                    if (Gff3Directive.toDirective(line) != Gff3Directive.VERSION3_DIRECTIVE) {
                        return false;
                    }
                    while (line.startsWith(Gff3Constants.COMMENT_START)) {
                        line = br.readLine();
                        if ( line == null ) {
                            return false;
                        }
                    }

                    // make sure line conforms to gtf spec
                    final List<String> fields = ParsingUtils.split(line, Gff3Constants.FIELD_DELIMITER);

                    canDecode &= fields.size() == NUM_FIELDS;

                    if (canDecode) {
                        // check that start and end fields are integers
                        try {
                            final int start = Integer.parseInt(fields.get(3));
                            final int end = Integer.parseInt(fields.get(4));
                        } catch (NumberFormatException | NullPointerException nfe) {
                            return false;
                        }

                        // check for strand

                        final String strand = fields.get(GENOMIC_STRAND_INDEX);
                        canDecode &= strand.equals(Strand.POSITIVE.toString()) ||
                                strand.equals(Strand.NEGATIVE.toString()) ||
                                strand.equals(Strand.NONE.toString()) ||
                                strand.equals("?");
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

    static List<String> decodeAttributeValue(final String attributeValue) {
        //split on VALUE_DELIMITER, then decode
        final List<String> splitValues = ParsingUtils.split(attributeValue, Gff3Constants.VALUE_DELIMITER);

        final List<String> decodedValues = new ArrayList<>();
        for (final String encodedValue : splitValues) {
            try {
                decodedValues.add(URLDecoder.decode(encodedValue.trim(), "UTF-8"));
            } catch (final UnsupportedEncodingException ex) {
                throw new TribbleException("Error decoding attribute " + encodedValue, ex);
            }
        }

        return decodedValues;
    }

    static String extractSingleAttribute(final List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        if (values.size() != 1) {
            throw new TribbleException("Attribute has multiple values when only one expected");
        }
        return values.get(0);
    }

    @Override
    public FeatureCodecHeader readHeader(LineIterator lineIterator) {

        List<String> header = new ArrayList<>();
        while(lineIterator.hasNext()) {
            String line = lineIterator.peek();
            if (line.startsWith(Gff3Constants.COMMENT_START)) {
                header.add(line);
                lineIterator.next();
            } else {
                break;
            }
        }

        return new FeatureCodecHeader(header, FeatureCodecHeader.NO_HEADER_END);
    }

    /**
     * Parse a directive line from a gff3 file
     * @param directiveLine
     * @throws IOException
     */
    private void parseDirective(final String directiveLine) throws IOException {
        final Gff3Directive directive = Gff3Directive.toDirective(directiveLine);
        if (directive != null) {
            processDirective(directive, directive.decode(directiveLine));

        } else {
            logger.warn("ignoring directive " + directiveLine);
        }
    }

    /**
     * Process a gff3 directive
     * @param directive the gff3 directive, indicated by a specific directive line
     * @param decodedResult the decoding of the directive line by the given directive
     */
    private void processDirective(final Gff3Directive directive, final Object decodedResult) {
        switch (directive) {
            case VERSION3_DIRECTIVE:
                break;

            case SEQUENCE_REGION_DIRECTIVE:
                final SequenceRegion newRegion = (SequenceRegion) decodedResult;
                if (sequenceRegionMap.containsKey(newRegion.getContig())) {
                    throw new TribbleException("directive for sequence-region " + newRegion.getContig() + " included more than once.");
                }
                sequenceRegionMap.put(newRegion.getContig(), newRegion);
                break;

            case FLUSH_DIRECTIVE:
                prepareToFlushFeatures();
                break;

            case FASTA_DIRECTIVE:
                reachedFasta = true;
                break;

            default:
                throw new IllegalArgumentException( "Directive " + directive + " has been added to Gff3Directive, but is not being handled by Gff3Codec::processDirective.  This is a BUG.");

        }
    }

    /**
     * move active top level features to featuresToFlush.  clear active features.
     */
    private void prepareToFlushFeatures() {
        featuresToFlush.addAll(activeFeatures);
        activeFeaturesWithIDs.clear();
        activeFeatures.clear();
        activeParentIDs.clear();
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
        return !lineIterator.hasNext() && activeFeatures.isEmpty() && featuresToFlush.isEmpty();
    }

    @Override
    public void close(final LineIterator lineIterator) {
        //cleanup resources
        featuresToFlush.clear();
        activeFeaturesWithIDs.clear();
        activeFeatures.clear();
        activeParentIDs.clear();
        CloserUtil.close(lineIterator);
    }

    @Override
    public TabixFormat getTabixFormat() {
        return TabixFormat.GFF;
    }

    /**
     * Enum for parsing directive lines.  If information in directive line needs to be parsed beyond specifying directive type, decode method should be overriden
     */
    public enum Gff3Directive {

        VERSION3_DIRECTIVE("##gff-version\\s+3(?:\\.\\d*)*$") {
            @Override
             protected Object decode(final String line) throws IOException {
                final String[] splitLine = line.split("\\s+");
                return splitLine[1];
            }

            @Override
            String encode(final Object object) {
                if (object == null) {
                    throw new TribbleException("Cannot encode null in VERSION3_DIRECTIVE");
                }
                if (!(object instanceof String)) {
                    throw new TribbleException("Cannot encode object of type " + object.getClass() + " in VERSION3_DIRECTIVE");
                }

                final String versionLine = "##gff-version " + (String)object;
                if (!regexPattern.matcher(versionLine).matches()) {
                    throw new TribbleException("Version " + (String)object + " is not a valid version");
                }

                return versionLine;
            }
        },

        SEQUENCE_REGION_DIRECTIVE("##sequence-region\\s+.+ \\d+ \\d+$") {
            final private int CONTIG_INDEX = 1;
            final private int START_INDEX = 2;
            final private int END_INDEX = 3;
            @Override
            protected Object decode(final String line) throws IOException {
                final String[] splitLine = line.split("\\s+");
                final String contig = URLDecoder.decode(splitLine[CONTIG_INDEX], "UTF-8");
                final int start = Integer.parseInt(splitLine[START_INDEX]);
                final int end = Integer.parseInt(splitLine[END_INDEX]);
                return new SequenceRegion(contig, start, end);
            }

            @Override
            String encode(final Object object) {
                if (object == null) {
                    throw new TribbleException("Cannot encode null in SEQUENCE_REGION_DIRECTIVE");
                }
                if (!(object instanceof SequenceRegion)) {
                    throw new TribbleException("Cannot encode object of type " + object.getClass() + " in SEQUENCE_REGION_DIRECTIVE");
                }

                final SequenceRegion sequenceRegion = (SequenceRegion) object;
                return "##sequence-region " + Gff3Writer.encodeString(sequenceRegion.getContig()) + " " + sequenceRegion.getStart() + " " + sequenceRegion.getEnd();
            }
        },

        FLUSH_DIRECTIVE("###$") {
            @Override
            String encode(final Object object) {
                return "###";
            }
        },

        FASTA_DIRECTIVE("##FASTA$") {
            @Override
            String encode(final Object object) {
                return "##FASTA";
            }
        };

        protected final Pattern regexPattern;

        Gff3Directive(String regex) {
            this.regexPattern = Pattern.compile(regex);
        }

        public static Gff3Directive toDirective(final String line) {
            for (final Gff3Directive directive : Gff3Directive.values()) {
                if(directive.regexPattern.matcher(line).matches()) {
                    return directive;
                }
            }
            return null;
        }

        protected Object decode(final String line) throws IOException {
            return null;
        }

        abstract String encode(final Object object);
    }

}
