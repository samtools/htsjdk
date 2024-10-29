package htsjdk.tribble.gff;

import htsjdk.samtools.util.Locatable;
import htsjdk.tribble.annotation.Strand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Gff3BaseData implements Locatable {
    private final String contig;
    private final String source;
    private final String type;
    private final int start;
    private final int end;
    private final double score;
    private final Strand strand;
    private final int phase;
    private final Map<String, List<String>> attributes;
    private final String id;
    private final String name;
    private final List<String> aliases;
    private final int hashCode;

    public Gff3BaseData(final String contig, final String source, final String type,
                 final int start, final int end, final Double score, final Strand strand, final int phase,
                 final Map<String, List<String>> attributes) {
        this.contig = contig;
        this.source = source;
        this.type = type;
        this.start = start;
        this.end = end;
        this.score = score;
        this.phase = phase;
        this.strand = strand;
        this.attributes = copyAttributesSafely(attributes);
        this.id = Gff3Codec.extractSingleAttribute(attributes.get(Gff3Constants.ID_ATTRIBUTE_KEY));
        this.name = Gff3Codec.extractSingleAttribute(attributes.get(Gff3Constants.NAME_ATTRIBUTE_KEY));
        this.aliases = attributes.getOrDefault(Gff3Constants.ALIAS_ATTRIBUTE_KEY, Collections.emptyList());
        this.hashCode = computeHashCode();
    }

    private static Map<String, List<String>> copyAttributesSafely(final Map<String, List<String>> attributes) {
        final Map<String, List<String>> modifiableDeepMap = new LinkedHashMap<>();

        for (final Map.Entry<String, List<String>> entry : attributes.entrySet()) {
            final List<String> unmodifiableDeepList = Collections.unmodifiableList(new ArrayList<>(entry.getValue()));
            modifiableDeepMap.put(entry.getKey(), unmodifiableDeepList);
        }

        return Collections.unmodifiableMap(modifiableDeepMap);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if(!other.getClass().equals(Gff3BaseData.class)) {
            return false;
        }

        final Gff3BaseData otherBaseData = (Gff3BaseData) other;
        boolean ret = otherBaseData.getContig().equals(getContig()) &&
                otherBaseData.getSource().equals(getSource()) &&
                otherBaseData.getType().equals(getType()) &&
                otherBaseData.getStart() == getStart() &&
                otherBaseData.getEnd() == getEnd() &&
                ((Double)otherBaseData.getScore()).equals(score) &&
                otherBaseData.getPhase() == getPhase() &&
                otherBaseData.getStrand().equals(getStrand()) &&
                otherBaseData.getAttributes().equals(getAttributes());
        if (getId() == null) {
            ret = ret && otherBaseData.getId() == null;
        } else {
            ret = ret && otherBaseData.getId() != null && otherBaseData.getId().equals(getId());
        }

        if (getName() == null) {
            ret = ret && otherBaseData.getName() == null;
        } else {
            ret = ret && otherBaseData.getName() != null && otherBaseData.getName().equals(getName());
        }

        ret = ret && otherBaseData.getAliases().equals(getAliases());

        return ret;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int computeHashCode() {
        int hash = getContig().hashCode();
        hash = 31 * hash + getSource().hashCode();
        hash = 31 * hash + getType().hashCode();
        hash = 31 * hash + getStart();
        hash = 31 * hash + getEnd();
        hash = 31 * hash + Double.hashCode(getScore());
        hash = 31 * hash + getPhase();
        hash = 31 * hash + getStrand().hashCode();
        hash = 31 * hash + getAttributes().hashCode();
        if (getId() != null) {
            hash = 31 * hash + getId().hashCode();
        }

        if (getName() != null) {
            hash = 31 * hash + getName().hashCode();
        }

        hash = 31 * hash + aliases.hashCode();

        return hash;
    }

    @Override
    public String getContig() {
        return contig;
    }

    public String getSource() {
        return source;
    }

    public String getType() {
        return type;
    }

    @Override
    public int getStart() {
        return start;
    }

    @Override
    public int getEnd() {
        return end;
    }

    public double getScore() {
        return score;
    }

    public Strand getStrand() {
        return strand;
    }

    public int getPhase() {
        return phase;
    }

    public Map<String, List<String>> getAttributes() {
        return attributes;
    }

    /** 
     * get the values as List for the <tt>key</tt>, or an empty list if this <tt>key</tt> is not present
     * 
     * @param key key whose presence in this map is to be tested
     * @return the values as List, or an empty list if this key is not present
     */
    public List<String> getAttribute(final String key) {
        return attributes.getOrDefault(key, Collections.emptyList());
    }

    /**
     * Returns <tt>true</tt> if this record contains an attribute for the specified key.
     * 
     * @param key key whose presence in this map is to be tested
     * @return <tt>true</tt> if this map contains an attribute for the specified key
     */
    public boolean hasAttribute(final String key) {
        return attributes.containsKey(key);
    }
    
    /**
     * Most attributes in a GFF file are present just one time in a line, e.g. : <tt>gene_biotype</tt>,  <tt>gene_name</tt>, etc ...  
     * This function returns an <tt>Optional.empty</tt> if the <tt>key</tt> is not present,
     *  an <tt>Optional.of(value)</tt> if there is only one value associated to the <tt>key</tt>,
     *  or it throws an <tt>IllegalArgumentException</tt> if there is more than one value.
     * 
     * @param key key whose presence in the attributes is to be tested
     * @return <tt>Optional&lt;String&gt;</tt> if this map contains zero or one attribute for the specified key
     * @throws IllegalArgumentException if there is more than one value
     */
    public Optional<String> getUniqueAttribute(final String key) {
        final List<String> atts = getAttribute(key);
        switch(atts.size()) {
            case 0 : return Optional.empty();
            case 1 : return Optional.of(atts.get(0));
            default : throw new IllegalArgumentException("getUniqueAttribute cannot be called with key="+key+" because it contains more than one value " + String.join(", ", atts));
        }
    }
    
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getAliases() {
        return aliases;
    }
}
