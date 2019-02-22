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
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.digest.ContentDigests;
import htsjdk.samtools.cram.encoding.reader.CramRecordReader;
import htsjdk.samtools.cram.encoding.reader.MultiRefSliceAlignmentSpanReader;
import htsjdk.samtools.cram.encoding.writer.CramRecordWriter;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.DefaultBitInputStream;
import htsjdk.samtools.cram.io.DefaultBitOutputStream;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.util.SequenceUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CRAM slice is a logical union of blocks into for example alignment slices.
 */
public class Slice {
    public static final int MULTI_REFERENCE = -2;
    public static final int NO_ALIGNMENT_START = -1;
    public static final int NO_ALIGNMENT_SPAN = 0;
    public static final int NO_ALIGNMENT_END = SAMRecord.NO_ALIGNMENT_START; // 0
    private static final Log log = Log.getInstance(Slice.class);

    // as defined in the specs:
    public int sequenceId = -1;
    public int alignmentStart = -1;
    public int alignmentSpan = -1;
    public int nofRecords = -1;
    public long globalRecordCounter = -1;
    public int nofBlocks = -1;
    public int[] contentIDs;
    public int embeddedRefBlockContentID = -1;
    public byte[] refMD5 = new byte[16];

    // content associated with ids:
    public Block headerBlock;
    public Block coreBlock;
    public Block embeddedRefBlock;
    public Map<Integer, Block> external;

    // for indexing purposes

    // the Slice's offset in bytes from the beginning of its Container
    // equal to Container.landmarks[Slice.index] of its enclosing Container
    public int offset = -1;
    // this Slice's Container's offset in bytes from the beginning of the stream
    // equal to Container.offset of its enclosing Container
    public long containerOffset = -1;
    // this Slice's size in bytes
    public int size = -1;
    // this Slice's index within its Container
    public int index = -1;

    // to pass this to the container:
    public long bases;

    public SAMBinaryTagAndValue sliceTags;

    private void alignmentBordersSanityCheck(final byte[] ref) {
        if (sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) return ;
        if (alignmentStart > 0 && sequenceId >= 0 && ref == null) throw new IllegalArgumentException ("Mapped slice reference is null.");

        if (alignmentStart > ref.length) {
            log.error(String.format("Slice mapped outside of reference: seqID=%d, start=%d, counter=%d.", sequenceId, alignmentStart,
                    globalRecordCounter));
            throw new RuntimeException("Slice mapped outside of the reference.");
        }

        if (alignmentStart - 1 + alignmentSpan > ref.length) {
            log.warn(String.format("Slice partially mapped outside of reference: seqID=%d, start=%d, span=%d, counter=%d.",
                    sequenceId, alignmentStart, alignmentSpan, globalRecordCounter));
        }
    }

    public boolean validateRefMD5(final byte[] ref) {
        if(sequenceId == Slice.MULTI_REFERENCE)
            throw new SAMException("Cannot verify a slice with multiple references on a single reference.");

        if (sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) return true;

        alignmentBordersSanityCheck(ref);

        if (!validateRefMD5(ref, alignmentStart, alignmentSpan, refMD5)) {
            final int shoulderLength = 10;
            final String excerpt = getBrief(alignmentStart, alignmentSpan, ref, shoulderLength);

            if (validateRefMD5(ref, alignmentStart, alignmentSpan - 1, refMD5)) {
                log.warn(String.format("Reference MD5 matches partially for slice %d:%d-%d, %s", sequenceId, alignmentStart,
                        alignmentStart + alignmentSpan - 1, excerpt));
                return true;
            }

            log.error(String.format("Reference MD5 mismatch for slice %d:%d-%d, %s", sequenceId, alignmentStart, alignmentStart +
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
        return String.format("slice: seqID %d, start %d, span %d, records %d.", sequenceId, alignmentStart, alignmentSpan, nofRecords);
    }

    public void setRefMD5(final byte[] ref) {
        alignmentBordersSanityCheck(ref);

        if (sequenceId < 0 && alignmentStart < 1) {
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

                log.debug(String.format("Slice md5: %s for %d:%d-%d, %s",
                        String.format("%032x", new BigInteger(1, refMD5)),
                        sequenceId, alignmentStart, alignmentStart + span - 1,
                        sb.toString()));
            }
        }
    }

    /**
     * Hijacking attributes-related methods from SAMRecord:
     */

    /**
     * Get tag value attached to the slice.
     * @param tag tag ID as a short integer as returned by {@link SAMTag#makeBinaryTag(String)}
     * @return a value of the tag
     */
    public Object getAttribute(final short tag) {
        if (this.sliceTags == null) return null;
        else {
            final SAMBinaryTagAndValue tmp = this.sliceTags.find(tag);
            if (tmp != null) return tmp.value;
            else return null;
        }
    }

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

    public void setUnsignedArrayAttribute(final String tag, final Object value) {
        if (!value.getClass().isArray()) {
            throw new IllegalArgumentException("Non-array passed to setUnsignedArrayAttribute for tag " + tag);
        }
        if (Array.getLength(value) == 0) {
            throw new IllegalArgumentException("Empty array passed to setUnsignedArrayAttribute for tag " + tag);
        }
        setAttribute(SAMTag.makeBinaryTag(tag), value, true);
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
     * Does this Slice contain only unplaced reads (whether their unmapped flags are set or not)?
     * @return true if the Slice is designated as Unmapped
     */
    public boolean isUnmapped() {
        return sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
    }

    /**
     * Does this Slice contain either:
     * - reads placed on multiple references
     * - or a combination of placed and unplaced reads?
     * @return true if the Slice contains reads places on multiple references or are a combination of placed and unplaced
     */
    public boolean isMultiref() {
        return sequenceId == Slice.MULTI_REFERENCE;
    }

    /**
     * Does this Slice contain reads placed on a single reference (whether their unmapped flags are set or not)?
     * @return true if all reads in the Slice are placed on a single reference
     */
    public boolean isMappedSingleRef() {
        return ! isUnmapped() && ! isMultiref();
    }

    private BitInputStream getCoreBlockInputStream() {
        return new DefaultBitInputStream(new ByteArrayInputStream(coreBlock.getUncompressedContent()));
    }

    private Map<Integer, ByteArrayInputStream> getExternalBlockInputMap() {
        return external.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new ByteArrayInputStream(e.getValue().getUncompressedContent())));
    }

    /**
     * Initialize a Cram Record Reader from a Slice
     *
     * @param header the associated Cram Compression Header
     * @param validationStringency how strict to be when reading this CRAM record
     */
    public CramRecordReader createCramRecordReader(final CompressionHeader header,
                                                   final ValidationStringency validationStringency) {
        return new CramRecordReader(getCoreBlockInputStream(),
                getExternalBlockInputMap(),
                header,
                sequenceId,
                validationStringency);
    }

    /**
     * Uses a Multiple Reference Slice Alignment Reader to determine the Reference Spans of a Slice.
     * The intended use is for CRAI indexing.
     *
     * @param header               the associated Cram Compression Header
     * @param validationStringency how strict to be when reading CRAM records
     */
    public Map<Integer, AlignmentSpan> getMultiRefAlignmentSpans(final CompressionHeader header,
                                                                 final ValidationStringency validationStringency) {
        final MultiRefSliceAlignmentSpanReader reader = new MultiRefSliceAlignmentSpanReader(getCoreBlockInputStream(),
                getExternalBlockInputMap(),
                header,
                validationStringency,
                alignmentStart,
                nofRecords);
        return reader.getReferenceSpans();
    }

    /**
     * Generate a CRAI Index entry from this Slice
     * @return a new CRAI Index Entry
     */
    public CRAIEntry getCRAIEntry() {
        return new CRAIEntry(sequenceId, alignmentStart, alignmentSpan, containerOffset, offset, size);
    }
    /**
     * Generate a CRAI Index entry from this Slice and the container offset.
     *
     * TODO: investigate why we sometimes need to pass in an external containerStartOffset
     * because this Slice's containerOffset is incorrect
     *
     * @param containerStartOffset the byte offset of this Slice's Container
     * @return a new CRAI Index Entry
     */
    public CRAIEntry getCRAIEntry(final long containerStartOffset) {
        return new CRAIEntry(sequenceId, alignmentStart, alignmentSpan, containerStartOffset, offset, size);
    }

    /**
     * Create a single Slice from CRAM Compression Records and a Compression Header.
     * The caller is responsible for appropriate subdivision of records into
     * containers and slices.
     *
     * @param records input CRAM Compression Records
     * @param header the enclosing {@link Container}'s Compression Header
     * @return a Slice corresponding to the given records
     */
    public static Slice buildSlice(final List<CramCompressionRecord> records,
                                   final CompressionHeader header) {
        final Map<Integer, ByteArrayOutputStream> externalBlockMap = new HashMap<>();
        for (final int id : header.externalIds) {
            externalBlockMap.put(id, new ByteArrayOutputStream());
        }

        final Slice slice = new Slice();
        slice.nofRecords = records.size();

        int minAlStart = Integer.MAX_VALUE;
        int maxAlEnd = SAMRecord.NO_ALIGNMENT_START;
        {
            // @formatter:off
            /*
             * 1) Count slice bases.
             * 2) Decide if the slice is single ref, unmapped or multi reference.
             * 3) Detect alignment boundaries for the slice if not multi reference.
             */
            // @formatter:on

            // Valid Slice states, by individual record contents:
            //
            // Single Reference: all records have valid placements/alignments on the same reference sequence
            //      * records can be unmapped-but-placed
            //      * reference can be external or embedded
            // Multiple Reference: records may be placed or not, and may have differing reference sequences
            //      * reference must not be embedded (not checked here)
            // Unmapped: all records are unmapped and unplaced

            final CramCompressionRecord firstRecord = records.get(0);
            slice.sequenceId = firstRecord.isPlaced() ?
                    firstRecord.sequenceId :
                    SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;

            final ContentDigests hasher = ContentDigests.create(ContentDigests.ALL);
            for (final CramCompressionRecord record : records) {
                slice.bases += record.readLength;
                hasher.add(record);

                // flip an unmapped slice to multi-ref if the record is placed
                if (slice.isUnmapped() && record.isPlaced()) {
                    slice.sequenceId = MULTI_REFERENCE;
                }
                else if (slice.isMappedSingleRef()) {
                    // flip a single-ref slice to multi-ref if the record is unplaced or on a different ref
                    if (!record.isPlaced() || slice.sequenceId != record.sequenceId) {
                        slice.sequenceId = MULTI_REFERENCE;
                    }
                    else {
                        // calculate single ref slice alignment

                        minAlStart = Math.min(record.alignmentStart, minAlStart);
                        maxAlEnd = Math.max(record.getAlignmentEnd(), maxAlEnd);
                    }
                }
            }

            slice.sliceTags = hasher.getAsTags();
        }

        if (slice.isMappedSingleRef()) {
            slice.alignmentStart = minAlStart;
            slice.alignmentSpan = maxAlEnd - minAlStart + 1;
        }
        else {
            slice.alignmentStart = NO_ALIGNMENT_START;
            slice.alignmentSpan = NO_ALIGNMENT_SPAN;
        }

        try (final ByteArrayOutputStream bitBAOS = new ByteArrayOutputStream();
             final DefaultBitOutputStream bitOutputStream = new DefaultBitOutputStream(bitBAOS)) {

            final CramRecordWriter writer = new CramRecordWriter(bitOutputStream, externalBlockMap, header, slice.sequenceId);
            writer.writeCramCompressionRecords(records, slice.alignmentStart);

            bitOutputStream.close();
            slice.coreBlock = Block.createRawCoreDataBlock(bitBAOS.toByteArray());
        }
        catch (final IOException e) {
            throw new RuntimeIOException(e);
        }

        slice.external = new HashMap<>();
        for (final Integer contentId : externalBlockMap.keySet()) {
            // remove after https://github.com/samtools/htsjdk/issues/1232
            if (contentId == Block.NO_CONTENT_ID) {
                throw new CRAMException("Valid Content ID required.  Given: " + contentId);
            }

            final ExternalCompressor compressor = header.externalCompressors.get(contentId);
            final byte[] rawContent = externalBlockMap.get(contentId).toByteArray();
            final Block externalBlock = Block.createExternalBlock(compressor.getMethod(), contentId,
                    compressor.compress(rawContent), rawContent.length);

            slice.external.put(contentId, externalBlock);
        }

        return slice;
    }

}
