/*
 * The MIT License
 *
 * Copyright (c) 2015 The Broad Institute
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
package htsjdk.samtools;

import htsjdk.samtools.util.CloseableIterator;

/**
 * Abstract base class for all DownsamplingIterators that provides a uniform interface for recording
 * and reporting statistics bout how many records have been kept and discarded.
 *
 * A DownsamplingIterator is an iterator that takes another iterator of SAMRecords and filters out a
 * subset of those records in a random way, while ensuring that all records for a template (i.e. record name)
 * are either retained or discarded.  Strictly speaking the proportion parameter applies to templates,
 * though in most instances it is safe to think about it being applied to records.
 *
 * @author Tim Fennell
 */
public abstract class DownsamplingIterator implements CloseableIterator<SAMRecord> {
    private long recordsSeen;
    private long recordsAccepted;
    private double targetProportion;

    /** Constructs a downsampling iterator that aims to retain the targetProportion of reads. */
    public DownsamplingIterator(final double targetProportion) {
        if (targetProportion < 0) throw new IllegalArgumentException("targetProportion must be >= 0");
        if (targetProportion > 1) throw new IllegalArgumentException("targetProportion must be <= 1");
        this.targetProportion = targetProportion;
    }

    /** Does nothing. */
    @Override public void close() { /** No Op. */ }

    /** Returns the number of records seen, including accepted and discarded, since creation of the last call to resetStatistics. */
    public long getSeenCount() { return this.recordsSeen; }

    /** Returns the number of records returned since creation of the last call to resetStatistics. */
    public long getAcceptedCount() { return this.recordsAccepted; }

    /** Returns the number of records discarded since creation of the last call to resetStatistics. */
    public long getDiscardedCount() { return this.recordsSeen - this.recordsAccepted; }

    /** Gets the fraction of records discarded since creation or the last call to resetStatistics(). */
    public double getDiscardedFraction() { return getDiscardedCount() / (double) getSeenCount(); }

    /** Gets the fraction of records accepted since creation or the last call to resetStatistics(). */
    public double getAcceptedFraction() { return getAcceptedCount() / (double) getSeenCount(); }

    /** Resets the statistics for records seen/accepted/discarded. */
    public void resetStatistics() {
        this.recordsSeen = 0;
        this.recordsAccepted = 0;
    }

    /** Gets the target proportion of records that should be retained during downsampling. */
    public double getTargetProportion() {
        return targetProportion;
    }

    /** Method for subclasses to record a record as being discarded. */
    protected final void recordDiscardedRecord() { this.recordsSeen++; }

    /**
     * Method for subclasses to record a specific record as being accepted. Null may be passed if a record
     * was discarded but access to the object is no longer available.
     */
    protected final void recordAcceptedRecord() { this.recordsSeen++; this.recordsAccepted++; }

    /** Record one or more records as having been discarded. */
    protected final void recordDiscardRecords(final long n) {
        this.recordsSeen += n;
    }

    /** Record one or more records as having been discarded. */
    protected final void recordAcceptedRecords(final long n) {
        this.recordsSeen += n;
        this.recordsAccepted += n;
    }

    /**
     * Indicates whether or not the strategy implemented by this DownsamplingIterator makes any effort to
     * increase accuracy beyond random sampling (i.e. to reduce the delta between the requested proportion
     * of reads and the actually emitted proportion of reads).
     */
    public boolean isHigherAccuracy() {
        return false;
    }

    /** Not supported. */
    @Override public void remove() {
        throw new UnsupportedOperationException("remove() not supported in DownsamplingIterators");
    }
}
