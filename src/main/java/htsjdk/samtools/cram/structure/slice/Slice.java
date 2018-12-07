package htsjdk.samtools.cram.structure.slice;

import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.encoding.reader.CramRecordReader;
import htsjdk.samtools.cram.encoding.reader.MultiRefSliceAlignmentSpanReader;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.DefaultBitInputStream;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.block.Block;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A Slice is a logical grouping of blocks inside a container.
 *
 * Each Slice has:
 * 1 Slice Header Block
 * 1 Core Data Block
 * N External Data Blocks, indexed by Block Content ID.
 *
 * A full {@link IndexableSlice} can be constructed from this Slice by also supplying BAI indexing metadata.
 *
 */
public class Slice {

    // Immutable Slice Header

    protected final SliceHeader header;

    // data blocks

    private final Block coreBlock;
    private final Map<Integer, Block> externalBlockMap;

    /**
     * Construct a Slice without indexing
     *
     * To construct a full IndexableSlice, we also need indexing metadata
     *
     * @param sliceHeader an immutable data structure representing the header fields of the slice
     * @param coreBlock the Core Data Block associated with the slice
     * @param externalDataBlockMap a mapping of Block Content IDs to External Data Blocks
     */
    public Slice(final SliceHeader sliceHeader,
                 final Block coreBlock,
                 final Map<Integer, Block> externalDataBlockMap) {

        this.header = sliceHeader;
        this.coreBlock = coreBlock;
        this.externalBlockMap = externalDataBlockMap;
    }

    /**
     * Fully construct an {@link IndexableSlice} by specifying indexing metadata
     *
     * @param byteOffset the start byte position in the stream for this Slice
     * @param byteSize the size of this Slice when serialized, in bytes
     * @param sliceIndex the index of this Slice in its Container
     */
    public IndexableSlice withIndexingMetadata(final int byteOffset, final int byteSize, final int sliceIndex) {
        return new IndexableSlice(header, coreBlock, externalBlockMap, byteOffset, byteSize, sliceIndex);
    }

    /**
     * Deserialize the Slice from the {@link InputStream}
     *
     * @param major CRAM version major number
     * @param blockStream input stream to read the slice from
     * @return a Slice object with fields and content from the input stream
     */
    public static Slice read(final int major,
                             final InputStream blockStream) {
        final SliceHeader header = SliceHeader.read(major, blockStream);

        Block coreBlock = null;
        Map<Integer, Block> externalDataBlockMap = new HashMap<>();

        for (int i = 0; i < header.getDataBlockCount(); i++) {
            final Block block = Block.read(major, blockStream);

            switch (block.getContentType()) {
                case CORE:
                    coreBlock = block;
                    break;
                case EXTERNAL:
                    externalDataBlockMap.put(block.getContentId(), block);
                    break;

                default:
                    throw new RuntimeException("Not a slice block, content type id " + block.getContentType().name());
            }
        }

        return new Slice(header, coreBlock, externalDataBlockMap);
    }

    /**
     * Write the Slice out to the the specified {@link OutputStream}.
     * The method is parameterized with the CRAM major version number.
     *
     * @param major CRAM version major number
     * @param blockStream output stream to write to
     */
    public void write(final int major, final OutputStream blockStream) {
        header.write(major, blockStream);
        coreBlock.write(major, blockStream);
        for (final Block block : externalBlockMap.values())
            block.write(major, blockStream);
    }

    /**
     * Initialize a Cram Record Reader from a Slice
     *
     * @param compressionHeader the associated Cram Compression Header
     * @param validationStringency how strict to be when reading this CRAM record
     */
    public CramRecordReader createCramRecordReader(final CompressionHeader compressionHeader,
                                                   final ValidationStringency validationStringency) {
        return new CramRecordReader(getCoreBlockInputStream(),
                getExternalBlockInputMap(),
                compressionHeader,
                getSequenceId(),
                validationStringency);
    }

    /**
     * Uses a Multiple Reference Slice Alignment Reader to determine the Reference Spans of a Slice.
     * The intended use is for CRAI indexing.
     *
     * @param compressionHeader the associated Cram Compression Header
     * @param validationStringency how strict to be when reading CRAM records
     */
    public Map<Integer, SliceAlignment> getMultiRefAlignmentSpans(final CompressionHeader compressionHeader,
                                                                  final ValidationStringency validationStringency) {
        final MultiRefSliceAlignmentSpanReader reader = new MultiRefSliceAlignmentSpanReader(getCoreBlockInputStream(),
                getExternalBlockInputMap(),
                compressionHeader,
                validationStringency,
                getAlignmentStart(),
                getRecordCount());
        return reader.getReferenceSpans();
    }

    private BitInputStream getCoreBlockInputStream() {
        return new DefaultBitInputStream(new ByteArrayInputStream(coreBlock.getUncompressedContent()));
    }

    private Map<Integer, ByteArrayInputStream> getExternalBlockInputMap() {
        return externalBlockMap.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new ByteArrayInputStream(e.getValue().getUncompressedContent())));
    }

    // header delegate methods

    public int getSequenceId() {
        return header.getSequenceId();
    }

    public SliceAlignment getSliceAlignment() {
        return header.getSliceAlignment();
    }

    public int getAlignmentStart() {
        return header.getAlignmentStart();
    }

    public int getAlignmentSpan() {
        return header.getAlignmentSpan();
    }

    public int getRecordCount() {
        return header.getRecordCount();
    }

    public byte[] getRefMD5() {
        return header.getRefMD5();
    }

    public void validateRefMD5(final byte[] refBases) {
        header.validateRefMD5(refBases);
    }

    public boolean hasSingleReference() {
        return header.hasSingleReference();
    }

    public boolean hasNoReference() {
        return header.hasNoReference();
    }

    public boolean hasMultipleReferences() {
        return header.hasMultipleReferences();
    }

    public boolean hasEmbeddedRefBlock() {
        return header.hasEmbeddedRefBlock();
    }

    // end header delegate methods

    public int getExternalBlockCount() {
        return externalBlockMap.size();
    }
}
