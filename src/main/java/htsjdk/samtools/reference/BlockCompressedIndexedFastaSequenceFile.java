/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Daniel Gomez-Sanchez
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package htsjdk.samtools.reference;

import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.seekablestream.ReadableSeekableStreamByteChannel;
import htsjdk.samtools.seekablestream.SeekablePathStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.GZIIndex;
import htsjdk.samtools.util.IOUtil;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A block-compressed FASTA file driven by an index for fast lookups.
 *
 * <p>Supports two interfaces: the ReferenceSequenceFile for old-style, stateful lookups and a direct getter.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class BlockCompressedIndexedFastaSequenceFile extends AbstractIndexedFastaSequenceFile {

    private final BlockCompressedInputStream stream;
    private final GZIIndex gzindex;

    public BlockCompressedIndexedFastaSequenceFile(final Path path)
            throws FileNotFoundException {
        this(path,new FastaSequenceIndex((findRequiredFastaIndexFile(path))));
    }

    public BlockCompressedIndexedFastaSequenceFile(final Path path, final FastaSequenceIndex index) {
        this(path, index, loadFastaGziIndex(path));
    }

    public BlockCompressedIndexedFastaSequenceFile(final Path path, final FastaSequenceIndex index, final GZIIndex gziIndex) {
        super(path, index);
        if (gziIndex == null) {
            throw new IllegalArgumentException("null gzi index");
        }
        if (!canCreateBlockCompresedIndexedFastaSequence(path)) {
            throw new SAMException("Invalid block-compressed Fasta file");
        }
        try {
            stream = new BlockCompressedInputStream(new SeekablePathStream(path));
            gzindex = gziIndex;
        } catch (IOException e) {
            throw new SAMException("Fasta file should be readable but is not: " + path, e);
        }
    }

    public BlockCompressedIndexedFastaSequenceFile(final String source, final SeekableStream in, final FastaSequenceIndex index, final SAMSequenceDictionary dictionary, final GZIIndex gziIndex) {
        super(source, index, dictionary);
        if (gziIndex == null) {
            throw new IllegalArgumentException("null gzi index");
        }
        stream = new BlockCompressedInputStream(in);
        gzindex = gziIndex;
    }

    private static GZIIndex loadFastaGziIndex(final Path path) {
        try {
            return GZIIndex.loadIndex(GZIIndex.resolveIndexNameForBgzipFile(path));
        } catch (final IOException e) {
            throw new SAMException("Error loading GZI index for " + path, e);
        }
    }

    private static boolean canCreateBlockCompresedIndexedFastaSequence(final Path path) {
        try {
            // check if the it is a valid block-compressed file and if the .gzi index exits
            return IOUtil.isBlockCompressed(path, true) && Files.exists(GZIIndex.resolveIndexNameForBgzipFile(path));
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    protected int readFromPosition(final ByteBuffer buffer, final long position) throws IOException {
        // old position to get back
        final long oldPos = stream.getFilePointer();
        try {
            final long virtualOffset = gzindex.getVirtualOffsetForSeek(position);
            stream.seek(virtualOffset);
            final byte[] array = new byte[buffer.remaining()];
            final int read = stream.read(array);
            buffer.put(array);
            return read;
        } finally {
            stream.seek(oldPos);
        }
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
