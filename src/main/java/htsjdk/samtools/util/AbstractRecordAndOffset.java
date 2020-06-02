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
     * Classifies whether the given event is a match, insertion, or deletion. This is deliberately not expressed
     * as CIGAR operators, since there is no knowledge of the CIGAR string at the time that this is determined.
     */
    public enum AlignmentType {
        Match,
        Insertion,
        Deletion,
    }

    /**
     * A SAMRecord aligned to reference position
     */
    protected final SAMRecord record;
    /**
     * Zero-based offset in the read corresponding to the current position in AbstractLocusInfo
     */
    protected final int offset;

    /**
     * The {@link AlignmentType} of this object, which classifies whether the given event is a match, insertion, or
     * deletion when queried from a {@link SamLocusIterator}.
     */
    protected final AlignmentType alignmentType;

    /**
     * @param record inner SAMRecord
     * @param offset from the start of the read
     */
    public AbstractRecordAndOffset(final SAMRecord record, final int offset) {
        this.offset = offset;
        this.record = record;
        this.alignmentType = AlignmentType.Match;
    }

    /**
     * @param record inner SAMRecord
     * @param offset from the start of the read
     * @param alignmentType The {@link AlignmentType} of this object, which is used when queried in
     *                      a {@link SamLocusIterator}.
     */
    public AbstractRecordAndOffset(final SAMRecord record, final int offset, final AlignmentType alignmentType) {
        this.offset = offset;
        this.record = record;
        this.alignmentType = alignmentType;
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
     * The {@link AlignmentType} of this object, which classifies whether the given event is a match, insertion, or
     * deletion when queried from a {@link SamLocusIterator}.
     */
    public AlignmentType getAlignmentType() {
        return alignmentType;
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
            throw new IllegalArgumentException("The requested position is not covered by this " + this.getClass().getSimpleName() + " object. "
                    + "\n Offset = " + offset + " Array length = " + array.length
                    + "\n Record is: " + getRecord().toString());
        }
    }
}
