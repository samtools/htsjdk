package htsjdk.tribble.bed;

/**
 * Representation of some subsequence of Feature which can be exon or intron. But in fact we don't care about second one for now.
 */
public interface FeatureSubSequence {
    void setMrnaBase(int base);

    /**
     * Flag indicating that the entire exon is the UTR.
     *
     * @param utr
     */
    void setUTR(boolean utr);

    void setCodingStart(int codingStart);

    void setCodingEnd(int codingEnd);

    void setReadingFrame(int offset);

    void setPhase(int phase);

    int getCdStart();

    int getCdEnd();

    int getCodingLength();

    String getValueString(double position);

    int getNumber();

    void setNumber(int number);
}
