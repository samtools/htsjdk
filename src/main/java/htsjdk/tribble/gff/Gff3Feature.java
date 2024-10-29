package htsjdk.tribble.gff;

import htsjdk.tribble.Feature;
import htsjdk.tribble.annotation.Strand;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Gff3 format spec is defined at https://github.com/The-Sequence-Ontology/Specifications/blob/31f62ad469b31769b43af42e0903448db1826925/gff3.md
 * Discontinuous features which are split between multiple lines in the gff files are implemented as separate features linked as "co-features"
 */
public interface Gff3Feature extends Feature {
    /**
     * Get the set of top level features from which this feature is descended.
     * Top level features are features with no linked parents
     * @return set of top level feature from which this feature is descended
     */
    Set<? extends Gff3Feature> getTopLevelFeatures();

    boolean isTopLevelFeature();


    default String getSource() {
        return getBaseData().getSource();
    }

    @Override
    default int getEnd() {
        return getBaseData().getEnd();
    }

    default Strand getStrand() {
        return getBaseData().getStrand();
    }

    default int getPhase() {
        return getBaseData().getPhase();
    }

    default String getType() {return getBaseData().getType();}

    @Override
    default String getContig() {
        return getBaseData().getContig();
    }

     @Override
    default int getStart() {
        return getBaseData().getStart();
    }


    default List<String> getAttribute(final String key) {
        return getBaseData().getAttribute(key);
    }
    
    /**
     * Returns <tt>true</tt> if this record contains an attribute for the specified key.
     * 
     * @param key key whose presence in this map is to be tested
     * @return <tt>true</tt> if this map contains an attribute for the specified key
     */
    default boolean hasAttribute(final String key) {
        return getBaseData().hasAttribute(key);
    }

    /**
     * Most attributes in a GFF file are present just one time in a line, e.g. : <tt>gene_biotype</tt>,  <tt>gene_name</tt>, etc ...  
     * This function returns an <tt>Optional.empty</tt> if the <tt>key</tt> is not present,
     *  an <tt>Optional.of(value)</tt> if there is only one value associated to the <tt>key</tt>,
     *  or it throws an <tt>IllegalArgumentException</tt> if there is more than one value.
     * 
     * @param key key whose presence in the attributes is to be tested
     * @return <tt>Optional&lt;String&gt;</tt> if this map contains zero or one attribute for the specified key
     * @throws IllegalArgumentException if there is more than one value.
     */
    default Optional<String> getUniqueAttribute(final String key) {
       return getBaseData().getUniqueAttribute(key);
    }
    
    default Map<String, List<String>> getAttributes() { return getBaseData().getAttributes();}

    default String getID() { return getBaseData().getId();}

    default String getName() { return getBaseData().getName();}

    default List<String> getAliases() { return getBaseData().getAliases();}

    default double getScore() { return getBaseData().getScore();}

    /**
     * Get BaseData object which contains all the basic information of the feature
     * @return
     */
    Gff3BaseData getBaseData();

    /**
     * Gets set of parent features
     * @return set of parent features
     */
    Set<? extends Gff3Feature> getParents();

    /**
     * Gets set of features for which this feature is a parent
     * @return set of child features
     */
    Set<? extends Gff3Feature> getChildren();

    /**
     * Get set of all features this feature descends from, through chains of Parent attributes.  If Derives_From exists for this feature,
     * then only features along the inheritance path specified by the Derives_From attribute should be included as ancestors of this feature
     * @return set of ancestor features
     */
    Set<? extends Gff3Feature> getAncestors();

    /**
     * Get set of all features descended from this features, through chains of Parent attributes.  If Derives_From attribute exists for a feature,
     * it should only be included as a descendent of this feature if the inheritance path specified by its Derives_From attribute includes this feature
     * @return set of descendents
     */
    Set<? extends Gff3Feature> getDescendents();

    /**
     * Get set of co-features.  Co-features correspond to the other lines in the gff file that together make up a single discontinuous feature
     * @return set of co-features
     */
    Set<? extends Gff3Feature> getCoFeatures();

    /***
     * Flatten this feature and all descendents into a set of features.  The Derives_From attribute is respected if it exists
     * for this feature
     * @return set of this feature and all descendents
     */
    Set<? extends Gff3Feature> flatten();

    boolean hasParents();

    boolean hasChildren();

    boolean hasCoFeatures();
}
