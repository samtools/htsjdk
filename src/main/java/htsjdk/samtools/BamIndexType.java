package htsjdk.samtools;

import htsjdk.index.BinningIndex;

/** The kind of index to write for a BAM file. */
public enum BamIndexType {
    /** A {@code .bai}: the fixed binning scheme, which cannot address a position beyond 2^29 (about 512 Mbp). */
    BAI,
    /** A {@code .csi}: stores its binning scheme, so it reaches as far as the longest sequence needs. */
    CSI,
    /** {@link #BAI} if every sequence in the header fits one, otherwise {@link #CSI}. */
    AUTO;

    /**
     * @param dictionary the sequences of the file to be indexed
     * @return the type that will be written for such a file: this type, or for {@link #AUTO} whichever of
     *     {@link #BAI} and {@link #CSI} the dictionary calls for
     */
    public BamIndexType resolve(final SAMSequenceDictionary dictionary) {
        if (this != AUTO) {
            return this;
        }
        final long baiReach = 1L << (BinningIndex.BAI_MIN_SHIFT + 3 * BinningIndex.BAI_DEPTH);
        return longestSequence(dictionary) > baiReach ? CSI : BAI;
    }

    /** The length of the dictionary's longest sequence, or 0 if it has none. */
    static long longestSequence(final SAMSequenceDictionary dictionary) {
        return dictionary.getSequences().stream()
                .mapToLong(SAMSequenceRecord::getSequenceLength)
                .max()
                .orElse(0);
    }
}
