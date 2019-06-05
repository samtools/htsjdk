/**
 * ****************************************************************************
 * Copyright 2013 EMBL-EBI
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at4
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
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.*;
import htsjdk.samtools.cram.BAIEntry;
import htsjdk.samtools.cram.CRAIEntry;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.build.CRAMReferenceRegion;
import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.digest.ContentDigests;
import htsjdk.samtools.cram.encoding.reader.CramRecordReader;
import htsjdk.samtools.cram.encoding.writer.CramRecordWriter;
import htsjdk.samtools.cram.io.CramIntArray;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.io.LTF8;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.ref.ReferenceContextType;
import htsjdk.samtools.cram.structure.block.BlockContentType;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.utils.ValidationUtils;

import java.io.*;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A CRAM slice is a logical construct that is just a subset of the blocks in a Slice.
 *
 * NOTE: Every Slice has a reference context (it is either single-reference (mapped), multi-reference, or
 * unmapped), reflecting depending on  the records it contains. Single-ref mapped doesn't mean that the records
 * are necessarily (that is, that their getMappedRead flag is true), only that the records in that slice are PLACED
 * on the corresponding reference contig.
 */
public class Slice {
    private static final Log log = Log.getInstance(Slice.class);
    private static final int MD5_BYTE_SIZE = 16;
    // for indexing purposes
    public static final int UNINITIALIZED_INDEXING_PARAMETER = -1;
    // the spec defines a special sentinel to indicate the absence of an embedded reference block
    public static final int EMBEDDED_REFERENCE_ABSENT_CONTENT_ID = -1;

    ////////////////////////////////
    // Slice header components as defined in the spec
    private final AlignmentContext alignmentContext; // ref sequence, alignment start and span
    private final int nRecords;
    private final long globalRecordCounter;
    private final int nSliceBlocks;              // includes the core block and external blocks, but not the header block
    private List<Integer> contentIDs;
    private int embeddedReferenceBlockContentID = EMBEDDED_REFERENCE_ABSENT_CONTENT_ID;
    private byte[] referenceMD5 = new byte[MD5_BYTE_SIZE];
    private SAMBinaryTagAndValue sliceTags;
    // End Slice header components
    ////////////////////////////////

    private final CompressionHeader compressionHeader;
    private final SliceBlocks sliceBlocks;
    private final long byteOffsetOfContainer;

    private Block sliceHeaderBlock;

    // Modeling the embedded reference block here is somewhat redundant, since it can be retrieved from the
    // external blocks that are managed by the {@link SliceBlocks} object by using the externalBlockContentId,
    // but we retain it here to use for validation purposes.
    private Block embeddedReferenceBlock;
    private long baseCount;

    // Values used for indexing. We don't use an AlignmentSpan object to model these values because
    // AlignmentSpan contains an AlignmentContext, but the AlignmentContext for a Slice is maintained as part
    // of the Slice header, so using AlignmentSpan here would result in redundant AlignmentContext values.
    //
    // These values are only maintained for slices that are created from SAM/CRAMCompressionRecords. Slices that
    // are created from deserializing a stream do not have recorded values for these because those values
    // not part of the stream, and the individual records are not decoded until they're requested (they are
    // not decoded during indexing, with the exception of MULTI_REF slices, where its required that the slice
    // be resolved into individual reference contexts for inclusion in the index).
    private int mappedReadsCount = 0;   // mapped (rec.getReadUnmappedFlag() != true)
    private int unmappedReadsCount = 0; // unmapped (rec.getReadUnmappedFlag() == true)
    private int unplacedReadsCount = 0; // nocoord (alignmentStart == SAMRecord.NO_ALIGNMENT_START)

    private int byteOffsetOfSliceHeaderBlock = UNINITIALIZED_INDEXING_PARAMETER;
    private int byteSizeOfSliceBlocks = UNINITIALIZED_INDEXING_PARAMETER;
    private int landmarkIndex = UNINITIALIZED_INDEXING_PARAMETER;

    /**
     * Create a slice by reading a serialized Slice from an input stream.
     *
     * @param cramVersion the version of the CRAM stream being read
     * @param compressionHeader the compression header for the contain in which the Slice resides
     * @param inputStream the input stream to be read
     * @param containerByteOffset the stream byte offset of start of the container in which this Slice resides
     */
    public Slice(
            final CRAMVersion cramVersion,
            final CompressionHeader compressionHeader,
            final InputStream inputStream,
            final long containerByteOffset) {
        sliceHeaderBlock = Block.read(cramVersion, inputStream);
        if (sliceHeaderBlock.getContentType() != BlockContentType.MAPPED_SLICE) {
            throw new RuntimeException("Slice Header Block expected, found:  " + sliceHeaderBlock.getContentType().name());
        }

        final InputStream parseInputStream = new ByteArrayInputStream(sliceHeaderBlock.getRawContent());
        this.compressionHeader = compressionHeader;
        this.byteOffsetOfContainer = containerByteOffset;

        final ReferenceContext refContext = new ReferenceContext(ITF8.readUnsignedITF8(parseInputStream));
        final int alignmentStart = ITF8.readUnsignedITF8(parseInputStream);
        final int alignmentSpan = (ITF8.readUnsignedITF8(parseInputStream));
        this.alignmentContext = new AlignmentContext(refContext, alignmentStart, alignmentSpan);

        this.nRecords = ITF8.readUnsignedITF8(parseInputStream);
        this.globalRecordCounter = LTF8.readUnsignedLTF8(parseInputStream);
        this.nSliceBlocks = ITF8.readUnsignedITF8(parseInputStream);

        setContentIDs(CramIntArray.arrayAsList(parseInputStream));
        embeddedReferenceBlockContentID = ITF8.readUnsignedITF8(parseInputStream);

        referenceMD5 = new byte[MD5_BYTE_SIZE];
        InputStreamUtils.readFully(parseInputStream, referenceMD5, 0, referenceMD5.length);

        final byte[] readTagBytes = InputStreamUtils.readFully(parseInputStream);
        if (cramVersion.getMajor() >= CramVersions.CRAM_v3.getMajor()) {
            setSliceTags(BinaryTagCodec.readTags(
                    readTagBytes, 0, readTagBytes.length, ValidationStringency.DEFAULT_STRINGENCY));
        }

        //NOTE: this reads the underlying blocks from the stream, but doesn't decode them because we don't want
        // to do this automatically since there are case where we want to iterate through containers or slices
        // (i.e., during indexing, or when satisfying index queries) when we want to consume the underlying blocks,
        // but not actually decode them
        sliceBlocks = new SliceBlocks(cramVersion, nSliceBlocks, inputStream);

        if (embeddedReferenceBlockContentID != EMBEDDED_REFERENCE_ABSENT_CONTENT_ID) {
            // also adds this block to the external list
            setEmbeddedReferenceBlock(sliceBlocks.getExternalBlock(embeddedReferenceBlockContentID));
        }
    }

    /**
     * Create a single Slice from CRAM Compression Records and a Compression Header. The caller is
     * responsible for appropriate subdivision of records into containers and slices (see ContainerFactory}.
     *
     * @param records input CRAM Compression Records
     * @param compressionHeader the enclosing {@link Container}'s Compression Header
     * @param containerByteOffset
     * @param globalRecordCounter
     * @return a Slice corresponding to the given records
     *
     * Determines whether the slice is single ref, unmapped or multi reference, and derives alignment
     * boundaries for the slice if single ref.
     *
     * Valid Slice states, by individual record contents:
     *
     * Single Reference: all records have valid placements/alignments on the same reference sequence
     * - records can be unmapped-but-placed
     * - reference can be external or embedded
     *
     * Multiple Reference: records may be placed or not, and may have differing reference sequences
     * - reference must not be embedded (not checked here)
     *
     * Unmapped: all records are unmapped and unplaced
     * - note however that we do not actually check mapping flags for unplaced reads.
     * @see CRAMCompressionRecord#isPlaced()
     *
     * @see ReferenceContextType
     */
    public Slice(
            final List<CRAMCompressionRecord> records,
            final CompressionHeader compressionHeader,
            final long containerByteOffset,
            final long globalRecordCounter) {
        ValidationUtils.validateArg(globalRecordCounter >= 0, "record counter must be >= 0");
        this.compressionHeader = compressionHeader;
        this.byteOffsetOfContainer = containerByteOffset;

        final ContentDigests hasher = ContentDigests.create(ContentDigests.ALL);
        final Set<ReferenceContext> referenceContexts = new HashSet<>();
        // ignore these values if we later determine this Slice is not single-ref
        int singleRefAlignmentStart = Integer.MAX_VALUE;
        int singleRefAlignmentEnd = SAMRecord.NO_ALIGNMENT_START;

        int baseCount = 0;
        for (final CRAMCompressionRecord record : records) {
            hasher.add(record);
            baseCount += record.getReadLength();

            if (record.isPlaced()) {
                referenceContexts.add(new ReferenceContext(record.getReferenceIndex()));
                singleRefAlignmentStart = Math.min(record.getAlignmentStart(), singleRefAlignmentStart);
                singleRefAlignmentEnd = Math.max(record.getAlignmentEnd(), singleRefAlignmentEnd);

                if (record.isSegmentUnmapped()) {
                    unmappedReadsCount++;
                } else {
                    mappedReadsCount++;
                }
            } else {
                referenceContexts.add(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT);
            }

            // This matches the logic of BAMIndexMetadata.recordMetaData(SAMRecord) and counts all reads
            // that are not placed, which will be a subset of reads that are included in unmappedReadsCount,
            // which counts all unmapped reads, whether placed or not.
            if (record.getAlignmentStart() == SAMRecord.NO_ALIGNMENT_START) {
                unplacedReadsCount++;
            }
        }

        this.alignmentContext = getDerivedAlignmentContext(
                referenceContexts,
                singleRefAlignmentStart,
                singleRefAlignmentEnd);

        sliceTags = hasher.getAsTags();
        nRecords = records.size();
        this.baseCount = baseCount;
        this.globalRecordCounter = globalRecordCounter;

        final CramRecordWriter writer = new CramRecordWriter(this);
        sliceBlocks = writer.writeToSliceBlocks(records, alignmentContext.getAlignmentStart());

        // we can't calculate the number of blocks until after the record writer has written everything out
        nSliceBlocks = caclulateNumberOfBlocks();
    }

    // May be null
    public Block getSliceHeaderBlock() { return sliceHeaderBlock; }

    public AlignmentContext getAlignmentContext() { return alignmentContext; }
    public SliceBlocks getSliceBlocks() { return sliceBlocks; }
    public int getNumberOfRecords() {
        return nRecords;
    }
    public long getGlobalRecordCounter() {
        return globalRecordCounter;
    }

    /**
     * @return the number of blocks as defined by the CRAM spec; this is 1 for the
     * core block plus the number of external blocks (does not include the slice header block);
     */
    public int getNumberOfBlocks() { return nSliceBlocks; }
    public List<Integer> getContentIDs() {
        return contentIDs;
    }

    private void setContentIDs(final List<Integer> contentIDs) {
        this.contentIDs = contentIDs;
    }
    public byte[] getReferenceMD5() { return referenceMD5; }

    /**
     * The Slice's offset in bytes from the beginning of the Container's Compression Header
     * (or the end of the Container Header), equal to {@link ContainerHeader#getLandmarks()}
     *
     * Used by BAI and CRAI indexing
     */
    public int getByteOffsetOfSliceHeaderBlock() {
        return byteOffsetOfSliceHeaderBlock;
    }

    public void setByteOffsetOfSliceHeaderBlock(int byteOffsetOfSliceHeaderBlock) {
        this.byteOffsetOfSliceHeaderBlock = byteOffsetOfSliceHeaderBlock;
    }

    /**
     * The Slice's size in bytes
     *
     * Used by CRAI indexing only
     */
    public int getByteSizeOfSliceBlocks() {
        return byteSizeOfSliceBlocks;
    }

    public void setByteSizeOfSliceBlocks(int byteSizeOfSliceBlocks) {
        this.byteSizeOfSliceBlocks = byteSizeOfSliceBlocks;
    }

    public void setLandmarkIndex(int landmarkIndex) {
        this.landmarkIndex = landmarkIndex;
    }

    public long getBaseCount() {
        return baseCount;
    }

    public SAMBinaryTagAndValue getSliceTags() {
        return sliceTags;
    }

    private void setSliceTags(SAMBinaryTagAndValue sliceTags) {
        this.sliceTags = sliceTags;
    }

    private int getMappedReadsCount() {
        return mappedReadsCount;
    }

    private int getUnmappedReadsCount() {
        return unmappedReadsCount;
    }

    private int getUnplacedReadsCount() {
        return unplacedReadsCount;
    }

    /**
     * Set the content ID of the embedded reference block. Per the CRAM spec, the value can be
     * -1 ({@link #EMBEDDED_REFERENCE_ABSENT_CONTENT_ID}) to indicate no embedded reference block is
     * present. If the reference block content ID already has a non-{@link #EMBEDDED_REFERENCE_ABSENT_CONTENT_ID}
     * value, it cannot be reset. If the embedded reference block has already been set, the provided
     * reference block content ID must agree with the content ID of the existing block.
     * @param embeddedReferenceBlockContentID
     */
    public void setEmbeddedReferenceContentID(final int embeddedReferenceBlockContentID) {
        if (this.embeddedReferenceBlockContentID != EMBEDDED_REFERENCE_ABSENT_CONTENT_ID &&
                this.embeddedReferenceBlockContentID != embeddedReferenceBlockContentID) {
            throw new CRAMException(
                    String.format("Can't reset embedded reference content ID (old %d new %d)",
                            this.embeddedReferenceBlockContentID, embeddedReferenceBlockContentID));

        }
        if (this.embeddedReferenceBlock != null &&
                this.embeddedReferenceBlock.getContentId() != embeddedReferenceBlockContentID) {
            throw new CRAMException(
                    String.format("Attempt to set embedded reference block content ID (%d) that is in conflict" +
                                    "with the content ID (%d) of the existing reference block ID",
                            embeddedReferenceBlockContentID,
                            this.embeddedReferenceBlock.getContentId()));
        }
        this.embeddedReferenceBlockContentID = embeddedReferenceBlockContentID;
    }

    /**
     * Get the content ID of the embedded reference block. Per the CRAM spec, the value
     * can be {@link #EMBEDDED_REFERENCE_ABSENT_CONTENT_ID} (-1) to indicate no embedded reference block is
     * present.
     * @return id of embedded reference block if present, otherwise {@link #EMBEDDED_REFERENCE_ABSENT_CONTENT_ID}
     */
    public int getEmbeddedReferenceContentID() {
        return embeddedReferenceBlockContentID;
    }

    public void setEmbeddedReferenceBlock(final Block embeddedReferenceBlock) {
        ValidationUtils.nonNull(embeddedReferenceBlock, "Embedded reference block must be non-null");
        ValidationUtils.validateArg(embeddedReferenceBlock.getContentId() != EMBEDDED_REFERENCE_ABSENT_CONTENT_ID,
                String.format("Invalid content ID (%d) for embedded reference block", embeddedReferenceBlock.getContentId()));
        ValidationUtils.validateArg(embeddedReferenceBlock.getContentType() == BlockContentType.EXTERNAL,
                String.format("Invalid embedded reference block type (%s)", embeddedReferenceBlock.getContentType()));
        if (this.embeddedReferenceBlock != null) {
            throw new CRAMException("Can't reset the slice embedded reference block");
        } else if (this.embeddedReferenceBlockContentID != EMBEDDED_REFERENCE_ABSENT_CONTENT_ID &&
                embeddedReferenceBlock.getContentId() != this.embeddedReferenceBlockContentID) {
            throw new CRAMException(
                    String.format(
                            "Embedded reference block content id (%d) conflicts with existing block if (%d)",
                            embeddedReferenceBlock.getContentId(),
                            this.embeddedReferenceBlockContentID));
        }

        setEmbeddedReferenceContentID(embeddedReferenceBlock.getContentId());
        this.embeddedReferenceBlock = embeddedReferenceBlock;
    }

    /**
     * Return the embedded reference block, if any.
     * @return embedded reference block. May be null.
     */
    // Unused because embedded reference isn't implemented for write
    public Block getEmbeddedReferenceBlock() { return embeddedReferenceBlock; }

    public CompressionHeader getCompressionHeader() { return compressionHeader; }

    /**
     * Reads and decodes the underlying blocks and returns a list of CRAMCompressionRecord. This isn't done initially
     * when the blocks are read from the underlying stream since there are cases where we want to iterate
     * through containers or slices and consume the underlying blocks, but not actually pay the price to
     * decode the records (i.e., during indexing, or when satisfying index queries).
     *
     * The CRAMRecords returned from this are not normalized (read bases, quality scores and mates have not
     * been resolved). See {@link #normalizeCRAMRecords} for more information about normalization.
     *
     * @param compressorCache cached compressor objects to use to decode streams
     * @param validationStringency validation stringency to use
     * @return list of raw (not normalized) CRAMCompressionRecord for this Slice ({@link #normalizeCRAMRecords})
     */
    public ArrayList<CRAMCompressionRecord> deserializeCRAMRecords(
            final CompressorCache compressorCache,
            final ValidationStringency validationStringency) {
        final CramRecordReader cramRecordReader = new CramRecordReader(this, compressorCache, validationStringency);
        final ArrayList<CRAMCompressionRecord> cramCompressionRecords = new ArrayList<>(nRecords);

        // in the case where APDelta = true, the first record in the slice has a 0 position delta, so initialize
        // prevAlignmentStart using the slice alignment start
        int prevAlignmentStart = alignmentContext.getAlignmentStart();
        for (int i = 0; i < nRecords; i++) {
            // read the new record and update the running prevAlignmentStart
            final CRAMCompressionRecord cramCompressionRecord = cramRecordReader.readCRAMRecord(globalRecordCounter + i, prevAlignmentStart);
            prevAlignmentStart = cramCompressionRecord.getAlignmentStart();
            cramCompressionRecords.add(cramCompressionRecord);
        }

        return cramCompressionRecords;
    }

    /**
     * Normalize a list of CRAMCompressionRecord that have been read in from a CRAM stream. Normalization converts raw
     * CRAM records to a state suitable for conversion to SAMRecords, resolving read bases against
     * the reference, as well as quality scores and mates.
     * The records in this list being normalized should be the records from a Slice, not an entire Container,
     * since the relative positions of mate records are determined relative to the Slice (downstream)
     * offsets.
     *
     * NOTE: This mutates (normalizes) the CRAM records in place.
     *
     * @param cramCompressionRecords CRAMCompressionRecords to normalize
     * @param cramReferenceRegion the reference region for this slice
     */
    public void normalizeCRAMRecords(final List<CRAMCompressionRecord> cramCompressionRecords,
                                     final CRAMReferenceRegion cramReferenceRegion) {
        byte[] referenceBases = null;
        boolean hasEmbeddedReference = false;
        if (compressionHeader.isReferenceRequired()) {
            // validate reference MD5 now that we have access to the reference bases
            if (getAlignmentContext().getReferenceContext().isMappedSingleRef()) {
                referenceBases = cramReferenceRegion.getReferenceBases(
                        getAlignmentContext().getReferenceContext().getReferenceSequenceID()
                );

                if (!referenceMD5IsValid(referenceBases)) {
                    throw new CRAMException(String.format(
                            "Reference sequence MD5 mismatch for slice: %s, expected MD5 %s",
                            getAlignmentContext(),
                            String.format("%032x", new BigInteger(1, getReferenceMD5()))));
                }
            }
        } else {
            // RR = false might mean that no reference compression was used, or that an embedded reference
            // was used, so if there is an embedded ref block, use it, and either way, skip MD5 validation
            final Block embeddedReferenceBlock = getEmbeddedReferenceBlock();
            if (embeddedReferenceBlock != null) {
                hasEmbeddedReference = true;
                cramReferenceRegion.setEmbeddedReference(
                        embeddedReferenceBlock.getUncompressedContent(new CompressorCache()),
                        getAlignmentContext().getReferenceContext().getReferenceSequenceID());
            }
        }

        // restore mate pairing first:
        for (final CRAMCompressionRecord record : cramCompressionRecords) {
            if (record.isReadPaired() &&
                    !record.isDetached() &&
                    record.isHasMateDownStream()) {
                final CRAMCompressionRecord downMate = cramCompressionRecords.get(
                        // getRecordsToNextFragment returns the value from the NF ("next fragment") data series,
                        // which is interpreted as the number of records to skip within this slice to find the next
                        // mate for this fragment
                        (int) (record.getSequentialIndex() + record.getRecordsToNextFragment() + 1L - globalRecordCounter));
                record.setNextSegment(downMate);
                downMate.setPreviousSegment(record);
            }
        }
        for (final CRAMCompressionRecord record : cramCompressionRecords) {
            if (record.getPreviousSegment() == null && record.getNextSegment() != null) {
                record.restoreMateInfo();
            }
        }

        // assign read names if needed (should be called after mate resolution so generated names
        // can be propagated to mates):
        for (final CRAMCompressionRecord record : cramCompressionRecords) {
            record.assignReadName();
        }

        // resolve bases:
        for (final CRAMCompressionRecord record : cramCompressionRecords) {
            if (!record.isSegmentUnmapped()) {
                if (compressionHeader.isReferenceRequired()) {
                    // we need to re-resolve the reference bases for each record we visit, since this might be a
                    // multi-reference slice, in which case all the reads are not necessarily mapped to the same
                    // reference contig
                    referenceBases = cramReferenceRegion.getReferenceBases(record.getReferenceIndex());
                } else if (hasEmbeddedReference) {
                    referenceBases = cramReferenceRegion.getCurrentReferenceBases();
                }
                record.restoreReadBases(
                        referenceBases,
                        getReferenceOffset(hasEmbeddedReference),
                        getCompressionHeader().getSubstitutionMatrix());
            }
        }

        for (final CRAMCompressionRecord record : cramCompressionRecords) {
            // resolve quality scores:
            record.resolveQualityScores();

            // in this last pass, set all records as normalized
            record.setIsNormalized();
        }
     }

    private int getReferenceOffset(final boolean hasEmbeddedReference) {
        final ReferenceContext sliceReferenceContext = getAlignmentContext().getReferenceContext();
        return sliceReferenceContext.isMappedSingleRef() && hasEmbeddedReference ?
            getAlignmentContext().getAlignmentStart() - 1 :
            0;
    }

    private int caclulateNumberOfBlocks() {
        // Each Slice has 1 core data block, plus zero or more external data blocks.
        // Since an embedded reference block is just stored as an external block, it is included in
        // the external block count, and does not need to be counted separately.
        return 1 + getSliceBlocks().getNumberOfExternalBlocks();
    }

    public void write(final CRAMVersion cramVersion, final OutputStream outputStream) {
        // establish our header block, then write it out
        sliceHeaderBlock = Block.createRawSliceHeaderBlock(createSliceHeaderBlockContent(cramVersion));
        sliceHeaderBlock.write(cramVersion, outputStream);

        // write the core and external blocks
        getSliceBlocks().writeBlocks(cramVersion, outputStream);
    }

    private byte[] createSliceHeaderBlockContent(final CRAMVersion cramVersion) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ITF8.writeUnsignedITF8(getAlignmentContext().getReferenceContext().getReferenceContextID(), byteArrayOutputStream);
        ITF8.writeUnsignedITF8(getAlignmentContext().getAlignmentStart(), byteArrayOutputStream);
        ITF8.writeUnsignedITF8(getAlignmentContext().getAlignmentSpan(), byteArrayOutputStream);
        ITF8.writeUnsignedITF8(getNumberOfRecords(), byteArrayOutputStream);
        LTF8.writeUnsignedLTF8(getGlobalRecordCounter(), byteArrayOutputStream);
        ITF8.writeUnsignedITF8(getNumberOfBlocks(), byteArrayOutputStream);

        setContentIDs(getSliceBlocks().getExternalContentIDs());
        CramIntArray.write(getContentIDs(), byteArrayOutputStream);
        ITF8.writeUnsignedITF8(getEmbeddedReferenceContentID(), byteArrayOutputStream);
        try {
            byteArrayOutputStream.write(getReferenceMD5() == null ? new byte[MD5_BYTE_SIZE] : getReferenceMD5());
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }

        if (cramVersion.getMajor() >= CramVersions.CRAM_v3.getMajor()) {
            SAMBinaryTagAndValue samBinaryTagAndValue = getSliceTags();
            if (samBinaryTagAndValue != null) {
                try (final BinaryCodec binaryCodec = new BinaryCodec(byteArrayOutputStream)) {
                    final BinaryTagCodec binaryTagCodec = new BinaryTagCodec(binaryCodec);
                    while (samBinaryTagAndValue != null) {
                        binaryTagCodec.writeTag(
                                samBinaryTagAndValue.tag,
                                samBinaryTagAndValue.value,
                                samBinaryTagAndValue.isUnsignedArray());
                        samBinaryTagAndValue = samBinaryTagAndValue.getNext();
                    }
                }
            }
        }

        return byteArrayOutputStream.toByteArray();
    }

    /**
     * Confirm that we have initialized the 3 BAI index parameters:
     * byteOffsetFromCompressionHeaderStart, containerByteOffset, and index
     */
    private void baiIndexInitializationCheck() {
        final StringBuilder error = new StringBuilder();

        if (byteOffsetOfSliceHeaderBlock == UNINITIALIZED_INDEXING_PARAMETER) {
            error.append("Cannot index this Slice for BAI because its byteOffsetFromCompressionHeaderStart is unknown.").append(System.lineSeparator());
        }

        if (landmarkIndex == UNINITIALIZED_INDEXING_PARAMETER) {
            error.append("Cannot index this Slice for BAI because its index is unknown.").append(System.lineSeparator());
        }

        if (error.length() > 0) {
            throw new CRAMException(error.toString());
        }
    }

    /**
     * Confirm that we have initialized the 3 CRAI index parameters:
     * byteOffsetFromCompressionHeaderStart, containerByteOffset, and byteSize
     */
    private void craiIndexInitializationCheck() {
        final StringBuilder error = new StringBuilder();

        if (byteOffsetOfSliceHeaderBlock == UNINITIALIZED_INDEXING_PARAMETER) {
            error.append("Cannot index this Slice for CRAI because its byteOffsetFromCompressionHeaderStart is unknown.").append(System.lineSeparator());
        }

        if (byteSizeOfSliceBlocks == UNINITIALIZED_INDEXING_PARAMETER) {
            error.append("Cannot index this Slice for CRAI because its byteSize is unknown.").append(System.lineSeparator());
        }

        if (error.length() > 0) {
            throw new CRAMException(error.toString());
        }
    }

    private static final AlignmentContext getDerivedAlignmentContext(
            final Set<ReferenceContext> sliceReferenceContexts,
            final int singleRefAlignmentStart,
            final int singleRefAlignmentEnd) {
        ReferenceContext referenceContext;
        switch (sliceReferenceContexts.size()) {
            case 0:
                referenceContext = ReferenceContext.UNMAPPED_UNPLACED_CONTEXT;
                break;
            case 1:
                // there is only one reference context, all reads placed are either on the same reference
                // or are unmapped
                referenceContext = sliceReferenceContexts.iterator().next();
                break;
            default:
                // placed reads on multiple references and/or a combination of placed and unplaced reads
                referenceContext = ReferenceContext.MULTIPLE_REFERENCE_CONTEXT;
        }

        if (referenceContext.isMappedSingleRef()) {
            AlignmentContext.validateAlignmentContext(
                    true, referenceContext,
                    singleRefAlignmentStart,
                    singleRefAlignmentEnd - singleRefAlignmentStart + 1);
            return new AlignmentContext(
                    referenceContext,
                    singleRefAlignmentStart,
                    singleRefAlignmentEnd - singleRefAlignmentStart + 1);
        } else if (referenceContext.isUnmappedUnplaced()) {
            return AlignmentContext.UNMAPPED_UNPLACED_CONTEXT;
        } else {
            return AlignmentContext.MULTIPLE_REFERENCE_CONTEXT;
        }
    }

    private void validateAlignmentSpanForReference(final byte[] referenceBases) {
        if (alignmentContext.getReferenceContext().isUnmappedUnplaced()) {
            return;
        }

        if (referenceBases == null &&
                alignmentContext.getAlignmentStart() > 0 &&
                alignmentContext.getReferenceContext().isMappedSingleRef()) {
            throw new CRAMException ("No reference bases found for mapped slice .");
        }

        //TODO: CRAMComplianceTest/c1#bounds triggers this (the reads are mapped beyond reference length),
        // and CRAMEdgeCasesTest.testNullsAndBeyondRef seems to deliberately test that reads that extend
        // beyond the reference length should be ok ?
        if (alignmentContext.getAlignmentStart() > referenceBases.length) {
            log.warn(String.format(
                    "Slice mapped outside of reference: seqID=%s, start=%d, record counter=%d.",
                    alignmentContext.getReferenceContext(),
                    alignmentContext.getAlignmentStart(),
                    globalRecordCounter));
        }

        if (alignmentContext.getAlignmentStart() - 1 + alignmentContext.getAlignmentSpan() > referenceBases.length) {
            log.warn(String.format("Slice mapped outside of reference: seqID=%s, start=%d, span=%d, counter=%d.",
                    alignmentContext.getReferenceContext(),
                    alignmentContext.getAlignmentStart(),
                    alignmentContext.getAlignmentSpan(),
                    globalRecordCounter));
        }
    }

    //VisibleForTesting
    boolean referenceMD5IsValid(final byte[] referenceBases) {
        if (alignmentContext.getReferenceContext().isMappedSingleRef() && compressionHeader.isReferenceRequired()) {
            validateAlignmentSpanForReference(referenceBases);
            if (!referenceMD5IsValid(
                    referenceBases,
                    alignmentContext.getAlignmentStart(),
                    alignmentContext.getAlignmentSpan(),
                    referenceMD5)) {
                throw new CRAMException(String.format("Reference MD5 failed to validate against %s",
                        String.format("%032x", new BigInteger(1, referenceMD5))));
            }
        }
        return true;
    }

    private static boolean referenceMD5IsValid(
            final byte[] referenceBases,
            final int alignmentStart,
            final int alignmentSpan,
            final byte[] expectedMD5) {
        final int span = Math.min(alignmentSpan, referenceBases.length - alignmentStart + 1);
        final byte md5[] = SequenceUtil.calculateMD5(referenceBases, alignmentStart - 1, span);
        return Arrays.equals(md5, expectedMD5);
    }

    @Override
    public String toString() {
        return String.format(
                "slice: %s globalRecordCounter=%d, nRecords=%d, sliceHeaderOffset=%d, sizeOfBlocks=%d, landmark=%d, mapped/unmapped/unplaced: %d/%d/%d, md5=%s",
                alignmentContext,
                globalRecordCounter,
                nRecords,
                getByteOffsetOfSliceHeaderBlock(),
                getByteSizeOfSliceBlocks(),
                landmarkIndex,
                mappedReadsCount,
                unmappedReadsCount,
                unmappedReadsCount,
                String.format("%032x", new BigInteger(1, getReferenceMD5())));
    }

    // *calculate* the MD5 for this reference
    public void setReferenceMD5(final byte[] ref) {
        validateAlignmentSpanForReference(ref);

        if (! alignmentContext.getReferenceContext().isMappedSingleRef() && alignmentContext.getAlignmentStart() < 1) {
            referenceMD5 = new byte[MD5_BYTE_SIZE];
        } else {
            final int span = Math.min(alignmentContext.getAlignmentSpan(), ref.length - alignmentContext.getAlignmentStart() + 1);
            if (alignmentContext.getAlignmentStart() + span > ref.length + 1) {
                throw new CRAMException("Invalid alignment boundaries.");
            }
            referenceMD5 = SequenceUtil.calculateMD5(ref, alignmentContext.getAlignmentStart() - 1, span);
        }
    }

    /**
     * Hijacking attributes-related methods from SAMRecord:
     */

    /**
     * Set a value for the tag.
     * @param tag tag ID as a short integer as returned by {@link SAMTag#makeBinaryTag(String)}
     * @param value tag value
     */
    public void setAttribute(final String tag, final Object value) {
        if (value != null && value.getClass().isArray() && Array.getLength(value) == 0) {
            throw new IllegalArgumentException("Empty value passed for tag " + tag);
        }
        setAttribute(SAMTag.makeBinaryTag(tag), value);
    }

    void setAttribute(final short tag, final Object value) {
        setAttribute(tag, value, false);
    }

    void setAttribute(final short tag, final Object value, final boolean isUnsignedArray) {
        if (value == null) {
            if (this.sliceTags != null) this.sliceTags = this.sliceTags.remove(tag);
        } else {
            final SAMBinaryTagAndValue tmp;
            if (!isUnsignedArray) {
                tmp = new SAMBinaryTagAndValue(tag, value);
            } else {
                tmp = new SAMBinaryTagAndUnsignedArrayValue(tag, value);
            }
            if (this.sliceTags == null) this.sliceTags = tmp;
            else this.sliceTags = this.sliceTags.insert(tmp);
        }
    }

    /**
     * Uses a Multiple Reference Slice Alignment Reader to determine the reference spans of a MULTI_REF Slice.
     * Used for creating CRAI/BAI index entries.
     *
     * @param validationStringency how strict to be when reading CRAM records
     */
    //VisibleForTesting
    public Map<ReferenceContext, AlignmentSpan> getMultiRefAlignmentSpans(
            final CompressorCache compressorCache,
            final ValidationStringency validationStringency) {
        if (!getAlignmentContext().getReferenceContext().isMultiRef()) {
            throw new IllegalStateException("can only create multiref span reader for multiref context slice");
        }

        // Ideally, we wouldn't have to decode the records just to get the slice spans; multi-ref
        // slices use the RI series for the reference index so in theory we could just decode that; but we also
        // need the alignment start and span for indexing. Is it possible to do this more efficiently ?
        // See https://github.com/samtools/htsjdk/issues/1347.
        // Note that this doesn't normalize the CRAMCompressionRecord, which bypasses resolution of bases
        // against the reference.
        final List<CRAMCompressionRecord> cramCompressionRecords = deserializeCRAMRecords(compressorCache, validationStringency);

        final Map<ReferenceContext, AlignmentSpan> spans = new HashMap<>();
        cramCompressionRecords.forEach(r -> mergeRecordSpan(r, spans));
        return Collections.unmodifiableMap(spans);
    }

    private void mergeRecordSpan(final CRAMCompressionRecord cramCompressionRecord, final Map<ReferenceContext, AlignmentSpan> spans) {
        // if unplaced: create or replace the current spans map entry.
        // we don't need to combine entries for different records because
        // we count them elsewhere and span is irrelevant

        // we need to combine the records' spans for counting and span calculation
        if (cramCompressionRecord.isSegmentUnmapped()) {
            if (cramCompressionRecord.getAlignmentStart() == SAMRecord.NO_ALIGNMENT_START) {
                // count it as both unmapped *and* unplaced, since for BAI we distinguish between them
                final AlignmentSpan span = new AlignmentSpan(
                        SAMRecord.NO_ALIGNMENT_START,
                        0,
                        0,
                        1,
                        1);
                spans.merge(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, span, AlignmentSpan::combine);
            } else {
                // merge it in with the reference context its mapped to
                final AlignmentSpan span = new AlignmentSpan(
                        cramCompressionRecord.getAlignmentStart(),
                        cramCompressionRecord.getReadLength(),
                        0,
                        1,
                        0);
                final int refIndex = cramCompressionRecord.getReferenceIndex();
                spans.merge(new ReferenceContext(refIndex), span, AlignmentSpan::combine);
            }
        } else {
            // 1 mapped, 0 unmapped, 0 unplaced
            final AlignmentSpan span = new AlignmentSpan(
                    cramCompressionRecord.getAlignmentStart(),
                    cramCompressionRecord.getAlignmentEnd() - cramCompressionRecord.getAlignmentStart(),
                    1,
                    0,
                    0);
            final ReferenceContext recordContext = new ReferenceContext(cramCompressionRecord.getReferenceIndex());
            spans.merge(recordContext, span, AlignmentSpan::combine);
        }
    }

    /**
     * Generate a CRAI Index entry from this Slice and other container parameters,
     * splitting Multiple Reference slices into constituent reference sequence entries.
     *
     * @return a list of CRAI Index Entries derived from this Slice
     */
    // Each line represents a slice in the CRAM file. Please note that all slices must be listed in the index file.
    // Multi-reference slices may need to have multiple lines for the same slice; one for each reference contained
    // within that slice. In this case the index reference sequence ID will be the actual reference ID (from the
    // “RI” data series) and not -2.
    //
    // Slices containing solely unmapped unplaced data (reference ID -1) still require values for all columns,
    // although the alignment start and span will be ignored. It is recommended that they are both set to zero.
    public List<CRAIEntry> getCRAIEntries(final CompressorCache compressorCache) {
        craiIndexInitializationCheck();

        if (alignmentContext.getReferenceContext().isMultiRef()) {
            final Map<ReferenceContext, AlignmentSpan> spans = getMultiRefAlignmentSpans(
                    compressorCache,
                    ValidationStringency.DEFAULT_STRINGENCY);

            return spans.entrySet().stream()
                    .map(e -> new CRAIEntry(
                            e.getKey().getReferenceContextID(),
                            e.getValue().getAlignmentStart(),
                            e.getValue().getAlignmentSpan(),
                            byteOffsetOfContainer,
                            byteOffsetOfSliceHeaderBlock,
                            byteSizeOfSliceBlocks)
                    )
                    .sorted()
                    .collect(Collectors.toList());
        } else {
            // single ref or unmapped
            final int sequenceId = alignmentContext.getReferenceContext().getReferenceContextID();
            return Collections.singletonList(
                    new CRAIEntry(
                        sequenceId,
                        alignmentContext.getAlignmentStart(),
                        alignmentContext.getAlignmentSpan(),
                        byteOffsetOfContainer,
                        byteOffsetOfSliceHeaderBlock,
                        byteSizeOfSliceBlocks)
            );
        }
    }

    /**
     * Generate a BAIEntry Index entry from this Slice and other container parameters,
     * splitting Multiple Reference slices into constituent reference sequence entries.
     *
     * @return a list of BAIEntry Index Entries derived from this Slice
     */
    public List<BAIEntry> getBAIEntries(final CompressorCache compressorCache) {
        baiIndexInitializationCheck();

        final List<BAIEntry> baiEntries = new ArrayList<>();
        switch (getAlignmentContext().getReferenceContext().getType()) {
            case UNMAPPED_UNPLACED_TYPE:
                baiEntries.add(
                        new BAIEntry(
                            getAlignmentContext().getReferenceContext(),
                            new AlignmentSpan(
                                    0,
                                    0,
                                    mappedReadsCount, //aligned
                                    unmappedReadsCount,
                                    unplacedReadsCount),
                            byteOffsetOfContainer,
                            byteOffsetOfSliceHeaderBlock,
                            landmarkIndex
                        )
                );
                break;

            case MULTIPLE_REFERENCE_TYPE:
                // NOTE: its possible that there are several different reference contexts embedded in this slice
                // i.e., there might be only one record per reference context, and thus not enough of any one
                // to warrant a separate slice)
                // unmapped span needs to go last
                final Map<ReferenceContext, AlignmentSpan> sliceSpanMap = getMultiRefAlignmentSpans(
                        compressorCache,
                        ValidationStringency.LENIENT);
                sliceSpanMap.entrySet().stream().filter(as -> !as.getKey().equals(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)).forEach(
                        entry -> baiEntries.add(
                                new BAIEntry(
                                    entry.getKey(),
                                    new AlignmentSpan(
                                            entry.getValue().getAlignmentStart(),
                                            entry.getValue().getAlignmentSpan(),
                                            entry.getValue().getMappedCount(),
                                            entry.getValue().getUnmappedCount(),
                                            entry.getValue().getUnmappedUnplacedCount()),
                                    byteOffsetOfContainer,
                                    byteOffsetOfSliceHeaderBlock,
                                    landmarkIndex)
                        )
                );
                final AlignmentSpan unmappedSpan = sliceSpanMap.get(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT);
                if (unmappedSpan != null) {
                    baiEntries.add(
                            new BAIEntry(
                                    ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                                    unmappedSpan,
                                    byteOffsetOfContainer,
                                    byteOffsetOfSliceHeaderBlock,
                                    landmarkIndex
                            )
                     );
                }
                break;

            default:
                baiEntries.add(
                        new BAIEntry(
                            getAlignmentContext().getReferenceContext(),
                            new AlignmentSpan(
                                    getAlignmentContext().getAlignmentStart(),
                                    getAlignmentContext().getAlignmentSpan(),
                                    getMappedReadsCount(),
                                    getUnmappedReadsCount(),
                                    getUnplacedReadsCount()),
                            byteOffsetOfContainer,
                            byteOffsetOfSliceHeaderBlock,
                            landmarkIndex
                        )
                );
                break;
        }

        return baiEntries;
    }

}
