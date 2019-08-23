package htsjdk.samtools;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.build.ContainerFactory;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

//TODO: test mate recovery for detached/not for both coord-sorted/not
//TODO: are mates recovered if the mate winds up in a separate container altogether ??

/**
 * Class for writing SAMRecords into a series of CRAM containers on an output stream.
 */
public class CRAMContainerStreamWriter {
    //private static final Log log = Log.getInstance(CRAMContainerStreamWriter.class);
    private static final Version CRAM_VERSION = CramVersions.DEFAULT_CRAM_VERSION;
    private static final int MIN_SINGLE_REF_RECORDS = 1000;

    private final CRAMEncodingStrategy encodingStrategy;
    private final SAMFileHeader samFileHeader;
    final Map<String, Integer> readGroupMap = new HashMap<>();

    private final String cramID;
    private final OutputStream outputStream;
    private final CRAMReferenceSource cramReferenceSource;

    private final List<SAMRecord> samRecords = new ArrayList<>();
    private final ContainerFactory containerFactory;
    private final CRAMIndexer indexer;

    private int currentReferenceContext = ReferenceContext.UNINITIALIZED_REFERENCE_ID;
    private long streamOffset = 0;

    private final int maxRecordsPerContainer;

    /**
     * Create a CRAMContainerStreamWriter for writing SAM records into a series of CRAM
     * containers on output stream, with an optional index.
     *
     * @param outputStream where to write the CRAM stream.
     * @param indexStream where to write the output index. Can be null if no index is required.
     * @param source reference cramReferenceSource
     * @param samFileHeader {@link SAMFileHeader} to be used. Sort order is determined by the sortOrder property of this arg.
     * @param cramId used for display in error message display
     */
    public CRAMContainerStreamWriter(
            final OutputStream outputStream,
            final OutputStream indexStream,
            final CRAMReferenceSource source,
            final SAMFileHeader samFileHeader,
            final String cramId) {
        this(outputStream, source, samFileHeader, cramId, indexStream == null ? null : new CRAMBAIIndexer(indexStream, samFileHeader));
    }

    /**
     * Create a CRAMContainerStreamWriter for writing SAM records into a series of CRAM
     * containers on output stream, with an optional index.
     *
     * @param outputStream where to write the CRAM stream.
     * @param source reference cramReferenceSource
     * @param samFileHeader {@link SAMFileHeader} to be used. Sort order is determined by the sortOrder property of this arg.
     * @param cramId used for display in error message display
     * @param indexer CRAM indexer. Can be null if no index is required.
     */
    public CRAMContainerStreamWriter(
            final OutputStream outputStream,
            final CRAMReferenceSource source,
            final SAMFileHeader samFileHeader,
            final String cramId,
            final CRAMIndexer indexer) {
        this(new CRAMEncodingStrategy(), source, samFileHeader, outputStream, indexer, cramId);
    }

    /**
     * Create a CRAMContainerStreamWriter for writing SAM records into a series of CRAM
     * containers on output stream, with an optional index.
     *
     * @param encodingStrategy encoding strategy values
     * @param referenceSource reference cramReferenceSource
     * @param samFileHeader {@link SAMFileHeader} to be used. Sort order is determined by the sortOrder property of this arg.
     * @param outputStream where to write the CRAM stream.
     * @param indexer CRAM indexer. Can be null if no index is required.
     * @param streamIdentifier informational string included in error reporting
     */
    public CRAMContainerStreamWriter(
            final CRAMEncodingStrategy encodingStrategy,
            final CRAMReferenceSource referenceSource,
            final SAMFileHeader samFileHeader,
            final OutputStream outputStream,
            final CRAMIndexer indexer,
            final String streamIdentifier) {
        this.encodingStrategy = encodingStrategy;
        this.cramReferenceSource = referenceSource;
        this.samFileHeader = samFileHeader;
        this.outputStream = outputStream;
        this.cramID = streamIdentifier;
        this.indexer = indexer;
        containerFactory = new ContainerFactory(samFileHeader, encodingStrategy);
        maxRecordsPerContainer = this.encodingStrategy.getRecordsPerSlice() * this.encodingStrategy.getSlicesPerContainer();

        // create our read group id map
        final List<SAMReadGroupRecord> readGroups = samFileHeader.getReadGroups();
        for (int i = 0; i < readGroups.size(); i++) {
            final SAMReadGroupRecord readGroupRecord = readGroups.get(i);
            readGroupMap.put(readGroupRecord.getId(), i);
        }
    }

    /**
     * Accumulate alignment records until we meet the threshold to flush a container.
     * @param alignment must not be null
     */
    public void writeAlignment(final SAMRecord alignment) {
        final int nextReferenceContext = alignment.getReferenceIndex();
        if (shouldFlushRecords(nextReferenceContext)) {
            flushRecords();
            samRecords.clear();
            currentReferenceContext = ReferenceContext.UNINITIALIZED_REFERENCE_ID;
        }
        currentReferenceContext = getNewReferenceContext(nextReferenceContext);
        samRecords.add(alignment);
    }

    /**
     * Write a CRAM file header and the previously provided SAM header to the stream.
     */
    // TODO: retained for BWC with direct users of CRAMContainerStreamWriter such as disq
    public void writeHeader(final SAMFileHeader requestedSAMFileHeader) {
        final CramHeader cramHeader = new CramHeader(CRAM_VERSION, cramID, requestedSAMFileHeader);
        streamOffset = CramIO.writeCramHeader(cramHeader, outputStream);
    }

    /**
     * Write a CRAM file header and the previously provided SAM header to the stream.
     */
    public void writeHeader() {
        final CramHeader cramHeader = new CramHeader(CRAM_VERSION, cramID, samFileHeader);
        streamOffset = CramIO.writeCramHeader(cramHeader, outputStream);
    }

    /**
     * Finish writing to the stream. Flushes the record cache and optionally emits an EOF container.
     * @param writeEOFContainer true if an EOF container should be written. Only use false if writing a CRAM file
     *                          fragment which will later be aggregated into a complete CRAM file.
     */
    public void finish(final boolean writeEOFContainer) {
        try {
            if (!samRecords.isEmpty()) {
                flushRecords();
            }
            if (writeEOFContainer) {
                CramIO.issueEOF(CRAM_VERSION, outputStream);
            }
            outputStream.flush();
            if (indexer != null) {
                indexer.finish();
            }
            outputStream.close();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    // TODO: should the spec allow embedded references for multi-ref containers (or more specifically, should it
    // TODO: be allowed for non-coord sorted inputs) ?

    /**
     * Decide if the current records should be flushed based on number of records and whether the
     * reference sequence id has changed.
     *
     * @param nextReferenceContext
     * @return true if the current container should be flushed and the following records should go into a new container; false otherwise.
     */
    protected boolean shouldFlushRecords(final int nextReferenceContext) {
        if (samRecords.isEmpty() || currentReferenceContext == ReferenceContext.UNINITIALIZED_REFERENCE_ID) {
            return false;
        }

        if (samRecords.size() >= maxRecordsPerContainer) {
            return true;
        }

        // we have fewer than maxRecordsPerContainer, so if we're not coord sorted, keep going
        if (samFileHeader.getSortOrder() != SAMFileHeader.SortOrder.coordinate) {
            return false;
        }

        // we're coord-sorted, and have < recordsPerContainer

        //TODO: why ? unmapped reads can go into multi-ref containers...
        // make unmapped reads don't get into multiref containers:
        if (currentReferenceContext != ReferenceContext.UNMAPPED_UNPLACED_ID &&
                nextReferenceContext == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            return true;
        }

        if (currentReferenceContext == nextReferenceContext || currentReferenceContext == ReferenceContext.MULTIPLE_REFERENCE_ID) {
            return false;
        }

        // we're singleRef, but the reference context has changed
        // Protection against too small containers: flush at least X single refs, switch to multiref otherwise.
        if (samRecords.size() > MIN_SINGLE_REF_RECORDS) {
            return true;
        }

        return false;
    }

    /**
     * Check if the reference has changed.
     *
     * @param samRecordReferenceIndex index of the new reference sequence
     */
    //TODO: old comment says...and create a new record factory using the new reference ????
    private int getNewReferenceContext(final int samRecordReferenceIndex) {
        if (currentReferenceContext == ReferenceContext.UNINITIALIZED_REFERENCE_ID) {
            return samRecordReferenceIndex;
        } else if (currentReferenceContext == ReferenceContext.MULTIPLE_REFERENCE_ID) {
            return currentReferenceContext;
        } else if (currentReferenceContext != samRecordReferenceIndex) {
            return ReferenceContext.MULTIPLE_REFERENCE_ID;
        } else {
            return currentReferenceContext;
        }
    }

    // Slices with the Multiple Reference flag (-2) set as the sequence ID in the header may contain reads mapped to
    // multiple external references, including unmapped reads (placed on these references or unplaced), but multiple
    // embedded references cannot be combined in this way. When multiple references are used, the RI data series will
    // be used to determine the reference sequence ID for each record. This data series is not present when only a
    // single reference is used within a slice.
    //
    // The Unmapped (-1) sequence ID in the header is for slices containing only unplaced unmapped3 reads.
    // A slice containing data that does not use the external reference in any sequence may set the reference MD5 sum
    // to zero. This can happen because the data is unmapped or the sequence has been stored verbatim instead of via
    // reference-differencing.
    /**
     * Complete the current container and flush it to the output stream.
     */
    protected void flushRecords() {

        // get the reference bases for the current reference context (even though it might not match the first
        // record we're about to convert
        byte[] referenceBases = getReferenceBaseForRecords(samFileHeader, currentReferenceContext, cramReferenceSource);

        final List<CRAMRecord> cramRecords = new ArrayList<>(samRecords.size());

        int index = 0;
        for (final SAMRecord samRecord : samRecords) {
            if (samRecord.getReferenceIndex() != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX &&
                    currentReferenceContext != samRecord.getReferenceIndex()) {
                // this may load all ref sequences into memory:
                referenceBases = cramReferenceSource.getReferenceBases(
                        samFileHeader.getSequence(samRecord.getReferenceIndex()),
                        true);
            }
            if (samRecord.getHeader() == null) {
                samRecord.setHeader(samFileHeader);
            }
            cramRecords.add(
                    new CRAMRecord(
                            CRAM_VERSION,
                            encodingStrategy,
                            samRecord,
                            referenceBases,
                            ++index,
                            readGroupMap)
            );
        }

        // TODO: this mate processing assumes that all of these records wind up in the same slice!!!!;
        // TODO: it will need to be updated when the container/slice partitioning is updated
        if (samFileHeader.getSortOrder() == SAMFileHeader.SortOrder.coordinate) {
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

        // TODO: Use this code (previously inline in CRAMContainerStreamWriter)
        // as test for validation
//    /**
//     * The following passage is for paranoid mode only. When java is run with asserts on it will throw an {@link AssertionError} if
//     * read bases or quality scores of a restored SAM record mismatch the original. This is effectively a runtime round trip test.
//     */
//    @SuppressWarnings("UnusedAssignment") boolean assertsEnabled = false;
//    //noinspection AssertWithSideEffects,ConstantConditions
//    assert assertsEnabled = true;
//    //noinspection ConstantConditions
//    if (assertsEnabled) {
//        final Cram2SamRecordFactory f = new Cram2SamRecordFactory(samFileHeader);
//        for (int i = 0; i < samRecords.size(); i++) {
//            final SAMRecord restoredSamRecord = f.create(cramRecords.get(i));
//            assert (restoredSamRecord.getAlignmentStart() == samRecords.get(i).getAlignmentStart());
//            assert (restoredSamRecord.getReferenceName().equals(samRecords.get(i).getReferenceName()));
//
//            if (!restoredSamRecord.getReadString().equals(samRecords.get(i).getReadString())) {
//                // try to fix the original read bases by normalizing them to BAM set:
//                final byte[] originalReadBases = samRecords.get(i).getReadString().getBytes();
//                final String originalReadBasesUpperCaseIupacNoDot = new String(SequenceUtil.toBamReadBasesInPlace(originalReadBases));
//                assert (restoredSamRecord.getReadString().equals(originalReadBasesUpperCaseIupacNoDot));
//            }
//            assert (restoredSamRecord.getBaseQualityString().equals(samRecords.get(i).getBaseQualityString()));
//        }
//    }

        //TODO: these records need to be broken up into groups with like reference context types, since if there is
        //TODO: more than one type present, they need to be split across multiple containers
        //TODO: this should really accumulate records to a slice, not a container, and then submit the slices
        //TODO: one at a time to container and let it aggregate and write them out as needed
        final Container container = containerFactory.buildContainer(cramRecords, streamOffset);
        for (final Slice slice : container.getSlices()) {
            //TODO: this is setting a reference MD5 even if the slice is multi-ref...
            //TODO: also, suppress this for embedded references...
            slice.setRefMD5(referenceBases);
        }
        streamOffset += ContainerIO.writeContainer(CRAM_VERSION, container, outputStream);
        if (indexer != null) {
            // using silent validation here because the reads have been through validation already or
            // they have been generated somehow through the htsjdk
            //TODO: do we need this....?
            indexer.processContainer(container, ValidationStringency.SILENT);
        }
    }

    private static byte[] getReferenceBaseForRecords(
            final SAMFileHeader samFileHeader,
            final int currentReferenceContext,
            final CRAMReferenceSource referenceSource) {
        final byte[] referenceBases;
        switch (currentReferenceContext) {
            case ReferenceContext.UNINITIALIZED_REFERENCE_ID:
                throw new CRAMException("Uninitialized reference context state in flushRecords");
            case ReferenceContext.MULTIPLE_REFERENCE_ID:
                referenceBases = new byte[0];
                break;
            case ReferenceContext.UNMAPPED_UNPLACED_ID:
                referenceBases = new byte[0];
                break;
            default:
                final SAMSequenceRecord sequence = samFileHeader.getSequence(currentReferenceContext);
                referenceBases = referenceSource.getReferenceBases(sequence, true);
                break;
        }
        return referenceBases;
    }

}
