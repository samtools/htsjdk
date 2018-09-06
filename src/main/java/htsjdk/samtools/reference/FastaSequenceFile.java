/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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

package htsjdk.samtools.reference;

import htsjdk.samtools.Defaults;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.FastLineReader;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Implementation of ReferenceSequenceFile for reading from FASTA files.
 *
 * @author Tim Fennell
 */
public class FastaSequenceFile extends AbstractFastaSequenceFile {

    private final boolean truncateNamesAtWhitespace;
    private final SeekableStream seekableStream;
    private FastLineReader in;
    private int sequenceIndex = -1;
    private final byte[] basesBuffer = new byte[Defaults.NON_ZERO_BUFFER_SIZE];


    /** Constructs a FastaSequenceFile that reads from the specified file. */
    public FastaSequenceFile(final File file, final boolean truncateNamesAtWhitespace) {
        this(IOUtil.toPath(file), truncateNamesAtWhitespace);
    }

    /** Constructs a FastaSequenceFile that reads from the specified file. */
    public FastaSequenceFile(final Path path, final boolean truncateNamesAtWhitespace) {
        super(path);
        this.truncateNamesAtWhitespace = truncateNamesAtWhitespace;
        this.seekableStream = null;
        this.in = new FastLineReader(IOUtil.openFileForReading(path));
    }

    /**
     * Constructs a FastaSequenceFile that reads from the specified stream (which must not be compressed, i.e.
     * the caller is responsible for decompressing the stream).
     */
    public FastaSequenceFile(String source, final SeekableStream seekableStream, SAMSequenceDictionary dictionary, final boolean truncateNamesAtWhitespace) {
        super(null, source, dictionary);
        this.truncateNamesAtWhitespace = truncateNamesAtWhitespace;
        this.seekableStream = seekableStream;
        this.in = new FastLineReader(seekableStream);
    }

    /**
     * It's good to call this to free up memory.
     */
    @Override
    public void close() {
        in.close();
    }

    @Override
    public ReferenceSequence nextSequence() {
        this.sequenceIndex += 1;

        // Read the header line
        final String name = readSequenceName();
        if (name == null) {
            close();
            return null;
        }

        // Read the sequence
        final int knownLength = (this.sequenceDictionary == null) ? -1 : this.sequenceDictionary.getSequence(this.sequenceIndex).getSequenceLength();
        final byte[] bases = readSequence(knownLength);

        return new ReferenceSequence(name, this.sequenceIndex, bases);
    }

    @Override
    public void reset() {
        this.sequenceIndex = -1;
        if (getPath() != null) {
            this.in.close();
            this.in = new FastLineReader(IOUtil.openFileForReading(getPath()));
        } else {
            try {
                this.seekableStream.seek(0);
            } catch (IOException e) {
                throw new SAMException("Problem seeking to start of stream during reset", e);
            }
            this.in = new FastLineReader(this.seekableStream);
        }
    }

    private String readSequenceName() {
        in.skipNewlines();
        if (in.eof()) {
            return null;
        }
        final byte b = in.getByte();
        if (b != '>') {
            throw new SAMException("Format exception reading FASTA " + getSource() + ".  Expected > but saw chr(" +
            b + ") at start of sequence with index " + this.sequenceIndex);
        }
        final byte[] nameBuffer = new byte[4096];
        int nameLength = 0;
        do {
            if (in.eof()) {
                break;
            }
            nameLength += in.readToEndOfOutputBufferOrEoln(nameBuffer, nameLength);
            if (nameLength == nameBuffer.length && !in.atEoln()) {
                throw new SAMException("Sequence name too long in FASTA " + getSource());
            }
        } while (!in.atEoln());
        if (nameLength == 0) {
            throw new SAMException("Missing sequence name in FASTA " + getSource());
        }
        String name = StringUtil.bytesToString(nameBuffer, 0, nameLength).trim();
        if (truncateNamesAtWhitespace) {
            name = SAMSequenceRecord.truncateSequenceName(name);
        }
        return name;
    }

    /**
     * Read bases from input
     * @param knownLength For performance:: -1 if length is not known, otherwise the length of the sequence.
     * @return ASCII bases for sequence
     */
    private byte[] readSequence(final int knownLength) {
        byte[] bases = (knownLength == -1) ?  basesBuffer : new byte[knownLength] ;

        int sequenceLength = 0;
        while (!in.eof()) {
            final boolean sawEoln = in.skipNewlines();
            if (in.eof()) {
                break;
            }
            if (sawEoln && in.peekByte() == '>') {
                break;
            }
            sequenceLength += in.readToEndOfOutputBufferOrEoln(bases, sequenceLength);
            while (sequenceLength > 0 && Character.isWhitespace(StringUtil.byteToChar(bases[sequenceLength - 1]))) {
                --sequenceLength;
            }
            if (sequenceLength == knownLength) {
                // When length is known, make sure there is no trailing whitespace that hasn't been traversed.
                skipToEoln();
                break;
            }
            if (sequenceLength == bases.length) {
                    final byte[] tmp = new byte[bases.length * 2];
                    System.arraycopy(bases, 0, tmp, 0, sequenceLength);
                    bases = tmp;
            }
        }

        // And lastly resize the array down to the right size
        if (sequenceLength != bases.length || bases == basesBuffer) {
            final byte[] tmp = new byte[sequenceLength];
            System.arraycopy(bases, 0, tmp, 0, sequenceLength);
            bases = tmp;
        }
        return bases;
    }

    private void skipToEoln() {
        byte[] ignoreBuffer = new byte[1024];
        while (!in.eof() && !in.atEoln()) {
            in.readToEndOfOutputBufferOrEoln(ignoreBuffer, 0);
        }
    }
}
