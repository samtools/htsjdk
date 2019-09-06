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

// TODO: test mate recovery for detached/not for both coord-sorted/not
// TODO: are mates recovered if the mate winds up in a separate container altogether ??
// TODO: should the spec allow embedded references for multi-ref containers (or more specifically, should it
// TODO: be allowed for non-coord sorted inputs) ?
// TODO: non-coordinate sorted inputs

/**
 * Class for writing SAMRecords into a series of CRAM containers on an output stream, with an optional index.
 */
public class CRAMContainerStreamWriter {
    private static final Version CRAM_VERSION = CramVersions.DEFAULT_CRAM_VERSION;
    private static final int MIN_SINGLE_REF_RECORDS = 1000;

    private final CRAMEncodingStrategy encodingStrategy;
    private final OutputStream outputStream;
    private final String outputStreamIdentifier;
    private final SAMFileHeader samFileHeader;
    private final CRAMReferenceSource cramReferenceSource;

    private final ContainerFactory containerFactory;
    private final CRAMIndexer cramIndexer;
    private final List<SAMRecord> samRecords = new ArrayList<>();
    private final Map<String, Integer> readGroupMap = new HashMap<>();
    private final int maxRecordsPerContainer;

    private int currentReferenceContext = ReferenceContext.UNINITIALIZED_REFERENCE_ID;
    private long streamOffset = 0;

    /**
     * Create a CRAMContainerStreamWriter for writing SAM records into a series of CRAM
     * containers on output stream, with an optional index.
     *
     * @param recordOutputStream where to write the CRAM stream.
     * @param indexOutputStream where to write the output index. Can be null if no index is required.
     * @param source reference cramReferenceSource
     * @param samFileHeader {@link SAMFileHeader} to be used. Sort order is determined by the sortOrder property of this arg.
     * @param outputIdentifier used for display in error message display
     */
    public CRAMContainerStreamWriter(
            final OutputStream recordOutputStream,
            final OutputStream indexOutputStream,
            final CRAMReferenceSource source,
            final SAMFileHeader samFileHeader,
            final String outputIdentifier) {
        this(recordOutputStream,
                source,
                samFileHeader,
                outputIdentifier,
                indexOutputStream == null ?
                        null :
                        new CRAMBAIIndexer(indexOutputStream, samFileHeader)); // default to BAI index
    }

    /**
     * Create a CRAMContainerStreamWriter for writing SAM records into a series of CRAM
     * containers on output stream, with an optional index.
     *
     * @param outputStream where to write the CRAM stream.
     * @param source reference cramReferenceSource
     * @param samFileHeader {@link SAMFileHeader} to be used. Sort order is determined by the sortOrder property of this arg.
     * @param outputIdentifier used for display in error message display
     * @param indexer CRAM indexer. Can be null if no index is required.
     */
    public CRAMContainerStreamWriter(
            final OutputStream outputStream,
            final CRAMReferenceSource source,
            final SAMFileHeader samFileHeader,
            final String outputIdentifier,
            final CRAMIndexer indexer) {
        this(new CRAMEncodingStrategy(), source, samFileHeader, outputStream, indexer, outputIdentifier);
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
     * @param outputIdentifier informational string included in error reporting
     */
    public CRAMContainerStreamWriter(
            final CRAMEncodingStrategy encodingStrategy,
            final CRAMReferenceSource referenceSource,
            final SAMFileHeader samFileHeader,
            final OutputStream outputStream,
            final CRAMIndexer indexer,
            final String outputIdentifier) {
        this.encodingStrategy = encodingStrategy;
        this.cramReferenceSource = referenceSource;
        this.samFileHeader = samFileHeader;
        this.outputStream = outputStream;
        this.cramIndexer = indexer;
        this.outputStreamIdentifier = outputIdentifier;
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
        if (shouldWriteContainer(nextReferenceContext)) {
            writeContainer();
            samRecords.clear();
            currentReferenceContext = ReferenceContext.UNINITIALIZED_REFERENCE_ID;
        }
        currentReferenceContext = getUpdatedReferenceContext(nextReferenceContext);
        samRecords.add(alignment);
    }

    /**
     * Write a CRAM file header and the previously provided SAM header to the stream.
     */
    // TODO: retained for backward compatibility for disq in order to run GATK tests (remove before merging this branch)
    public void writeHeader(final SAMFileHeader requestedSAMFileHeader) {
        final CramHeader cramHeader = new CramHeader(CRAM_VERSION, outputStreamIdentifier, requestedSAMFileHeader);
        streamOffset = CramIO.writeCramHeader(cramHeader, outputStream);
    }

    /**
     * Write a CRAM file header and the previously provided SAM header to the stream.
     */
    public void writeHeader() {
        final CramHeader cramHeader = new CramHeader(CRAM_VERSION, outputStreamIdentifier, samFileHeader);
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
                writeContainer();
            }
            if (writeEOFContainer) {
                CramIO.issueEOF(CRAM_VERSION, outputStream);
            }
            outputStream.flush();
            if (cramIndexer != null) {
                cramIndexer.finish();
            }
            outputStream.close();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    // Get the updated reference context based on the current reference context and the reference index
    // for the next samRecord;
    private int getUpdatedReferenceContext(final int nextSAMRecordReferenceIndex) {
        switch (currentReferenceContext) {

            case ReferenceContext.UNINITIALIZED_REFERENCE_ID:
                return nextSAMRecordReferenceIndex;

            case ReferenceContext.UNMAPPED_UNPLACED_ID:
                return nextSAMRecordReferenceIndex == ReferenceContext.UNMAPPED_UNPLACED_ID ?
                        ReferenceContext.UNMAPPED_UNPLACED_ID :
                        ReferenceContext.MULTIPLE_REFERENCE_ID;

            case ReferenceContext.MULTIPLE_REFERENCE_ID:
                return ReferenceContext.MULTIPLE_REFERENCE_ID;

            default:
                return currentReferenceContext == nextSAMRecordReferenceIndex ?
                        currentReferenceContext :
                        samFileHeader.getSortOrder() == SAMFileHeader.SortOrder.coordinate ?
                                nextSAMRecordReferenceIndex :
                                ReferenceContext.MULTIPLE_REFERENCE_ID;
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
    // reference-differencing. This latter scenario is recommended for unsorted or non-coordinate-sorted data.
    /**
     * Complete the current container and flush it to the output stream.
     */
    /**
     * Decide if the current records should be flushed based on number of records and whether the reference
     * sequence id has changed.
     *
     * @param nextSAMRecordReferenceIndex
     * @return true if the current container should be flushed and the following records should go into a new
     * container; false otherwise.
     */
    protected boolean shouldWriteContainer(final int nextSAMRecordReferenceIndex) {
        switch (currentReferenceContext) {
            case ReferenceContext.UNINITIALIZED_REFERENCE_ID:
                return false;

            case ReferenceContext.UNMAPPED_UNPLACED_ID:
                if (nextSAMRecordReferenceIndex == currentReferenceContext) { // still unmapped...
                    return samRecords.size() >= maxRecordsPerContainer;
                } else if (samFileHeader.getSortOrder() == SAMFileHeader.SortOrder.coordinate) {
                    return samRecords.size() >= maxRecordsPerContainer;
                } else {
                    // allow the mapped records into the same container as the unmapped ones, since there
                    // is are no index query concerns because we're not coordinate sorted (though there is
                    // probably no reference compression happening in this container)
                    return samRecords.size() >= maxRecordsPerContainer;
                }

            case ReferenceContext.MULTIPLE_REFERENCE_ID:
                if (nextSAMRecordReferenceIndex == currentReferenceContext) {
                    return samRecords.size() >= maxRecordsPerContainer;
                } else if (nextSAMRecordReferenceIndex == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                    // TODO: special case to not allow unmapped records in with the multi-refs; this is mostly
                    // to prevent index queries from failing
                    return true;
                }
                // we've already switched to multi-ref, keep going until we hit maxRecordsPerContainer
                return samRecords.size() >= maxRecordsPerContainer;

            default:
                // so far everything we'e accumulated is on a single reference
                if (nextSAMRecordReferenceIndex == currentReferenceContext) {
                    // still on the same (single) reference
                    return samRecords.size() >= maxRecordsPerContainer;
                } else {
                    // switching to a new reference contig, or to unmapped
                    if (samFileHeader.getSortOrder() == SAMFileHeader.SortOrder.coordinate) {
                        // TODO: we're coord-sorted, so ideally we'd emit a container unless its too small,
                        // TODO: but emitting multi-ref containers on cord-sorted will break index queries for unmapped (??),
                        // TODO: so just return true;
                        //return samRecords.size() >= maxRecordsPerContainer;
                        return true;
                    }
                    return samRecords.size() >= MIN_SINGLE_REF_RECORDS;
                }
        }
    }

    protected void writeContainer() {
        //System.out.println(String.format("Current %d samRecord %d", currentReferenceContext, samRecords.size()));
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
        streamOffset += container.writeContainer(CRAM_VERSION, outputStream);
        if (cramIndexer != null) {
            // using silent validation here because the reads have been through validation already or
            // they have been generated somehow through the htsjdk
            //TODO: do we need this....?
            cramIndexer.processContainer(container, ValidationStringency.SILENT);
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
