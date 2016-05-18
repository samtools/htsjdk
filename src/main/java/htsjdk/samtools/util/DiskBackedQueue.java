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

import htsjdk.samtools.Defaults;
import htsjdk.samtools.SAMException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;

/**
 * A single-ended FIFO queue. Writes elements to temporary files when the queue gets too big.
 * External references to elements in this queue are NOT guaranteed to be valid, due to the disk write/read
 * <p/>
 * NB: The queue becomes read-only after the first time that an on-disk record is "next up" to be read (i.e. has been
 * loaded into headRecord). Max size is therefore non-deterministic.
 * This avoids issues arising from conflicts between the input and output streams.
 * This could perhaps be avoided by creating a version of BAMRecordCodec that operates on RandomAccessFiles or channels.
 * <p/>
 *
 *
 * Created by bradt on 4/28/14.
 */
public class DiskBackedQueue<E> implements Queue<E> {
    private final int maxRecordsInRamQueue;
    private final Queue<E> ramRecords;
    private File diskRecords = null;
    private final TempStreamFactory tempStreamFactory = new TempStreamFactory();
    private OutputStream outputStream = null;
    private InputStream inputStream = null;
    private boolean canAdd = true;
    private int numRecordsOnDisk = 0;

    /** Record representing the head of the queue; returned by peek, poll **/
    private E headRecord = null;

    /** Directories where file of records go. **/
    private final List<File> tmpDirs;

    /**
     * Used to write records to file, and used as a prototype to create codecs for reading.
     */
    private final SortingCollection.Codec<E> codec;


    /**
     * Prepare to accumulate records
     *
     * @param codec For writing records to file and reading them back into RAM
     * @param maxRecordsInRam how many records to accumulate before spilling to disk
     * @param tmpDirs Where to write files of records that will not fit in RAM
     */
    private DiskBackedQueue(final SortingCollection.Codec<E> codec,
                            final int maxRecordsInRam, final List<File> tmpDirs) {
        if (maxRecordsInRam < 0) {
            throw new IllegalArgumentException("maxRecordsInRamQueue must be >= 0");
        }
        if (tmpDirs == null || tmpDirs.isEmpty()) {
            throw new IllegalArgumentException("At least one temp directory must be provided.");
        }
        for (final File tmpDir : tmpDirs) IOUtil.assertDirectoryIsWritable(tmpDir);
        this.tmpDirs = tmpDirs;
        this.codec = codec;
        this.maxRecordsInRamQueue = (maxRecordsInRam == 0) ? 0 : maxRecordsInRam - 1; // the first of our ram records is stored as headRecord
        this.ramRecords = new ArrayDeque<E>(this.maxRecordsInRamQueue);
    }

    /**
     * Syntactic sugar around the ctor, to save some typing of type parameters
     *
     * @param codec For writing records to file and reading them back into RAM
     * @param maxRecordsInRam how many records to accumulate in memory before spilling to disk
     * @param tmpDir Where to write files of records that will not fit in RAM
     */
    public static <T> DiskBackedQueue<T> newInstance(final SortingCollection.Codec<T> codec,
                                                     final int maxRecordsInRam,
                                                     final List<File> tmpDir) {
        return new DiskBackedQueue<T>(codec, maxRecordsInRam, tmpDir);
    }

    public boolean canAdd() {
        return this.canAdd;
    }

    public int getNumRecordsOnDisk() {
        return this.numRecordsOnDisk;
    }

    public boolean headRecordIsFromDisk() {
        return (!this.canAdd);
    }

    /**
     * Add the record to the tail of the queue, spilling to disk if necessary
     * Must check that (canAdd() == true) before calling this method
     *
     * @param record The record to be added to the queue
     * @return true (if add successful)
     * @throws IllegalStateException if the queue cannot be added to
     */
    public boolean add(final E record) throws IllegalStateException {
        if (!canAdd) throw new IllegalStateException("Cannot add to DiskBackedQueue whose canAdd() method returns false");

        // NB: we add all the records before removing them, so we can never have spilled to disk unless all the space for ram records
        // have been exhausted.
        if (this.headRecord == null) { // this is the first record in the queue
            if (0 < this.numRecordsOnDisk) throw new SAMException("Head record was null but we have records on disk. Bug!");
            this.headRecord = record;
        }
        else if (this.ramRecords.size() == this.maxRecordsInRamQueue) {
            spillToDisk(record);
        }
        else {
            if (0 < this.numRecordsOnDisk) throw new SAMException("Trying to add records to RAM but there were records on disk. Bug!");
            this.ramRecords.add(record);
        }
        return true;
    }

    @Override
    public boolean offer(final E e) {
        return this.canAdd && this.add(e);
    }

    @Override
    public E remove() {
        final E element = this.poll();
        if (element == null) {
            throw new NoSuchElementException("Attempting to remove() from empty DiskBackedQueue");
        }
        else {
            return element;
        }
    }

    @Override
    public E poll() {
        final E outRecord = this.headRecord;
        if (outRecord != null) {
            updateQueueHead();
        }
        return outRecord;
    }

    @Override
    public E element() {
        if (this.headRecord != null) {
            return this.headRecord;
        }
        else {
            throw new NoSuchElementException("Attempting to element() from empty DiskBackedQueue");
        }
    }

    @Override
    public E peek() {
        return this.headRecord;
    }

    /**
     * Return the total number of elements in the queue, both in memory and on disk
     */
    public int size() {
        return (this.headRecord == null) ? 0 : (1 + this.ramRecords.size() + this.numRecordsOnDisk);
    }

    @Override
    public boolean isEmpty() {
        return (this.headRecord == null);
    }

    /**
     * Add all elements from collection c to this DiskBackedQueue
     * Must check that (canAdd() == true) before calling this method
     *
     * @param c the collection of elements to add
     * @return true if this collection changed as a result of the call
     * @throws IllegalStateException if the queue cannot be added to
     */
    @Override
    public boolean addAll(final Collection<? extends E> c) {
        try {
            for (final E element : c) {
                this.add(element);
            }
            return true;
        } catch (final IllegalStateException e) {
            throw new IllegalStateException("Cannot add to DiskBackedQueue whose canAdd() method returns false", e);
        }
    }

    @Override
    public void clear() {
        this.headRecord = null;
        this.ramRecords.clear();
        this.closeIOResources();
        this.outputStream = null;
        this.inputStream = null;
        this.diskRecords = null;
        this.canAdd = true;
    }

    /**
     * Clean up disk resources in case clear() has not been explicitly called (as would be preferable)
     * Closes the input and output streams associated with this DiskBackedQueue and deletes the temporary file
     *
     * @throws Throwable
     */
    protected void finalize() throws Throwable {
        this.closeIOResources();
        super.finalize(); // NB: intellij wanted me to do this. Need I?  I'm not extending anything
    }

    /**
     * Write the present record to the end of a file representing the tail of the queue.
     * @throws RuntimeIOException
     */
    private void spillToDisk(final E record) throws RuntimeIOException {
        try {
            if (this.diskRecords == null) {
                this.diskRecords = newTempFile();
                this.outputStream = tempStreamFactory.wrapTempOutputStream(new FileOutputStream(this.diskRecords), Defaults.BUFFER_SIZE);
                this.codec.setOutputStream(this.outputStream);
            }
            this.codec.encode(record);
            this.outputStream.flush();
            this.numRecordsOnDisk++;
        } catch (final IOException e) {
            throw new RuntimeIOException("Problem writing temporary file. Try setting TMP_DIR to a file system with lots of space.", e);
        }
    }

    /**
     * Creates a new tmp file on one of the available temp filesystems, registers it for deletion
     * on JVM exit and then returns it.
     */
    private File newTempFile() throws IOException {
        return IOUtil.newTempFile("diskbackedqueue.", ".tmp", this.tmpDirs.toArray(new File[tmpDirs.size()]), IOUtil.FIVE_GBS);
    }

    /**
     * Update the head of the queue with the next record in memory or on disk.
     * Sets headRecord to null if the queue is now empty
     */
    private void updateQueueHead() {
        if (!this.ramRecords.isEmpty()) {
            this.headRecord = this.ramRecords.poll();
            if (0 < numRecordsOnDisk) this.canAdd = false;
        }
        else if (this.diskRecords != null) {
            this.headRecord = this.readFileRecord(this.diskRecords);
            this.canAdd = false;
        }
        else {
            this.canAdd = true;
            this.headRecord = null;
        }
    }

    /**
     * Read back a record that had been spilled to disk. Return null if there are no disk records
     * Note- if we are reading disk records, we can no longer add additional elements to this DiskBackedQueue
     *
     * @param file the file to read from
     * @return The next element from the head of the file, or null if end-of-file is reached
     * @throws RuntimeIOException
     */
    private E readFileRecord (final File file) {
        if (this.canAdd) this.canAdd = false; // NB: should this just be an assignment regardless?

        // we never wrote a record to disk
        if (file == null) {
            throw new IllegalStateException("The file to read from was null");
        }
        try {
            if (this.inputStream == null) {
                inputStream = new FileInputStream(file);
                this.codec.setInputStream(tempStreamFactory.wrapTempInputStream(inputStream, Defaults.BUFFER_SIZE));
            }
            final E record = this.codec.decode(); // NB: returns null if end-of-file is reached.
            if (record != null) {
                numRecordsOnDisk--;
            }
            return record;
        } catch (final IOException e) {
            throw new RuntimeIOException("DiskBackedQueue encountered an error reading from a file", e);
        }
    }

    private void closeIOResources() {
        CloserUtil.close(this.outputStream);
        CloserUtil.close(this.inputStream);
        if (this.diskRecords != null) IOUtil.deleteFiles(this.diskRecords);
    }

    /**
     * Not supported. Cannot access particular elements, as some elements may have been written to disk
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public boolean remove(final Object o) {
        throw new UnsupportedOperationException("DiskBackedQueue does not support remove(Object o)");
    }

    /**
     * Not supported. Cannot access particular elements, as some elements may have been written to disk
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public boolean removeAll(final Collection<?> c) {
        throw new UnsupportedOperationException("DiskBackedQueue does not support removeAll(Collection<?> c)");
    }

    /**
     * Not supported. Cannot access particular elements, as some elements may have been written to disk
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public boolean retainAll(final Collection<?> c) {
        throw new UnsupportedOperationException("DiskBackedQueue does not support retainAll(Collection<?> c)");
    }

    /**
     * Not supported. It is not possible to check for the presence of a particular element,
     * as some elements may have been written to disk
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public boolean contains(final Object o) {
        throw new UnsupportedOperationException("DiskBackedQueue does not support contains(Object o)");
    }

    /**
     * Not supported. It is not possible to check for the presence of a particular element,
     * as some elements may have been written to disk
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public boolean containsAll(final Collection<?> c) {
        throw new UnsupportedOperationException("DiskBackedQueue does not support containsAll(Collection<?> c)");
    }

    /**
     * Not supported at this time
     * @throws UnsupportedOperationException
     */
    @Override
    public Iterator<E> iterator() {
        throw new UnsupportedOperationException("DiskBackedQueue does not support iterator()");
    }

    /**
     * Not supported at this time
     * @throws UnsupportedOperationException
     */
    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException("DiskBackedQueue does not support toArray()");
    }

    /**
     * Not supported at this time
     * @throws UnsupportedOperationException
     */
    @Override
    public <T1> T1[] toArray(final T1[] a) {
        throw new UnsupportedOperationException("DiskBackedQueue does not support toArray(T1[] a)");
    }
}
