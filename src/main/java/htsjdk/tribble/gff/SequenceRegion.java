package htsjdk.tribble.gff;

import htsjdk.samtools.util.Locatable;

public class SequenceRegion implements Locatable {
    private final int start;
    private final int end;
    private final String contig;
    private boolean isCircular;

    SequenceRegion(final String contig, final int start, final int end) {
        this(contig, start, end, false);
    }

    SequenceRegion(final String contig, final int start, final int end, final boolean isCircular) {
        this.contig = contig;
        this.start = start;
        this.end = end;
        this.isCircular = isCircular;
    }

    void setCircular(final boolean isCircular) {
        this.isCircular = isCircular;
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

    public boolean equals(final SequenceRegion other) {
        return other.start == start && other.end==end && other.contig.equals(contig) && other.isCircular == isCircular;
    }


}
