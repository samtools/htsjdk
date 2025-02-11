package htsjdk.samtools.cram.compression.fqzcomp;

import htsjdk.samtools.cram.structure.CRAMEncodingStrategy;

public class FQZState {
    private int qualityContext;     // Qual-only sub-context
    private int previousQuality;    // Previous quality value
    private int delta;              // Running delta (quality vs previousQuality)
    private int bases;              // (p) Number of bases left in current record
    private int selector;           // (s) Current parameter selector value (0 if unused)
    private int selectorTable;      // (x) "stab" tabulated copy of s
    private int recordLength;       // (len) Length of current string
    private boolean isDuplicate;    // This string is a duplicate of last
    private int readOrdinal;        // read ordinal within the enclosing context(/slice)

    //the htslib C code uses 1000 to start, but that seems crazy low, since the default container size is 10,000
    int nReads = CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE; // start out with room for 10000 reads, realloc as needed
    int[] qualityLengths = new int[nReads];
    boolean[] readsToReverse = new boolean[nReads];
    private int context;

    public int getContext() { return context;}
    public void setContext(final int context) { this.context = context; }

    public int getQualityContext() { return qualityContext; }
    public void setQualityContext(int qualityContext) { this.qualityContext = qualityContext; }

    public int getPreviousQuality() { return previousQuality; }
    public void setPreviousQuality(int previousQuality) { this.previousQuality = previousQuality; }

    public int getDelta() { return delta; }
    public void setDelta(int delta) { this.delta = delta;}

    public int getBases() { return bases; }
    public void setBases(int bases) { this.bases = bases; }

    public int getSelector() { return selector; }
    public void setSelector(int selector) { this.selector = selector; }

    public int getSelectorTable() { return selectorTable; }
    public void setSelectorTable(int selectorTable) { this.selectorTable = selectorTable; }

    public int getRecordLength() { return recordLength; }
    public void setRecordLength(int recordLength) { this.recordLength = recordLength; }

    public boolean getIsDuplicate() { return isDuplicate; }
    public void setIsDuplicate(boolean isDuplicate) { this.isDuplicate = isDuplicate; }

    public int getReadOrdinal() { return readOrdinal; }
    public void setReadOrdinal(int readOrdinal) { this.readOrdinal = readOrdinal; }

    public int[] getQualityLengthArray() {
        return qualityLengths;
    }
    public boolean[] getReverseArray() { return readsToReverse; }

    void resizeArrays(int numReadsSeen) {
        if (numReadsSeen >= nReads) {
            final int oldnRec = nReads;
            nReads *= 2;
            final int[] newQual = new int[nReads];
            final boolean[] newRev = new boolean[nReads];
            for (int j = 0; j < oldnRec; j++) {
                newQual[j] = qualityLengths[j];
                newRev[j] = readsToReverse[j];
            }
            readsToReverse = newRev;
            qualityLengths = newQual;
        }
    }

}