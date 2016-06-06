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

import static htsjdk.samtools.util.TypedRecordAndOffset.Type.BEGIN;
import static htsjdk.samtools.util.TypedRecordAndOffset.Type.END;

/**
 * Holds a SAMRecord plus the zero-based offset into that SAMRecord's bases and quality scores that corresponds
 * to the base and quality for the start of alignment block at the genomic position described by the AbstractLocusInfo.
 * This is implementation for ReadEndsIterator, field <code>type</code> added to indicate whether object
 * represents the start or the end of an alignment block.
 */
public class TypedRecordAndOffset extends AbstractRecordAndOffset {
    /**
     * Indicates whether object represents the start or the end of an alignment block.
     */
    private Type type;

    /**
     * For object with type END this fields holds the reference to object with type BEGIN for the read.
     */
    private TypedRecordAndOffset start;


    /**
     * @param record inner SAMRecord
     * @param offset from the start of the read
     * @param length of alignment block
     * @param refPos corresponding to read offset reference position
     */
    public TypedRecordAndOffset(SAMRecord record, int offset, int length, int refPos, Type type) {
        super(record, offset, length, refPos);
        this.type = type;
    }

    /**
     * @return <code>TypedRecordAndOffset</code> that represents the start of alignment block of the read
     * for object with type END. For object with type BEGIN will return null.
     */
    public TypedRecordAndOffset getStart() {
        return start;
    }


    /**
     * Method can be called only for an object with type END.
     * @param start <code>TypedRecordAndOffset</code> that represents the start of alignment block of the read,
     *              must have type <code>BEGIN</code>.
     */
    public void setStart(TypedRecordAndOffset start) {
        if (type != END) {
            throw new IllegalArgumentException("Start field can be set only for object with type END.");
        }
        if (start.type != BEGIN) {
            throw new IllegalArgumentException("The start object must have type BEGIN.");
        }
        this.start = start;
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
}
