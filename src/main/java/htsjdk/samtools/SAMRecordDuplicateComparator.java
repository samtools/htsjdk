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

import htsjdk.samtools.DuplicateScoringStrategy.ScoringStrategy;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares records based on if they should be considered PCR Duplicates (see MarkDuplicates).
 * 
 * There are three orderings provided by this comparator: compare, duplicateSetCompare, and fileOrderCompare.
 *  
 * Specify the headers when constructing this comparator if you would like to consider the library as the major sort key.
 * The records being compared must also have non-null SAMFileHeaders.
 *
 * @author nhomer
 */
public class SAMRecordDuplicateComparator implements SAMRecordComparator, Serializable {
    private static final long serialVersionUID = 1L;

    /** An enum to provide type-safe keys for transient attributes the comparator puts on SAMRecords. */
    private static enum Attr {
        LibraryId, ReadCoordinate, MateCoordinate
    }

    private static final byte FF = 0, FR = 1, F = 2, RF = 3, RR = 4, R = 5;

    private final Map<String, Short> libraryIds = new HashMap<String, Short>(); // from library string to library id
    private short nextLibraryId = 1;
    
    private ScoringStrategy scoringStrategy = ScoringStrategy.TOTAL_MAPPED_REFERENCE_LENGTH;
    
    public SAMRecordDuplicateComparator() {}

    public SAMRecordDuplicateComparator(final List<SAMFileHeader> headers) {
        // pre-populate the library names
        for (final SAMFileHeader header : headers) {
            for (final SAMReadGroupRecord readGroup : header.getReadGroups()) {
                final String libraryName = readGroup.getLibrary();
                if (null != libraryName) {
                    final short libraryId = this.nextLibraryId++;
                    this.libraryIds.put(libraryName, libraryId);
                }
            }
        }
    }
    
    public void setScoringStrategy(final ScoringStrategy scoringStrategy) {
        this.scoringStrategy = scoringStrategy;
    }

    /**
     * Populates the set of transient attributes on SAMRecords if they are not already there.
     */
    private void populateTransientAttributes(final SAMRecord... recs) {
        for (final SAMRecord rec : recs) {
            if (rec.getTransientAttribute(Attr.LibraryId) != null) continue;
            rec.setTransientAttribute(Attr.LibraryId, getLibraryId(rec));
            rec.setTransientAttribute(Attr.ReadCoordinate, rec.getReadNegativeStrandFlag() ? rec.getUnclippedEnd() : rec.getUnclippedStart());
            rec.setTransientAttribute(Attr.MateCoordinate, getMateCoordinate(rec));
        }
    }

    /**
     * Gets the library name from the header for the record. If the RG tag is not present on
     * the record, or the library isn't denoted on the read group, a constant string is
     * returned.
     */
    private static String getLibraryName(final SAMRecord rec) {
        final String readGroupId = (String) rec.getAttribute("RG");

        if (readGroupId != null) {
            final SAMFileHeader samHeader = rec.getHeader();
            if (null != samHeader) {
                final SAMReadGroupRecord rg = samHeader.getReadGroup(readGroupId);
                if (rg != null) {
                    final String libraryName = rg.getLibrary();
                    if (null != libraryName) return libraryName;
                }
            }
        }

        return "Unknown Library";
    }

    /** Get the library ID for the given SAM record. */
    private short getLibraryId(final SAMRecord rec) {
        final String library = getLibraryName(rec);
        Short libraryId = this.libraryIds.get(library);

        if (libraryId == null) {
            libraryId = this.nextLibraryId++;
            this.libraryIds.put(library, libraryId);
        }

        return libraryId;
    }

    /**
     * Convenience method for comparing two orientation bytes.  This is critical if we have mapped reads compared to fragment reads.
     */
    private int compareOrientationByteCollapseOrientation(final int orientation1, final int orientation2) {
        // F == FR, F == FF
        // R == RF, R == RR
        if (F == orientation1 || R == orientation1) { // first orientation is fragment
            /**
             * We want 
             * F == FR, F == FF
             * R == RF, R == RR
             */
            if (F == orientation1) {
                if (F == orientation2 || FR == orientation2 || FF == orientation2) {
                    return 0;
                }
            }
            else { // R == orientation1
                if (R == orientation2 || RF == orientation2 || RR == orientation2) {
                    return 0;
                }
            }
        }
        else if (F == orientation2 || R == orientation2) { // first orientation is paired, second is fragment
            return -compareOrientationByteCollapseOrientation(orientation2, orientation1);
        }

        return orientation1 - orientation2;
    }
    
    /**
     * Returns a single byte that encodes the orientation of the two reads in a pair.
     */
    private static byte getPairedOrientationByte(final boolean read1NegativeStrand, final boolean read2NegativeStrand) {
        if (read1NegativeStrand) {
            if (read2NegativeStrand) return SAMRecordDuplicateComparator.RR;
            else return SAMRecordDuplicateComparator.RF;
        } else {
            if (read2NegativeStrand) return SAMRecordDuplicateComparator.FR;
            else return SAMRecordDuplicateComparator.FF;
        }
    }
    
    private int getFragmentOrientation(final SAMRecord record) {
         return record.getReadNegativeStrandFlag() ? SAMRecordDuplicateComparator.R : SAMRecordDuplicateComparator.F;
    }

    private int getPairedOrientation(final SAMRecord record) {
        if (record.getReadPairedFlag() && !record.getReadUnmappedFlag() && !record.getMateUnmappedFlag()) {
            return getPairedOrientationByte(record.getReadNegativeStrandFlag(), record.getMateNegativeStrandFlag());
        } else {
            return getFragmentOrientation(record);
        }
    }

    private int getMateReferenceIndex(final SAMRecord record) {
        if (record.getReadPairedFlag() && !record.getReadUnmappedFlag() && !record.getMateUnmappedFlag()) {
            return record.getMateReferenceIndex();
        } else {
            return -1;
        }
    }

    private int getMateCoordinate(final SAMRecord record) {
        if (record.getReadPairedFlag() && !record.getReadUnmappedFlag() && !record.getMateUnmappedFlag()) {
            return record.getMateNegativeStrandFlag() ? SAMUtils.getMateUnclippedEnd(record) : SAMUtils.getMateUnclippedStart(record);
        } else {
            return -1;
        }
    }
    
    /** Is one end of a pair, or the fragment, unmapped? */
    private boolean hasUnmappedEnd(final SAMRecord record) {
        return (record.getReadUnmappedFlag() || (record.getReadPairedFlag() && record.getMateUnmappedFlag()));
    }

    /** Are both ends of a pair, or the fragment, mapped? */
    private boolean hasMappedEnd(final SAMRecord record) {
        return (!record.getReadUnmappedFlag() || (record.getReadPairedFlag() && !record.getMateUnmappedFlag()));
    }
    
    /** Is this paired end and are both ends of a pair mapped */
    private boolean pairedEndAndBothMapped(final SAMRecord record) {
        return (record.getReadPairedFlag() && !record.getReadUnmappedFlag() && !record.getMateUnmappedFlag());
        
    }

    /**
     * Most stringent comparison.
     *
     * Two records are compared based on if they are duplicates of each other, and then based
     * on if they should be prioritized for being the most "representative".  Typically, the representative
     * is the record in the SAM file that is *not* marked as a duplicate within a set of duplicates.
     *  
     * Compare by file order, then duplicate scoring strategy, read name.
     * 
     * If both reads are paired and both ends mapped, always prefer the first end over the second end.  This is needed to
     * properly choose the first end for optical duplicate identification when both ends are mapped to the same position etc. 
     */ 
    public int compare(final SAMRecord samRecord1, final SAMRecord samRecord2) {
        populateTransientAttributes(samRecord1, samRecord2);
        int cmp;

        // temporary variables for comparisons
        int samRecord1Value, samRecord2Value;

        cmp = fileOrderCompare(samRecord1, samRecord2);
        // the duplicate scoring strategy
        if (cmp == 0) {
            cmp = DuplicateScoringStrategy.compare(samRecord1, samRecord2, this.scoringStrategy, true);
        }
        // the read name
        if (cmp == 0) {
            cmp = samRecord1.getReadName().compareTo(samRecord2.getReadName());
        }
        // needed for optical duplicate detection when both ends are mapped to the same position.
        if (cmp == 0) {
            if (samRecord1.getReadPairedFlag() && samRecord2.getReadPairedFlag()) {
                samRecord1Value = samRecord1.getFirstOfPairFlag() ? 0 : 1;
                samRecord2Value = samRecord2.getFirstOfPairFlag() ? 0 : 1;
                cmp = samRecord1Value - samRecord2Value;
            }
        }

        return cmp;
    }

    /**
     * Compares: Library identifier, reference index, read coordinate, orientation of the read (or read pair), mate's coordinate (if paired and mapped),
     * mapped ends, ...
     *  
     * collapseOrientation - true if we want cases where fragment orientation to paired end orientation can be equal (ex. F == FR), false otherwise
     * considerNumberOfEndsMappedAndPairing - true if we want to prefer paired ends with both ends mapped over paired ends with only one end mapped, or paired ends with end
     * mapped over fragment reads, false otherwise.
     *  
     */
    private int fileOrderCompare(final SAMRecord samRecord1, final SAMRecord samRecord2, final boolean collapseOrientation, final boolean considerNumberOfEndsMappedAndPairing) {
        populateTransientAttributes(samRecord1, samRecord2);
        int cmp;

        if (null == samRecord1.getHeader() || null == samRecord2.getHeader()) {
            throw new IllegalArgumentException("Records must have non-null SAMFileHeaders to be compared");
        }

        // temporary variables for comparisons
        int samRecord1Value, samRecord2Value;

        // library identifier
        {
            samRecord1Value = (Short) samRecord1.getTransientAttribute(Attr.LibraryId);
            samRecord2Value = (Short) samRecord2.getTransientAttribute(Attr.LibraryId);
            cmp = samRecord1Value - samRecord2Value;
        }
        // reference index
        if (cmp == 0) {
            samRecord1Value = samRecord1.getReferenceIndex();
            samRecord2Value = samRecord2.getReferenceIndex();
            // NB: this accounts for unmapped reads to be placed at the ends of the file
            if (samRecord1Value == -1) {
                cmp = (samRecord2Value == -1) ? 0 : 1;
            }
            else if (samRecord2Value == -1) {
                cmp = -1;
            }
            else {
                cmp = samRecord1Value - samRecord2Value;
            }
        }
        // read coordinate
        if (cmp == 0) {
            samRecord1Value = (Integer) samRecord1.getTransientAttribute(Attr.ReadCoordinate);
            samRecord2Value = (Integer) samRecord2.getTransientAttribute(Attr.ReadCoordinate);
            cmp = samRecord1Value - samRecord2Value;
        }
        // orientation
        if (cmp == 0) {
            samRecord1Value = getPairedOrientation(samRecord1);
            samRecord2Value = getPairedOrientation(samRecord2);
            if (collapseOrientation) {
                cmp = compareOrientationByteCollapseOrientation(samRecord1Value, samRecord2Value);
            }
            else {
                cmp = samRecord1Value - samRecord2Value;
            }
        }
        // both ends need to be mapped
        if (pairedEndAndBothMapped(samRecord1) && pairedEndAndBothMapped(samRecord2)) {
            // mate's reference index
            if (cmp == 0) {
                samRecord1Value = getMateReferenceIndex(samRecord1);
                samRecord2Value = getMateReferenceIndex(samRecord2);
                cmp = samRecord1Value - samRecord2Value;
            }
            // mate's coordinate
            if (cmp == 0) {
                samRecord1Value = (Integer) samRecord1.getTransientAttribute(Attr.MateCoordinate);
                samRecord2Value = (Integer) samRecord2.getTransientAttribute(Attr.MateCoordinate);;
                cmp = samRecord1Value - samRecord2Value;
            }
        }
        if (cmp == 0) {
            samRecord1Value = hasMappedEnd(samRecord1) ? 0 : 1;
            samRecord2Value = hasMappedEnd(samRecord2) ? 0 : 1;
            cmp = samRecord1Value - samRecord2Value;
        }
        // if both paired or both unpaired, then check if one of the two ends (or single end) is unmapped
        // else prefer the one that is paired end
        if (cmp == 0 && considerNumberOfEndsMappedAndPairing) {
            if (samRecord1.getReadPairedFlag() == samRecord2.getReadPairedFlag()) {
                // Is this unmapped or its mate?
                samRecord1Value = hasUnmappedEnd(samRecord1) ? 1 : 0;
                samRecord2Value = hasUnmappedEnd(samRecord2) ? 1 : 0;
                cmp = samRecord1Value - samRecord2Value;
            }
            else { // if we care if one is paired and the other is not
                cmp = samRecord1.getReadPairedFlag() ? -1 : 1;
            }
        }

        return cmp;
    }

    /**
     * Less stringent than compare, such that two records are equal enough such that their ordering within their duplicate set would be arbitrary.
     *
     * Major difference between this and fileOrderCompare is how we compare the orientation byte.  Here we want:
     *   F == FR, F == FF
     *   R == RF, R == RR
     */
    public int duplicateSetCompare(final SAMRecord samRecord1, final SAMRecord samRecord2) {
        return fileOrderCompare(samRecord1, samRecord2, true, false);
    }

    /**
     * Less stringent than duplicateSetCompare, such that two records are equal enough such that their ordering in a sorted SAM file would be arbitrary.
     */
    public int fileOrderCompare(final SAMRecord samRecord1, final SAMRecord samRecord2) {
        return fileOrderCompare(samRecord1, samRecord2, false, true);
    }
}
