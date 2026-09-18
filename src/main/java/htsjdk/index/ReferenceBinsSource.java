package htsjdk.index;

import java.util.OptionalLong;

/**
 * A binning index as its readers see it: a binning scheme and, per reference, that reference's bins, however and
 * whenever they come to be in memory. {@link BinningIndex} holds them all; {@link FileBackedBinningIndex} fetches
 * each from the index file when it is first asked for.
 */
public interface ReferenceBinsSource extends HtsQueryIndex {
    /** @return log2 of the span of the smallest bins */
    int getMinShift();

    /** @return the number of bin levels above the smallest bins */
    int getDepth();

    /** @return the number of references the index covers, with or without records */
    int getReferenceCount();

    /**
     * @param referenceIndex ordinal of the reference
     * @return the reference's bins; never null, but {@link ReferenceBins#isEmpty() empty} if nothing is indexed
     *     on the reference
     */
    ReferenceBins getReference(int referenceIndex);

    /** @return the number of records that have no position, if the index records it */
    OptionalLong getNoCoordinateCount();

    /** @return the whole index in memory; this index itself if that is what it already is */
    BinningIndex loadAll();
}
