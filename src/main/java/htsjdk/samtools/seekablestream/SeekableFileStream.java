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

    File file;
    RandomAccessFile fis;

    /**
     * File length cached at construction time. Callers that read SAM/BAM/CRAM open the file once
     * and read it sequentially without other writers, so caching avoids repeated stat syscalls
     * inside the hot read loop (notably {@link #available()}, which is called once per
     * {@link java.io.InputStreamReader} fill via {@code StreamDecoder.inReady}).
     */
    private final long length;

    /**
     * Position tracked locally so that {@link #available()} does not need a per-call
     * {@code lseek(SEEK_CUR)} syscall via {@link RandomAccessFile#getFilePointer()}. All mutation
     * paths in this class keep this in sync with the underlying {@link RandomAccessFile}.
     */
    private long pos;

    public SeekableFileStream(final File file) throws FileNotFoundException {
        this.file = file;
        fis = new RandomAccessFile(file, "r");
        this.length = file.length();
        this.pos = 0;
        allInstances.add(this);
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public boolean eof() throws IOException {
        return pos >= length;
    }

    @Override
    public int available() throws IOException {
        final long remaining = length - pos;
        if (remaining <= 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, remaining);
    }

    @Override
    public void seek(final long position) throws IOException {
        fis.seek(position);
        pos = position;
    }

    @Override
    public long position() throws IOException {
        return pos;
    }

    @Override
    public long skip(long n) throws IOException {
        final long newPos = pos + n;
        fis.getChannel().position(newPos);
        final long actual = newPos - pos;
        pos = newPos;
        return actual;
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
                    pos += n;
                    return n;
                } else {
                    return count;
                }
            }
            n += count;
        }
        pos += n;
        return n;
    }

    @Override
    public int read() throws IOException {
        final int b = fis.read();
        if (b >= 0) {
            pos++;
        }
        return b;
    }

    @Override
    public int read(byte[] b) throws IOException {
        final int n = fis.read(b);
        if (n > 0) {
            pos += n;
        }
        return n;
    }

    @Override
    public String getSource() {
        return file.getAbsolutePath();
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
