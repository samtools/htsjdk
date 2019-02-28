/**
 * ****************************************************************************
 * Copyright 2013 EMBL-EBI
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.CRAIEntry;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.block.Block;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Container {
    private final ReferenceContext referenceContext;

    // container header as defined in the specs, in addition to sequenceId from ReferenceContext
    /**
     * Byte size of the content excluding header.
     */
    public int containerByteSize;
    public int alignmentStart = Slice.NO_ALIGNMENT_START;
    public int alignmentSpan = Slice.NO_ALIGNMENT_SPAN;
    public int nofRecords = 0;
    public long globalRecordCounter = 0;

    public long bases = 0;
    public int blockCount = -1;
    public int[] landmarks;
    public int checksum = 0;

    /**
     * Container data
     */
    public Block[] blocks;

    public CompressionHeader header;

    // slices found in the container:
    public Slice[] slices;

    // for indexing:
    /**
     * Container start in the stream.
     */
    public long offset;

    /**
     * Construct this Container by providing its {@link ReferenceContext}
     * @param refContext the reference context associated with this container
     */
    public Container(final ReferenceContext refContext) {
        this.referenceContext = refContext;
    }

    public ReferenceContext getReferenceContext() {
        return referenceContext;
    }

    /**
     * Derive the container's {@link ReferenceContext} from its {@link Slice}s.
     *
     * A Single Reference Container contains only Single Reference Slices mapped to the same reference.
     * - set the Container's ReferenceContext to be the same as those slices
     * - set the Container's Alignment Start and Span to cover all slices
     *
     * A Multiple Reference Container contains only Multiple Reference Slices.
     * - set the Container's ReferenceContext to MULTIPLE_REFERENCE_CONTEXT
     * - unset the Container's Alignment Start and Span
     *
     * An Unmapped Container contains only Unmapped Slices.
     * - set the Container's ReferenceContext to UNMAPPED_UNPLACED_CONTEXT
     * - unset the Container's Alignment Start and Span
     *
     * Any other combination is invalid.
     *
     * TODO for general Container refactoring: make this part of construction
     *
     * @param containerSlices the constituent Slices of the Container
     * @throws CRAMException for invalid Container states
     * @return the initialized Container
     */
    public static Container initializeFromSlices(final List<Slice> containerSlices) {
        final Set<ReferenceContext> sliceRefContexts = containerSlices.stream()
                .map(Slice::getReferenceContext)
                .collect(Collectors.toSet());

        if (sliceRefContexts.isEmpty()) {
            throw new CRAMException("Cannot construct a Container without any Slices");
        }
        else if (sliceRefContexts.size() > 1) {
            throw new CRAMException("Cannot construct a Container from Slices with conflicting types or sequence IDs");
        }

        final ReferenceContext commonRefContext = sliceRefContexts.iterator().next();

        final Container container = new Container(commonRefContext);
        container.slices = containerSlices.toArray(new Slice[0]);

        if (commonRefContext.isMappedSingleRef()) {
            int start = Integer.MAX_VALUE;
            // end is start + span - 1.  We can do slightly easier math instead.
            int endPlusOne = Integer.MIN_VALUE;

            for (final Slice slice : containerSlices) {
                start = Math.min(start, slice.alignmentStart);
                endPlusOne = Math.max(endPlusOne, slice.alignmentStart + slice.alignmentSpan);
            }

            container.alignmentStart = start;
            container.alignmentSpan = endPlusOne - start;
        }
        else {
            container.alignmentStart = Slice.NO_ALIGNMENT_START;
            container.alignmentSpan = Slice.NO_ALIGNMENT_SPAN;
        }

        return container;
    }

    public List<CRAIEntry> getCRAIEntries() {
        return Arrays.stream(slices)
                .map(Slice::getCRAIEntry)
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return String
                .format("seqID=%s, start=%d, span=%d, records=%d, slices=%d, blocks=%d.",
                        referenceContext, alignmentStart, alignmentSpan, nofRecords,
                        slices == null ? -1 : slices.length, blockCount);
    }

    public boolean isEOF() {
        final boolean v3 = containerByteSize == 15 && referenceContext.isUnmappedUnplaced()
                && alignmentStart == 4542278 && blockCount == 1
                && nofRecords == 0 && (slices == null || slices.length == 0);

        final boolean v2 = containerByteSize == 11 && referenceContext.isUnmappedUnplaced()
                && alignmentStart == 4542278 && blockCount == 1
                && nofRecords == 0 && (slices == null || slices.length == 0);

        return v3 || v2;
    }
}
