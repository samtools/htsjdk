package htsjdk.samtools;

import htsjdk.samtools.cram.build.ContainerFactory;
import htsjdk.samtools.cram.build.Cram2SamRecordFactory;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.build.CramNormalizer;
import htsjdk.samtools.cram.build.Sam2CramRecordFactory;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.lossy.PreservationPolicy;
import htsjdk.samtools.cram.lossy.QualityScorePreservation;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.ref.ReferenceTracks;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.Slice;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.util.SequenceUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Class for writing SAMRecords into a series of CRAM containers on an output stream.
 */
public class CRAMContainerStreamWriter {
    private static final Version cramVersion = CramVersions.DEFAULT_CRAM_VERSION;

    static int DEFAULT_RECORDS_PER_SLICE = 10000;
    static int MIN_SINGLE_REF_RECORDS = 1000;
    protected final int recordsPerSlice = DEFAULT_RECORDS_PER_SLICE;
    private static final int DEFAULT_SLICES_PER_CONTAINER = 1;
    protected final int containerSize = recordsPerSlice * DEFAULT_SLICES_PER_CONTAINER;

    private final SAMFileHeader samFileHeader;
    private final String cramID;
    private final OutputStream outputStream;
    private CRAMReferenceSource source;

    private final List<SAMRecord> samRecords = new ArrayList<>();
    private ContainerFactory containerFactory;
    private ReferenceContext refContext = null;     // or some uninit sentinel

    private static final Log log = Log.getInstance(CRAMContainerStreamWriter.class);

    private boolean preserveReadNames = true;
    private QualityScorePreservation preservation = null;
    private boolean captureAllTags = true;
    private Set<String> captureTags = new TreeSet<>();
    private Set<String> ignoreTags = new TreeSet<>();

    private CRAMBAIIndexer indexer;
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
        this.outputStream = outputStream;
        this.samFileHeader = samFileHeader;
        this.cramID = cramId;
        this.source = source;
        containerFactory = new ContainerFactory(samFileHeader, recordsPerSlice);
        if (indexStream != null) {
            indexer = new CRAMBAIIndexer(indexStream, samFileHeader);
        }
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
     * Write a CRAM file header and SAM header to the stream.

     * @param header SAMFileHeader to write
     */
    public void writeHeader(final SAMFileHeader header) {
        // TODO: header must be written exactly once per writer life cycle.
        offset = CramIO.writeHeader(cramVersion, outputStream, header, cramID);
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

    public boolean isPreserveReadNames() {
        return preserveReadNames;
    }

    public void setPreserveReadNames(final boolean preserveReadNames) {
        this.preserveReadNames = preserveReadNames;
    }

    public List<PreservationPolicy> getPreservationPolicies() {
        if (preservation == null) {
            // set up greedy policy by default:
            preservation = new QualityScorePreservation("*8");
        }
        return preservation.getPreservationPolicies();
    }

    public boolean isCaptureAllTags() {
        return captureAllTags;
    }

    public void setCaptureAllTags(final boolean captureAllTags) {
        this.captureAllTags = captureAllTags;
    }

    public Set<String> getCaptureTags() {
        return captureTags;
    }

    public void setCaptureTags(final Set<String> captureTags) {
        this.captureTags = captureTags;
    }

    public Set<String> getIgnoreTags() {
        return ignoreTags;
    }

    public void setIgnoreTags(final Set<String> ignoreTags) {
        this.ignoreTags = ignoreTags;
    }

    /**
     * Decide if the current container should be completed and flushed. The decision is based on a) number of records and b) if the
     * reference sequence id has changed.
     *
     * @param nextRecord the record to be added into the current or next container
     * @return true if the current container should be flushed and the following records should go into a new container; false otherwise.
     */
    protected boolean shouldFlushContainer(final SAMRecord nextRecord) {
        final ReferenceContext nextRefContext = new ReferenceContext(nextRecord.getReferenceIndex());
        if (samRecords.isEmpty()) {
            refContext = nextRefContext;
            return false;
        }

        if (samRecords.size() >= containerSize) {
            return true;
        }

        if (samFileHeader.getSortOrder() != SAMFileHeader.SortOrder.coordinate) {
            return false;
        }

        // make sure unmapped reads don't get into multiref containers:
        if (! refContext.isUnmappedUnplaced() && nextRefContext.isUnmappedUnplaced()) {
            return true;
        }

        if (refContext.isMultipleReference()) {
            return false;
        }

        if (refContext == nextRefContext) {
            return false;
        }

        /**
         * Protection against too small containers: flush at least X single refs, switch to multiref otherwise.
         */
        if (samRecords.size() > MIN_SINGLE_REF_RECORDS) {
            return true;
        } else {
            refContext = ReferenceContext.MULTIPLE_REFERENCE_CONTEXT;
            return false;
        }
    }

    private static void updateTracks(final List<SAMRecord> samRecords, final ReferenceTracks tracks) {
        for (final SAMRecord samRecord : samRecords) {
            if (samRecord.getAlignmentStart() != SAMRecord.NO_ALIGNMENT_START) {
                int refPos = samRecord.getAlignmentStart();
                int readPos = 0;
                for (final CigarElement cigarElement : samRecord.getCigar().getCigarElements()) {
                    if (cigarElement.getOperator().consumesReferenceBases()) {
                        for (int elementIndex = 0; elementIndex < cigarElement.getLength(); elementIndex++)
                            tracks.addCoverage(refPos + elementIndex, 1);
                    }
                    switch (cigarElement.getOperator()) {
                        case M:
                        case X:
                        case EQ:
                            for (int pos = readPos; pos < cigarElement.getLength(); pos++) {
                                final byte readBase = samRecord.getReadBases()[readPos + pos];
                                final byte refBase = tracks.baseAt(refPos + pos);
                                if (readBase != refBase) tracks.addMismatches(refPos + pos, 1);
                            }
                            break;

                        default:
                            break;
                    }

                    readPos += cigarElement.getOperator().consumesReadBases() ? cigarElement.getLength() : 0;
                    refPos += cigarElement.getOperator().consumesReferenceBases() ? cigarElement.getLength() : 0;
                }
            }
        }
    }

    /**
     * Complete the current container and flush it to the output stream.
     *
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws IOException
     */
    protected void flushContainer() throws IllegalArgumentException {

        final byte[] referenceBases;
        String refSeqName = null;
        switch (refContext.getType()) {
            case MULTIPLE_REFERENCE_TYPE:
                if (preservation != null && preservation.areReferenceTracksRequired()) {
                    throw new SAMException("Cannot apply reference-based lossy compression on non-coordinate sorted reads.");
                }
                referenceBases = new byte[0];
                break;
            case UNMAPPED_UNPLACED_TYPE:
                referenceBases = new byte[0];
                break;
            case SINGLE_REFERENCE_TYPE:
                final SAMSequenceRecord sequence = samFileHeader.getSequence(refContext.getSequenceId());
                referenceBases = source.getReferenceBases(sequence, true);
                refSeqName = sequence.getSequenceName();
                break;
            default:
                throw new SAMException("Container Reference Context was not initialized." );
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

        ReferenceTracks tracks = null;
        if (preservation != null && preservation.areReferenceTracksRequired()) {
            tracks = new ReferenceTracks(refContext.getSerializableId(), refSeqName, referenceBases);

            tracks.ensureRange(start, stop - start + 1);
            updateTracks(samRecords, tracks);
        }

        final List<CramCompressionRecord> cramRecords = new ArrayList<>(samRecords.size());

        final Sam2CramRecordFactory sam2CramRecordFactory = new Sam2CramRecordFactory(referenceBases, samFileHeader, cramVersion);
        sam2CramRecordFactory.preserveReadNames = preserveReadNames;
        sam2CramRecordFactory.captureAllTags = captureAllTags;
        sam2CramRecordFactory.captureTags.addAll(captureTags);
        sam2CramRecordFactory.ignoreTags.addAll(ignoreTags);
        containerFactory.setPreserveReadNames(preserveReadNames);

        int index = 0;
        for (final SAMRecord samRecord : samRecords) {
            final int samRefIndex = samRecord.getReferenceIndex();
            if ((! ReferenceContext.isUnmappedUnplaced(samRefIndex)) && samRefIndex != refContext.getSerializableId()) {
                // this may load all ref sequences into memory:
                sam2CramRecordFactory.setRefBases(source.getReferenceBases(samFileHeader.getSequence(samRefIndex), true));
            }
            final CramCompressionRecord cramRecord = sam2CramRecordFactory.createCramRecord(samRecord);
            cramRecord.index = ++index;
            cramRecord.alignmentStart = samRecord.getAlignmentStart();
            cramRecords.add(cramRecord);

            if (preservation != null) preservation.addQualityScores(samRecord, cramRecord, tracks);
            else if (cramRecord.qualityScores != SAMRecord.NULL_QUALS) cramRecord.setForcePreserveQualityScores(true);
            }


        if (sam2CramRecordFactory.getBaseCount() < 3 * sam2CramRecordFactory.getFeatureCount())
            log.warn("Abnormally high number of mismatches, possibly wrong reference.");

        {
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
        }


        {
            /**
             * The following passage is for paranoid mode only. When java is run with asserts on it will throw an {@link AssertionError} if
             * read bases or quality scores of a restored SAM record mismatch the original. This is effectively a runtime round trip test.
             */
            @SuppressWarnings("UnusedAssignment") boolean assertsEnabled = false;
            //noinspection AssertWithSideEffects,ConstantConditions
            assert assertsEnabled = true;
            //noinspection ConstantConditions
            if (assertsEnabled) {
                final Cram2SamRecordFactory f = new Cram2SamRecordFactory(samFileHeader);
                for (int i = 0; i < samRecords.size(); i++) {
                    final SAMRecord restoredSamRecord = f.create(cramRecords.get(i));
                    assert (restoredSamRecord.getAlignmentStart() == samRecords.get(i).getAlignmentStart());
                    assert (restoredSamRecord.getReferenceName().equals(samRecords.get(i).getReferenceName()));

                    if (!restoredSamRecord.getReadString().equals(samRecords.get(i).getReadString())) {
                        // try to fix the original read bases by normalizing them to BAM set:
                        final byte[] originalReadBases = samRecords.get(i).getReadString().getBytes();
                        final String originalReadBasesUpperCaseIupacNoDot = new String(SequenceUtil.toBamReadBasesInPlace(originalReadBases));
                        assert (restoredSamRecord.getReadString().equals(originalReadBasesUpperCaseIupacNoDot));
                    }
                    assert (restoredSamRecord.getBaseQualityString().equals(samRecords.get(i).getBaseQualityString()));
                }
            }
        }

        final Container container = containerFactory.buildContainer(cramRecords);
        for (final Slice slice : container.slices) {
            slice.setRefMD5(referenceBases);
        }
        container.offset = offset;
        offset += ContainerIO.writeContainer(cramVersion, container, outputStream);
        if (indexer != null) {
            /**
             * Using silent validation here because the reads have been through validation already or
             * they have been generated somehow through the htsjdk.
             */
            indexer.processContainer(container, ValidationStringency.SILENT);
        }
        samRecords.clear();
        refContext = null; // or some uninit sentinel
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
        if (refContext.isMultipleReference()) {
            return;
        }

        final ReferenceContext samRecordRefContext = new ReferenceContext(samRecordReferenceIndex);
        if (refContext == null) {
            refContext = samRecordRefContext;
        } else if (refContext != samRecordRefContext) {
            refContext = ReferenceContext.MULTIPLE_REFERENCE_CONTEXT;
        }
    }

}
