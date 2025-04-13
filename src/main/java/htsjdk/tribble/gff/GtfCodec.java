package htsjdk.tribble.gff;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
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

import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.gff.AbstractGxxCodec.DecodeDepth;
import htsjdk.tribble.readers.LineIterator;

/**
 * Codec for parsing GTF files
 */

public class GtfCodec extends AbstractGxxCodec {
	
    private final static Log logger = Log.getInstance(GtfCodec.class);
    private final Map<String,Gff3FeatureImpl> activeGenes = new HashMap<>();
    private final Map<String,Gff3FeatureImpl> activeTranscripts = new HashMap<>();
    private final Map<String,List<Gff3FeatureImpl>> activeTranscriptComponents = new HashMap<>();
    private final List<Gff3FeatureImpl> activeUncharacterized = new ArrayList<>();

    
    public GtfCodec() {
        this(DecodeDepth.DEEP);
    }

    public GtfCodec(final DecodeDepth decodeDepth) {
        this(decodeDepth, KEY -> false);
    }
    /**
     * @param decodeDepth a value from DecodeDepth
     * @param filterOutAttribute  filter to remove keys from the EXTRA_FIELDS column
     */
    public GtfCodec(final DecodeDepth decodeDepth, final Predicate<String> filterOutAttribute) {
        super(decodeDepth,filterOutAttribute);
        /* check required keys are always kept */
        for (final String key : new String[] {GtfConstants.GENE_ID, GtfConstants.TRANSCRIPT_ID}) {
            if (filterOutAttribute.test(key)) {
                throw new IllegalArgumentException("Predicate should always accept " + key);
                }
        }
	}

    private boolean isGene(final Gff3Feature feature) {
    	return feature.getType().equals("gene");
    }
    
    private boolean isTranscript(final Gff3Feature feature) {
    	return feature.getType().equals("transcript") || feature.getType().equalsIgnoreCase("mrna") ;
    }
    
    
    
	@Override
	protected Gff3Feature decode(LineIterator lineIterator, DecodeDepth depth) throws IOException {
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


        if (line.startsWith(GtfConstants.COMMENT_START)) {
            commentsWithLineNumbers.put(currentLine, line.substring(GtfConstants.COMMENT_START.length()));
            return featuresToFlush.poll();
        }

        
        final Gff3FeatureImpl thisFeature = new Gff3FeatureImpl(parseLine(line, currentLine, super.filterOutAttribute));
        switch(depth) {
        case SHALLOW:
        		this.activeUncharacterized.add(thisFeature);
	        	//flush all features immediatly
	            prepareToFlushFeatures();
	            break;
        case DEEP: 
        	if(isGene(thisFeature)) {
                final String gene_id = thisFeature.getUniqueAttribute(GtfConstants.GENE_ID).orElseThrow(()->
                	new TribbleException("attribute "+GtfConstants.GENE_ID+" missing in "+line)
                	);
                if(activeGenes.containsKey(gene_id)) {
                	throw new TribbleException("duplicate gene "+ gene_id +"  in "+line);
                	}
                activeGenes.put(gene_id, thisFeature);
        	} else if(isTranscript(thisFeature)) {
        		final String transcript_id = thisFeature.getUniqueAttribute(GtfConstants.TRANSCRIPT_ID).orElseThrow(()->
            		new TribbleException("attribute "+GtfConstants.TRANSCRIPT_ID+" missing in "+line)
        				);
                if(activeTranscripts.containsKey(transcript_id)) {
                	throw new TribbleException("duplicate transcript "+ transcript_id +"  in "+line);
                	}
                activeTranscripts.put(transcript_id, thisFeature);
        	} else if(thisFeature.hasAttribute(GtfConstants.TRANSCRIPT_ID)){
        		final String transcript_id = thisFeature.getUniqueAttribute(GtfConstants.TRANSCRIPT_ID).orElseThrow(()->
        			new TribbleException("attribute "+GtfConstants.TRANSCRIPT_ID+" missing in "+line)
    				);
        		List<Gff3FeatureImpl> components =this.activeTranscriptComponents.get(transcript_id);
        		if(components==null) {
        			components = new ArrayList<>();
        			this.activeTranscriptComponents.put(transcript_id, components);
        			}
        		components.add(thisFeature);
        	} else {
        		this.activeUncharacterized.add(thisFeature);
        	}
        	break;
        }
        
        return featuresToFlush.poll();
    }

	@Override
	protected Map<String, List<String>> parseAttributesColumn(final String attributesString) throws UnsupportedEncodingException {
        if (attributesString.equals(GtfConstants.UNDEFINED_FIELD_VALUE)) {
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
                keyBuilder.append(attributesString.charAt(i));
                i++;
            }
            // skip whitespaces
            while (i < len && Character.isWhitespace(attributesString.charAt(i))) {
                i++;
            }

            final String key = keyBuilder.toString();


            // no value
            if (i >= len) {
                logger.warn("no value for '" + key + "' in " + attributesString);
            	attributes.put(key,Collections.singletonList(""));
                break;
            	}

            final List<String> values=new ArrayList<>(1);
            // read multiple values
	        for(;;) {
	            /* read VALUE */
	            final StringBuilder valueBuilder = new StringBuilder();
	            // first char of value
	            char c = attributesString.charAt(i);
	
	            if (c == AbstractGxxConstants.ATTRIBUTE_DELIMITER) { // no value
	                i++;
	                values.add("");
	                logger.warn("no value for '" + key + "' in " + attributesString);
	                break;
	            	}
	
	
	            // quoted string
	            if (c == '\"') {
	                i++;
	                while (i < len) {
	                    c = attributesString.charAt(i);
	                    ++i;
	                    if (c == '\\') {
	                        c = (i < len ? attributesString.charAt(i) : '\0');
	                        ++i;
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
	                                //logger.warn("unparsed value in " + attributesString);
	                                break;
	                        }
	                    } else if (c == '\"') {
	                    	values.add(valueBuilder.toString());
	                        break;
	                    } else {
	                        valueBuilder.append(c);
	                    }
	                } // end of while
	                
	            
	              
	            }
	            else /* not a quoted string */
		            {
		                while (i < len) {
		                    c = attributesString.charAt(i);
		                    ++i;
		                    if (Character.isWhitespace(c)  || c == AbstractGxxConstants.ATTRIBUTE_DELIMITER) {
		                        break;
		                    }
		                    valueBuilder.append(c);
		                }
		                values.add(valueBuilder.toString());
		            }
	            
                // skip whitespaces
                while (i < len && Character.isWhitespace(attributesString.charAt(i))) {
                    i++;
                }
                if (i >= len) {
                    break;
                }

                if(attributesString.charAt(i)==AbstractGxxConstants.ATTRIBUTE_DELIMITER) {
                	i++;
                	break;
                	}
	            }//end of read multiple values
	        attributes.put(key, values);
        	}
        return attributes;

    }

	@Override
	public boolean canDecode(final String inputFilePath) {
        try {
            // Simple file and name checks to start with:
            Path p = IOUtil.getPath(inputFilePath);
            if(!FileExtensions.GTF.stream().anyMatch(fe -> p.toString().endsWith(fe))) {
            	return false;
            	}

            // Crack open the file and look at the top of it:
            final InputStream inputStream = IOUtil.hasGzipFileExtension(p)? new GZIPInputStream(Files.newInputStream(p)) : Files.newInputStream(p);

            try ( BufferedReader br = new BufferedReader(new InputStreamReader(inputStream)) ) {
            	String line;
                for(;;) {
                	line = br.readLine();
                	 if ( line == null ) {
                         return false;
                     }
                	 if(line.startsWith(GtfConstants.COMMENT_START)) {
                		 continue;
                	 }
                	 return canDecodeFirstLine(line);
                }

                }
           
        }
        catch (final FileNotFoundException ex) {
            logger.error(inputFilePath + " not found.");
            return false;
        }
        catch (final IOException ex) {
            return false;
        }

    }


    /**
     * move active top level features to featuresToFlush.  clear active features.
     */
    private void prepareToFlushFeatures() {
    	
    	activeTranscripts.values().stream().forEach(FEAT->{
            final String gene_id = FEAT.getUniqueAttribute(GtfConstants.GENE_ID).orElseThrow(()->
        	new TribbleException("attribute "+GtfConstants.GENE_ID+" missing in "+FEAT)
        	);
       final Gff3FeatureImpl geneFeat  = activeGenes.get(gene_id);
        if(!activeGenes.containsKey(gene_id)) {
        	throw new TribbleException("undefined gene "+ gene_id +"  in "+FEAT);
        	}
        FEAT.addParent(geneFeat);
		});
    	
    	activeTranscriptComponents.values().stream().flatMap(L->L.stream()).forEach(FEAT->{
            final String transcript_id = FEAT.getUniqueAttribute(GtfConstants.TRANSCRIPT_ID).orElseThrow(()->
        	new TribbleException("attribute "+GtfConstants.TRANSCRIPT_ID+" missing in "+FEAT)
        	);
       final Gff3FeatureImpl transcriptFeat  = activeTranscripts.get(transcript_id);
        if(!activeTranscripts.containsKey(transcript_id)) {
        	throw new TribbleException("undefined transcript_id "+ transcript_id +"  in "+FEAT);
        	}
        FEAT.addParent(transcriptFeat);
		});
    	
    	
        featuresToFlush.addAll(activeGenes.values());
        featuresToFlush.addAll(activeUncharacterized);
    	activeTranscripts.clear();
    	activeTranscriptComponents.clear();
    	activeGenes.clear();
    	activeUncharacterized.clear();
    }

	
	@Override
	public boolean isDone(LineIterator lineIterator) {
		// TODO Auto-generated method stub
		return false;
	}
}
