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

import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SAMTextHeaderCodec;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Represents a list of intervals against a reference sequence that can be written to
 * and read from a file.  The file format is relatively simple and reflects the SAM
 * alignment format to a degree.
 *
 * A SAM style header must be present in the file which lists the sequence records
 * against which the intervals are described.  After the header the file then contains
 * records one per line in text format with the following values tab-separated:
 *    Sequence name,
 *    Start position (1-based),
 *    End position (1-based, end inclusive),
 *    Strand (either + or -),
 *    Interval name (an, ideally unique, name for the interval),
 *
 * @author Tim Fennell
 * @author Yossi Farjoun
 */
public class IntervalList implements Iterable<Interval> {
    public static final String INTERVAL_LIST_FILE_EXTENSION = ".interval_list";

    private final SAMFileHeader header;
    private final List<Interval> intervals = new ArrayList<Interval>();

    private static final Log log = Log.getInstance(IntervalList.class);

    /** Constructs a new interval list using the supplied header information. */
    public IntervalList(final SAMFileHeader header) {
        if (header == null) {
            throw new IllegalArgumentException("SAMFileHeader must be supplied.");
        }
        this.header = header;
    }

    /** Gets the header (if there is one) for the interval list. */
    public SAMFileHeader getHeader() { return header; }

    /** Returns an iterator over the intervals. */
    public Iterator<Interval> iterator() { return this.intervals.iterator(); }

    /** Adds an interval to the list of intervals. */
    public void add(final Interval interval) {
        if (header.getSequence(interval.getContig()) == null) {
            throw new IllegalArgumentException(String.format("Cannot add interval %s, contig not in header", interval.toString()));
        }
        this.intervals.add(interval);
    }

    /** Adds a Collection of intervals to the list of intervals. */
    public void addall(final Collection<Interval> intervals) {
        //use this instead of addAll so that the contig checking happens.
        for (Interval interval : intervals) {
            add(interval);
        }
    }

    /** Sorts the internal collection of intervals by coordinate. */
    @Deprecated // Use sorted() instead of sort(). The sort() function modifies the object in-place and
    // is therefore difficult to work with. sorted() returns a new IntervalList that is sorted
    public void sort() {
        Collections.sort(this.intervals, new IntervalCoordinateComparator(this.header));
        this.header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
    }

    /** Returns a new IntervalList where each interval is padded by the specified amount of bases. */
    public IntervalList padded(final int before, final int after) {
        if (before < 0 || after < 0) throw new IllegalArgumentException("Padding values must be >= 0.");
        final IntervalList padded = new IntervalList(this.getHeader().clone());
        final SAMSequenceDictionary dict = padded.getHeader().getSequenceDictionary();
        for (final Interval i : this) {
            final SAMSequenceRecord seq = dict.getSequence(i.getContig());
            final int start = Math.max(1, i.getStart() - before);
            final int end   = Math.min(seq.getSequenceLength(), i.getEnd() + after);
            padded.add(new Interval(i.getContig(), start, end, i.isNegativeStrand(), i.getName()));
        }

        return padded;
    }

    /** Returns a new IntervalList where each interval is padded by 'padding' bases on each side. */
    public IntervalList padded(final int padding) {
        return padded(padding, padding);
    }

    /** returns an independent sorted IntervalList*/
    public IntervalList sorted() {
        final IntervalList sorted = IntervalList.copyOf(this);
        Collections.sort(sorted.intervals, new IntervalCoordinateComparator(sorted.header));
        sorted.header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        return sorted;
    }

    /** Returned an independent IntervalList that is sorted and uniquified. */
    public IntervalList uniqued() {
        return uniqued(true);
    }

    /**
     * Returned an independent IntervalList that is sorted and uniquified.
     * @param concatenateNames If false, interval names are not concatenated when merging intervals to save space.
     */
    public IntervalList uniqued(final boolean concatenateNames) {
        final List<Interval> tmp = getUniqueIntervals(sorted(), concatenateNames);
        final IntervalList value = new IntervalList(this.header.clone());
        value.intervals.addAll(tmp);
        return value;
    }

    /** Sorts and uniques the list of intervals held within this interval list. */
    @Deprecated//use uniqued() instead. This function modifies the object in-place and
    // is therefore difficult to work with.
    public void unique() {
        unique(true);
    }

    /**
     * Sorts and uniques the list of intervals held within this interval list.
     * @param concatenateNames If false, interval names are not concatenated when merging intervals to save space.
     */
    @Deprecated//use uniqued() instead. This function modifies the object in-place and
    // is therefore difficult to work with
    public void unique(final boolean concatenateNames) {
        sort();
        final List<Interval> tmp = getUniqueIntervals(concatenateNames);
        this.intervals.clear();
        this.intervals.addAll(tmp);
    }

    /** Gets the set of intervals as held internally. */
    public List<Interval> getIntervals() {
        return Collections.unmodifiableList(this.intervals);
    }

    /**
     * Merges the list of intervals and then reduces them down where regions overlap
     * or are directly adjacent to one another.  During this process the "merged" interval
     * will retain the strand and name of the 5' most interval merged.
     *
     * Note: has the side-effect of sorting the stored intervals in coordinate order if not already sorted.
     *
     * @return the set of unique intervals condensed from the contained intervals
     */
    @Deprecated//use uniqued().getIntervals() instead. This function modifies the object in-place and
    // is therefore difficult to work with
    public List<Interval> getUniqueIntervals() {
        return getUniqueIntervals(true);
    }

    //NO SIDE EFFECTS HERE!
    /**
     * Merges list of intervals and reduces them like htsjdk.samtools.util.IntervalList#getUniqueIntervals()
     * @param concatenateNames If false, the merged interval has the name of the earlier interval.  This keeps name shorter.
     */
    public static List<Interval> getUniqueIntervals(final IntervalList list, final boolean concatenateNames) {
        return getUniqueIntervals(list, concatenateNames, false);
    }

    //NO SIDE EFFECTS HERE!
    /**
     * Merges list of intervals and reduces them like htsjdk.samtools.util.IntervalList#getUniqueIntervals()
     * @param concatenateNames If false, the merged interval has the name of the earlier interval.  This keeps name shorter.
     * @param enforceSameStrands enforce that merged intervals have the same strand, otherwise ignore.
     */
    public static List<Interval> getUniqueIntervals(final IntervalList list, final boolean concatenateNames, final boolean enforceSameStrands) {

        final List<Interval> intervals;
        if (list.getHeader().getSortOrder() != SAMFileHeader.SortOrder.coordinate) {
            intervals = list.sorted().intervals;
        }
        else {
            intervals = list.intervals;
        }

        final List<Interval> unique = new ArrayList<Interval>();
        final TreeSet<Interval> toBeMerged = new TreeSet<Interval>();
        Interval current = null;

        for (final Interval next : intervals) {
            if (current == null) {
                toBeMerged.add(next);
                current = next;
            }
            else if (current.intersects(next) || current.abuts(next)) {
                if (enforceSameStrands && current.isNegativeStrand() != next.isNegativeStrand()) throw new SAMException("Strands were not equal for: " + current.toString() + " and " + next.toString());
                toBeMerged.add(next);
                current = new Interval(current.getContig(), current.getStart(), Math.max(current.getEnd(), next.getEnd()), current.isNegativeStrand(), null);
            }
            else {
                // Emit merged/unique interval
                unique.add(merge(toBeMerged, concatenateNames));

                // Set current == next for next iteration
                toBeMerged.clear();
                current = next;
                toBeMerged.add(current);
            }
        }

        if (!toBeMerged.isEmpty()) unique.add(merge(toBeMerged, concatenateNames));
        return unique;
    }

    /**
     * Merges list of intervals and reduces them like htsjdk.samtools.util.IntervalList#getUniqueIntervals()
     * @param concatenateNames If false, the merged interval has the name of the earlier interval.  This keeps name shorter.
     */
    @Deprecated //use uniqued(concatenateNames).getIntervals() or the static version instead to avoid changing the underlying object.
    /**
     * Merges list of intervals and reduces them like htsjdk.samtools.util.IntervalList#getUniqueIntervals()
     * @param concatenateNames If false, the merged interval has the name of the earlier interval.  This keeps name shorter.
     */
    public List<Interval> getUniqueIntervals(final boolean concatenateNames) {
        if (getHeader().getSortOrder() != SAMFileHeader.SortOrder.coordinate) {
            sort();
        }

        return getUniqueIntervals(this, concatenateNames);
    }

    /**
     * Given a list of Intervals and a band multiple, this method will return a list of Intervals such that all of the intervals
     * do not straddle integer multiples of that band.
     *
     * ex: if there is an interval (7200-9300) and the bandMultiple is 1000, the interval will be split into:
     * (7200-7999, 8000-8999, 9000-9300)
     * @param intervals A list of Interval
     * @param bandMultiple integer value (> 0) to break up intervals in the list at integer multiples of
     * @return list of intervals that are broken up
     */
    public static List<Interval> breakIntervalsAtBandMultiples(final List<Interval> intervals, final int bandMultiple) {
        final List<Interval> brokenUpIntervals = new ArrayList<Interval>();
        for (final Interval interval : intervals) {
            if (interval.getEnd() >= interval.getStart()) {       // Normal, non-empty intervals
                final int startIndex = interval.getStart() / bandMultiple;
                final int endIndex = interval.getEnd() / bandMultiple;
                if (startIndex == endIndex) {
                    brokenUpIntervals.add(interval);
                } else {
                    brokenUpIntervals.addAll(breakIntervalAtBandMultiples(interval, bandMultiple));
                }
            }
            else {                                  // Special case - empty intervals ex: (100-99)
                brokenUpIntervals.add(interval);
            }
        }
        return brokenUpIntervals;
    }

    /**
     * Given an Interval and a band multiple, this method will return a list of Intervals such that all of the intervals
     * do not straddle integer multiples of that band.
     *
     * ex: if the interval is (7200-9300) and the bandMultiple is 1000, the interval will be split into:
     * (7200-7999, 8000-8999, 9000-9300)
     * @param interval an Interval
     * @param bandMultiple integer value (> 0) to break up intervals in the list at integer multiples of
     * @return list of intervals that are broken up
     */
    private static List<Interval> breakIntervalAtBandMultiples(final Interval interval, final int bandMultiple) {
        final List<Interval> brokenUpIntervals = new ArrayList<Interval>();

        int startPos = interval.getStart();
        final int startOfIntervalIndex = startPos / bandMultiple;
        int startIndex = startOfIntervalIndex;
        final int endIndex = interval.getEnd() / bandMultiple;
        while (startIndex <= endIndex) {
            int endPos = (startIndex + 1) * bandMultiple -1;
            if (endPos > interval.getEnd()) {
                endPos = interval.getEnd();
            }
            // add start/end to list of broken up intervals to return (and uniquely name it).
            brokenUpIntervals.add(new Interval(interval.getContig(), startPos, endPos, interval.isNegativeStrand(), interval.getName() + "." + (startIndex - startOfIntervalIndex + 1)));
            startIndex++;
            startPos = startIndex * bandMultiple;
        }
        return brokenUpIntervals;
    }


    /** Merges a sorted collection of intervals and optionally concatenates unique names or takes the first name. */
    static Interval merge(final SortedSet<Interval> intervals, final boolean concatenateNames) {
        final String chrom = intervals.first().getContig();
        int start = intervals.first().getStart();
        int end   = intervals.last().getEnd();
        final boolean neg  = intervals.first().isNegativeStrand();
        final LinkedHashSet<String> names = new LinkedHashSet<String>();
        final String name;

        for (final Interval i : intervals) {
            if (i.getName() != null) names.add(i.getName());
            start = Math.min(start, i.getStart());
            end   = Math.max(end, i.getEnd());
        }

        if (concatenateNames) {
            if (names.isEmpty()) name = null;
            else name = StringUtil.join("|", names);
        }
        else { name = names.iterator().next(); }

        return new Interval(chrom, start, end, neg, name);
    }

    /** Gets the (potentially redundant) sum of the length of the intervals in the list. */
    public long getBaseCount() {
        return Interval.countBases(this.intervals);
    }

    /** Gets the count of unique bases represented by the intervals in the list. */
    public long getUniqueBaseCount() {
        return uniqued().getBaseCount();
    }

    /** Returns the count of intervals in the list. */
    public int size() {
        return this.intervals.size();
    }

    /** creates a independent copy of the given IntervalList
     *
     * @param list
     * @return
     */
    public static IntervalList copyOf(final IntervalList list){
        final IntervalList clone = new IntervalList(list.header.clone());
        clone.intervals.addAll(list.intervals);
        return clone;
    }

    /**
     * Parses an interval list from a file.
     * @param file the file containing the intervals
     * @return an IntervalList object that contains the headers and intervals from the file
     */
    public static IntervalList fromFile(final File file) {
        final BufferedReader reader= IOUtil.openFileForBufferedReading(file);
        final IntervalList list = fromReader(reader);
        try {
            reader.close();
        } catch (final IOException e) {
            throw new SAMException(String.format("Failed to close file %s after reading",file));
        }

        return list;
    }

    /**
     * Creates an IntervalList from the given sequence name
     * @param header header to use to create IntervalList
     * @param sequenceName name of sequence in header
     * @return a new intervalList with given header that contains the reference name
     */
    public static IntervalList fromName(final SAMFileHeader header, final String sequenceName) {
        final IntervalList ref = new IntervalList(header);
        ref.add(new Interval(sequenceName, 1, header.getSequence(sequenceName).getSequenceLength()));

        return ref;
    }

    /**
     * Calls {@link #fromFile(java.io.File)} on the provided files, and returns their {@link #union(java.util.Collection)}.
     */
    public static IntervalList fromFiles(final Collection<File> intervalListFiles) {
        final Collection<IntervalList> intervalLists = new ArrayList<IntervalList>();
        for (final File file : intervalListFiles) {
            intervalLists.add(IntervalList.fromFile(file));
        }
        return IntervalList.union(intervalLists);
    }

    /**
     * Parses an interval list from a reader in a stream based fashion.
     * @param in a BufferedReader that can be read from
     * @return an IntervalList object that contains the headers and intervals from the file
     */
    public static IntervalList fromReader(final BufferedReader in) {
        try {
            // Setup a reader and parse the header
            final StringBuilder builder = new StringBuilder(4096);
            String line = null;

            while ((line = in.readLine()) != null) {
                if (line.startsWith("@")) {
                    builder.append(line).append('\n');
                }
                else {
                    break;
                }
            }

            if (builder.length() == 0) {
                throw new IllegalStateException("Interval list file must contain header. ");
            }

            final StringLineReader headerReader = new StringLineReader(builder.toString());
            final SAMTextHeaderCodec codec = new SAMTextHeaderCodec();
            final IntervalList list = new IntervalList(codec.decode(headerReader, "BufferedReader"));
            final SAMSequenceDictionary dict = list.getHeader().getSequenceDictionary();

            //there might not be any lines after the header, in which case we should return an empty list
            if(line == null) return list;

            // Then read in the intervals
            final FormatUtil format = new FormatUtil();
            do {
                if (line.trim().isEmpty()) continue; // skip over blank lines

                // Make sure we have the right number of fields
                final String[] fields = line.split("\t");
                if (fields.length != 5) {
                    throw new SAMException("Invalid interval record contains " +
                            fields.length + " fields: " + line);
                }

                // Then parse them out
                final String seq = fields[0];
                final int start = format.parseInt(fields[1]);
                final int end   = format.parseInt(fields[2]);

                final boolean negative;
                if (fields[3].equals("-")) negative = true;
                else if (fields[3].equals("+")) negative = false;
                else throw new IllegalArgumentException("Invalid strand field: " + fields[3]);

                final String name = fields[4];

                final Interval interval = new Interval(seq, start, end, negative, name);
                if (dict.getSequence(seq) == null) {
                    log.warn("Ignoring interval for unknown reference: " + interval);
                }
                else {
                    list.intervals.add(interval);
                }
            }
            while ((line = in.readLine()) != null);

            return list;
        }
        catch (final IOException ioe) {
            throw new SAMException("Error parsing interval list.", ioe);
        }
        finally {
            try { in.close(); } catch (final Exception e) { /* do nothing */ }
        }
    }

    /**
     * Writes out the list of intervals to the supplied file.
     * @param file a file to write to.  If exists it will be overwritten.
     */
    public void write(final File file) {
        try {
            final BufferedWriter out = IOUtil.openFileForBufferedWriting(file);
            final FormatUtil format = new FormatUtil();

            // Write out the header
            if (this.header != null) {
                final SAMTextHeaderCodec codec = new SAMTextHeaderCodec();
                codec.encode(out, this.header);
            }

            // Write out the intervals
            for (final Interval interval : this) {
                out.write(interval.getContig());
                out.write('\t');
                out.write(format.format(interval.getStart()));
                out.write('\t');
                out.write(format.format(interval.getEnd()));
                out.write('\t');
                out.write(interval.isPositiveStrand() ? '+' : '-');
                out.write('\t');
                if(interval.getName() != null){
                    out.write(interval.getName());
                }
                else{
                    out.write(".");
                }
                out.newLine();
            }

            out.flush();
            out.close();
        }
        catch (final IOException ioe) {
            throw new SAMException("Error writing out interval list to file: " + file.getAbsolutePath(), ioe);
        }
    }

    /**
     * A utility function for generating the intersection of two IntervalLists, checks for equal dictionaries.
     *
     * @param list1 the first IntervalList
     * @param list2 the second IntervalList
     * @return the intersection of list1 and list2.
     */

    public static IntervalList intersection(final IntervalList list1, final IntervalList list2) {

        final IntervalList result;
        // Ensure that all the sequence dictionaries agree and merge the lists
        SequenceUtil.assertSequenceDictionariesEqual(list1.getHeader().getSequenceDictionary(),
                list2.getHeader().getSequenceDictionary());

        result = new IntervalList(list1.getHeader().clone());

        final OverlapDetector<Interval> detector = new OverlapDetector<Interval>(0, 0);

        detector.addAll(list1.getIntervals(), list1.getIntervals());

        for (final Interval i : list2.getIntervals()) {
            final Collection<Interval> as = detector.getOverlaps(i);
            for (final Interval j : as) {
                final Interval tmp = i.intersect(j);

                result.add(tmp);
            }
        }
        return result.uniqued();

    }

    /**
     * A utility function for intersecting a list of IntervalLists, checks for equal dictionaries.
     *
     * @param lists the list of IntervalList
     * @return the intersection of all the IntervalLists in lists.
     */


    public static IntervalList intersection(final Collection<IntervalList> lists) {

        IntervalList intersection = null;
        for (final IntervalList list : lists) {
            if(intersection == null){
                intersection = list;
            }
            else{
                intersection = intersection(intersection, list);
            }
        }
        return intersection;
    }

    /**
     * A utility function for merging a list of IntervalLists, checks for equal dictionaries.
     * Merging does not look for overlapping intervals nor uniquify
     *
     * @param lists a list of IntervalList
     * @return the union of all the IntervalLists in lists.
     */
    public static IntervalList concatenate(final Collection<IntervalList> lists) {
        if(lists.isEmpty()){
            throw new SAMException("Cannot concatenate an empty list of IntervalLists.");
        }

        // Ensure that all the sequence dictionaries agree and merge the lists
        final SAMFileHeader header = lists.iterator().next().getHeader().clone();
        header.setSortOrder(SAMFileHeader.SortOrder.unsorted);

        final IntervalList merged = new IntervalList(header);

        for (final IntervalList in : lists) {
            SequenceUtil.assertSequenceDictionariesEqual(merged.getHeader().getSequenceDictionary(),
                    in.getHeader().getSequenceDictionary());

            merged.addall(in.intervals);
        }

        return merged;
    }

    /**
     * A utility function for finding the union of a list of IntervalLists, checks for equal dictionaries.
     * also looks for overlapping intervals, uniquifies, and sorts (by coordinate)
     *
     * @param lists the list of IntervalList
     * @return the union of all the IntervalLists in lists.
     */
    public static IntervalList union(final Collection<IntervalList> lists) {
        final IntervalList merged = concatenate(lists);
        return merged.uniqued();
    }

    public static IntervalList union(final IntervalList list1, final IntervalList list2) {
        final Collection<IntervalList> duo = CollectionUtil.makeList(list1, list2);
        return IntervalList.union(duo);
    }

    /** inverts an IntervalList and returns one that has exactly all the bases in the dictionary that the original one does not.
     *
     * @param list an IntervalList
     * @return an IntervalList that is complementary to list
     */
    public static IntervalList invert(final IntervalList list) {
        final IntervalList inverse = new IntervalList(list.header.clone());

        final ListMap<Integer,Interval> map = new ListMap<Integer,Interval>();

        //add all the intervals (uniqued and therefore also sorted) to a ListMap from sequenceIndex to a list of Intervals
        for(final Interval i : list.uniqued().getIntervals()){
            map.add(list.getHeader().getSequenceIndex(i.getContig()),i);
        }

        // a counter to supply newly-created intervals with a name
        int intervals = 0;

        //iterate over the contigs in the dictionary
        for (final SAMSequenceRecord samSequenceRecord : list.getHeader().getSequenceDictionary().getSequences()) {
            final Integer sequenceIndex = samSequenceRecord.getSequenceIndex();
            final String sequenceName   = samSequenceRecord.getSequenceName();
            final int sequenceLength    = samSequenceRecord.getSequenceLength();

            Integer lastCoveredPosition = 0; //start at beginning of sequence
            //iterate over list of intervals that are in sequence
            if (map.containsKey(sequenceIndex)) // if there are intervals in the ListMap on this contig, iterate over them (in order)
                for (final Interval i : map.get(sequenceIndex)) {
                    if (i.getStart() > lastCoveredPosition + 1) //if there's space between the last interval and the current one, add an interval between them
                        inverse.add(new Interval(sequenceName, lastCoveredPosition + 1, i.getStart() - 1, false, "interval-" + (++intervals)));
                    lastCoveredPosition = i.getEnd(); //update the last covered position
                }
            //finally, if there's room between the last covered position and the end of the sequence, add an interval
            if (sequenceLength > lastCoveredPosition) //if there's space between the last interval and the next
                // one, add an interval. This also covers the case that there are no intervals in the ListMap for a contig.
                inverse.add(new Interval(sequenceName, lastCoveredPosition + 1, sequenceLength, false, "interval-" + (++intervals)));
        }

        return inverse;
    }

    /**
     * A utility function for subtracting a collection of IntervalLists from another. Resulting loci are those that are in the first collection
     * but not the second.
     *
     * @param lhs the collection of IntervalList from which to subtract intervals
     * @param rhs the collection of intervals to subtract
     * @return an IntervalList comprising all loci that are in the first collection but not the second  lhs-rhs=answer.
     */
    public static IntervalList subtract(final Collection<IntervalList> lhs, final Collection<IntervalList> rhs) {
        return intersection(
                union(lhs),
                invert(union(rhs)));
    }

    /**
     * A utility function for subtracting a single IntervalList from another. Resulting loci are those that are in the first List
     * but not the second.
     *
     * @param lhs the IntervalList from which to subtract intervals
     * @param rhs the IntervalList to subtract
     * @return an IntervalList comprising all loci that are in first IntervalList but not the second. lhs-rhs=answer
     */
    public static IntervalList subtract(final IntervalList lhs, final IntervalList rhs) {
        return subtract(Collections.singletonList(lhs),
                Collections.singletonList(rhs));
    }

    /**
     * A utility function for finding the difference between two IntervalLists.
     *
     * @param lists1 the first collection of IntervalLists
     * @param lists2 the second collection of IntervalLists
     * @return the difference between the two intervals, i.e. the loci that are only in one IntervalList but not both
     */
    public static IntervalList difference(final Collection<IntervalList> lists1, final Collection<IntervalList> lists2) {
        return union(
                subtract(lists1, lists2),
                subtract(lists2, lists1));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final IntervalList intervals1 = (IntervalList) o;

        return header.equals(intervals1.header) && intervals.equals(intervals1.intervals);
    }

    @Override
    public int hashCode() {
        int result = header.hashCode();
        result = 31 * result + intervals.hashCode();
        return result;
    }
}

/**
 * Comparator that orders intervals based on their sequence index, by coordinate
 * then by strand and finally by name.
 */
class IntervalCoordinateComparator implements Comparator<Interval>, Serializable {
    private static final long serialVersionUID = 1L;

    private final SAMFileHeader header;

    /** Constructs a comparator using the supplied sequence header. */
    IntervalCoordinateComparator(final SAMFileHeader header) {
        this.header = header;
    }

    public int compare(final Interval lhs, final Interval rhs) {
        final int lhsIndex = this.header.getSequenceIndex(lhs.getContig());
        final int rhsIndex = this.header.getSequenceIndex(rhs.getContig());
        int retval = lhsIndex - rhsIndex;

        if (retval == 0) retval = lhs.getStart() - rhs.getStart();
        if (retval == 0) retval = lhs.getEnd()   - rhs.getEnd();
        if (retval == 0) {
            if (lhs.isPositiveStrand() && rhs.isNegativeStrand()) retval = -1;
            else if (lhs.isNegativeStrand() && rhs.isPositiveStrand()) retval = 1;
        }
        if (retval == 0) {
            if (lhs.getName() == null) {
                if (rhs.getName() == null) return 0;
                else return -1;
            } else if (rhs.getName() == null) {
                return 1;
            }
            else {
                return lhs.getName().compareTo(rhs.getName());
            }
        }

        return retval;
    }
}