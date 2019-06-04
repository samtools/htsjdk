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
import htsjdk.tribble.IntervalList.IntervalListCodec;
import htsjdk.utils.ValidationUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Represents a list of intervals against a reference sequence that can be written to
 * and read from a file.  The file format is relatively simple and reflects the SAM
 * alignment format to a degree.
 *
 * A SAM style header must be present in the file which lists the sequence records
 * against which the intervals are described.  After the header the file then contains
 * records one per line in text format with the following values tab-separated:
 * <ul>
 * <li>Sequence name </li>
 * <li>Start position (1-based)</li>
 * <li>End position (1-based, end inclusive)</li>
 * <li>Strand (either + or -)</li>
 * <li>Interval name (an, ideally unique, name for the interval)</li>
 * </ul>
 *
 * @author Tim Fennell
 * @author Yossi Farjoun
 */
public class IntervalList implements Iterable<Interval> {
    /**
     * @deprecated Use {@link IOExtensions#INTERVAL_LIST_FILE_EXTENSION} instead.
     */
    @Deprecated
    public static final String INTERVAL_LIST_FILE_EXTENSION = IOExtensions.INTERVAL_LIST_FILE_EXTENSION;

    private final SAMFileHeader header;
    private final List<Interval> intervals = new ArrayList<>();

    private static final Log log = Log.getInstance(IntervalList.class);

    /**
     * Constructs a new interval list using the supplied header information.
     */
    public IntervalList(final SAMFileHeader header) {
        ValidationUtils.nonNull(header, "SAMFileHeader");
        this.header = header;
    }

    /**
     * Constructs a new interval list using the supplied header information.
     */
    public IntervalList(final SAMSequenceDictionary dict) {
        this(new SAMFileHeader(dict));
    }

    /**
     * Gets the header (if there is one) for the interval list.
     */
    public SAMFileHeader getHeader() {
        return header;
    }

    /**
     * Returns an iterator over the intervals.
     */
    @Override
    public Iterator<Interval> iterator() {
        return this.intervals.iterator();
    }

    /**
     * Adds an interval to the list of intervals.
     */
    public void add(final Interval interval) {
        ValidationUtils.nonNull(header.getSequence(interval.getContig()),
                () -> String.format("Cannot add interval %s, contig not in header", interval.toString()));

        this.intervals.add(interval);
    }

    /**
     * Adds a Collection of intervals to the list of intervals.
     */
    public void addall(final Collection<Interval> intervals) {
        //use this instead of addAll so that the contig checking happens.
        for (Interval interval : intervals) {
            add(interval);
        }
    }

    /**
     * Sorts the internal collection of intervals by coordinate.
     *
     * Note: this function modifies the object in-place and is therefore difficult to work with.
     *
     * @deprecated use {@link #sorted()} instead.
     */
    @Deprecated
    public void sort() {
        this.intervals.sort(new IntervalCoordinateComparator(this.header));
        this.header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
    }

    /**
     * Returns a new IntervalList where each interval is padded by the specified amount of bases.
     */
    public IntervalList padded(final int before, final int after) {
        if (before < 0 || after < 0) {
            throw new IllegalArgumentException("Padding values must be >= 0.");
        }
        final IntervalList padded = new IntervalList(this.getHeader().clone());
        final SAMSequenceDictionary dict = padded.getHeader().getSequenceDictionary();
        for (final Interval i : this) {
            final SAMSequenceRecord seq = dict.getSequence(i.getContig());
            final int start = Math.max(1, i.getStart() - before);
            final int end = Math.min(seq.getSequenceLength(), i.getEnd() + after);
            padded.add(new Interval(i.getContig(), start, end, i.isNegativeStrand(), i.getName()));
        }

        return padded;
    }

    /**
     * Returns a new IntervalList where each interval is padded by 'padding' bases on each side.
     */
    public IntervalList padded(final int padding) {
        return padded(padding, padding);
    }

    /**
     * returns an independent sorted IntervalList
     */
    public IntervalList sorted() {
        final IntervalList sorted = IntervalList.copyOf(this);
        sorted.intervals.sort(new IntervalCoordinateComparator(sorted.header));
        sorted.header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        return sorted;
    }

    /**
     * Returned an independent IntervalList that is sorted and uniquified.
     */
    public IntervalList uniqued() {
        return uniqued(true);
    }

    /**
     * Returned an independent IntervalList that is sorted and uniquified.
     *
     * @param concatenateNames If false, interval names are not concatenated when merging intervals to save space.
     */
    public IntervalList uniqued(final boolean concatenateNames) {
        final List<Interval> tmp = getUniqueIntervals(sorted(), concatenateNames);
        final IntervalList value = new IntervalList(this.header.clone());
        value.intervals.addAll(tmp);
        return value;
    }

    /**
     * Sorts and uniques the list of intervals held within this interval list.
     *
     * Note: this function modifies the object in-place and is therefore difficult to work with.
     *
     * @deprecated use {@link #uniqued()} instead.
     */
    @Deprecated
    public void unique() {
        unique(true);
    }

    /**
     * Sorts and uniques the list of intervals held within this interval list.
     *
     * Note: this function modifies the object in-place and is therefore difficult to work with.
     *
     * @param concatenateNames If false, interval names are not concatenated when merging intervals to save space.
     * @deprecated use {@link #uniqued(boolean)} instead.
     */
    @Deprecated
    public void unique(final boolean concatenateNames) {
        sort();
        final List<Interval> tmp = getUniqueIntervals(concatenateNames);
        this.intervals.clear();
        this.intervals.addAll(tmp);
    }

    /**
     * Gets the set of intervals as held internally.
     */
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
     * Note: this function modifies the object in-place and is therefore difficult to work with.
     *
     * @return the set of unique intervals condensed from the contained intervals
     * @deprecated use {@link #uniqued()#getIntervals()} instead.
     */
    @Deprecated
    public List<Interval> getUniqueIntervals() {
        return getUniqueIntervals(true);
    }

    //NO SIDE EFFECTS HERE!

    /**
     * Merges list of intervals and reduces them like htsjdk.samtools.util.IntervalList#getUniqueIntervals()
     *
     * @param concatenateNames If false, the merged interval has the name of the earlier interval.  This keeps name shorter.
     */
    public static List<Interval> getUniqueIntervals(final IntervalList list, final boolean concatenateNames) {
        return getUniqueIntervals(list, true, concatenateNames, false);
    }

    //NO SIDE EFFECTS HERE!

    /**
     * Merges list of intervals and reduces them like htsjdk.samtools.util.IntervalList#getUniqueIntervals()
     *
     * @param concatenateNames   If false, the merged interval has the name of the earlier interval.  This keeps name shorter.
     * @param enforceSameStrands enforce that merged intervals have the same strand, otherwise ignore.
     */
    public static List<Interval> getUniqueIntervals(final IntervalList list, final boolean concatenateNames, final boolean enforceSameStrands) {
        return getUniqueIntervals(list, true, concatenateNames, enforceSameStrands);
    }

    /**
     * Merges list of intervals and reduces them like htsjdk.samtools.util.IntervalList#getUniqueIntervals()
     *
     * @param combineAbuttingIntervals If true, intervals that are abutting will be combined into one interval.
     * @param concatenateNames         If false, the merged interval has the name of the earlier interval.  This keeps name shorter.
     * @param enforceSameStrands       enforce that merged intervals have the same strand, otherwise ignore.
     */
    public static List<Interval> getUniqueIntervals(final IntervalList list, final boolean combineAbuttingIntervals, final boolean concatenateNames, final boolean enforceSameStrands) {

        final List<Interval> intervals;
        if (list.getHeader().getSortOrder() != SAMFileHeader.SortOrder.coordinate) {
            intervals = list.sorted().intervals;
        } else {
            intervals = list.intervals;
        }

        final List<Interval> unique = new ArrayList<>();
        final List<Interval> toBeMerged = new ArrayList<>();
        Interval current = null;

        for (final Interval next : intervals) {
            if (current == null) {
                toBeMerged.add(next);
                current = next;
            } else if (current.intersects(next) || (combineAbuttingIntervals && current.abuts(next))) {
                if (enforceSameStrands && current.isNegativeStrand() != next.isNegativeStrand()) {
                    throw new SAMException("Strands were not equal for: " + current.toString() + " and " + next.toString());
                }
                toBeMerged.add(next);
                current = new Interval(current.getContig(), current.getStart(), Math.max(current.getEnd(), next.getEnd()), current.isNegativeStrand(), null);
            } else {
                // Emit merged/unique interval
                unique.add(merge(toBeMerged, concatenateNames));

                // Set current == next for next iteration
                toBeMerged.clear();
                current = next;
                toBeMerged.add(current);
            }
        }

        if (!toBeMerged.isEmpty()) {
            unique.add(merge(toBeMerged, concatenateNames));
        }
        return unique;
    }

    /**
     * Merges list of intervals and reduces them like {@link #getUniqueIntervals()}.
     *
     * Note: this function modifies the object in-place and is therefore difficult to work with.
     *
     * @param concatenateNames If false, the merged interval has the name of the earlier interval. This keeps name shorter.
     * @deprecated use {@link #uniqued(boolean)#getIntervals()} or {@link #getUniqueIntervals(IntervalList, boolean)} instead.
     */
    @Deprecated
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
     *
     * @param intervals    A list of Interval
     * @param bandMultiple integer value (> 0) to break up intervals in the list at integer multiples of
     * @return list of intervals that are broken up
     */
    public static List<Interval> breakIntervalsAtBandMultiples(final List<Interval> intervals, final int bandMultiple) {
        final List<Interval> brokenUpIntervals = new ArrayList<>();
        for (final Interval interval : intervals) {
            if (interval.getEnd() >= interval.getStart()) {       // Normal, non-empty intervals
                final int startIndex = interval.getStart() / bandMultiple;
                final int endIndex = interval.getEnd() / bandMultiple;
                if (startIndex == endIndex) {
                    brokenUpIntervals.add(interval);
                } else {
                    brokenUpIntervals.addAll(breakIntervalAtBandMultiples(interval, bandMultiple));
                }
            } else {                                  // Special case - empty intervals ex: (100-99)
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
     *
     * @param interval     an Interval
     * @param bandMultiple integer value (> 0) to break up intervals in the list at integer multiples of
     * @return list of intervals that are broken up
     */
    private static List<Interval> breakIntervalAtBandMultiples(final Interval interval, final int bandMultiple) {
        final List<Interval> brokenUpIntervals = new ArrayList<>();

        int startPos = interval.getStart();
        final int startOfIntervalIndex = startPos / bandMultiple;
        int startIndex = startOfIntervalIndex;
        final int endIndex = interval.getEnd() / bandMultiple;
        while (startIndex <= endIndex) {
            int endPos = (startIndex + 1) * bandMultiple - 1;
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

    /**
     * Merges a collection of intervals and optionally concatenates unique names or takes the first name.
     * *
     *
     * @param concatenateNames if true, combine the names of all the intervals with |, otherwise use the name of the first interval.
     * @return a single interval which spans from the minimum input start position to the maximum input end position.
     * The resulting strandedness and contig are those of the first input with no validation.
     */
    static Interval merge(final Iterable<Interval> intervals, final boolean concatenateNames) {
        final Interval first = intervals.iterator().next();
        final String chrom = first.getContig();
        int start = first.getStart();
        int end = first.getEnd();
        final boolean neg = first.isNegativeStrand();
        final LinkedHashSet<String> names = new LinkedHashSet<>();
        final String name;

        for (final Interval i : intervals) {
            if (i.getName() != null) {
                names.add(i.getName());
            }
            start = Math.min(start, i.getStart());
            end = Math.max(end, i.getEnd());
        }

        if (names.isEmpty()) {
            name = null;
        } else if (concatenateNames) {
            name = StringUtil.join("|", names);
        } else {
            name = names.iterator().next();
        }

        return new Interval(chrom, start, end, neg, name);
    }

    /**
     * Gets the (potentially redundant) sum of the length of the intervals in the list.
     */
    public long getBaseCount() {
        return Interval.countBases(this.intervals);
    }

    /**
     * Gets the count of unique bases represented by the intervals in the list.
     */
    public long getUniqueBaseCount() {
        return uniqued().getBaseCount();
    }

    /**
     * Returns the count of intervals in the list.
     */
    public int size() {
        return this.intervals.size();
    }

    /**
     * creates a independent copy of the given IntervalList
     */
    public static IntervalList copyOf(final IntervalList list) {
        final IntervalList clone = new IntervalList(list.header.clone());
        clone.intervals.addAll(list.intervals);
        return clone;
    }

    /**
     * Parses an interval list from a file.
     *
     * @param file the file containing the intervals
     * @return an IntervalList object that contains the headers and intervals from the file
     */
    public static IntervalList fromFile(final File file) {
        return fromPath(IOUtil.toPath(file));
    }

    /**
     * Parses an interval list from a path.
     *
     * @param path the path containing the intervals
     * @return an IntervalList object that contains the headers and intervals from the path
     */
    public static IntervalList fromPath(final Path path) {
        try (final BufferedReader reader = IOUtil.openFileForBufferedReading(path)) {
            return fromReader(reader);
        } catch (final IOException e) {
            throw new SAMException(String.format("Failed to close file %s after reading", path.toUri().toString()));
        }
    }

    /**
     * Creates an IntervalList from the given sequence name
     *
     * @param header       header to use to create IntervalList
     * @param sequenceName name of sequence in header
     * @return a new intervalList with given header that contains the reference name
     */
    public static IntervalList fromName(final SAMFileHeader header, final String sequenceName) {
        final IntervalList ref = new IntervalList(header);
        ref.add(new Interval(sequenceName, 1, header.getSequence(sequenceName).getSequenceLength()));

        return ref;
    }

    /**
     * Calls {@link #fromFile(java.io.File)} on the provided files, and returns their {@link #concatenate(Collection)}
     */
    public static IntervalList fromFiles(final Collection<File> intervalListFiles) {
        final Collection<IntervalList> intervalLists = new ArrayList<>();
        for (final File file : intervalListFiles) {
            intervalLists.add(IntervalList.fromFile(file));
        }
        return IntervalList.concatenate(intervalLists);
    }

    /**
     * Parses an interval list from a reader in a stream based fashion.
     *
     * @param in a BufferedReader that can be read from. Caller is responsible to close reader as needed.
     * @return an IntervalList object that contains the headers and intervals from the file
     * @throws IllegalArgumentException if start or end are less than 1 or greater than the length of the sequence
     */
    public static IntervalList fromReader(final BufferedReader in) {

        try {
            // Setup a reader and parse the header
            final StringBuilder builder = new StringBuilder(4096);
            String line = null;

            while ((line = in.readLine()) != null) {
                if (line.startsWith("@")) {
                    builder.append(line).append('\n');
                } else {
                    break;
                }
            }

            if (builder.length() == 0) {
                throw new IllegalStateException("Interval list file must contain header. ");
            }

            final BufferedLineReader headerReader = BufferedLineReader.fromString(builder.toString());
            final SAMTextHeaderCodec codec = new SAMTextHeaderCodec();
            final IntervalList list = new IntervalList(codec.decode(headerReader, "BufferedReader"));
            final SAMSequenceDictionary dict = list.getHeader().getSequenceDictionary();

            // There might not be any lines after the header, in which case we should return an empty list
            if (line == null) {
                return list;
            }

            final IntervalListCodec intervalListCodec = new IntervalListCodec(dict);

            // Then read in the intervals
            do {
                final Optional<Interval> maybeInterval = Optional.ofNullable(intervalListCodec.decode(line));
                maybeInterval.ifPresent(list.intervals::add);
            }
            while ((line = in.readLine()) != null);

            return list;
        } catch (final IOException ioe) {
            throw new SAMException("Error parsing interval list.", ioe);
        }
    }

    /**
     * Writes out the list of intervals to the supplied path.
     *
     * @param path a path to write to.  If exists it will be overwritten.
     */
    public void write(final Path path) {
        try {
            try (IntervalListWriter writer = new IntervalListWriter(path, this.header)) {
                for (final Interval interval : this) {
                    writer.write(interval);
                }
            }
        } catch (final IOException ioe) {
            throw new SAMException("Error writing out interval list to file: " + path.toAbsolutePath(), ioe);
        }
    }

    /**
     * Writes out the list of intervals to the supplied file.
     *
     * @param file a file to write to.  If exists it will be overwritten.
     */
    public void write(final File file) {
        this.write(file.toPath());
    }

    /**
     * A utility function for generating the intersection of two IntervalLists, checks for equal dictionaries.
     *
     * @param list1 the first IntervalList
     * @param list2 the second IntervalList
     * @return the intersection of list1 and list2.
     */

    public static IntervalList intersection(final IntervalList list1, final IntervalList list2) {

        // Ensure that all the sequence dictionaries agree and merge the lists
        SequenceUtil.assertSequenceDictionariesEqual(
                list1.getHeader().getSequenceDictionary(),
                list2.getHeader().getSequenceDictionary());

        final IntervalList result = new IntervalList(list1.getHeader().clone());

        final OverlapDetector<Interval> detector = OverlapDetector.create(list1.getIntervals());

        for (final Interval i : list2.getIntervals()) {
            detector.getOverlaps(i).stream()
                    .map(i::intersect)
                    .forEach(result::add);
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
        return lists.stream()
                .reduce(IntervalList::intersection)
                .orElse(null);
    }

    /**
     * A utility function for merging a two IntervalLists, checks for equal dictionaries.
     * Merging does not look for overlapping intervals nor uniquify
     *
     * @param list1 the first list
     * @param list2 the second list
     * @return the union of all the IntervalLists in lists.
     */
    public static IntervalList concatenate(final IntervalList list1, final IntervalList list2) {

        final SAMFileHeader header = list1.getHeader().clone();

        // Ensure that all the sequence dictionaries agree and merge the lists
        return new IntervalList(header).addOther(list1).addOther(list2);
    }


    /**
     * A method for  concatenating the intervals from one list to this one, checks for equal dictionaries.
     * Does not look for overlapping intervals nor uniquify.
     *
     * @param other the other list
     * @return the modified this
     */
    public IntervalList addOther(final IntervalList other) {

        SequenceUtil.assertSequenceDictionariesEqual(
                this.getHeader().getSequenceDictionary(),
                other.getHeader().getSequenceDictionary());
        this.header.setSortOrder(SAMFileHeader.SortOrder.unsorted);
        this.addall(other.intervals);
        return this;
    }


    /**
     * A utility function for concatenating a list of IntervalLists, checks for equal dictionaries.
     * Concatenating does not look for overlapping intervals nor uniquify the intervals.
     *
     * @param lists a list of IntervalList
     * @return the union of all the IntervalLists in lists.
     */
    public static IntervalList concatenate(final Collection<IntervalList> lists) {

        final SAMFileHeader header = lists.stream()
                .findFirst()
                .map(IntervalList::getHeader)
                .orElseThrow(
                        () -> new IllegalArgumentException("Cannot combine empty collection of IntervalLists"));

        return lists.stream()
                .reduce(new IntervalList(header), IntervalList::addOther, IntervalList::concatenate);
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

    /**
     * inverts an IntervalList and returns one that has exactly all the bases in the dictionary that the original one does not.
     *
     * @param list an IntervalList
     * @return an IntervalList that is complementary to list
     */
    public static IntervalList invert(final IntervalList list) {
        final IntervalList inverse = new IntervalList(list.header.clone());

        final ListMap<Integer, Interval> map = new ListMap<>();

        //add all the intervals (uniqued and therefore also sorted) to a ListMap from sequenceIndex to a list of Intervals
        for (final Interval i : list.uniqued().getIntervals()) {
            final int sequenceIndex = list.getHeader().getSequenceIndex(i.getContig());
            ValidationUtils.validateArg(sequenceIndex != SAMSequenceRecord.UNAVAILABLE_SEQUENCE_INDEX,
                    () -> String.format("Cannot add interval %s, contig not in header", i.toString()));
            map.add(sequenceIndex, i);
        }

        // a counter to supply newly-created intervals with a name
        int intervals = 0;

        //iterate over the contigs in the dictionary
        for (final SAMSequenceRecord samSequenceRecord : list.getHeader().getSequenceDictionary().getSequences()) {
            final Integer sequenceIndex = samSequenceRecord.getSequenceIndex();
            final String sequenceName = samSequenceRecord.getSequenceName();
            final int sequenceLength = samSequenceRecord.getSequenceLength();

            int lastCoveredPosition = 0; //start at beginning of sequence
            //iterate over list of intervals that are in sequence
            if (map.containsKey(sequenceIndex)) {
                // if there are intervals in the ListMap on this contig, iterate over them (in order)

                for (final Interval i : map.get(sequenceIndex)) {
                    if (i.getStart() > lastCoveredPosition + 1) {
                        //if there's space between the last interval and the current one, add an interval between them
                        inverse.add(new Interval(sequenceName, lastCoveredPosition + 1, i.getStart() - 1, false, "interval-" + (++intervals)));
                    }
                    lastCoveredPosition = i.getEnd(); //update the last covered position
                }
            }
            if (sequenceLength > lastCoveredPosition) {
                // finally, if there's space between the last interval and the next
                // one, add an interval. This also covers the case that there are no intervals in the ListMap for a contig.
                inverse.add(new Interval(sequenceName, lastCoveredPosition + 1, sequenceLength, false, "interval-" + (++intervals)));
            }
        }

        return inverse;
    }

    /**
     * A utility function for subtracting one IntervalLists from another. Resulting loci are those that are in the first
     * but not the second.
     *
     * @param lhs the IntervalList from which to subtract intervals
     * @param rhs the IntervalList to subtract
     * @return an IntervalList comprising all loci that are in the first IntervalList but not the second  lhs-rhs=answer.
     */
    public static IntervalList subtract(final IntervalList lhs, final IntervalList rhs) {
        return intersection(lhs, invert(rhs));
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
        return subtract(
                union(lhs),
                union(rhs));
    }

    /**
     * A utility function for finding the difference between two IntervalLists.
     *
     * @param lists1 the first collection of IntervalLists
     * @param lists2 the second collection of IntervalLists
     * @return the difference between the two intervals, i.e. the loci that are only in one IntervalList but not both
     */
    public static IntervalList difference(final Collection<IntervalList> lists1, final Collection<IntervalList> lists2) {
        return difference(union(lists1), union(lists2));
    }

    /**
     * A utility function for finding the difference between two IntervalLists.
     *
     * @param list1 the first collection of IntervalLists
     * @param list2 the second collection of IntervalLists
     * @return the difference between the two intervals, i.e. the loci that are only in one IntervalList but not both
     */
    public static IntervalList difference(final IntervalList list1, final IntervalList list2) {
        return union(
                subtract(list1, list2),
                subtract(list2, list1));
    }

    /**
     * A utility function for finding the intervals in the first list that have at least 1bp overlap with any interval
     * in the second list.
     *
     * @param lhs the first collection of IntervalLists
     * @param rhs the second collection of IntervalLists
     * @return an IntervalList comprising of all intervals in the first IntervalList that have at least 1bp overlap with
     * any interval in the second.
     */
    public static IntervalList overlaps(final IntervalList lhs, final IntervalList rhs) {

        final SAMFileHeader header = lhs.getHeader().clone();
        SequenceUtil.assertSequenceDictionariesEqual(header.getSequenceDictionary(),
                rhs.getHeader().getSequenceDictionary());

        header.setSortOrder(SAMFileHeader.SortOrder.unsorted);

        // Create an overlap detector on rhs
        final IntervalList overlapIntervals = new IntervalList(header);
        overlapIntervals.addall(rhs.getIntervals());

        final OverlapDetector<Integer> detector = new OverlapDetector<>(0, 0);
        final int dummy = -1; // NB: since we don't actually use the returned objects, we can use a dummy value
        for (final Interval interval : overlapIntervals.sorted().uniqued()) {
            detector.addLhs(dummy, interval);
        }

        // Go through each input interval in lhs and see if overlaps any interval in rhs
        final IntervalList merged = new IntervalList(header);
        for (final Interval interval : lhs.getIntervals()) {
            if (detector.overlapsAny(interval)) {
                merged.add(interval);
            }
        }

        return merged;
    }

    /**
     * A utility function for finding the intervals in the first list that have at least 1bp overlap with any interval
     * in the second list.
     *
     * @param lists1 the first collection of IntervalLists
     * @param lists2 the second collection of IntervalLists
     * @return an IntervalList comprising of all intervals in the first collection of lists that have at least 1bp
     * overlap with any interval in the second lists.
     */
    public static IntervalList overlaps(final Collection<IntervalList> lists1, final Collection<IntervalList> lists2) {
        if (lists1.isEmpty()) {
            throw new SAMException("Cannot call overlaps with the first collection having empty list of IntervalLists.");
        }
        return overlaps(concatenate(lists1), union(lists2));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

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
