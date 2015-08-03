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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores a set of records that are duplicates of each other.  The first records in the list of records is 
 * considered the representative of the duplicate, and typically does not have it's duplicate flag set.  
 * The records' duplicate flag will be set appropriately as records are added.  This behavior can be 
 * turned off.
 * 
 * At this time, this set does not track optical duplicates.
 *
 * @author nhomer
 */
public class DuplicateSet {
    
    private final List<SAMRecord> records;

    private static final SAMRecordDuplicateComparator defaultComparator = new SAMRecordDuplicateComparator();

    private final SAMRecordDuplicateComparator comparator;
    
    private boolean needsSorting = false;
    
    private boolean setDuplicateFlag = false;

    /** Sets the duplicate flag by default */
    public DuplicateSet() {
        this(true);
    }

    public DuplicateSet(final boolean setDuplicateFlag) {
        this(setDuplicateFlag, defaultComparator);
    }

    public DuplicateSet(final SAMRecordDuplicateComparator comparator) {
        this(true, comparator);
    }

    public DuplicateSet(final boolean setDuplicateFlag, final SAMRecordDuplicateComparator comparator) {
        records = new ArrayList<SAMRecord>(10);
        this.setDuplicateFlag = setDuplicateFlag;
        this.comparator = comparator;
    }
           
    /**
     * Adds a record to the set and returns zero if either the set is empty, or it is a duplicate of the records already in the set.  Otherwise,
     * it does not add the record and returns non-zero.
     * @param record the record to add.
     * @return zero if the record belongs in this set, -1 in a previous set, or 1 in a subsequent set, according to the comparison order
     */
    public int add(final SAMRecord record) {

        if (!this.records.isEmpty()) {
            final int cmp = this.comparator.duplicateSetCompare(this.getRepresentative(), record);
            if (0 != cmp) {
                return cmp;
            }
        }
        
        this.records.add(record);
        needsSorting = true;
        
        return 0;
    }

    private void sort() {
        if (!records.isEmpty()) {
            Collections.sort(records, this.comparator);
            
            final SAMRecord representative = records.get(0);
            
            if (setDuplicateFlag) {
                // reset duplicate flags
                for (final SAMRecord record : records) {
                    if (!record.getReadUnmappedFlag() && !record.isSecondaryOrSupplementary() && !record.getReadName().equals(representative.getReadName())) {
                        record.setDuplicateReadFlag(true);
                    }
                }
                records.get(0).setDuplicateReadFlag(false);
            }
        }
        needsSorting = false; // this could be in the if above if you think hard about it
    }

    /**
     * Gets the list of records from this set.
     */
    public List<SAMRecord> getRecords() {
        if (needsSorting) {
            sort();
        }
        
        return this.records;
    }

    /**
     * Gets the representative record according to the duplicate comparator.
     */
    public SAMRecord getRepresentative() {
        if (needsSorting) {
            sort();
        }
        
        return records.get(0);
    }

    /**
     * Returns the number of records in this set.
     */
    public int size() {
        return this.records.size();
    }

    /**
     * Returns the number of duplicates in this set, including the representative record.  Does not include records that are unmapped,
     * secondary, or supplementary.
     */
    public int numDuplicates() {
        int n = 0;
        for (final SAMRecord record : records) {
            if (!record.getReadUnmappedFlag() && !record.isSecondaryOrSupplementary()) {
                n++;
            }
        }
        return n;
    }    
    
    public boolean isEmpty() {
        return this.records.isEmpty();
    }

    /**
     * Controls if we should update the duplicate flag of the records in this set.
     */
    public void setDuplicateFlag(boolean setDuplicateFlag) { this.setDuplicateFlag = setDuplicateFlag; }
}
