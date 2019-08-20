package htsjdk.samtools;

import htsjdk.samtools.cram.build.ContainerFactory;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.build.CramNormalizer;
import htsjdk.samtools.cram.build.Sam2CramRecordFactory;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Class for writing SAMRecords into a series of CRAM containers on an output stream.
 */
public class CRAMContainerStreamWriter {
    private static final Log log = Log.getInstance(CRAMContainerStreamWriter.class);
    private static final Version cramVersion = CramVersions.DEFAULT_CRAM_VERSION;

    private final CRAMEncodingStrategy encodingStrategy;

    private final int containerSize;
    private static int MIN_SINGLE_REF_RECORDS = 1000;
    private static final int REF_SEQ_INDEX_NOT_INITIALIZED = -3;

    private SAMFileHeader samFileHeader;
    private final String cramID;
    private final OutputStream outputStream;
    private CRAMReferenceSource source;

    private final List<SAMRecord> samRecords = new ArrayList<SAMRecord>();
    private ContainerFactory containerFactory;
    private int refSeqIndex = REF_SEQ_INDEX_NOT_INITIALIZED;

    private final CRAMIndexer indexer;
    private long offset;

    /**
     * Create a CRAMContainerStreamWriter for writing SAM records into a series of CRAM
     * containers on output stream, with an optional index.
     *
     * @param outputStream where to write the CRAM stream.
     * @param indexStream where to write the output index. Can be null if no index is required.
     * @param source reference source
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
     * @param source reference source
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
     * @param referenceSource reference source
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
        this.source = referenceSource;
        this.samFileHeader = samFileHeader;
        this.outputStream = outputStream;
        this.cramID = streamIdentifier;
        this.indexer = indexer;
        containerFactory = new ContainerFactory(samFileHeader, encodingStrategy);
        containerSize = this.encodingStrategy.getRecordsPerSlice() * this.encodingStrategy.getSlicesPerContainer();
    }

    /**
     * Write an alignment record.
     * @param alignment must not be null
     */
    public void writeAlignment(final SAMRecord alignment) {
        if (shouldFlushContainer(alignment)) {
            flushContainer();
        }

        updateReferenceContext(alignment.getReferenceIndex());

        samRecords.add(alignment);
    }

    /**
     * Write a CRAM file header and the previously provided SAM header to the stream.
     */
    public void writeHeader() {
        final CramHeader cramHeader = new CramHeader(cramVersion, cramID, samFileHeader);
        offset = CramIO.writeCramHeader(cramHeader, outputStream);
    }

    /**
     * Finish writing to the stream. Flushes the record cache and optionally emits an EOF container.
     * @param writeEOFContainer true if an EOF container should be written. Only use false if writing a CRAM file
     *                          fragment which will later be aggregated into a complete CRAM file.
     */
    public void finish(final boolean writeEOFContainer) {
        try {
            if (!samRecords.isEmpty()) {
                flushContainer();
            }
            if (writeEOFContainer) {
                CramIO.issueEOF(cramVersion, outputStream);
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

    /**
     * Decide if the current container should be completed and flushed based on number of records and whether the
     * reference sequence id has changed.
     *
     * @param nextRecord the record to be added into the current or next container
     * @return true if the current container should be flushed and the following records should go into a new container; false otherwise.
     */
    protected boolean shouldFlushContainer(final SAMRecord nextRecord) {
        if (samRecords.isEmpty()) {
            refSeqIndex = nextRecord.getReferenceIndex();
            return false;
        }

        if (samRecords.size() >= containerSize) {
            return true;
        }

        if (samFileHeader.getSortOrder() != SAMFileHeader.SortOrder.coordinate) {
            return false;
        }

        // make unmapped reads don't get into multiref containers:
        if (refSeqIndex != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX && nextRecord.getReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            return true;
        }

        if (refSeqIndex == ReferenceContext.MULTIPLE_REFERENCE_ID) {
            return false;
        }

        final boolean sameRef = (refSeqIndex == nextRecord.getReferenceIndex());
        if (sameRef) {
            return false;
        }

        /**
         * Protection against too small containers: flush at least X single refs, switch to multiref otherwise.
         */
        if (samRecords.size() > MIN_SINGLE_REF_RECORDS) {
            return true;
        } else {
            refSeqIndex = ReferenceContext.MULTIPLE_REFERENCE_ID;
            return false;
        }
    }

    /**
     * Complete the current container and flush it to the output stream.
     *
     * @throws IllegalArgumentException
     */
    protected void flushContainer() throws IllegalArgumentException {

        final byte[] referenceBases;
        String refSeqName = null;
        switch (refSeqIndex) {
            case ReferenceContext.MULTIPLE_REFERENCE_ID:
                referenceBases = new byte[0];
                break;
            case ReferenceContext.UNMAPPED_UNPLACED_ID:
                referenceBases = new byte[0];
                break;
            default:
                final SAMSequenceRecord sequence = samFileHeader.getSequence(refSeqIndex);
                referenceBases = source.getReferenceBases(sequence, true);
                refSeqName = sequence.getSequenceName();
                break;
        }

        int start = SAMRecord.NO_ALIGNMENT_START;
        int stop = SAMRecord.NO_ALIGNMENT_START;
        for (final SAMRecord r : samRecords) {
            if (r.getAlignmentStart() == SAMRecord.NO_ALIGNMENT_START) {
                continue;
            }

            if (start == SAMRecord.NO_ALIGNMENT_START) {
                start = r.getAlignmentStart();
            }

            start = Math.min(r.getAlignmentStart(), start);
            stop = Math.max(r.getAlignmentEnd(), stop);
        }

        final List<CramCompressionRecord> cramRecords = new ArrayList<>(samRecords.size());

        final Sam2CramRecordFactory sam2CramRecordFactory = new Sam2CramRecordFactory(encodingStrategy, referenceBases, samFileHeader, cramVersion);

        int index = 0;
        for (final SAMRecord samRecord : samRecords) {
            if (samRecord.getReferenceIndex() != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX && refSeqIndex != samRecord.getReferenceIndex()) {
                // this may load all ref sequences into memory:
                sam2CramRecordFactory.setRefBases(source.getReferenceBases(samFileHeader.getSequence(samRecord.getReferenceIndex()), true));
            }
            final CramCompressionRecord cramRecord = sam2CramRecordFactory.createCramRecord(samRecord);
            cramRecord.index = ++index;
            cramRecord.alignmentStart = samRecord.getAlignmentStart();
            cramRecords.add(cramRecord);

            if (cramRecord.qualityScores != SAMRecord.NULL_QUALS) {
                cramRecord.setForcePreserveQualityScores(true);
            }
        }


        if (sam2CramRecordFactory.getBaseCount() < 3 * sam2CramRecordFactory.getFeatureCount()) {
            log.warn("Abnormally high number of mismatches, possibly wrong reference.");
        }

        if (samFileHeader.getSortOrder() == SAMFileHeader.SortOrder.coordinate) {
            // mating:
            final Map<String, CramCompressionRecord> primaryMateMap = new TreeMap<String, CramCompressionRecord>();
            final Map<String, CramCompressionRecord> secondaryMateMap = new TreeMap<String, CramCompressionRecord>();
            for (final CramCompressionRecord r : cramRecords) {
                if (!r.isMultiFragment()) {
                    r.setDetached(true);

                    r.setHasMateDownStream(false);
                    r.recordsToNextFragment = -1;
                    r.next = null;
                    r.previous = null;
                } else {
                    final String name = r.readName;
                    final Map<String, CramCompressionRecord> mateMap = r.isSecondaryAlignment() ? secondaryMateMap : primaryMateMap;
                    final CramCompressionRecord mate = mateMap.get(name);
                    if (mate == null) {
                        mateMap.put(name, r);
                    } else {
                        CramCompressionRecord prev = mate;
                        while (prev.next != null) prev = prev.next;
                        prev.recordsToNextFragment = r.index - prev.index - 1;
                        prev.next = r;
                        r.previous = prev;
                        r.previous.setHasMateDownStream(true);
                        r.setHasMateDownStream(false);
                        r.setDetached(false);
                        r.previous.setDetached(false);
                    }
                }
            }

            // mark unpredictable reads as detached:
            for (final CramCompressionRecord cramRecord : cramRecords) {
                if (cramRecord.next == null || cramRecord.previous != null) continue;
                CramCompressionRecord last = cramRecord;
                while (last.next != null) last = last.next;

                if (cramRecord.isFirstSegment() && last.isLastSegment()) {
                    final int templateLength = CramNormalizer.computeInsertSize(cramRecord, last);

                    if (cramRecord.templateSize == templateLength) {
                        last = cramRecord.next;
                        while (last.next != null) {
                            if (last.templateSize != -templateLength)
                                break;

                            last = last.next;
                        }
                        if (last.templateSize != -templateLength) detach(cramRecord);
                    }else detach(cramRecord);
                } else detach(cramRecord);
            }

            for (final CramCompressionRecord cramRecord : primaryMateMap.values()) {
                if (cramRecord.next != null) continue;
                cramRecord.setDetached(true);

                cramRecord.setHasMateDownStream(false);
                cramRecord.recordsToNextFragment = -1;
                cramRecord.next = null;
                cramRecord.previous = null;
            }

            for (final CramCompressionRecord cramRecord : secondaryMateMap.values()) {
                if (cramRecord.next != null) continue;
                cramRecord.setDetached(true);

                cramRecord.setHasMateDownStream(false);
                cramRecord.recordsToNextFragment = -1;
                cramRecord.next = null;
                cramRecord.previous = null;
            }
        }
        else {
            for (final CramCompressionRecord cramRecord : cramRecords) {
                cramRecord.setDetached(true);
            }
        }

        //TODO: these records need to be broken up into groups with like reference context types, since if there is
        //TODO: more than one type present, they need to be split across multiple containers
        //TODO: this should really accumulate records to a slice, not a container, and then submit the slices
        //TODO: one at a time to container and let it aggregate and write them out as needed
        final Container container = containerFactory.buildContainer(cramRecords, offset);
        for (final Slice slice : container.getSlices()) {
            slice.setRefMD5(referenceBases);
        }
        offset += ContainerIO.writeContainer(cramVersion, container, outputStream);
        if (indexer != null) {
            /**
             * Using silent validation here because the reads have been through validation already or
             * they have been generated somehow through the htsjdk.
             */
            indexer.processContainer(container, ValidationStringency.SILENT);
        }
        samRecords.clear();
        refSeqIndex = REF_SEQ_INDEX_NOT_INITIALIZED;
    }

    /**
     * Traverse the graph and mark all segments as detached.
     *
     * @param cramRecord the starting point of the graph
     */
    private static void detach(CramCompressionRecord cramRecord) {
        do {
            cramRecord.setDetached(true);

            cramRecord.setHasMateDownStream(false);
            cramRecord.recordsToNextFragment = -1;
        }
        while ((cramRecord = cramRecord.next) != null);
    }

    /**
     * Check if the reference has changed and create a new record factory using the new reference.
     *
     * @param samRecordReferenceIndex index of the new reference sequence
     */
    private void updateReferenceContext(final int samRecordReferenceIndex) {
        if (refSeqIndex == ReferenceContext.MULTIPLE_REFERENCE_ID) {
            return;
        }

        if (refSeqIndex == REF_SEQ_INDEX_NOT_INITIALIZED) {
            refSeqIndex = samRecordReferenceIndex;
        } else if (refSeqIndex != samRecordReferenceIndex) {
            refSeqIndex = ReferenceContext.MULTIPLE_REFERENCE_ID;
    }
    }

}
