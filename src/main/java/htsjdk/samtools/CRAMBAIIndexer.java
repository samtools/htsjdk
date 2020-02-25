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

import htsjdk.samtools.cram.BAIEntry;
import htsjdk.samtools.cram.CRAIEntry;
import htsjdk.samtools.cram.CRAIIndex;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.ProgressLogger;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

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
 * but it is unused.  This would be accomplished via {@link #createIndex(SeekableStream, File, Log, ValidationStringency)}.
 */
public class CRAMBAIIndexer implements CRAMIndexer {

    // The number of references (chromosomes) in the BAM file
    private final int numReferences;

    // output written as binary, or (for debugging) as text
    private final BAMIndexWriter outputWriter;

    private int currentReference = 0;

    // content is built up from the input bam file using this
    private final CRAMBAIIndexBuilder indexBuilder;
    private final CompressorCache compressorCache = new CompressorCache();

    /**
     * Create a CRAM indexer that writes BAI to a file.
     *
     * @param output     binary BAM Index (.bai) file
     * @param fileHeader header for the corresponding bam file
     */
    private CRAMBAIIndexer(final File output, final SAMFileHeader fileHeader) {
        if (fileHeader.getSortOrder() != SAMFileHeader.SortOrder.coordinate) {
            throw new SAMException("CRAM file must be coordinate-sorted for indexing.");
        }
        numReferences = fileHeader.getSequenceDictionary().size();
        indexBuilder = new CRAMBAIIndexBuilder(fileHeader);
        outputWriter = new BinaryBAMIndexWriter(numReferences, output);
    }

    /**
     * Create a CRAM indexer that writes BAI to a stream.
     *
     * @param output     Index will be written here.  output will be closed when finish() method is called.
     * @param fileHeader header for the corresponding bam file.
     */
    public CRAMBAIIndexer(final OutputStream output, final SAMFileHeader fileHeader) {
        if (fileHeader.getSortOrder() != SAMFileHeader.SortOrder.coordinate) {
            throw new SAMException("CRAM file mut be coordinate-sorted for indexing.");
        }
        numReferences = fileHeader.getSequenceDictionary().size();
        indexBuilder = new CRAMBAIIndexBuilder(fileHeader);
        outputWriter = new BinaryBAMIndexWriter(numReferences, output);
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

        if (entryContext.isMappedSingleRef()) {
            final int reference = entryContext.getReferenceSequenceID();
            if (reference != currentReference) {
                // process any completed references
                advanceToReference(reference);
            }

            // check that it advanced properly
            if (reference != currentReference) {
                throw new SAMException(
                        String.format("Unexpected reference %s when constructing index for reference %d for slice",
                                reference,
                                currentReference));
            }
        }

        indexBuilder.recordBAIEntryIndexMetadata(baiEntry);

        if (entryContext.isMappedSingleRef()) {
            indexBuilder.processBAIEntry(baiEntry);
        }
    }

    /**
     * After all the slices have been processed, finish is called.
     * Writes any final information and closes the output file.
     */
    @Override
    public void finish() {
        // process any remaining references
        advanceToReference(numReferences);
        outputWriter.writeNoCoordinateRecordCount(indexBuilder.getNoCoordinateRecordCount());
        outputWriter.close();
    }

    /**
     * write out any references between the currentReference and the nextReference
     */
    private void advanceToReference(final int nextReference) {
        while (currentReference < nextReference) {
            final BAMIndexContent content = indexBuilder.processCurrentReference();
            outputWriter.writeReference(content);
            currentReference++;
            indexBuilder.startNewReference();
        }
    }

    /**
     * Generates a BAI index file from an input CRAM stream
     *
     * @param stream CRAM stream to index
     * @param output File for output index file
     * @param log    optional {@link htsjdk.samtools.util.Log} to output progress
     */
    public static void createIndex(final SeekableStream stream,
                                   final File output,
                                   final Log log,
                                   final ValidationStringency validationStringency) {

        final CramHeader cramHeader = CramIO.readCramHeader(stream);
        final SAMFileHeader samFileHeader = Container.readSAMFileHeaderContainer(cramHeader.getCRAMVersion(), stream, null);
        if (samFileHeader.getSortOrder() != SAMFileHeader.SortOrder.coordinate) {
            throw new SAMException(String.format(
                    "Input must be coordinate sorted (found %s) to create an index.",
                    samFileHeader.getSortOrder()));
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
                        sequenceName = samFileHeader.getSequence(
                                containerReferenceContext.getReferenceSequenceID()).getSequenceName();
                        break;
                }
                progressLogger.record(sequenceName, alignmentContext.getAlignmentStart());
            }

        } while (!container.isEOF());

        indexer.finish();
    }

    /**
     * Class for constructing BAM index files.
     * One instance is used to construct an entire index.
     * processAlignment is called for each alignment until a new reference is encountered, then
     * processReference is called when all records for the reference have been processed.
     */
    private class CRAMBAIIndexBuilder {

        private final SAMFileHeader bamHeader;

        // the bins for the current reference
        private Bin[] bins; // made only as big as needed for each reference
        private int binsSeen = 0;

        // linear index for the current reference
        private final long[] index = new long[LinearIndex.MAX_LINEAR_INDEX_SIZE];
        private int largestIndexSeen = -1;

        // information in meta data
        private final BAMIndexMetaData indexStats = new BAMIndexMetaData();

        /**
         * @param header SAMFileHeader used for reference name (in index stats) and for max bin number
         */
        private CRAMBAIIndexBuilder(final SAMFileHeader header) {
            this.bamHeader = header;
        }

        private SAMFileHeader getBamHeader() {
            return bamHeader;
        }

        private void recordBAIEntryIndexMetadata(final BAIEntry baiEntry) {
            indexStats.recordMetaData(baiEntry);
        }

        private int computeIndexingBin(final BAIEntry baiEntry) {
            // regionToBin has zero-based, half-open API
            //final AlignmentContext sliceAlignmentContext = baiEntry.getAlignmentContext();
            final int alignmentStart = baiEntry.getAlignmentStart() - 1;
            int alignmentEnd = baiEntry.getAlignmentStart() + baiEntry.getAlignmentSpan() - 1;
            if (alignmentEnd <= alignmentStart) {
                // If alignment end cannot be determined (e.g. because this read is not really aligned),
                // then treat this as a one base alignment for indexing purposes.
                alignmentEnd = alignmentStart + 1;
            }
            return GenomicIndexUtil.regionToBin(alignmentStart, alignmentEnd);
        }

        /**
         * Record any index information for a given CRAM slice
         *
         * Reads these Slice fields:
         * sequenceId, alignmentStart, alignmentSpan, containerByteOffset, index
         *
         //* @param slice CRAM slice, single ref only.
         */
        private void processBAIEntry(final BAIEntry baiEntry) {
            final ReferenceContext sliceContext = baiEntry.getReferenceContext();
            if (! sliceContext.isMappedSingleRef()) {
                return; // do nothing for records without coordinates, but count them
            }

            // various checks
            final int reference = sliceContext.getReferenceSequenceID();
            if (reference != currentReference) {
                throw new SAMException(
                        String.format("Unexpected reference %s when constructing index for reference %d for slice",
                                reference,
                                currentReference));
            }

            // process bins

            final int binNum = computeIndexingBin(baiEntry);

            // has the bins array been allocated? If not, do so
            if (bins == null) {
                final SAMSequenceRecord seq = bamHeader.getSequence(reference);
                if (seq == null) {
                    bins = new Bin[GenomicIndexUtil.MAX_BINS + 1];
                } else {
                    bins = new Bin[AbstractBAMFileIndex.getMaxBinNumberForSequenceLength(seq.getSequenceLength()) + 1];
                }
            }

            // is there a bin already represented for this index?  if not, add one
            final Bin bin;
            if (bins[binNum] != null) {
                bin = bins[binNum];
            } else {
                bin = new Bin(reference, binNum);
                bins[binNum] = bin;
                binsSeen++;
            }

            // process chunks

            final long chunkStart = (baiEntry.getContainerStartByteOffset() << 16) | baiEntry.getLandmarkIndex();
            final long chunkEnd = ((baiEntry.getContainerStartByteOffset() << 16) | baiEntry.getLandmarkIndex()) + 1;

            final Chunk newChunk = new Chunk(chunkStart, chunkEnd);

            final List<Chunk> oldChunks = bin.getChunkList();
            if (!bin.containsChunks()) {
                bin.addInitialChunk(newChunk);

            } else {
                final Chunk lastChunk = bin.getLastChunk();

                // Coalesce chunks that are in the same or adjacent file blocks.
                // Similar to AbstractBAMFileIndex.optimizeChunkList,
                // but no need to copy the list, no minimumOffset, and maintain bin.lastChunk
                if (BlockCompressedFilePointerUtil.areInSameOrAdjacentBlocks(lastChunk.getChunkEnd(), chunkStart)) {
                    lastChunk.setChunkEnd(chunkEnd);  // coalesced
                } else {
                    oldChunks.add(newChunk);
                    bin.setLastChunk(newChunk);
                }
            }

            // process linear index

            // the smallest file offset that appears in the 16k window for this bin
            final int alignmentStart = baiEntry.getAlignmentStart();
            final int alignmentEnd = baiEntry.getAlignmentStart() + baiEntry.getAlignmentSpan();
            int startWindow = LinearIndex.convertToLinearIndexOffset(alignmentStart); // the 16k window
            final int endWindow;

            if (alignmentEnd == SAMRecord.NO_ALIGNMENT_START) {   // assume alignment uses one position
                // Next line for C (samtools index) compatibility. Differs only when on a window boundary
                startWindow = LinearIndex.convertToLinearIndexOffset(alignmentStart - 1);
                endWindow = startWindow;
            } else {
                endWindow = LinearIndex.convertToLinearIndexOffset(alignmentEnd);
            }

            if (endWindow > largestIndexSeen) {
                largestIndexSeen = endWindow;
            }

            // set linear index at every 16K window that this alignment overlaps
            for (int win = startWindow; win <= endWindow; win++) {
                if (index[win] == 0 || chunkStart < index[win]) {
                    index[win] = chunkStart;
                }
            }
        }

        /**
         * Creates the BAMIndexContent for this reference.
         * Requires all alignments of the reference have already been processed.
         */
        private BAMIndexContent processCurrentReference() {

            // process bins
            if (binsSeen == 0) {
                return null;  // no bins for this reference
            }

            // process chunks
            // nothing needed

            // process linear index
            // linear index will only be as long as the largest index seen
            final long[] newIndex = new long[largestIndexSeen + 1]; // in java1.6 Arrays.copyOf(index, largestIndexSeen + 1);

            // C (samtools index) also fills in intermediate 0's with values.  This seems unnecessary, but safe
            long lastNonZeroOffset = 0;
            for (int i = 0; i <= largestIndexSeen; i++) {
                if (index[i] == 0) {
                    index[i] = lastNonZeroOffset; // not necessary, but C (samtools index) does this
                    // note, if you remove the above line BAMIndexWriterTest.compareTextual and compareBinary will have to change
                } else {
                    lastNonZeroOffset = index[i];
                }
                newIndex[i] = index[i];
            }

            final LinearIndex linearIndex = new LinearIndex(currentReference, 0, newIndex);

            return new BAMIndexContent(currentReference, bins, binsSeen, indexStats, linearIndex);
        }

        /**
         * @return the count of records with no coordinate positions
         */
        private long getNoCoordinateRecordCount() {
            return indexStats.getNoCoordinateRecordCount();
        }

        /**
         * reinitialize all data structures when the reference changes
         */
        private void startNewReference() {
            bins = null;
            if (binsSeen > 0) {
                Arrays.fill(index, 0);
            }
            binsSeen = 0;
            largestIndexSeen = -1;
            indexStats.newReference();
        }
    }
}
