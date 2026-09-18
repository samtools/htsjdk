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
import htsjdk.index.ReferenceBins;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes a BAM index as human-readable text, so that two indexes can be compared with a text diff.
 * Used for testing only.
 */
final class TextualBAMIndexWriter {
    private TextualBAMIndexWriter() {}

    /**
     * @param index the index to describe
     * @param output the text file to write
     */
    static void write(final BinningIndex index, final Path output) {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(output))) {
            pw.println("n_ref=" + index.getReferenceCount());
            for (int reference = 0; reference < index.getReferenceCount(); reference++) {
                writeReference(pw, reference, index.getReference(reference));
            }
            final String noCoordinateCount = index.getNoCoordinateCount().isPresent()
                    ? Long.toString(index.getNoCoordinateCount().getAsLong())
                    : "null";
            pw.println("No Coordinate Count=" + noCoordinateCount);
            // A PrintWriter keeps quiet about a failed write unless asked.
            if (pw.checkError()) {
                throw new SAMException("Error writing " + output);
            }
        } catch (final IOException e) {
            throw new SAMException("Can't open output file " + output, e);
        }
    }

    private static void writeReference(final PrintWriter pw, final int reference, final ReferenceBins bins) {
        if (bins.getBinCount() == 0) {
            pw.println("Reference " + reference + " has n_bin=0");
            pw.println("Reference " + reference + " has n_intv=0");
            return;
        }
        final ReferenceBins.Metadata metadata = bins.getMetadata().orElse(null);
        pw.println("Reference " + reference + " has n_bin= " + (bins.getBinCount() + (metadata != null ? 1 : 0)));

        for (int i = 0; i < bins.getBinCount(); i++) {
            final int binNumber = bins.getBinNumber(i);
            final List<Chunk> chunks = bins.getChunks(i);
            pw.println("  Ref " + reference + " bin " + binNumber + " ("
                    + GenomicIndexUtil.getBinSummaryString(binNumber) + ") has n_chunk= " + chunks.size());
            if (chunks.isEmpty()) {
                pw.println();
            }
            for (final Chunk chunk : chunks) {
                pw.println("     Chunk: " + chunk + " start: " + Long.toString(chunk.getChunkStart(), 16) + " end: "
                        + Long.toString(chunk.getChunkEnd(), 16));
            }
        }

        // The metadata pseudo-bin, which a BAI numbers 37450
        pw.println("  Ref " + reference + " bin " + GenomicIndexUtil.MAX_BINS + " has n_chunk= "
                + (metadata == null ? 0 : 2));
        if (metadata == null) {
            pw.println();
        } else {
            pw.println("     Chunk:  start: " + Long.toString(metadata.firstOffset(), 16) + " end: "
                    + Long.toString(metadata.lastOffset(), 16));
            pw.println("     Chunk:  start: " + Long.toString(metadata.mappedCount(), 16) + " end: "
                    + Long.toString(metadata.unmappedCount(), 16));
        }

        final long[] linearIndex = bins.getLinearIndex();
        pw.println("Reference " + reference + " has n_intv= " + linearIndex.length);
        for (int window = 0; window < linearIndex.length; window++) {
            if (linearIndex[window] != 0) {
                pw.println("  Ref " + reference + " ioffset for " + window + " is "
                        + BlockCompressedFilePointerUtil.asAddressOffsetString(linearIndex[window]));
            }
        }
    }
}
