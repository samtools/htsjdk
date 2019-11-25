package htsjdk.tribble.gff;

import htsjdk.samtools.util.Tuple;
import htsjdk.tribble.Feature;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.annotation.Strand;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Gff3 format spec is defined at https://github.com/The-Sequence-Ontology/Specifications/blob/master/gff3.md
 * Discontinous features which are split between multiple lines in the gff files are implemented as separate features linked as "co-features"
 */
public class Gff3Feature implements Feature {

    private final Gff3BaseData baseData;
    private final Set<Gff3Feature> parents;
    private final Set<Gff3Feature> children = new HashSet<>();
    private final Set<Gff3Feature> coFeatures = new HashSet<>();

    /**
     * top level features are features with no parents.  Each feature maintains a list
     * of its top level features, from which it and all related features descend.
     */
    private final Set<Gff3Feature> topLevelFeatures = new HashSet<>();
    private final boolean isTopLevelFeature;
    private boolean topLevelFeaturesFiltered = false;

    private final static String DERIVES_FROM_ATTRIBUTE_KEY = "Derives_from";
    private static final String ID_ATTRIBUTE_KEY = "ID";
    private static final String NAME_ATTRIBUTE_KEY = "Name";
    private static final String ALIAS_ATTRIBUTE_KEY = "Alias";


    public Gff3Feature(final String contig, final String source, final String type,
                       final int start, final int end, final Strand strand, final int phase,
                       final Map<String, String> attributes) {
        this(contig, source, type, start, end, strand, phase, attributes, Collections.EMPTY_LIST);
    }

    public Gff3Feature(final String contig, final String source, final String type,
                       final int start, final int end, final Strand strand, final int phase,
                       final Map<String, String> attributes, final Collection<Gff3Feature> parents) {
        baseData = new Gff3BaseData(contig, source, type, start, end, strand, phase, attributes);

        final Set<Gff3Feature> modifiableParents = new HashSet<>();
        modifiableParents.addAll(parents);

        this.parents = Collections.unmodifiableSet(modifiableParents);

        this.parents.forEach( p -> {
            topLevelFeatures.addAll(p.getTopLevelFeatures());
            p.addChild(this);
        });

        isTopLevelFeature = topLevelFeatures.isEmpty();

        if(topLevelFeatures.isEmpty()) {
            topLevelFeatures.add(this);
        }
    }

    public Set<Gff3Feature> getTopLevelFeatures() {
        if (!topLevelFeaturesFiltered) {
            if (baseData.attributes.containsKey(DERIVES_FROM_ATTRIBUTE_KEY)) {
                topLevelFeatures.removeIf(f -> !f.getID().equals(baseData.attributes.get(DERIVES_FROM_ATTRIBUTE_KEY)) && f.getDescendents().stream().noneMatch(f2 -> f2.getID().equals(baseData.attributes.get(DERIVES_FROM_ATTRIBUTE_KEY))));
            }
            topLevelFeaturesFiltered = true;
        }
        return topLevelFeatures;
    }

    public boolean isTopLevelFeature() {
        return isTopLevelFeature;
    }

    public String getSource() {
        return baseData.source;
    }

    @Override
    public int getEnd() {
        return baseData.end;
    }

    public Strand getStrand() {
        return baseData.strand;
    }

    public int getPhase() {
        return baseData.phase;
    }

    public String getType() {return baseData.type;}

    @Override
    public String getContig() {
        return baseData.contig;
    }

    @Override
    public int getStart() {
        return baseData.start;
    }

    public String getAttribute(final String key) {
        return baseData.attributes.get(key);
    }

    public Map<String, String> getAttributes() { return baseData.attributes;}

    /**
     * Gets set of parent features
     * @return list of parent features
     */
    public Set<Gff3Feature> getParents() {return parents;}

    /**
     * Gets set of features for which this feature is a parent
     * @return list of child features
     */
    public Set<Gff3Feature> getChildren() {return children;}

    /**
     * Get set of all features this feature descends from, through chains of Parent attributes.  Derives_From can be used to specify a particular inheritance path for this feature when multiple paths are available
     * @return set of ancestor features
     */
    public Set<Gff3Feature> getAncestors() {
        final List<Gff3Feature> ancestors = new ArrayList<>(parents);
        for (final Gff3Feature parent : parents) {
            ancestors.addAll(baseData.attributes.containsKey(DERIVES_FROM_ATTRIBUTE_KEY)? parent.getAncestors(baseData.attributes.get(DERIVES_FROM_ATTRIBUTE_KEY)) : parent.getAncestors());
        }
        return ancestors.stream().collect(Collectors.toSet());
    }

    private Set<Gff3Feature> getAncestors(final String derivingFrom) {
        final List<Gff3Feature> ancestors = new ArrayList<>();
        for (final Gff3Feature parent : parents) {
            if (parent.getID().equals(derivingFrom) || parent.getAncestors().stream().anyMatch(f -> f.getID().equals(derivingFrom))) {
                ancestors.add(parent);
                ancestors.addAll(parent.getAncestors());
            }
        }
        return ancestors.stream().collect(Collectors.toSet());
    }

    /**
     * Get set of all features descended from this features, through chains of Parent attributes.  Derives_From can be used to specify a particular inheritance path for this feature when multiple paths are available
     * @return set of descendents
     */
    public Set<Gff3Feature> getDescendents() {
        final List<Gff3Feature> descendants = new ArrayList<>(children);
        final Set<String> idsInLineage = new HashSet<>(Collections.singleton(baseData.ID));
        idsInLineage.addAll(children.stream().map(Gff3Feature::getID).collect(Collectors.toSet()));
        for(final Gff3Feature child : children) {
            descendants.addAll(child.getDescendents(idsInLineage));
        }
        return descendants.stream().collect(Collectors.toSet());
    }

    private Set<Gff3Feature> getDescendents(final Set<String> idsInLineage) {
        final List<Gff3Feature> decendants = new ArrayList<>();
        final List<Gff3Feature> childrenToAdd = children.stream().filter(c -> c.getAttribute(DERIVES_FROM_ATTRIBUTE_KEY) == null ||
                idsInLineage.contains(c.getAttribute(DERIVES_FROM_ATTRIBUTE_KEY))).
                collect(Collectors.toList());
        decendants.addAll(childrenToAdd);
        idsInLineage.addAll(childrenToAdd.stream().map(Gff3Feature::getID).collect(Collectors.toSet()));
        for (final Gff3Feature child : childrenToAdd) {
            decendants.addAll(child.getDescendents(idsInLineage));
        }
        return decendants.stream().collect(Collectors.toSet());
    }

    /**
     * Get set of co-features.  Co-features correspond to the other lines in the gff file that together make up a single discontinuous feature
     * @return set of co-features
     */
    public Set<Gff3Feature> getCoFeatures() {return coFeatures;}

    public String getID() {return baseData.ID;}

    public boolean hasParents() {return !parents.isEmpty();}

    public boolean hasChildren() {return !children.isEmpty();}


    public boolean hasCoFeatures() {return !coFeatures.isEmpty();}

    private void addChild(final Gff3Feature child) {
        children.add(child);
    }

    /**
     * Add a feature as a coFeature of this feature.  When this method is called, the input coFeature will also be
     * added as a coFeature of all the previous coFeatures of this object, and this feature and all coFeatures will be
     * added as coFeatures of the input coFeature.  All coFeatures must have equal IDs and parents.
     * @param coFeature feature to add as this features coFeature
     */
    public void addCoFeature(final Gff3Feature coFeature) {
        for (final Gff3Feature feature : coFeatures) {
            feature.addCoFeatureShallow(coFeature);
            coFeature.addCoFeatureShallow(feature);
        }
        addCoFeatureShallow(coFeature);
        coFeature.addCoFeatureShallow(this);
    }

    private void addCoFeatureShallow(final Gff3Feature coFeature) {
        coFeatures.add(coFeature);
        if (!coFeature.getID().equals(baseData.ID)) {
            throw new TribbleException("Attempting to add co-feature with ID " + coFeature.getID() + " to feature with ID " + baseData.ID);
        }
        if (!parents.equals(coFeature.getParents())) {

            throw new TribbleException("Co-featrues " + baseData.ID + " do not have same parents");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (!other.getClass().equals(Gff3Feature.class)) {
            return false;
        }
        /* to test for equality, the doubly linked list representation used to represent feature relationships is replaced with a graph representation.
        equality for between two features is tested by testing equality between their base data fields, and equality between the graphs they are part of.
         */
        return baseData.equals(((Gff3Feature) other).baseData) &&
                new Gff3Graph(this).equals(new Gff3Graph((Gff3Feature) other));
    }

    @Override
    public int hashCode() {
        //hash only based on baseData, to keep immutable.
        return baseData.hashCode();
    }





    /***
     * flatten this features and all descendents into a list of features
     * @return list of this feature and all descendents
     */
    public List<Gff3Feature> flatten() {
        final List<Gff3Feature> features = new ArrayList<>(Collections.singleton(this));

        features.addAll(this.getDescendents());
        return features;
    }

    /**
     * Class for storing basic data of a feature
     */
    private class Gff3BaseData {
        private final String contig;
        private final String source;
        private final String type;
        private final int start;
        private final int end;
        private final Strand strand;
        private final int phase;
        private final Map<String, String> attributes;
        private final String ID;
        private final String name;
        private final String alias;

        Gff3BaseData(final String contig, final String source, final String type,
                     final int start, final int end, final Strand strand, final int phase,
                     final Map<String, String> attributes) {
            this.contig = contig;
            this.source = source;
            this.type = type;
            this.start = start;
            this.end = end;
            this.phase = phase;
            this.strand = strand;
            this.attributes = attributes;
            this.ID = attributes.get(ID_ATTRIBUTE_KEY);
            this.name = attributes.get(NAME_ATTRIBUTE_KEY);
            this.alias = attributes.get(ALIAS_ATTRIBUTE_KEY);
        }

        @Override
        public boolean equals(Object other) {
            if(!other.getClass().equals(Gff3BaseData.class)) {
                return false;
            }

            final Gff3BaseData otherBaseData = (Gff3BaseData) other;
            boolean ret = otherBaseData.contig.equals(contig) &&
                    otherBaseData.source.equals(source) &&
                    otherBaseData.type.equals(type) &&
                    otherBaseData.start == start &&
                    otherBaseData.end == end &&
                    otherBaseData.phase == phase &&
                    otherBaseData.strand.equals(strand) &&
                    otherBaseData.attributes.equals(attributes);
            if (ID == null) {
                ret = ret && otherBaseData.ID == null;
            } else {
                ret = ret && otherBaseData.ID != null && otherBaseData.ID.equals(ID);
            }

            if (name == null) {
                ret = ret && otherBaseData.name == null;
            } else {
                ret = ret && otherBaseData.name != null && otherBaseData.name.equals(name);
            }

            if (alias == null) {
                ret = ret && otherBaseData.alias == null;
            } else {
                ret = ret && otherBaseData.alias != null && otherBaseData.alias.equals(alias);
            }

            return ret;
        }

        @Override
        public int hashCode() {
            int hash = contig.hashCode();
            hash = 31 * hash + source.hashCode();
            hash = 31 * hash + type.hashCode();
            hash = 31 * hash + start;
            hash = 31 * hash + end;
            hash = 31 * hash + phase;
            hash = 31 * hash + strand.hashCode();
            hash = 31 * hash + attributes.hashCode();
            if (ID != null) {
                hash = 31 * hash + ID.hashCode();
            }

            return hash;
        }
    }

    /**
     * Class for graph representation of relationships between features.
     * Used for testing equality between features
     */
    private class Gff3Graph {
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
            nodes.add(feature.baseData);
        }

        private void addParentEdges(final Gff3Feature feature) {
            for(final Gff3Feature parent : feature.getParents()) {
                parentEdges.add(new Tuple(feature.baseData, parent.baseData));
            }
        }

        private void addChildEdges(final Gff3Feature feature) {
            for(final Gff3Feature child : feature.getChildren()) {
                childEdges.add(new Tuple(feature.baseData, child.baseData));
            }
        }

        private void addCoFeatureSet(final Gff3Feature feature) {
            if (feature.hasCoFeatures()) {
                final Set<Gff3BaseData> coFeaturesBaseData = feature.getCoFeatures().stream().map(f -> f.baseData).collect(Collectors.toSet());
                coFeaturesBaseData.add(feature.baseData);
                coFeatureSets.add(coFeaturesBaseData);
            }
        }

        @Override
        public boolean equals(Object other) {
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