/*
 * The MIT License
 *
 * Copyright (c) 2019 Nils Homer
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
package htsjdk.samtools.util;

import htsjdk.samtools.SAMSequenceDictionary;
import java.io.InputStream;
import java.io.OutputStream;

public class IntervalCodec implements SortingCollection.Codec<Interval> {

    private final SAMSequenceDictionary dict;

    private final BinaryCodec binaryCodec = new BinaryCodec();

    /**
     * Creates a new binary codec to read or write.
     * @param dict the sequence dictionary associated with the intervals.
     */
    public IntervalCodec(final SAMSequenceDictionary dict) {
        this.dict = dict;
    }

    @Override
    public IntervalCodec clone() {
        return new IntervalCodec(this.dict);
    }


    /**
     * Sets the output stream that records will be written to.
     */
    @Override
    public void setOutputStream(final OutputStream os) {
        this.binaryCodec.setOutputStream(os);
    }

    /**
     * Sets the output stream that records will be written to.
     */
    public void setOutputStream(final OutputStream os, final String filename) {
        this.binaryCodec.setOutputStream(os);
        this.binaryCodec.setOutputFileName(filename);
    }

    /**
     * Sets the input stream that records will be read from.
     */
    @Override
    public void setInputStream(final InputStream is) {
        this.binaryCodec.setInputStream(is);
    }

    /**
     * Sets the input stream that records will be read from.
     */
    public void setInputStream(final InputStream is, final String filename) {
        this.binaryCodec.setInputStream(is);
        this.binaryCodec.setInputFileName(filename);
    }

    /**
     * Writes the interval to the output stream.
     * @param interval the interval to write.
     */
    @Override
    public void encode(final Interval interval) {
        final String name = interval.getName();
        this.binaryCodec.writeInt(this.dict.getSequenceIndex(interval.getContig()));
        this.binaryCodec.writeInt(interval.getStart());
        this.binaryCodec.writeInt(interval.getEnd());
        this.binaryCodec.writeBoolean(interval.isNegativeStrand());
        this.binaryCodec.writeBoolean(name != null);
        if (name != null) {
            this.binaryCodec.writeString(name, false, true);
        }
    }

    /**
     * Reads an interval from the input stream.
     * @return null if no more intervals, otherwise the next interval.
     */
    @Override
    public Interval decode() {
        final int sequenceIndex;
        try {
            sequenceIndex = this.binaryCodec.readInt();
        } catch (final RuntimeEOFException e) {
            return null;
        }
        return new Interval(
            this.dict.getSequence(sequenceIndex).getSequenceName(),
            this.binaryCodec.readInt(),
            this.binaryCodec.readInt(),
            this.binaryCodec.readBoolean(),
             (this.binaryCodec.readBoolean()) ? this.binaryCodec.readNullTerminatedString() : null
        );
    }
}