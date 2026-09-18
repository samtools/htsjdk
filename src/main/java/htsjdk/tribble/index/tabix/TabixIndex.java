/*
 * The MIT License
 *
 * Copyright (c) 2014 The Broad Institute
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
package htsjdk.tribble.index.tabix;

import htsjdk.index.BinningIndex;
import htsjdk.samtools.Chunk;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.RuntimeEOFException;
import htsjdk.samtools.util.StringUtil;
import htsjdk.tribble.Tribble;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.index.Block;
import htsjdk.tribble.index.Index;
import htsjdk.tribble.util.LittleEndianOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This class represent a Tabix index that has been built in memory or read from a file.  It can be queried or
 * written to a file.
 *
 * <p>A tabix index is a {@link BinningIndex} plus what is needed to interpret the tab-delimited file it indexes:
 * the {@link TabixFormat} and the sequence names, which give the binning index's reference ordinals their meaning.
 */
public class TabixIndex implements Index {
    private static final byte[] MAGIC = {'T', 'B', 'I', 1};
    public static final int MAGIC_NUMBER;

    static {
        final ByteBuffer bb = ByteBuffer.allocate(MAGIC.length);
        bb.put(MAGIC);
        bb.flip();
        MAGIC_NUMBER = bb.order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private final TabixFormat formatSpec;
    private final List<String> sequenceNames;
    private final BinningIndex binningIndex;

    /**
     * @param formatSpec    Information about how to interpret the file being indexed.  Unused by this class other than
     *                      written to an output file.
     * @param sequenceNames Sequences in the file being indexed, in the order they appear in the file.
     * @param binningIndex  The index proper, with one reference for each element of sequenceNames
     */
    public TabixIndex(final TabixFormat formatSpec, final List<String> sequenceNames, final BinningIndex binningIndex) {
        if (sequenceNames.size() != binningIndex.getReferenceCount()) {
            throw new IllegalArgumentException("sequenceNames.size() != binningIndex.getReferenceCount()");
        }
        this.formatSpec = formatSpec.clone();
        this.sequenceNames = Collections.unmodifiableList(new ArrayList<String>(sequenceNames));
        this.binningIndex = binningIndex;
    }

    /**
     * @param inputStream This is expected to be buffered and be gzip-decompressing as appropriate.  Caller
     *                    should close input stream after ctor returns.
     */
    public TabixIndex(final InputStream inputStream) throws IOException {
        this(inputStream, false);
    }

    /**
     * Convenient ctor that opens the path, wraps with with BGZF reader, and closes after reading index.
     */
    public TabixIndex(final Path tabixPath) throws IOException {
        this(new BlockCompressedInputStream(tabixPath), true);
    }

    /**
     * Reads the tabix header, then hands the stream to {@link BinningIndex#readBaiLayout} for the body.
     *
     * @param inputStream positioned at the magic number, already decompressing
     * @param closeInputStream whether to close the stream once the index is read, even on failure
     */
    private TabixIndex(final InputStream inputStream, final boolean closeInputStream) throws IOException {
        final BinaryCodec codec = new BinaryCodec(inputStream);
        try {
            if (codec.readInt() != MAGIC_NUMBER) {
                throw new TribbleException(String.format("Unexpected magic number 0x%x", MAGIC_NUMBER));
            }
            final int numSequences = codec.readInt();
            formatSpec = new TabixFormat();
            formatSpec.flags = codec.readInt();
            formatSpec.sequenceColumn = codec.readInt();
            formatSpec.startPositionColumn = codec.readInt();
            formatSpec.endPositionColumn = codec.readInt();
            formatSpec.metaCharacter = (char) codec.readInt();
            formatSpec.numHeaderLinesToSkip = codec.readInt();
            final byte[] nameBlock = new byte[codec.readInt()];
            codec.readBytes(nameBlock);
            final List<String> sequenceNames = new ArrayList<String>(numSequences);
            int startPos = 0;
            for (int i = 0; i < numSequences; ++i) {
                int endPos = startPos;
                while (endPos < nameBlock.length && nameBlock[endPos] != '\0') ++endPos;
                if (endPos == nameBlock.length) {
                    throw new TribbleException(
                            "Tabix header format exception.  Sequence name block is shorter than expected");
                }
                sequenceNames.add(StringUtil.bytesToString(nameBlock, startPos, endPos - startPos));
                startPos = endPos + 1;
            }
            if (startPos != nameBlock.length) {
                throw new TribbleException(
                        "Tabix header format exception.  Sequence name block is longer than expected");
            }
            binningIndex =
                    BinningIndex.readBaiLayout(codec, numSequences, BinningIndex.BAI_MIN_SHIFT, BinningIndex.BAI_DEPTH);
            this.sequenceNames = Collections.unmodifiableList(sequenceNames);
        } catch (final RuntimeEOFException e) {
            throw new TribbleException("Premature end of file reading Tabix index", e);
        } finally {
            if (closeInputStream) CloserUtil.close(inputStream);
        }
    }

    /**
     * @param chr   the chromosome
     * @param start the start position, one-based, inclusive.
     * @param end   the end position, one-based, inclusive.
     * @return List of regions of file that are candidates for the given query.
     */
    @Override
    public List<Block> getBlocks(final String chr, final int start, final int end) {
        final int sequenceIndex = sequenceNames.indexOf(chr);
        if (sequenceIndex == -1) {
            return Collections.emptyList();
        }
        final List<Chunk> chunks =
                binningIndex.getSpanOverlapping(sequenceIndex, start, end).getChunks();
        final List<Block> ret = new ArrayList<>(chunks.size());
        for (final Chunk chunk : chunks) {
            ret.add(new Block(chunk.getChunkStart(), chunk.getChunkEnd() - chunk.getChunkStart()));
        }
        return ret;
    }

    @Override
    public boolean isCurrentVersion() {
        return true;
    }

    @Override
    public List<String> getSequenceNames() {
        return sequenceNames;
    }

    @Override
    public boolean containsChromosome(final String chr) {
        return sequenceNames.contains(chr);
    }

    /**
     * No arbitrary properties in Tabix
     */
    @Override
    public Map<String, String> getProperties() {
        return null;
    }

    @Override
    public boolean equalsIgnoreProperties(final Object o) {
        return equals(o);
    }

    public TabixFormat getFormatSpec() {
        return formatSpec;
    }

    /**
     * @return the index proper; its reference ordinals are positions in {@link #getSequenceNames()}
     */
    public BinningIndex getBinningIndex() {
        return binningIndex;
    }

    /**
     * Writes the index with BGZF.
     *
     * @param tabixPath Where to write the index.
     */
    @Override
    public void write(final Path tabixPath) throws IOException {
        try (final LittleEndianOutputStream los = new LittleEndianOutputStream(
                new BlockCompressedOutputStream(Files.newOutputStream(tabixPath), (Path) null))) {
            write(los);
        }
    }

    /**
     * Writes to a path with appropriate name and directory based on feature path.
     *
     * @param featurePath Path being indexed.
     * @throws IOException if featureFile is not a normal file.
     */
    @Override
    public void writeBasedOnFeaturePath(final Path featurePath) throws IOException {
        if (!Files.isRegularFile(featurePath)) {
            throw new IOException("Cannot write based on a non-regular file: " + featurePath.toUri());
        }
        write(Tribble.tabixIndexPath(featurePath));
    }

    /**
     * @param los It is assumes that caller has done appropriate buffering and BlockCompressedOutputStream wrapping.
     *            Caller should close output stream after invoking this method.
     * @throws IOException
     */
    @Override
    public void write(final LittleEndianOutputStream los) throws IOException {
        // The codec is not closed, since that would close the caller's stream; it holds nothing to flush.
        final BinaryCodec codec = new BinaryCodec(los);
        codec.writeInt(MAGIC_NUMBER);
        codec.writeInt(sequenceNames.size());
        codec.writeInt(formatSpec.flags);
        codec.writeInt(formatSpec.sequenceColumn);
        codec.writeInt(formatSpec.startPositionColumn);
        codec.writeInt(formatSpec.endPositionColumn);
        codec.writeInt(formatSpec.metaCharacter);
        codec.writeInt(formatSpec.numHeaderLinesToSkip);
        int nameBlockSize = sequenceNames.size(); // null terminators
        for (final String sequenceName : sequenceNames) nameBlockSize += sequenceName.length();
        codec.writeInt(nameBlockSize);
        for (final String sequenceName : sequenceNames) {
            codec.writeBytes(StringUtil.stringToBytes(sequenceName));
            codec.writeByte(0);
        }
        binningIndex.writeBaiLayout(codec);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final TabixIndex index = (TabixIndex) o;

        if (!formatSpec.equals(index.formatSpec)) return false;
        if (!binningIndex.equals(index.binningIndex)) return false;
        if (!sequenceNames.equals(index.sequenceNames)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = formatSpec.hashCode();
        result = 31 * result + sequenceNames.hashCode();
        result = 31 * result + binningIndex.hashCode();
        return result;
    }
}
