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
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.*;
import htsjdk.utils.ValidationUtils;

import java.util.*;

// TODO: this should be called ContainerBuilder

// NOTE: we don't ever put more than one MULTI_REF slice into a MULTI_REF container, even if the requested
// slices/container > 1, since it unnecessary and can be inefficient if the next slice is actually single-ref,
// which is common in coord-sorted. We only put multiple slices into a container if the all share the same
// (mapped) reference context.
//
// For coordinate sorted inputs, we only create a multi-ref slice if there are not enough reads mapped to a
// given reference sequence that we can't reach the MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD. For coordinate
// sorted, this usually happens after emitting several full containers of reads mapped to a given sequence,
// and only a few reads remain that are mapped to that sequence, followed by some reads that are mapped to a
// new sequence, or are unmapped. At that point we emit the small multi-ref slice that includes the remaining
// reads mapped to the previous sequence, plus some  of the new ones. Once we hit the
// MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD is hit, we the multi-ref slice into it's own container, and then
// resume, returning to single ref state.

public class ContainerFactory {
    private final CRAMEncodingStrategy encodingStrategy;
    private final CompressionHeaderFactory compressionHeaderFactory;
    private final SliceFactory sliceFactory;

    private final SAMFileHeader samFileHeader;
    private final boolean coordinateSorted;
    private final List<SAMRecord> sliceSAMRecords;

    private long globalRecordCounter = 0;
    private int currentReferenceContextID = ReferenceContext.UNINITIALIZED_REFERENCE_ID;

    /**
     * @param samFileHeader
     * @param encodingStrategy
     * @param referenceSource
     */
    public ContainerFactory(
            final SAMFileHeader samFileHeader,
            final CRAMEncodingStrategy encodingStrategy,
            final CRAMReferenceSource referenceSource) {
        this.encodingStrategy = encodingStrategy;
        this.samFileHeader = samFileHeader;

        this.coordinateSorted = samFileHeader.getSortOrder() == SAMFileHeader.SortOrder.coordinate;
        compressionHeaderFactory = new CompressionHeaderFactory(encodingStrategy);
        sliceFactory = new SliceFactory(encodingStrategy, referenceSource, samFileHeader);
        sliceSAMRecords = new ArrayList<>(this.encodingStrategy.getReadsPerSlice());
    }

    public final Container getNextContainer(final SAMRecord samRecord, final long containerByteOffset) {
        Container container = null;

        if (samRecord.getHeader() == null) {
            samRecord.setHeader(samFileHeader);
        }
        final int nextRecordIndex = samRecord.getReferenceIndex();

        // determine if we should emit a new slice...
        final int updatedReferenceContextID = sliceFactory.shouldEmitSlice(
                currentReferenceContextID,
                nextRecordIndex,
                sliceSAMRecords.size());

        if (updatedReferenceContextID == ReferenceContext.UNINITIALIZED_REFERENCE_ID) {
            sliceFactory.addSliceEntry(currentReferenceContextID, sliceSAMRecords);
            sliceSAMRecords.clear();

            if (shouldEmitContainer(
                    currentReferenceContextID,
                    nextRecordIndex,
                    sliceFactory.getNumberOfSLiceEntries())) {
                container = makeContainer(containerByteOffset);
            }
            currentReferenceContextID = nextRecordIndex;
        } else {
            currentReferenceContextID = updatedReferenceContextID;
        }

        sliceSAMRecords.add(samRecord);
        return container;
    }

    // We emit a container if:
    //  - we've reached the requested number of slices per container, or
    //  - we've accumulated a multi-reference slice (we always emit a multi-ref slice into it's own
    //    container as soon as it's  generated, since we dont want to confer multi-ref-ness on the
    //    next slice, which might otherwise be single-ref), or
    // - we haven't reached the requested number of slices, but we're changing reference
    //   contexts and we don't want to create a MULTI-REF container out of two or more SINGLE_REF
    //   slices with different contexts, since by the spec we'd be forced to call that container MULTI-REF,
    //   and thus the slices would have to be multi-ref. so instead we emit a single ref container
    public boolean shouldEmitContainer(
            final int currentReferenceContextID,
            final int nextRecordIndex,
            final int numberOfSliceEntries) {
        return numberOfSliceEntries == encodingStrategy.getSlicesPerContainer() ||
            currentReferenceContextID ==ReferenceContext.MULTIPLE_REFERENCE_ID ||
            currentReferenceContextID != nextRecordIndex;
    }

    public Container getFinalContainer(final long streamOffset) {
        // write a final slice, if any, and a final container, if there are any slices
        if (sliceSAMRecords.size() > 0) {
            sliceFactory.addSliceEntry(currentReferenceContextID, sliceSAMRecords);
            sliceSAMRecords.clear();
        }
        if (sliceFactory.getNumberOfSLiceEntries() != 0) {
            final Container container = makeContainer(streamOffset);
            currentReferenceContextID = ReferenceContext.UNINITIALIZED_REFERENCE_ID;
            return container;
        }
        currentReferenceContextID = ReferenceContext.UNINITIALIZED_REFERENCE_ID;
        return null;
    }

    /**
     * Build a Container (and its constituent Slices) from {@link CRAMRecord}s.
     * Note that this will always result in a single Container, regardless of how many Slices
     * are created.  It is up to the caller to divide the records into multiple Containers,
     * if that is desired.
     *
     //* @param samRecords the records used to build the Container
     * @param containerByteOffset the Container's byte offset from the start of the stream
     * @return the container built from these records
     */
    private final Container makeContainer(final long containerByteOffset) {
        ValidationUtils.validateArg(
                sliceFactory.getNumberOfSLiceEntries() != 0,
                "must have slice entries to create a container");

        // Create the compression header, then convert to slices. The compression header  must
        // be presented with ALL reads that will be included in the container, no matter how
        // they may be distributed across slices.
        final CompressionHeader compressionHeader = compressionHeaderFactory.build(
                sliceFactory.getCRAMRecords(),
                coordinateSorted);
        final Container container = new Container(
                compressionHeader,
                sliceFactory.getSlices(compressionHeader, containerByteOffset, globalRecordCounter),
                containerByteOffset,
                globalRecordCounter);
        globalRecordCounter += container.getContainerHeader().getNumberOfRecords();
        return container;
    }

}
