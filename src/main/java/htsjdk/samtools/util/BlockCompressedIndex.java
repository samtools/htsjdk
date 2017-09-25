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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Represents an index of a block-compressed file (.gzi)
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
// as defined in https://github.com/samtools/htslib/issues/473
public final class BlockCompressedIndex {

    /**
     * Maps the compressed offset and uncompressed offset.
     */
    // as defined in https://github.com/samtools/htslib/issues/473
    public static class IndexEntry {
        private final long compressedOffset;
        private final long uncompressedOffset;

        private IndexEntry(final long compressedOffset, final long uncompressedOffset) {
            this.compressedOffset = compressedOffset;
            this.uncompressedOffset = uncompressedOffset;
        }

        /** Returns the compressed offset. */
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

    private final IndexEntry[] entries;

    // private constructors
    private BlockCompressedIndex(final IndexEntry[] entries) {
        this.entries = entries;
    }

    /**
     * Gets the number of blocks on the file.
     *
     * @return the number of blocks.
     */
    public int getNumberOfBlocks() {
        return entries.length;
    }

    /**
     * Gets an unmodifiable list with the index entries.
     *
     * @return index entries.
     */
    public List<IndexEntry> getIndexEntries() {
        return Collections.unmodifiableList(Arrays.asList(entries));
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof BlockCompressedIndex)) {
            return false;
        }
        return Arrays.equals(this.entries, ((BlockCompressedIndex) obj).entries);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(entries);
    }

    @Override
    public String toString() {
        return String.format("BlockCompressedIndex:%s", Arrays.toString(entries));
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

        final ByteBuffer buffer = allocateBuffer(entries.length, true);

        // put the number of entries
        buffer.putLong(Integer.toUnsignedLong(entries.length));

        for (final IndexEntry entry : entries) {
            // TODO - check if it is unsigned?
            buffer.putLong(entry.getCompressedOffset());
            buffer.putLong(entry.getUncompressedOffset());
        }

        // write into the output
        Files.write(output, buffer.array());
    }

    /**
     * Load the index from the provided file.
     *
     * @param indexPath the path for the index to load.
     *
     * @return loaded index.
     *
     * @throws IOException if an I/O error occurs.
     */
    public static final BlockCompressedIndex loadIndex(final Path indexPath) throws IOException {
        // allocate a buffer for re-use for read each byte
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        try (final ByteChannel channel = Files.newByteChannel(indexPath)) {
            if (Long.BYTES != channel.read(buffer)) {
                throw new IOException("corrupted index file");
            }
            buffer.flip();
            final int numberOfEntries = (int) buffer.getLong();

            // allocate array with the entries
            final IndexEntry[] entries = new IndexEntry[numberOfEntries];

            // create a new buffer with the correct size and read into it
            buffer = allocateBuffer(numberOfEntries, false);
            channel.read(buffer);
            buffer.flip();

            for (int i = 0; i < numberOfEntries; i++) {
                entries[i] = new IndexEntry(buffer.getLong(), (int) buffer.getLong());
            }

            return new BlockCompressedIndex(entries);
        }
    }

    /**
     * Creates an index for a block-compressed file.
     *
     * @param bgzipFile the bqzip file.
     *
     * @return generated index.
     *
     * @throws IOException if an I/O error occurs.
     */
    public static final BlockCompressedIndex createIndex(final Path bgzipFile)
            throws IOException {
        if (bgzipFile == null) {
            throw new IllegalArgumentException("null input path");
        }
        // open the file for reading as a block-compressed file
        final BlockCompressedInputStream bgzipStream =
                new BlockCompressedInputStream(Files.newInputStream(bgzipFile));

        // store the entries as a list
        final List<IndexEntry> entries = new ArrayList<>();

        // we need to track how many bytes we read to compute the offset
        // because the at the end of the block, the offset is set to 0
        int currentOffset = 0;
        // until the end of the stream
        while (bgzipStream.read() != -1) {
            currentOffset++;
            // if we are at the end of the block
            if (bgzipStream.endOfBlock()) {
                // gets the block address (compressed offset)
                // requires to parse with the file pointer utils
                final long compressed = BlockCompressedFilePointerUtil.getBlockAddress(bgzipStream.getFilePointer());
                // add a new IndexEntry
                entries.add(new IndexEntry(compressed, currentOffset));
            }
        }
        // construct by converting into an array
        return new BlockCompressedIndex(entries.toArray(new IndexEntry[entries.size()]));
    }

    // helper method for allocate a buffer for read/write
    private static final ByteBuffer allocateBuffer(final int numberOfEntries,
            final boolean includeNumerOfEntries) {
        // everything is encoded as an unsigned long
        int size = (includeNumerOfEntries) ? Long.BYTES : 0;
        size += numberOfEntries * 2 * Long.BYTES;
        // creates a byte buffer in little-endian
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }
}
