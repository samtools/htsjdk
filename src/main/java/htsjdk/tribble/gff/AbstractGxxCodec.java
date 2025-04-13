package htsjdk.tribble.gff;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.LocationAware;
import htsjdk.tribble.AbstractFeatureCodec;
import htsjdk.tribble.Feature;
import htsjdk.tribble.FeatureCodecHeader;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.annotation.Strand;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.readers.AsciiLineReader;
import htsjdk.tribble.readers.AsciiLineReaderIterator;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.tribble.readers.LineIteratorImpl;
import htsjdk.tribble.readers.SynchronousLineReader;
import htsjdk.tribble.util.ParsingUtils;

/**
 * Abstract Base Codec for parsing Gff3 files or GTF codec
 */

public abstract class AbstractGxxCodec extends AbstractFeatureCodec<Gff3Feature, LineIterator> {

    protected static final int NUM_FIELDS = 9;

    protected static final int CHROMOSOME_NAME_INDEX = 0;
    protected static final int ANNOTATION_SOURCE_INDEX = 1;
    protected static final int FEATURE_TYPE_INDEX = 2;
    protected static final int START_LOCATION_INDEX = 3;
    protected static final int END_LOCATION_INDEX = 4;
    protected static final int SCORE_INDEX = 5;
    protected static final int GENOMIC_STRAND_INDEX = 6;
    protected static final int GENOMIC_PHASE_INDEX = 7;
    protected static final int EXTRA_FIELDS_INDEX = 8;


    protected final Queue<Gff3FeatureImpl> featuresToFlush = new ArrayDeque<>();
    protected final Map<Integer, String> commentsWithLineNumbers = new LinkedHashMap<>();


    protected DecodeDepth decodeDepth;

    protected int currentLine = 0;

    /** filter to removing keys from the EXTRA_FIELDS column */
    protected final Predicate<String> filterOutAttribute;
    


    /**
     * @param decodeDepth a value from DecodeDepth
     * @param filterOutAttribute  filter to remove keys from the EXTRA_FIELDS column
     */
    protected AbstractGxxCodec(final DecodeDepth decodeDepth, final Predicate<String> filterOutAttribute) {
        super(Gff3Feature.class);
        this.decodeDepth = decodeDepth;
        this.filterOutAttribute = filterOutAttribute;
        }
    

    public enum DecodeDepth {
        DEEP ,
        SHALLOW
    }
    
    @Override
    public final Gff3Feature decode(final LineIterator lineIterator) throws IOException {
        return decode(lineIterator, decodeDepth);
    }

    protected abstract Gff3Feature decode(final LineIterator lineIterator, final DecodeDepth depth) throws IOException;

    

    /**
     * Parse attributes field for gff3 feature
     * @param attributesString attributes field string from line in gff3 file
     * @return map of keys to values for attributes of this feature
     * @throws UnsupportedEncodingException
     */
    protected abstract Map<String, List<String>> parseAttributesColumn(final String attributesString) throws UnsupportedEncodingException;


    protected final Gff3BaseData parseLine(final String line, final int currentLine, final Predicate<String> filterOutAttribute) {
        final List<String> splitLine = ParsingUtils.split(line, AbstractGxxConstants.FIELD_DELIMITER);

        if (splitLine.size() != NUM_FIELDS) {
            throw new TribbleException("Found an invalid number of columns in the given Gff3/GTF file at line + " + currentLine + " - Given: " + splitLine.size() + " Expected: " + NUM_FIELDS + " : " + line);
        }

        try {
            final String contig = URLDecoder.decode(splitLine.get(CHROMOSOME_NAME_INDEX), "UTF-8");
            final String source = URLDecoder.decode(splitLine.get(ANNOTATION_SOURCE_INDEX), "UTF-8");
            final String type = URLDecoder.decode(splitLine.get(FEATURE_TYPE_INDEX), "UTF-8");
            final int start = Integer.parseInt(splitLine.get(START_LOCATION_INDEX));
            final int end = Integer.parseInt(splitLine.get(END_LOCATION_INDEX));
            final double score = splitLine.get(SCORE_INDEX).equals(AbstractGxxConstants.UNDEFINED_FIELD_VALUE) ? -1 : Double.parseDouble(splitLine.get(SCORE_INDEX));
            final int phase = splitLine.get(GENOMIC_PHASE_INDEX).equals(AbstractGxxConstants.UNDEFINED_FIELD_VALUE) ? -1 : Integer.parseInt(splitLine.get(GENOMIC_PHASE_INDEX));
            final Strand strand = Strand.decode(splitLine.get(GENOMIC_STRAND_INDEX));
            final Map<String, List<String>> attributes = parseAttributesColumn(splitLine.get(EXTRA_FIELDS_INDEX));
            /* remove attibutes matching 'filterOutAttribute' */
            attributes.keySet().removeIf(filterOutAttribute);
            return new Gff3BaseData(contig, source, type, start, end, score, strand, phase, attributes);
        } catch (final NumberFormatException ex ) {
            throw new TribbleException("Cannot read integer value for start/end position from line " + currentLine + ".  Line is: " + line, ex);
        } catch (final IOException ex) {
            throw new TribbleException("Cannot decode feature info from line " + currentLine + ".  Line is: " + line, ex);
        }
    }

    /**
     * Gets map from line number to comment found on that line.  The text of the comment EXCLUDES the leading # which indicates a comment line.
     * @return Map from line number to comment found on line
     */
    public final Map<Integer, String> getCommentsWithLineNumbers() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(commentsWithLineNumbers));
    }

    /**
     * Gets list of comments parsed by the codec.  Excludes leading # which indicates a comment line.
     * @return
     */
    public final List<String> getCommentTexts() {
        return Collections.unmodifiableList(new ArrayList<>(commentsWithLineNumbers.values()));
    }

    @Override
    public final Feature decodeLoc(LineIterator lineIterator) throws IOException {
        return decode(lineIterator, DecodeDepth.SHALLOW);
    }

    @Override
    public abstract boolean canDecode(final String inputFilePath);

    protected boolean canDecodeFirstLine(final String line) {
        // make sure line conforms to gtf spec
        final List<String> fields = ParsingUtils.split(line, AbstractGxxConstants.FIELD_DELIMITER);

        if(fields.size() != NUM_FIELDS) return false;;

            // check that start and end fields are integers
            try {
                /* final int start = */ Integer.parseInt(fields.get(3));
                /* final int end = */ Integer.parseInt(fields.get(4));
            } catch (NumberFormatException | NullPointerException nfe) {
                return false;
            }

            // check for strand
            final String strand = fields.get(GENOMIC_STRAND_INDEX);
            return strand.equals(Strand.POSITIVE.toString()) ||
                    strand.equals(Strand.NEGATIVE.toString()) ||
                    strand.equals(Strand.NONE.toString()) ||
                    strand.equals("?");
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
    public final FeatureCodecHeader readHeader(LineIterator lineIterator) {

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

    @Override
    public final LineIterator makeSourceFromStream(final InputStream bufferedInputStream) {
        return new LineIteratorImpl(new SynchronousLineReader(bufferedInputStream));
    }

    @Override
    public final LocationAware makeIndexableSourceFromStream(final InputStream bufferedInputStream) {
        return new AsciiLineReaderIterator(AsciiLineReader.from(bufferedInputStream));
    }

    @Override
    public abstract boolean isDone(final LineIterator lineIterator);

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
    public final TabixFormat getTabixFormat() {
        return TabixFormat.GFF;
    }

}
