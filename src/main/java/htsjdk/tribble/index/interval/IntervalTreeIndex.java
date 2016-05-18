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

import htsjdk.tribble.index.AbstractIndex;
import htsjdk.tribble.index.Block;
import htsjdk.tribble.util.LittleEndianInputStream;
import htsjdk.tribble.util.LittleEndianOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Index based on an interval tree
 *
 * @author jrobinso
 * @date Jul 9, 2010
 * @see IntervalTree
 */
public class IntervalTreeIndex extends AbstractIndex {
    public static final int INDEX_TYPE = IndexType.INTERVAL_TREE.fileHeaderTypeIdentifier;

    /**
     * Load from file.
     *
     * @param inputStream This method assumes that the input stream is already buffered as appropriate.  Caller
     *                    should close after this object is constructed.
     */
    public IntervalTreeIndex(final InputStream inputStream) throws IOException {
        final LittleEndianInputStream dis = new LittleEndianInputStream(inputStream);
        validateIndexHeader(INDEX_TYPE, dis);
        read(dis);
    }

    /**
     * Prepare to build an index.
     *
     * @param featureFile File which we are indexing
     */
    public IntervalTreeIndex(final String featureFile) {
        super(featureFile);
    }

    @Override
    public Class getChrIndexClass() {
        return ChrIndex.class;
    }

    @Override
    protected int getType() {
        return INDEX_TYPE;
    }

    /**
     * Add a new interval to this index
     *
     * @param chr      Chromosome
     * @param interval
     */
    public void insert(final String chr, final Interval interval) {
        ChrIndex chrIdx = (ChrIndex) chrIndices.get(chr);
        if (chrIdx == null) {
            chrIdx = new ChrIndex(chr);
            chrIndices.put(chr, chrIdx);
        }
        chrIdx.insert(interval);
    }

    protected void setChrIndex(final List<ChrIndex> indicies) {
        for (final ChrIndex index : indicies) {
            chrIndices.put(index.getName(), index);
        }
    }

    public void printTree() {

        for (final String chr : chrIndices.keySet()) {
            System.out.println(chr + ":");
            final ChrIndex chrIdx = (ChrIndex) chrIndices.get(chr);
            chrIdx.printTree();
            System.out.println();
        }
    }

    public static class ChrIndex implements htsjdk.tribble.index.ChrIndex {

        IntervalTree tree;
        String name;

        /**
         * Default constructor needed for factory methods -- DO NOT REMOVE
         */
        public ChrIndex() {

        }

        public ChrIndex(final String name) {
            this.name = name;
            tree = new IntervalTree();
        }

        public String getName() {
            return name;
        }

        public void insert(final Interval iv) {
            tree.insert(iv);
        }

        public List<Block> getBlocks() {
            return null;
        }


        public List<Block> getBlocks(final int start, final int end) {

            // Get intervals and build blocks list
            final List<Interval> intervals = tree.findOverlapping(new Interval(start, end));

            // save time (and save throwing an exception) if the blocks are empty, return now
            if (intervals == null || intervals.isEmpty()) return new ArrayList<Block>();

            final Block[] blocks = new Block[intervals.size()];
            int idx = 0;
            for (final Interval iv : intervals) {
                blocks[idx++] = iv.getBlock();
            }

            // Sort blocks by start position
            Arrays.sort(blocks, new Comparator<Block>() {
                public int compare(final Block b1, final Block b2) {
                    // this is a little cryptic because the normal method (b1.getStartPosition() - b2.getStartPosition()) wraps in int space and we incorrectly sort the blocks in extreme cases
                    return b1.getStartPosition() - b2.getStartPosition() < 1 ? -1 : (b1.getStartPosition() - b2.getStartPosition() > 1 ? 1 : 0);
                }
            });

            // Consolidate blocks  that are close together
            final List<Block> consolidatedBlocks = new ArrayList<Block>(blocks.length);
            Block lastBlock = blocks[0];
            consolidatedBlocks.add(lastBlock);
            for (int i = 1; i < blocks.length; i++) {
                final Block block = blocks[i];
                if (block.getStartPosition() < (lastBlock.getEndPosition() + 1000)) {
                    lastBlock.setEndPosition(block.getEndPosition());
                } else {
                    lastBlock = block;
                    consolidatedBlocks.add(lastBlock);
                }
            }

            return consolidatedBlocks;
        }

        public void printTree() {
            System.out.println(tree.toString());
        }

        public void write(final LittleEndianOutputStream dos) throws IOException {

            dos.writeString(name);
            final List<Interval> intervals = tree.getIntervals();

            dos.writeInt(intervals.size());
            for (final Interval interval : intervals) {
                dos.writeInt(interval.start);
                dos.writeInt(interval.end);
                dos.writeLong(interval.getBlock().getStartPosition());
                dos.writeInt((int) interval.getBlock().getSize());
            }

        }

        public void read(final LittleEndianInputStream dis) throws IOException {

            tree = new IntervalTree();

            name = dis.readString();
            int nIntervals = dis.readInt();
            while (nIntervals-- > 0) {

                final int start = dis.readInt();
                final int end = dis.readInt();
                final long pos = dis.readLong();
                final int size = dis.readInt();

                final Interval iv = new Interval(start, end, new Block(pos, size));
                tree.insert(iv);
            }


        }

    }
}
