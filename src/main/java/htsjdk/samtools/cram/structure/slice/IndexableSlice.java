/**
 * ****************************************************************************
 * Copyright 2013 EMBL-EBI
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at4
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
package htsjdk.samtools.cram.structure.slice;

import htsjdk.samtools.cram.CRAIEntry;
import htsjdk.samtools.cram.structure.block.Block;

import java.util.Map;

/**
 * A Slice is a logical grouping of blocks inside a container.
 *
 * Each Slice has:
 * 1 Slice Header Block
 * 1 Core Data Block
 * N External Data Blocks, indexed by Block Content ID.
 *
 * Here we also store BAI indexing information along with the Slice.
 * Since this is not part of the CRAM stream, we must first construct a {@link Slice} from the stream
 * and then construct the IndexableSlice using values derived from the Slice
 */
public class IndexableSlice extends Slice {
    private final int byteOffset;
    private final int byteSize;
    private final int sliceIndex;

    /**
     * Fully construct an IndexableSlice by specifying both the streamable data and indexing metadata
     *
     * @param sliceHeader an immutable data structure representing the header fields of the slice
     * @param coreBlock the Core Data Block associated with the slice
     * @param externalDataBlockMap a mapping of Block Content IDs to External Data Blocks
     * @param byteOffset the start byte position in the stream for this Slice
     * @param byteSize the size of this Slice when serialized, in bytes
     * @param sliceIndex the sliceIndex of this Slice in its Container
     */
    IndexableSlice(final SliceHeader sliceHeader,
                   final Block coreBlock,
                   final Map<Integer, Block> externalDataBlockMap,
                   final int byteOffset,
                   final int byteSize,
                   final int sliceIndex) {

        super(sliceHeader, coreBlock, externalDataBlockMap);

        this.byteOffset = byteOffset;
        this.byteSize = byteSize;
        this.sliceIndex = sliceIndex;
    }

    /**
     * Generate BAI indexing metadata from this Slice and other parameters.
     * This method is appropriate for single-reference Slices.
     *
     * @param containerByteOffset the byte offset of this Slice's Container
     * @return a new BAI indexing metadata object
     */
    public SliceBAIMetadata getBAIMetadata(final long containerByteOffset) {
        return new SliceBAIMetadata(getSequenceId(),
                getSliceAlignment(),
                byteOffset,
                containerByteOffset,
                byteSize,
                sliceIndex);
    }

    /**
     * Generate BAI indexing metadata from this Slice and other parameters.
     *
     * @param sequenceId which Sequence ID this metadata should be associated with
     * @param span the Alignment Span to be indexed
     * @param containerByteOffset the byte offset of this Slice's Container
     * @return a new BAI indexing metadata object
     */
    public SliceBAIMetadata getBAIMetadata(final int sequenceId,
                                           final SliceAlignment span,
                                           final long containerByteOffset) {
        return new SliceBAIMetadata(sequenceId,
                span,
                byteOffset,
                containerByteOffset,
                byteSize,
                sliceIndex);
    }

    /**
     * Generate a CRAI Index entry from this Slice and other parameters.
     *
     * @param containerByteOffset the byte offset of this Slice's Container
     * @return a new CRAI Index Entry
     */
    public CRAIEntry getCRAIEntry(final long containerByteOffset) {
        return new CRAIEntry(getSequenceId(),
                getAlignmentStart(),
                getAlignmentSpan(),
                containerByteOffset,
                byteOffset,
                byteSize);
    }

    /**
     * Generate a CRAI Index entry from this Slice and other parameters.
     * This method is appropriate for multiple-reference Slices.
     *
     * @param sequenceId a Sequence ID to associate with this sliceIndex entry
     * @param span the Alignment Span to sliceIndex
     * @param containerByteOffset the byte offset of this Slice's Container
     * @return a new CRAI Index Entry
     */
    public CRAIEntry getCRAIEntry(final int sequenceId,
                                  final SliceAlignment span,
                                  final long containerByteOffset) {
        return new CRAIEntry(sequenceId,
                span.getStart(),
                span.getSpan(),
                containerByteOffset,
                byteOffset,
                byteSize);
    }

    /**
     * Where does this Slice appear in its Container?
     * Used when populating a {@link htsjdk.samtools.cram.structure.CramCompressionRecord}
     *
     * @return the sliceIndex of this Slice in its Container
     */
    public int getSliceIndex() {
        return sliceIndex;
    }
}

