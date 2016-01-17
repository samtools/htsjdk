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

import htsjdk.samtools.util.IOUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
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
	
    private Path path;
    private SeekableByteChannel seekableByteChannel;
    private long fileLength;
	
    public SeekableFileStream(final File file) throws FileNotFoundException {
        this.path = IOUtil.getPath(file);
        try {
	        this.seekableByteChannel= Files.newByteChannel(path);
        } catch (IOException e) {
	        throw new FileNotFoundException("cannot open file " + path);
        }
        try {
	        this.fileLength = seekableByteChannel.size();
        } catch (IOException e) {
        	throw new FileNotFoundException("cannot get file sie " + path);
        }
    }

    public long length() {
        return fileLength;
    }

    public boolean eof() throws IOException {
        return fileLength == seekableByteChannel.position();
    }

    public void seek(final long position) throws IOException {
    	seekableByteChannel.position(position);
    }

    public long position() throws IOException {
        return seekableByteChannel.position();
    }

    @Override
    public long skip(long n) throws IOException {
        long initPos = position();
        seekableByteChannel.position(initPos + n);
        return position() - initPos;
    }
    
    public int read(final byte[] buffer, final int offset, final int length) throws IOException {
        if (length < 0) {
            throw new IndexOutOfBoundsException();
        }
		ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, offset, length);
        int n = 0;
        while (n < length) {
            final int count = seekableByteChannel.read(byteBuffer);
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

    public int read() throws IOException {
    	if (seekableByteChannel.position() >= fileLength) {
			return -1;
		}
		ByteBuffer buffer = ByteBuffer.allocate(1);
		seekableByteChannel.read(buffer);
		return buffer.array()[0];
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public String getSource() {
        return path.toAbsolutePath().toString();
    }
    public void close() throws IOException {
    	allInstances.remove(this);
    	seekableByteChannel.close();

    }

    public static synchronized void closeAllInstances() {
        Collection<SeekableFileStream> clonedInstances = new HashSet<SeekableFileStream>();
        clonedInstances.addAll(allInstances);
        for (SeekableFileStream sfs : clonedInstances) {
            try {
                sfs.close();
            } catch (IOException e) {
                //TODO
                //log.error("Error closing SeekableFileStream", e);
            }
        }
        allInstances.clear();
    }
}
