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
import htsjdk.samtools.cram.structure.block.BlockContentType;

import java.util.*;
import java.util.stream.Collectors;

public class Container {
    private final ContainerHeader containerHeader;

    private Block[] blocks;

    private CompressionHeader compressionHeader;

    // slices found in the container:
    private Slice[] slices;

    // this Container's byte offset from the the start of the stream.
    // Used for indexing.
    private long byteOffset;

    public Container(final ContainerHeader containerHeader) {
        this.containerHeader = containerHeader;
    }

    public Container(final ContainerHeader containerHeader,
                     final long containerByteOffset) {
        this.containerHeader = containerHeader;
        this.byteOffset = containerByteOffset;
    }

    public Container(final ContainerHeader containerHeader,
                     final CompressionHeader compressionHeader,
                     final List<Slice> slices,
                     final long containerByteOffset) {
        this.containerHeader = containerHeader;
        this.compressionHeader = compressionHeader;

        setSlicesAndByteOffset(slices, containerByteOffset);
    }

    public ContainerHeader getContainerHeader() {
        return containerHeader;
    }

    public boolean isFileHeaderContainer() {
        return blocks != null
                && blocks.length > 0
                && blocks[0].getContentType() == BlockContentType.FILE_HEADER;
    }

    public boolean isEOFContainer() {
        return containerHeader.isEOFContainer() && (slices == null || slices.length == 0);
    }

    /**
     * Byte size of the content excluding compressionHeader.
     */
    public int getContainerBlocksByteSize() {
        return containerHeader.getContainerBlocksByteSize();
    }

    public ReferenceContext getReferenceContext() {
        return containerHeader.getReferenceContext();
    }

    public int getAlignmentStart() {
        return containerHeader.getAlignmentStart();
    }

    public int getAlignmentSpan() {
        return containerHeader.getAlignmentSpan();
    }

    public int getNofRecords() {
        return containerHeader.getNofRecords();
    }

    public long getGlobalRecordCounter() {
        return containerHeader.getGlobalRecordCounter();
    }

    public long getBases() {
        return containerHeader.getBases();
    }

    public int getBlockCount() {
        return containerHeader.getBlockCount();
    }

    public int[] getLandmarks() {
        return containerHeader.getLandmarks();
    }

    public int getChecksum() {
        return containerHeader.getChecksum();
    }

    /**
     * Container data
     */
    public Block[] getBlocks() {
        return blocks;
    }

    public CompressionHeader getCompressionHeader() {
        return compressionHeader;
    }

    public Slice[] getSlices() {
        return slices;
    }

    /**
     * Assign {@link Slice}s to this Container and set its byteOffset.
     * Also distribute the Container's byte offset to the {@link Slice}s, for indexing.
     * @param slices the Slices belonging to this container
     * @param byteOffset the byte location in the stream where this Container begins
     */
    void setSlicesAndByteOffset(final List<Slice> slices, final long byteOffset) {
        for (final Slice slice : slices) {
            slice.containerByteOffset = byteOffset;
        }
        this.slices = slices.toArray(new Slice[0]);
        this.byteOffset = byteOffset;
    }

    public long getByteOffset() {
        return byteOffset;
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

        final int landmarks[] = containerHeader.getLandmarks();
        if (landmarks == null) {
            throw new CRAMException("Cannot set Slice indexing parameters if this Container does not have landmarks");
        }

        if (landmarks.length != slices.length) {
            final String format = "This Container's landmark and slice counts do not match: %d landmarks and %d slices";
            throw new CRAMException(String.format(format, landmarks.length, slices.length));
        }

        if (containerHeader.getContainerBlocksByteSize() == 0) {
            throw new CRAMException("Cannot set Slice indexing parameters if the byte size of this Container's blocks is unknown");
        }

        final int lastSliceIndex = slices.length - 1;
        for (int i = 0; i < lastSliceIndex; i++) {
            final Slice slice = slices[i];
            slice.index = i;
            slice.byteOffsetFromCompressionHeaderStart = landmarks[i];
            slice.byteSize = landmarks[i + 1] - slice.byteOffsetFromCompressionHeaderStart;
        }

        final Slice lastSlice = slices[lastSliceIndex];
        lastSlice.index = lastSliceIndex;
        lastSlice.byteOffsetFromCompressionHeaderStart = landmarks[lastSliceIndex];
        lastSlice.byteSize = containerHeader.getContainerBlocksByteSize() - lastSlice.byteOffsetFromCompressionHeaderStart;
    }

    /**
     * Retrieve the list of CRAI Index entries corresponding to this Container
     * @return the list of CRAI Index entries
     */
    public List<CRAIEntry> getCRAIEntries() {
        if (isEOFContainer()) {
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
        final String format = "seqID=%s, start=%d, span=%d, records=%d, slices=%d, blocks=%d.";
        return String.format(format, containerHeader.getReferenceContext(), containerHeader.getAlignmentStart(),
                containerHeader.getAlignmentSpan(), containerHeader.getNofRecords(),
                slices == null ? -1 : slices.length, containerHeader.getBlockCount());
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
