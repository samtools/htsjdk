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

/**
 * Aggregates SAMRecord objects into one or more {@link Container}s, composed of one or more {@link Slice}s.
 * based on a set of rules implemented by this class in combination with the parameter values provided via a
 * {@link CRAMEncodingStrategy} object.
 *
 * The general call pattern is to pass records in one at a time, and process Containers as they are returned:
 *
 * <pre>{@code
 *  long containerOffset = initialOffset; // after writing header, etc
 *  ContainerFactory containerFactory = new ContainerFactory(...)
 *  // retrieve input records and obtain/emit Containers as they are produced by the factory...
 *  while (inputSAM.hasNext() {
 *     Container container = containerFactory.getNextContainer(inputSAM.next, containerOffset);
 *     if (container != null) {
 *         containerOffset = writeContainer(container...)
 *     }
 *  }
 *
 *  // if there is a final Container, retrieve and emit it
 *  Container finalContainer = containerFactory.getFinalContainer(containerOffset);
 *  if (finalContainer != null) {
 *      containers.add(finalContainer);
 *  }
 *  }
 * </pre>
 * Multiple slices are only aggregated into a single container if slices/container is > 1, *and* all of the
 * slices are SINGLE_REFERENCE and have the same (mapped) reference context. MULTI_REFERENCE slices are never
 * aggregated with other slices into a single container, no matter how many slices/container are requested,
 * since it can be very inefficient to do so (the spec requires that if any slice in a container is
 * multiple-reference, all slices in the container must also be MULTI_REFERENCE).
 *
 * For coordinate sorted inputs, a MULTI_REFERENCE slice is only created when there are not enough reads mapped
 * to a single reference sequence to reach the MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD. This usually only happens
 * near the end of the reads mapped to a given sequence. When that happens, a small MULTI_REFERENCE slice for the
 * remaining reads mapped to the previous sequence, plus some subsequent records are accumulated until
 * MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD is hit, and the resulting MULTI_REFERENCE slice will be emitted into
 * it's own container.
 */
public final class ContainerFactory {
    private final CRAMEncodingStrategy encodingStrategy;
    private final CompressionHeaderFactory compressionHeaderFactory;
    private final SliceFactory sliceFactory;

    private final SAMFileHeader samFileHeader;
    private final boolean coordinateSorted;
    private final List<SAMRecord> sliceSAMRecords;

    private long globalRecordCounter = 0;
    private int currentReferenceContextID = ReferenceContext.UNINITIALIZED_REFERENCE_ID;

    /**
     * @param samFileHeader the {@link SAMFileHeader} (used to determine sort order and resolve read groups)
     * @param encodingStrategy the {@link CRAMEncodingStrategy} parameters to use
     * @param referenceSource the {@link CRAMReferenceSource} to use for containers created by this factory
     */
    public ContainerFactory(
            final SAMFileHeader samFileHeader,
            final CRAMEncodingStrategy encodingStrategy,
            final CRAMReferenceSource referenceSource) {
        this.encodingStrategy = encodingStrategy;
        this.samFileHeader = samFileHeader;
        this.coordinateSorted = samFileHeader.getSortOrder() == SAMFileHeader.SortOrder.coordinate;
        compressionHeaderFactory = new CompressionHeaderFactory(encodingStrategy);
        sliceFactory = new SliceFactory(encodingStrategy, referenceSource, samFileHeader, globalRecordCounter);
        sliceSAMRecords = new ArrayList<>(this.encodingStrategy.getReadsPerSlice());
    }

    /**
     * Add a new {@link SAMRecord} object to the factory, obtaining a {@link Container} if one is returned.
     *
     * @param samRecord the next SAMRecord to be written
     * @param containerByteOffset the byte offset to record in the Container if one is created
     * @return a {@link Container} if the threshold for emitting a {@link Container} has been reached, otherwise null
     */
    public final Container getNextContainer(final SAMRecord samRecord, final long containerByteOffset) {
        Container container = null;

        if (samRecord.getHeader() == null) {
            samRecord.setHeaderStrict(samFileHeader);
        }
        final int nextRecordIndex = samRecord.getReferenceIndex();

        // get the updated reference context to determine if we should emit a slice with the accumulated records...
        final int updatedReferenceContextID = sliceFactory.getUpdatedReferenceContext(
                currentReferenceContextID,
                nextRecordIndex,
                sliceSAMRecords.size());

        if (shouldEmitSlice(updatedReferenceContextID)) {
            sliceFactory.createNewSliceEntry(currentReferenceContextID, sliceSAMRecords);
            sliceSAMRecords.clear();

            if (shouldEmitContainer(
                    currentReferenceContextID,
                    nextRecordIndex,
                    sliceFactory.getNumberOfSliceEntries())) {
                container = makeContainer(containerByteOffset);
            }
            currentReferenceContextID = nextRecordIndex;
        } else {
            currentReferenceContextID = updatedReferenceContextID;
        }

        sliceSAMRecords.add(samRecord);
        return container;
    }

    /**
     * Obtain a {@link Container} from any remaining accumulated SAMRecords, if any.
     *
     * @param containerByteOffset the byte offset to record in the newly emitted {@link Container} if one is created
     * @return a {@link Container} if any record have been accumulated, otherwise null
     */
    public Container getFinalContainer(final long containerByteOffset) {
        // write a final slice, if any, and a final container, if there are any slices
        if (sliceSAMRecords.size() > 0) {
            sliceFactory.createNewSliceEntry(currentReferenceContextID, sliceSAMRecords);
            sliceSAMRecords.clear();
        }
        if (sliceFactory.getNumberOfSliceEntries() != 0) {
            final Container container = makeContainer(containerByteOffset);
            currentReferenceContextID = ReferenceContext.UNINITIALIZED_REFERENCE_ID;
            return container;
        }
        currentReferenceContextID = ReferenceContext.UNINITIALIZED_REFERENCE_ID;
        return null;
    }

    /**
     * Determine if a Container should be emitted based on the current reference context and the reference
     * context for the next record to be processed, and the encoding strategy parameters.
     *
     * A container is emitted if:
     *
     *   - the requested number of slices per container has been reached, or
     *   - a multi-reference slice has been accumulated (a multi-ref slice will always be emitted into it's own
     *     container as soon as it's generated, since we dont want to confer multi-ref-ness on the
     *     next slice, which might otherwise be single-ref), or
     *   - we haven't reached the requested number of slices, but we're changing reference
     *     contexts and we don't want to create a MULTI-REF container out of two or more SINGLE_REF
     *     slices with different contexts, since by the spec we'd be forced to call that container MULTI-REF,
     *     and thus the slices would have to be multi-ref. So instead emit a single ref container
     *
     * @param currentReferenceContextID
     * @param nextRecordIndex
     * @param numberOfSliceEntries
     * @return true if a {@link Container}should be emitted, otherwise false
     */
    public boolean shouldEmitContainer(
            final int currentReferenceContextID,
            final int nextRecordIndex,
            final int numberOfSliceEntries) {
        return numberOfSliceEntries >= encodingStrategy.getSlicesPerContainer() ||
            currentReferenceContextID == ReferenceContext.MULTIPLE_REFERENCE_ID ||
            currentReferenceContextID != nextRecordIndex;
    }

    /**
     * Return true if the updated reference context indicates that we should emit a slice and
     * start accumulating a new slice
     */
    private boolean shouldEmitSlice(final int updatedReferenceContextID) {
        return updatedReferenceContextID == ReferenceContext.UNINITIALIZED_REFERENCE_ID;
    }

    /**
     * Build a Container (and its constituent Slices) from {@link CRAMCompressionRecord}s.
     * Note that this will always result in a single Container, regardless of how many Slices
     * are created.  It is up to the caller to divide the records into multiple Containers,
     * if that is desired.
     *
     * @param containerByteOffset the Container's byte offset from the start of the stream
     * @return the container built from the records
     */
    private final Container makeContainer(final long containerByteOffset) {
        ValidationUtils.validateArg(
                sliceFactory.getNumberOfSliceEntries() != 0,
                "must have slice entries to create a container");

        // Create the compression header, then convert to slices. The compression header  must
        // be presented with ALL reads that will be included in the container, no matter how
        // they may be distributed across slices.
        final CompressionHeader compressionHeader = compressionHeaderFactory.createCompressionHeader(
                sliceFactory.getCRAMRecordsForAllSlices(),
                coordinateSorted);
        final Container container = new Container(
                compressionHeader,
                sliceFactory.createSlices(compressionHeader, containerByteOffset),
                containerByteOffset,
                globalRecordCounter);
        globalRecordCounter += container.getContainerHeader().getNumberOfRecords();
        return container;
    }

}
