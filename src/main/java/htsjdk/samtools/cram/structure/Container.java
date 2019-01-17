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

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.CRAIEntry;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.structure.slice.Slice;
import htsjdk.samtools.cram.structure.slice.SliceAlignmentMetadata;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Container {
    // container header as defined in the specs:
    /**
     * Byte size of the content excluding header.
     */
    public int containerByteSize;
    public int sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
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

    public Map<Integer, SliceAlignmentMetadata> getSliceMetadata(final ValidationStringency validationStringency) {
        final Map<Integer, SliceAlignmentMetadata> sliceMetadataMap  = new HashMap<>();

        // iterate through the container's slices, parsing multi-ref into individual references,
        // and recombining slice metadata per-reference as necessary

        Arrays.stream(slices)
                .flatMap(slice ->
                        slice.getAlignmentMetadata(header, validationStringency).entrySet().stream())
                .forEach(metadataMapEntry ->
                        sliceMetadataMap.merge(metadataMapEntry.getKey(), metadataMapEntry.getValue(), SliceAlignmentMetadata::add));

        return sliceMetadataMap;
    }

    public List<CRAIEntry> getCRAIEntries() {
        return Arrays.stream(slices)
                .map(Slice::getCRAIEntry)
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return String
                .format("seqID=%d, start=%d, span=%d, records=%d, slices=%d, blocks=%d.",
                        sequenceId, alignmentStart, alignmentSpan, nofRecords,
                        slices == null ? -1 : slices.length, blockCount);
    }

    public boolean isEOF() {
        final boolean v3 = containerByteSize == 15 && sequenceId == -1
                && alignmentStart == 4542278 && blockCount == 1
                && nofRecords == 0 && (slices == null || slices.length == 0);

        final boolean v2 = containerByteSize == 11 && sequenceId == -1
                && alignmentStart == 4542278 && blockCount == 1
                && nofRecords == 0 && (slices == null || slices.length == 0);

        return v3 || v2;
    }
}
