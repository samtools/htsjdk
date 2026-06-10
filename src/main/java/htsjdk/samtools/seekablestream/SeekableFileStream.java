/*
 * Copyright (c) 2007-2009 by The Broad Institute, Inc. and the Massachusetts Institute of Technology.
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
package htsjdk.samtools.seekablestream;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 *
 * @author jrobinso
 */
public class SeekableFileStream extends SeekableStream {

    /**
     * Collection of all open instances.  SeekableFileStream objects are usually open and kept open for the
     * duration of a session.  This collection supports a method to close them all.
     */
    static Collection<SeekableFileStream> allInstances =
            Collections.synchronizedCollection(new HashSet<SeekableFileStream>());

    private final Path path;
    RandomAccessFile fis;

    /**
     * Create a {@code SeekableFileStream} over a local file represented by a {@link Path}.
     *
     * @param path the path to the local file to open; must be backed by the default (local) filesystem
     *             because the underlying {@link RandomAccessFile} requires a {@link File}
     * @throws FileNotFoundException if the file does not exist or cannot be opened for reading
     */
    public SeekableFileStream(final Path path) throws FileNotFoundException {
        this.path = path;
        fis = new RandomAccessFile(path.toFile(), "r");
        allInstances.add(this);
    }

    /**
     * Create a {@code SeekableFileStream} over a local {@link File}.
     *
     * @deprecated use {@link #SeekableFileStream(Path)} instead.
     */
    @Deprecated
    public SeekableFileStream(final File file) throws FileNotFoundException {
        this(file.toPath());
    }

    @Override
    public long length() {
        return path.toFile().length();
    }

    @Override
    public boolean eof() throws IOException {
        return fis.length() == fis.getFilePointer();
    }

    @Override
    public void seek(final long position) throws IOException {
        fis.seek(position);
    }

    @Override
    public long position() throws IOException {
        return fis.getChannel().position();
    }

    @Override
    public long skip(long n) throws IOException {
        long initPos = position();
        fis.getChannel().position(initPos + n);
        return position() - initPos;
    }

    @Override
    public int read(final byte[] buffer, final int offset, final int length) throws IOException {
        if (length < 0) {
            throw new IndexOutOfBoundsException();
        }
        int n = 0;
        while (n < length) {
            final int count = fis.read(buffer, offset + n, length - n);
            if (count < 0) {
                if (n > 0) {
                    return n;
                } else {
                    return count;
                }
            }
            n += count;
        }
        return n;
    }

    @Override
    public int read() throws IOException {
        return fis.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return fis.read(b);
    }

    @Override
    public String getSource() {
        return path.toAbsolutePath().toString();
    }

    @Override
    public void close() throws IOException {
        allInstances.remove(this);
        fis.close();
    }

    public static synchronized void closeAllInstances() {
        Collection<SeekableFileStream> clonedInstances = new HashSet<SeekableFileStream>();
        clonedInstances.addAll(allInstances);
        for (SeekableFileStream sfs : clonedInstances) {
            try {
                sfs.close();
            } catch (IOException e) {
                // TODO
                // log.error("Error closing SeekableFileStream", e);
            }
        }
        allInstances.clear();
    }
}
