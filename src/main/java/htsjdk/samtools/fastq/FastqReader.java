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
package htsjdk.samtools.fastq;

import htsjdk.samtools.SAMException;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.StringUtil;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Reads a FASTQ file with four lines per record.
 * WARNING: Despite the fact that this class implements Iterable, calling iterator() method does not
 * start iteration from the beginning of the file.  Developers should probably not call iterator()
 * directly.  It is provided so that this class can be used in Java for-each loop.
 */
public class FastqReader implements Iterator<FastqRecord>, Iterable<FastqRecord>, Closeable {
    final private File fastqFile;
    final private BufferedReader reader;
    private FastqRecord nextRecord;
    private int line=1;

    final private boolean skipBlankLines;

    public FastqReader(final File file) {
        this(file,false);
    }
    
    /**
     * Constructor
     * @param file of FASTQ to read read. Will be opened with htsjdk.samtools.util.IOUtil.openFileForBufferedReading
     * @param skipBlankLines should we skip blank lines ?
     */
    public FastqReader(final File file, final boolean skipBlankLines) {
        this.skipBlankLines=skipBlankLines;
        fastqFile = file;
        reader = IOUtil.openFileForBufferedReading(fastqFile);
        nextRecord = readNextRecord();
    }

    public FastqReader(final BufferedReader reader) {
        this(null, reader);
    }

    /**
     * Constructor
     * @param file Name of FASTQ being read, or null if not known.
     * @param reader input reader . Will be closed by the close method
     * @param skipBlankLines should we skip blank lines ?
     */
    public FastqReader(final File file, final BufferedReader reader,boolean skipBlankLines) {
        this.fastqFile = file;
        this.reader = reader;
        this.nextRecord = readNextRecord();
        this.skipBlankLines = skipBlankLines;
    }

    public FastqReader(final File file, final BufferedReader reader) {
        this(file,reader,false);
    }

    private FastqRecord readNextRecord() {
        try {

            // Read sequence header
            final String seqHeader = readLineConditionallySkippingBlanks();
            if (seqHeader == null) return null ;
            if (StringUtil.isBlank(seqHeader)) {
                throw new SAMException(error("Missing sequence header"));
            }
            if (!seqHeader.startsWith(FastqConstants.SEQUENCE_HEADER)) {
                throw new SAMException(error("Sequence header must start with "+ FastqConstants.SEQUENCE_HEADER+": "+seqHeader));
            }

            // Read sequence line
            final String seqLine = readLineConditionallySkippingBlanks();
            checkLine(seqLine,"sequence line");

            // Read quality header
            final String qualHeader = readLineConditionallySkippingBlanks();
            checkLine(qualHeader,"quality header");
            if (!qualHeader.startsWith(FastqConstants.QUALITY_HEADER)) {
                throw new SAMException(error("Quality header must start with "+ FastqConstants.QUALITY_HEADER+": "+qualHeader));
            }

            // Read quality line
            final String qualLine = readLineConditionallySkippingBlanks();
            checkLine(qualLine,"quality line");

            // Check sequence and quality lines are same length
            if (seqLine.length() != qualLine.length()) {
                throw new SAMException(error("Sequence and quality line must be the same length"));
            }

            final FastqRecord frec = new FastqRecord(seqHeader.substring(1, seqHeader.length()), seqLine,
                    qualHeader.substring(1, qualHeader.length()), qualLine);
            line += 4 ;
            return frec ;

        } catch (IOException e) {
            throw new SAMException(String.format("Error reading fastq '%s'", getAbsolutePath()), e);
        }
    }

    public boolean hasNext() { return nextRecord != null; }

    public FastqRecord next() {
        if (!hasNext()) {
            throw new NoSuchElementException("next() called when !hasNext()");
        }
        final FastqRecord rec = nextRecord;
        nextRecord = readNextRecord();
        return rec;
    }

    public void remove() { throw new UnsupportedOperationException("Unsupported operation"); }

    /**
     * WARNING: Despite the fact that this class implements Iterable, calling iterator() method does not
     * start iteration from the beginning of the file.  Developers should probably not call iterator()
     * directly.  It is provided so that this class can be used in Java for-each loop.
     */
    public Iterator<FastqRecord> iterator() { return this; }

    public int getLineNumber() { return line ; }


    /**
     * @return Name of FASTQ being read, or null if not known.
     */
    public File getFile() { return fastqFile ; }

    @Override
    public void close() {
        try {
            reader.close();
        } catch (IOException e) {
            throw new SAMException("IO problem in fastq file "+getAbsolutePath(), e);
        }
    }

    private void checkLine(final String line, final String kind) {
        if (line == null) {
            throw new SAMException(error("File is too short - missing "+kind+" line"));
        }
        if (StringUtil.isBlank(line)) {
            throw new SAMException(error("Missing "+kind));
        }
    }

    private String error(final String msg) {
        return msg + " at line "+line+" in fastq "+getAbsolutePath();
    }

    private String getAbsolutePath() {
        if (fastqFile == null) return "";
        else return fastqFile.getAbsolutePath();
    }

    private String readLineConditionallySkippingBlanks() throws IOException {
        String line;
        do {
            line = reader.readLine();
            if (line == null) return line;
        } while(skipBlankLines && StringUtil.isBlank(line));
        return line;
    }

    @Override
    public String toString() {
        return "FastqReader["+(this.fastqFile == null?"":this.fastqFile)+ " Line:"+getLineNumber()+"]";
    }
}
