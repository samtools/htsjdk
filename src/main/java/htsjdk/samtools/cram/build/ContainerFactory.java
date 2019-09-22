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
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.*;
import htsjdk.utils.ValidationUtils;

import java.util.*;
import java.util.stream.Collectors;

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
    private final CRAMReferenceSource referenceSource;
    private final SAMFileHeader samFileHeader;
    private final Map<String, Integer> readGroupMap = new HashMap<>();
    private final boolean coordinateSorted;

    private final int maxRecordsPerSlice;
    private final int minimumSingleReferenceSliceThreshold;

    private final List<SAMRecord> sliceSAMRecords;
    private final List<SliceEntry<SAMRecord>> sliceEntries;
    private long globalRecordCounter = 0;

    private int currentReferenceContextID = ReferenceContext.UNINITIALIZED_REFERENCE_ID;

    private int referenceBasesContextID = ReferenceContext.UNINITIALIZED_REFERENCE_ID;
    // cache the reference bases
    private byte[] referenceBases;

    /**
     * @param samFileHeader
     * @param encodingStrategy
     */
    public ContainerFactory(
            final SAMFileHeader samFileHeader,
            final CRAMEncodingStrategy encodingStrategy,
            final CRAMReferenceSource referenceSource) {
        this.referenceSource = referenceSource;
        this.encodingStrategy = encodingStrategy;
        this.samFileHeader = samFileHeader;
        this.coordinateSorted = samFileHeader.getSortOrder() == SAMFileHeader.SortOrder.coordinate;

        compressionHeaderFactory = new CompressionHeaderFactory(encodingStrategy);
        minimumSingleReferenceSliceThreshold = encodingStrategy.getMinimumSingleReferenceSliceSize();
        maxRecordsPerSlice = this.encodingStrategy.getReadsPerSlice();
        sliceSAMRecords = new ArrayList<>(maxRecordsPerSlice);
        sliceEntries = new ArrayList<>(this.encodingStrategy.getSlicesPerContainer());

        // create our read group id map
        final List<SAMReadGroupRecord> readGroups = samFileHeader.getReadGroups();
        for (int i = 0; i < readGroups.size(); i++) {
            final SAMReadGroupRecord readGroupRecord = readGroups.get(i);
            readGroupMap.put(readGroupRecord.getId(), i);
        }
    }

    public final Container getNextContainer(final SAMRecord samRecord, final long containerByteOffset) {
        Container container = null;

        if (samRecord.getHeader() == null) {
            samRecord.setHeader(samFileHeader);
        }
        final int nextRecordIndex = samRecord.getReferenceIndex();

        // determine if we should emit a new slice...
        final int updatedReferenceContextID = shouldEmitSlice(currentReferenceContextID, nextRecordIndex, sliceSAMRecords.size());
        if (updatedReferenceContextID == ReferenceContext.UNINITIALIZED_REFERENCE_ID) {
            // save up our slice context and it's records, and then determine if we should emit
            // a container. Only write multiple slices to a container if they share a referenceContext.
            sliceEntries.add(new SliceEntry(currentReferenceContextID, sliceSAMRecords));
            // We emit a container if:
            //  - we've reached the requested number of slices per container, or
            //  - we've accumulated a multi-reference slice (we always emit a multi-ref slice into it's own
            //    container as soon as it's  generated, since we dont want to confer multi-ref-ness on the
            //    next slice, which might otherwise be single-ref), or
            // - we haven't reached the requested number of slices, but we're changing reference
            //   contexts and we don't want to create a MULTI-REF container out of two or more SINGLE_REF
            //   slices with different contexts, since by the spec we'd be forced to call that container MULTI-REF,
            //   and thus the slices would have to be multi-ref. so instead we emit a single ref container
            if (sliceEntries.size() == encodingStrategy.getSlicesPerContainer() ||
                    currentReferenceContextID == ReferenceContext.MULTIPLE_REFERENCE_ID ||
                    currentReferenceContextID != nextRecordIndex) {
                container = makeContainerFromSliceEntries(containerByteOffset);
            }
            sliceSAMRecords.clear();
            currentReferenceContextID = nextRecordIndex;
        } else {
            currentReferenceContextID = updatedReferenceContextID;
        }

        sliceSAMRecords.add(samRecord);
        return container;
    }

    public Container getFinalContainer(final long streamOffset) {
        // write a final slice, if any, and a final container, if there are any slices
        if (sliceSAMRecords.size() > 0) {
            sliceEntries.add(new SliceEntry(currentReferenceContextID, sliceSAMRecords));
            sliceSAMRecords.clear();
        }
        if (sliceEntries.size() != 0) {
            final Container container = makeContainerFromSliceEntries(streamOffset);
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
    private final Container makeContainerFromSliceEntries(final long containerByteOffset) {
        ValidationUtils.validateArg(sliceEntries.size() != 0, "must have slices to create a container");

        //convert the SAMRecords to CRAMRecords
        final List<SliceEntry<CRAMRecord>> cramSliceEntries = convertToCRAMRecords(sliceEntries);

        // Create the compression header. The compression header  must be presented with ALL
        // records that will be included in the container, no matter how they may be distributed
        // across slices, so if there is more than one slice requested, we need to temporarily
        // stream all the records into a single list to present to compressionHeaderFactory.
        final CompressionHeader compressionHeader = compressionHeaderFactory.build(
                cramSliceEntries.size() > 1 ?
                    cramSliceEntries.stream().flatMap(e -> e.records.stream()).collect(Collectors.toList()) :
                    cramSliceEntries.get(0).getRecords(),
                coordinateSorted);

        // now convert to slices
        long sliceRecordCounter = globalRecordCounter;
        final List<Slice> slices = new ArrayList<>(cramSliceEntries.size());
        for (final SliceEntry sliceEntry : cramSliceEntries) {
            final Slice slice = new Slice(sliceEntry.getRecords(), compressionHeader, containerByteOffset, sliceRecordCounter);
            if (slice.getAlignmentContext().getReferenceContext().isMappedSingleRef()) {
                //TODO: are these the correct bases ? in the multi-ref case ....?
                slice.setRefMD5(referenceBases);
            }
            slices.add(slice);
            sliceRecordCounter += sliceEntry.getRecords().size();
        }

        final Container container = new Container(compressionHeader, slices, containerByteOffset, globalRecordCounter);
        globalRecordCounter += container.getContainerHeader().getNumberOfRecords();
        sliceEntries.clear();
        return container;
    }

    // turn our SliceEntry<SAMRecord> into SliceEntry<CRAMRecord>
    private final List<SliceEntry<CRAMRecord>> convertToCRAMRecords(
            final List<SliceEntry<SAMRecord>> samRecordSliceEntries) {
        final List<SliceEntry<CRAMRecord>> cramSliceEntries = new ArrayList<>(samRecordSliceEntries.size());

        int recordIndex = 0;
        for (final SliceEntry<SAMRecord> samSliceEntry : samRecordSliceEntries) {
            final List<CRAMRecord> cramRecords = new ArrayList<>();
             for (final SAMRecord samRecord : samSliceEntry.getRecords()) {
                int referenceIndex = samRecord.getReferenceIndex();
                final CRAMRecord cramRecord = new CRAMRecord(
                        CramVersions.DEFAULT_CRAM_VERSION,
                        encodingStrategy,
                        samRecord,
                        getReferenceBases(referenceIndex, referenceSource),
                        ++recordIndex,
                        readGroupMap);
                cramRecords.add(cramRecord);
            }
            resolveMatesForSlice(cramRecords);
            cramSliceEntries.add(new SliceEntry(samSliceEntry.getReferenceContext().getReferenceContextID(), cramRecords));
        }
        return cramSliceEntries;
    }

    private byte[] getReferenceBases(final int referenceIndex, final CRAMReferenceSource referenceSource) {
        //TODO: this may load all ref sequences into memory:
        // should this also filter on unmapped so that unmapped/placed don't pull in a reference contig ?
        if (referenceIndex != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            if (referenceBases == null ||
                    referenceBasesContextID == ReferenceContext.UNINITIALIZED_REFERENCE_ID ||
                    (referenceIndex != referenceBasesContextID)) {
                final SAMSequenceRecord sequence = samFileHeader.getSequence(referenceIndex);
                referenceBases = referenceSource.getReferenceBases(sequence, true);
                referenceBasesContextID = referenceIndex;
                return referenceBases;
            } else {
                return referenceBases;
            }
        }

        // retain whatever cached reference bases we may have to minimize subsequent re-fetching
        return null;
    }

    // Slices with the Multiple Reference flag (-2) set as the sequence ID in the header may contain reads mapped to
    // multiple external references, including unmapped reads (placed on these references or unplaced), but multiple
    // embedded references cannot be combined in this way. When multiple references are used, the RI data series will
    // be used to determine the reference sequence ID for each record. This data series is not present when only a
    // single reference is used within a slice.
    //
    // The Unmapped (-1) sequence ID in the header is for slices containing only unplaced unmapped reads.
    // A slice containing data that does not use the external reference in any sequence may set the reference MD5 sum
    // to zero. This can happen because the data is unmapped or the sequence has been stored verbatim instead of via
    // reference-differencing. This latter scenario is recommended for unsorted or non-coordinate-sorted data.

    /**
     * Decide if the current records should be flushed based on the current reference context, the reference context
     * for the next record to be written, and the number of records seen so far.
     *
     * @param nextReferenceIndex
     * @return ReferenceContext.UNINITIALIZED_REFERENCE_ID if a current container should be flushed and
     * subsequent records should go into a new container; otherwise the updated reference context.
     */
    //Assumes readsPerSlice >= minimumSingleReferenceSliceSize
    // Visible for testing
    int shouldEmitSlice(
            final int currentReferenceContext,
            final int nextReferenceIndex,
            final int numberOfSAMRecords) {
        switch (currentReferenceContext) {
            // uninitialized can go to: unmapped or mapped
            case ReferenceContext.UNINITIALIZED_REFERENCE_ID:
                if (numberOfSAMRecords != 0) {
                    throw new CRAMException(
                            "Reference context should have been initialized if records have previously been processed");
                }
                return nextReferenceIndex;

            case ReferenceContext.UNMAPPED_UNPLACED_ID:
                if (nextReferenceIndex == currentReferenceContext) {
                    // still unmapped...
                    return numberOfSAMRecords < maxRecordsPerSlice ?
                            ReferenceContext.UNMAPPED_UNPLACED_ID :
                            ReferenceContext.UNINITIALIZED_REFERENCE_ID;
                } else if (coordinateSorted) {
                    // coordinate sorted, and we're going from unmapped to mapped ??
                    throw new CRAMException("Invalid coord-sorted input - unmapped records must be last");
                } else {
                    // Not coordinate sorted, and we're going from unmapped to mapped, so allow the mapped
                    // record into the same slice with the unmapped ones, since there is no index query
                    // concern since we're not coord sorted anyway (though there is no reference compression
                    // happening in this container).
                    return numberOfSAMRecords >= maxRecordsPerSlice ?
                            ReferenceContext.UNINITIALIZED_REFERENCE_ID :
                            ReferenceContext.MULTIPLE_REFERENCE_ID;
                }

            case ReferenceContext.MULTIPLE_REFERENCE_ID:
                // This always accumulates a full multi-ref slice, but maybe it would be more efficient
                // to emit smaller multi-ref slices on the theory that the stream will get back on track
                // for single ref, at least for coord-sorted.
                if (coordinateSorted) {
                    return numberOfSAMRecords < minimumSingleReferenceSliceThreshold ?
                            ReferenceContext.MULTIPLE_REFERENCE_ID :
                            ReferenceContext.UNINITIALIZED_REFERENCE_ID; // emit a small mutli-ref
//                }
//                else if (nextReferenceIndex == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
//                    return numberOfSAMRecords >= maxRecordsPerSlice ?
//                            ReferenceContext.UNINITIALIZED_REFERENCE_ID :
//                            ReferenceContext.MULTIPLE_REFERENCE_ID;
                } else {
                    // multi-ref, not coord sorted
                    return numberOfSAMRecords >= maxRecordsPerSlice ?
                            ReferenceContext.UNINITIALIZED_REFERENCE_ID :
                            ReferenceContext.MULTIPLE_REFERENCE_ID;
                }

            default:
                // We're single-reference; so far everything we've seen is on a single reference contig
                if (nextReferenceIndex == currentReferenceContext) {
                    // still on the same reference contig
                    return numberOfSAMRecords >= maxRecordsPerSlice ?
                            ReferenceContext.UNINITIALIZED_REFERENCE_ID :
                            nextReferenceIndex;
                } else {
                    // switching to either a new reference contig, or to unmapped
                   return numberOfSAMRecords < minimumSingleReferenceSliceThreshold ?
                            ReferenceContext.MULTIPLE_REFERENCE_ID :
                            ReferenceContext.UNINITIALIZED_REFERENCE_ID;
                }
        }
    }

    // Note: this mate processing assumes that all of these records wind up in the same slice!!
    private void resolveMatesForSlice(final List<CRAMRecord> cramRecords) {
        if (coordinateSorted) {
            // mating:
            final Map<String, CRAMRecord> primaryMateMap = new TreeMap<>();
            final Map<String, CRAMRecord> secondaryMateMap = new TreeMap<>();
            for (final CRAMRecord r : cramRecords) {
                if (r.isMultiFragment()) {
                    final Map<String, CRAMRecord> mateMap =
                            r.isSecondaryAlignment() ?
                                    secondaryMateMap :
                                    primaryMateMap;
                    final CRAMRecord mate = mateMap.get(r.getReadName());
                    if (mate == null) {
                        mateMap.put(r.getReadName(), r);
                    } else {
                        mate.attachToMate(r);
                    }
                }
            }

            // mark unpredictable reads as detached:
            for (final CRAMRecord cramRecord : cramRecords) {
                cramRecord.updateDetachedState();
            }
        }
    }

    private static class SliceEntry<T> {
        final List<T> records;
        final ReferenceContext referenceContext;

        public SliceEntry(final int referenceContextID, final List<T> sourceRecords) {
            this.records = new ArrayList<>(sourceRecords);
            this.referenceContext = new ReferenceContext(referenceContextID);
        }
        public ReferenceContext getReferenceContext() {
            return referenceContext;
        }
        public List<T> getRecords() {
            return records;
        }
    }

}
