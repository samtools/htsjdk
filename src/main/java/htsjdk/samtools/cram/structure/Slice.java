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
import htsjdk.samtools.cram.CRAIEntry;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.digest.ContentDigests;
import htsjdk.samtools.cram.encoding.reader.CramRecordReader;
import htsjdk.samtools.cram.encoding.reader.MultiRefSliceAlignmentSpanReader;
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

import java.io.*;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A CRAM slice is a logical union of blocks into for example alignment slices.
 */
public class Slice {
    private static final Log log = Log.getInstance(Slice.class);

    public static final int NO_ALIGNMENT_START = -1;
    public static final int NO_ALIGNMENT_SPAN = 0;
    public static final int NO_ALIGNMENT_END = SAMRecord.NO_ALIGNMENT_START; // 0

    // for indexing purposes
    //TODO: this should be the same as CRAMRecord.SLICE_INDEX_DEFAULT when used for sliceIndex
    public static final int UNINITIALIZED_INDEXING_PARAMETER = -1;

    ////////////////////////////////
    // Start slice header values
    // header values as defined in the specs, in addition to sequenceId from ReferenceContext
    // int sequenceID
    private final ReferenceContext referenceContext;
    // minimum alignment start of the reads in this Slice
    // uses a 1-based coordinate system
    private int alignmentStart = NO_ALIGNMENT_START;
    private int alignmentSpan = NO_ALIGNMENT_SPAN;
    private int nofRecords = -1;
    private long globalRecordCounter = -1;
    // total number of blocks in this slice, including the core block
    private int nofBlocks = -1;
    private int[] contentIDs;
    // embeddedReferenceContentID (stored in SliceBlocks)..
    private byte[] refMD5 = new byte[16];
    private SAMBinaryTagAndValue sliceTags;
    // End slice header values
    ////////////////////////////////

    private Block headerBlock;
    private CompressionHeader compressionHeader;
    private final SliceBlocks sliceBlocks = new SliceBlocks();

    private int byteOffsetFromCompressionHeaderStart = UNINITIALIZED_INDEXING_PARAMETER;
    private long containerByteOffset = UNINITIALIZED_INDEXING_PARAMETER;
    private int byteSize = UNINITIALIZED_INDEXING_PARAMETER;
    private int index = UNINITIALIZED_INDEXING_PARAMETER;
    // to pass this to the container:
    private long baseCount;

    // read counters per type, for BAMIndexMetaData.recordMetaData()
    // see also AlignmentSpan and CRAMBAIIndexer.processContainer()
    private int mappedReadsCount = 0;
    private int unmappedReadsCount = 0;
    private int unplacedReadsCount = 0;

    //TODO: this is the case where we're reading the slice from an input stream
    public Slice(final int major, final CompressionHeader compressionHeader, final InputStream inputStream) {
        headerBlock = Block.read(major, inputStream);
        if (headerBlock.getContentType() != BlockContentType.MAPPED_SLICE) {
            throw new RuntimeException("Slice Header Block expected, found:  " + headerBlock.getContentType().name());
        }

        final InputStream parseInputStream = new ByteArrayInputStream(headerBlock.getRawContent());

        //TODO: validate that this matches the container
        // if MULTIPLE_REFERENCE_ID, enclosing container must also be MULTIPLE_REFERENCE_ID
        this.referenceContext = new ReferenceContext(ITF8.readUnsignedITF8(parseInputStream));
        this.compressionHeader = compressionHeader;
        setAlignmentStart(ITF8.readUnsignedITF8(parseInputStream));
        setAlignmentSpan(ITF8.readUnsignedITF8(parseInputStream));
        setNofRecords(ITF8.readUnsignedITF8(parseInputStream));
        setGlobalRecordCounter(LTF8.readUnsignedLTF8(parseInputStream));
        setNofBlocks(ITF8.readUnsignedITF8(parseInputStream));

        setContentIDs(CramIntArray.array(parseInputStream));
        // embedded ref content id == -1 if embedded ref not present
        setEmbeddedReferenceContentID(ITF8.readUnsignedITF8(parseInputStream));
        setReferenceMD5(new byte[16]);
        InputStreamUtils.readFully(parseInputStream, getRefMD5(), 0, getRefMD5().length);

        final byte[] bytes = InputStreamUtils.readFully(parseInputStream);

        if (major >= CramVersions.CRAM_v3.major) {
            setSliceTags(BinaryTagCodec.readTags(bytes, 0, bytes.length, ValidationStringency.DEFAULT_STRINGENCY));
        }

        sliceBlocks.readBlocks(major, nofBlocks, inputStream);
    }

    /**
     * Construct this Slice by providing its {@link ReferenceContext}
     * @param refContext the reference context associated with this slice
     */
    // TODO: remove this constructor, or require it to set the comp header; this is only used in calls sites
    // TODO: where the comp header is irrelevant (ie. indexing) ? Unfortunately, this is the constructor thats
    // TODO: used all over the place...
    public Slice(final ReferenceContext refContext) {
        this.referenceContext = refContext;
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
    public Slice(final List<CRAMRecord> records, final CompressionHeader compressionHeader) {
        this.compressionHeader = compressionHeader;

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

            // this check matches the logic of BAMIndexMetadata.recordMetaData(SAMRecord)
            // we's prefer to use isPlaced() like we do elsewhere, but we'd have inconsistent results if we did

            // TODO? either update isPlaced() to match this logic (i.e. don't check the reference ID)
            // or update BAMIndexMetadata.recordMetaData(SAMRecord) to have a similar notion of placement

            if (record.getAlignmentStart() == SAMRecord.NO_ALIGNMENT_START) {
                unplacedReadsCount++;
            }
        }

        switch (referenceContexts.size()) {
            case 0:
                this.referenceContext = ReferenceContext.UNMAPPED_UNPLACED_CONTEXT;
                break;
            case 1:
                // SINGLE_REFERENCE_TYPE context: all reads placed on the same reference
                // or UNMAPPED_UNPLACED_CONTEXT: all reads unplaced
                this.referenceContext = referenceContexts.iterator().next();
                break;
            default:
                // placed reads on multiple references and/or a combination of placed and unplaced reads
                this.referenceContext = ReferenceContext.MULTIPLE_REFERENCE_CONTEXT;
        }

        if (this.referenceContext.isMappedSingleRef()) {
            alignmentStart = singleRefAlignmentStart;
            alignmentSpan = singleRefAlignmentEnd - singleRefAlignmentStart + 1;
        }

        sliceTags = hasher.getAsTags();
        this.baseCount = baseCount;
        nofRecords = records.size();

        final CramRecordWriter writer = new CramRecordWriter(this);
        writer.writeToSliceBlocks(records, this.alignmentStart);
    }

    // May be null
    public Block getHeaderBlock() { return headerBlock; }

    public SliceBlocks getSliceBlocks() { return sliceBlocks; }

    public ReferenceContext getReferenceContext() {
        return referenceContext;
    }

    public int getAlignmentStart() {
        return alignmentStart;
    }
    public void setAlignmentStart(int alignmentStart) {
        this.alignmentStart = alignmentStart;
    }

    public int getAlignmentSpan() {
        return alignmentSpan;
    }
    public void setAlignmentSpan(int alignmentSpan) {
        this.alignmentSpan = alignmentSpan;
    }

    public int getNofRecords() {
        return nofRecords;
    }
    private void setNofRecords(int nofRecords) {
        this.nofRecords = nofRecords;
    }

    public long getGlobalRecordCounter() {
        return globalRecordCounter;
    }
    public void setGlobalRecordCounter(long globalRecordCounter) {
        this.globalRecordCounter = globalRecordCounter;
    }

    public int getNofBlocks() {
        return nofBlocks;
    }
    public void setNofBlocks(int nofBlocks) {
        this.nofBlocks = nofBlocks;
    }

    public int[] getContentIDs() {
        return contentIDs;
    }
    public void setContentIDs(int[] contentIDs) {
        this.contentIDs = contentIDs;
    }

    public byte[] getRefMD5() {
        return refMD5;
    }

    /**
     * The Slice's offset in bytes from the beginning of the Container's Compression Header
     * (or the end of the Container Header), equal to {@link ContainerHeader#getLandmarks()}
     *
     * Used by BAI and CRAI indexing
     */
    public int getByteOffsetFromCompressionHeaderStart() {
        return byteOffsetFromCompressionHeaderStart;
    }

    public void setByteOffsetFromCompressionHeaderStart(int byteOffsetFromCompressionHeaderStart) {
        this.byteOffsetFromCompressionHeaderStart = byteOffsetFromCompressionHeaderStart;
    }

    /**
     * The Slice's Container's offset in bytes from the beginning of the stream
     * equal to {@link Container#getContainerByteOffset()}
     *
     * Used by BAI and CRAI indexing
     */
    public long getContainerByteOffset() {
        return containerByteOffset;
    }

    public void setContainerByteOffset(long containerByteOffset) {
        this.containerByteOffset = containerByteOffset;
    }

    /**
     * The Slice's size in bytes
     *
     * Used by CRAI indexing only
     */
    public int getByteSize() {
        return byteSize;
    }

    public void setByteSize(int byteSize) {
        this.byteSize = byteSize;
    }

    /**
     * The Slice's index number within its Container
     *
     * Used by BAI indexing only
     */
    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public long getBaseCount() {
        return baseCount;
    }

    public SAMBinaryTagAndValue getSliceTags() {
        return sliceTags;
    }

    public void setSliceTags(SAMBinaryTagAndValue sliceTags) {
        this.sliceTags = sliceTags;
    }

    public int getMappedReadsCount() {
        return mappedReadsCount;
    }

    public void setMappedReadsCount(int mappedReadsCount) {
        this.mappedReadsCount = mappedReadsCount;
    }

    public int getUnmappedReadsCount() {
        return unmappedReadsCount;
    }

    public void setUnmappedReadsCount(int unmappedReadsCount) {
        this.unmappedReadsCount = unmappedReadsCount;
    }

    public int getUnplacedReadsCount() {
        return unplacedReadsCount;
    }

    public void setUnplacedReadsCount(int unplacedReadsCount) {
        this.unplacedReadsCount = unplacedReadsCount;
    }

    public void setReferenceMD5(final byte[] ref) {
        refMD5 = ref;
    }
    public void setEmbeddedReferenceContentID(final int embeddedRefContentID) { sliceBlocks.setEmbeddedReferenceContentID(embeddedRefContentID); }
    public int getEmbeddedReferenceContentID() { return sliceBlocks.getEmbeddedReferenceContentID(); }

    // Unused because embedded reference isn't implemented for write
    public Block getEmbeddedReferenceBlock() { return sliceBlocks.getEmbeddedReferenceBlock(); }

    public CompressionHeader getCompressionHeader() {
        if (compressionHeader == null) {
            //TODO: do we need this guard....
            // temporary guard until the Slice(refContext) constructor is expunged
            throw new IllegalStateException("null compression header");
        }
        return compressionHeader;
    }

    public ArrayList<CRAMRecord> getRecords(final CompressorCache compressorCache, final ValidationStringency validationStringency) {
        final CramRecordReader reader = new CramRecordReader(this, compressorCache, validationStringency);

        final ArrayList<CRAMRecord> records = new ArrayList<>(nofRecords);

        int prevAlignmentStart = alignmentStart;
        for (int i = 0; i < nofRecords; i++) {
            // read the new record and update the running prevAlignmentStart
            final CRAMRecord cramRecord = reader.read(index, i, prevAlignmentStart);
            prevAlignmentStart = cramRecord.getAlignmentStart();

            records.add(cramRecord);
        }

        return records;
    }

    public void write(final int major, final OutputStream outputStream) {
        // TODO: ensure that the Slice blockCount stays in sync with the
        // Container's blockCount in Container.writeContainer()

        // Each Slice has 1 core data block, plus zero or more external data blocks.
        // Since an embedded reference block is just stored as an external block, it is included in
        // the external block count, and does not need to be counted separately.
        setNofBlocks(1 + getSliceBlocks().getNumberOfExternalBlocks());
        setContentIDs(new int[getSliceBlocks().getNumberOfExternalBlocks()]);
        final int i = 0;
        for (final int id : getSliceBlocks().getExternalContentIDs()) {
            getContentIDs()[i] = id;
        }
        // establish our header block before writing
        headerBlock = Block.createRawSliceHeaderBlock(createSliceHeaderBlockContent(major, this));
        headerBlock.write(major, outputStream);
        // writes the core, and external blocks
        getSliceBlocks().writeBlocks(major, outputStream);
    }

    private static byte[] createSliceHeaderBlockContent(final int major, final Slice slice) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ITF8.writeUnsignedITF8(slice.getReferenceContext().getSerializableId(), byteArrayOutputStream);
        ITF8.writeUnsignedITF8(slice.getAlignmentStart(), byteArrayOutputStream);
        ITF8.writeUnsignedITF8(slice.getAlignmentSpan(), byteArrayOutputStream);
        ITF8.writeUnsignedITF8(slice.getNofRecords(), byteArrayOutputStream);
        LTF8.writeUnsignedLTF8(slice.getGlobalRecordCounter(), byteArrayOutputStream);
        ITF8.writeUnsignedITF8(slice.getNofBlocks(), byteArrayOutputStream);

        slice.setContentIDs(new int[slice.getSliceBlocks().getNumberOfExternalBlocks()]);
        int i = 0;
        for (final int id : slice.getSliceBlocks().getExternalContentIDs()) {
            slice.getContentIDs()[i++] = id;
        }
        CramIntArray.write(slice.getContentIDs(), byteArrayOutputStream);
        ITF8.writeUnsignedITF8(slice.getEmbeddedReferenceContentID(), byteArrayOutputStream);
        try {
            byteArrayOutputStream.write(slice.getRefMD5() == null ? new byte[16] : slice.getRefMD5());
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
    public void baiIndexInitializationCheck() {
        final StringBuilder error = new StringBuilder();

        if (byteOffsetFromCompressionHeaderStart == UNINITIALIZED_INDEXING_PARAMETER) {
            error.append("Cannot index this Slice for BAI because its byteOffsetFromCompressionHeaderStart is unknown.").append(System.lineSeparator());
        }

        if (containerByteOffset == UNINITIALIZED_INDEXING_PARAMETER) {
            error.append("Cannot index this Slice for BAI because its containerByteOffset is unknown.").append(System.lineSeparator());
        }

        if (index == UNINITIALIZED_INDEXING_PARAMETER) {
            error.append("Cannot index this Slice for BAI because its index is unknown.").append(System.lineSeparator());
        }

        if (error.length() > 0) {
            throw new CRAMException(error.toString());
        }
    }

    /**
     * Confirm that we have initialized the 3 CRAI index parameters:
     * byteOffsetFromCompressionHeaderStart, containerByteOffset, and byteSize
     *
     * NOTE: this is currently unused because we always use BAI
     */
    void craiIndexInitializationCheck() {
        final StringBuilder error = new StringBuilder();

        if (byteOffsetFromCompressionHeaderStart == UNINITIALIZED_INDEXING_PARAMETER) {
            error.append("Cannot index this Slice for CRAI because its byteOffsetFromCompressionHeaderStart is unknown.").append(System.lineSeparator());
        }

        if (containerByteOffset == UNINITIALIZED_INDEXING_PARAMETER) {
            error.append("Cannot index this Slice for CRAI because its containerByteOffset is unknown.").append(System.lineSeparator());
        }

        if (byteSize == UNINITIALIZED_INDEXING_PARAMETER) {
            error.append("Cannot index this Slice for CRAI because its byteSize is unknown.").append(System.lineSeparator());
        }

        if (error.length() > 0) {
            throw new CRAMException(error.toString());
        }
    }

    private void alignmentBordersSanityCheck(final byte[] ref) {
        if (referenceContext.isUnmappedUnplaced()) {
            return;
        }

        if (alignmentStart > 0 && referenceContext.isMappedSingleRef() && ref == null) {
            throw new IllegalArgumentException ("Mapped slice reference is null.");
        }

        if (alignmentStart > ref.length) {
            log.error(String.format("Slice mapped outside of reference: seqID=%s, start=%d, counter=%d.",
                    referenceContext, alignmentStart, globalRecordCounter));
            throw new RuntimeException("Slice mapped outside of the reference.");
        }

        //TODO: is it OK to proceed in this case ? why does this not throw ?
        if (alignmentStart - 1 + alignmentSpan > ref.length) {
            log.warn(String.format("Slice partially mapped outside of reference: seqID=%s, start=%d, span=%d, counter=%d.",
                    referenceContext, alignmentStart, alignmentSpan, globalRecordCounter));
        }
    }

    public boolean validateRefMD5(final byte[] ref) {
        if (referenceContext.isMultiRef()) {
            throw new SAMException("Cannot verify a slice with multiple references on a single reference.");
        }

        if (referenceContext.isUnmappedUnplaced()) {
            return true;
        }

        alignmentBordersSanityCheck(ref);

        if (!validateRefMD5(ref, alignmentStart, alignmentSpan, refMD5)) {
            final int shoulderLength = 10;
            final String excerpt = getBrief(alignmentStart, alignmentSpan, ref, shoulderLength);

            if (validateRefMD5(ref, alignmentStart, alignmentSpan - 1, refMD5)) {
                log.warn(String.format("Reference MD5 matches partially for slice %s:%d-%d, %s",
                        referenceContext, alignmentStart,
                        alignmentStart + alignmentSpan - 1, excerpt));
                return true;
            }

            log.error(String.format("Reference MD5 mismatch for slice %s:%d-%d, %s",
                    referenceContext, alignmentStart, alignmentStart +
                    alignmentSpan - 1, excerpt));
            return false;
        }

        return true;
    }

    private static boolean validateRefMD5(final byte[] ref, final int alignmentStart, final int alignmentSpan, final byte[] expectedMD5) {
        final int span = Math.min(alignmentSpan, ref.length - alignmentStart + 1);
        final String md5 = SequenceUtil.calculateMD5String(ref, alignmentStart - 1, span);
        return md5.equals(String.format("%032x", new BigInteger(1, expectedMD5)));
    }

    private static String getBrief(final int startOneBased, final int span, final byte[] bases, final int shoulderLength) {
        if (span >= bases.length)
            return new String(bases);

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
        return String.format("slice: seqID %s, start %d, span %d, records %d.",
                referenceContext, alignmentStart, alignmentSpan, nofRecords);
    }

    // *calculate* the MD5 for this reference
    public void setRefMD5(final byte[] ref) {
        if (referenceContext.isMultiRef()) {
            //TODO: fix this
            //log.warn("Attempt to set MD5 on multiref slice");
            //throw new IllegalArgumentException("Attempt to set MD5 on multiref slice");
        }
        alignmentBordersSanityCheck(ref);

        if (! referenceContext.isMappedSingleRef() && alignmentStart < 1) {
            refMD5 = new byte[16];
            Arrays.fill(refMD5, (byte) 0);

            log.debug("Empty slice ref md5 is set.");
        } else {

            final int span = Math.min(alignmentSpan, ref.length - alignmentStart + 1);

            if (alignmentStart + span > ref.length + 1)
                throw new RuntimeException("Invalid alignment boundaries.");

            refMD5 = SequenceUtil.calculateMD5(ref, alignmentStart - 1, span);

            if (log.isEnabled(Log.LogLevel.DEBUG)) {
                final StringBuilder sb = new StringBuilder();
                final int shoulder = 10;
                if (ref.length <= shoulder * 2)
                    sb.append(new String(ref));
                else {

                    sb.append(getBrief(alignmentStart, alignmentSpan, ref, shoulder));
                }

                log.debug(String.format("Slice md5: %s for %s:%d-%d, %s",
                        String.format("%032x", new BigInteger(1, refMD5)),
                        referenceContext, alignmentStart, alignmentStart + span - 1,
                        sb.toString()));
            }
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
     * Uses a Multiple Reference Slice Alignment Reader to determine the Reference Spans of a Slice.
     * The intended use is for CRAI indexing.
     *
     * @param validationStringency how strict to be when reading CRAM records
     */
    public Map<ReferenceContext, AlignmentSpan> getMultiRefAlignmentSpans(final ValidationStringency validationStringency) {
        if (!compressionHeader.isCoordinateSorted()) {
            throw new IllegalStateException("Can't get multiref alignment spans for non-coordinate sorted inputs");
        }
        final MultiRefSliceAlignmentSpanReader reader = new MultiRefSliceAlignmentSpanReader(
                this,
                validationStringency,
                alignmentStart,
                nofRecords);
        return reader.getReferenceSpans();
    }

    /**
     * Generate a CRAI Index entry from this Slice and other container parameters,
     * splitting Multiple Reference slices into constituent reference sequence entries.
     *
     * @param compressionHeader the enclosing {@link Container}'s CompressionHeader
     * @return a list of CRAI Index Entries derived from this Slice
     */
    public List<CRAIEntry> getCRAIEntries(final CompressionHeader compressionHeader) {
        if (! compressionHeader.isCoordinateSorted()) {
            throw new CRAMException("Cannot construct index if the CRAM is not Coordinate Sorted");
        }

        craiIndexInitializationCheck();

        if (referenceContext.isMultiRef()) {
            final Map<ReferenceContext, AlignmentSpan> spans = getMultiRefAlignmentSpans(ValidationStringency.DEFAULT_STRINGENCY);

            return spans.entrySet().stream()
                    .map(e -> new CRAIEntry(e.getKey().getSerializableId(),
                            e.getValue().getStart(),
                            e.getValue().getSpan(),
                            containerByteOffset,
                            byteOffsetFromCompressionHeaderStart,
                            byteSize))
                    .sorted()
                    .collect(Collectors.toList());
        } else {
            // single ref or unmapped
            final int sequenceId = referenceContext.getSerializableId();
            return Collections.singletonList(new CRAIEntry(
                    sequenceId,
                    alignmentStart,
                    alignmentSpan,
                    containerByteOffset,
                    byteOffsetFromCompressionHeaderStart,
                    byteSize));
        }
    }

}
