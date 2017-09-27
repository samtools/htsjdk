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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

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
            if (Long.signum(compressedOffset) == -1) {
                throw new IllegalArgumentException("negative compressed offset: " + compressedOffset);
            }
            if (Long.signum(uncompressedOffset) == -1) {
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

    // first entry in the mapping (do not write down)
    private static final IndexEntry FIRST_MAPPING = new IndexEntry(0, 0);

    private final List<IndexEntry> entries;

    // private constructors
    private GZIIndex(final List<IndexEntry> entries) {
        this.entries = entries;
    }

    /**
     * Gets the number of blocks on the file.
     *
     * @return the number of blocks.
     */
    public int getNumberOfBlocks() {
        return entries.size();
    }

    /**
     * Gets an unmodifiable list with the index entries.
     *
     * @return index entries.
     */
    public List<IndexEntry> getIndexEntries() {
        return Collections.unmodifiableList(entries);
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
     * @param output the output file.
     *
     * @throws IOException if an I/O error occurs.
     */
    public void writeIndex(final Path output) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("null output path");
        }

        // the first entry is never written - 0, 0
        final int numberOfBlocksToWrite = getNumberOfBlocks() - 1;

        final ByteBuffer buffer = allocateBuffer(numberOfBlocksToWrite, true);
        // put the number of entries
        buffer.putLong(Integer.toUnsignedLong(numberOfBlocksToWrite));

        // except the first, iterate over all the entries
        for (final IndexEntry entry : entries.subList(1, entries.size())) {
            // implementation of entry ensures that the offsets are no negative
            buffer.putLong(entry.getCompressedOffset());
            buffer.putLong(entry.getUncompressedOffset());
        }

        // write into the output
        Files.write(output, buffer.array());
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
        // allocate a buffer for re-use for read each byte
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        try (final ByteChannel channel = Files.newByteChannel(indexPath)) {
            if (Long.BYTES != channel.read(buffer)) {
                throw getCorruptedIndexException(indexPath, "less than " + Long.BYTES+ "bytes");
            }
            buffer.flip();

            final int numberOfEntries;
            try {
                numberOfEntries = Math.toIntExact(buffer.getLong());
            } catch (ArithmeticException e) {
                buffer.flip();
                throw getCorruptedIndexException(indexPath,
                        String.format("HTSJDK cannot handle more than %d entries in .gzi index, but found %s",
                                Integer.MAX_VALUE, buffer.getLong()));
            }

            // allocate array with the entries and add the first one
            final List<IndexEntry> entries = new ArrayList<>(numberOfEntries + 1);
            entries.add(FIRST_MAPPING);

            // create a new buffer with the correct size and read into it
            buffer = allocateBuffer(numberOfEntries, false);
            channel.read(buffer);
            buffer.flip();

            for (int i = 1; i <= numberOfEntries; i++) {
                final IndexEntry entry;
                try {
                    entry = new IndexEntry(buffer.getLong(), buffer.getLong());
                } catch (IllegalArgumentException e) {
                    throw new IOException(String.format("Corrupted index file: %s (%s)",
                            indexPath.toUri(), e.getMessage()));
                }
                // check if the entry is increasing in order
                if (entries.get(i - 1).getCompressedOffset() >= entry.getCompressedOffset()
                        || entries.get(i - 1).getUncompressedOffset() >= entry.getUncompressedOffset()) {
                    throw getCorruptedIndexException(indexPath,
                            String.format("index entries in misplaced order - %s vs %s",
                                    entries.get(i - 1), entry));
                }

                entries.add(entry);
            }

            return new GZIIndex(entries);
        }
    }

    private static final IOException getCorruptedIndexException(final Path indexPath, final String msg) {
        return new IOException(String.format("Corrupted index file: %s (%s)",
                msg,
                indexPath == null ? "unknown" : indexPath.toUri()));
    }

    /**
     * Builds a {@link GZIIndex} on the fly from a BGZIP file.
     *
     * <p>Note that this method does not write the index on disk. Use {@link #writeIndex(Path)} on
     * the returned object to safe the index.
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
            final List<IndexEntry> entries = new ArrayList<>(2);
            entries.add(FIRST_MAPPING);

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
            // construct by converting into an array
            return new GZIIndex(entries);
        }
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
}
