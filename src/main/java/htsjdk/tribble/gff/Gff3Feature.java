package htsjdk.tribble.gff;

import htsjdk.samtools.util.Tuple;
import htsjdk.tribble.Feature;
import htsjdk.tribble.annotation.Strand;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Gff3 format spec is defined at https://github.com/The-Sequence-Ontology/Specifications/blob/master/gff3.md
 * Discontinous features which are split between multiple lines in the gff files are implemented as separate features linked as "co-features"
 */
public class Gff3Feature implements Feature {

    private final String contig;
    private final String source;
    private final String type;
    private final int start;
    private final int end;
    private final Strand strand;
    private final int phase;
    private final Map<String, String> attributes;
    private final HashSet<Gff3Feature> parents = new HashSet<>();
    private final HashSet<Gff3Feature> children = new HashSet<>();
    private final HashSet<Gff3Feature> coFeatures = new HashSet<>();
    private final String ID;

    private final static String DERIVES_FROM_ATTRIBUTE_KEY = "Derives_from";
    private static final String ID_ATTRIBUTE_KEY = "ID";

    public Gff3Feature(final String contig, final String source, final String type,
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
    }

    public String getSource() {
        return source;
    }

    @Override
    public int getEnd() {
        return end;
    }

    public Strand getStrand() {
        return strand;
    }

    public int getPhase() {
        return phase;
    }

    public String getType() {return type;}

    @Override
    public String getContig() {
        return contig;
    }

    @Override
    public int getStart() {
        return start;
    }

    public String getAttribute(final String key) {
        return attributes.get(key);
    }

    public Map<String, String> getAttributes() { return attributes;}

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
     * Get list of all features this feature descends from, through chains of Parent attributes.  Derives_From can be used to specify a particular inheritance path for this feature when multiple paths are available
     * @return list of ancestor features
     */
    public List<Gff3Feature> getAncestors() {
        final List<Gff3Feature> ancestors = new ArrayList<>(parents);
        for (final Gff3Feature parent : parents) {
            ancestors.addAll(attributes.containsKey(DERIVES_FROM_ATTRIBUTE_KEY)? parent.getAncestors(attributes.get(DERIVES_FROM_ATTRIBUTE_KEY)) : parent.getAncestors());
        }
        return ancestors.stream().distinct().collect(Collectors.toList());
    }

    private List<Gff3Feature> getAncestors(final String derivingFrom) {
        final List<Gff3Feature> ancestors = new ArrayList<>();
        for (final Gff3Feature parent : parents) {
            if (parent.getID().equals(derivingFrom) || parent.getAncestors().stream().anyMatch(f -> f.getID().equals(derivingFrom))) {
                ancestors.add(parent);
                ancestors.addAll(parent.getAncestors());
            }
        }
        return ancestors.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Get list of all features descended from this features, through chains of Parent attributes.  Derives_From can be used to specify a particular inheritance path for this feature when multiple paths are available
     * @return list of descendents
     */
    public List<Gff3Feature> getDescendents() {
        final List<Gff3Feature> descendants = new ArrayList<>(children);
        final Set<String> idsInLineage = new HashSet<>(Collections.singleton(ID));
        idsInLineage.addAll(children.stream().map(Gff3Feature::getID).collect(Collectors.toSet()));
        for(final Gff3Feature child : children) {
            descendants.addAll(child.getDescendents(idsInLineage));
        }
        return descendants.stream().distinct().collect(Collectors.toList());
    }

    private List<Gff3Feature> getDescendents(final Set<String> idsInLineage) {
        final List<Gff3Feature> decendants = new ArrayList<>();
        final List<Gff3Feature> childrenToAdd = children.stream().filter(c -> c.getAttribute(DERIVES_FROM_ATTRIBUTE_KEY) == null ||
                idsInLineage.contains(c.getAttribute(DERIVES_FROM_ATTRIBUTE_KEY))).
                collect(Collectors.toList());
        decendants.addAll(childrenToAdd);
        idsInLineage.addAll(childrenToAdd.stream().map(Gff3Feature::getID).collect(Collectors.toSet()));
        for (final Gff3Feature child : childrenToAdd) {
            decendants.addAll(child.getDescendents(idsInLineage));
        }
        return decendants.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Get set of co-features.  Co-features correspond to the other lines in the gff file that together make up a single discontinuous feature
     * @return list of co-features
     */
    public Set<Gff3Feature> getCoFeatures() {return coFeatures;}

    public String getID() {return ID;}

    public boolean hasParents() {return !parents.isEmpty();}

    public boolean hasChildren() {return !children.isEmpty();}


    public boolean hasCoFeatures() {return !coFeatures.isEmpty();}


    public void addParent(final Gff3Feature parent) {
        parents.add(parent);
        parent.addChildShallow(this);
    }

    public void addChild(final Gff3Feature child) {
        children.add(child);
        child.addParentShallow(this);
    }

    private void addParentShallow(final Gff3Feature parent) {
        parents.add(parent);
    }

    private void addChildShallow(final Gff3Feature child) { children.add(child); }

    public void addCoFeature(final Gff3Feature coFeature) {
        coFeatures.add(coFeature);
        coFeature.addCoFeatureShallow(coFeature);
    }

    private void addCoFeatureShallow(final Gff3Feature coFeatuure) { coFeatures.add(coFeatuure); }

    @Override
    public boolean equals(Object other) {
        if (!other.getClass().equals(Gff3Feature.class)) {
            return false;
        }

        final Gff3Feature otherGff3Feature = (Gff3Feature) other;

        final Set<Gff3Feature> topLevelFeatures = getAncestors().stream().filter(f -> !f.hasParents()).collect(Collectors.toSet());
        final Set<Gff3Feature> allNodes = topLevelFeatures.stream().flatMap(f -> f.flatten().stream().map(Gff3Feature::shallowCopy)).collect(Collectors.toSet());
        final Set<List<Gff3Feature>> allChildEdges = allNodes.stream()
                .flatMap(f ->f.getChildren().stream().map(c -> Arrays.asList(f.shallowCopy(), c.shallowCopy()))).collect(Collectors.toSet());
        final Set<List<Gff3Feature>> allParentEdges = allNodes.stream()
                .flatMap(f ->f.getParents().stream().map(p -> Arrays.asList(f.shallowCopy(), p.shallowCopy()))).collect(Collectors.toSet());
        final Set<Set>

        boolean ret = otherGff3Feature.getContig().equals(contig) &&
                otherGff3Feature.getSource().equals(source) &&
                otherGff3Feature.getType().equals(type) &&
                otherGff3Feature.getStart() == start &&
                otherGff3Feature.getEnd()== end &&
                otherGff3Feature.getStrand() == strand &&
                otherGff3Feature.getPhase() == phase &&
                otherGff3Feature.getAttributes().equals(attributes);
        for (final Gff3Feature parent : otherGff3Feature)
        // &&
//                otherGff3Feature.getParents().equals(parents) &&
//                otherGff3Feature.getChildren().equals(children) &&
//                otherGff3Feature.getCoFeatures().equals(coFeatures);
    }


    // graph representation, for
    private boolean upwardsEquals(Gff3Feature other) {
        boolean ret = shallowEquals(other);

        ret &= getParents().size() == other.getParents().size();

        if (ret) {
            //construct hashmap between shallow copies of parents and parents
            final HashMap<Gff3Feature, Gff3Feature> shallowCopyMap = getParents().stream().collect(Collectors.toMap(f -> f.shallowCopy()))
        }
    }

    private Gff3Feature shallowCopy() {
        return new Gff3Feature(contig, source, type, start, end, strand, phase, attributes);
    }

    public boolean shallowEquals(final Gff3Feature other) {
        return other.getContig().equals(contig) &&
                other.getSource().equals(source) &&
                other.getType().equals(type) &&
                other.getStart() == start &&
                other.getEnd()== end &&
                other.getStrand() == strand &&
                other.getPhase() == phase &&
                other.getAttributes().equals(attributes);
    }

    @Override
    public int hashCode() {
        int hash = verticalHashCode();
        hash = 31 * hash + coFeatures.stream().map(Gff3Feature::verticalHashCode).reduce(0, Integer::sum);
        return hash;
    }

    public int shallowHashCode() {
        int hash = contig.hashCode();
        hash = 31 * hash + source.hashCode();
        hash = 31 * hash + type.hashCode();
        hash += start;
        hash = 31 * hash + end;
        hash = 31 * hash + phase;
        hash += strand.hashCode();
        hash += attributes.hashCode();
        return hash;
    }

    public int upwardHashCode() {
        int hash = shallowHashCode();
        hash = 31 * hash + parents.stream().map(Gff3Feature::upwardHashCode).reduce(0, Integer::sum);
        return hash;
    }

    public int downwardHashCode() {
        int hash = shallowHashCode();
        hash = 31 * hash + children.stream().map(Gff3Feature::downwardHashCode).reduce(0, Integer::sum);
        return hash;
    }

    public int verticalHashCode() {
        int hash = shallowHashCode();
        hash = 31 * hash + parents.stream().map(Gff3Feature::upwardHashCode).reduce(0, Integer::sum);
        hash = 31 * hash + children.stream().map(Gff3Feature::downwardHashCode).reduce(0, Integer::sum);
        return hash;
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
}