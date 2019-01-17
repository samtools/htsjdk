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
import htsjdk.samtools.cram.encoding.reader.CramRecordReader;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.slice.Slice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContainerParser {
    private final SAMFileHeader samFileHeader;

    public ContainerParser(final SAMFileHeader samFileHeader) {
        this.samFileHeader = samFileHeader;
    }

    public List<CramCompressionRecord> getRecords(final ArrayList<CramCompressionRecord> records,
                                                  final Container container,
                                                  final ValidationStringency validationStringency) throws IllegalArgumentException {
        if (container.isEOF()) {
            return Collections.emptyList();
        }

        for (final Slice slice : container.slices) {
            addSliceRecords(records, slice, container.header, validationStringency);
        }

        return records;
    }

    private void addSliceRecords(final ArrayList<CramCompressionRecord> records,
                                 final Slice slice,
                                 final CompressionHeader header,
                                 final ValidationStringency validationStringency) throws IllegalArgumentException {
        String seqName = SAMRecord.NO_ALIGNMENT_REFERENCE_NAME;
        switch (slice.sequenceId) {
            case SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX:
            case -2:
                break;

            default:
                final SAMSequenceRecord sequence = samFileHeader.getSequence(slice.sequenceId);
                seqName = sequence.getSequenceName();
                break;
        }

        final CramRecordReader reader = slice.createCramRecordReader(header, validationStringency);

        int prevStart = slice.alignmentStart;
        for (int i = 0; i < slice.nofRecords; i++) {
            final CramCompressionRecord record = new CramCompressionRecord();
            record.sliceIndex = slice.index;
            record.index = i;

            reader.read(record);

            if (record.sequenceId == slice.sequenceId) {
                record.sequenceName = seqName;
                record.sequenceId = slice.sequenceId;
            } else {
                if (record.sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                    record.sequenceName = SAMRecord.NO_ALIGNMENT_REFERENCE_NAME;
                } else {
                    record.sequenceName = samFileHeader.getSequence(record.sequenceId).getSequenceName();
                }
            }

            records.add(record);

            if (header.APDelta) {
                prevStart += record.alignmentDelta;
                record.alignmentStart = prevStart;
            }
        }
    }

}
