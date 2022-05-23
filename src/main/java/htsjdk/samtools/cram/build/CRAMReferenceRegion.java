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

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.ref.ReferenceContextType;
import htsjdk.samtools.cram.structure.AlignmentContext;
import htsjdk.samtools.util.Log;
import htsjdk.utils.ValidationUtils;

/**
 * Holds a region/fragment of a reference contig. Maintains a CRAMReferenceSource for retrieving additional regions.
 * This is a mutable object that is used to traverse along a reference contig via serial calls to either
 * {@link #fetchReferenceBases(int)} or {@link #fetchReferenceBasesByRegion(int, int, int)}. It caches the bases
 * from the previous request, along with metadata about the (0-based) start offset, and length of the
 * cached bases.
 */
public class CRAMReferenceRegion {
    private static final Log log = Log.getInstance(CRAMReferenceRegion.class);
    public static final int UNINITIALIZED_START = -1;
    public static final int  UNINITIALIZED_LENGTH = -1;

    private final CRAMReferenceSource referenceSource;
    private final SAMSequenceDictionary sequenceDictionary;

    private int referenceIndex = ReferenceContext.UNINITIALIZED_REFERENCE_ID;
    private byte[] referenceBases = null;
    private SAMSequenceRecord sequenceRecord = null;
    private int regionStart = UNINITIALIZED_START; // 0-based start offset of the region
    private int regionLength = UNINITIALIZED_LENGTH; // length of the bases cached by this reference region

    /**
     * @param cramReferenceSource {@link CRAMReferenceSource} to use to obtain reference bases
     * @param sequenceDictionary {@link SAMSequenceDictionary} to use to resolve reference contig names to reference index
     */
    public CRAMReferenceRegion(final CRAMReferenceSource cramReferenceSource, final SAMSequenceDictionary sequenceDictionary) {
        ValidationUtils.nonNull(cramReferenceSource, "cramReferenceSource");
        ValidationUtils.nonNull(sequenceDictionary, "sequenceDictionary");

        this.referenceSource = cramReferenceSource;
        this.sequenceDictionary = sequenceDictionary;
    }

    /**
     * @return the currently cached reference bases (may return null)
     */
    public byte[] getCurrentReferenceBases() {
        return referenceBases;
    }

    /**
     * @return the current reference index or {@link ReferenceContext#UNINITIALIZED_REFERENCE_ID} if no index has
     * been established
     */
    public int getReferenceIndex() {
        return referenceIndex;
    }

    /**
     * @return the 0-based start position of the range of the current reference sequence region.
     * {@link #UNINITIALIZED_START} if no region has been established
     */
    public int getRegionStart() {
        return regionStart;
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
     * Note: Serial calls to this method on behalf of non-coordinate sorted inputs can result in
     * thrashing and poor performance due to repeated calls to the underlying CRAMReferenceSource,
     * especially when the CRAMReferenceSource is fetching bases from a remote reference.
     *
     * @param referenceIndex the reference index for which bases should be retrieved.
     * @throws IllegalArgumentException if the requested index is not present in the sequence dictionary or if
     * the sequence's bases cannot be retrieved from the CRAMReferenceSource
     */
    public void fetchReferenceBases(final int referenceIndex) {
        ValidationUtils.validateArg(referenceIndex >= 0, "reference index must be >= 0");

        // Re-resolve the reference bases if we don't have a current region or if the region we have
        // doesn't span the *entire* contig requested.
        if ((referenceIndex != this.referenceIndex) ||
                regionStart != 0 ||
                (regionLength < referenceBases.length)) {
            setCurrentSequence(referenceIndex);
            referenceBases = referenceSource.getReferenceBases(sequenceRecord, true);
            if (referenceBases == null) {
                throw new IllegalArgumentException(
                        String.format("A reference must be supplied (reference sequence %s not found).", sequenceRecord));
            }
            regionStart = 0;
            regionLength = sequenceRecord.getSequenceLength();
        }
    }

    /**
     * Get the reference bases for a region of a reference contig. If the current region does not match the
     * requested region, the {@link #referenceSource} will be called to retrieve the bases.
     *
     * The caller cannot assume that the entire region requested is always fetched (if the requested range matches
     * the alignment span for CRAM record or slice contains a CRAM records that is mapped beyond the end of the
     * reference contig, fewer bases than were requested may be fetched.
     *
     * @param referenceIndex reference index for which to retrieve bases
     * @param zeroBasedStart zero based start of the first base to be retrieved
     * @param requestedFragmentLength length of the fragment to be retrieved
     *
     * @throws IllegalArgumentException if the requested sequence cannot be located in the sequence dictionary, or
     * if the requested sequence cannot be provided by the underlying referenceSource
     */
    public void fetchReferenceBasesByRegion(
            final int referenceIndex,
            final int zeroBasedStart,
            final int requestedFragmentLength) {
        ValidationUtils.validateArg(referenceIndex >= 0, "reference index must be non-negative");
        ValidationUtils.validateArg(zeroBasedStart >= 0, "start must be >= 0");

        if (referenceIndex == this.referenceIndex &&
                zeroBasedStart == regionStart &&
                requestedFragmentLength == regionLength) {
            // exact match for what we already have
            return;
        }

        if (referenceIndex != this.referenceIndex) {
            setCurrentSequence(referenceIndex);
        }

        if (zeroBasedStart >= sequenceRecord.getSequenceLength()) {
            throw new IllegalArgumentException(String.format("Requested start %d is beyond the sequence length %s",
                    zeroBasedStart,
                    sequenceRecord.getSequenceName()));
        }

        referenceBases = referenceSource.getReferenceBasesByRegion(sequenceRecord, zeroBasedStart, requestedFragmentLength);
        if (referenceBases == null) {
            throw new IllegalArgumentException(
                    String.format("Failure getting reference bases for sequence %s", sequenceRecord.getSequenceName()));
        } else if (referenceBases.length < requestedFragmentLength) {
            log.warn("The bases of length " + referenceBases.length +
                    " returned by the reference source do not satisfy the requested fragment length " +
                    (zeroBasedStart + requestedFragmentLength));
        }
        regionStart = zeroBasedStart;
        regionLength = referenceBases.length;
    }

    /**
     * Fetch the bases to span an {@link AlignmentContext}.
     * @param alignmentContext the alignment context for which to fetch bases. must be an AlignmentContext
     *                         for a single reference {@link ReferenceContextType#SINGLE_REFERENCE_TYPE} slice (see
     *                         {@link ReferenceContext#isMappedSingleRef()})
     */
    public void fetchReferenceBasesByRegion(final AlignmentContext alignmentContext) {
        ValidationUtils.validateArg(
                alignmentContext.getReferenceContext().isMappedSingleRef(),
                "a mapped single reference alignment context is required");
        fetchReferenceBasesByRegion(
                alignmentContext.getReferenceContext().getReferenceSequenceID(),
                alignmentContext.getAlignmentStart() - 1, // 1-based alignment context to 0-based reference offset
                alignmentContext.getAlignmentSpan());
    }

    /**
     * Set this {@link CRAMReferenceRegion} to use an embedded reference.
     *
     * @param embeddedReferenceBases the embedded reference bases to be used
     * @param embeddedReferenceIndex the reference ID used in the slice containing the embedded reference
     * @param zeroBasedStart the zero based reference start of the first base in the embedded reference bases
     */
    public void setEmbeddedReferenceBases(
            final byte[] embeddedReferenceBases,
            final int embeddedReferenceIndex,
            final int zeroBasedStart) {
        ValidationUtils.nonNull(embeddedReferenceBases, "embeddedReferenceBases");
        setCurrentSequence(embeddedReferenceIndex);
        referenceBases = embeddedReferenceBases;
        regionStart = zeroBasedStart;
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

    private void setCurrentSequence(final int referenceIndex) {
        this.referenceIndex= referenceIndex;
        this.sequenceRecord = getSAMSequenceRecord(referenceIndex);
    }

    private SAMSequenceRecord getSAMSequenceRecord(final int referenceIndex) {
        final SAMSequenceRecord samSequenceRecord = sequenceDictionary.getSequence(referenceIndex);
        if (samSequenceRecord == null) {
            throw new IllegalArgumentException(
                    String.format("Reference sequence index %d not found", referenceIndex));
        }
        return samSequenceRecord;
    }

}
