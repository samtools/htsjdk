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

import htsjdk.io.IOPath;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.seekablestream.ReadableSeekableStreamByteChannel;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.IOUtil;
import java.io.FileNotFoundException;
import java.io.IOException;
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
     * @param path The file to open.
     * @param index Pre-built FastaSequenceIndex, for the case in which one does not exist on disk.
     */
    public IndexedFastaSequenceFile(final Path path, final FastaSequenceIndex index) {
        super(path, index);
        try {
            // check if it is a valid block-compressed file
            if (IOUtil.isBlockCompressed(path, true)) {
                throw new SAMException("Indexed block-compressed FASTA file cannot be handled: " + path);
            }
            this.channel = Files.newByteChannel(path);
            sanityCheckFastaAgainstIndex(path, index);
        } catch (IOException e) {
            throw new SAMException("FASTA file should be readable but is not: " + path, e);
        }
    }

    /**
     * Open the given indexed fasta sequence file.  Throw an exception if the file cannot be opened.
     *
     * @param path The file to open.
     * @param dictPath the dictionary path (may be null)
     * @param index Pre-built FastaSequenceIndex, for the case in which one does not exist on disk. may not be null.
     */
    public IndexedFastaSequenceFile(final IOPath path, final IOPath dictPath, final FastaSequenceIndex index) {
        super(path, dictPath, index);
        try {
            // reject block-compressed files (use BlockCompressedIndexedFastaSequenceFile)
            if (IOUtil.isBlockCompressed(path.toPath(), true)) {
                throw new SAMException("Indexed block-compressed FASTA file cannot be handled: " + path);
            }
            this.channel = Files.newByteChannel(path.toPath());
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
     * Initialise the given indexed fasta sequence file stream.
     * @param source The named source of the reference file (used in error messages).
     * @param in The input stream to read the fasta file from.
     * @param index The fasta index.
     * @param dictionary The sequence dictionary, or null if there isn't one.
     */
    public IndexedFastaSequenceFile(
            String source, final SeekableStream in, final FastaSequenceIndex index, SAMSequenceDictionary dictionary) {
        super(source, index, dictionary);
        this.channel = new ReadableSeekableStreamByteChannel(in);
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
            return (Files.exists(fastaFile) && findFastaIndex(fastaFile) != null);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Do some basic checking to make sure the fasta and its index are consistent with one another.
     * <p>
     * Verifies that the fasta file is at least as long as the last base position claimed by the index, and
     * that any bytes beyond that last base are only whitespace. A mismatch usually indicates a corrupt fasta
     * or a stale/incorrect index (which would otherwise silently return the wrong bases), and should be
     * resolved by reindexing the fasta.
     * <p>
     * This is a cheap, necessary-but-not-sufficient check: it reads only the file length and the trailing
     * bytes, so it catches truncation, wrong-file, and appended-content mismatches, but not an index whose
     * internal offsets are wrong yet happen to end at the right place. It applies only to plain (non
     * block-compressed) fasta files, where the on-disk length equals the uncompressed length; block-compressed
     * references (see {@link BlockCompressedIndexedFastaSequenceFile}) are not checked.
     *
     * @param fastaFile Path to the fasta file
     * @param fastaSequenceIndex the index to check against the fasta file
     * @throws IOException if an io-error occurs while reading fastaFile
     */
    private void sanityCheckFastaAgainstIndex(final Path fastaFile, final FastaSequenceIndex fastaSequenceIndex)
            throws IOException {

        final FastaSequenceIndexEntry lastSequence = fastaSequenceIndex.getLastIndexEntry();
        // 0-based byte offset of the last base of the last contig; that base occupies exactly this one byte.
        final long lastBaseOffset = lastSequence.getLocation() + lastSequence.getOffset(lastSequence.getSize());
        final long fastaLength = Files.size(fastaFile);

        // The file must actually contain the last base byte, i.e. be strictly longer than its offset.
        if (lastBaseOffset >= fastaLength) {
            throw new IllegalArgumentException(
                    ("The fasta file (%s) is shorter (%d bytes) than its index claims: the last base is expected at "
                                    + "byte %d. Please reindex the fasta.")
                            .formatted(fastaFile.toUri(), fastaLength, lastBaseOffset));
        }

        // Everything strictly after the last base (its line terminator plus any trailing blank lines) must be
        // whitespace; a non-whitespace byte there means the index does not account for all of the fasta's content.
        long position = lastBaseOffset + 1;
        final ByteBuffer buffer = ByteBuffer.allocate(100);
        while (position < fastaLength) {
            buffer.clear();
            final int bytesRead = readFromPosition(buffer, position);
            if (bytesRead <= 0) {
                break;
            }
            for (int i = 0; i < bytesRead; i++) {
                final byte b = buffer.get(i);
                if (!Character.isWhitespace((char) b)) {
                    throw new IllegalArgumentException(
                            ("The fasta file (%s) is longer than its index accounts for: found a non-whitespace "
                                            + "character (%c) at byte %d, past the last base at byte %d. Please reindex the fasta.")
                                    .formatted(fastaFile.toUri(), (char) b, position + i, lastBaseOffset));
                }
            }
            position += bytesRead;
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
            return ((FileChannel) channel).read(buffer, position);
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
