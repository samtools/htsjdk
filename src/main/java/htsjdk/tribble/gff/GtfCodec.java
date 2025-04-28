package htsjdk.tribble.gff;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.readers.LineIterator;

/**
 * Codec for parsing GTF files
 * @author Pierre Lindenbaum
 * 
 * This codec reads GTF file.
 * In `DecodeDepth.DEEP` mode, the features with type="gene" have children with type="transcript|mrna" and `gene_id` linked to the gene.
 * Other features having an attribute `transcript_id` linked to their transcript.
 */

public class GtfCodec extends AbstractGxxCodec {

    private final static Log logger = Log.getInstance(GtfCodec.class);
    private final Map<String, Gff3FeatureImpl> id2gene = new HashMap<>();
    private final Map<String, Gff3FeatureImpl> id2transcripts = new HashMap<>();
    private final Map<String, List<Gff3FeatureImpl>> id2TranscriptComponents = new HashMap<>();

    public GtfCodec() {
        this(DecodeDepth.DEEP);
    }

    public GtfCodec(final DecodeDepth decodeDepth) {
        this(decodeDepth, KEY -> false);
    }

    /**
     * @param decodeDepth a value from DecodeDepth
     * @param filterOutAttribute filter to remove keys from the EXTRA_FIELDS column
     */
    public GtfCodec(final DecodeDepth decodeDepth, final Predicate<String> filterOutAttribute) {
        super(decodeDepth, filterOutAttribute);
        /* check required keys are always kept */
        for (final String key : new String[] {GtfConstants.GENE_ID, GtfConstants.TRANSCRIPT_ID}) {
            if (filterOutAttribute.test(key)) {
                throw new IllegalArgumentException("Predicate should always accept " + key);
            }
        }
    }

    /** test if a feature is a gene */
    private boolean isGene(final Gff3Feature feature) {
        return feature.getType().equals("gene");
    }

    /** test if a feature is a transcript */
    private boolean isTranscript(final Gff3Feature feature) {
        return feature.getType().equals("transcript") || feature.getType().equalsIgnoreCase("mrna");
    }

    @Override
    protected Gff3Feature decode(final LineIterator lineIterator, DecodeDepth depth) throws IOException {
        currentLine++;
        /*
         * Basic strategy: Load features into deque, create maps for gene and transcript.
         * For each feature, link to parents using these maps. When
         * reaching flush directive,  or end of file, prepare to flush features by moving all
         * active features to deque of features to flush, and clearing list of active features and
         * both maps. Always poll featuresToFlush to return any completed top level features.
         */
        if (!lineIterator.hasNext()) {
            // no more lines, flush whatever is active
            prepareToFlushFeatures();
            return featuresToFlush.poll();
        }

        final String line = lineIterator.next();

        if (line.startsWith(GtfConstants.COMMENT_START)) {
            commentsWithLineNumbers.put(currentLine,
                    line.substring(GtfConstants.COMMENT_START.length()));
            return featuresToFlush.poll();
        }

        final Gff3FeatureImpl thisFeature =
                new Gff3FeatureImpl(parseLine(line, currentLine, super.filterOutAttribute));
        super.activeFeatures.add(thisFeature);

        switch (depth) {
            case SHALLOW:
                // flush all features immediatly
                prepareToFlushFeatures();
                break;
            case DEEP:
                if (isGene(thisFeature)) {
                    final String gene_id = thisFeature.getUniqueAttribute(GtfConstants.GENE_ID)
                            .orElseThrow(() -> new TribbleException(
                                    "attribute " + GtfConstants.GENE_ID + " missing in " + line));
                    if (id2gene.containsKey(gene_id)) {
                        throw new TribbleException("duplicate gene " + gene_id + "  in " + line);
                    }
                    id2gene.put(gene_id, thisFeature);
                } else if (isTranscript(thisFeature)) {
                    final String transcript_id =
                            thisFeature.getUniqueAttribute(GtfConstants.TRANSCRIPT_ID)
                                    .orElseThrow(() -> new TribbleException("attribute "
                                            + GtfConstants.TRANSCRIPT_ID + " missing in " + line));
                    if (id2transcripts.containsKey(transcript_id)) {
                        throw new TribbleException(
                                "duplicate transcript " + transcript_id + "  in " + line);
                    }
                    id2transcripts.put(transcript_id, thisFeature);
                } else if (thisFeature.hasAttribute(GtfConstants.TRANSCRIPT_ID)) {
                    final String transcript_id =
                            thisFeature.getUniqueAttribute(GtfConstants.TRANSCRIPT_ID)
                                    .orElseThrow(() -> new TribbleException("attribute "
                                            + GtfConstants.TRANSCRIPT_ID + " missing in " + line));
                    List<Gff3FeatureImpl> components =
                            this.id2TranscriptComponents.get(transcript_id);
                    if (components == null) {
                        components = new ArrayList<>();
                        this.id2TranscriptComponents.put(transcript_id, components);
                    }
                    components.add(thisFeature);
                }
                break;
            default: throw new IllegalStateException(depth.name());
        }

        return featuresToFlush.poll();
    }

    @Override
    protected Map<String, List<String>> parseAttributesColumn(final String attributesString)
            throws UnsupportedEncodingException {
        return parseAttributes(attributesString);
    }

    /** parse attributes for GTF */
    static Map<String, List<String>> parseAttributes(final String attributesString)
            throws UnsupportedEncodingException {
        if (attributesString.trim().equals(GtfConstants.UNDEFINED_FIELD_VALUE)) {
            return Collections.emptyMap();
        }
        final Map<String, List<String>> attributes = new LinkedHashMap<>();

        final int len = attributesString.length();
        int i = 0;
        for (;;) {
            // skip whitespaces
            while (i < len && Character.isWhitespace(attributesString.charAt(i))) {
                i++;
            }

            // end of string
            if (i >= len) {
                break;
            }

            final StringBuilder keyBuilder = new StringBuilder();

            // consumme key
            while (i < len && !Character.isWhitespace(attributesString.charAt(i))) {
                char c = attributesString.charAt(i);
                if (c == Gff3Constants.KEY_VALUE_SEPARATOR) {
                    throw new TribbleException(
                            "unexpected gff3 separator " + Gff3Constants.KEY_VALUE_SEPARATOR
                                    + " in gtf line " + attributesString);
                }
                keyBuilder.append(c);
                i++;
            }
            // skip whitespaces
            while (i < len && Character.isWhitespace(attributesString.charAt(i))) {
                i++;
            }

            final String key = URLDecoder.decode(keyBuilder.toString().trim(), "UTF-8");

            /* read VALUE */
            final StringBuilder valueBuilder = new StringBuilder();

            // no value
            if (i >= len) {
                logger.warn("no value for '" + key + "' in " + attributesString);
            } else {
                // first char of value
                char c = attributesString.charAt(i);

                if (c == AbstractGxxConstants.ATTRIBUTE_DELIMITER) { // no value
                    logger.warn("no value for '" + key + "' in " + attributesString);
                } else if (c == Gff3Constants.KEY_VALUE_SEPARATOR) {
                    throw new TribbleException(
                            "unexpected gff3 separator '" + Gff3Constants.KEY_VALUE_SEPARATOR
                                    + "' in gtf line " + attributesString);
                }
                // quoted string
                else if (c == '\"' || c == '\'') {
                    final char quote_symbol = c;
                    i++;
                    for (;;) {
                        if (i >= len)
                            throw new TribbleException(
                                    "unclosed quoted string in " + attributesString);
                        c = attributesString.charAt(i);
                        if (c == '\\') {
                            if (i + 1 >= len)
                                throw new TribbleException(
                                        "unclosed escape symbol in " + attributesString);
                            ++i;
                            c = attributesString.charAt(i);
                            switch (c) {
                                case '"':
                                    valueBuilder.append("\"");
                                    break;
                                case '\'':
                                    valueBuilder.append("\'");
                                    break;
                                case 't':
                                    valueBuilder.append("\t");
                                    break;
                                case 'n':
                                    valueBuilder.append("\n");
                                    break;
                                default:
                                    logger.warn("unparsed escape symbol in " + attributesString);
                                    break;
                            }
                            i++;
                        } else if (c == quote_symbol) {
                            i++;
                            break;
                        } else {
                            valueBuilder.append(c);
                            i++;
                        }
                    } // end of for
                } else /* not a quoted string, for example, a number */
                {
                    while (i < len) {
                        c = attributesString.charAt(i);
                        if (c == Gff3Constants.KEY_VALUE_SEPARATOR) {
                            throw new TribbleException("unexpected gff3 separator '"
                                    + Gff3Constants.KEY_VALUE_SEPARATOR + "' in gtf line "
                                    + attributesString);
                        }
                        if (Character.isWhitespace(c)
                                || c == AbstractGxxConstants.ATTRIBUTE_DELIMITER) {
                            break;
                        }
                        valueBuilder.append(c);
                        i++;
                    }
                }
            } // end of else 'value'

            List<String> values = attributes.get(key);
            if (values == null) {
                values = new ArrayList<>();
                attributes.put(key, values);
            }
            values.add(URLDecoder.decode(valueBuilder.toString(), "UTF-8"));

            // skip whitespaces
            while (i < len && Character.isWhitespace(attributesString.charAt(i))) {
                i++;
            }
            if (i >= len) {
                break;
            }
            if (attributesString.charAt(i) != AbstractGxxConstants.ATTRIBUTE_DELIMITER) {
                throw new TribbleException("expected a '" + AbstractGxxConstants.ATTRIBUTE_DELIMITER
                        + "' in gtf line before : \"" + attributesString.substring(i) + "\". "
                        + attributes);
            }
            i++;
        }
        return attributes;
    }

    @Override
    public boolean canDecode(final String inputFilePath) {
        try {
            // Simple file and name checks to start with:
            Path p = IOUtil.getPath(inputFilePath);
            if (!FileExtensions.GTF.stream().anyMatch(fe -> p.toString().endsWith(fe))) {
                return false;
            }

            // Crack open the file and look at the top of it:
            final InputStream inputStream =
                    IOUtil.hasGzipFileExtension(p) ? new GZIPInputStream(Files.newInputStream(p))
                            : Files.newInputStream(p);

            try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                for (;;) {
                    line = br.readLine();
                    if (line == null) {
                        return false;
                    }
                    if (line.startsWith(GtfConstants.COMMENT_START)) {
                        continue;
                    }
                    return canDecodeFirstLine(line);
                }

            }

        } catch (final FileNotFoundException ex) {
            logger.error(inputFilePath + " not found.");
            return false;
        } catch (final IOException ex) {
            return false;
        }

    }


    /**
     * move active top level features to featuresToFlush. clear active features.
     */
    private void prepareToFlushFeatures() {

        this.id2transcripts.values().stream().forEach(FEAT -> {
            final String gene_id = FEAT.getUniqueAttribute(GtfConstants.GENE_ID)
                    .orElseThrow(() -> new TribbleException(
                            "attribute " + GtfConstants.GENE_ID + " missing in " + FEAT));
            if (!id2gene.containsKey(gene_id)) {
                throw new TribbleException("undefined gene " + gene_id + "  in " + FEAT);
            }
            final Gff3FeatureImpl geneFeat = id2gene.get(gene_id);
            FEAT.addParent(geneFeat);
        });

        this.id2TranscriptComponents.values().stream().flatMap(L -> L.stream()).forEach(FEAT -> {
            final String transcript_id = FEAT.getUniqueAttribute(GtfConstants.TRANSCRIPT_ID)
                    .orElseThrow(() -> new TribbleException(
                            "attribute " + GtfConstants.TRANSCRIPT_ID + " missing in " + FEAT));
            if (!id2transcripts.containsKey(transcript_id)) {
                throw new TribbleException(
                        "undefined transcript " + transcript_id + "  in " + FEAT);
            }
            final Gff3FeatureImpl transcriptFeat = id2transcripts.get(transcript_id);
            
            /* check they share the same gene_id */
            if(transcriptFeat.hasAttribute(GtfConstants.GENE_ID) && FEAT.hasAttribute(GtfConstants.GENE_ID) &&
                    !transcriptFeat.getUniqueAttribute(GtfConstants.GENE_ID).get().equals(FEAT.getUniqueAttribute(GtfConstants.GENE_ID).get()) ) {
                throw new TribbleException(
                        "same transcript_id bit no the same gene_id" + transcriptFeat + "  and " + FEAT);
            }
            
            FEAT.addParent(transcriptFeat);
        });


        featuresToFlush.addAll(activeFeatures);
        id2transcripts.clear();
        id2gene.clear();
        id2TranscriptComponents.clear();
        super.activeFeatures.clear();
    }



    @Override
    public boolean isDone(LineIterator lineIterator) {
        return !lineIterator.hasNext() && id2gene.isEmpty() && id2transcripts.isEmpty()
                && id2TranscriptComponents.isEmpty() && featuresToFlush.isEmpty()
                && super.activeFeatures.isEmpty();
    }

    @Override
    public void close(LineIterator source) {
        id2gene.clear();
        id2transcripts.clear();
        id2TranscriptComponents.clear();
        featuresToFlush.clear();
        activeFeatures.clear();
        CloserUtil.close(source);
    }
}
