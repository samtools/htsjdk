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

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * the basic interface that feature sources need to match
 * @param <T> a feature type
 */
public interface FeatureReader<T extends Feature> extends Closeable {

    /**
     * Query the reader for a particular interval corresponding to a contig and a 1-based closed
     *
     * @param chr the contig to be queried
     * @param start the start of the interval (1-based) to be queried
     * @param end the last base in the interval to be queried
     * @return an iterator containing the features that at in the interval.
     * @throws IOException If there's a problem reading or if the reader is not queryable, e.g. if it doesn't have an index.
     */
    CloseableTribbleIterator<T> query(final String chr, final int start, final int end) throws IOException;

    /**
     * Provides access to all the features in the reader
     * @return an iterator to all the features in the reader
     * @throws IOException If there's a problem reading.
     */
    CloseableTribbleIterator<T> iterator() throws IOException;

    /**
     * Closes the reader
     * @throws IOException
     */
    @Override
    void close() throws IOException;

    /**
     * Provides the list of sequenceNames if known. Otherwise will return an empty list.
     * @return  the list of sequenceNames if known. Otherwise will return an empty list.
     */
    List<String> getSequenceNames();

    /**
     * Provide access to the header of the reader
     * @return the header of the reader
     */
    Object getHeader();

    /**
     * @return true if the reader has an index, which means that it can be queried.
     */
    default boolean hasIndex() {
        return false;
    }
}
