/*
 * The MIT License
 *
 * Copyright (c) 2018 The Broad Institute
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
package htsjdk.samtools;

import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import htsjdk.samtools.util.IOExtensions;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * SBI is an index into BGZF-compressed data files, which has an entry for the file position of the start of every
 * <i>n</i>th record. Reads files that were created by {@link BAMSBIIndexer}.
 */
public final class SBIIndex implements Serializable {

    public static class Header implements Serializable {
        private final long fileLength;
        private final byte[] md5;
        private final byte[] uuid;
        private final long totalNumberOfRecords;
        private final long granularity;

        public Header(long fileLength, byte[] md5, byte[] uuid, long totalNumberOfRecords, long granularity) {
            this.fileLength = fileLength;
            this.md5 = md5;
            this.uuid = uuid;
            this.totalNumberOfRecords = totalNumberOfRecords;
            this.granularity = granularity;
        }

        public long getFileLength() {
            return fileLength;
        }

        public byte[] getMd5() {
            return md5;
        }

        public byte[] getUuid() {
            return uuid;
        }

        public long getTotalNumberOfRecords() {
            return totalNumberOfRecords;
        }

        public long getGranularity() {
            return granularity;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Header header = (Header) o;
            return fileLength == header.fileLength &&
                    totalNumberOfRecords == header.totalNumberOfRecords &&
                    granularity == header.granularity &&
                    Arrays.equals(md5, header.md5) &&
                    Arrays.equals(uuid, header.uuid);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(fileLength, totalNumberOfRecords, granularity);
            result = 31 * result + Arrays.hashCode(md5);
            result = 31 * result + Arrays.hashCode(uuid);
            return result;
        }

        @Override
        public String toString() {
            return "Header{" +
                    "fileLength=" + fileLength +
                    ", md5=" + Arrays.toString(md5) +
                    ", uuid=" + Arrays.toString(uuid) +
                    ", totalNumberOfRecords=" + totalNumberOfRecords +
                    ", granularity=" + granularity +
                    '}';
        }
    }

    /**
     * @deprecated Use {@link IOExtensions#SBI_FILE_EXTENSION} instead.
     */
    @Deprecated
    public static final String FILE_EXTENSION = IOExtensions.SBI_FILE_EXTENSION;

    /**
     * SBI magic number.
     */
    static final byte[] SBI_MAGIC = "SBI\1".getBytes();

    private final Header header;
    private final long[] virtualOffsets;

    /**
     * Create an in-memory SBI with the given virtual offsets.
     * @param virtualOffsets the offsets in the index
     */
    public SBIIndex(final Header header, final long[] virtualOffsets) {
        this.header = header;
        this.virtualOffsets = virtualOffsets;
        if (this.virtualOffsets.length == 0) {
            throw new RuntimeException("Invalid SBI format: should contain at least one offset");
        }
    }

    /**
     * Load an SBI into memory from a path.
     * @param path the path to the SBI file
     * @throws IOException as per java IO contract
     */
    public static SBIIndex load(final Path path) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            return readIndex(in);
        }
    }

    /**
     * Load an SBI into memory from a stream.
     * @param in the stream to read the SBI from
     */
    public static SBIIndex load(final InputStream in) {
        return readIndex(in);
    }

    private static SBIIndex readIndex(final InputStream in) {
        final BinaryCodec binaryCodec = new BinaryCodec(in);
        final Header header = readHeader(binaryCodec);
        final long numOffsetsLong = binaryCodec.readLong();
        if (numOffsetsLong > Integer.MAX_VALUE) {
            throw new RuntimeException(String.format("Cannot read SBI with more than %s offsets.", Integer.MAX_VALUE));
        }
        final int numOffsets = (int) numOffsetsLong;
        final long[] virtualOffsets = new long[numOffsets];
        long prev = -1;
        for (int i = 0; i < numOffsets; i++) {
            final long cur = binaryCodec.readLong();
            if (prev > cur) {
                throw new RuntimeException(String.format(
                        "Invalid SBI; offsets not in order: %#x > %#x",
                        prev, cur));
            }
            virtualOffsets[i] = cur;
            prev = cur;
        }
        return new SBIIndex(header, virtualOffsets);
    }

    private static Header readHeader(final BinaryCodec binaryCodec) {
        final byte[] buffer = new byte[SBI_MAGIC.length];
        binaryCodec.readBytes(buffer);
        if (!Arrays.equals(buffer, SBI_MAGIC)) {
            throw new RuntimeException("Invalid file header in SBI: " + new String(buffer) + " (" + Arrays.toString(buffer) + ")");
        }
        final long fileLength = binaryCodec.readLong();
        final byte[] md5 = new byte[16];
        binaryCodec.readBytes(md5);
        final byte[] uuid = new byte[16];
        binaryCodec.readBytes(uuid);
        final long totalNumberOfRecords = binaryCodec.readLong();
        final long granularity = binaryCodec.readLong();
        return new Header(fileLength, md5, uuid, totalNumberOfRecords, granularity);
    }

    /**
     * Returns the index header.
     *
     * @return the header
     */
    public Header getHeader() {
        return header;
    }

    /**
     * Returns the granularity of the index, that is the number of alignments between subsequent entries in the index,
     * or zero if not specified.
     * @return the granularity of the index
     */
    public long getGranularity() {
        return header.getGranularity();
    }

    /**
     * Returns the entries in the index.
     *
     * @return an array of file pointers for all the alignment offsets in the index, in ascending order. The last
     *      virtual file pointer is the position at which the next record would start if it were added to the file.
     */
    public long[] getVirtualOffsets() {
        return virtualOffsets;
    }

    /**
     * Returns number of entries in the index.
     *
     * @return the number of virtual offsets in the index
     */
    public long size() {
        return virtualOffsets.length;
    }

    /**
     * Returns the length of the data file in bytes.
     *
     * @return the length of the data file in bytes
     */
    public long dataFileLength() {
        return header.getFileLength();
    }

    /**
     * Split the data file for this index into non-overlapping chunks of roughly the given size that cover the whole
     * file and that can be read independently of one another.
     *
     * @param splitSize the rough size of each split in bytes
     * @return a list of contiguous, non-overlapping, sorted chunks that cover the whole data file
     * @see #getChunk(long, long)
     */
    public List<Chunk> split(final long splitSize) {
        if (splitSize <= 0) {
            throw new IllegalArgumentException(String.format("Split size must be positive: %s", splitSize));
        }
        final long fileSize = dataFileLength();
        final List<Chunk> chunks = new ArrayList<>();
        for (long splitStart = 0; splitStart < fileSize; splitStart += splitSize) {
            final Chunk chunk = getChunk(splitStart, splitStart + splitSize);
            if (chunk != null) {
                chunks.add(chunk);
            }
        }
        return chunks;
    }

    /**
     * Return a chunk that corresponds to the given range in the data file. Note that the chunk does not necessarily
     * completely cover the given range, however this method will map a set of contiguous, non-overlapping file ranges
     * that cover the whole data file to a set of contiguous, non-overlapping chunks that cover the whole data file.
     *
     * @param splitStart the start of the file range (inclusive)
     * @param splitEnd the start of the file range (exclusive)
     * @return a chunk whose virtual start is at the first alignment start position that is greater than or equal to the
     * given split start position, and whose virtual end is at the first alignment start position that is greater than
     * or equal to the given split end position, or null if the chunk would be empty.
     * @see #split(long)
     */
    public Chunk getChunk(final long splitStart, final long splitEnd) {
        if (splitStart >= splitEnd) {
            throw new IllegalArgumentException(String.format("Split start (%s) must be less than end (%s)", splitStart, splitEnd));
        }
        final long lastVirtualOffset = virtualOffsets[virtualOffsets.length - 1];
        final long maxEnd = BlockCompressedFilePointerUtil.getBlockAddress(lastVirtualOffset);
        final long actualSplitStart = Math.min(splitStart, maxEnd);
        final long actualSplitEnd = Math.min(splitEnd, maxEnd);
        final long virtualSplitStart = BlockCompressedFilePointerUtil.makeFilePointer(actualSplitStart);
        final long virtualSplitEnd = BlockCompressedFilePointerUtil.makeFilePointer(actualSplitEnd);
        final long virtualSplitStartAlignment = ceiling(virtualSplitStart);
        final long virtualSplitEndAlignment = ceiling(virtualSplitEnd);
        if (virtualSplitStartAlignment == virtualSplitEndAlignment) {
            return null;
        }
        return new Chunk(virtualSplitStartAlignment, virtualSplitEndAlignment);
    }

    private long ceiling(final long virtualOffset) {
        int index = Arrays.binarySearch(virtualOffsets, virtualOffset);
        if (index < 0) {
            index = -index - 1;
            if (index == virtualOffsets.length) {
                long lastVirtualOffset = virtualOffsets[virtualOffsets.length - 1];
                throw new IllegalArgumentException(String.format("No virtual offset found for virtual file pointer %s, last virtual offset %s",
                        BlockCompressedFilePointerUtil.asString(virtualOffset), BlockCompressedFilePointerUtil.asString(lastVirtualOffset)));
            }
        }
        return virtualOffsets[index];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SBIIndex sbiIndex = (SBIIndex) o;
        return Objects.equals(header, sbiIndex.header) &&
                Arrays.equals(virtualOffsets, sbiIndex.virtualOffsets);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(header);
        result = 31 * result + Arrays.hashCode(virtualOffsets);
        return result;
    }

    @Override
    public String toString() {
        String virtualOffsetsString;
        if (virtualOffsets.length > 30) {
            virtualOffsetsString = Arrays.toString(Arrays.copyOfRange(virtualOffsets, 0, 30)).replace("]", ", ...]");
        } else {
            virtualOffsetsString = Arrays.toString(virtualOffsets);
        }
        return "SBIIndex{" +
                "header=" + header +
                ", numVirtualOffsets=" + virtualOffsets.length +
                ", virtualOffsets=" + virtualOffsetsString +
                '}';
    }
}
