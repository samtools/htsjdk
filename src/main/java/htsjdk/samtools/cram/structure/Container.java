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

import java.util.*;
import java.util.stream.Collectors;

public class Container {
    private final ContainerHeader containerHeader;
    public CompressionHeader compressionHeader;

    // slices found in the container:
    private Slice[] slices;

    // this Container's byte offset from the the start of the stream.
    // Used for indexing.
    public long byteOffset;

    //TODO: this case is either an EOF container or a ContainerHeaderIterator container
    public Container(final ContainerHeader containerHeader, final long byteOffset) {
        this.containerHeader = containerHeader;
        this.byteOffset = byteOffset;
    }

    public Container(
            final ContainerHeader containerHeader,
            final CompressionHeader compressionHeader,
            final List<Slice> containerSlices,
            final long containerByteOffset) {
        this.containerHeader = containerHeader;
        this.compressionHeader = compressionHeader;
        this.slices = containerSlices.toArray(new Slice[0]);
        setSlicesAndByteOffset(containerSlices, containerByteOffset);
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
    public Container(
            final CompressionHeader compressionHeader,
            final List<Slice> containerSlices,
            final long containerByteOffset,
            final long globalRecordCounter,
            final int blockCount,
            final int bases) {
        final Set<ReferenceContext> sliceRefContexts = containerSlices.stream()
                .map(Slice::getReferenceContext)
                .collect(Collectors.toSet());

        if (sliceRefContexts.isEmpty()) {
            throw new CRAMException("Cannot construct a Container without any Slices");
        }
        else if (sliceRefContexts.size() > 1) {
            final String msg = String.format(
                    "Cannot construct a Container from Slices with conflicting types or sequence IDs: %s",
                    sliceRefContexts.stream()
                            .map(ReferenceContext::toString)
                            .collect(Collectors.joining(", ")));
            throw new CRAMException(msg);
        }

        final ReferenceContext commonRefContext = sliceRefContexts.iterator().next();

        this.containerHeader = new ContainerHeader(commonRefContext, globalRecordCounter, blockCount, bases);
        int noOfRecords = 0;
        for (final Slice slice : containerSlices) {
            noOfRecords += slice.nofRecords;
        }
        this.containerHeader.setNofRecords(noOfRecords);
        setSlicesAndByteOffset(containerSlices, containerByteOffset);
        this.compressionHeader = compressionHeader;

        if (commonRefContext.isMappedSingleRef()) {
            int start = Integer.MAX_VALUE;
            // end is start + span - 1.  We can do slightly easier math instead.
            int endPlusOne = Integer.MIN_VALUE;

            for (final Slice slice : containerSlices) {
                start = Math.min(start, slice.alignmentStart);
                endPlusOne = Math.max(endPlusOne, slice.alignmentStart + slice.alignmentSpan);
            }

            containerHeader.setAlignmentStart(start);
            containerHeader.setAlignmentSpan(endPlusOne - start);
        }
        else {
            containerHeader.setAlignmentStart(Slice.NO_ALIGNMENT_START);
            containerHeader.setAlignmentSpan(Slice.NO_ALIGNMENT_SPAN);
        }
    }

    public ContainerHeader getContainerHeader() { return containerHeader; }

    public ReferenceContext getReferenceContext() { return containerHeader.getReferenceContext(); }

    public Slice[] getSlices() {
        return slices;
    }

    /**
     * Populate the indexing parameters of this Container's slices
     *
     * Requires: valid landmarks and containerBlocksByteSize
     *
     * @throws CRAMException when the Container is in an invalid state
     */
    public void distributeIndexingParametersToSlices() {
        if (slices.length == 0) {
            return;
        }

        if (containerHeader.getLandmarks() == null) {
            throw new CRAMException("Cannot set Slice indexing parameters if this Container does not have landmarks");
        }

        if (containerHeader.getLandmarks().length != slices.length) {
            final String format = "This Container's landmark and slice counts do not match: %d landmarks and %d slices";
            throw new CRAMException(String.format(format, containerHeader.getLandmarks().length, slices.length));
        }

        if (containerHeader.getContainerBlocksByteSize() == 0) {
            throw new CRAMException("Cannot set Slice indexing parameters if the byte size of this Container's blocks is unknown");
        }

        final int lastSliceIndex = slices.length - 1;
        for (int i = 0; i < lastSliceIndex; i++) {
            final Slice slice = slices[i];
            slice.index = i;
            slice.byteOffsetFromCompressionHeaderStart = containerHeader.getLandmarks()[i];
            slice.byteSize = containerHeader.getLandmarks()[i + 1] - slice.byteOffsetFromCompressionHeaderStart;
        }

        final Slice lastSlice = slices[lastSliceIndex];
        lastSlice.index = lastSliceIndex;
        lastSlice.byteOffsetFromCompressionHeaderStart = containerHeader.getLandmarks()[lastSliceIndex];
        lastSlice.byteSize = containerHeader.getContainerBlocksByteSize() - lastSlice.byteOffsetFromCompressionHeaderStart;
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
        return String.format("%s slices=%d",
                containerHeader.toString(),
                getSlices() == null ?
                        -1 :
                        getSlices().length);
    }

    public boolean isEOF() {
        return containerHeader.isEOF() && (getSlices() == null || getSlices().length == 0);
    }

    /**
     * Iterate through all of this container's {@link Slice}s to derive a map of reference sequence IDs
     * to {@link AlignmentSpan}s.  Used to create BAI Indexes.
     *
     * @param validationStringency stringency for validating records, passed to
     * {@link Slice#getMultiRefAlignmentSpans(ValidationStringency)}
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
                    final Map<ReferenceContext, AlignmentSpan> spans = slice.getMultiRefAlignmentSpans(validationStringency);
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

    /**
     * Assign {@link Slice}s to this Container and set its byteOffset.
     * Also distribute the Container's byte offset to the {@link Slice}s, for indexing.
     * @param slices the Slices belonging to this container
     * @param byteOffset the byte location in the stream where this Container begins
     */
    private void setSlicesAndByteOffset(final List<Slice> slices, final long byteOffset) {
        for (final Slice slice : slices) {
            slice.containerByteOffset = byteOffset;
        }
        this.slices = slices.toArray(new Slice[0]);
        this.byteOffset = byteOffset;
    }

}
