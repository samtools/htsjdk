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

import java.util.*;

/**
 * Utility class to efficiently do in memory overlap detection between a large
 * set of mapping like objects, and one or more candidate mappings.
 *
 * You can use it for example to detect all locatables overlapping a given set of locatables:
 * <pre>{@code
 *    OverlapDetector<Locatable> detector = OverlapDetector.create(locatables);
 *    Set<Locatable> overlaps = detector.getOverlaps(query);
 *
 *    boolean anyOverlap = detector.overlapsAny(query); //faster API for checking presence of any overlap
 * }</pre>
 */
public class OverlapDetector<T> {
    private final Map<Object, IntervalTree<Set<T>>> cache = new HashMap<>();
    private final int lhsBuffer;
    private final int rhsBuffer;

    /**
     * Constructs an overlap detector.
     * @param lhsBuffer the amount by which to "trim" coordinates of mappings on the left
     *                  hand side when calculating overlaps
     * @param rhsBuffer the amount by which to "trim" coordinates of mappings on the right
     *                  hand side when calculating overlaps
     */
    public OverlapDetector(int lhsBuffer, int rhsBuffer) {
        this.lhsBuffer = lhsBuffer;
        this.rhsBuffer = rhsBuffer;
    }

    /**
     * Creates a new OverlapDetector with no trim and the given set of intervals.
     */
    public static <T extends Locatable> OverlapDetector<T> create(final List<T> intervals) {
        final OverlapDetector<T> detector = new OverlapDetector<>(0, 0);
        detector.addAll(intervals, intervals);
        return detector;
    }

    /** Adds a Locatable to the set of Locatables against which to match candidates. */
    public void addLhs(final T object, final Locatable interval) {
        if (object == null) {
            throw new IllegalArgumentException("null object");
        }
        if (interval == null) {
            throw new IllegalArgumentException("null interval");
        }
        final String seqId = interval.getContig();

        IntervalTree<Set<T>> tree = this.cache.get(seqId);
        if (tree == null) {
            tree = new IntervalTree<>();
            this.cache.put(seqId, tree);
        }

        final int start = interval.getStart() + this.lhsBuffer;
        final int end   = interval.getEnd()   - this.lhsBuffer;

        final Set<T> objects = new HashSet<>(1);
        objects.add(object);
        if (start <= end) {  // Don't put in sequences that have no overlappable bases
            final Set<T> alreadyThere = tree.put(start, end, objects);
            if (alreadyThere != null) {
                alreadyThere.add(object);
                tree.put(start, end, alreadyThere);
            }
        }
    }

    /**
     * Adds all items to the overlap detector.
     *
     * The order of the lists matters only in the sense that it needs to be the same for the intervals
     * and the corresponding objects.
     */
    public void addAll(final List<T> objects, final List<? extends Locatable> intervals) {
        if (objects == null) {
            throw new IllegalArgumentException("null objects");
        }
        if (intervals == null) {
            throw new IllegalArgumentException("null intervals");
        }
        if (objects.size() != intervals.size()) {
            throw new IllegalArgumentException("Objects and intervals must be the same size but were " + objects.size() + " and " + intervals.size());
        }

        for (int i=0; i<objects.size(); ++i) {
            addLhs(objects.get(i), intervals.get(i));
        }
    }

    /**
     * Gets all the objects that could be returned by the overlap detector.
     */
    public Set<T> getAll() {
        final Set<T> all = new HashSet<>();
        for (final IntervalTree<Set<T>> tree : this.cache.values()) {
            for (IntervalTree.Node<Set<T>> node : tree) {
                all.addAll(node.getValue());
            }
        }
        return all;
    }

    /**
     * Returns true iff the given locatable overlaps any locatable in this detector.
     *
     * This is a performance shortcut API functionally equivalent to:
     * <pre>{@code
     *      ! getOverlaps(locatable).isEmpty()
     * }</pre>
     */
    public boolean overlapsAny(final Locatable locatable) {
        if (locatable == null) {
            throw new IllegalArgumentException("null locatable");
        }
        final String seqId = locatable.getContig();
        final IntervalTree<Set<T>> tree = this.cache.get(seqId);
        if (tree == null) {
            return false;
        }
        final int start = locatable.getStart() + this.rhsBuffer;
        final int end   = locatable.getEnd()   - this.rhsBuffer;

        if (start > end) {
            return false;
        }

        final Iterator<IntervalTree.Node<Set<T>>> it = tree.overlappers(start, end);
        while (it.hasNext()) {
            final IntervalTree.Node<Set<T>> node = it.next();
            if (!node.getValue().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the Set of objects that overlap the provided locatable.
     * The returned set may not be modifiable.
     */
    public Set<T> getOverlaps(final Locatable locatable)  {
        if (locatable == null) {
            throw new IllegalArgumentException("null locatable");
        }
        final String seqId = locatable.getContig();
        final IntervalTree<Set<T>> tree = this.cache.get(seqId);
        if (tree == null) {
            return Collections.emptySet();
        }
        final int start = locatable.getStart() + this.rhsBuffer;
        final int end   = locatable.getEnd()   - this.rhsBuffer;

        if (start > end) {
            return Collections.emptySet();
        }

        final Set<T> matches = new HashSet<>();
        final Iterator<IntervalTree.Node<Set<T>>> it = tree.overlappers(start, end);
        while (it.hasNext()) {
            final IntervalTree.Node<Set<T>> node = it.next();
            matches.addAll(node.getValue());
        }
        return matches;
    }
}
