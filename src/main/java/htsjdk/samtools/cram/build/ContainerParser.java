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
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.encoding.reader.CramRecordReader;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.Slice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContainerParser {
    private final SAMFileHeader samFileHeader;

    public ContainerParser(final SAMFileHeader samFileHeader) {
        this.samFileHeader = samFileHeader;
    }

    public List<CramCompressionRecord> getRecords(final Container container,
                                                  ArrayList<CramCompressionRecord> records,
                                                  final ValidationStringency validationStringency) {
        if (container.isEOFContainer()) {
            return Collections.emptyList();
        }

        if (records == null) {
            records = new ArrayList<>(container.getNofRecords());
        }

        for (final Slice slice : container.getSlices()) {
            records.addAll(getRecords(slice, container.getCompressionHeader(), validationStringency));
        }

        return records;
    }

    private ArrayList<CramCompressionRecord> getRecords(final Slice slice,
                                                        final CompressionHeader header,
                                                        final ValidationStringency validationStringency) {
        final ReferenceContext sliceContext = slice.getReferenceContext();
        String seqName = SAMRecord.NO_ALIGNMENT_REFERENCE_NAME;
        if (sliceContext.isMappedSingleRef()) {
            final SAMSequenceRecord sequence = samFileHeader.getSequence(sliceContext.getSequenceId());
            seqName = sequence.getSequenceName();

        }

        final CramRecordReader reader = slice.createCramRecordReader(header, validationStringency);

        final ArrayList<CramCompressionRecord> records = new ArrayList<>(slice.nofRecords);

        int prevAlignmentStart = slice.alignmentStart;
        for (int i = 0; i < slice.nofRecords; i++) {
            final CramCompressionRecord record = new CramCompressionRecord();
            record.sliceIndex = slice.index;
            record.index = i;

            // read the new record and update the running prevAlignmentStart
            prevAlignmentStart = reader.read(record, prevAlignmentStart);

            if (sliceContext.isMappedSingleRef() && record.sequenceId == sliceContext.getSequenceId()) {
                record.sequenceName = seqName;
            } else {
                if (record.sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                    record.sequenceName = SAMRecord.NO_ALIGNMENT_REFERENCE_NAME;
                } else {
                    record.sequenceName = samFileHeader.getSequence(record.sequenceId)
                            .getSequenceName();
                }
            }

            records.add(record);
        }

        return records;
    }
}
