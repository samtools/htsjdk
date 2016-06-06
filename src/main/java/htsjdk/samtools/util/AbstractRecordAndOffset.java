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

package htsjdk.samtools.util;

import htsjdk.samtools.SAMRecord;

/**
 * Holds a SAMRecord plus the zero-based offset into that SAMRecord's bases and quality scores that corresponds
 * to the base and quality at the genomic position described the containing AbstractLocusInfo. One object represents
 * one base for <code>SamLocusIterator.RecordAndOffset</code> implementation or one alignment block of
 * <code>SAMRecord</code> for <code>TypedRecordAndOffset</code> implementation.
 */
public class AbstractRecordAndOffset {

    /**
     * A SAMRecord aligned to reference position
     */
    private final SAMRecord record;
    /**
     * Zero-based offset into the read corresponding to the current position in AbstractLocusInfo
     */
    private final int offset;
    /**
     * Length of alignment block of the read
     */
    private final int length;
    /**
     * A reference position to which read offset is aligned.
     */
    private final int refPos;
    private int hash = 0;

    /**
     * @param record inner SAMRecord
     * @param offset from the start of the read
     * @param length of alignment block
     * @param refPos corresponding to read offset reference position
     */
    public AbstractRecordAndOffset(final SAMRecord record, final int offset, int length, int refPos) {
        validateIndex(offset, record.getBaseQualities());
        if (length > record.getReadLength()) {
            throw new IllegalArgumentException("Block length cannot be larger than whole read length");
        }
        this.offset = offset;
        this.record = record;
        this.length = length;
        this.refPos = refPos;
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
     * @return the base quality according to <code>offset</code>.
     */
    public byte getBaseQuality() {
        return record.getBaseQualities()[offset];
    }

    /**
     * @return the length of alignment block represented by the object.
     */
    public int getLength() {
        return length;
    }

    /**
     * @return the position in reference sequence, to which the start of alignment block is aligned.
     */
    public int getRefPos() {
        return refPos;
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
     * @param position in the reference
     * @return base quality of a read base, corresponding to a given reference position
     */
    public byte getBaseQuality(int position) {
        int index = getRelativeOffset(position);
        byte[] baseQualities = record.getBaseQualities();
        validateIndex(index, baseQualities);
        return baseQualities[index];
    }

    private void validateIndex(int index, byte[] array) {
        if (index < 0 || index >= array.length) {
            throw new IllegalArgumentException("The requested position is not covered by this AbstractRecordAndOffset" +
                    " object.");
        }
    }

    private int getRelativeOffset(int position) {
        if (refPos == -1) {
            return position - offset;
        }
        return position - refPos + offset;
    }

    @Override
    public int hashCode() {
        if (hash != 0) return hash;
        hash = record.hashCode();
        hash = 31 * hash + length;
        hash = 31 * hash + offset;
        hash = 31 * hash + refPos;
        return hash;
    }

}