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
package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.structure.slice.SliceAlignment;
import htsjdk.samtools.cram.encoding.reader.CramRecordReader;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.slice.IndexableSlice;
import htsjdk.samtools.cram.structure.slice.Slice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContainerParser {
    private final SAMFileHeader samFileHeader;

    public ContainerParser(final SAMFileHeader samFileHeader) {
        this.samFileHeader = samFileHeader;
    }

    public List<CramCompressionRecord> getRecords(final Container container,
                                                  ArrayList<CramCompressionRecord> records,
                                                  final ValidationStringency validationStringency) {
        if (container.isEOF()) {
            return Collections.emptyList();
        }

        if (records == null) {
            records = new ArrayList<>(container.nofRecords);
        }

        for (final IndexableSlice slice : container.slices) {
            records.addAll(getRecords(slice, container.header, validationStringency));
        }

        return records;
    }

    public Map<Integer, SliceAlignment> getReferences(final Container container, final ValidationStringency validationStringency) {
        final Map<Integer, SliceAlignment> containerSpanMap  = new HashMap<>();
        for (final IndexableSlice slice : container.slices) {
            addAllSpans(containerSpanMap, getReferences(slice, container.header, validationStringency));
        }
        return containerSpanMap;
    }

    private static void addSpan(final Map<Integer, SliceAlignment> map, final int seqId, final SliceAlignment sliceAlignment) {
        if (map.containsKey(seqId)) {
            map.get(seqId).add(sliceAlignment);
        } else {
            map.put(seqId, sliceAlignment);
        }
    }

    private static void addAllSpans(final Map<Integer, SliceAlignment> spanMap, final Map<Integer, SliceAlignment> addition) {
        for (final Map.Entry<Integer, SliceAlignment> entry:addition.entrySet()) {
            addSpan(spanMap, entry.getKey(), entry.getValue());
        }
    }

    private Map<Integer, SliceAlignment> getReferences(final Slice slice, final CompressionHeader header, final ValidationStringency validationStringency) {
        final Map<Integer, SliceAlignment> spanMap = new HashMap<>();

        if (slice.hasNoReference()) {
            spanMap.put(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, SliceAlignment.UNMAPPED_SPAN);
        } else if (slice.hasMultipleReferences()) {
            addAllSpans(spanMap, slice.getMultiRefAlignmentSpans(header, validationStringency));
        } else {
            addSpan(spanMap, slice.getSequenceId(), slice.getSliceAlignment());
        }

        return spanMap;
    }

    private ArrayList<CramCompressionRecord> getRecords(final IndexableSlice slice,
                                                        final CompressionHeader header,
                                                        final ValidationStringency validationStringency) {
        String seqName = SAMRecord.NO_ALIGNMENT_REFERENCE_NAME;

        if (slice.hasSingleReference()) {
            final SAMSequenceRecord sequence = samFileHeader.getSequence(slice.getSequenceId());
            seqName = sequence.getSequenceName();
        }

        final CramRecordReader reader = slice.createCramRecordReader(header, validationStringency);

        ArrayList<CramCompressionRecord> records = new ArrayList<>(slice.getRecordCount());

        int prevStart = slice.getAlignmentStart();
        for (int i = 0; i < slice.getRecordCount(); i++) {
            final CramCompressionRecord record = new CramCompressionRecord();
            record.sliceIndex = slice.getSliceIndex();
            record.index = i;

            reader.read(record);

            if (record.sequenceId == slice.getSequenceId()) {
                record.sequenceName = seqName;
                record.sequenceId = slice.getSequenceId();
            } else {
                if (record.sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                    record.sequenceName = SAMRecord.NO_ALIGNMENT_REFERENCE_NAME;
                } else {
                    record.sequenceName = samFileHeader.getSequence(record.sequenceId)
                            .getSequenceName();
                }
            }

            records.add(record);

            if (header.APDelta) {
                prevStart += record.alignmentDelta;
                record.alignmentStart = prevStart;
            }
        }

        return records;
    }
}
