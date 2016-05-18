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
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.ProgressLogger;
import htsjdk.samtools.util.SortingCollection;

import java.io.File;
import java.util.Collections;

/**
 * An iterator of sets of duplicates.  Duplicates are defined currently by the ordering in
 * SAMRecordDuplicateComparator.
 * <p/>
 * If the input records are not pre-sorted according to the duplicate ordering, the records
 * will be sorted on-the-fly.  This may require extra memory or disk to buffer records, and
 * also computational time to perform the sorting.
 *
 * @author nhomer
 */
public class DuplicateSetIterator implements CloseableIterator<DuplicateSet> {

    private final CloseableIterator<SAMRecord> wrappedIterator;

    private DuplicateSet duplicateSet = null;

    private final SAMRecordDuplicateComparator comparator;

    public DuplicateSetIterator(final CloseableIterator<SAMRecord> iterator, final SAMFileHeader header) {
        this(iterator, header, false);
    }

    public DuplicateSetIterator(final CloseableIterator<SAMRecord> iterator,
                                final SAMFileHeader header,
                                final boolean preSorted) {
        this(iterator, header, preSorted, null);
    }

    public DuplicateSetIterator(final CloseableIterator<SAMRecord> iterator,
                                final SAMFileHeader header,
                                final boolean preSorted,
                                final SAMRecordDuplicateComparator comparator) {
        this(iterator, header, preSorted, comparator, null);
    }

    /**
     * Allows the user of this iterator to skip the sorting of the input if the input is already sorted.  If the records are said to be
     * sorted but not actually sorted in the correct order, an exception during iteration will be thrown.  Progress information will
     * be printed for sorting of the input if `log` is provided.
     */
    public DuplicateSetIterator(final CloseableIterator<SAMRecord> iterator,
                                final SAMFileHeader header,
                                final boolean preSorted,
                                final SAMRecordDuplicateComparator comparator,
                                final Log log) {
        this.comparator = (comparator == null) ? new SAMRecordDuplicateComparator(Collections.singletonList(header)) : comparator;

        if (preSorted) {
            this.wrappedIterator = iterator;
        } else {
            ProgressLogger progressLogger = null;
            if (log != null) {
                progressLogger = new ProgressLogger(log, 100000);
                log.info("Duplicate set iterator initializing.");
            }

            // Sort it!
            final int maxRecordsInRam = SAMFileWriterImpl.getDefaultMaxRecordsInRam();
            final File tmpDir = new File(System.getProperty("java.io.tmpdir"));
            final SortingCollection<SAMRecord> alignmentSorter = SortingCollection.newInstance(SAMRecord.class,
                    new BAMRecordCodec(header), this.comparator,
                    maxRecordsInRam, tmpDir);

            while (iterator.hasNext()) {
                final SAMRecord record = iterator.next();
                alignmentSorter.add(record);
                if (progressLogger != null) progressLogger.record(record);
            }
            iterator.close();

            this.wrappedIterator = alignmentSorter.iterator();
            if (log != null) log.info("Duplicate set iterator initialized.");
        }

        this.duplicateSet = new DuplicateSet(this.comparator);

        if (hasNext()) {
            this.duplicateSet.add(this.wrappedIterator.next());
        }

    }

    @Deprecated
    /** Do not use this method as the first duplicate set will not be compared with this scoring strategy.
      * Instead, provide a comparator to the constructor that has the scoring strategy set. */
    public void setScoringStrategy(final DuplicateScoringStrategy.ScoringStrategy scoringStrategy) {
        this.comparator.setScoringStrategy(scoringStrategy);
    }

    public DuplicateSet next() {
        DuplicateSet duplicateSet = null;

        int cmp = 0;

        while (0 == cmp) {
            if (!wrappedIterator.hasNext()) { // no more!
                duplicateSet = this.duplicateSet;
                this.duplicateSet = new DuplicateSet(this.comparator);
                break;
            } else {
                // get another one
                final SAMRecord record = this.wrappedIterator.next();

                // assumes that the duplicate set always has at least one record inside!
                final SAMRecord representative = this.duplicateSet.getRepresentative();

                if (representative.getReadUnmappedFlag() || representative.isSecondaryOrSupplementary()) {
                    duplicateSet = this.duplicateSet;
                    this.duplicateSet = new DuplicateSet(this.comparator);
                    this.duplicateSet.add(record);
                    break; // exits the 0 == cmp loop
                } else {
                    // compare against the representative for set membership, not ordering
                    cmp = this.duplicateSet.add(record);

                    if (0 < cmp) {
                        throw new SAMException("The input records were not sorted in duplicate order:\n" +
                                representative.getSAMString() + record.getSAMString());
                    } else if (cmp < 0) {
                        duplicateSet = this.duplicateSet;
                        this.duplicateSet = new DuplicateSet(this.comparator);
                        this.duplicateSet.add(record);
                    } // otherwise it was already added
                }
            }
        }

        return duplicateSet;
    }

    public void close() { wrappedIterator.close(); }

    public boolean hasNext() {
        return (!duplicateSet.isEmpty() || wrappedIterator.hasNext());
    }

    // Does nothing!
    public void remove() { }
}
