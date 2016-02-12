/*
 * The MIT License
 *
 * Copyright (c) 2015 Tim Fennell
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

/**
 * A DownsamplingIterator that attempts to provide very high accuracy (minimizing the difference between emitted proportion
 * and requested proportion) at the expense of using memory proportional to the number of reads in the incoming stream.
 *
 * @author Tim Fennell
 */
class HighAccuracyDownsamplingIterator extends DownsamplingIterator {
    private final Iterator<SAMRecord> underlyingIterator;
    private final Random random;
    private SAMRecord nextRecord;
    private final Map<String, Boolean> decisions = new HashMap<String, Boolean>();

    private double targetAccuracy = 0.0001;
    private long totalTemplates, keptTemplates;
    private Iterator<SAMRecord> bufferedRecords = new ArrayList<SAMRecord>().iterator();
    private Set<String> bufferedRecordsToKeep;

    /** Override method to make it clear that this iterator attempts to provide a higher accuracy of downsampling. */
    @Override public boolean isHigherAccuracy() {
        return true;
    }

    /** Constructs a downsampling iterator upon the supplied iterator, using the Random as the source of randomness. */
    HighAccuracyDownsamplingIterator(final Iterator<SAMRecord> iterator, final double proportion, final int seed) {
        super(proportion);
        this.underlyingIterator = iterator;
        this.random = new Random(seed);
    }

    /**
     * Sets the target accuracy of the downsampling iterator.  The value should be thought of as
     * probability +/- accuracy.  So a value of 0.001 would instruct the downsampling iterator to
     * attempt to guarantee at accuracy to within 0.1%.  The downsampler will need to buffer reads
     * for 1/accuracy templates, so setting this to extremely small numbers is not advisable.
     */
    public DownsamplingIterator setTargetAccuracy(final double accuracy) {
        if (accuracy >= 1 || accuracy <= 1d/Integer.MAX_VALUE) throw new IllegalArgumentException("Illegal value. Must be 1/MAX_INT < accuracy < 1");
        this.targetAccuracy = accuracy;
        return this;
    }

    /** Returns true if there is another record available post-downsampling, false otherwise. */
    @Override public boolean hasNext() {
        return this.nextRecord != null || advance();
    }

    /** Returns the next record from the iterator, or throws an exception if there is no next record. */
    @Override public SAMRecord next() {
        if (this.nextRecord == null) {
            throw new NoSuchElementException("Call to next() when hasNext() == false");
        }
        else {
            final SAMRecord retval = this.nextRecord;
            advance();
            return retval;
        }
    }

    /** Returns the underlying iterator so that subclasses may manipulate it. */
    protected Iterator<SAMRecord> getUnderlyingIterator() {
        return this.underlyingIterator;
    }

    /**
     * Clears the current record and attempts to advance through the underlying iterator until a
     * record is kept during downsampling.  If no more records are kept and the end of the input
     * is reached this.nextRecord will be null.
     *
     * @return true if a record is available after advancing, false otherwise
     */
    protected boolean advance() {
        this.nextRecord = null;

        while (this.nextRecord == null && (this.bufferedRecords.hasNext() || bufferNextChunkOfRecords(getTargetProportion(), this.targetAccuracy))) {
            final SAMRecord rec = this.bufferedRecords.next();
            final String key = rec.getReadName();
            final Boolean previous = decisions.get(key);
            final boolean keepThisRecord;

            if (previous == null) {
                keepThisRecord = this.bufferedRecordsToKeep.contains(rec.getReadName());
                decisions.put(key, keepThisRecord);
            }
            else {
                keepThisRecord = previous;
            }

            if (keepThisRecord) {
                this.nextRecord = rec;
                recordAcceptedRecord();
            }
            else {
                recordDiscardedRecord();
            }
        }

        return this.nextRecord != null;
    }

    /**
     * Buffers reads until either the end of the file is reached or enough reads have been buffered such
     * that downsampling can be performed to the desired target accuracy.  Once reads have been buffered,
     * template names are randomly sampled out for discarding until the desired number of reads have
     * been discarded.
     *
     * @return True if one or more reads have been buffered, false otherwise
     */
    protected boolean bufferNextChunkOfRecords(final double proportion, final double accuracy) {
        final int templatesToRead = (int) Math.ceil(1 / accuracy);
        final Set<String> names = new HashSet<String>();
        final List<SAMRecord> recs = new ArrayList<SAMRecord>(templatesToRead);

        readFromUnderlyingIterator(recs, names, templatesToRead);

        // Determine how many templates to keep/discard
        final int templatesRead = names.size();
        final int templatesToKeep = calculateTemplatesToKeep(templatesRead, proportion);

        // Randomly shuffle a list of all the template names, and then remove some from the set
        final int templatesToDiscard = templatesRead - templatesToKeep;
        final List<String> tmp    = new ArrayList<String>(names);
        Collections.shuffle(tmp, this.random);
        for (int i = 0; i < templatesToDiscard; ++i) names.remove(tmp.get(i));

        // Set all the instance state so that advance()/next() get what they need
        this.bufferedRecordsToKeep = names;
        this.bufferedRecords = recs.iterator();
        this.totalTemplates += templatesRead;
        this.keptTemplates  += names.size();
        return !recs.isEmpty();
    }

    /**
     * Calculates the number of templates to keep in a specific batch of reads having just read templatesRead reads
     * and wanting to keep proportion of them.  Rounds the final number up or down based on whether, to this point,
     * the iterator is under or over it's goal proportion.
     *
     * Implemented as second method to allow ChainedDownsamplingIterator to tamper with the strategy!
     */
    protected int calculateTemplatesToKeep(final int templatesRead, final double proportion) {
        final double rawTemplatesToKeep = templatesRead * proportion;
        return (keptTemplates / (double) totalTemplates < proportion)
                ? (int) Math.ceil(rawTemplatesToKeep) : (int) Math.floor(rawTemplatesToKeep);
    }

    /**
     * Reads from the underlying iterator until it has observed templatesToRead templates (i.e. read names) that it has not yet
     * observed, so that templatesToRead new keep/reject decisions can be made.  The records that are read are placed into recs
     * and _novel_ template names are placed into names.
     */
    protected void readFromUnderlyingIterator(final List<SAMRecord> recs, final Set<String> names, final int templatesToRead) {
        while (this.underlyingIterator.hasNext() && names.size() < templatesToRead) {
            final SAMRecord rec = this.underlyingIterator.next();
            recs.add(rec);

            if (this.decisions.containsKey(rec.getReadName())) continue;
            names.add(rec.getReadName());
        }
    }
}
