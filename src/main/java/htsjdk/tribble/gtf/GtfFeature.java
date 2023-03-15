package htsjdk.tribble.gtf;

import htsjdk.tribble.Feature;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.annotation.Strand;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * describes a GTF record
 */
public interface GtfFeature extends Feature {
    public String getSource();

    @Override
    public  int getEnd();

    public Strand getStrand();

    public OptionalInt getPhase();

    public  String getType();

    @Override
    public String getContig();
	 @Override
	 public int getStart();

	public OptionalDouble getScore();

    public Map<String, List<String>> getAttributes();
	 
	 
    /**
     * @param key the searched key
     * @return true if feature has this attribute
     */
    public default boolean hasAttribute(final String key) {
    	return getAttributes().containsKey(key);
    	}
    
    /**
     * @param key the attribute name
     * @return the value for this attribute or null if there is none
     * @throws TribbleException if there is more than one value for this attribute
     */
    public default String getAttribute(final String key) {
    	  final List<String> values = getAttributes(key);
    	  if (values == null || values.isEmpty()) {
              return null;
          }

          if (values.size() != 1) {
              throw new TribbleException("Attribute " + key + " has multiple values when only one expected");
          }
        return values.get(0);
    	}

    /**
     * @param key the attribute name
     * @return the values for this attribute or an empty List if there is none
     */
    public default List<String> getAttributes(final String key) {
        return getAttributes().getOrDefault(key, Collections.emptyList());
    	}

    /**
     * @return true if this feature type is a gene
     */
    public default boolean isGene() {
    	return this.getType().equals("gene");
    }
    
    /**
     * @return true if this feature type is a transcript 
     */
    public default boolean isTranscript() {
    	return this.getType().equals("transcript");
    }

    /**
     * @return true if this feature type is an exon
     */
    public default boolean isExon() {
    	return this.getType().equals("exon");
    }
    
    /**
     * @return true if this feature type is a CDS
     */
    public default boolean isCDS() {
    	return this.getType().equals("CDS");
    }
    
    /**
     * shortcut to <code>getAttribute("gene_name")</code>
     * @return the gene name
     */
    public default String getGeneName() {
    	return getAttribute("gene_name");
    }
    
    /**
     * shortcut to <code>getAttribute(GtfConstants.GENE_ID)</code>
     * @return the gene name
     */
    public default String getGeneId() {
    	return getAttribute(GtfConstants.GENE_ID);
    }
    
    /**
     * shortcut to <code>getAttribute(GtfConstants.GENE_ID)</code>
     * @return the gene name
     */
    public default String getTranscriptId() {
    	return getAttribute(GtfConstants.TRANSCRIPT_ID);
    }
}
