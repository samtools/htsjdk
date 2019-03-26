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

import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.CRAIEntry;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.block.Block;

import java.util.*;
import java.util.stream.Collectors;

public class Container {
    private final ReferenceContext referenceContext;

    // container header as defined in the specs, in addition to sequenceId from ReferenceContext

    /**
     * Byte size of the content excluding header.
     */
    public int containerByteSize = 0;

    // minimum alignment start of the reads in this Container
    // uses a 1-based coordinate system
    public int alignmentStart = Slice.NO_ALIGNMENT_START;
    public int alignmentSpan = Slice.NO_ALIGNMENT_SPAN;
    public int nofRecords = 0;
    public long globalRecordCounter = 0;

    public long bases = 0;
    public int blockCount = -1;

    // Slice byte boundaries within this container, after the header.  Equal to Slice.offset.
    // e.g. if landmarks[0] = 9000 and landmarks[1] = 109000, we know:
    // the container's header size = 9000
    // Slice[0].offset = 9000
    // Slice[0].size = 109000 - 9000 = 100000
    // Slice[1].offset = 109000

    public int[] landmarks;

    public int checksum = 0;

    /**
     * Container data
     */
    public Block[] blocks;

    public CompressionHeader compressionHeader;

    // slices found in the container:
    private Slice[] slices;

    // this Container's byte offset from the the start of the stream.
    // Used for indexing.
    private long byteOffset;

    public Slice[] getSlices() {
        return slices;
    }

    /**
     * Assign {@link Slice}s to this Container.
     * Also distribute the Container's byte offset to the {@link Slice}s, for indexing.
     * @param slices the Slices belonging to this container
     * @param byteOffset the byte location in the stream where this Container begins
     */
    void setSlices(final Slice[] slices, final long byteOffset) {
        this.slices = slices;
        setByteOffsetInSlices(byteOffset);
    }

    public long getByteOffset() {
        return byteOffset;
    }

    /**
     * Set this Container's byte offset from the the start of the stream.
     * Also distribute this value to the Container's constituent {@link Slice}s, if present.
     *
     * @param byteOffset the byte location in the stream where this Container begins
     */
    void setByteOffset(final long byteOffset) {
        this.byteOffset = byteOffset;
        if (slices != null) {
            setByteOffsetInSlices(byteOffset);
        }
    }

    /**
     * Distribute this Container's byte offset to the Container's constituent {@link Slice}s.
     * For indexing.
     *
     * @param byteOffset the byte location in the stream where this Container begins
     */
    private void setByteOffsetInSlices(final long byteOffset) {
        for (final Slice slice : slices) {
            slice.containerByteOffset = byteOffset;
        }
    }

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
     * @param compressionHeader the CRAM {@link CompressionHeader} to assign to the Container
     * @param containerByteOffset the Container's byte offset from the start of the stream
     * @throws CRAMException for invalid Container states
     * @return the initialized Container
     */
    public static Container initializeFromSlices(final List<Slice> containerSlices,
                                                 final CompressionHeader compressionHeader,
                                                 final long containerByteOffset) {
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
        container.setSlices(containerSlices.toArray(new Slice[0]), containerByteOffset);
        container.compressionHeader = compressionHeader;

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

        container.setByteOffset(containerByteOffset);
        return container;
    }

    /**
     * Populate the indexing parameters of this Container's slices
     *
     * Requires: valid landmarks and containerByteSize
     *
     * @throws CRAMException when the Container is in an invalid state
     */
    public void distributeIndexingParametersToSlices() {
        if (slices.length == 0) {
            return;
        }

        if (landmarks == null) {
            throw new CRAMException("Cannot set Slice indexing parameters if this Container does not have landmarks");
        }

        if (landmarks.length != slices.length) {
            final String format = "This Container's landmark and slice counts do not match: %d landmarks and %d slices";
            throw new CRAMException(String.format(format, landmarks.length, slices.length));
        }

        if (containerByteSize == 0) {
            throw new CRAMException("Cannot set Slice indexing parameters if this Container's byte size is unknown");
        }

        final int lastSliceIndex = slices.length - 1;
        for (int i = 0; i < lastSliceIndex; i++) {
            final Slice slice = slices[i];
            slice.index = i;
            slice.byteOffsetFromContainer = landmarks[i];
            slice.byteSize = landmarks[i + 1] - slice.byteOffsetFromContainer;
        }

        final Slice lastSlice = slices[lastSliceIndex];
        lastSlice.index = lastSliceIndex;
        lastSlice.byteOffsetFromContainer = landmarks[lastSliceIndex];

        // calculate a "final landmark" indicating the byte offset of the end of the container
        // equivalent to the container's total byte size

        final int containerHeaderSize = landmarks[0];
        final int containerTotalByteSize = containerHeaderSize + containerByteSize;
        lastSlice.byteSize = containerTotalByteSize - lastSlice.byteOffsetFromContainer;
    }

    /**
     * Retrieve the list of CRAI Index entries corresponding to this Container
     * @return the list of CRAI Index entries
     */
    public List<CRAIEntry> getCRAIEntries() {
        if (isEOF()) {
            return Collections.emptyList();
        }

        return Arrays.stream(getSlices())
                .map(s -> s.getCRAIEntries(compressionHeader))
                .flatMap(List::stream)
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return String
                .format("seqID=%s, start=%d, span=%d, records=%d, slices=%d, blocks=%d.",
                        referenceContext, alignmentStart, alignmentSpan, nofRecords,
                        getSlices() == null ? -1 : getSlices().length, blockCount);
    }

    public boolean isEOF() {
        final boolean v3 = containerByteSize == 15 && referenceContext.isUnmappedUnplaced()
                && alignmentStart == 4542278 && blockCount == 1
                && nofRecords == 0 && (getSlices() == null || getSlices().length == 0);

        final boolean v2 = containerByteSize == 11 && referenceContext.isUnmappedUnplaced()
                && alignmentStart == 4542278 && blockCount == 1
                && nofRecords == 0 && (getSlices() == null || getSlices().length == 0);

        return v3 || v2;
    }

    /**
     * Iterate through all of this container's {@link Slice}s to derive a map of reference sequence IDs
     * to {@link AlignmentSpan}s.  Used to create BAI Indexes.
     *
     * @param validationStringency stringency for validating records, passed to
     * {@link Slice#getMultiRefAlignmentSpans(CompressionHeader, ValidationStringency)}
     * @return the map of map of reference sequence IDs to AlignmentSpans.
     */
    public Map<ReferenceContext, AlignmentSpan> getSpans(final ValidationStringency validationStringency) {
        final Map<ReferenceContext, AlignmentSpan> containerSpanMap  = new HashMap<>();
        for (final Slice slice : getSlices()) {
            switch (slice.getReferenceContext().getType()) {
                case UNMAPPED_UNPLACED_TYPE:
                    containerSpanMap.put(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, AlignmentSpan.UNPLACED_SPAN);
                    break;
                case MULTIPLE_REFERENCE_TYPE:
                    final Map<ReferenceContext, AlignmentSpan> spans = slice.getMultiRefAlignmentSpans(compressionHeader, validationStringency);
                    for (final Map.Entry<ReferenceContext, AlignmentSpan> entry : spans.entrySet()) {
                        containerSpanMap.merge(entry.getKey(), entry.getValue(), AlignmentSpan::combine);
                    }
                    break;
                default:
                    final AlignmentSpan alignmentSpan = new AlignmentSpan(slice.alignmentStart, slice.alignmentSpan, slice.mappedReadsCount, slice.unmappedReadsCount);
                    containerSpanMap.merge(slice.getReferenceContext(), alignmentSpan, AlignmentSpan::combine);
                    break;
            }
        }
        return containerSpanMap;
    }
}
