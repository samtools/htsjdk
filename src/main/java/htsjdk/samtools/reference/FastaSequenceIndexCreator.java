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
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.readers.AsciiLineReader;

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
     * {@link htsjdk.samtools.util.BlockCompressedIndex#createIndex(Path)}.
     *
     * @param fastaFile the FASTA file.
     *
     * @return a fai index.
     *
     * @throws SAMException for formatting errors.
     * @throws IOException  if an IO error occurs.
     */
    public static FastaSequenceIndex buildFromFasta(final Path fastaFile) throws IOException {
        try(final AsciiLineReader in = AsciiLineReader.from(IOUtil.openFileForReading(fastaFile))) {

            // sanity check reference format:
            // 1. Non-empty file
            // 2. Header name starts with >
            String previous = in.readLine();
            if (previous == null) {
                throw new SAMException("Cannot index empty file: " + fastaFile);
            } else if (previous.charAt(0) != '>') {
                throw new SAMException("Wrong sequence header: " + previous);
            }

            // initialize the sequence index
            int sequenceIndex = -1;
            // the location should be kept before iterating over the rest of the lines
            long location = in.getPosition();

            // initialize an empty index and the entry builder to null
            final FastaSequenceIndex index = new FastaSequenceIndex();
            FaiEntryBuilder entry = null;

            // read the lines two by two
            for (String line = in.readLine(); previous != null; line = in.readLine()) {
                // in this case, the previous line contains a header and the current line the first sequence
                if (previous.charAt(0) == '>') {
                    // first entry should be skipped; otherwise it should be added to the index
                    if (entry != null) index.add(entry.build());
                    // creates a new entry (and update sequence index)
                    entry = new FaiEntryBuilder(sequenceIndex++, previous, line, in.getLineTerminatorLength(), location);
                } else if (line != null && line.charAt(0) == '>') {
                    // update the location, next iteration the sequence will be handled
                    location = in.getPosition();
                } else if (line != null && !line.isEmpty()) {
                    // update in case it is not a blank-line
                    entry.updateWithSequence(line, in.getLineTerminatorLength());
                }
                // set the previous to the current line
                previous = line;
            }
            // add the last entry
            index.add(entry.build());

            // and return the index
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

        private FaiEntryBuilder(final int index, final String header, final String firstSequenceLine, final int endOfLineLength, final long location) {
            if (header == null || header.charAt(0) != '>') {
                throw new SAMException("Wrong sequence header: " + header);
            } else if (firstSequenceLine == null) {
                throw new SAMException("Empty sequences could not be indexed");
            }
            this.index = index;
            // parse the contig name (without the starting '>' and truncating white-spaces)
            this.contig =  SAMSequenceRecord.truncateSequenceName(header.substring(1).trim());
            this.location = location;
            this.basesPerLine = firstSequenceLine.length();
            this.endOfLineLength = endOfLineLength;
            this.size = firstSequenceLine.length();
            this.lessBasesFound = false;
        }

        private void updateWithSequence(final String sequence, final int endOfLineLength) {
            if (this.endOfLineLength != endOfLineLength) {
                throw new SAMException(String.format("Different end of line for the same sequence was found."));
            }
            if (sequence.length() > basesPerLine) {
                throw new SAMException(String.format("Sequence line for {} was longer than the expected length ({}): {}",
                        contig, basesPerLine, sequence));
            } else if (sequence.length() < basesPerLine) {
                if (lessBasesFound) {
                    throw new SAMException(String.format("Only last line could have less than {} bases for '{}' sequence, but at least two are different. Last sequence line: {}",
                            basesPerLine, contig, sequence));
                }
                lessBasesFound = true;
            }
            // update size
            this.size += sequence.length();
        }

        private FastaSequenceIndexEntry build() {
            return new FastaSequenceIndexEntry(contig, location, size, basesPerLine, basesPerLine + endOfLineLength, index);
        }
    }
}
