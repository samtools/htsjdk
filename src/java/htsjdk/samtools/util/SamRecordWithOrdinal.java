/*
 * The MIT License
 *
 * Copyright (c) 2014 The Broad Institute
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
 * A little class to store the unique index associated with this record.  The index is determined as records are read in, so is in fact
 * gives the ordinal of the record in the input file.  All sub-classes should have a default constructor.  Additionally, all implementations
 * of this class must implement setResultState, as this class is typically used when wanting to return SAMRecord's in same input order,
 * but only when some computation (ex. duplicate marking) has been performed.
 */
public abstract class SamRecordWithOrdinal {
    private SAMRecord record;
    private long recordOrdinal;

    public SamRecordWithOrdinal() {
        this.record = null;
        this.recordOrdinal = -1;
    }

    public SamRecordWithOrdinal(final SAMRecord record, final long recordOrdinal) {
        this.record = record;
        this.recordOrdinal = recordOrdinal;
    }

    public SAMRecord getRecord() { return this.record; }
    public void setRecord(final SAMRecord record) { this.record = record; }
    public long getRecordOrdinal() { return this.recordOrdinal; }
    public void setRecordOrdinal(final long recordOrdinal) { this.recordOrdinal = recordOrdinal; }

    /** Set the result state on this record. */
    abstract public void setResultState(final boolean resultState);
}
