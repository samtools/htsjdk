/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
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
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceTracks;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.cram.structure.Slice;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.StringLineReader;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@SuppressWarnings("UnusedDeclaration")
public class CRAMFileWriter extends SAMFileWriterImpl {
    private static final int REF_SEQ_INDEX_NOT_INITIALIZED = -2;
    static int DEFAULT_RECORDS_PER_SLICE = 10000;
    private static final int DEFAULT_SLICES_PER_CONTAINER = 1;
    private static final Version cramVersion = CramVersions.CRAM_v2_1;

    private final String fileName;
    private final List<SAMRecord> samRecords = new ArrayList<SAMRecord>();
    private ContainerFactory containerFactory;
    protected final int recordsPerSlice = DEFAULT_RECORDS_PER_SLICE;
    protected final int containerSize = recordsPerSlice * DEFAULT_SLICES_PER_CONTAINER;

    private final OutputStream outputStream;
    private ReferenceSource source;
    private int refSeqIndex = REF_SEQ_INDEX_NOT_INITIALIZED;

    private static final Log log = Log.getInstance(CRAMFileWriter.class);

    private final SAMFileHeader samFileHeader;
    private boolean preserveReadNames = true;
    private QualityScorePreservation preservation = null;
    private boolean captureAllTags = true;
    private Set<String> captureTags = new TreeSet<String>();
    private Set<String> ignoreTags = new TreeSet<String>();

    private CRAMIndexer indexer;
    private long offset;

    public CRAMFileWriter(final OutputStream outputStream, final ReferenceSource source, final SAMFileHeader samFileHeader, final String fileName) {
        this(outputStream, null, source, samFileHeader, fileName);
    }

    public CRAMFileWriter(final OutputStream outputStream, final OutputStream indexOS, final ReferenceSource source, final SAMFileHeader samFileHeader, final String fileName) {
        this.outputStream = outputStream;
        this.source = source;
        this.samFileHeader = samFileHeader;
        this.fileName = fileName;
        setSortOrder(samFileHeader.getSortOrder(), true);
        setHeader(samFileHeader);

        if (this.source == null) this.source = new ReferenceSource(Defaults.REFERENCE_FASTA);

        containerFactory = new ContainerFactory(samFileHeader, recordsPerSlice);
        if (indexOS != null) indexer = new CRAMIndexer(indexOS, samFileHeader);
    }

    /**
     * Decide if the current container should be completed and flushed. The decision is based on a) number of records and b) if the
     * reference sequence id has changed.
     *
     * @param nextRecord the record to be added into the current or next container
     * @return true if the current container should be flushed and the following records should go into a new container; false otherwise.
     */
    protected boolean shouldFlushContainer(final SAMRecord nextRecord) {
        return samRecords.size() >= containerSize || refSeqIndex != REF_SEQ_INDEX_NOT_INITIALIZED && refSeqIndex != nextRecord.getReferenceIndex();

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
    protected void flushContainer() throws IllegalArgumentException, IllegalAccessException, IOException {

        final byte[] refs;
        String refSeqName = null;
        if (refSeqIndex == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) refs = new byte[0];
        else {
            final SAMSequenceRecord sequence = samFileHeader.getSequence(refSeqIndex);
            refs = source.getReferenceBases(sequence, true);
            refSeqName = sequence.getSequenceName();
        }

        int start = SAMRecord.NO_ALIGNMENT_START;
        int stop = SAMRecord.NO_ALIGNMENT_START;
        for (final SAMRecord r : samRecords) {
            if (r.getAlignmentStart() == SAMRecord.NO_ALIGNMENT_START) continue;

            if (start == SAMRecord.NO_ALIGNMENT_START) start = r.getAlignmentStart();

            start = Math.min(r.getAlignmentStart(), start);
            stop = Math.max(r.getAlignmentEnd(), stop);
        }

        ReferenceTracks tracks = null;
        if (preservation != null && preservation.areReferenceTracksRequired()) {
            tracks = new ReferenceTracks(refSeqIndex, refSeqName, refs);

            tracks.ensureRange(start, stop - start + 1);
            updateTracks(samRecords, tracks);
        }

        final List<CramCompressionRecord> cramRecords = new ArrayList<CramCompressionRecord>(samRecords.size());

        final Sam2CramRecordFactory sam2CramRecordFactory = new Sam2CramRecordFactory(refs, samFileHeader, cramVersion);
        sam2CramRecordFactory.preserveReadNames = preserveReadNames;
        sam2CramRecordFactory.captureAllTags = captureAllTags;
        sam2CramRecordFactory.captureTags.addAll(captureTags);
        sam2CramRecordFactory.ignoreTags.addAll(ignoreTags);
        containerFactory.setPreserveReadNames(preserveReadNames);

        int index = 0;
        int prevAlStart = start;
        for (final SAMRecord samRecord : samRecords) {
            final CramCompressionRecord cramRecord = sam2CramRecordFactory.createCramRecord(samRecord);
            cramRecord.index = ++index;
            cramRecord.alignmentDelta = samRecord.getAlignmentStart() - prevAlStart;
            cramRecord.alignmentStart = samRecord.getAlignmentStart();
            prevAlStart = samRecord.getAlignmentStart();

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
                        }
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
                    assert (restoredSamRecord.getReadString().equals(samRecords.get(i).getReadString()));
                    assert (restoredSamRecord.getBaseQualityString().equals(samRecords.get(i).getBaseQualityString()));
                }
            }
        }

        final Container container = containerFactory.buildContainer(cramRecords);
        for (final Slice slice : container.slices)
            slice.setRefMD5(refs);
        container.offset = offset;
        offset += ContainerIO.writeContainer(cramVersion, container, outputStream);
        if (indexer != null) {
            for (final Slice slice : container.slices) {
                indexer.processAlignment(slice);
            }
        }
        samRecords.clear();
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

    @Override
    protected void writeAlignment(final SAMRecord alignment) {
        if (shouldFlushContainer(alignment)) try {
            flushContainer();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

        updateReferenceContext(alignment.getReferenceIndex());

        samRecords.add(alignment);
    }

    /**
     * Check if the reference has changed and create a new record factory using the new reference.
     *
     * @param samRecordReferenceIndex index of the new reference sequence
     */
    private void updateReferenceContext(final int samRecordReferenceIndex) {
        if (refSeqIndex == REF_SEQ_INDEX_NOT_INITIALIZED) {
            refSeqIndex = samRecordReferenceIndex;
        } else
            if (refSeqIndex != samRecordReferenceIndex) refSeqIndex = samRecordReferenceIndex;
    }

    @Override
    protected void writeHeader(final String textHeader) {
        // TODO: header must be written exactly once per writer life cycle.
        final SAMFileHeader header = new SAMTextHeaderCodec().decode(new StringLineReader(textHeader), (fileName != null ? fileName : null));

        containerFactory = new ContainerFactory(header, recordsPerSlice);

        final CramHeader cramHeader = new CramHeader(cramVersion, fileName, header);
        try {
            offset = CramIO.writeCramHeader(cramHeader, outputStream);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void finish() {
        try {
            if (!samRecords.isEmpty()) flushContainer();
            CramIO.issueEOF(cramVersion, outputStream);
            outputStream.flush();
            if (indexer != null)
                indexer.finish();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected String getFilename() {
        return fileName;
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
}
