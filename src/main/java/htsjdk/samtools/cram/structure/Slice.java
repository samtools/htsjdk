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
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.ref.ReferenceContextType;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.util.SequenceUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CRAM slice is a logical union of blocks into for example alignment slices.
 */
public class Slice {
    public static final int NO_ALIGNMENT_START = -1;
    public static final int NO_ALIGNMENT_SPAN = 0;
    public static final int NO_ALIGNMENT_END = SAMRecord.NO_ALIGNMENT_START; // 0
    private static final Log log = Log.getInstance(Slice.class);

    private final ReferenceContext referenceContext;

    // header values as defined in the specs, in addition to sequenceId from ReferenceContext

    // minimum alignment start of the reads in this Slice
    // uses a 1-based coordinate system
    public int alignmentStart = NO_ALIGNMENT_START;
    public int alignmentSpan = NO_ALIGNMENT_SPAN;
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

    public static final int UNINITIALIZED_INDEXING_PARAMETER = -1;

    // the Slice's offset in bytes from the beginning of its Container
    // equal to Container.landmarks[Slice.index] of its enclosing Container
    // BAI and CRAI
    public int byteOffsetFromContainer = UNINITIALIZED_INDEXING_PARAMETER;
    // this Slice's Container's offset in bytes from the beginning of the stream
    // equal to Container.byteOffset of its enclosing Container
    // BAI and CRAI
    public long containerByteOffset = UNINITIALIZED_INDEXING_PARAMETER;
    // this Slice's size in bytes
    // CRAI only
    public int byteSize = UNINITIALIZED_INDEXING_PARAMETER;
    // this Slice's index number within its Container
    // BAI only
    public int index = UNINITIALIZED_INDEXING_PARAMETER;

    // to pass this to the container:
    public long bases;

    public SAMBinaryTagAndValue sliceTags;

    // read counters per type, for BAMIndexMetaData.recordMetaData()
    //
    // see also AlignmentSpan and CRAMBAIIndexer.processContainer()
    //
    // TODO: redesign/refactor

    public int mappedReadsCount = 0;
    public int unmappedReadsCount = 0;
    public int unplacedReadsCount = 0;

    /**
     * Construct this Slice by providing its {@link ReferenceContext}
     * @param refContext the reference context associated with this slice
     */
    public Slice(final ReferenceContext refContext) {
        this.referenceContext = refContext;
    }

    public ReferenceContext getReferenceContext() {
        return referenceContext;
    }

    /**
     * Confirm that we have initialized the 3 BAI index parameters:
     * byteOffsetFromContainer, containerByteOffset, and index
     */
    public void baiIndexInitializationCheck() {
        final StringBuilder error = new StringBuilder();

        if (byteOffsetFromContainer == UNINITIALIZED_INDEXING_PARAMETER) {
            error.append("Cannot index this Slice for BAI because its byteOffsetFromContainer is unknown.").append(System.lineSeparator());
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
     * byteOffsetFromContainer, containerByteOffset, and byteSize
     *
     * NOTE: this is currently unused because we always use BAI
     */
    void craiIndexInitializationCheck() {
        final StringBuilder error = new StringBuilder();

        if (byteOffsetFromContainer == UNINITIALIZED_INDEXING_PARAMETER) {
            error.append("Cannot index this Slice for CRAI because its byteOffsetFromContainer is unknown.").append(System.lineSeparator());
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

    public void setRefMD5(final byte[] ref) {
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
                referenceContext,
                validationStringency);
    }

    /**
     * Uses a Multiple Reference Slice Alignment Reader to determine the Reference Spans of a Slice.
     * The intended use is for CRAI indexing.
     *
     * @param header               the associated Cram Compression Header
     * @param validationStringency how strict to be when reading CRAM records
     */
    public Map<ReferenceContext, AlignmentSpan> getMultiRefAlignmentSpans(final CompressionHeader header,
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
            final Map<ReferenceContext, AlignmentSpan> spans = getMultiRefAlignmentSpans(compressionHeader, ValidationStringency.DEFAULT_STRINGENCY);

            return spans.entrySet().stream()
                    .map(e -> new CRAIEntry(e.getKey().getSerializableId(),
                            e.getValue().getStart(),
                            e.getValue().getSpan(),
                            containerByteOffset,
                            byteOffsetFromContainer,
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
                    byteOffsetFromContainer,
                    byteSize));
        }
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
        final Slice slice = initializeFromRecords(records);

        final Map<Integer, ByteArrayOutputStream> externalBlockMap = new HashMap<>();
        for (final int id : header.externalIds) {
            externalBlockMap.put(id, new ByteArrayOutputStream());
        }

        try (final ByteArrayOutputStream bitBAOS = new ByteArrayOutputStream();
             final DefaultBitOutputStream bitOutputStream = new DefaultBitOutputStream(bitBAOS)) {

            final CramRecordWriter writer = new CramRecordWriter(bitOutputStream, externalBlockMap, header, slice.referenceContext);
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

    /**
     * Using a collection of {@link CramCompressionRecord}s,
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
     * @see CramCompressionRecord#isPlaced()
     *
     * @see ReferenceContextType
     * @param records the input records
     * @return the initialized Slice
     */
    private static Slice initializeFromRecords(final Collection<CramCompressionRecord> records) {
        final ContentDigests hasher = ContentDigests.create(ContentDigests.ALL);
        final Set<ReferenceContext> referenceContexts = new HashSet<>();
        // ignore these values if we later determine this Slice is not single-ref
        int singleRefAlignmentStart = Integer.MAX_VALUE;
        int singleRefAlignmentEnd = SAMRecord.NO_ALIGNMENT_START;

        int baseCount = 0;
        int mappedReadsCount = 0;
        int unmappedReadsCount = 0;
        int unplacedReadsCount = 0;

        for (final CramCompressionRecord record : records) {
            hasher.add(record);
            baseCount += record.readLength;

            if (record.isPlaced()) {
                referenceContexts.add(new ReferenceContext(record.sequenceId));
                singleRefAlignmentStart = Math.min(record.alignmentStart, singleRefAlignmentStart);
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

            if (record.alignmentStart == SAMRecord.NO_ALIGNMENT_START) {
                unplacedReadsCount++;
            }
        }

        ReferenceContext sliceRefContext;
        switch (referenceContexts.size()) {
            case 0:
                sliceRefContext = ReferenceContext.UNMAPPED_UNPLACED_CONTEXT;
                break;
            case 1:
                // SINGLE_REFERENCE_TYPE context: all reads placed on the same reference
                // or UNMAPPED_UNPLACED_CONTEXT: all reads unplaced
                sliceRefContext = referenceContexts.iterator().next();
                break;
            default:
                // placed reads on multiple references and/or a combination of placed and unplaced reads
                sliceRefContext = ReferenceContext.MULTIPLE_REFERENCE_CONTEXT;
        }

        final Slice slice = new Slice(sliceRefContext);
        if (sliceRefContext.isMappedSingleRef()) {
            slice.alignmentStart = singleRefAlignmentStart;
            slice.alignmentSpan = singleRefAlignmentEnd - singleRefAlignmentStart + 1;
        }

        slice.sliceTags = hasher.getAsTags();
        slice.bases = baseCount;
        slice.nofRecords = records.size();
        slice.mappedReadsCount = mappedReadsCount;
        slice.unmappedReadsCount = unmappedReadsCount;
        slice.unplacedReadsCount = unplacedReadsCount;

        return slice;
    }
}
