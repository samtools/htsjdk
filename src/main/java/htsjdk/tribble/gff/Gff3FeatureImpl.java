package htsjdk.tribble.gff;

import htsjdk.samtools.util.Tuple;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.annotation.Strand;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Gff3 format spec is defined at https://github.com/The-Sequence-Ontology/Specifications/blob/31f62ad469b31769b43af42e0903448db1826925/gff3.md
 * Discontinuous features which are split between multiple lines in the gff files are implemented as separate features linked as "co-features"
 */
public class Gff3FeatureImpl implements Gff3Feature {
    private final static String DERIVES_FROM_ATTRIBUTE_KEY = "Derives_from";

    /**
     * basic data about feature, contig, position, strand, etc.
     */
    private final Gff3BaseData baseData;

    private final Set<Gff3FeatureImpl> parents;
    private final LinkedHashSet<Gff3FeatureImpl> children = new LinkedHashSet<>();
    private final LinkedHashSet<Gff3FeatureImpl> coFeatures = new LinkedHashSet<>();

    /**
     * top level features are features with no parents.  Each feature maintains a list
     * of its top level features, from which it and all related features descend.
     */
    private final Set<Gff3FeatureImpl> topLevelFeatures = new HashSet<>();
    private final boolean isTopLevelFeature;
    private boolean topLevelFeaturesFiltered = false;



    public Gff3FeatureImpl(final String contig, final String source, final String type,
                           final int start, final int end, final Strand strand, final int phase,
                           final Map<String, String> attributes) {
        this(contig, source, type, start, end, strand, phase, attributes, Collections.emptyList());
    }

    public Gff3FeatureImpl(final String contig, final String source, final String type,
                           final int start, final int end, final Strand strand, final int phase,
                           final Map<String, String> attributes, final Collection<Gff3FeatureImpl> parents) {
        baseData = new Gff3BaseData(contig, source, type, start, end, strand, phase, attributes);


        this.parents = Collections.unmodifiableSet(new LinkedHashSet<>(parents));

        /*build set of top level features as top level features of this feature's parents.
        When top level features is first accessed through getTopLevelFeatures(), this set will
        be filtered to account for Derives_from attribute if needed.
         */
        this.parents.forEach( p -> {
            topLevelFeatures.addAll(p.getTopLevelFeatures());
            p.addChild(this);
        });

        isTopLevelFeature = topLevelFeatures.isEmpty();

        if(topLevelFeatures.isEmpty()) {
            topLevelFeatures.add(this);
        }
    }

    /**
     * Get the set of top level features from which this feature is descended
     * @return set of top level feature from which this feature is descended
     */
    @Override
    public Set<Gff3FeatureImpl> getTopLevelFeatures() {
        if (!topLevelFeaturesFiltered) {
            if (baseData.attributes.containsKey(DERIVES_FROM_ATTRIBUTE_KEY)) {
                topLevelFeatures.removeIf(f -> !f.getID().equals(baseData.attributes.get(DERIVES_FROM_ATTRIBUTE_KEY)) && f.getDescendents().stream().noneMatch(f2 -> f2.getID().equals(baseData.attributes.get(DERIVES_FROM_ATTRIBUTE_KEY))));
            }
            topLevelFeaturesFiltered = true;
        }
        return topLevelFeatures;
    }

    @Override
    public boolean isTopLevelFeature() {
        return isTopLevelFeature;
    }

    /**
     * Gets set of parent features
     * @return set of parent features
     */
    @Override
    public Set<Gff3FeatureImpl> getParents() {return parents;}

    /**
     * Gets set of features for which this feature is a parent
     * @return set of child features
     */
    @Override
    public Set<Gff3FeatureImpl> getChildren() {return children;}

    @Override
    public Gff3BaseData getBaseData() {
        return baseData;
    }

    /**
     * Get set of all features this feature descends from, through chains of Parent attributes.  Derives_From can be used to specify a particular inheritance path for this feature when multiple paths are available
     * @return set of ancestor features
     */
    @Override
    public Set<Gff3FeatureImpl> getAncestors() {
        final List<Gff3FeatureImpl> ancestors = new ArrayList<>(parents);
        for (final Gff3FeatureImpl parent : parents) {
            ancestors.addAll(baseData.attributes.containsKey(DERIVES_FROM_ATTRIBUTE_KEY)? parent.getAncestors(baseData.attributes.get(DERIVES_FROM_ATTRIBUTE_KEY)) : parent.getAncestors());
        }
        return new LinkedHashSet<>(ancestors);
    }

    private Set<Gff3FeatureImpl> getAncestors(final String derivingFrom) {
        final List<Gff3FeatureImpl> ancestors = new ArrayList<>();
        for (final Gff3FeatureImpl parent : parents) {
            if (parent.getID().equals(derivingFrom) || parent.getAncestors().stream().anyMatch(f -> f.getID().equals(derivingFrom))) {
                ancestors.add(parent);
                ancestors.addAll(parent.getAncestors());
            }
        }
        return new LinkedHashSet<>(ancestors);
    }

    /**
     * Get set of all features descended from this features, through chains of Parent attributes.  Derives_From can be used to specify a particular inheritance path for this feature when multiple paths are available
     * @return set of descendents
     */
    @Override
    public Set<Gff3FeatureImpl> getDescendents() {
        final List<Gff3FeatureImpl> descendants = new ArrayList<>(children);
        final Set<String> idsInLineage = new HashSet<>(Collections.singleton(baseData.id));
        idsInLineage.addAll(children.stream().map(Gff3Feature::getID).collect(Collectors.toSet()));
        for(final Gff3FeatureImpl child : children) {
            descendants.addAll(child.getDescendents(idsInLineage));
        }
        return new LinkedHashSet<>(descendants);
    }

    private Set<Gff3FeatureImpl> getDescendents(final Set<String> idsInLineage) {
        final List<Gff3FeatureImpl> childrenToAdd = children.stream().filter(c -> c.getAttribute(DERIVES_FROM_ATTRIBUTE_KEY) == null ||
                idsInLineage.contains(c.getAttribute(DERIVES_FROM_ATTRIBUTE_KEY))).
                collect(Collectors.toList());
        final List<Gff3FeatureImpl> descendants = new ArrayList<>(childrenToAdd);

        final Set<String> updatedIdsInLineage = new HashSet<>(idsInLineage);
        updatedIdsInLineage.addAll(childrenToAdd.stream().map(Gff3Feature::getID).collect(Collectors.toSet()));
        for (final Gff3FeatureImpl child : childrenToAdd) {
            descendants.addAll(child.getDescendents(updatedIdsInLineage));
        }
        return new LinkedHashSet<>(descendants);
    }

    /**
     * Get set of co-features.  Co-features correspond to the other lines in the gff file that together make up a single discontinuous feature
     * @return set of co-features
     */
    @Override
    public Set<Gff3FeatureImpl> getCoFeatures() {return coFeatures;}

    @Override
    public boolean hasParents() {return !parents.isEmpty();}

    @Override
    public boolean hasChildren() {return !children.isEmpty();}


    @Override
    public boolean hasCoFeatures() {return !coFeatures.isEmpty();}

    private void addChild(final Gff3FeatureImpl child) {
        children.add(child);
    }

    /**
     * Add a feature as a coFeature of this feature.  When this method is called, the input coFeature will also be
     * added as a coFeature of all the other coFeatures of this object, and this feature and all coFeatures will be
     * added as coFeatures of the input coFeature.  All coFeatures must have equal IDs and parents.
     * @param coFeature feature to add as this features coFeature
     */
    public void addCoFeature(final Gff3FeatureImpl coFeature) {
        for (final Gff3FeatureImpl feature : coFeatures) {
            feature.addCoFeatureShallow(coFeature);
            coFeature.addCoFeatureShallow(feature);
        }
        addCoFeatureShallow(coFeature);
        coFeature.addCoFeatureShallow(this);
    }

    private void addCoFeatureShallow(final Gff3FeatureImpl coFeature) {
        coFeatures.add(coFeature);
        if (!coFeature.getID().equals(baseData.id)) {
            throw new TribbleException("Attempting to add co-feature with id " + coFeature.getID() + " to feature with id " + baseData.id);
        }
        if (!parents.equals(coFeature.getParents())) {

            throw new TribbleException("Co-featrues " + baseData.id + " do not have same parents");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof Gff3Feature)) {
            return false;
        }
        /* to test for equality, the doubly linked list representation used to represent feature relationships is replaced with a graph representation.
        equality for between two features is tested by testing equality between their base data fields, and equality between the graphs they are part of.
         */
        return baseData.equals(((Gff3Feature) other).getBaseData()) &&
                new Gff3Graph(this).equals(new Gff3Graph((Gff3Feature) other));
    }

    @Override
    public int hashCode() {
        //hash only based on baseData, to keep immutable.
        return baseData.hashCode();
    }





    /***
     * flatten this feature and all descendents into a set of features
     * @return set of this feature and all descendents
     */
    @Override
    public Set<Gff3FeatureImpl> flatten() {
        final LinkedHashSet<Gff3FeatureImpl> features = new LinkedHashSet<>(Collections.singleton(this));

        features.addAll(this.getDescendents());
        return features;
    }

    /**
     * Class for graph representation of relationships between features.
     * Used for testing equality between features
     */
    private static class Gff3Graph {
        final private Set<Gff3BaseData> nodes = new HashSet<>();
        final private Set<Tuple<Gff3BaseData, Gff3BaseData>> parentEdges = new HashSet<>();
        final private Set<Tuple<Gff3BaseData, Gff3BaseData>> childEdges = new HashSet<>();
        final private Set<Set<Gff3BaseData>> coFeatureSets = new HashSet<>();

        Gff3Graph(final Gff3Feature feature) {
            feature.getTopLevelFeatures().stream().flatMap(f -> f.flatten().stream()).forEach(this::addFeature);
        }

        private void addFeature(final Gff3Feature feature) {
            addNode(feature);
            addParentEdges(feature);
            addChildEdges(feature);
            addCoFeatureSet(feature);
        }

        private void addNode(final Gff3Feature feature) {
            nodes.add(feature.getBaseData());
        }

        private void addParentEdges(final Gff3Feature feature) {
            for(final Gff3Feature parent : feature.getParents()) {
                parentEdges.add(new Tuple<>(feature.getBaseData(), parent.getBaseData()));
            }
        }

        private void addChildEdges(final Gff3Feature feature) {
            for(final Gff3Feature child : feature.getChildren()) {
                childEdges.add(new Tuple<>(feature.getBaseData(), child.getBaseData()));
            }
        }

        private void addCoFeatureSet(final Gff3Feature feature) {
            if (feature.hasCoFeatures()) {
                final Set<Gff3BaseData> coFeaturesBaseData = feature.getCoFeatures().stream().map(Gff3Feature::getBaseData).collect(Collectors.toSet());
                coFeaturesBaseData.add(feature.getBaseData());
                coFeatureSets.add(coFeaturesBaseData);
            }
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if (!other.getClass().equals(Gff3Graph.class)) {
                return false;
            }

            return nodes.equals(((Gff3Graph) other).nodes) &&
                    parentEdges.equals(((Gff3Graph) other).parentEdges) &&
                    childEdges.equals(((Gff3Graph) other).childEdges) &&
                    coFeatureSets.equals(((Gff3Graph) other).coFeatureSets);
        }

        @Override
        public int hashCode() {
            int hash = nodes.hashCode();
            hash = 31 * hash + parentEdges.hashCode();
            hash = 31 * hash + childEdges.hashCode();
            hash = 31 * hash + coFeatureSets.hashCode();

            return hash;
        }
    }

}