package htsjdk.samtools.cram.build;

import htsjdk.samtools.*;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.CRAMEncodingStrategy;
import htsjdk.samtools.cram.structure.CRAMRecord;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.Slice;

import java.util.*;
import java.util.stream.Collectors;

public class SliceFactory {
    private final CRAMEncodingStrategy encodingStrategy;

    private final List<SliceEntry> cramRecordSliceEntries;
    private final CRAMReferenceState cramReferenceState;

    private final int maxRecordsPerSlice;
    private final int minimumSingleReferenceSliceThreshold;
    private final boolean coordinateSorted;

    private final Map<String, Integer> readGroupNameToID = new HashMap<>();

    public SliceFactory(
            final CRAMEncodingStrategy cramEncodingStrategy,
            final CRAMReferenceSource cramReferenceSource,
            final SAMFileHeader samFileHeader) {
        this.encodingStrategy = cramEncodingStrategy;
        this.cramReferenceState = new CRAMReferenceState(cramReferenceSource, samFileHeader);

        minimumSingleReferenceSliceThreshold = encodingStrategy.getMinimumSingleReferenceSliceSize();
        maxRecordsPerSlice = this.encodingStrategy.getReadsPerSlice();
        this.coordinateSorted = samFileHeader.getSortOrder() == SAMFileHeader.SortOrder.coordinate;

        cramRecordSliceEntries = new ArrayList<>(this.encodingStrategy.getSlicesPerContainer());

        // create our read group id map
        final List<SAMReadGroupRecord> readGroups = samFileHeader.getReadGroups();
        for (int i = 0; i < readGroups.size(); i++) {
            final SAMReadGroupRecord readGroupRecord = readGroups.get(i);
            readGroupNameToID.put(readGroupRecord.getId(), i);
        }
    }

    /**
     * Add a new slice entry, and return the number of sliceEntries.
     *
     * @param currentReferenceContextID
     * @param sliceSAMRecords
     * @return
     */
    public void addSliceEntry(final int currentReferenceContextID, final List<SAMRecord> sliceSAMRecords) {
        cramRecordSliceEntries.add(
                new SliceEntry(
                        currentReferenceContextID,
                        convertToCRAMRecords(sliceSAMRecords),
                        cramReferenceState.getCurrentReferenceBases())
        );
    }

    //convert the SAMRecords to CRAMRecords, and return as a list
    public List<CRAMRecord> getCRAMRecords() {
        // Create a list of ALL reads from all accumulated slices (used to create the container
        // compression header, which must be presented with ALL reads that will be included in the
        // container, no matter how they may be distributed across slices. So if more than one slice
        // entry has been accumulated, we need to temporarily stream all the records into a single
        // list to present to compressionHeaderFactory.
        return cramRecordSliceEntries.size() > 1 ?
                cramRecordSliceEntries.stream().flatMap(e -> e.records.stream()).collect(Collectors.toList()) :
                cramRecordSliceEntries.get(0).getRecords();
    }

    public int getNumberOfSliceEntries() {
        return cramRecordSliceEntries.size();
    }

    public List<Slice> getSlices(
            final CompressionHeader compressionHeader,
            final long containerByteOffset,
            final long globalRecordCounter) {
        long sliceRecordCounter = globalRecordCounter;
        final List<Slice> slices = new ArrayList<>(cramRecordSliceEntries.size());
        for (final SliceEntry sliceEntry : cramRecordSliceEntries) {
            final Slice slice = new Slice(
                    sliceEntry.getRecords(),
                    compressionHeader,
                    containerByteOffset,
                    sliceRecordCounter
            );
            slice.setRefMD5(cramReferenceState.getCurrentReferenceBases());
            slices.add(slice);
            sliceRecordCounter += sliceEntry.getRecords().size();
        }
        cramRecordSliceEntries.clear();
        return slices;
    }

    private final List<CRAMRecord> convertToCRAMRecords(final List<SAMRecord> samRecords) {
        int recordIndex = 0;
        final List<CRAMRecord> cramRecords = new ArrayList<>();
        for (final SAMRecord samRecord : samRecords) {
            int referenceIndex = samRecord.getReferenceIndex();
            final CRAMRecord cramRecord = new CRAMRecord(
                    CramVersions.DEFAULT_CRAM_VERSION,
                    encodingStrategy,
                    samRecord,
                    cramReferenceState.getReferenceBases(referenceIndex),
                    ++recordIndex,
                    readGroupNameToID);
            cramRecords.add(cramRecord);
        }
        resolveMatesForSlice(cramRecords);
        return cramRecords;
    }

    // Note: this mate processing assumes that all of these records wind up in the same slice!!
    private void resolveMatesForSlice(final List<CRAMRecord> cramRecords) {
        if (coordinateSorted) {
            final Map<String, CRAMRecord> primaryMateMap = new TreeMap<>();
            final Map<String, CRAMRecord> secondaryMateMap = new TreeMap<>();
            for (final CRAMRecord r : cramRecords) {
                if (r.isMultiFragment()) {
                    final Map<String, CRAMRecord> mateMap =
                            r.isSecondaryAlignment() ?
                                    primaryMateMap :
                                    secondaryMateMap;
                    CRAMRecord mate = mateMap.get(r.getReadName());
                    mate = rejectBadMateChoice(mate, r);
                    if (mate == null) {
                        if (r.isSecondaryAlignment()) {
                            secondaryMateMap.put(r.getReadName(), r);
                        } else {
                            primaryMateMap.put(r.getReadName(), r);
                        }
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

    //TODO: We want to make sure that we choose the next mate in the fragment sequence to preserve
    //the original order. If we just choose the first name that matches, it might not be the right one.
    //So test the candidate mate to see if it matches the mate properties specified in the original
    //read.
    //TODO: we might want to move this method to CRAMRecord...
    private CRAMRecord rejectBadMateChoice(final CRAMRecord firstMate, final CRAMRecord candidateMate) {
        if (firstMate == null) {
            return null;
        }
        //if the first mate's mate is on the reverse strand, and the candidate isn't, reject it
        if ((firstMate.getBAMFlags() & SAMFlag.MATE_REVERSE_STRAND.intValue()) != 0 &&
                (candidateMate.getBAMFlags() & SAMFlag.READ_REVERSE_STRAND.intValue()) == 0) {
            return null;
        }

        //if the first mate's mate is unmapped, and the candidate is mapped, reject it
        if ((firstMate.getBAMFlags() & SAMFlag.MATE_UNMAPPED.intValue()) != 0 &&
                (candidateMate.getBAMFlags() & SAMFlag.READ_UNMAPPED.intValue()) == 0) {
            return null;
        }

        if  (candidateMate.getAlignmentStart() != firstMate.getMateAlignmentStart()) {
            return null;
        }
        return candidateMate;
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
    public int shouldEmitSlice(
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

    // We can't create a slice until we have a compression header, and we can't create a compression
    // header until we've seen all records that will live in a container. So we use SliceEntry to
    // accumulate sets of records that represent will slice when we're ready to create the actual
    // container and it's slices.
    public class SliceEntry {
        private final List<CRAMRecord> records;
        private final ReferenceContext referenceContext;

        public SliceEntry(final int referenceContextID, final List<CRAMRecord> sourceRecords, final byte[] refMD5) {
            this.records = new ArrayList<>(sourceRecords);
            this.referenceContext = new ReferenceContext(referenceContextID);
        }
        public ReferenceContext getReferenceContext() {
            return referenceContext;
        }
        public List<CRAMRecord> getRecords() {
            return records;
        }
    }
}
