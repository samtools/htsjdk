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

import static htsjdk.samtools.util.EdgingRecordAndOffset.Type.BEGIN;
import static htsjdk.samtools.util.EdgingRecordAndOffset.Type.END;

/**
 * Holds a SAMRecord plus the zero-based offset into that SAMRecord's bases and quality scores that corresponds
 * to the base and quality for the start of alignment block at the genomic position described by the AbstractLocusInfo.
 * This is implementation for EdgeReadIterator, field <code>type</code> added to indicate whether object
 * represents the start or the end of an alignment block.
 * <p>
 * Types BEGIN and END are used in EdgeReadIterator to distinguish starting and ending of the alignment block
 * as for each alignment block two objects of <code>EdgingRecordAndOffset</code> are created with two different types.
 * The main idea of using EdgeReadIterator is to process alignment block starting from locus where BEGIN type occurs,
 * aggregate information per locus and keep it until END type occurs, then remove alignment block from consideration.
 * 
 * @author Darina_Nikolaeva@epam.com, EPAM Systems, Inc. <www.epam.com>
 * 
 */
public class EdgingRecordAndOffset extends AbstractRecordAndOffset {
    /**
     * Length of alignment block of the read
     */
    private final int length;
    /**
     * A reference position to which read offset is aligned.
     */
    private final int refPos;
    /**
     * Indicates whether object represents the start or the end of an alignment block.
     */
    private Type type;
    private int hash = 0;

    /**
     * For object with type END this fields holds the reference to object with type BEGIN for the read.
     */
    private EdgingRecordAndOffset start;

    /**
     * @param record inner SAMRecord
     * @param offset from the start of the read
     * @param length of alignment block
     * @param refPos corresponding to read offset reference position
     */
    public EdgingRecordAndOffset(SAMRecord record, int offset, int length, int refPos, Type type) {
        super(record, offset);
        if (length > record.getReadLength()) {
            throw new IllegalArgumentException("Block length cannot be larger than whole read length");
        }
        this.type = type;
        this.length = length;
        this.refPos = refPos;
    }

    /**
     * @return <code>TypedRecordAndOffset</code> that represents the start of alignment block of the read
     * for object with type END. For object with type BEGIN will return null.
     */
    public EdgingRecordAndOffset getStart() {
        return start;
    }


    /**
     * Method can be called only for an object with type END.
     * @param start <code>TypedRecordAndOffset</code> that represents the start of alignment block of the read,
     *              must have type <code>BEGIN</code>.
     */
    public void setStart(EdgingRecordAndOffset start) {
        if (type != END) {
            throw new IllegalArgumentException("Start field can be set only for object with type END.");
        }
        if (start.type != BEGIN) {
            throw new IllegalArgumentException("The start object must have type BEGIN.");
        }
        this.start = start;
    }
    
    /**
     * @param position in the reference
     * @return base quality of a read base, corresponding to a given reference position
     */
    @Override
    public byte getBaseQuality(int position) {
        int rOffset = getRelativeOffset(position);
        byte[] baseQualities = record.getBaseQualities();
        validateOffset(rOffset, baseQualities);
        return baseQualities[rOffset];
    }
    
    /**
     * @return the length of alignment block represented by the object.
     */
    @Override
    public int getLength() {
        return length;
    }

    /**
     * @return the position in reference sequence, to which the start of alignment block is aligned.
     */
    @Override
    public int getRefPos() {
        return refPos;
    }


    /**
     * @return type of object
     */
    public Type getType() {
        return type;
    }

    /**
     * Describes the type of <code>TypedRecordAndOffset</code>, whether it represents the start or the end of
     * an alignment block.
     */
    public enum Type {
        BEGIN, END
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

    private int getRelativeOffset(int position) {
        return position - refPos + offset;
    }

}
