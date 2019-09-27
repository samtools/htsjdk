package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
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
    private final CRAMReferenceSource referenceSource;
    private final SAMFileHeader samFileHeader;

    private final List<SliceEntry> cramRecordSliceEntries;

    private final int maxRecordsPerSlice;
    private final int minimumSingleReferenceSliceThreshold;
    private final boolean coordinateSorted;
    private final Map<String, Integer> readGroupNameToID = new HashMap<>();
    private byte[] referenceBases = null; // cache the reference bases
    private int referenceBasesContextID = ReferenceContext.UNINITIALIZED_REFERENCE_ID;

    public SliceFactory(
            final CRAMEncodingStrategy cramEncodingStrategy,
            final CRAMReferenceSource cramReferenceSource,
            final SAMFileHeader samFileHeader) {
        this.encodingStrategy = cramEncodingStrategy;
        this.referenceSource = cramReferenceSource;
        this.samFileHeader = samFileHeader;

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
    public int addSliceEntry(final int currentReferenceContextID, final List<SAMRecord> sliceSAMRecords) {
        cramRecordSliceEntries.add(
                new SliceEntry(currentReferenceContextID, convertToCRAMRecords(sliceSAMRecords), referenceBases)
        );
        return cramRecordSliceEntries.size();
    }

    //convert the SAMRecords to CRAMRecords, and return as a list
    final List<CRAMRecord> getCRAMRecords() {
        // Create a list of ALL reads from all accumulated slices (used to create the container
        // compression header, which must be presented with ALL reads that will be included in the
        // container, no matter how they may be distributed across slices. So if more than one slice
        // entry has been accumulated, we need to temporarily stream all the records into a single
        // list to present to compressionHeaderFactory.
        return cramRecordSliceEntries.size() > 1 ?
                cramRecordSliceEntries.stream().flatMap(e -> e.records.stream()).collect(Collectors.toList()) :
                cramRecordSliceEntries.get(0).getRecords();
    }

    final int getNumberOfSLiceEntries() {
        return cramRecordSliceEntries.size();
    }

    final List<Slice> getSlices(
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
                    //, sliceEntry.getReferenceMD5()
            );
            slices.add(slice);
            sliceRecordCounter += sliceEntry.getRecords().size();
        }
        cramRecordSliceEntries.clear();
        return slices;
    }

    public final List<CRAMRecord> convertToCRAMRecords(final List<SAMRecord> samRecords) {
        int recordIndex = 0;
        final List<CRAMRecord> cramRecords = new ArrayList<>();
        for (final SAMRecord samRecord : samRecords) {
            int referenceIndex = samRecord.getReferenceIndex();
            final CRAMRecord cramRecord = new CRAMRecord(
                    CramVersions.DEFAULT_CRAM_VERSION,
                    encodingStrategy,
                    samRecord,
                    getReferenceBases(referenceIndex, referenceSource),
                    ++recordIndex,
                    readGroupNameToID);
            cramRecords.add(cramRecord);
        }
        resolveMatesForSlice(cramRecords);
        return cramRecords;
    }

    private byte[] getReferenceBases(final int referenceIndex, final CRAMReferenceSource referenceSource) {
        //TODO: for non-coord sorted this could cause a lot of thrashing
        if (referenceIndex != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            if (referenceBases == null ||
                    referenceBasesContextID == ReferenceContext.UNINITIALIZED_REFERENCE_ID ||
                    referenceIndex != referenceBasesContextID) {
                final SAMSequenceRecord sequence = samFileHeader.getSequence(referenceIndex);
                System.out.println(String.format("retrieving reference sequence for index %d", referenceIndex));
                referenceBases = referenceSource.getReferenceBases(sequence, true);
                referenceBasesContextID = referenceIndex;
            }
            return referenceBases;
        }

        // retain whatever cached reference bases we may have to minimize subsequent re-fetching
        return null;
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

    public class SliceEntry {
        private final List<CRAMRecord> records;
        private final ReferenceContext referenceContext;
        private final byte[] refMD5;

        public SliceEntry(final int referenceContextID, final List<CRAMRecord> sourceRecords, final byte[] refMD5) {
            this.records = new ArrayList<>(sourceRecords);
            this.referenceContext = new ReferenceContext(referenceContextID);
            this.refMD5 = refMD5;
        }
        public ReferenceContext getReferenceContext() {
            return referenceContext;
        }
        public List<CRAMRecord> getRecords() {
            return records;
        }
        public byte[] getReferenceMD5() { return refMD5; }
    }
}
