/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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

import com.sun.jndi.url.iiopname.iiopnameURLContextFactory;
import net.sf.samtools.Defaults;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;

/**
 * A FIFO queue. Writes elements to temporary files when the queue gets too big.
 * External references to elements in this queue are NOT guaranteed to be valid, due to the disk write/read
 * <p/>
 * NB: The queue becomes read-only after the first on-disk record is read. Max size is therefore non-deterministic.
 * This avoids issues arising from conflicts between the input and output streams.
 * This could perhaps be avoided by creating a version of BAMRecordCodec that operates on RandomAccessFiles or channels.
 * <p/>
 * Created by bradt on 4/28/14.
 */
public class DiskBackedQueue<E> implements Queue<E> {

    // TODO - this needs to be altered (and simplified significantly) to conform with the storage behavior we expect for MarkDuplicatesWithMateCigar BufferBlock

    /** TODO - support peeking. Add a "nextRecord" member. Return this if we are peeking, and update it if polling or removing.
     Move the record reading (current 'poll') to a private method  **/

    private final int maxRecordsInRam;
    private final Deque<E> ramRecords;
    private File diskRecords = null;
    private final TempStreamFactory tempStreamFactory = new TempStreamFactory();
    private OutputStream outputStream = null;
    private InputStream inputStream = null;
    private boolean canAdd = false;

    /** Record representing the head of the queue; returned by peek, poll **/
    private E headRecord;

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
     * @param tmpDir Where to write files of records that will not fit in RAM
     */
    private DiskBackedQueue(final SortingCollection.Codec<E> codec,
                            final int maxRecordsInRam, final List<File> tmpDir) {
        if (maxRecordsInRam <= 0) {
            throw new IllegalArgumentException("maxRecordsInRam must be > 0");
        }
        if (tmpDir == null || tmpDir.size() == 0) {
            throw new IllegalArgumentException("At least one temp directory must be provided.");
        }
        this.tmpDirs = tmpDir;
        this.codec = codec;
        this.maxRecordsInRam = maxRecordsInRam;
        this.ramRecords = new ArrayDeque<E>();
    }

    /**
     * Syntactic sugar around the ctor, to save some typing of type parameters
     *
     * @param codec For writing records to file and reading them back into RAM
     * @param maxRecordsInRAM how many records to accumulate in memory before spilling to disk
     * @param tmpDir Where to write files of records that will not fit in RAM
     */
    public static <T> DiskBackedQueue<T> newInstance(final SortingCollection.Codec<T> codec,
                                                     final int maxRecordsInRAM,
                                                     final List<File> tmpDir) {
        return new DiskBackedQueue<T>(codec, maxRecordsInRAM, tmpDir);

    }

    public boolean canAdd() {
        return canAdd;
    }

    /**
     * Add the record to the tail of the queue, spilling to disk if necessary
     * Must check that (canAdd() == true) before calling this method
     */
    public boolean add(final E record) throws IllegalStateException {
        if (canAdd) {
            if (diskRecords != null || ramRecords.size() == maxRecordsInRam)
                spillToDisk(record);
            else
                ramRecords.add(record);
            return true;
        } else {
            throw new IllegalStateException("cannot add to DiskBackedQueue whose canAdd() method returns false");
        }
    }

    @Override
    public boolean offer(final E e) {
        return this.canAdd && this.add(e);
    }

    @Override
    public E remove() {
        final E element = this.poll();
        if (element == null)
            throw new NoSuchElementException("Attempting to remove() from empty DiskBackedQueue");
        else
            return element;
    }

    @Override
    public E poll() {
        return (ramRecords.isEmpty()) ? ramRecords.peekFirst() : readFileRecord(this.diskRecords);
    }

    @Override
    public E element() {
        return null;
    }

    @Override
    public E peek() {
        // TODO - fix
        if (!ramRecords.isEmpty()) {
            return ramRecords.peekFirst();
        } else if (files.size() != 0) {
            final File currentFile = files.remove(0);  // get the file nearest the head of the queue
            CloserUtil.close(outputStreams.remove(0)); // close the file's associated output stream
            readFileRecord(currentFile);
            return ramRecords.peekFirst();
        }
        return null;
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        return false;
    }

    @Override
    public boolean addAll(final Collection<? extends E> c) {
        return false;
    }

    @Override
    public boolean remove(final Object o) {
        return false;
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        return false;
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        return false;
    }

    @Override
    public void clear() {

    }


    /**
     * Write the present record to the end of a file representing the tail of the queue.
     */
    private void spillToDisk(final E record) throws RuntimeIOException {
        try {
            if (this.diskRecords == null) {
                this.diskRecords = newTempFile();
                this.outputStream = tempStreamFactory.wrapTempOutputStream(new FileOutputStream(this.diskRecords), Defaults.BUFFER_SIZE);
            }
            try {
                this.codec.setOutputStream(this.outputStream);
                this.codec.encode(record);
                this.outputStream.flush();
            } catch (final RuntimeIOException ex) {
                throw new RuntimeIOException("Problem writing temporary file. " +
                        "Try setting TMP_DIR to a file system with lots of space.", ex);
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
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
     * Read back a record that had been spilled to disk.
     * Note- if we are reading disk records, we can no longer add additional elements to this DiskBackedQueue
     *
     * @param file the file to read from
     * @return The next element from the head of the file, or null if end-of-file is reached
     */
    private E readFileRecord (final File file) {
        if (this.canAdd)
            this.canAdd = false; // NB: should this just be an assignment regardless?
        try {
            if (this.inputStream == null) {
                inputStream = new FileInputStream(file);
                this.codec.setInputStream(tempStreamFactory.wrapTempInputStream(inputStream, Defaults.BUFFER_SIZE));
            }
            return this.codec.decode(); // NB: returns null if end-of-file is reached.
        } catch (final IOException e) {
            throw new RuntimeIOException("DiskBackedQueue encountered an error reading from a file", e);
        }
    }

    /**
     * Return the total number of elements in the queue, both in memory and on disk
     */
    public int size() {
        return (files.size() * maxRecordsInRam) + ramRecords.size();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean contains(final Object o) {
        return false;
    }

    @Override
    public Iterator<E> iterator() {
        return null;
    }

    @Override
    public Object[] toArray() {
        return new Object[0];
    }

    @Override
    public <T1> T1[] toArray(final T1[] a) {
        return null;
    }
}
