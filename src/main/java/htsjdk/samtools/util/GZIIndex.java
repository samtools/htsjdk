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

package htsjdk.samtools.util;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Represents a .gzi index of a block-compressed file.
 *
 * <p>The .gzi index is a mapping between the offset of each block in the gzipped file and the
 * uncompressed offset that that block starts with. This mapping is represented by {@link IndexEntry}.
 *
 * <p>An example of usage for this index for random access a bgzip file using an index generated
 * from raw data. For example, for indexing a compressed FASTA file the .gzi index can be used in
 * conjunction with the {@link htsjdk.samtools.reference.FastaSequenceIndex} to seek concrete
 * sequences.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 * @see <a href="http://github.com/samtools/htslib/issues/473">https://github.com/samtools/htslib/issues/473</a>
 */
public final class GZIIndex {

    /** Default extension for the files storing a {@link GZIIndex}. */
    /**
     * @deprecated since June 2019 Use {@link FileExtensions#GZI} instead.
     */
    @Deprecated
    public static final String DEFAULT_EXTENSION = FileExtensions.GZI;

    /**
     * Index entry mapping the block-offset (compressed offset) to the uncompressed offset where the
     * block starts.
     *
     * @see <a href="http://github.com/samtools/htslib/issues/473">https://github.com/samtools/htslib/issues/473</a>
     */
    public static final class IndexEntry {
        private final long compressedOffset;
        private final long uncompressedOffset;

        private IndexEntry(final long compressedOffset, final long uncompressedOffset) {
            if (compressedOffset < 0) {
                throw new IllegalArgumentException("negative compressed offset: " + compressedOffset);
            }
            if (uncompressedOffset < 0) {
                throw new IllegalArgumentException("negative uncompressed offset: " + uncompressedOffset);
            }
            this.compressedOffset = compressedOffset;
            this.uncompressedOffset = uncompressedOffset;
        }

        /** Returns the compressed offset (block-offset). */
        public long getCompressedOffset() {
            return compressedOffset;
        }

        /** Returns the uncompressed offset. */
        public long getUncompressedOffset() {
            return uncompressedOffset;
        }

        @Override
        public String toString() {
            return String.format("IndexEntry={compressed=%d(0x%x),uncompressed=%d(0x%x)",
                    compressedOffset, compressedOffset,
                    uncompressedOffset, uncompressedOffset);
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == null || !(obj instanceof IndexEntry)) {
                return false;
            }
            final IndexEntry other = (IndexEntry) obj;
            return compressedOffset == other.compressedOffset
                    && uncompressedOffset == other.uncompressedOffset;
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(compressedOffset) + Long.hashCode(uncompressedOffset);
        }
    }

    private final List<IndexEntry> entries;

    // private constructors
    private GZIIndex(final List<IndexEntry> entries) {
        this.entries = Collections.unmodifiableList(entries);
    }

    /**
     * Gets the number of blocks on the file.
     *
     * @return the number of blocks.
     */
    public int getNumberOfBlocks() {
        // +1 because the first block is not included in the entry list
        return entries.size() + 1;
    }

    /**
     * Gets an unmodifiable list with the index entries.
     *
     * <p>Note: because the first block corresponds to a dummy index entry (0, 0), the returned list
     * does not include it. Thus, the size of the list is {@code getNumberOfBlocks() - 1}.
     *
     * @return index entries.
     */
    public List<IndexEntry> getIndexEntries() {
        return entries;
    }

    /**
     * Gets the virtual offset for seek with {@link BlockCompressedInputStream#seek(long)}.
     *
     * <p>{@link BlockCompressedInputStream#seek(long)} parameter is not a byte-offset, but a
     * special virtual file pointer that specifies the block-offset within the file
     * ({@link BlockCompressedFilePointerUtil#getBlockAddress(long)}), and the offset within the
     * block ({@link BlockCompressedFilePointerUtil#getBlockOffset(long)}).
     *
     * <p>This methods converts the provided byte-offset on the file to the special file pointer
     * used to seek a block-compressed file, using this index to find the block where the
     * byte-offset is located.
     *
     * @param uncompressedOffset the file-offset.
     *
     * @return virtual offset for {@link BlockCompressedInputStream}.
     * @see BlockCompressedFilePointerUtil
     */
    public long getVirtualOffsetForSeek(final long uncompressedOffset) {
        try {
            if (uncompressedOffset == 0) {
                return BlockCompressedFilePointerUtil.makeFilePointer(0, 0);
            }

            // binary search in the entries for the uncompressed offset
            final int pos = Collections.binarySearch(entries,
                    // this is a fake index for getting the uncompressed offsets
                    new IndexEntry(0, uncompressedOffset),
                    Comparator.comparingLong(IndexEntry::getUncompressedOffset));

            // if it is found, it is at the beginning of the block
            if (pos >= 0) {
                return BlockCompressedFilePointerUtil.makeFilePointer(
                        entries.get(pos).getCompressedOffset(),
                        // offset in the block is always 0 (beginning of the block)
                        0);
            }

            // if pos < 0, then the offset is in the previous block to the insertion point
            // the insertion_point == -pos - 1
            // previous block = inserion_point - 1
            final int entryPos = -pos - 2;

            // if the insertion point is -1, it means that it is in the first block
            // so the virtual offset is just the uncompressed offset on the first block
            if (entryPos == -1) {
                return BlockCompressedFilePointerUtil.makeFilePointer(0, Math.toIntExact(uncompressedOffset));
            }

            final IndexEntry indexEntry = entries.get(entryPos);

            // now we should convert the uncompressed offste to an offset within the block
            final int blockOffset = Math.toIntExact(uncompressedOffset - indexEntry.getUncompressedOffset());

            // we use the file pointer utils to convert to the virtual-offset representation
            return BlockCompressedFilePointerUtil.makeFilePointer(indexEntry.getCompressedOffset(), blockOffset);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Cannot handle offsets within blocks larger than " +  Integer.MAX_VALUE, e);
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof GZIIndex)) {
            return false;
        }
        return this.entries.equals(((GZIIndex) obj).entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return "GZIIndex:" + StringUtil.join(", ", entries);
    }

    /**
     * Writes this index into the requested path.
     *
     * @param output the output path.
     *
     * @throws IOException if an I/O error occurs.
     */
    public void writeIndex(final Path output) throws IOException {
        writeIndex(Files.newOutputStream(output));
    }

    /**
     * Writes this index into the requested path.
     *
     * NOTE: This method will close out the provided output stream when it finishes writing the index
     *
     * @param output the output file.
     *
     * @throws IOException if an I/O error occurs.
     */
    public void writeIndex(final OutputStream output) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("null output path");
        }
        BinaryCodec codec = new BinaryCodec(output);

        // the first entry is never written - 0, 0
        final int numberOfBlocksToWrite = entries.size();

        // put the number of entries
        codec.writeLong(Integer.toUnsignedLong(numberOfBlocksToWrite));

        // except the first, iterate over all the entries
        for (final IndexEntry entry : entries) {
            // implementation of entry ensures that the offsets are no negative
            codec.writeLong(entry.getCompressedOffset());
            codec.writeLong(entry.getUncompressedOffset());
        }

        // Close the codec to ensure the output is written
        codec.close();
    }

    /**
     * Loads the index from the provided file.
     *
     * @param indexPath the path for the index to load.
     *
     * @return loaded index.
     *
     * @throws IOException if an I/O error occurs.
     */
    public static final GZIIndex loadIndex(final Path indexPath) throws IOException {
        if (indexPath == null) {
            throw new IllegalArgumentException("null input path");
        }
        try (final ReadableByteChannel channel = Files.newByteChannel(indexPath)) {
            return loadIndex(indexPath.toUri().toString(), channel);
        }
    }

    /**
     * Loads the index from the provided input stream.
     *
     * @param source The named source of the reference file (used in error messages). May be null if unknown.
     * @param indexIn the input stream for the index to load.
     *
     * @return loaded index.
     *
     * @throws IOException if an I/O error occurs.
     */
    public static final GZIIndex loadIndex(final String source, final InputStream indexIn) throws IOException {
        if (indexIn == null) {
            throw new IllegalArgumentException("null input stream");
        }
        try (final ReadableByteChannel channel = Channels.newChannel(indexIn)) {
            return loadIndex(source, channel);
        }
    }

    /**
     * Loads the index from the provided channel.
     *
     * @param source The named source of the reference file (used in error messages). May be null if unknown.
     * @param channel the channel to read the index from.
     *
     * @return loaded index.
     *
     * @throws IOException if an I/O error occurs.
     */
    public static final GZIIndex loadIndex(final String source, final ReadableByteChannel channel) throws IOException {
        // allocate a buffer for re-use for read each byte
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        if (Long.BYTES != channel.read(buffer)) {
            throw getCorruptedIndexException(source, "less than " + Long.BYTES+ "bytes", null);
        }
        buffer.flip();

        final int numberOfEntries;
        try {
            numberOfEntries = Math.toIntExact(buffer.getLong());
        } catch (ArithmeticException e) {
            buffer.flip();
            throw getCorruptedIndexException(source,
                    String.format("HTSJDK cannot handle more than %d entries in .gzi index, but found %s",
                            Integer.MAX_VALUE, buffer.getLong()),
                    e);
        }

        // allocate array with the entries and add the first one
        final List<IndexEntry> entries = new ArrayList<>(numberOfEntries);

        // create a new buffer with the correct size and read into it
        buffer = allocateBuffer(numberOfEntries, false);
        channel.read(buffer);
        buffer.flip();

        for (int i = 0; i < numberOfEntries; i++) {
            final IndexEntry entry;
            try {
                entry = new IndexEntry(buffer.getLong(), buffer.getLong());
            } catch (IllegalArgumentException e) {
                throw getCorruptedIndexException(source, e.getMessage(), e);
            }
            // check if the entry is increasing in order
            if (i == 0) {
                if (entry.getUncompressedOffset() == 0 && entry.getCompressedOffset() == 0) {
                    throw getCorruptedIndexException(source, "first block index entry should not be present", null);
                }
            } else if (entries.get(i - 1).getCompressedOffset() >= entry.getCompressedOffset()
                    || entries.get(i - 1).getUncompressedOffset() >= entry.getUncompressedOffset()) {
                throw getCorruptedIndexException(source,
                        String.format("index entries in misplaced order - %s vs %s",
                                entries.get(i - 1), entry),
                        null);
            }

            entries.add(entry);
        }

        return new GZIIndex(entries);
    }

    private static final IOException getCorruptedIndexException(final String source, final String msg, final Exception e) {
        return new IOException(String.format("Corrupted index file: %s (%s)",
                msg,
                source == null ? "unknown" : source),
                e);
    }

    /**
     * Builds a {@link GZIIndex} on the fly from a BGZIP file.
     *
     * <p>Note that this method does not write the index on disk. Use {@link #writeIndex(OutputStream)} on
     * the returned object to save the index.
     *
     * @param bgzipFile the bgzip file.
     *
     * @return in-memory .gzi index.
     *
     * @throws IOException if an I/O error occurs.
     */
    public static final GZIIndex buildIndex(final Path bgzipFile) throws IOException {
        if (bgzipFile == null) {
            throw new IllegalArgumentException("null input path");
        }
        // open the file for reading as a block-compressed file
        try (final BlockCompressedInputStream bgzipStream = new BlockCompressedInputStream(Files.newInputStream(bgzipFile))) {

            // store the entries as a list
            final List<IndexEntry> entries = new ArrayList<>();

            // accumulator for number of bytes read to use in the offset for the index-entry
            int currentOffset = 0;
            // until the end of the stream
            while (bgzipStream.read() != -1) {
                currentOffset++;
                // if we are at the end of the block
                if (bgzipStream.endOfBlock()) {
                    // gets the block address (compressed offset) - requires to parse with the file pointer utils
                    final long compressed = BlockCompressedFilePointerUtil.getBlockAddress(bgzipStream.getFilePointer());
                    // add a new IndexEntry
                    entries.add(new IndexEntry(compressed, currentOffset));
                }
            }
            return new GZIIndex(entries);
        }
    }

    /**
     * Creates a {@link GZIIndex} from a BGZIP file and store it in memory and disk.
     *
     * @param bgzipFile the bgzip file.
     * @param overwrite if the .fai index already exists override it if {@code true}; otherwise, throws a {@link IOException}.
     *
     * @return the in-memory representation for the created index.
     * @throws IOException  if an IO error occurs.
     */
    public static GZIIndex createIndex(final Path bgzipFile, final boolean overwrite) throws IOException {
        final Path indexFile = resolveIndexNameForBgzipFile(bgzipFile);
        if (!overwrite && Files.exists(indexFile)) {
            // throw an exception if the file already exists
            throw new IOException("Index file " + indexFile + " already exists for " + bgzipFile);
        }
        // build the index, write and return
        final GZIIndex index = buildIndex(bgzipFile);
        index.writeIndex(new BufferedOutputStream(Files.newOutputStream(indexFile)));
        return index;
    }

    /** Gets the default index path for the bgzip file. */
    public static Path resolveIndexNameForBgzipFile(final Path bgzipFile) {
        return bgzipFile.resolveSibling(bgzipFile.getFileName().toString() + FileExtensions.GZI);
    }

    // helper method for allocate a buffer for read/write
    private static final ByteBuffer allocateBuffer(final int numberOfEntries,
            final boolean includeNumberOfEntries) {
        // everything is encoded as an unsigned long
        int size = (includeNumberOfEntries) ? Long.BYTES : 0;
        size += numberOfEntries * 2 * Long.BYTES;
        // creates a byte buffer in little-endian
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Helper class for constructing the GZIindex.
     *
     * In order to construct a GZI index addGzipBlock() should be called every time a new Gzip Block is written out and
     * the entire index will be written out when close() is called.
     */
    public static final class GZIIndexer implements Closeable {
        private int uncompressedFileOffset;
        private final OutputStream output;
        private final List<IndexEntry> entries = new ArrayList<>();

        public GZIIndexer(final OutputStream outputStream) {
            output = outputStream;
        }

        public GZIIndexer(final Path outputFile) throws IOException {
            output = Files.newOutputStream(outputFile);
        }

        // Adds a new index location given the compressed file offset and a running tally based on the uncompressed block sizes
        public void addGzipBlock(final long compressedFileOffset, final long uncompressedBlockSize) {
            IndexEntry indexEntry = new IndexEntry(compressedFileOffset, uncompressedFileOffset);
            uncompressedFileOffset += uncompressedBlockSize;
            entries.add(indexEntry);
        }

        @Override
        public void close() throws IOException {
            GZIIndex index = new GZIIndex(entries);
            index.writeIndex(output); //NOTE this relies on writeIndex closing the output stream for it
        }
    }
}
