package htsjdk.tribble.gff;

import htsjdk.tribble.annotation.Strand;

import java.util.Collections;
import java.util.Map;

public class Gff3BaseData {
    private static final String ID_ATTRIBUTE_KEY = "ID";
    private static final String NAME_ATTRIBUTE_KEY = "Name";
    private static final String ALIAS_ATTRIBUTE_KEY = "Alias";
    final String contig;
    final String source;
    final String type;
    final int start;
    final int end;
    final Strand strand;
    final int phase;
    final Map<String, String> attributes;
    final String id;
    final String name;
    final String alias;
    final int hashCode;

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
        this.attributes = Collections.unmodifiableMap(attributes);
        this.id = attributes.get(ID_ATTRIBUTE_KEY);
        this.name = attributes.get(NAME_ATTRIBUTE_KEY);
        this.alias = attributes.get(ALIAS_ATTRIBUTE_KEY);
        hashCode = computeHashCode();
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
        boolean ret = otherBaseData.contig.equals(contig) &&
                otherBaseData.source.equals(source) &&
                otherBaseData.type.equals(type) &&
                otherBaseData.start == start &&
                otherBaseData.end == end &&
                otherBaseData.phase == phase &&
                otherBaseData.strand.equals(strand) &&
                otherBaseData.attributes.equals(attributes);
        if (id == null) {
            ret = ret && otherBaseData.id == null;
        } else {
            ret = ret && otherBaseData.id != null && otherBaseData.id.equals(id);
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
        return hashCode;
    }

    private int computeHashCode() {
        int hash = contig.hashCode();
        hash = 31 * hash + source.hashCode();
        hash = 31 * hash + type.hashCode();
        hash = 31 * hash + start;
        hash = 31 * hash + end;
        hash = 31 * hash + phase;
        hash = 31 * hash + strand.hashCode();
        hash = 31 * hash + attributes.hashCode();
        if (id != null) {
            hash = 31 * hash + id.hashCode();
        }

        if (name != null) {
            hash = 31 * hash + name.hashCode();
        }

        if (alias != null) {
            hash = 31 * hash + alias.hashCode();
        }

        return hash;
    }
}
