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
import htsjdk.samtools.cram.build.CRAMReferenceState;
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
 * A CRAM slice is a logical union of blocks into for example alignment slices.
 *
 * NOTE: Every Slice has a reference context (it is either single-ref/mapped, unmapped, or multi-ref). But
 * single-ref mapped doesn't mean that the records are MAPPED (that is, that their getMappedRead flag is true),
 * only that the records in that slice are PLACED on the corresponding reference contig.
 */
public class Slice {
    private static final Log log = Log.getInstance(Slice.class);
    private static final int MD5_BYTE_SIZE = 16;
    // for indexing purposes
    //TODO: this should be the same as CRAMRecord.SLICE_INDEX_DEFAULT when used for sliceIndex
    public static final int UNINITIALIZED_INDEXING_PARAMETER = -1;
    // the spec defines a special sentinel to indicate the absence of an embedded reference block
    public static final int EMBEDDED_REFERENCE_ABSENT_CONTENT_ID = -1;

    ////////////////////////////////
    // Slice header values as defined in the spec
    private final AlignmentContext alignmentContext; // ref sequence, alignment start and span
    private final int nRecords;
    private final long globalRecordCounter;
    private final int nBlocks;              // includes the core block, but not the slice header block
    private int[] contentIDs;
    private int embeddedReferenceBlockContentID = EMBEDDED_REFERENCE_ABSENT_CONTENT_ID;
    private byte[] referenceMD5 = new byte[MD5_BYTE_SIZE];
    private SAMBinaryTagAndValue sliceTags;
    // End slice header values
    ////////////////////////////////

    private final CompressionHeader compressionHeader;
    private final SliceBlocks sliceBlocks = new SliceBlocks();
    private final long byteOffsetOfContainer;

    private Block sliceHeaderBlock;
    // Modeling the contentID and embedded reference block separately is redundant, since the
    // block can be retrieved from the external blocks list given the content id, but we retain
    // them both for validation purposes because they're both present in the serialized CRAM stream,
    // and on read these are provided separately when populating the slice.
    private Block embeddedReferenceBlock;
    private long baseCount;

    // Values used for indexing. Even though AlignmentSpan could be used here, we don't use it
    // because the alignment context part of the AlignmentSpan in the SliceHeader, so keeping it
    // here would be redundant.
    // These values are only maintained for slices that are created from SAM/CRAMRecords. Slices that
    // are created from deserializing a stream do not have recorded values for these because they're
    // not kept in the stream, and the individual records are not decoded until they're requested (and
    // they are not decoded during indexing, with the exception of MULTI_REF slices, where its required
    // that the slice be resolved into individual reference contexts for inclusion in the index).
    private int mappedReadsCount = 0;   // mapped (rec.getReadUnmappedFlag() != true)
    private int unmappedReadsCount = 0; // unmapped (rec.getReadUnmappedFlag() == true)
    private int unplacedReadsCount = 0; // nocoord (alignmentStart == SAMRecord.NO_ALIGNMENT_START)

    private int byteOffsetOfSliceHeaderBlock = UNINITIALIZED_INDEXING_PARAMETER;
    private int byteSizeOfSliceBlocks = UNINITIALIZED_INDEXING_PARAMETER;
    private int landmarkIndex = UNINITIALIZED_INDEXING_PARAMETER;

    public Slice(
            final int major,
            final CompressionHeader compressionHeader,
            final InputStream inputStream,
            final long containerByteOffset) {
        sliceHeaderBlock = Block.read(major, inputStream);
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
        this.nBlocks = ITF8.readUnsignedITF8(parseInputStream);

        setContentIDs(CramIntArray.array(parseInputStream));
        // embedded ref content id == -1 if embedded ref not present
        embeddedReferenceBlockContentID = ITF8.readUnsignedITF8(parseInputStream);
        referenceMD5 = new byte[MD5_BYTE_SIZE];
        InputStreamUtils.readFully(parseInputStream, referenceMD5, 0, referenceMD5.length);

        final byte[] readTagBytes = InputStreamUtils.readFully(parseInputStream);
        if (major >= CramVersions.CRAM_v3.major) {
            setSliceTags(BinaryTagCodec.readTags(
                    readTagBytes, 0, readTagBytes.length, ValidationStringency.DEFAULT_STRINGENCY));
        }

        //NOTE: this reads the underlying blocks from the stream, but doesn't decode them because we don't want
        // to do this automatically since there are case where we want to iterate through containers or slices
        // (i.e., during indexing, or when satisfying index queries) when we want to consume the underlying blocks,
        // but not actually decode them
        sliceBlocks.readBlocks(major, nBlocks, inputStream);

        if (embeddedReferenceBlockContentID != EMBEDDED_REFERENCE_ABSENT_CONTENT_ID) {
            // also adds this block to the external list
            setEmbeddedReferenceBlock(sliceBlocks.getExternalBlock(embeddedReferenceBlockContentID));
        }
    }

    /**
     * Create a single Slice from CRAM Compression Records and a Compression Header.
     * The caller is responsible for appropriate subdivision of records into
     * containers and slices.
     *
     * @param records input CRAM Compression Records
     * @param compressionHeader the enclosing {@link Container}'s Compression Header
     * @return a Slice corresponding to the given records
     *
     * Using a collection of {@link CRAMRecord}s,
     * determine whether the slice is single ref, unmapped or multi reference.
     * Derive alignment boundaries for the slice if single ref.
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
     * @see CRAMRecord#isPlaced()
     *
     * @see ReferenceContextType
     * @param records the input records
     * @return the initialized Slice
     */
    public Slice(
            final List<CRAMRecord> records,
            final CompressionHeader compressionHeader,
            final long containerByteOffset,
            final long globalRecordCounter) {
        this.compressionHeader = compressionHeader;
        this.byteOffsetOfContainer = containerByteOffset;

        final ContentDigests hasher = ContentDigests.create(ContentDigests.ALL);
        final Set<ReferenceContext> referenceContexts = new HashSet<>();
        // ignore these values if we later determine this Slice is not single-ref
        int singleRefAlignmentStart = Integer.MAX_VALUE;
        int singleRefAlignmentEnd = SAMRecord.NO_ALIGNMENT_START;

        int baseCount = 0;
        for (final CRAMRecord record : records) {
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

            // TODO: either update isPlaced() to match this logic (i.e. don't check the reference ID)
            // or update BAMIndexMetadata.recordMetaData(SAMRecord) to have a similar notion of placement
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
        writer.writeToSliceBlocks(records, alignmentContext.getAlignmentStart());

        // we can't calculate the number of blocks until after the record writer has written everything out
        nBlocks = caclulateNumberOfBlocks();
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
    public int getNumberOfBlocks() { return nBlocks; }
    public int[] getContentIDs() {
        return contentIDs;
    }
    private void setContentIDs(int[] contentIDs) {
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
            throw new IllegalArgumentException(
                    String.format("Can't reset embedded reference content ID (old %d new %d)",
                            this.embeddedReferenceBlockContentID, embeddedReferenceBlockContentID));

        }
        if (this.embeddedReferenceBlock != null &&
                this.embeddedReferenceBlock.getContentId() != embeddedReferenceBlockContentID) {
            throw new IllegalArgumentException(
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
    private int getEmbeddedReferenceContentID() {
        return embeddedReferenceBlockContentID;
    }

    public void setEmbeddedReferenceBlock(final Block embeddedReferenceBlock) {
        ValidationUtils.nonNull(embeddedReferenceBlock, "Embedded reference block must be non-null");
        ValidationUtils.validateArg(embeddedReferenceBlock.getContentId() != EMBEDDED_REFERENCE_ABSENT_CONTENT_ID,
                String.format("Invalid content ID (%d) for embedded reference block", embeddedReferenceBlock.getContentId()));
        ValidationUtils.validateArg(embeddedReferenceBlock.getContentType() == BlockContentType.EXTERNAL,
                String.format("Invalid embedded reference block type (%s)", embeddedReferenceBlock.getContentType()));
        if (this.embeddedReferenceBlock != null) {
            throw new IllegalArgumentException("Can't reset embedded reference block");
        } else if (this.embeddedReferenceBlockContentID != EMBEDDED_REFERENCE_ABSENT_CONTENT_ID &&
                embeddedReferenceBlock.getContentId() != this.embeddedReferenceBlockContentID) {
            throw new IllegalArgumentException(
                    String.format(
                            "Embedded reference block content id (%d) conflicts with existing block if (%d)",
                            embeddedReferenceBlock.getContentId(),
                            this.embeddedReferenceBlockContentID));
        }

        setEmbeddedReferenceContentID(embeddedReferenceBlock.getContentId());
        this.embeddedReferenceBlock = embeddedReferenceBlock;
        sliceBlocks.addExternalBlock(embeddedReferenceBlock);
    }

    /**
     * Return the embedded reference block, if any.
     * @return embedded reference block. May be null.
     */
    // Unused because embedded reference isn't implemented for write
    public Block getEmbeddedReferenceBlock() { return embeddedReferenceBlock; }

    public CompressionHeader getCompressionHeader() {
        if (compressionHeader == null) {
            //TODO: do we need this guard....
            // temporary guard until the Slice(refContext) constructor is expunged
            throw new IllegalStateException("null compression header");
        }
        return compressionHeader;
    }

    //NOTE: this is what ACTUALLY decodes the underlying blocks. We don't do this automatically when initially
    // reading the blocks from the underlying stream since there are case where we want to iterate through
    // containers or slices (i.e., during indexing, or when satisfying index queries) where we want to consume
    // the underlying blocks, but not actually pay the price to decode them
    //
    // The CRAMRecords returned from this are not normalized (read bases, quality scores and mates have not
    // been resolved).
    public ArrayList<CRAMRecord> getRawCRAMRecords(
            final CompressorCache compressorCache,
            final ValidationStringency validationStringency) {
        final CramRecordReader reader = new CramRecordReader(this, compressorCache, validationStringency);
        final ArrayList<CRAMRecord> cramRecords = new ArrayList<>(nRecords);

        int prevAlignmentStart = alignmentContext.getAlignmentStart();
        for (int i = 0; i < nRecords; i++) {
            // read the new record and update the running prevAlignmentStart
            final CRAMRecord cramRecord = reader.read(globalRecordCounter + i, prevAlignmentStart);
            prevAlignmentStart = cramRecord.getAlignmentStart();
            cramRecords.add(cramRecord);
        }

        return cramRecords;
    }

    // Resolves read bases against a reference, and resolves quality scores and mates.
    //
    // Normalize a list of CramCompressionRecords that have been read in from a CRAM stream.
    // The records in this list should be the records from this slice, not the entire container,
    // since the relative positions of mate records are determined relative to the slice (downstream)
    // offsets.
    //
    //This has a side effect of updating the CRAM records in place.
    public void normalizeCRAMRecords(final List<CRAMRecord> records,
                                     CRAMReferenceState cramReferenceState,
                                     final int zeroBasedReferenceOffset,  //TODO: unused - always 0
                                     final SubstitutionMatrix substitutionMatrix) {
        // first, validate or reference MD5 now that we have access to the reference bases
        if (getAlignmentContext().getReferenceContext().isMappedSingleRef()) {
            final byte[] referenceBases = cramReferenceState.getReferenceBases(
                    getAlignmentContext().getReferenceContext().getReferenceSequenceID()
            );

            if (!validateReferenceMD5(referenceBases)) {
                throw new CRAMException(String.format(
                        "Reference sequence MD5 mismatch for slice: %s, expected MD5 %s",
                        getAlignmentContext(),
                        String.format("%032x", new BigInteger(1, getReferenceMD5()))));
            }
        }

        // restore pairing first:
        for (final CRAMRecord record : records) {
            if (record.isMultiFragment() &&
                    !record.isDetached() &&
                    record.isHasMateDownStream()) {
                final CRAMRecord downMate = records.get(
                        //TODO: add a method to do this calc
                        (int) (record.getSequentialIndex() + record.getRecordsToNextFragment() + 1L - globalRecordCounter));
                record.setNextSegment(downMate);
                downMate.setPreviousSegment(record);
            }
        }

        for (final CRAMRecord record : records) {
            if (record.getPreviousSegment() == null && record.getNextSegment() != null) {
                record.restoreMateInfo();
            }
        }

        // assign read names if needed:
        for (final CRAMRecord record : records) {
            record.assignReadName();
        }

        // resolve bases:
        for (final CRAMRecord record : records) {
            if (!record.isSegmentUnmapped()) {
                byte[] refBases = cramReferenceState.getReferenceBases(record.getReferenceIndex());
                record.restoreReadBases(refBases, zeroBasedReferenceOffset, substitutionMatrix);
            }
        }

        // restore quality scores:
        final byte defaultQualityScore = '?' - '!';
        //TODO: this should be a CRAMRecord instance method
        CRAMRecord.restoreQualityScores(defaultQualityScore, records);
    }

    private int caclulateNumberOfBlocks() {
        // Each Slice has 1 core data block, plus zero or more external data blocks.
        // Since an embedded reference block is just stored as an external block, it is included in
        // the external block count, and does not need to be counted separately.
        return 1 + getSliceBlocks().getNumberOfExternalBlocks();
    }

    public void write(final int major, final OutputStream outputStream) {
        // TODO: ensure that the Slice blockCount stays in sync with the
        // Container's blockCount in Container.writeContainer()

        // Each Slice has 1 core data block, plus zero or more external data blocks.
        // Since an embedded reference block is just stored as an external block, it is included in
        // the external block count, and does not need to be counted separately.
        //setNofBlocks(1 + getSliceBlocks().getNumberOfExternalBlocks());
        setContentIDs(new int[getSliceBlocks().getNumberOfExternalBlocks()]);
        final int i = 0;
        for (final int id : getSliceBlocks().getExternalContentIDs()) {
            getContentIDs()[i] = id;
        }
        // establish our header block before writing
        sliceHeaderBlock = Block.createRawSliceHeaderBlock(createSliceHeaderBlockContent(major, this));
        sliceHeaderBlock.write(major, outputStream);
        // writes the core, and external blocks
        getSliceBlocks().writeBlocks(major, outputStream);
    }

    private static byte[] createSliceHeaderBlockContent(final int major, final Slice slice) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ITF8.writeUnsignedITF8(slice.getAlignmentContext().getReferenceContext().getReferenceContextID(), byteArrayOutputStream);
        ITF8.writeUnsignedITF8(slice.getAlignmentContext().getAlignmentStart(), byteArrayOutputStream);
        ITF8.writeUnsignedITF8(slice.getAlignmentContext().getAlignmentSpan(), byteArrayOutputStream);
        ITF8.writeUnsignedITF8(slice.getNumberOfRecords(), byteArrayOutputStream);
        LTF8.writeUnsignedLTF8(slice.getGlobalRecordCounter(), byteArrayOutputStream);
        ITF8.writeUnsignedITF8(slice.getNumberOfBlocks(), byteArrayOutputStream);

        slice.setContentIDs(new int[slice.getSliceBlocks().getNumberOfExternalBlocks()]);
        int i = 0;
        for (final int id : slice.getSliceBlocks().getExternalContentIDs()) {
            slice.getContentIDs()[i++] = id;
        }
        CramIntArray.write(slice.getContentIDs(), byteArrayOutputStream);
        ITF8.writeUnsignedITF8(slice.getEmbeddedReferenceContentID(), byteArrayOutputStream);
        try {
            byteArrayOutputStream.write(slice.getReferenceMD5() == null ? new byte[16] : slice.getReferenceMD5());
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }

        if (major >= CramVersions.CRAM_v3.major) {
            if (slice.getSliceTags() != null) {
                final BinaryCodec binaryCoded = new BinaryCodec(byteArrayOutputStream);
                final BinaryTagCodec binaryTagCodec = new BinaryTagCodec(binaryCoded);
                SAMBinaryTagAndValue samBinaryTagAndValue = slice.getSliceTags();
                do {
                    log.debug("Writing slice tag: " + SAMTag.makeStringTag(samBinaryTagAndValue.tag));
                    binaryTagCodec.writeTag(samBinaryTagAndValue.tag, samBinaryTagAndValue.value, samBinaryTagAndValue.isUnsignedArray());
                } while ((samBinaryTagAndValue = samBinaryTagAndValue.getNext()) != null);
                // BinaryCodec doesn't seem to cache things.
                // In any case, not calling baseCodec.close() because it's behaviour is
                // irrelevant here.
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

    private final AlignmentContext getDerivedAlignmentContext(
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
        //TODO this aggressively validates derived context against the start/span values. Even though
        // these are not the start/span values we use below to create the actual derived context,
        // they still SHOULD be valid (if they're not something else is wrong).
        //NOTE: this causes SAMFileWriterFactoryTest.testMakeWriterForCramExtensionNoReference (cram with only
        // unmapped records to throw):
        //htsjdk.samtools.cram.CRAMException: Unmapped/unplaced alignment context with invalid
        // start/span detected (2147483647/-2147483646)
        //AlignmentContext.validateAlignmentContext(
        //        true, referenceContext,
        //        singleRefAlignmentStart,
        //        singleRefAlignmentEnd - singleRefAlignmentStart + 1);

        if (referenceContext.isMappedSingleRef()) {
            AlignmentContext.validateAlignmentContext(
                    true, referenceContext,
                    singleRefAlignmentStart,
                    singleRefAlignmentEnd - singleRefAlignmentStart + 1);
            return new AlignmentContext(
                    referenceContext,
                    singleRefAlignmentStart,
                    //TODO: +1 ?
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
        // Is there any case where being mapped outside of the reference span is legitimate ?
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
    boolean validateReferenceMD5(final byte[] referenceBases) {
        if (alignmentContext.getReferenceContext().isMappedSingleRef()) {
            validateAlignmentSpanForReference(referenceBases);
            if (!validateReferenceMD5(
                    referenceBases,
                    alignmentContext.getAlignmentStart(),
                    alignmentContext.getAlignmentSpan(),
                    referenceMD5)) {
                //TODO: does the spec allow matching on partial reference like this ?
                //System.out.println("Failed MD5 check on full slice span - trying narrower span");
                final int shoulderLength = 10;
                final String excerpt = getReferenceBaseExcerpt(
                        alignmentContext.getAlignmentStart(),
                        alignmentContext.getAlignmentSpan()
                        , referenceBases,
                        shoulderLength);
                if (validateReferenceMD5(
                        referenceBases,
                        alignmentContext.getAlignmentStart(),
                        alignmentContext.getAlignmentSpan() - 1,
                        referenceMD5)) {
                    log.warn(String.format("Reference MD5 matches partially for slice %s:%d-%d, %s",
                            alignmentContext.getReferenceContext(),
                            alignmentContext.getAlignmentStart(),
                            alignmentContext.getAlignmentStart() + alignmentContext.getAlignmentSpan() - 1,
                            excerpt));
                    return true;
                }

                log.error(String.format(
                        "Reference MD5 mismatch for slice %s:%d-%d, %s",
                        alignmentContext.getReferenceContext(),
                        alignmentContext.getAlignmentStart(),
                        alignmentContext.getAlignmentStart() + alignmentContext.getAlignmentSpan() - 1,
                        excerpt));
                return false;
            }
        }

        return true;
    }

    private static boolean validateReferenceMD5(
            final byte[] referenceBases,
            final int alignmentStart,
            final int alignmentSpan,
            final byte[] expectedMD5) {
        final int span = Math.min(alignmentSpan, referenceBases.length - alignmentStart + 1);
        final String md5 = SequenceUtil.calculateMD5String(referenceBases, alignmentStart - 1, span);
        return md5.equals(String.format("%032x", new BigInteger(1, expectedMD5)));
    }

    //TODO:get rid of this if partial matching isn't accepted
    private static String getReferenceBaseExcerpt(
            final int startOneBased,
            final int span,
            final byte[] bases,
            final int shoulderLength) {
        if (span >= bases.length) {
            return new String(bases);
        }

        final StringBuilder sb = new StringBuilder();
        final int fromInc = startOneBased - 1;

        int toExc = startOneBased + span - 1;
        toExc = Math.min(toExc, bases.length);

        if (toExc - fromInc <= 2 * shoulderLength) {
            sb.append(new String(Arrays.copyOfRange(bases, fromInc, toExc)));
        } else {
            sb.append(new String(Arrays.copyOfRange(bases, fromInc, fromInc + shoulderLength)));
            sb.append("...");
            sb.append(new String(Arrays.copyOfRange(bases, toExc - shoulderLength, toExc)));
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("slice: %s, records %d", alignmentContext, nRecords);
    }

    // *calculate* the MD5 for this reference
    public void setReferenceMD5(final byte[] ref) {
        validateAlignmentSpanForReference(ref);

        if (! alignmentContext.getReferenceContext().isMappedSingleRef() && alignmentContext.getAlignmentStart() < 1) {
            referenceMD5 = new byte[MD5_BYTE_SIZE];
            Arrays.fill(referenceMD5, (byte) 0);
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

//    /**
//     * Get tag value attached to the slice.
//     * @param tag tag ID as a short integer as returned by {@link SAMTag#makeBinaryTag(String)}
//     * @return a value of the tag
//     */
//    public Object getAttribute(final short tag) {
//        if (this.sliceTags == null) return null;
//        else {
//            final SAMBinaryTagAndValue tmp = this.sliceTags.find(tag);
//            if (tmp != null) return tmp.value;
//            else return null;
//        }
//    }

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

//    //TODO this is unused
//    public void setUnsignedArrayAttribute(final String tag, final Object value) {
//        if (!value.getClass().isArray()) {
//            throw new IllegalArgumentException("Non-array passed to setUnsignedArrayAttribute for tag " + tag);
//        }
//        if (Array.getLength(value) == 0) {
//            throw new IllegalArgumentException("Empty array passed to setUnsignedArrayAttribute for tag " + tag);
//        }
//        setAttribute(SAMTag.makeBinaryTag(tag), value, true);
//    }

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
        if (!compressionHeader.isAPDelta()) {
            throw new IllegalStateException("Can't get multiref alignment spans for non-coordinate sorted inputs");
        }
        if (!getAlignmentContext().getReferenceContext().isMultiRef()) {
            throw new IllegalStateException("can only create multiref span reader for multiref context slice");
        }

        // Ideally, we wouldn't have to decode the records just to get the slice spans; multi-ref
        // slices use the RI series for the reference index so in theory we could just decode that; but we also
        // need the alignment start and span for indexing. Is it possible to do this more efficiently ?
        // See https://github.com/samtools/htsjdk/issues/1347.
        // Note that this doesn't normalize the CRAMRecords, which saves actually resolving the bases
        // against the reference.
        final List<CRAMRecord> cramRecords = getRawCRAMRecords(compressorCache, validationStringency);

        final Map<ReferenceContext, AlignmentSpan> spans = new HashMap<>();
        cramRecords.forEach(r -> mergeRecordSpan(r, spans));
        return Collections.unmodifiableMap(spans);
    }

    private void mergeRecordSpan(final CRAMRecord cramRecord, final Map<ReferenceContext, AlignmentSpan> spans) {
        // if unplaced: create or replace the current spans map entry.
        // we don't need to combine entries for different records because
        // we count them elsewhere and span is irrelevant

        // we need to combine the records' spans for counting and span calculation
        if (cramRecord.isSegmentUnmapped()) {
            //TODO: should we just change isPlaced to only check the alignment start, and then use that here ?
            if (cramRecord.getAlignmentStart() == SAMRecord.NO_ALIGNMENT_START) {
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
                        cramRecord.getAlignmentStart(),
                        cramRecord.getReadLength(),
                        0,
                        1,
                        0);
                final int refIndex = cramRecord.getReferenceIndex();
                spans.merge(new ReferenceContext(refIndex), span, AlignmentSpan::combine);
            }
        } else {
            // 1 mapped, 0 unmapped, 0 unplaced
            final AlignmentSpan span = new AlignmentSpan(
                    cramRecord.getAlignmentStart(),
                    cramRecord.getAlignmentEnd() - cramRecord.getAlignmentStart(),
                    1,
                    0,
                    0);
            final ReferenceContext recordContext = new ReferenceContext(cramRecord.getReferenceIndex());
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
        if (! compressionHeader.isAPDelta()) {
            throw new CRAMException("Cannot construct index if the CRAM is not coordinate Sorted");
        }

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
        if (!compressionHeader.isAPDelta()) {
            throw new CRAMException("Cannot construct index if the CRAM is not coordinate sorted");
        }

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
                sliceSpanMap.entrySet().stream().filter(as -> !as.equals(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)).forEach(
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

        //TODO: remove validation
        //TODO: slice mapped/unmapped/unplaced counts are only updated when reading the container from a stream
        //validateBAIEntries(baiEntries);
        return baiEntries;
    }

    private void validateBAIEntries(final List<BAIEntry> baiEntries) {
        // make sure we're not double counting
        int mappedReadsCount = 0;   // mapped (rec.getReadUnmappedFlag() != true)
        int unmappedReadsCount = 0; // unmapped (rec.getReadUnmappedFlag() == true)
        int unplacedReadsCount = 0; // nocoord (alignmentStart == SAMRecord.NO_ALIGNMENT_START)
        for (final BAIEntry baiEntry : baiEntries) {
            unmappedReadsCount += baiEntry.getUnmappedReadsCount();
            mappedReadsCount += baiEntry.getMappedReadsCount();
            unplacedReadsCount += baiEntry.getUnmappedUnplacedReadsCount();
        }

        if (mappedReadsCount != getMappedReadsCount() ||
            unmappedReadsCount != getUnmappedReadsCount() ||
            unplacedReadsCount != getUnplacedReadsCount()) {
            throw new CRAMException(String.format(
                    "Expected (mapped %d unmapped %d unplaced %d) but got (mapped %d unmapped %d unplaced %d)",
                    getMappedReadsCount(),
                    getUnmappedReadsCount(),
                    getUnplacedReadsCount(),
                    mappedReadsCount,
                    unmappedReadsCount,
                    unplacedReadsCount));
        }
    }

}
