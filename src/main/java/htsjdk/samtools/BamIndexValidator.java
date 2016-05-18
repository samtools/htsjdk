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

import htsjdk.samtools.util.CloseableIterator;

import java.util.Arrays;
import java.util.List;

/**
 * Class to validate (at two different levels of thoroughness) the index for a BAM file.
 *
 * This class is [<em>not</em>] thread safe [because it is immutable].
 */
public class BamIndexValidator {

    public enum IndexValidationStringency {
        EXHAUSTIVE, LESS_EXHAUSTIVE, NONE
    }

    public static int exhaustivelyTestIndex(final SamReader reader) { // throws Exception {
        // look at all chunk offsets in a linear index to make sure they are valid

        if (reader.indexing().hasBrowseableIndex()) {

            // content is from an existing bai file
            final CachingBAMFileIndex existingIndex = (CachingBAMFileIndex) reader.indexing().getBrowseableIndex(); // new CachingBAMFileIndex(inputBai, null);
            final int numRefs = existingIndex.getNumberOfReferences();

            int chunkCount = 0;
            int indexCount = 0;
            for (int i = 0; i < numRefs; i++) {
                final BAMIndexContent content = existingIndex.getQueryResults(i);
                for (final Chunk c : content.getAllChunks()) {
                    final CloseableIterator<SAMRecord> iter = ((SamReader.PrimitiveSamReaderToSamReaderAdapter) reader).iterator(new BAMFileSpan(c));
                    chunkCount++;
                    SAMRecord sam = null;
                    try {
                        sam = iter.next();
                        iter.close();
                    } catch (final Exception e) {
                        throw new SAMException("Exception in BamIndexValidator. Last good record " + sam + " in chunk " + c + " chunkCount=" + chunkCount, e);
                    }
                }
                // also seek to every position in the linear index
                // final BAMRecordCodec bamRecordCodec = new BAMRecordCodec(reader.getFileHeader());
                // bamRecordCodec.setInputStream(reader.getInputStream());

                final LinearIndex linearIndex = content.getLinearIndex();
                for (final long l : linearIndex.getIndexEntries()) {
                    try {
                        if (l != 0) {
                            final CloseableIterator<SAMRecord> iter = ((SamReader.PrimitiveSamReaderToSamReaderAdapter) reader).iterator(new BAMFileSpan(new Chunk(l, l + 1)));
                            final SAMRecord sam = iter.next();   // read the first record identified by the linear index
                            indexCount++;
                            iter.close();
                        }
                    } catch (final Exception e) {
                        throw new SAMException("Exception in BamIndexValidator. Linear index access failure " + l + " indexCount=" + indexCount, e);
                    }

                }
            }
            return chunkCount;
            // System.out.println("Found " chunkCount + " chunks in test " + inputBai +
            // " linearIndex positions = " + indexCount);
        } // else  not a bam file with a browseable index
        //    System.err.println("No browseableIndex for reader");
        return 0;
    }

    /**
     * A less time-consuming index validation that only looks at the first and last references in the index
     * and the first and last chunks in each of those
     *
     * @param reader
     * @return # of chunks examined, or 0 if there is no browseable index for the reader
     */
    public static int lessExhaustivelyTestIndex(final SamReader reader) {
        // look at all chunk offsets in a linear index to make sure they are valid
        if (reader.indexing().hasBrowseableIndex()) {

            // content is from an existing bai file
            final CachingBAMFileIndex existingIndex = (CachingBAMFileIndex) reader.indexing().getBrowseableIndex();
            final int numRefs = existingIndex.getNumberOfReferences();

            int chunkCount = 0;
            int indexCount = 0;
            for (int i = 0; i < numRefs; i++) {

                final BAMIndexContent content = existingIndex.getQueryResults(i);

                final List<Chunk> chunks = content.getAllChunks();
                final int numChunks = chunks.size();
                // We are looking only at the first and last chunks
                for (final int chunkNo : Arrays.asList(0, numChunks - 1)) {
                    chunkCount++;

                    final Chunk c = chunks.get(chunkNo);
                    final CloseableIterator<SAMRecord> iter = ((SamReader.PrimitiveSamReaderToSamReaderAdapter) reader).iterator(new BAMFileSpan(c));
                    try {
                        final SAMRecord sam = iter.next();
                        iter.close();
                    } catch (final Exception e) {
                        throw new SAMException("Exception querying chunk " + chunkNo + " from reference index " + i, e);
                    }
                }

                // also seek to first and last position in the linear index
                final long linearIndexEntries[] = content.getLinearIndex().getIndexEntries();
                for (final int binNo : Arrays.asList(0, linearIndexEntries.length - 1)) {
                    indexCount++;
                    final long l = linearIndexEntries[binNo];
                    try {
                        if (l != 0) {
                            final CloseableIterator<SAMRecord> iter = ((SamReader.PrimitiveSamReaderToSamReaderAdapter) reader).iterator(new BAMFileSpan(new Chunk(l, l + 1)));
                            final SAMRecord sam = iter.next();   // read the first record identified by the linear index
                            iter.close();
                        }
                    } catch (final Exception e) {
                        throw new SAMException("Exception in BamIndexValidator. Linear index access failure " + l + " indexCount=" + indexCount, e);
                    }
                }
            }
            return chunkCount;
        }
        // else it's not a bam file with a browseable index
        return 0;
    }
}
