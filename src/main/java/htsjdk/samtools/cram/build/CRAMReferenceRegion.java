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
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceContext;

/**
 * A (cached) region of a reference. Maintains a CRAMReferenceSource for retrieving additional regions.
 */
public class CRAMReferenceRegion {

    private final CRAMReferenceSource referenceSource;
    private final SAMFileHeader samFileHeader;

    private byte[] referenceBases = null; // cache the reference bases
    private int referenceBasesContextID = ReferenceContext.UNINITIALIZED_REFERENCE_ID;

    /**
     * @param cramReferenceSource {@link CRAMReferenceSource} to use to obtain reference bases
     * @param samFileHeader {@link SAMFileHeader} to use to resolve reference contig names to reference index
     */
    public CRAMReferenceRegion(final CRAMReferenceSource cramReferenceSource, final SAMFileHeader samFileHeader) {
        if (cramReferenceSource == null) {
            throw new IllegalArgumentException("A valid reference must be supplied to retrieve records from the CRAM stream.");
        }
        this.referenceSource = cramReferenceSource;
        this.samFileHeader = samFileHeader;
    }

    /**
     * @return the currently cached reference bases (may ne null)
     */
    public byte[] getCurrentReferenceBases() {
        return referenceBases;
    }

    /**
     * Return the reference bases for the given reference index.
     * @param referenceIndex
     *
     * @return bases for the entire reference contig specifed by {@code referenceIndex}
     */
    public byte[] getReferenceBases(final int referenceIndex) {
        // for non-coord sorted this could cause a lot of thrashing
        if (referenceIndex != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            if (referenceBases == null || referenceIndex != referenceBasesContextID) {
                final SAMSequenceRecord sequence = samFileHeader.getSequence(referenceIndex);
                referenceBases = referenceSource.getReferenceBases(sequence, true);
                if (referenceBases == null) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "A reference must be supplied (reference sequence %s not found).",
                                    sequence));
                }

                referenceBasesContextID = referenceIndex;
            }
            return referenceBases;
        }

        // retain whatever cached reference bases we may have to minimize subsequent re-fetching
        return null;
    }

    public void setEmbeddedReference(final byte[] embeddedReferenceBytes, final int embeddedReferenceIndex) {
        referenceBasesContextID = embeddedReferenceIndex;
        referenceBases = embeddedReferenceBytes;
    }

}
