package htsjdk.tribble.gff;

import htsjdk.samtools.util.Locatable;

/**
 * Represents a sequence region feature in a gff3 file.  May be linear or circular.
 */
public class SequenceRegion implements Locatable {
    private final int start;
    private final int end;
    private final String contig;
    private Boolean isCircular;
    private int hashCode;

    SequenceRegion(final String contig, final int start, final int end) {
        this(contig, start, end, false);
    }

    SequenceRegion(final String contig, final int start, final int end, final boolean isCircular) {
        this.contig = contig;
        this.start = start;
        this.end = end;
        this.isCircular = isCircular;
        hashCode = computeHashCode();
    }

    void setCircular(final boolean isCircular) {
        this.isCircular = isCircular;
        hashCode = computeHashCode();
    }

    void setCircular() {
        setCircular(true);
    }

    @Override
    public String getContig(){return contig;}

    @Override
    public int getStart(){return start;}

    @Override
    public int getEnd(){return end;}

    public boolean isCircular(){return  isCircular;}

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (!(other instanceof SequenceRegion)) {
            return false;
        }

        final SequenceRegion otherSequenceRegion = (SequenceRegion) other;
        return otherSequenceRegion.start == start && otherSequenceRegion.end==end && otherSequenceRegion.contig.equals(contig) && otherSequenceRegion.isCircular == isCircular;
    }

    private int computeHashCode() {
        int hash = contig.hashCode();
        hash = 31 * hash + start;
        hash = 31 * hash + end;
        hash = 31 * hash + isCircular.hashCode();
        return hash;
    }

    @Override
    public int hashCode() { return hashCode;}
}
