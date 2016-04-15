/*
 * Copyright (c) 2007-2010 by The Broad Institute, Inc. and the Massachusetts Institute of Technology.
 * All Rights Reserved.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL), Version 2.1 which
 * is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 *
 * THE SOFTWARE IS PROVIDED "AS IS." THE BROAD AND MIT MAKE NO REPRESENTATIONS OR WARRANTIES OF
 * ANY KIND CONCERNING THE SOFTWARE, EXPRESS OR IMPLIED, INCLUDING, WITHOUT LIMITATION, WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NONINFRINGEMENT, OR THE ABSENCE OF LATENT
 * OR OTHER DEFECTS, WHETHER OR NOT DISCOVERABLE.  IN NO EVENT SHALL THE BROAD OR MIT, OR THEIR
 * RESPECTIVE TRUSTEES, DIRECTORS, OFFICERS, EMPLOYEES, AND AFFILIATES BE LIABLE FOR ANY DAMAGES OF
 * ANY KIND, INCLUDING, WITHOUT LIMITATION, INCIDENTAL OR CONSEQUENTIAL DAMAGES, ECONOMIC
 * DAMAGES OR INJURY TO PROPERTY AND LOST PROFITS, REGARDLESS OF WHETHER THE BROAD OR MIT SHALL
 * BE ADVISED, SHALL HAVE OTHER REASON TO KNOW, OR IN FACT SHALL KNOW OF THE POSSIBILITY OF THE
 * FOREGOING.
 */

package htsjdk.tribble;

import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.IntervalList;
import htsjdk.samtools.util.Locatable;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * Basic interface that feature sources need to match
 *
 * @param <T> a feature type
 */
public interface FeatureReader<T extends Feature> extends Closeable {

    /**
     * Iterate over features that overlap the given interval.  Only valid to call this if hasIndex() == true.
     *
     * @param chr   Reference sequence of interest
     * @param start start of interval of interest
     * @param end   end of interval of interest
     * @return iterator over the Features overlapping the interval.
     */
    public CloseableTribbleIterator<T> query(final String chr, final int start, final int end) throws IOException;

    /**
     * Iterate over features that overlap the given interval.  Only valid to call this if hasIndex() == true.
     *
     * @param loc the interval of interest
     * @return iterator over the Features overlapping the interval.
     */
    public CloseableTribbleIterator<T> query(final Locatable loc) throws IOException;

    /**
     * Iterate over features that match a set of intervals.
     *
     * @param locs the intervals of interest
     * @return iterator over the Features overlapping this intervals
     */
    public CloseableTribbleIterator<T> query(final List<Locatable> locs) throws IOException;

    /**
     * Whether the reader has an index or not
     *
     * @return {@code true} if it have index; {@code false} otherwise
     */
    public boolean hasIndex();

    /**
     * Iterate through the file in order
     *
     * @return a new iterator
     */
    public CloseableTribbleIterator<T> iterator() throws IOException;

    /**
     * Get sequence names in this file. Only valid to call this if hasIndex() == true.
     *
     * @return list of sequence names
     */
    public List<String> getSequenceNames();

    /**
     * Get the header
     *
     * @return the header
     */
    public Object getHeader();

}
