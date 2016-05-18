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

import htsjdk.samtools.BAMRecordCodec;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;

import java.io.File;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * This class stores SAMRecords for return.  The purpose of this class is to buffer records that need to be modified or processed in some
 * fashion, and only return (or emit) them when they have been recorded as being fully examined.  If we have too many records in RAM,
 * we can spill over to disk.  The order in which they are given (via SamRecordWithOrdinal) determines their order of being returned.  It is the
 * responsibility of the user of this class to make sure all records have unique index and are added in order.
 *
 * When a record is examined, we also store a result state.  This is currently a boolean to reduce on memory and disk footprint.
 *
 * We store groups of records in blocks and the size of these blocks can be controlled.  If we have too many records in RAM, we start
 * spilling blocks to disk.
 *
 * Users should check isEmpty() to see if any records are still being tracked.  If so, they should check canEmit() to see if the
 * next record can be returned.  If so, they can call next() to get that record.
 *
 * When users are done with this structure, call close().
 *
 * @author bradtaylor
 */
public class SamRecordTrackingBuffer<T extends SamRecordWithOrdinal> {
    private int availableRecordsInMemory; // how many more records can we store in memory
    private final int blockSize; // the size of each block
    private final List<File> tmpDirs; // the list of temporary directories to use
    private long queueHeadRecordIndex; // the index of the head of the buffer
    private long queueTailRecordIndex; // the index of the tail of the buffer
    private final Deque<BufferBlock> blocks; // the queue of blocks, in which records are contained
    private final SAMFileHeader header;

    private final Class<T> clazz; // the class to create

    /**
     * @param maxRecordsInRam how many records to buffer before spilling to disk
     * @param blockSize the number of records in a given block
     * @param tmpDirs the temporary directories to use when spilling to disk
     * @param header the header
     * @param clazz the class that extends SamRecordWithOrdinal
     */
    public SamRecordTrackingBuffer(final int maxRecordsInRam, final int blockSize, final List<File> tmpDirs, final SAMFileHeader header, final Class<T> clazz) {
        this.availableRecordsInMemory = maxRecordsInRam;
        this.blockSize = blockSize;
        this.tmpDirs = tmpDirs;
        this.queueHeadRecordIndex = -1;
        this.queueTailRecordIndex = -1;
        this.blocks = new ArrayDeque<BufferBlock>();
        this.header = header;
        this.clazz = clazz;
    }

    /** Returns true if we are tracking no records, false otherwise */
    public boolean isEmpty() { return (blocks.isEmpty() || this.blocks.getFirst().isEmpty()); }

    /** Returns true if we can return the next record (it has been examined). */
    public boolean canEmit() { return (!this.blocks.isEmpty() && this.blocks.getFirst().canEmit()); }

    /**
     * Add the given SAMRecordIndex to the buffer.  The records must be added in order.
     * @param samRecordWithOrdinal The samRecordWithOrdinal to be added
     */
    public void add(final SamRecordWithOrdinal samRecordWithOrdinal) {
        if (this.isEmpty()) {
            this.queueHeadRecordIndex = samRecordWithOrdinal.getRecordOrdinal();
            this.queueTailRecordIndex = samRecordWithOrdinal.getRecordOrdinal() - 1;
        }
        this.queueTailRecordIndex++;
        if (samRecordWithOrdinal.getRecordOrdinal() != this.queueTailRecordIndex) {
            throw new SAMException("The records were added out of order");
        }
        // If necessary, create a new block, using as much ram as available up to its total size
        if (this.blocks.isEmpty() || !this.blocks.getLast().canAdd()) {
            // once ram is given to a block, we can't give it to another block (until some is recovered from the head of the queue)
            final int blockRam = Math.min(this.blockSize, this.availableRecordsInMemory);
            this.availableRecordsInMemory = this.availableRecordsInMemory - blockRam;
            final BufferBlock block = new BufferBlock(this.blockSize, blockRam, this.tmpDirs, this.header, samRecordWithOrdinal.getRecordOrdinal());
            this.blocks.addLast(block);
        }
        this.blocks.getLast().add(samRecordWithOrdinal);
    }

    /**
     * Returns the next element in the iteration.
     *
     * @return The next element in the iteration.
     * @throws java.util.NoSuchElementException if the buffer is empty.
     * @throws SAMException if the buffer is not competent to emit (canEmit returns false)
     */
    public SamRecordWithOrdinal next() {
        if (this.isEmpty())
            throw new NoSuchElementException("Attempting to remove an element from an empty SamRecordTrackingBuffer");
        final BufferBlock headBlock = this.blocks.getFirst();
        if (!headBlock.canEmit())
            throw new SAMException("Attempting to get a samRecordWithOrdinal from the SamRecordTrackingBuffer that has not been through " +
                    "marked as examined. canEmit() must return true in order to call next()");

        // If the samRecordWithOrdinal was stored in memory, reclaim its ram for use in additional blocks at tail of queue
        // NB: this must be checked before calling next(), as that method updates the block-head
        if (!headBlock.headRecordIsFromDisk()) {
            this.availableRecordsInMemory++;
        }
        final SamRecordWithOrdinal samRecordWithOrdinal = headBlock.next();
        if (headBlock.hasBeenDrained()) {
            blocks.poll(); // remove the block as it is now empty
            headBlock.clear(); // free any disk io resources associated with empty block
        }
        this.queueHeadRecordIndex++;
        return samRecordWithOrdinal;
    }

    /** Removes the next record from this buffer */
    public void remove() { this.next(); }

    /**
     * Return the total number of elements in the queue, both in memory and on disk
     */
    public long size() { return this.queueTailRecordIndex - this.queueHeadRecordIndex + 1; }

    /** Returns the block that holds the sam record at the given index, null if no such block exists */
    private BufferBlock getBlock(final SamRecordWithOrdinal samRecordWithOrdinal) {
        for (final BufferBlock block : this.blocks) {
            if (block.getStartIndex() <= samRecordWithOrdinal.getRecordOrdinal() && block.getEndIndex() >= samRecordWithOrdinal.getRecordOrdinal()) {
                return block;
            }
        }
        return null;
    }

    /** Returns true if this buffer contains the record at the given index, false otherwise */
    public boolean contains(final SamRecordWithOrdinal samRecordWithOrdinal) {
        return (null != getBlock(samRecordWithOrdinal));
    }

    /**
     * Mark the current samRecordWithOrdinal as having been examined.
     *
     * @param samRecordWithOrdinal The samRecordWithOrdinal to be marked
     * @param resultState Boolean flag indicating the result of the examination of this record.
     * @throws SAMException if the provided recordIndex is not found within the SamRecordTrackingBuffer
     */
    public void setResultState(final SamRecordWithOrdinal samRecordWithOrdinal, final boolean resultState) {
        final BufferBlock block = getBlock(samRecordWithOrdinal);
        if (null == block) {
            throw new SAMException("Attempted to set examined information on a samRecordWithOrdinal whose index is not found " +
                    "in the SamRecordTrackingBuffer. recordIndex: " + samRecordWithOrdinal.getRecordOrdinal());
        }
        block.setResultState(samRecordWithOrdinal, resultState);
    }

    /**
     * Close IO resources associated with each underlying BufferBlock
     */
    public void close() {
        while (!blocks.isEmpty()) {
            final BufferBlock block = blocks.pollFirst();
            block.clear();
        }
    }

    /**
     * This stores blocks of records, either in memory or on disk, or both!
     */
    private class BufferBlock {
        private final DiskBackedQueue<SAMRecord> recordsQueue;
        private final int maxBlockSize;
        private long currentStartIndex;
        private final long originalStartIndex;
        private long endIndex;

        private final BitSet wasExaminedIndexes;
        private final BitSet resultStateIndexes;

        /** Creates an empty block buffer, with an allowable # of records in RAM */
        public BufferBlock(final int maxBlockSize, final int maxBlockRecordsInMemory, final List<File> tmpDirs,
                           final SAMFileHeader header,
                           final long originalStartIndex) {
            this.recordsQueue = DiskBackedQueue.newInstance(new BAMRecordCodec(header), maxBlockRecordsInMemory, tmpDirs);
            this.maxBlockSize = maxBlockSize;
            this.currentStartIndex = 0;
            this.endIndex = -1;
            this.wasExaminedIndexes = new BitSet(maxBlockSize);
            this.resultStateIndexes = new BitSet(maxBlockSize);
            this.originalStartIndex = originalStartIndex;
        }

        /**
         * Check that the tail of the block has not grown past the maximum block size (even if records were popped) and that the underlying queue can be added to.
         * TODO - reimplement with a circular byte array buffer PROVIDED RECORDS ARE IN MEMORY
         * @return
         */
        public boolean canAdd() { return (this.endIndex - this.originalStartIndex + 1) < this.maxBlockSize && this.recordsQueue.canAdd(); }

        /** Returns true if the record at the front of the buffer is on disk */
        public boolean headRecordIsFromDisk() { return this.recordsQueue.headRecordIsFromDisk(); }

        /**
         * Check whether we have read all possible records from this block (and it is available to be destroyed)
         * @return true if we have read the last /possible/ record (ie the block size, or if !canAdd the end index)
         */
        public boolean hasBeenDrained() {
            final long maximalIndex = (this.canAdd()) ? (this.originalStartIndex + this.maxBlockSize) : this.endIndex;
            return this.currentStartIndex > maximalIndex;       //NB: watch out for an off by one here
        }

        /** Gets the index of the first record in this block */
        public long getStartIndex() { return this.currentStartIndex; }

        /** Gets the index of the last record in this block */
        public long getEndIndex() { return this.endIndex; }

        /** Add a record to this block */
        public void add(final SamRecordWithOrdinal samRecordWithOrdinal) {
            if (this.recordsQueue.canAdd()) {
                if (this.recordsQueue.isEmpty()) {
                    this.currentStartIndex = samRecordWithOrdinal.getRecordOrdinal();
                    this.endIndex = samRecordWithOrdinal.getRecordOrdinal() - 1;
                }
                this.recordsQueue.add(samRecordWithOrdinal.getRecord());
                this.endIndex++;
            } else {
                throw new IllegalStateException("Cannot add to DiskBackedQueue whose canAdd() method returns false");
            }
        }

        private int ensureIndexFitsInAnInt(final long value) {
            if (value < Integer.MIN_VALUE || Integer.MAX_VALUE < value) throw new SAMException("Error: index out of range: " + value);
            return (int)value;
        }

        /**
         * Mark the current samRecordWithOrdinal as having been examined with a given result state.
         *
         * @param samRecordWithOrdinal The samRecordWithOrdinal to be marked
         * @param resultState Boolean flag indicating the result of the examination of this record.
         *
         * This assumes that this record index does not fall out of range.
         */
        public void setResultState(final SamRecordWithOrdinal samRecordWithOrdinal, final boolean resultState) {
            // find the correct byte array index and update both metadata byte arrays
            this.wasExaminedIndexes.set(ensureIndexFitsInAnInt(samRecordWithOrdinal.getRecordOrdinal() - this.originalStartIndex), true);
            this.resultStateIndexes.set(ensureIndexFitsInAnInt(samRecordWithOrdinal.getRecordOrdinal() - this.originalStartIndex), resultState);
        }

        public boolean isEmpty() {
            return (this.recordsQueue.isEmpty());
        }

        public boolean canEmit() {
            // TODO: what if isEmpty() == true?
            return this.wasExaminedIndexes.get(ensureIndexFitsInAnInt(this.currentStartIndex - this.originalStartIndex));
        }

        public SamRecordWithOrdinal next() throws IllegalStateException {
            if (this.canEmit()) {
                try {
                    // create a wrapped record for the head of the queue, and set the underlying record's examined information appropriately
                    final SamRecordWithOrdinal samRecordWithOrdinal = clazz.newInstance();
                    samRecordWithOrdinal.setRecord(this.recordsQueue.poll());
                    samRecordWithOrdinal.setRecordOrdinal(this.currentStartIndex);
                    samRecordWithOrdinal.setResultState(this.resultStateIndexes.get(ensureIndexFitsInAnInt(this.currentStartIndex - this.originalStartIndex)));
                    this.currentStartIndex++;
                    return samRecordWithOrdinal;
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new IllegalStateException("Cannot call next() on a buffer block where canEmit() is false!");
            }
        }

        /**
         * Remove, but do not return, the next samRecordWithOrdinal in the iterator
         */
        public void remove() { this.next(); }

        /**
         * Return the total number of elements in the block, both in memory and on disk
         */
        public long size() { return this.endIndex - this.currentStartIndex + 1; }

        /**
         * Close disk IO resources associated with the underlying records queue.
         * This must be called when a block is no longer needed in order to prevent memory leaks.
         */
        public void clear() { this.recordsQueue.clear(); }
    }
}
