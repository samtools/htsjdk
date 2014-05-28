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

import net.sf.samtools.Defaults;
import net.sf.samtools.SAMException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * An iterator intended for use as a queue. Writes elements to temporary files when the queue gets too big
 * External references to elements in this queue are NOT guaranteed to be valid, due to the disk write/read
 *
 * Created by bradt on 4/28/14.
 */
public class DiskBackedQueue<T> implements Iterator<T> {

    // TODO - this needs to be altered (and simplified significantly) to conform with the storage behavior we expect for MarkDuplicatesWithMateCigar BufferBlock

    private final int maxRecordsInRam;
    private final Deque<T> ramRecords;

    /** Directories where files of records go. */
    private final List<File> tmpDirs;

    /**
     * Used to write records to file, and used as a prototype to create codecs for reading.
     */
    private final SortingCollection.Codec<T> codec;

    /**
     *
     * List of files in tmpDir containing sorted records, their associated output streams, and a map counting their contents
     */
    private final List<File> files = new ArrayList<File>();
    private final List<OutputStream> outputStreams = new ArrayList<OutputStream>();
    private final Map<File, Integer> elementsInFile = new HashMap<File, Integer>();
    private final TempStreamFactory tempStreamFactory = new TempStreamFactory();

    /**
     * Prepare to accumulate records
     * @param codec For writing records to file and reading them back into RAM
     * @param maxRecordsInRam how many records to accumulate before spilling to disk
     * @param tmpDir Where to write files of records that will not fit in RAM
     */
    private DiskBackedQueue(final SortingCollection.Codec<T> codec,
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
        this.ramRecords = new ArrayDeque<T>();
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

    /**
     * Add the record to the queue, spilling to disk if necessary
     */
    public void add(final T record) {
        if (files.size() != 0 || ramRecords.size() == maxRecordsInRam) {
            spillToDisk(record);
        }
        else {
            ramRecords.add(record);
        }
    }

    @Override
    public boolean hasNext() {
        return !(ramRecords.isEmpty() && files.size() == 0);
    }

    /**
     * Return the first element at the head of the queue, but do not remove it
     */
    public T peek() {
        if (!ramRecords.isEmpty()) {
            return ramRecords.peekFirst();
        }
        else if (files.size() != 0) {
            final File currentFile = files.remove(0);  // get the file nearest the head of the queue
            CloserUtil.close(outputStreams.remove(0)); // close the file's associated output stream
            readFileIntoRam(currentFile);
            return ramRecords.peekFirst();
        }
        return null;
    }

    @Override
    public T next() {
        if (!ramRecords.isEmpty()) {
            return ramRecords.pollFirst();
        }
        else if (files.size() != 0) {
            final File currentFile = files.remove(0);  // get the file nearest the head of the queue
            CloserUtil.close(outputStreams.remove(0)); // close the file's associated output stream
            readFileIntoRam(currentFile);
            return ramRecords.pollFirst();
        }
        return null;
    }

    /**
     * Write the present record to the end of a file representing the tail of the queue.
     * The tail of the queue should always be the last file in the files list
     */
    private void spillToDisk(final T record) {
        try {
            OutputStream os;
            File f;
            if (this.files.size() == 0 || elementsInFile.get(this.files.get(this.files.size() - 1)) == maxRecordsInRam ) {
                f = newTempFile();
                os = tempStreamFactory.wrapTempOutputStream(new FileOutputStream(f), Defaults.BUFFER_SIZE);
                this.files.add(f);
                this.outputStreams.add(os);
            }
            else {
                f = this.files.get(this.files.size() - 1);
                os = this.outputStreams.get(this.outputStreams.size() - 1);
            }
            try {
                this.codec.setOutputStream(os);
                this.codec.encode(record);
                os.flush();
            }
            catch (final RuntimeIOException ex) {
                throw new RuntimeIOException("Problem writing temporary file. " +
                                             "Try setting TMP_DIR to a file system with lots of space.", ex);
            }
            Integer elements = this.elementsInFile.get(f);
            if (elements == null)
                elements = 0;
            this.elementsInFile.put(f, ++elements);
        }
        catch (final IOException e) {
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
     * Read one of our disk-written files back into RAM
     */
    private void readFileIntoRam(final File file) {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            this.codec.setInputStream(tempStreamFactory.wrapTempInputStream(inputStream, Defaults.BUFFER_SIZE));
            T record;
            while ((record = this.codec.decode()) != null) {
                ramRecords.addLast(record);
            }
            IOUtil.deleteFiles(file);
        }
        catch (final IOException ex) {
            throw new RuntimeIOException("DiskBackedQueue tried to read from a file that did not exist");
        }
        finally {
            CloserUtil.close(inputStream);
        }
    }

    /**
     * Pop the first element from the head of the queue, but do not return it
     */
    @Override
    public void remove() {
        if (this.next() == null) {
            throw new SAMException("trying to remove from empty DiskBackedQueue");
        }
    }

    /**
     * Return the total number of elements in the queue, both in memory and on disk
     */
    public int size() {
        return (files.size() * maxRecordsInRam) + ramRecords.size();
    }
}
