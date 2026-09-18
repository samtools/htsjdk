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

import htsjdk.index.ReferenceBins;
import htsjdk.index.ReferenceBinsSource;
import htsjdk.samtools.util.CloseableIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * Class to validate (at two different levels of thoroughness) the index for a BAM file.
 *
 * This class is [<em>not</em>] thread safe [because it is immutable].
 */
public class BamIndexValidator {

    public enum IndexValidationStringency {
        EXHAUSTIVE,
        LESS_EXHAUSTIVE,
        NONE
    }

    public static int exhaustivelyTestIndex(final SamReader reader) { // throws Exception {
        // look at all chunk offsets in the index, and in a linear index, to make sure they are valid
        final ReferenceBinsSource index = binningIndexOf(reader);
        if (index == null) {
            return 0;
        }
        int chunkCount = 0;
        int indexCount = 0;
        for (int i = 0; i < index.getReferenceCount(); i++) {
            final ReferenceBins reference = index.getReference(i);
            for (final Chunk c : allChunks(reference)) {
                chunkCount++;
                readFirstRecord(reader, c, "Exception in BamIndexValidator. Chunk " + c + " chunkCount=" + chunkCount);
            }
            // also seek to every position in the linear index, which a CSI does not have
            for (final long l : reference.getLinearIndex()) {
                if (l != 0) {
                    indexCount++;
                    readFirstRecord(
                            reader,
                            new Chunk(l, l + 1),
                            "Exception in BamIndexValidator. Linear index access failure " + l + " indexCount="
                                    + indexCount);
                }
            }
        }
        return chunkCount;
    }

    /**
     * A less time-consuming index validation that only looks at the first and last chunks of each reference in
     * the index, and the first and last entries of its linear index
     *
     * @param reader
     * @return # of chunks examined, or 0 if there is no browseable index for the reader
     */
    public static int lessExhaustivelyTestIndex(final SamReader reader) {
        final ReferenceBinsSource index = binningIndexOf(reader);
        if (index == null) {
            return 0;
        }
        int chunkCount = 0;
        for (int i = 0; i < index.getReferenceCount(); i++) {
            final ReferenceBins reference = index.getReference(i);
            final List<Chunk> chunks = allChunks(reference);
            for (final int chunkNo : firstAndLast(chunks.size())) {
                chunkCount++;
                readFirstRecord(
                        reader,
                        chunks.get(chunkNo),
                        "Exception querying chunk " + chunkNo + " from reference index " + i);
            }
            final long[] linearIndex = reference.getLinearIndex();
            for (final int window : firstAndLast(linearIndex.length)) {
                final long l = linearIndex[window];
                if (l != 0) {
                    readFirstRecord(
                            reader,
                            new Chunk(l, l + 1),
                            "Exception in BamIndexValidator. Linear index access failure " + l);
                }
            }
        }
        return chunkCount;
    }

    /** The first and last ordinals of a list of the given size: none if it is empty, one if they are the same. */
    private static List<Integer> firstAndLast(final int size) {
        if (size == 0) {
            return List.of();
        }
        return size == 1 ? List.of(0) : List.of(0, size - 1);
    }

    /** The reader's BAI or CSI, or null if it has neither. */
    private static ReferenceBinsSource binningIndexOf(final SamReader reader) {
        if (!reader.hasIndex()) {
            return null;
        }
        return reader.indexing()
                .getHtsIndex(BinningBAMIndex.class)
                .map(BinningBAMIndex::getSource)
                .orElse(null);
    }

    /** Every chunk of every bin of a reference, in bin order. */
    private static List<Chunk> allChunks(final ReferenceBins reference) {
        final List<Chunk> chunks = new ArrayList<>();
        for (int bin = 0; bin < reference.getBinCount(); bin++) {
            chunks.addAll(reference.getChunks(bin));
        }
        return chunks;
    }

    /** Reads the first record of a chunk, which fails if the chunk does not start at one. */
    private static void readFirstRecord(final SamReader reader, final Chunk chunk, final String failureMessage) {
        try (CloseableIterator<SAMRecord> records = reader.indexing().iterator(new BAMFileSpan(chunk))) {
            records.next();
        } catch (final Exception e) {
            throw new SAMException(failureMessage, e);
        }
    }
}
