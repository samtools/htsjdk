package htsjdk.tribble.gff;

import htsjdk.tribble.Feature;
import htsjdk.tribble.annotation.Strand;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gff3 format spec is defined at https://github.com/The-Sequence-Ontology/Specifications/blob/31f62ad469b31769b43af42e0903448db1826925/gff3.md
 * Discontinuous features which are split between multiple lines in the gff files are implemented as separate features linked as "co-features"
 */
public interface Gff3Feature extends Feature {
    /**
     * Get the set of top level features from which this feature is descended.
     * Top level features are features with no parents
     * @return set of top level feature from which this feature is descended
     */
    Set<? extends Gff3Feature> getTopLevelFeatures();

    boolean isTopLevelFeature();


    default String getSource() {
        return getBaseData().source;
    }

    @Override
    default int getEnd() {
        return getBaseData().end;
    }

    default Strand getStrand() {
        return getBaseData().strand;
    }

    default int getPhase() {
        return getBaseData().phase;
    }

    default String getType() {return getBaseData().type;}

    @Override
    default String getContig() {
        return getBaseData().contig;
    }

     @Override
    default int getStart() {
        return getBaseData().start;
    }

    default String getAttribute(final String key) {
        return getBaseData().attributes.get(key);
    }

    default Map<String, String> getAttributes() { return getBaseData().attributes;}

    default String getID() { return getBaseData().id;}

    default String getName() { return getBaseData().name;}

    default String getAlias() { return getBaseData().alias;}

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
