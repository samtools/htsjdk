package htsjdk.samtools.cram.compression.fqzcomp;

public class FQZState {
    private int qualityContext;    // Qual-only sub-context
    private int previousQuality;   // Previous quality value
    private int delta;   // Running delta (quality vs previousQuality)
    private int bases;       // Number of bases left in current record
    private int selector;       // Current parameter selector value (0 if unused)
    private int selectorTable;       // "stab" tabulated copy of s
    private int recordLength;     // Length of current string
    private boolean isDuplicate;   // This string is a duplicate of last
    private int recordNumber;     // Record number

    public FQZState() {
    }

    public int getQualityContext() {
        return qualityContext;
    }

    public void setQualityContext(int qualityContext) {
        this.qualityContext = qualityContext;
    }

    public int getPreviousQuality() {
        return previousQuality;
    }

    public void setPreviousQuality(int previousQuality) {
        this.previousQuality = previousQuality;
    }

    public int getDelta() {
        return delta;
    }

    public void setDelta(int delta) {
        this.delta = delta;
    }

    public int getBases() {
        return bases;
    }

    public void setBases(int bases) {
        this.bases = bases;
    }

    public int getSelector() {
        return selector;
    }

    public void setSelector(int selector) {
        this.selector = selector;
    }

    public int getSelectorTable() {
        return selectorTable;
    }

    public void setSelectorTable(int selectorTable) {
        this.selectorTable = selectorTable;
    }

    public int getRecordLength() {
        return recordLength;
    }

    public void setRecordLength(int recordLength) {
        this.recordLength = recordLength;
    }

    public boolean getIsDuplicate() {
        return isDuplicate;
    }

    public void setIsDuplicate(boolean isDuplicate) {
        this.isDuplicate = isDuplicate;
    }

    public int getRecordNumber() {
        return recordNumber;
    }

    public void setRecordNumber(int recordNumber) {
        this.recordNumber = recordNumber;
    }
}