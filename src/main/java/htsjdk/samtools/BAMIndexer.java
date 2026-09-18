/*
 * The MIT License
 *
 * Copyright (c) 2010 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools;

import htsjdk.index.BinningIndex;
import htsjdk.index.FileBackedBinningIndex;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Class for both constructing BAM index content and writing it out, as a BAI or a CSI.
 * There are two usage patterns:
 * 1) Building a bam index from an existing bam file
 * 2) Building a bam index while building the bam file
 * In both cases, processAlignment is called for each alignment record and
 * finish() is called at the end, which is when the index is written.
 */
public class BAMIndexer {

    /** The {@code min_shift} samtools gives a CSI index unless told otherwise. */
    public static final int DEFAULT_CSI_MIN_SHIFT = BinningIndex.BAI_MIN_SHIFT;

    private static final Log log = Log.getInstance(BAMIndexer.class);

    // The number of references (chromosomes) in the BAM file
    private final int numReferences;

    // BAI or CSI; never AUTO
    private final BamIndexType indexType;

    private final BinningIndex.Builder indexBuilder;

    private final OutputStream output;

    /**
     * Prepare to index a BAM.
     *
     * @param output     binary BAM Index (.bai) file path
     * @param fileHeader header for the corresponding bam file
     */
    public BAMIndexer(final Path output, final SAMFileHeader fileHeader) {
        this(output, fileHeader, BamIndexType.BAI);
    }

    /**
     * Prepare to index a BAM.
     *
     * @param output     index file path; this class does not choose its name, so a caller passing
     *                   {@link BamIndexType#AUTO} should {@link BamIndexType#resolve resolve} it first
     * @param fileHeader header for the corresponding bam file
     * @param indexType  the kind of index to write
     */
    public BAMIndexer(final Path output, final SAMFileHeader fileHeader, final BamIndexType indexType) {
        this(output, fileHeader, indexType, DEFAULT_CSI_MIN_SHIFT);
    }

    /**
     * Prepare to index a BAM.
     *
     * @param output      index file path, as for {@link #BAMIndexer(Path, SAMFileHeader, BamIndexType)}
     * @param fileHeader  header for the corresponding bam file
     * @param indexType   the kind of index to write
     * @param csiMinShift for a CSI, log2 of the span of its smallest bins; ignored for a BAI
     */
    public BAMIndexer(
            final Path output, final SAMFileHeader fileHeader, final BamIndexType indexType, final int csiMinShift) {
        this(() -> openForWriting(output), fileHeader, indexType, csiMinShift, true);
    }

    /**
     * Prepare to index a BAM.
     *
     * @param output     Index will be written here.  output will be closed when finish() method is called.
     * @param fileHeader header for the corresponding bam file.
     */
    public BAMIndexer(final OutputStream output, final SAMFileHeader fileHeader) {
        this(output, fileHeader, true);
    }

    /**
     * Prepare to index a BAM.
     *
     * @param output     Index will be written here.  output will be closed when finish() method is called.
     * @param fileHeader header for the corresponding bam file.
     * @param fillInUninitializedValues if true, set uninitialized values (-1) to the last non-zero offset;
     *                                  if false, leave uninitialized values as -1, which is required when merging index files
     *                                  (see {@link BAMIndexMerger})
     */
    public BAMIndexer(
            final OutputStream output, final SAMFileHeader fileHeader, final boolean fillInUninitializedValues) {
        this(output, fileHeader, BamIndexType.BAI, DEFAULT_CSI_MIN_SHIFT, fillInUninitializedValues);
    }

    /**
     * Prepare to index a BAM.
     *
     * @param output      Index will be written here.  output will be closed when finish() method is called.
     * @param fileHeader  header for the corresponding bam file.
     * @param indexType   the kind of index to write
     * @param csiMinShift for a CSI, log2 of the span of its smallest bins; the rest of its binning scheme is
     *                    chosen to reach the header's longest sequence, as samtools chooses it. Ignored for a BAI.
     * @param fillInUninitializedValues as for {@link #BAMIndexer(OutputStream, SAMFileHeader, boolean)}; a CSI
     *                    stores no linear index, so it makes no difference to one
     */
    public BAMIndexer(
            final OutputStream output,
            final SAMFileHeader fileHeader,
            final BamIndexType indexType,
            final int csiMinShift,
            final boolean fillInUninitializedValues) {
        this(() -> output, fileHeader, indexType, csiMinShift, fillInUninitializedValues);
    }

    /**
     * @param outputOpener called once everything else has been checked, so that a path is not created or
     *     truncated for an index that is not going to be written
     */
    private BAMIndexer(
            final Supplier<OutputStream> outputOpener,
            final SAMFileHeader fileHeader,
            final BamIndexType indexType,
            final int csiMinShift,
            final boolean fillInUninitializedValues) {
        if (fileHeader.getSortOrder() != SAMFileHeader.SortOrder.coordinate) {
            if (fileHeader.getSortOrder() == SAMFileHeader.SortOrder.unsorted) {
                log.warn(
                        "For indexing, the BAM file is required to be coordinate sorted. Attempting to index \"unsorted\" BAM file.");
            } else {
                throw new SAMException("Indexing requires a coordinate-sorted input BAM.");
            }
        }
        final SAMSequenceDictionary dictionary = fileHeader.getSequenceDictionary();
        this.numReferences = dictionary.size();
        this.indexType = indexType.resolve(dictionary);
        final boolean csi = this.indexType == BamIndexType.CSI;
        final BinningIndex.Geometry geometry = csi
                ? BinningIndex.shallowestCsiGeometry(csiMinShift, BamIndexType.longestSequence(dictionary))
                : new BinningIndex.Geometry(BinningIndex.BAI_MIN_SHIFT, BinningIndex.BAI_DEPTH);
        this.indexBuilder =
                new BinningIndex.Builder(geometry.minShift(), geometry.depth(), csi).reportingRecordCounts();
        if (!fillInUninitializedValues) {
            indexBuilder.leavingEmptyWindowsUnset();
        }
        this.output = outputOpener.get();
    }

    private static OutputStream openForWriting(final Path output) {
        try {
            return IOUtil.maybeBufferOutputStream(Files.newOutputStream(output));
        } catch (final IOException e) {
            throw new SAMException("Exception opening output file " + output, e);
        }
    }

    /**
     * Record any index information for a given BAM record.
     * If this alignment starts a new reference, write out the old reference.
     * Requires a non-null value for rec.getFileSource()
     *
     * @param rec The BAM record
     */
    public void processAlignment(final SAMRecord rec) {
        try {
            final int alignmentStart = rec.getAlignmentStart();
            if (alignmentStart == SAMRecord.NO_ALIGNMENT_START) {
                indexBuilder.addNoCoordinateRecords(1);
                return;
            }
            final SAMFileSource source = rec.getFileSource();
            if (source == null) {
                throw new SAMException("No source (virtual file offsets); needed for indexing on BAM Record " + rec);
            }
            final Chunk chunk = ((BAMFileSpan) source.getFilePointer()).getSingleChunk();
            // An unmapped read has no alignment end, which the builder takes as the single base at its start.
            indexBuilder.add(
                    rec.getReferenceIndex(),
                    alignmentStart,
                    rec.getAlignmentEnd(),
                    chunk.getChunkStart(),
                    chunk.getChunkEnd());
            if (rec.getReadUnmappedFlag()) {
                indexBuilder.addRecordCounts(0, 1);
            } else {
                indexBuilder.addRecordCounts(1, 0);
            }
        } catch (final Exception e) {
            throw new SAMException("Exception creating BAM index for record " + rec, e);
        }
    }

    /**
     * After all the alignment records have been processed, finish is called.
     * Writes the index and closes the output.
     */
    public void finish() {
        // samtools writes a CSI BGZF-compressed
        final OutputStream stream =
                indexType == BamIndexType.CSI ? new BlockCompressedOutputStream(output, (Path) null) : output;
        // The codec owns the output, so that it is closed even when the index cannot be built or written.
        try (BinaryCodec codec = new BinaryCodec(stream)) {
            final BinningIndex index = indexBuilder.build(numReferences);
            if (indexType == BamIndexType.CSI) {
                // samtools puts nothing in the format-specific block for a BAM
                index.writeCsi(codec, new byte[0]);
            } else {
                writeBai(index, codec);
            }
        }
    }

    /** Writes a whole BAI file: the magic, the reference count, and the index in the BAI layout. */
    static void writeBai(final BinningIndex index, final BinaryCodec codec) {
        codec.writeBytes(BAMFileConstants.BAM_INDEX_MAGIC);
        codec.writeInt(index.getReferenceCount());
        index.writeBaiLayout(codec);
    }

    /**
     * Rewrites a BAI file, as a BAI or as human-readable text. Only used for testing.
     *
     * @param input      Input BAM Index (.bai) file path
     * @param output     Output BAM Index (.bai) file path (or bai.txt file when text)
     * @param textOutput Whether to create text output or binary
     */
    public static void createAndWriteIndex(final Path input, final Path output, final boolean textOutput) {
        final BinningIndex index;
        try (FileBackedBinningIndex existingIndex = FileBackedBinningIndex.open(input, true)) {
            if (existingIndex.isCsi()) {
                throw new SAMException("Expected a BAI but found a CSI: " + input);
            }
            index = existingIndex.loadAll();
        }
        if (textOutput) {
            TextualBAMIndexWriter.write(index, output);
        } else {
            try (BinaryCodec codec = new BinaryCodec(output, true)) {
                writeBai(index, codec);
            }
        }
    }

    /**
     * Generates a BAM index file from an input BAM file
     *
     * @param reader SamReader for input BAM file
     * @param output Path for output index file
     */
    public static void createIndex(SamReader reader, Path output) {
        createIndex(reader, output, null);
    }

    /**
     * Generates a BAM index file from an input BAM file
     *
     * @param reader SamReader for input BAM file
     * @param output Path for output index file
     * @param log    Optional logger for progress messages
     */
    public static void createIndex(SamReader reader, Path output, Log log) {
        createIndex(reader, output, log, BamIndexType.BAI);
    }

    /**
     * Generates a BAM index file from an input BAM file
     *
     * @param reader    SamReader for input BAM file
     * @param output    Path for output index file; see {@link #BAMIndexer(Path, SAMFileHeader, BamIndexType)}
     * @param log       Optional logger for progress messages
     * @param indexType the kind of index to write
     */
    public static void createIndex(SamReader reader, Path output, Log log, BamIndexType indexType) {

        BAMIndexer indexer = new BAMIndexer(output, reader.getFileHeader(), indexType);

        long totalRecords = 0;

        // create and write the content
        for (SAMRecord rec : reader) {
            if (++totalRecords % 1000000 == 0) {
                if (null != log) log.info(totalRecords + " reads processed ...");
            }
            indexer.processAlignment(rec);
        }
        indexer.finish();
    }
}
