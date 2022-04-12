/*
 * Copyright (c) 2019 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.util.Log;
import htsjdk.utils.ValidationUtils;

/**
 * Holds a region/fragment of a reference contig. Maintains a CRAMReferenceSource for retrieving additional regions.
 * This is a mutable object that is used to traverse along a reference contig via serial calls to either
 * {@link #getReferenceBases(int)} or {@link #getReferenceBasesByRegion(int, int, int)}. It caches the bases
 * from the previous request, along with metadata about the (0-based) position/offset, and length of the
 * cached bases.
 */
public class CRAMReferenceRegion {
    private static final Log log = Log.getInstance(CRAMReferenceRegion.class);
    public static final int UNINITIALIZED_OFFSET = -1;
    public static final int  UNINITIALIZED_LENGTH = -1;

    private final CRAMReferenceSource referenceSource;
    private final SAMFileHeader samFileHeader;

    private int referenceBasesContextID = ReferenceContext.UNINITIALIZED_REFERENCE_ID;
    private byte[] referenceBases = null;
    private SAMSequenceRecord sequenceRecord = null;
    private int regionOffset = UNINITIALIZED_OFFSET; // 0-based start offset of the region
    private int regionLength = UNINITIALIZED_LENGTH; // length of the bases cached by this reference region

    /**
     * @param cramReferenceSource {@link CRAMReferenceSource} to use to obtain reference bases
     * @param samFileHeader {@link SAMFileHeader} to use to resolve reference contig names to reference index
     */
    public CRAMReferenceRegion(final CRAMReferenceSource cramReferenceSource, final SAMFileHeader samFileHeader) {
        ValidationUtils.nonNull(cramReferenceSource, "cramReferenceSource");
        ValidationUtils.nonNull(samFileHeader, "samFileHeader");

        this.referenceSource = cramReferenceSource;
        this.samFileHeader = samFileHeader;
    }

    /**
     * @return the currently cached reference bases (may return null)
     */
    public byte[] getCurrentReferenceBases() {
        return referenceBases;
    }

    /**
     * @return the current reference index ({@link ReferenceContext#UNINITIALIZED_REFERENCE_ID} if no index has
     * been established
     */
    public int getReferenceIndex() {
        return referenceBasesContextID;
    }

    /**
     * @return the 0-based start position of the range of the current reference sequence region.
     * {@link #UNINITIALIZED_OFFSET} if no region has been established
     */
    public int getRegionOffset() {
        return regionOffset;
    }

    /**
     * @return the length of the current reference sequence region. {@link #UNINITIALIZED_LENGTH}
     * if no region has been established
     */
    public int getRegionLength() {
        return regionLength;
    }

    /**
     * Return the reference bases for an entire contig given a reference contig index.
     *
     * @param referenceIndex the reference index for which bases should be retrieved
     * @return bases for the entire reference contig specified by {@code referenceIndex}, may be null if
     * the specified referenceIndex cannot be resolved in this region's sequence dictionary
     * @throws IllegalArgumentException if the requested index is not present in the sequence dictionary
     */
    public byte[] getReferenceBases(final int referenceIndex) {
        ValidationUtils.validateArg(referenceIndex >= 0, "reference index must be >= 0");

        // Re-resolve our the reference bases if we don't have a current region or the one we have
        // doesn't span the *entire* contig requested. When called on behalf of non-coord sorted inputs,
        // this can cause a lot of thrashing.
        if ((referenceIndex != referenceBasesContextID) ||
                regionOffset != 0 ||
                (regionLength != referenceBases.length)) {
            sequenceRecord = getSAMSequenceRecord(referenceIndex);
            if (sequenceRecord == null) {
                throw new IllegalArgumentException(
                        String.format("A reference must be supplied (reference sequence %s not found).", sequenceRecord));
            }
            referenceBases = referenceSource.getReferenceBases(sequenceRecord, true);
            if (referenceBases == null) {
                throw new IllegalArgumentException(
                        String.format("A reference must be supplied (reference sequence %s not found).", sequenceRecord));
            }
            referenceBasesContextID = referenceIndex;
            regionOffset = 0;
            regionLength = sequenceRecord.getSequenceLength();
        }
        return referenceBases;
    }

    /**
     * Get the reference bases for a region of a reference contig. If the current region does not match the
     * requested region, the {@link #referenceSource} will be called to retrieve the bases.
     *
     * The caller cannot assume that the entire region requested is returned (the region may not span the
     * entire requested region if the requested length extends beyond the end of the actual reference sequence).
     *
     * @param referenceIndex reference index for which to retrieve bases
     * @param zeroBasedOffset zero based offset of the first base to be retrieved
     * @param requestedFragmentLength length of the fragment to be retrieved
     * @return the reference bases requested. note that it is possible that fewer bases than requested
     * are returned (it is possible for some cram records to be mapped beyond the end of the contig; in
     * which case fewer bases may be returned).
     *
     * @throws IllegalArgumentException if the requested sequence cannot be located in the sequence dictionary, or
     * if the contig cannot be provided by the underlying referenceSource
     */
    public byte[] getReferenceBasesByRegion(
            final int referenceIndex,
            final int zeroBasedOffset,
            final int requestedFragmentLength) {
        ValidationUtils.validateArg(referenceIndex >= 0, "reference index must be non-negative");
        ValidationUtils.validateArg(zeroBasedOffset >= 0, "offset must be >= 0");

        if (referenceIndex == referenceBasesContextID &&
                zeroBasedOffset == regionOffset &&
                requestedFragmentLength == regionLength) {
            // exact match for what we already have
            return referenceBases;
        }

        if (referenceIndex != referenceBasesContextID) {
            sequenceRecord = getSAMSequenceRecord(referenceIndex);
        }

        if ((zeroBasedOffset + requestedFragmentLength) > sequenceRecord.getSequenceLength()) {
            log.warn("Attempt to retrieve a reference fragment (start: " + zeroBasedOffset + " length: "
                    + requestedFragmentLength + ") that extends beyond the end of the reference contig length: "
                    + sequenceRecord.getSequenceLength());
        }

        referenceBases = referenceSource.getReferenceBasesByRegion(sequenceRecord, zeroBasedOffset, requestedFragmentLength);
        if (referenceBases == null) {
            throw new IllegalArgumentException(
                    String.format("Failure getting reference bases for sequence %s", sequenceRecord.getSequenceName()));
        } else if (referenceBases.length < requestedFragmentLength) {
            log.warn("The bases of length " + referenceBases.length +
                    " returned by the reference source do not satisfy the requested fragment length " +
                    zeroBasedOffset + requestedFragmentLength);
        }
        referenceBasesContextID = referenceIndex;
        regionOffset = zeroBasedOffset;
        regionLength = referenceBases.length;
        return referenceBases;
    }

    /**
     * Set this {@link CRAMReferenceRegion} to use an embedded reference.
     *
     * @param embeddedReferenceBases the embedded reference bases to be used
     * @param embeddedReferenceIndex the reference ID used in the slice containing the embedded reference
     * @param zeroBasedOffset the zero based reference offset of the first base in the embedded reference bases
     */
    public void setEmbeddedReferenceBases(
            final byte[] embeddedReferenceBases,
            final int embeddedReferenceIndex,
            final int zeroBasedOffset) {
        sequenceRecord = getSAMSequenceRecord(embeddedReferenceIndex);
        referenceBasesContextID = embeddedReferenceIndex;
        referenceBases = embeddedReferenceBases;
        regionOffset = zeroBasedOffset;
        regionLength = embeddedReferenceBases.length;
    }

    /**
     * @return the length of the entire reference contig maintained by this region. note that this is not the
     * same as the length of the current bases maintained by this region (this can happen if the region contains
     * a fragment, or an embedded reference fragment), or -1 if no current region is established
     */
    public int getFullContigLength() {
        return sequenceRecord == null ? -1 : sequenceRecord.getSequenceLength();
    }

    private SAMSequenceRecord getSAMSequenceRecord(final int referenceIndex) {
        final SAMSequenceRecord samSequenceRecord = samFileHeader.getSequence(referenceIndex);
        if (samSequenceRecord == null) {
            throw new IllegalArgumentException(
                    String.format("A reference must be supplied (reference sequence index %d not found)", referenceIndex));
        }
        return samSequenceRecord;
    }

}
