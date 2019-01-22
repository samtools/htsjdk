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
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.Slice;
import htsjdk.samtools.cram.structure.SubstitutionMatrix;

import java.util.ArrayList;
import java.util.List;

public class ContainerFactory {
    private final SAMFileHeader samFileHeader;
    private int recordsPerSlice = 10000;
    private boolean preserveReadNames = true;
    private long globalRecordCounter = 0;

    public ContainerFactory(final SAMFileHeader samFileHeader, final int recordsPerSlice) {
        this.samFileHeader = samFileHeader;
        this.recordsPerSlice = recordsPerSlice;
    }

    public Container buildContainer(final List<CramCompressionRecord> records) {
        return buildContainer(records, null);
    }

    Container buildContainer(final List<CramCompressionRecord> records,
                             final SubstitutionMatrix substitutionMatrix) {

        // sets header APDelta
        final boolean coordinateSorted = samFileHeader.getSortOrder() == SAMFileHeader.SortOrder.coordinate;
        final CompressionHeader header = new CompressionHeaderFactory().build(records, substitutionMatrix, coordinateSorted);

        header.readNamesIncluded = preserveReadNames;

        final List<Slice> slices = new ArrayList<>();

        final Container container = new Container();
        container.header = header;
        container.nofRecords = records.size();
        container.globalRecordCounter = globalRecordCounter;
        container.bases = 0;
        container.blockCount = 0;

        long lastGlobalRecordCounter = container.globalRecordCounter;
        for (int i = 0; i < records.size(); i += recordsPerSlice) {
            final List<CramCompressionRecord> sliceRecords = records.subList(i,
                    Math.min(records.size(), i + recordsPerSlice));
            final Slice slice = Slice.buildSlice(sliceRecords, header);
            slice.globalRecordCounter = lastGlobalRecordCounter;
            lastGlobalRecordCounter += slice.nofRecords;
            container.bases += slice.bases;
            slices.add(slice);
        }

        container.slices = slices.toArray(new Slice[0]);
        calculateAlignmentBoundaries(container);

        globalRecordCounter += records.size();
        return container;
    }

    static void calculateAlignmentBoundaries(final Container container) {
        int start = Integer.MAX_VALUE;
        int end = Integer.MIN_VALUE;

        // in general practice, the only slice
        final Slice firstSlice = container.slices[0];
        container.sequenceId = firstSlice.sequenceId;

        for (final Slice slice : container.slices) {
            if (slice.sequenceId != container.sequenceId) {
                final String msg = String.format(
                        "Slices in Container have conflicting reference sequences: %d and %d ",
                        slice.sequenceId, container.sequenceId);
                throw new CRAMException(msg);
            }

            if (slice.isMappedSingleRef()) {
                start = Math.min(start, slice.alignmentStart);
                end = Math.max(end, slice.alignmentStart + slice.alignmentSpan);
            }
        }

        // equivalent to "are all slices mapped" because we enforce identical ref seq IDs
        if (firstSlice.isMappedSingleRef()) {
            container.alignmentStart = start;
            container.alignmentSpan = end - start;
        }
        else {
            container.alignmentStart = Slice.NO_ALIGNMENT_START;
            container.alignmentSpan = Slice.NO_ALIGNMENT_SPAN;
        }
    }

    public boolean isPreserveReadNames() {
        return preserveReadNames;
    }

    public void setPreserveReadNames(final boolean preserveReadNames) {
        this.preserveReadNames = preserveReadNames;
    }
}
