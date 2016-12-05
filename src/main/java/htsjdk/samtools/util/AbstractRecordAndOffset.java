/*
 * The MIT License
 *
 * Copyright (c) 2016 The Broad Institute
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

import htsjdk.samtools.SAMRecord;

/**
 * Holds a SAMRecord plus the zero-based offset into that SAMRecord's bases and quality scores that corresponds
 * to the base and quality at the genomic position described the containing AbstractLocusInfo. One object represents
 * one base for <code>SamLocusIterator.RecordAndOffset</code> implementation or one alignment block of
 * <code>SAMRecord</code> for <code>TypedRecordAndOffset</code> implementation.
 * 
 * @author Darina_Nikolaeva@epam.com, EPAM Systems, Inc. <www.epam.com>
 * @author Mariia_Zueva@epam.com, EPAM Systems, Inc. <www.epam.com>
 */
public class AbstractRecordAndOffset {

    /**
     * A SAMRecord aligned to reference position
     */
    protected final SAMRecord record;
    /**
     * Zero-based offset in the read corresponding to the current position in AbstractLocusInfo
     */
    protected final int offset;

    /**
     * @param record inner SAMRecord
     * @param offset from the start of the read
     * @param length of alignment block
     * @param refPos corresponding to read offset reference position
     */
    public AbstractRecordAndOffset(final SAMRecord record, final int offset, int length, int refPos) {
        this(record, offset);
    }

    /**
     * @param record inner SAMRecord
     * @param offset from the start of the read
     */
    public AbstractRecordAndOffset(final SAMRecord record, final int offset) {
        this.offset = offset;
        this.record = record;
    }

    /**
     * @return offset of aligned read base from the start of the read.
     */
    public int getOffset() {
        return offset;
    }

    /**
     * @return inner <code>SAMRecord</code> object.
     */
    public SAMRecord getRecord() {
        return record;
    }

    /**
     * @return the read base according to <code>offset</code>.
     */
    public byte getReadBase() {
        return record.getReadBases()[offset];
    }

    /**
     * @return the length of alignment block represented by the object.
     */
    public int getLength() {
        return 1;
    }

    /**
     * @return the position in reference sequence, to which the start of alignment block is aligned.
     */
    public int getRefPos() {
        return -1;
    }

    /**
     * @return read name of inner SAMRecord.
     */
    public String getReadName() {
        return record.getReadName();
    }

    /**
     * @return array of base qualities of inner SAMRecord.
     */
    public byte[] getBaseQualities() {
        return record.getBaseQualities();
    }
    
    /**
     * @return the base quality according to <code>offset</code>.
     */
    public byte getBaseQuality() {
        return record.getBaseQualities()[offset];
    }

    protected void validateOffset(int offset, final byte[] array) {
        if (offset < 0 || offset >= array.length) {
            throw new IllegalArgumentException("The requested position is not covered by this " + this.getClass().getSimpleName() +
                    " object.");
        }
    }
}
