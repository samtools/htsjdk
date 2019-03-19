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
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.Slice;

import java.util.ArrayList;
import java.util.List;

public class ContainerFactory {
    private final SAMFileHeader samFileHeader;
    private final int recordsPerSlice; // default 10000; see CRAMContainerStreamWriter.DEFAULT_RECORDS_PER_SLICE
    private boolean preserveReadNames = true;
    private long globalRecordCounter = 0;

    public ContainerFactory(final SAMFileHeader samFileHeader, final int recordsPerSlice) {
        this.samFileHeader = samFileHeader;
        this.recordsPerSlice = recordsPerSlice;
    }

    /**
     * Build a Container (and its constituent Slices) from {@link CramCompressionRecord}s
     * @param records the records used to build the Container
     * @param containerByteOffset the Container's byte offset from the start of the stream
     * @return the container built from these records
     */
    public Container buildContainer(final List<CramCompressionRecord> records, final long containerByteOffset) {
        // sets header APDelta
        final boolean coordinateSorted = samFileHeader.getSortOrder() == SAMFileHeader.SortOrder.coordinate;
        final CompressionHeader compressionHeader = new CompressionHeaderFactory().build(records, null, coordinateSorted);

        compressionHeader.readNamesIncluded = preserveReadNames;

        final List<Slice> slices = new ArrayList<>();

        int baseCount = 0;
        long lastGlobalRecordCounter = globalRecordCounter;
        for (int i = 0; i < records.size(); i += recordsPerSlice) {
            final List<CramCompressionRecord> sliceRecords = records.subList(i,
                    Math.min(records.size(), i + recordsPerSlice));
            final Slice slice = Slice.buildSlice(sliceRecords, compressionHeader);
            slice.globalRecordCounter = globalRecordCounter;
            globalRecordCounter += slice.nofRecords;
            baseCount += slice.bases;
            slices.add(slice);
        }

        final Container container = Container.initializeFromSlices(slices, compressionHeader, containerByteOffset);
        container.nofRecords = records.size();
        container.globalRecordCounter = lastGlobalRecordCounter;
        container.blockCount = 0;
        container.bases += baseCount;
        return container;
    }

    public boolean isPreserveReadNames() {
        return preserveReadNames;
    }

    public void setPreserveReadNames(final boolean preserveReadNames) {
        this.preserveReadNames = preserveReadNames;
    }
}
