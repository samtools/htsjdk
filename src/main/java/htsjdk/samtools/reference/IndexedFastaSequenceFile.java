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

package htsjdk.samtools.reference;

import htsjdk.samtools.SAMException;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.IOUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A fasta file driven by an index for fast, concurrent lookups.  Supports two interfaces:
 * the ReferenceSequenceFile for old-style, stateful lookups and a direct getter.
 */
public class IndexedFastaSequenceFile extends AbstractIndexedFastaSequenceFile {
    /**
     * The interface facilitating direct access to the fasta.
     */
    private final SeekableByteChannel channel;

    /**
     * Open the given indexed fasta sequence file.  Throw an exception if the file cannot be opened.
     * @param file The file to open.
     * @param index Pre-built FastaSequenceIndex, for the case in which one does not exist on disk.
     * @throws FileNotFoundException If the fasta or any of its supporting files cannot be found.
     */
    public IndexedFastaSequenceFile(final File file, final FastaSequenceIndex index) {
        this(file == null ? null : file.toPath(), index);
    }

    /**
     * Open the given indexed fasta sequence file.  Throw an exception if the file cannot be opened.
     * @param file The file to open.
     * @throws FileNotFoundException If the fasta or any of its supporting files cannot be found.
     */
    public IndexedFastaSequenceFile(final File file) throws FileNotFoundException {
        this(file, new FastaSequenceIndex((findRequiredFastaIndexFile(file == null ? null : file.toPath()))));
    }

    /**
     * Open the given indexed fasta sequence file.  Throw an exception if the file cannot be opened.
     * @param path The file to open.
     * @param index Pre-built FastaSequenceIndex, for the case in which one does not exist on disk.
     */
    public IndexedFastaSequenceFile(final Path path, final FastaSequenceIndex index) {
        super(path, index);
        try {
            // check if the it is a valid block-compressed file
            if (IOUtil.isBlockCompressed(path, true)) {
                throw new SAMException("Indexed block-compressed FASTA file cannot be handled: " + path);
            }
            this.channel = Files.newByteChannel(path);
        } catch (IOException e) {
            throw new SAMException("FASTA file should be readable but is not: " + path, e);
        }
    }

    /**
     * Open the given indexed fasta sequence file.  Throw an exception if the file cannot be opened.
     * @param path The file to open.
     * @throws FileNotFoundException If the fasta or any of its supporting files cannot be found.
     */
    public IndexedFastaSequenceFile(final Path path) throws FileNotFoundException {
        this(path, new FastaSequenceIndex((findRequiredFastaIndexFile(path))));
    }

    /**
     * @deprecated use {@link ReferenceSequenceFileFactory#canCreateIndexedFastaReader(Path)} instead.
     */
    @Deprecated
    public static boolean canCreateIndexedFastaReader(final File fastaFile) {
        return canCreateIndexedFastaReader(fastaFile.toPath());
    }

    /**
     * @deprecated use {@link ReferenceSequenceFileFactory#canCreateIndexedFastaReader(Path)} instead.
     */
    @Deprecated
    public static boolean canCreateIndexedFastaReader(final Path fastaFile) {
        try {
            if (IOUtil.isBlockCompressed(fastaFile, true)) {
                return false;
            }
            return (Files.exists(fastaFile) &&
                    findFastaIndex(fastaFile) != null);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Reads a sequence of bytes from this channel into the given buffer,
     * starting at the given file position.
     * @param buffer the buffer into which bytes are to be transferred
     * @param position the position to start reading at
     * @return the number of bytes read
     * @throws IOException if an I/O error occurs while reading
     */
    @Override
    protected int readFromPosition(final ByteBuffer buffer, long position) throws IOException {
        if (channel instanceof FileChannel) { // special case to take advantage of native code path
            return ((FileChannel) channel).read(buffer,position);
        } else {
            long oldPos = channel.position();
            try {
                channel.position(position);
                return channel.read(buffer);
            } finally {
                channel.position(oldPos);
            }
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
