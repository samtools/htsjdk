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

package htsjdk.tribble.index.interval;

import htsjdk.tribble.Feature;
import htsjdk.tribble.index.Block;
import htsjdk.tribble.index.Index;
import htsjdk.tribble.index.TribbleIndexCreator;
import htsjdk.tribble.index.interval.IntervalTreeIndex.ChrIndex;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Creates interval indexes from a stream of features
 * @author jrobinso
 */
public class IntervalIndexCreator extends TribbleIndexCreator {

    public static int DEFAULT_FEATURE_COUNT = 600;

    /**
     * Maximum number of features stored per interval.
     * @see #DEFAULT_FEATURE_COUNT
     */
    private int featuresPerInterval = DEFAULT_FEATURE_COUNT;

    private final LinkedList<ChrIndex> chrList = new LinkedList<ChrIndex>();

    /**
     * Instance variable for the number of features we currently are storing in the interval
     */
    private int featureCount = 0;

    private final ArrayList<MutableInterval> intervals = new ArrayList<MutableInterval>();

    File inputFile;

    public IntervalIndexCreator(final File inputFile, final int featuresPerInterval) {
        this.inputFile = inputFile;
        this.featuresPerInterval = featuresPerInterval;
    }

    public IntervalIndexCreator(final File inputFile) {
        this(inputFile, DEFAULT_FEATURE_COUNT);
    }

    public void addFeature(final Feature feature, final long filePosition) {
        // if we don't have a chrIndex yet, or if the last one was for the previous contig, create a new one
        if (chrList.isEmpty() || !chrList.getLast().getName().equals(feature.getChr())) {
            // if we're creating a new chrIndex (not the first), make sure to dump the intervals to the old chrIndex
            if (!chrList.isEmpty())
                addIntervalsToLastChr(filePosition);

            // create a new chr index for the current contig
            chrList.add(new ChrIndex(feature.getChr()));
            intervals.clear();
        }

        // if we're about to overflow the current bin, make a new one
        if (featureCount >= featuresPerInterval || intervals.isEmpty()) {
            final MutableInterval i = new MutableInterval();
            i.setStart(feature.getStart());
            i.setStartFilePosition(filePosition);
            if(!intervals.isEmpty()) intervals.get(intervals.size()-1).setEndFilePosition(filePosition);
            featureCount = 0; // reset the feature count
            intervals.add(i);
        }
        
        // make sure we update the ending position of the bin
        intervals.get(intervals.size()-1).setStop(Math.max(feature.getEnd(),intervals.get(intervals.size()-1).getStop()));
        featureCount++;
    }

    /**
     * dump the intervals we have stored to the last chrList entry
     * @param currentPos the current position, for the last entry in the interval list
     */
    private void addIntervalsToLastChr(final long currentPos) {
        for (int x = 0; x < intervals.size(); x++) {
            if (x == intervals.size()-1) intervals.get(x).setEndFilePosition(currentPos);
            chrList.getLast().insert(intervals.get(x).toInterval());
        }
    }

    /**
     * finalize the index; create a tree index given the feature list passed in so far
     * @param finalFilePosition the final file position, for indexes that have to close out with the final position
     * @return a Tree Index
     */
    public Index finalizeIndex(final long finalFilePosition) {
        final IntervalTreeIndex featureIndex = new IntervalTreeIndex(inputFile.getAbsolutePath());
        // dump the remaining bins to the index
        addIntervalsToLastChr(finalFilePosition);
        featureIndex.setChrIndex(chrList);
        featureIndex.addProperties(properties);
        featureIndex.finalizeIndex();
        return featureIndex;
    }

    public int getFeaturesPerInterval() {
        return featuresPerInterval;
    }
}

/**
 * The interval class isn't mutable; use this private class as a temporary storage until we're ready to make intervals
 */
class MutableInterval {

    // the start, the stop, and the start position
    private int start;
    private int stop;                                                                                                                                               
    private long startFilePosition;
    private long endFilePosition;

    public void setStart(final int start) {
        if (start < 0) throw new IllegalArgumentException("Start must be greater than 0!");
        this.start = start;
    }

    public void setStop(final int stop) {
        if (stop < 0) throw new IllegalArgumentException("Start must be greater than 0!");
        this.stop = stop;
    }

    public void setStartFilePosition(final long startFilePosition) {
        this.startFilePosition = startFilePosition;
    }

    public void setEndFilePosition(final long endFilePosition) {
        this.endFilePosition = endFilePosition;
    }

    public Interval toInterval() {
        return new Interval(start,stop,new Block(startFilePosition, endFilePosition - startFilePosition));
    }

    public int getStop() {
        return stop;
    }
}