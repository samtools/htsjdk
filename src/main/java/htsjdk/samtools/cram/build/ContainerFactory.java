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
import htsjdk.samtools.cram.structure.*;

import java.util.ArrayList;
import java.util.List;

public class ContainerFactory {
    private final CRAMEncodingStrategy encodingStrategy;
    private final CompressionHeaderFactory compressionHeaderFactory;
    private final boolean coordinateSorted;
    private long globalRecordCounter = 0;

    public ContainerFactory(final SAMFileHeader samFileHeader, final CRAMEncodingStrategy encodingStrategy) {
        this.coordinateSorted = samFileHeader.getSortOrder() == SAMFileHeader.SortOrder.coordinate;
        compressionHeaderFactory = new CompressionHeaderFactory(encodingStrategy);
        this.encodingStrategy = encodingStrategy;
    }

    /**
     * Build a Container (and its constituent Slices) from {@link CRAMRecord}s.
     * Note that this will always result in a single Container, regardless of how many Slices
     * are created.  It is up to the caller to divide the records into multiple Containers,
     * if that is desired.
     *
     * @param records the records used to build the Container
     * @param containerByteOffset the Container's byte offset from the start of the stream
     * @return the container built from these records
     */
    public Container buildContainer(final List<CRAMRecord> records, final long containerByteOffset) {
        final CompressionHeader compressionHeader = compressionHeaderFactory.build(records, coordinateSorted);
        //TODO: CompressionHeader can just get this value from the EncodingStrategy
        compressionHeader.readNamesIncluded = encodingStrategy.getPreserveReadNames();

        // TODO: this code needs to handle the case where these slices have different sliceRefContexts
        // TODO: to prevent initializeFromSlices from throwing (i.e., if there is a single and a multi, or any
        // TODO: other combination)
        // break up the records into groups based on ref context, recodes/container, etc.

        final List<Slice> slices = new ArrayList<>();
        int baseCount = 0;
        long lastGlobalRecordCounter = globalRecordCounter;
        for (int i = 0; i < records.size(); i += encodingStrategy.getRecordsPerSlice()) {
            final List<CRAMRecord> sliceRecords = records.subList(
                    i,
                    Math.min(records.size(),
                            i + encodingStrategy.getRecordsPerSlice()));
            final Slice slice = new Slice(sliceRecords, compressionHeader);
            // TODO: we might be building more than one container here...
            slice.setGlobalRecordCounter(globalRecordCounter);
            globalRecordCounter += slice.getNofRecords();
            baseCount += slice.getBaseCount();
            slices.add(slice);
        }

        //TODO: blockCount and baseCount should be able to be derived from within the constructor based on the slices,
        //TODO: so they can be removed from this constuctor's args
        final Container container = new Container(
                compressionHeader,
                slices,
                containerByteOffset,
                lastGlobalRecordCounter,
                0,
                baseCount);

        return container;
    }
}
