/*
 * The MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.tribble.gtf;

import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.LocationAware;

import htsjdk.samtools.util.Log;
import htsjdk.tribble.AsciiFeatureCodec;
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
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

/**
 * Codec for parsing Gtf files.
 * The code was partially copied from the gff package
 * @author Pierre Lindenbaum
 */
public class GtfCodec extends AsciiFeatureCodec<GtfFeature> {

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
    
    private final static Log logger = Log.getInstance(GtfCodec.class);

    /** current line number */
    private int currentLine = 0;

    /** filter to removing keys from the EXTRA_FIELDS column */
    private final Predicate<String> filterOutAttribute;
    

    public GtfCodec() {
        this(KEY -> false);
    }

    /**
     * @param decodeDepth a value from DecodeDepth
     * @param filterOutAttribute  filter to remove keys from the EXTRA_FIELDS column
     */
    public GtfCodec(final Predicate<String> filterOutAttribute) {
        super(GtfFeature.class);
        this.filterOutAttribute = filterOutAttribute;
        /* check required keys are always kept */
        for (final String key : new String[] {GtfConstants.GENE_ID, GtfConstants.TRANSCRIPT_ID}) {
            if (filterOutAttribute.test(key)) {
                throw new IllegalArgumentException("Predicate should always accept " + key);
                }
        }
    }

    
    @Override
    public GtfFeature decode(final LineIterator lineIterator)  {
		while(lineIterator.hasNext()) {
			 final String line = lineIterator.next();
			 this.currentLine++;
			 if (line.startsWith(GtfConstants.COMMENT_START)) continue;
			return decode(line);
			}
		return null;
		}

    /**
     * Parse attributes field for Gtf feature
     * @param attributesString attributes field string from line in Gtf file
     * @return map of keys to values for attributes of this feature
     * @throws UnsupportedEncodingException
     */
    static private Map<String, List<String>> parseAttributes(final String attributesString) throws UnsupportedEncodingException {
        if (attributesString.equals(GtfConstants.UNDEFINED_FIELD_VALUE)) {
            return Collections.emptyMap();
        	}
        final Map<String, List<String>> attributes = new LinkedHashMap<>();
        final int len = attributesString.length();
        int i=0;
        for(;;) {
        	//skip whitespaces
        	while( i<  len &&  Character.isWhitespace(attributesString.charAt(i))) {
        		i++;
        		}
        	//end of string
        	if(i>=len) break;
        	final StringBuilder keyBuilder = new StringBuilder();
        	
        	//consumme key
        	while( i< len &&  !Character.isWhitespace(attributesString.charAt(i))) {
        		keyBuilder.append(attributesString.charAt(i));
        		i++;
        		}
        	//skip whitespaces
        	while( i<  len &&  Character.isWhitespace(attributesString.charAt(i))) {
        		i++;
        		}
        	
        	final String key = keyBuilder.toString();
        	        	
        	List<String> values = attributes.get(key);
        	if(values==null) {
        		values=new ArrayList<>();
        		 attributes.put(key,values);
        		}
        	
        	//no value
        	if (i >= len) {
        		logger.warn("no value for '"+key+"' in "+attributesString);
        		values.add("");
        		break;
        		}
        	
			/* read VALUE */
			final StringBuilder valueBuilder =new StringBuilder();
			//first char of value
			char c = attributesString.charAt(i);
			
			if (c==GtfConstants.ATTRIBUTE_DELIMITER) {  // no value
        		i++;
				values.add("");
        		logger.warn("no value for '"+key+"' in "+attributesString);
				continue;
        		}
			
			
			// quoted string
			if( c == '\"')
				{
				i++;
				while(i <len) {
					c= attributesString.charAt(i);
					++i;
					if(c=='\\')
						{
						c=(i < len ?attributesString.charAt(i):'\0');
						++i;
						switch(c) {
							case '"': valueBuilder.append("\"");break;
							case '\'': valueBuilder.append("\'");break;
							case 't': valueBuilder.append("\t");break;
							case 'n': valueBuilder.append("\n");break;
							default:logger.warn("unparsed value in "+attributesString);break;
							}
						}
					else if(c=='\"')
						{
						values.add(valueBuilder.toString());
						break;
						}
					else
						{
						valueBuilder.append(c);
						}
					}
				//skip whitespaces
	        	while( i<  len &&  Character.isWhitespace(attributesString.charAt(i))) {
	        		i++;
	        		}
	        	if ( i >= len) break;
	        	if ( attributesString.charAt(i) != GtfConstants.ATTRIBUTE_DELIMITER) throw new TribbleException("expected a "+GtfConstants.ATTRIBUTE_DELIMITER+" after value "+ valueBuilder);
	        	i++;
				}
			else /* not a quoted string */
				{
				while( i < len) {
					c= attributesString.charAt(i);
					++i;
					if(c==GtfConstants.ATTRIBUTE_DELIMITER)
						{
						break;
						}
					valueBuilder.append(c);
					}
				values.add(valueBuilder.toString());
				}
        	
        	}
        return attributes;
    	}

	@Override
	public GtfFeature decode(final String line) {
		if(line.startsWith(GtfConstants.COMMENT_START)) return null;
		
        final List<String> splitLine = ParsingUtils.split(line, GtfConstants.FIELD_DELIMITER);

        if (splitLine.size() != NUM_FIELDS) {
            throw new TribbleException("Found an invalid number of columns in the given Gtf file at line + " + this.currentLine + 
            		" - Given: " + splitLine.size() + " Expected: " + NUM_FIELDS + " : " + line);
        }

        try {
            final String contig = URLDecoder.decode(splitLine.get(CHROMOSOME_NAME_INDEX), "UTF-8");
            final String source = URLDecoder.decode(splitLine.get(ANNOTATION_SOURCE_INDEX), "UTF-8");
            final String type = URLDecoder.decode(splitLine.get(FEATURE_TYPE_INDEX), "UTF-8");
            final int start = Integer.parseInt(splitLine.get(START_LOCATION_INDEX));
            final int end = Integer.parseInt(splitLine.get(END_LOCATION_INDEX));
            final OptionalDouble score = splitLine.get(SCORE_INDEX).equals(GtfConstants.UNDEFINED_FIELD_VALUE) ? OptionalDouble.empty() : OptionalDouble.of(Double.parseDouble(splitLine.get(SCORE_INDEX)));
            final OptionalInt phase = splitLine.get(GENOMIC_PHASE_INDEX).equals(GtfConstants.UNDEFINED_FIELD_VALUE) ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(splitLine.get(GENOMIC_PHASE_INDEX)));
            final Strand strand = Strand.decode(splitLine.get(GENOMIC_STRAND_INDEX));
            final Map<String, List<String>> attributes = parseAttributes(splitLine.get(EXTRA_FIELDS_INDEX));
            /* remove attibutes matching 'filterOutAttribute' */
            attributes.keySet().removeIf(this.filterOutAttribute);
            return new GtfFeatureImpl(contig, source, type, start, end, score, strand, phase, attributes);
        } catch (final NumberFormatException ex ) {
            throw new TribbleException("Cannot read integer value for start/end position from line " + currentLine + ".  Line is: " + line, ex);
        } catch (final IOException ex) {
            throw new TribbleException("Cannot decode feature info from line " + currentLine + ".  Line is: " + line, ex);
        }
    }

    @Override
    public Feature decodeLoc(LineIterator lineIterator) throws IOException {
        return decode(lineIterator);
    }

    @Override
    public boolean canDecode(final String inputFilePath) {
        boolean canDecode;
        try {
            // Simple file and name checks to start with:
            Path p = IOUtil.getPath(inputFilePath);
            canDecode = FileExtensions.GTF.stream().anyMatch(fe -> p.toString().endsWith(fe));

            if (canDecode) {
                // Crack open the file and look at the top of it:
                try( InputStream inputStream = IOUtil.hasGzipFileExtension(p)? new GZIPInputStream(Files.newInputStream(p)) : Files.newInputStream(p) ) {
	                try ( BufferedReader br = new BufferedReader(new InputStreamReader(inputStream)) ) {
	
	                    String line = br.readLine();
	
	                    while (line.startsWith(GtfConstants.COMMENT_START)) {
	                        line = br.readLine();
	                        if ( line == null ) {
	                            return false;
	                        }
	                    }
	
	                    // make sure line conforms to gtf spec
	                    final List<String> fields = ParsingUtils.split(line, GtfConstants.FIELD_DELIMITER);
	
	                    canDecode &= fields.size() == NUM_FIELDS;
	
	                    if (canDecode) {
	                        // check that start and end fields are integers
	                        try {
	                            Integer.parseInt(fields.get(START_LOCATION_INDEX));
	                            Integer.parseInt(fields.get(END_LOCATION_INDEX));
	                            
	                            parseAttributes(fields.get(EXTRA_FIELDS_INDEX));
	                            
	                        } catch (NumberFormatException | NullPointerException | UnsupportedEncodingException | TribbleException ex) {
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
        final List<String> splitValues = ParsingUtils.split(attributeValue, GtfConstants.VALUE_DELIMITER);

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


    @Override
    public FeatureCodecHeader readHeader(final LineIterator lineIterator) {


        return new FeatureCodecHeader(readActualHeader(lineIterator), FeatureCodecHeader.NO_HEADER_END);
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
        return !lineIterator.hasNext();
    }

    @Override
    public void close(final LineIterator lineIterator) {
        //cleanup resources
        CloserUtil.close(lineIterator);
    }

    @Override
    public TabixFormat getTabixFormat() {
        return TabixFormat.GFF;// GFF has the same columns than GTF
    }


	@Override
	public Object readActualHeader(LineIterator lineIterator) {
        final List<String> header = new ArrayList<>();
        while(lineIterator.hasNext()) {
            final String line = lineIterator.peek();
            if (line.startsWith(GtfConstants.COMMENT_START)) {
                header.add(line);
                lineIterator.next();
            } else {
                break;
            }
        }
        return header;
	}



}
