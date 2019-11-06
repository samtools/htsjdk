package htsjdk.tribble.gff;

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
    private final List<Gff3Feature> parents = new ArrayList<>();
    private final List<Gff3Feature> children = new ArrayList<>();
    private final List<Gff3Feature> coFeatures = new ArrayList<>();
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
     * Gets list of parent features
     * @return list of parent features
     */
    public List<Gff3Feature> getParents() {return parents;}

    /**
     * Gets list of features for which this feature is a parent
     * @return list of child features
     */
    public List<Gff3Feature> getChildren() {return children;}

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
     * Get list of co-features.  Co-features correspond to the other lines in the gff file that together make up a single discontinuous feature
     * @return list of co-features
     */
    public List<Gff3Feature> getCoFeatures() {return coFeatures;}

    public String getID() {return ID;}

    public boolean hasParents() {return !parents.isEmpty();}

    public boolean hasChildren() {return !children.isEmpty();}


    public boolean hasCoFeatures() {return !coFeatures.isEmpty();}


    public void addParent(final Gff3Feature parent) {
        parents.add(parent);
    }

    public void addChild(final Gff3Feature child) {children.add(child);}

    public void addCoFeature(final Gff3Feature coFeature) {coFeatures.add(coFeature);}

    @Override
    public boolean equals(Object other) {
        if (!other.getClass().equals(Gff3Feature.class)) {
            return false;
        }

        final Gff3Feature otherGff3Feature = (Gff3Feature) other;

        return otherGff3Feature.getContig().equals(contig) &&
                otherGff3Feature.getSource().equals(source) &&
                otherGff3Feature.getType().equals(type) &&
                otherGff3Feature.getStart() == start &&
                otherGff3Feature.getEnd()== end &&
                otherGff3Feature.getStrand() == strand &&
                otherGff3Feature.getPhase() == phase &&
                otherGff3Feature.getAttributes().equals(attributes) &&
                otherGff3Feature.getParents().equals(parents) &&
                otherGff3Feature.getChildren().equals(children) &&
                otherGff3Feature.getCoFeatures().equals(coFeatures);
    }

    @Override
    public int hashCode() {
        return shallowHashCode() +
                children.stream().map(Gff3Feature::shallowHashCode).reduce(0, Integer::sum) +
                parents.stream().map(Gff3Feature::shallowHashCode).reduce(0, Integer::sum) +
                coFeatures.stream().map(Gff3Feature::shallowHashCode).reduce(0, Integer::sum);
    }

    private int shallowHashCode() {
        return contig.hashCode() + source.hashCode() + type.hashCode() + start + 31*end +
                strand.hashCode() + phase*(31^2) + attributes.hashCode();
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