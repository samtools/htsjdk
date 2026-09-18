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
/*
 * The MIT License
 *
 * Copyright (c) 2014 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sub-license, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools;

import htsjdk.index.BinningIndex;
import htsjdk.samtools.cram.BAIEntry;
import htsjdk.samtools.cram.CRAIEntry;
import htsjdk.samtools.cram.CRAIIndex;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.ProgressLogger;
import htsjdk.samtools.util.RuntimeIOException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Class for both constructing BAM index content and writing it out.
 *
 * There are two usage patterns:
 *
 * 1) Building a bam index (BAI) while building the CRAM file
 * 2) Building a bam index (BAI) from an existing CRAI file
 *
 * 1) is driven by {@link CRAMContainerStreamWriter} and proceeds by calling {@link CRAMBAIIndexer#processContainer}
 * after each {@link Container} is built, and {@link CRAMBAIIndexer#finish()} is called at the end.
 *
 * 2) is driven by {@link CRAIIndex#openCraiFileAsBaiStream(InputStream, SAMSequenceDictionary)}
 * and proceeds by processing {@link CRAIEntry} elements obtained from
 * {@link CRAMCRAIIndexer#readIndex(InputStream)}.  {@link CRAMBAIIndexer#processBAIEntry(BAIEntry)}
 * is called on each {@link CRAIEntry} and {@link CRAMBAIIndexer#finish()} is called at the end.
 *
 * NOTE: a third pattern of building a BAI from a CRAM file is also supported by this class,
 * but it is unused.  This would be accomplished via {@link #createIndex(SeekableStream, Path, Log, ValidationStringency)}.
 */
public class CRAMBAIIndexer implements CRAMIndexer {

    // The number of references (chromosomes) in the BAM file
    private final int numReferences;

    private final OutputStream output;

    private final BinningIndex.Builder indexBuilder =
            new BinningIndex.Builder(BinningIndex.BAI_MIN_SHIFT, BinningIndex.BAI_DEPTH).reportingRecordCounts();

    private final CompressorCache compressorCache = new CompressorCache();

    /**
     * Create a CRAM indexer that writes BAI to a file.
     *
     * @param output     binary BAM Index (.bai) file path
     * @param fileHeader header for the corresponding bam file
     */
    private CRAMBAIIndexer(final Path output, final SAMFileHeader fileHeader) {
        this(openForWriting(output, fileHeader), fileHeader);
    }

    /** Checks the header first, so that a path is not created or truncated for an index that will not be written. */
    private static OutputStream openForWriting(final Path output, final SAMFileHeader fileHeader) {
        if (fileHeader.getSortOrder() != SAMFileHeader.SortOrder.coordinate) {
            throw new SAMException("CRAM file must be coordinate-sorted for indexing.");
        }
        try {
            return IOUtil.maybeBufferOutputStream(Files.newOutputStream(output));
        } catch (final IOException e) {
            throw new SAMException("Exception opening output file " + output, e);
        }
    }

    /**
     * Create a CRAM indexer that writes BAI to a stream.
     *
     * @param output     Index will be written here.  output will be closed when finish() method is called.
     * @param fileHeader header for the corresponding bam file.
     */
    public CRAMBAIIndexer(final OutputStream output, final SAMFileHeader fileHeader) {
        if (fileHeader.getSortOrder() != SAMFileHeader.SortOrder.coordinate) {
            throw new SAMException("CRAM file must be coordinate-sorted for indexing.");
        }
        this.numReferences = fileHeader.getSequenceDictionary().size();
        this.output = output;
    }

    /**
     * Index a container, any of mapped, unmapped and multiple references are allowed.
     * The only requirement is sort order by coordinate.
     * For multiref containers the method reads the container through unpacking all reads.
     * This is slower than single reference but should be faster than normal reading.
     *
     * @param container container to be indexed
     */
    @Override
    public void processContainer(final Container container, final ValidationStringency validationStringency) {
        container.getBAIEntries(compressorCache).forEach(b -> processBAIEntry(b));
    }

    public final void processBAIEntry(final BAIEntry baiEntry) {
        final ReferenceContext entryContext = baiEntry.getReferenceContext();
        if (entryContext.isMultiRef()) {
            throw new SAMException("Expecting a single reference or unmapped slice.");
        }
        if (!entryContext.isMappedSingleRef()) {
            indexBuilder.addNoCoordinateRecords(baiEntry.getUnmappedUnplacedReadsCount());
            return;
        }

        // An entry with no start is filed at the first base rather than rejected. One with no span covers a base.
        final int alignmentStart = Math.max(baiEntry.getAlignmentStart(), 1);
        // In long arithmetic: a span near the largest int would otherwise wrap to an end that passes the check.
        final long alignmentEnd = (long) alignmentStart + Math.max(baiEntry.getAlignmentSpan(), 1) - 1;
        if (alignmentEnd > indexBuilder.getMaxPosition()) {
            throw new SAMException(String.format(
                    "Slice at %d-%d on reference %d lies beyond %d, the last position a BAI can address; "
                            + "index this CRAM with a CRAI instead",
                    alignmentStart,
                    alignmentEnd,
                    entryContext.getReferenceSequenceID(),
                    indexBuilder.getMaxPosition()));
        }
        // A BAI's virtual offsets have no meaning for a CRAM, so they carry the container's offset and the
        // slice's place within it, and a chunk ends one "byte" after it starts.
        final long chunkStart = (baiEntry.getContainerStartByteOffset() << 16) | baiEntry.getLandmarkIndex();
        try {
            indexBuilder.add(
                    entryContext.getReferenceSequenceID(),
                    alignmentStart,
                    (int) alignmentEnd,
                    chunkStart,
                    chunkStart + 1);
        } catch (final IllegalArgumentException e) {
            throw new SAMException("Exception creating BAI index for a CRAM slice", e);
        }
        // The unmapped count includes any unplaced reads, which a slice on a single reference cannot have.
        indexBuilder.addRecordCounts(
                baiEntry.getMappedReadsCount(),
                Math.max(0, baiEntry.getUnmappedReadsCount() - baiEntry.getUnmappedUnplacedReadsCount()));
        indexBuilder.addNoCoordinateRecords(baiEntry.getUnmappedUnplacedReadsCount());
    }

    /**
     * After all the slices have been processed, finish is called.
     * Writes the index and closes the output.
     */
    @Override
    public void finish() {
        // The codec owns the output, so that it is closed even when the index cannot be built or written.
        try (BinaryCodec codec = new BinaryCodec(output)) {
            final BinningIndex index = indexBuilder.build(numReferences);
            codec.writeBytes(BAMFileConstants.BAM_INDEX_MAGIC);
            codec.writeInt(numReferences);
            index.writeBaiLayout(codec);
        }
    }

    /**
     * Generates a BAI index file from an input CRAM stream
     *
     * @param stream CRAM stream to index
     * @param output Path for output index file
     * @param log    optional {@link htsjdk.samtools.util.Log} to output progress
     * @param validationStringency validation stringency for processing
     */
    public static void createIndex(
            final SeekableStream stream,
            final Path output,
            final Log log,
            final ValidationStringency validationStringency) {

        final CramHeader cramHeader = CramIO.readCramHeader(stream);
        final SAMFileHeader samFileHeader =
                Container.readSAMFileHeaderContainer(cramHeader.getCRAMVersion(), stream, null);
        if (samFileHeader.getSortOrder() != SAMFileHeader.SortOrder.coordinate) {
            throw new SAMException(String.format(
                    "Input must be coordinate sorted (found %s) to create an index.", samFileHeader.getSortOrder()));
        }
        final CRAMBAIIndexer indexer = new CRAMBAIIndexer(output, samFileHeader);

        Container container = null;
        final ProgressLogger progressLogger = new ProgressLogger(log, 1, "indexed", "slices");
        do {
            try {
                container = new Container(cramHeader.getCRAMVersion(), stream, stream.position());
            } catch (final IOException e) {
                throw new RuntimeIOException("error getting stream position", e);
            }
            if (container == null || container.isEOF()) {
                break;
            }

            indexer.processContainer(container, validationStringency);

            if (null != log) {
                String sequenceName;
                final AlignmentContext alignmentContext = container.getAlignmentContext();
                final ReferenceContext containerReferenceContext = alignmentContext.getReferenceContext();
                switch (containerReferenceContext.getType()) {
                    case UNMAPPED_UNPLACED_TYPE:
                        sequenceName = "?";
                        break;
                    case MULTIPLE_REFERENCE_TYPE:
                        sequenceName = "???";
                        break;
                    default:
                        sequenceName = samFileHeader
                                .getSequence(containerReferenceContext.getReferenceSequenceID())
                                .getSequenceName();
                        break;
                }
                progressLogger.record(sequenceName, alignmentContext.getAlignmentStart());
            }

        } while (!container.isEOF());

        indexer.finish();
    }
}
