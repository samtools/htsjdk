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

import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;

import java.util.Iterator;

/**
 * @author alecw@broadinstitute.org
 */
public class IntervalUtil {

    /** Return true if the sequence/position lie in the provided interval. */
    public static boolean contains(final Interval interval, final String sequenceName, final long position) {
        return interval.getContig().equals(sequenceName) && (position >= interval.getStart() && position <= interval.getEnd());
    }

    /** Return true if the sequence/position lie in the provided interval list. */
    public static boolean contains(final IntervalList intervalList, final String sequenceName, final long position) {
        for (final Interval interval : intervalList.uniqued().getIntervals()) {
            if (contains(interval, sequenceName, position))
                return true;
        }
        return false;
    }

    /**
     * Throws RuntimeException if the given intervals are not locus ordered and non-overlapping
     *
     * @param intervals
     * @param sequenceDictionary used to determine order of sequences
     */
    public static void assertOrderedNonOverlapping(final Iterator<Interval> intervals, final SAMSequenceDictionary sequenceDictionary) {
        if (!intervals.hasNext()) {
            return;
        }
        Interval prevInterval = intervals.next();
        int prevSequenceIndex = sequenceDictionary.getSequenceIndex(prevInterval.getContig());
        while (intervals.hasNext()) {
            final Interval interval = intervals.next();
            if (prevInterval.intersects(interval)) {
                throw new SAMException("Intervals should not overlap: " + prevInterval + "; " + interval);
            }
            final int thisSequenceIndex = sequenceDictionary.getSequenceIndex(interval.getContig());
            if (prevSequenceIndex > thisSequenceIndex ||
                    (prevSequenceIndex == thisSequenceIndex && prevInterval.compareTo(interval) >= 0)) {
                throw new SAMException("Intervals not in order: " + prevInterval + "; " + interval);
            }
            prevInterval = interval;
            prevSequenceIndex = thisSequenceIndex;
        }
    }
   
    /** 
     * Utility to parse a user's coordinate.
     * called by <pre>SAMSequenceDictionary.parseInterval()</pre>. Parse a coordinate string, remove the comma look at the suffix (bp,kb,mp,gb) to scale,
     * verify that the result is a positive integer
     * @author Pierre Lindenbaum 
     * @param numStr the coordinate. comma(s) will be removed. suffixes like 'bp,kb,Mb,Gb' will be interpreted as 1, 1000, 1E6 and 1E9 bases.
     * @return the coordinate as integer
     * @throws IllegalArgumentException if numStr is not a number, if it is lower than 0 or greater than Integer.MAX_VALUE
     */
    public static int parseCoordinate(final String numStr) {
        if (numStr == null) {
            throw new IllegalArgumentException("parseCoordinate: null argument.");
        }
        long factor = 1L;
        String num = numStr.replace(",", "").trim().toLowerCase();
        if (num.endsWith("bp")) {
            factor = 1L;
            num = num.substring(0, num.length() - 2).trim();
        } else if (num.endsWith("kb")) {
            factor = 1_000L;// 1E3
            num = num.substring(0, num.length() - 2).trim();
        } else if (num.endsWith("mb")) {
            factor = 1_000_000L;// 1E6
            num = num.substring(0, num.length() - 2).trim();
        } else if (num.endsWith("gb")) {
            factor = 1000_000_000L;// 1E9
            num = num.substring(0, num.length() - 2).trim();
        } else if (!num.isEmpty() && Character.isLetter(num.charAt(num.length() - 1))) {
            throw new IllegalArgumentException(
                    "bad extension for coordinate '" + numStr + "'. Allowed extensions are bp,kb,mb and gb.");
        }

        try {
            long pos = Long.parseLong(num);
            pos *= factor;
            if (pos < 0L || pos > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Cannot convert " + numStr + " to a positive integer");
            return (int) pos;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot convert " + numStr + " to a coordinate.");
        }
    }
    

    /** finds user's contig by name.
     * @throws IllegalArgumentException if not found.
     */
    private static SAMSequenceRecord findSequenceByName(final SAMSequenceDictionary dictionary, final String contig) {
        /* first try using the map.get(contig) */
        final SAMSequenceRecord samSequenceRecord = dictionary.getSequence(contig);
        if( samSequenceRecord != null) return samSequenceRecord;
        /* build error message */
        final StringBuilder sb = new StringBuilder( "Cannot find contig \"" + contig+"\" in dictionary. Available are:");
        for(final SAMSequenceRecord seqRecord : dictionary.getSequences()) {
            sb.append(" \"").append(seqRecord.getSequenceName()).append("\"");
        }
        throw new IllegalArgumentException(
                "Cannot find contig \"" + contig+"\" in dictionary. Available are: "+sb
                );
       }
    
    /** 
     * parses the interval String and returns a htsjdk.samtools.util.Interval
     * The contig/chromosome must exist in the dictionary (ignoring case ). out-of-bound index <code>!(1<= index <= sequence.length())</code> will be clipped 
     * coordinate will be parsed by <pre>htsjdk.samtools.util.Interval.parseCoordinate</pre>.
     * Comma in the coordinate will be removed.
     * left/right white spaces will be trimmed.
     * A Position larger than the contig size will be set to the contig size
     * 
     * Valid syntax -> conversion :
     * <pre> 
     *     CHROM -> CHROM:1-seq.length()
     *     CHROM:POS ->  CHROM:POS-POS (POS must be greater than 0)
     *     CHROM:POS+NUM -> CHROM:{POS-NUM}-{POS+NUM}
     *     CHROM:BEGIN- ->  CHROM:BEGIN-seq.length()
     *     CHROM:BEGIN-END ->  CHROM:BEGIN-END
     * </pre>
     * @author Pierre Lindenbaum
     * @param dictionary dictionary. The config must be present in the dictionary. 
     * @param region a 1-based coordinate region. 
     * @return the parsed htsjdk.samtools.util.Interval
     * @throws IllegalArgumentException if 'region' cannot be converted.
     */
    public static Interval parseInterval(final SAMSequenceDictionary dictionary, final String region) {
        if (dictionary == null) {
            throw new IllegalArgumentException("parseInterval: dictionary is null");
        }
        if (region == null) {
            throw new IllegalArgumentException("parseInterval: region is null");
        }
        if (region.isEmpty()) {
            throw new IllegalArgumentException("parseInterval: region is empty");
        }
        final int colon = region.indexOf(':');
        // no colon, chromosome only
        if (colon == -1) {
            final SAMSequenceRecord record = findSequenceByName(dictionary, region.trim());
            // return whole chromosome
            return new Interval(
                    record.getSequenceName(),
                    1,
                    record.getSequenceLength()
                    );
        }
        if (colon == 0)
            throw new IllegalArgumentException("Empty chromosome in " + region);
        // contig is before colon
        final SAMSequenceRecord record = findSequenceByName(dictionary, region.substring(0, colon).trim());
        final int hyphen = region.indexOf('-', colon + 1);
        final int plus = region.indexOf('+', colon + 1);
        // CHROM:POS
        if (hyphen == -1 && plus == -1) {
            final int pos = IntervalUtil.parseCoordinate(region.substring(colon + 1));
            // if pos is out of bounds, we cannot trim here
            if (pos < 1 || pos > record.getSequenceLength())
                throw new IllegalArgumentException(
                        "In " + region + ", position=" + pos + " out of bounds 1<=pos<=" + record.getSequenceLength());
            return new Interval(
                    record.getSequenceName(),
                    pos,
                    pos
                    );
        }
        // CHROM:POS+NUM
        if (hyphen == -1 && plus > colon) {
            final int pos = parseCoordinate(region.substring(colon + 1, plus));
            final int extend = parseCoordinate(region.substring(plus + 1));
            if (extend < 0)
                throw new IllegalArgumentException("Extend cannot be negative in " + region);
            return new Interval(
                    record.getSequenceName(),
                    Math.max(1, pos - extend),
                    Math.min(pos + extend, record.getSequenceLength())
                    );
        }
        // CHROM:START-END or CHROM:START-
        final int chromStart;
        try {
            chromStart = parseCoordinate(region.substring(colon + 1, hyphen));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Cannot parse " + region , error);
        }
        if (chromStart > record.getSequenceLength())
            throw new IllegalArgumentException("In " + region + ", position=" + chromStart
                    + " is greater than the contig length:" + record.getSequenceLength());
        // CHROM:START-
        if (region.substring(hyphen + 1).trim().isEmpty()) {
            return new Interval(
                    record.getSequenceName(),
                    Math.max(1, chromStart),
                    record.getSequenceLength()
                    );
        }
        // CHROM:START-END
        final int chromEnd;
        try {
            chromEnd =  parseCoordinate(region.substring(hyphen + 1));
        } catch (Exception error) {
            throw new IllegalArgumentException("Cannot parse " + region , error);
        }
       
        if (chromEnd == 0 && chromStart == 0) {
            throw new IllegalArgumentException("Cannot parse "+region+" bot are equals to zero.");
        }
        if (chromEnd < chromStart) {// this is legal for deletions
            return new Interval(
                    record.getSequenceName(),
                    Math.max(1, chromEnd),
                    Math.min(chromStart, record.getSequenceLength())
                    );
        } else {
            return new Interval(
                    record.getSequenceName(),
                    Math.max(1, chromStart),
                    Math.min(chromEnd, record.getSequenceLength())
                    );
        }
    }
}
