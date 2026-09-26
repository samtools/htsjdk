/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Daniel Gomez-Sanchez
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package htsjdk.samtools.reference;

import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.GZIIndex;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.readers.PositionalBufferedStream;
import htsjdk.tribble.readers.Utf8LineReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Static methods to create an {@link FastaSequenceIndex}.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public final class FastaSequenceIndexCreator {

    // cannot be instantiated because it is an utility class
    private FastaSequenceIndexCreator() {}

    /**
     * Creates a FASTA .fai index for the provided FASTA.
     *
     * @param fastaFile the file to build the index from.
     * @param overwrite if the .fai index already exists override it if {@code true}; otherwise, throws a {@link SAMException}.
     *
     * @throws SAMException if the fai file already exists or the file is malformed.
     * @throws IOException  if an IO error occurs.
     */
    public static void create(final Path fastaFile, final boolean overwrite) throws IOException {
        // get the index to write the file in
        final Path indexFile = ReferenceSequenceFileFactory.getFastaIndexFileName(fastaFile);
        if (!overwrite && Files.exists(indexFile)) {
            // throw an exception if the file already exists
            throw new SAMException("Index file " + indexFile + " already exists for " + fastaFile);
        }
        // build the index
        final FastaSequenceIndex index = buildFromFasta(fastaFile);
        index.write(indexFile);
    }

    /**
     * Builds a FastaSequenceIndex on the fly from a FASTA file.
     *
     * <p>Note: this also allows to create an index for a compressed file, but does not generate the
     * .gzi index required for use it with samtools. To generate that index, use
     * {@link GZIIndex#buildIndex(Path)}.
     *
     * <p>Blank lines may come before the first header, between sequences and at the end of the file. A
     * sequence with no bases, or a sequence line after a blank line, is an error.
     *
     * @param fastaFile the FASTA file.
     *
     * @return a fai index.
     *
     * @throws SAMException for formatting errors.
     * @throws IOException  if an IO error occurs.
     */
    public static FastaSequenceIndex buildFromFasta(final Path fastaFile) throws IOException, SAMException {
        // The .fai records offsets into the uncompressed sequence data, so the decompressed stream is
        // always wrapped as positional. Passing a BlockCompressedInputStream to Utf8LineReader.from
        // directly would make getPosition() return BGZF virtual file pointers instead.
        try (final Utf8LineReader in =
                Utf8LineReader.from(new PositionalBufferedStream(IOUtil.openFileForReading(fastaFile)))) {

            // Blank lines before the first header are skipped, as samtools skips them
            String line = in.readLine();
            while (line != null && line.isEmpty()) {
                line = in.readLine();
            }
            if (line == null) {
                throw new SAMException("Cannot index empty file: " + fastaFile);
            } else if (line.charAt(0) != '>') {
                throw new SAMException("Wrong sequence header: " + line);
            }

            final FastaSequenceIndex index = new FastaSequenceIndex();
            int sequenceIndex = 0;

            // Each pass indexes one sequence, starting with `line` holding its header
            while (line != null) {
                final long location = in.getPosition();
                final String firstSequenceLine = in.readLine();
                final FaiEntryBuilder entry = new FaiEntryBuilder(
                        sequenceIndex++, line, firstSequenceLine, in.getLineTerminatorLength(), location);

                line = in.readLine();
                while (line != null && !line.isEmpty() && line.charAt(0) != '>') {
                    entry.updateWithSequence(line, in.getLineTerminatorLength());
                    line = in.readLine();
                }

                // A blank line ends the sequence. A .fai entry cannot describe bases after a gap, so only more
                // blank lines, the next header or the end of the file may follow it.
                while (line != null && line.isEmpty()) {
                    line = in.readLine();
                }
                if (line != null && line.charAt(0) != '>') {
                    throw new SAMException(String.format(
                            "A sequence line follows a blank line in sequence '%s'; a blank line may be followed "
                                    + "only by a header, another blank line or the end of the file",
                            entry.contig));
                }

                index.add(entry.build());
            }

            return index;
        }
    }

    // utility class for building the FastaSequenceIndexEntry
    private static class FaiEntryBuilder {
        private final int index;
        private final String contig;
        private final long location;
        // the bytes per line is the bases per line plus the length of the end of the line
        private final int basesPerLine;
        private final int endOfLineLength;

        // the size is updated for each line in the input using updateWithSequence
        private long size;
        // flag to check if the supposedly last line was already reached
        private boolean lessBasesFound;

        private FaiEntryBuilder(
                final int index,
                final String header,
                final String firstSequenceLine,
                final int endOfLineLength,
                final long location) {
            if (header == null || header.charAt(0) != '>') {
                throw new SAMException("Wrong sequence header: " + header);
            }
            this.index = index;
            // parse the contig name (without the starting '>' and truncating white-spaces)
            this.contig =
                    SAMSequenceRecord.truncateSequenceName(header.substring(1).trim());
            // A header followed by another header, a blank line or the end of the file has no bases. samtools
            // leaves such a sequence out of its index, but that would make the index disagree with the dictionary.
            if (firstSequenceLine == null || firstSequenceLine.isEmpty() || firstSequenceLine.charAt(0) == '>') {
                throw new SAMException(
                        String.format("Empty sequences could not be indexed: sequence '%s' has no bases", contig));
            }
            this.location = location;
            this.basesPerLine = firstSequenceLine.length();
            this.endOfLineLength = endOfLineLength;
            this.size = firstSequenceLine.length();
            this.lessBasesFound = false;
        }

        private void updateWithSequence(final String sequence, final int endOfLineLength) {
            // Only the last line of the file can have no terminator (length 0), so that is not a mismatch
            if (endOfLineLength != 0 && this.endOfLineLength != endOfLineLength) {
                throw new SAMException(String.format("Different end of line for the same sequence was found."));
            }
            if (sequence.length() > basesPerLine) {
                throw new SAMException(String.format(
                        "Sequence line for %s was longer than the expected length (%d): %s",
                        contig, basesPerLine, sequence));
            } else if (sequence.length() < basesPerLine) {
                if (lessBasesFound) {
                    throw new SAMException(String.format(
                            "Only last line could have less than %d bases for '%s' sequence, but at least two are different. Last sequence line: %s",
                            basesPerLine, contig, sequence));
                }
                lessBasesFound = true;
            }
            // update size
            this.size += sequence.length();
        }

        private FastaSequenceIndexEntry build() {
            return new FastaSequenceIndexEntry(
                    contig, location, size, basesPerLine, basesPerLine + endOfLineLength, index);
        }
    }
}
