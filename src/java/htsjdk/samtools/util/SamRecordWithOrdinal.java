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
