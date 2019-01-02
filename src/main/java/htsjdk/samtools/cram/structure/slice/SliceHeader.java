package htsjdk.samtools.cram.structure.slice;

import htsjdk.samtools.*;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.io.CramIntArray;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.io.LTF8;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.structure.block.BlockContentType;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.util.SequenceUtil;

import java.io.*;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * Header values associated with a {@link Slice}
 */
public class SliceHeader {
    private static final Log log = Log.getInstance(SliceHeader.class);

    public static final int REFERENCE_INDEX_NOT_INITIALIZED = -3;
    public static final int REFERENCE_INDEX_MULTI = -2;
    public static final int REFERENCE_INDEX_NONE = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;  // -1

    public static final int NO_ALIGNMENT_START = -1;
    public static final int NO_ALIGNMENT_SPAN = 0;
    public static final int NO_EMBEDDED_REFERENCE = -1;
    public static final int MD5_LEN = 16;
    public static final byte[] NO_MD5 = new byte[MD5_LEN];

    private final int sequenceId;
    private final int alignmentStart;
    private final int alignmentSpan;
    private final int recordCount;
    private final long globalRecordCounter;
    private final int dataBlockCount;
    private final int[] contentIDs;
    private final int embeddedRefBlockContentID;
    private final byte[] refMD5;
    private final SAMBinaryTagAndValue tags;

    /**
     * Construct a Slice Header
     *
     * @param sequenceId the reference sequence ID of this Slice, or REFERENCE_INDEX_MULTI or REFERENCE_INDEX_NONE
     * @param alignmentStart the alignment start position, or NO_ALIGNMENT_START
     * @param alignmentSpan the length of the alignment, or NO_ALIGNMENT_SPAN
     * @param recordCount the number of records in this Slice
     * @param globalRecordCounter the number of records in the stream seen up to this point
     * @param dataBlockCount the number of data blocks in this Slice (Core and External)
     * @param contentIDs an array of the External Block Content IDs of this Slice
     * @param embeddedRefBlockContentID the Content ID of the Embedded Reference, or NO_EMBEDDED_REFERENCE
     * @param refMD5 the MD5 checksum of the reference bases within the Slice boundaries, or NO_MD5
     * @param tags an optional series of BAM tags, in the {@link SAMBinaryTagAndValue format}
     */
    public SliceHeader(final int sequenceId,
                       final int alignmentStart,
                       final int alignmentSpan,
                       final int recordCount,
                       final long globalRecordCounter,
                       final int dataBlockCount,
                       final int[] contentIDs,
                       final int embeddedRefBlockContentID,
                       final byte[] refMD5,
                       final SAMBinaryTagAndValue tags) {
        this.sequenceId = sequenceId;
        this.alignmentStart = alignmentStart;
        this.alignmentSpan = alignmentSpan;
        this.recordCount = recordCount;
        this.globalRecordCounter = globalRecordCounter;
        this.dataBlockCount = dataBlockCount;
        this.contentIDs = contentIDs;
        this.embeddedRefBlockContentID = embeddedRefBlockContentID;
        this.refMD5 = refMD5;
        this.tags = tags;
    }

    /**
     * Read a MAPPED_SLICE Block from an InputStream and return its contents as a SliceHeader
     * We do this instead of reading the InputStream directly because the Block content may be compressed
     *
     * @param major CRAM version major number
     * @param blockStream input stream to read the slice header from
     * @return a {@link SliceHeader} object with fields and content from the input stream
     */
    static SliceHeader read(final int major, final InputStream blockStream) {
        final Block headerBlock = Block.read(major, blockStream);
        if (headerBlock.getContentType() != BlockContentType.MAPPED_SLICE)
            throw new RuntimeIOException("Slice Header Block expected, found: " + headerBlock.getContentType().name());

        try (final InputStream parseInputStream = new ByteArrayInputStream(headerBlock.getUncompressedContent())) {
            return internalRead(major, parseInputStream);
        }
        catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    private static SliceHeader internalRead(int major, InputStream inputStream) throws IOException {
        final int sequenceId = ITF8.readUnsignedITF8(inputStream);
        final int alignmentStart = ITF8.readUnsignedITF8(inputStream);
        final int alignmentSpan = ITF8.readUnsignedITF8(inputStream);
        final int recordCount = ITF8.readUnsignedITF8(inputStream);
        final long globalRecordCounter = LTF8.readUnsignedLTF8(inputStream);
        final int blockCount = ITF8.readUnsignedITF8(inputStream);
        final int[] contentIDs = CramIntArray.array(inputStream);
        final int embeddedRefBlockContentID = ITF8.readUnsignedITF8(inputStream);

        final byte[] referenceMD5s = new byte[MD5_LEN];
        InputStreamUtils.readFully(inputStream, referenceMD5s, 0, MD5_LEN);

        final byte[] tagBytes = InputStreamUtils.readFully(inputStream);
        final SAMBinaryTagAndValue tags = (major >= CramVersions.CRAM_v3.major) ?
                BinaryTagCodec.readTags(tagBytes, 0, tagBytes.length, ValidationStringency.DEFAULT_STRINGENCY) :
                null;

        return new SliceHeader(sequenceId, alignmentStart, alignmentSpan, recordCount, globalRecordCounter,
                blockCount, contentIDs, embeddedRefBlockContentID, referenceMD5s, tags);
    }

    /**
     * Write the SliceHeader out to the the specified {@link OutputStream}.
     * The method is parameterized with the CRAM major version number.
     *
     * @param major CRAM version major number
     * @param blockStream output stream to write to
     */
    void write(final int major, final OutputStream blockStream) {
        try {
            final Block sliceHeaderBlock = Block.createRawSliceHeaderBlock(internalWrite(major));
            sliceHeaderBlock.write(major, blockStream);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    private byte[] internalWrite(final int major) throws IOException {
        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ITF8.writeUnsignedITF8(sequenceId, outputStream);
            ITF8.writeUnsignedITF8(alignmentStart, outputStream);
            ITF8.writeUnsignedITF8(alignmentSpan, outputStream);
            ITF8.writeUnsignedITF8(recordCount, outputStream);
            LTF8.writeUnsignedLTF8(globalRecordCounter, outputStream);
            ITF8.writeUnsignedITF8(dataBlockCount, outputStream);
            CramIntArray.write(contentIDs, outputStream);

            ITF8.writeUnsignedITF8(embeddedRefBlockContentID, outputStream);
            outputStream.write(refMD5 == null ? NO_MD5 : refMD5);

            if (major >= CramVersions.CRAM_v3.major && tags != null) {
                try (final BinaryCodec codec = new BinaryCodec(outputStream)) {
                    final BinaryTagCodec binaryTagCodec = new BinaryTagCodec(codec);
                    SAMBinaryTagAndValue samBinaryTagAndValue = tags;
                    do {
                        log.debug("Writing slice tag: " + SAMTag.makeStringTag(samBinaryTagAndValue.tag));
                        binaryTagCodec.writeTag(samBinaryTagAndValue.tag, samBinaryTagAndValue.value, samBinaryTagAndValue.isUnsignedArray());
                    } while ((samBinaryTagAndValue = samBinaryTagAndValue.getNext()) != null);
                }
            }

            return outputStream.toByteArray();
        }
    }

    /**
     * Calculate the reference MD5 in the given alignment span
     *
     * @param refBases the reference, in byte array format
     * @param sequenceId the reference sequence ID of this Slice, or REFERENCE_INDEX_MULTI or REFERENCE_INDEX_NONE
     * @param alignmentStart the alignment start position, or NO_ALIGNMENT_START
     * @param alignmentSpan the length of the alignment, or NO_ALIGNMENT_SPAN
     * @param globalRecordCounter the counter of records seen so far
     * @return
     */
    public static byte[] calculateRefMD5(final byte[] refBases,
                                         final int sequenceId,
                                         final int alignmentStart,
                                         final int alignmentSpan,
                                         final long globalRecordCounter) {
        if (refBases == null) {
            return NO_MD5;
        }

        alignmentBordersSanityCheck(refBases, sequenceId, alignmentStart, alignmentSpan, globalRecordCounter);

        if (!hasSingleReference(sequenceId) && alignmentStart == NO_ALIGNMENT_START) {
            log.debug("Empty slice ref md5 is set.");
            return NO_MD5;
        } else {
            final int span = Math.min(alignmentSpan, refBases.length - alignmentStart + 1);

            if (alignmentStart + span > refBases.length + 1)
                throw new RuntimeException("Invalid alignment boundaries.");

            return SequenceUtil.calculateMD5(refBases, alignmentStart - 1, span);
        }
    }

    /**
     * Validate the MD5 of this Slice's single reference.
     * Always returns true of REFERENCE_INDEX_NONE
     *
     * @param refBases the reference, in byte array format
     * @throws CRAMException if this slice has multiple references
     */
    public void validateRefMD5(final byte[] refBases) {
        if (hasMultipleReferences()) {
            throw new CRAMException("Cannot verify a slice with multiple references on a single reference.");
        }

        if (hasNoReference()) {
            return;
        }

        alignmentBordersSanityCheck(refBases, sequenceId, alignmentStart, alignmentSpan, globalRecordCounter);

        if (!validateRefMD5(refBases, alignmentSpan)) {
            // try again with a shorter span
            if (validateRefMD5(refBases, alignmentSpan - 1)) {
                final String excerpt = getBrief(refBases);
                log.warn(String.format("Reference MD5 matches partially for slice %d:%d-%d, %s", sequenceId, alignmentStart,
                        alignmentStart + alignmentSpan - 1, excerpt));
                return;
            }

            final String msg = String.format(
                    "Reference sequence MD5 mismatch for slice: sequence id %d, start %d, span %d, expected MD5 %s",
                    getSequenceId(),
                    getAlignmentStart(),
                    getAlignmentSpan(),
                    String.format("%032x", new BigInteger(1, getRefMD5())));
            log.error(msg);
            throw new CRAMException(msg);
        }
    }

    private boolean validateRefMD5(final byte[] refBases, final int checkSpan) {
        final int span = Math.min(checkSpan, refBases.length - alignmentStart + 1);
        final String md5 = SequenceUtil.calculateMD5String(refBases, alignmentStart - 1, span);
        return md5.equals(String.format("%032x", new BigInteger(1, refMD5)));
    }

    private static void alignmentBordersSanityCheck(final byte[] refBases,
                                                    final int sequenceId,
                                                    final int alignmentStart,
                                                    final int alignmentSpan,
                                                    final long globalRecordCounter) {
        if (sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            return;
        }

        if (alignmentStart > NO_ALIGNMENT_START && hasSingleReference(sequenceId) && refBases == null) {
            throw new IllegalArgumentException("Mapped slice reference is null.");
        }

        if (alignmentStart > refBases.length) {
            log.error(String.format("Slice mapped outside of reference: seqID=%d, start=%d, counter=%d.",
                    sequenceId, alignmentStart, globalRecordCounter));
            throw new RuntimeException("Slice mapped outside of the reference.");
        }

        if (alignmentStart - 1 + alignmentSpan > refBases.length) {
            log.warn(String.format("Slice partially mapped outside of reference: seqID=%d, start=%d, span=%d, counter=%d.",
                    sequenceId, alignmentStart, alignmentSpan, globalRecordCounter));
        }
    }

    private String getBrief(final byte[] bases) {
        if (alignmentSpan >= bases.length)
            return new String(bases);

        final StringBuilder sb = new StringBuilder();
        final int fromInc = alignmentStart - 1;

        int toExc = alignmentStart + alignmentSpan - 1;
        toExc = Math.min(toExc, bases.length);

        final int shoulderLength = 10;
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
        return String.format("slice: seqID %d, start %d, span %d, records %d.", sequenceId, alignmentStart, alignmentSpan, recordCount);
    }

    public int getSequenceId() {
        return sequenceId;
    }

    public int getAlignmentStart() {
        return alignmentStart;
    }

    public int getAlignmentSpan() {
        return alignmentSpan;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public int getDataBlockCount() {
        return dataBlockCount;
    }

    public int getEmbeddedRefBlockContentID() {
        return embeddedRefBlockContentID;
    }

    public byte[] getRefMD5() {
        return refMD5;
    }

    public static boolean hasSingleReference(final int sequenceId) {
        return sequenceId != REFERENCE_INDEX_NONE && sequenceId != REFERENCE_INDEX_MULTI;
    }

    public boolean hasSingleReference() {
        return hasSingleReference(getSequenceId());
    }

    public boolean hasNoReference() {
        return getSequenceId() == REFERENCE_INDEX_NONE;
    }

    public boolean hasMultipleReferences() {
        return getSequenceId() == REFERENCE_INDEX_MULTI;
    }

    public boolean hasEmbeddedRefBlock() {
        return getEmbeddedRefBlockContentID() != NO_EMBEDDED_REFERENCE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SliceHeader header = (SliceHeader) o;

        if (sequenceId != header.sequenceId) return false;
        if (alignmentStart != header.alignmentStart) return false;
        if (alignmentSpan != header.alignmentSpan) return false;
        if (recordCount != header.recordCount) return false;
        if (globalRecordCounter != header.globalRecordCounter) return false;
        if (dataBlockCount != header.dataBlockCount) return false;
        if (embeddedRefBlockContentID != header.embeddedRefBlockContentID) return false;
        if (!Arrays.equals(contentIDs, header.contentIDs)) return false;
        if (!Arrays.equals(refMD5, header.refMD5)) return false;
        return tags != null ? tags.equals(header.tags) : header.tags == null;
    }

    @Override
    public int hashCode() {
        int result = sequenceId;
        result = 31 * result + alignmentStart;
        result = 31 * result + alignmentSpan;
        result = 31 * result + recordCount;
        result = 31 * result + (int) (globalRecordCounter ^ (globalRecordCounter >>> 32));
        result = 31 * result + dataBlockCount;
        result = 31 * result + Arrays.hashCode(contentIDs);
        result = 31 * result + embeddedRefBlockContentID;
        result = 31 * result + Arrays.hashCode(refMD5);
        result = 31 * result + (tags != null ? tags.hashCode() : 0);
        return result;
    }
}
