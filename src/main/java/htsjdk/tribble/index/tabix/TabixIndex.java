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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This class represent a Tabix index that has been built in memory or read from a file.  It can be queried or
 * written to a file.
 *
 * <p>A tabix index is a {@link BinningIndex} plus what is needed to interpret the tab-delimited file it indexes:
 * the {@link TabixFormat} and the sequence names, which give the binning index's reference ordinals their meaning.
 * It can be stored as either {@link TabixIndexType}; a file of either type is read by the same constructors.
 */
public class TabixIndex implements Index {
    private static final byte[] MAGIC = {'T', 'B', 'I', 1};
    public static final int MAGIC_NUMBER;
    /** The CSI magic number as tabix files store it, for telling the two formats apart. */
    public static final int CSI_MAGIC_NUMBER;

    static {
        MAGIC_NUMBER = ByteBuffer.wrap(MAGIC).order(ByteOrder.LITTLE_ENDIAN).getInt();
        CSI_MAGIC_NUMBER = ByteBuffer.wrap(BinningIndex.CSI_MAGIC)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();
    }

    // Within a CSI file the tabix header lives in the aux block: the six format fields, then the names' length.
    private static final int CSI_AUX_HEADER_BYTES = 7 * 4;

    private final TabixFormat formatSpec;
    private final List<String> sequenceNames;
    private final BinningIndex binningIndex;
    private final TabixIndexType indexType;

    /**
     * A TBI index.
     *
     * @param formatSpec    Information about how to interpret the file being indexed.  Unused by this class other than
     *                      written to an output file.
     * @param sequenceNames Sequences in the file being indexed, in the order they appear in the file.
     * @param binningIndex  The index proper, with one reference for each element of sequenceNames; must use the
     *                      TBI binning scheme
     */
    public TabixIndex(final TabixFormat formatSpec, final List<String> sequenceNames, final BinningIndex binningIndex) {
        this(formatSpec, sequenceNames, binningIndex, TabixIndexType.TBI);
    }

    /**
     * @param formatSpec    Information about how to interpret the file being indexed.  Unused by this class other than
     *                      written to an output file.
     * @param sequenceNames Sequences in the file being indexed, in the order they appear in the file.
     * @param binningIndex  The index proper, with one reference for each element of sequenceNames
     * @param indexType     The format the index is written in; TBI requires the TBI binning scheme
     */
    public TabixIndex(
            final TabixFormat formatSpec,
            final List<String> sequenceNames,
            final BinningIndex binningIndex,
            final TabixIndexType indexType) {
        if (sequenceNames.size() != binningIndex.getReferenceCount()) {
            throw new IllegalArgumentException("sequenceNames.size() != binningIndex.getReferenceCount()");
        }
        if (indexType == TabixIndexType.TBI
                && (binningIndex.getMinShift() != BinningIndex.BAI_MIN_SHIFT
                        || binningIndex.getDepth() != BinningIndex.BAI_DEPTH)) {
            throw new IllegalArgumentException(String.format(
                    "A TBI index must use the fixed binning scheme minShift=%d, depth=%d, not minShift=%d, depth=%d",
                    BinningIndex.BAI_MIN_SHIFT,
                    BinningIndex.BAI_DEPTH,
                    binningIndex.getMinShift(),
                    binningIndex.getDepth()));
        }
        this.formatSpec = formatSpec.clone();
        this.sequenceNames = Collections.unmodifiableList(new ArrayList<String>(sequenceNames));
        this.binningIndex = binningIndex;
        this.indexType = indexType;
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
     * Reads a TBI or CSI file, telling them apart by the magic number.
     *
     * @param inputStream positioned at the magic number, already decompressing
     * @param closeInputStream whether to close the stream once the index is read, even on failure
     */
    private TabixIndex(final InputStream inputStream, final boolean closeInputStream) throws IOException {
        // The magic number decides the format, and each format's reader wants to see it, so peek and put it back.
        final PushbackInputStream stream = new PushbackInputStream(inputStream, 4);
        final BinaryCodec codec = new BinaryCodec(stream);
        try {
            final byte[] magic = new byte[4];
            codec.readBytes(magic);
            stream.unread(magic);
            final int magicNumber =
                    ByteBuffer.wrap(magic).order(ByteOrder.LITTLE_ENDIAN).getInt();
            final TabixFormat formatSpec = new TabixFormat();
            final List<String> sequenceNames;
            if (magicNumber == MAGIC_NUMBER) {
                indexType = TabixIndexType.TBI;
                codec.readInt(); // the magic number, again
                final int numSequences = codec.readInt();
                readFormat(codec, formatSpec);
                final byte[] nameBlock = new byte[codec.readInt()];
                codec.readBytes(nameBlock);
                sequenceNames = parseNames(nameBlock);
                if (sequenceNames.size() != numSequences) {
                    throw new TribbleException(String.format(
                            "Tabix header lists %d sequences but names %d", numSequences, sequenceNames.size()));
                }
                binningIndex = BinningIndex.readBaiLayout(
                        codec, numSequences, BinningIndex.BAI_MIN_SHIFT, BinningIndex.BAI_DEPTH);
            } else if (magicNumber == CSI_MAGIC_NUMBER) {
                indexType = TabixIndexType.CSI;
                final BinningIndex.CsiContents contents = BinningIndex.readCsi(codec);
                binningIndex = contents.index();
                sequenceNames = parseAux(contents.aux(), formatSpec);
                if (sequenceNames.size() != binningIndex.getReferenceCount()) {
                    throw new TribbleException(String.format(
                            "CSI index covers %d sequences but its tabix header names %d",
                            binningIndex.getReferenceCount(), sequenceNames.size()));
                }
            } else {
                throw new TribbleException(
                        String.format("Unexpected magic number 0x%x; not a TBI or CSI index", magicNumber));
            }
            this.formatSpec = formatSpec;
            this.sequenceNames = Collections.unmodifiableList(sequenceNames);
        } catch (final RuntimeEOFException e) {
            throw new TribbleException("Premature end of file reading Tabix index", e);
        } catch (final IllegalArgumentException e) {
            throw new TribbleException("Malformed Tabix index: " + e.getMessage(), e);
        } finally {
            if (closeInputStream) CloserUtil.close(inputStream);
        }
    }

    /** The six format fields that follow the sequence count in a TBI header, and open a CSI aux block. */
    private static void readFormat(final BinaryCodec codec, final TabixFormat formatSpec) {
        formatSpec.flags = codec.readInt();
        formatSpec.sequenceColumn = codec.readInt();
        formatSpec.startPositionColumn = codec.readInt();
        formatSpec.endPositionColumn = codec.readInt();
        formatSpec.metaCharacter = (char) codec.readInt();
        formatSpec.numHeaderLinesToSkip = codec.readInt();
    }

    /** What a tabix index says of the file it indexes: how to read its columns, and its sequences in index order. */
    public record Header(TabixFormat format, List<String> sequenceNames) {}

    /**
     * Reads the tabix header that a CSI file made by tabix carries in its aux block, for a reader that holds the
     * index proper some other way and needs only to know which sequence each of its references is.
     *
     * @param aux the aux block of a CSI file
     * @throws TribbleException if the block does not hold a tabix header
     */
    public static Header readCsiAux(final byte[] aux) {
        final TabixFormat format = new TabixFormat();
        return new Header(format, Collections.unmodifiableList(parseAux(aux, format)));
    }

    /**
     * The tabix header a CSI file carries in its aux block: the format fields, the names' length, then the names.
     * A CSI whose aux block lacks this (as those samtools writes for BAM do) is not a tabix index.
     */
    private static List<String> parseAux(final byte[] aux, final TabixFormat formatSpec) {
        if (aux.length < CSI_AUX_HEADER_BYTES) {
            throw new TribbleException(String.format(
                    "CSI index has a %d-byte aux block, too short for a tabix header; it is not a tabix index",
                    aux.length));
        }
        final BinaryCodec codec = new BinaryCodec(new ByteArrayInputStream(aux));
        readFormat(codec, formatSpec);
        final int nameBlockLength = codec.readInt();
        if (nameBlockLength != aux.length - CSI_AUX_HEADER_BYTES) {
            throw new TribbleException(String.format(
                    "CSI tabix header claims %d bytes of sequence names but the aux block has room for %d",
                    nameBlockLength, aux.length - CSI_AUX_HEADER_BYTES));
        }
        return parseNames(Arrays.copyOfRange(aux, CSI_AUX_HEADER_BYTES, aux.length));
    }

    /** Splits the NUL-terminated names block. */
    private static List<String> parseNames(final byte[] nameBlock) {
        final List<String> sequenceNames = new ArrayList<>();
        int startPos = 0;
        for (int endPos = 0; endPos < nameBlock.length; endPos++) {
            if (nameBlock[endPos] == '\0') {
                sequenceNames.add(StringUtil.bytesToString(nameBlock, startPos, endPos - startPos));
                startPos = endPos + 1;
            }
        }
        if (startPos != nameBlock.length) {
            throw new TribbleException("Tabix header format exception.  Sequence name block is not NUL-terminated");
        }
        return sequenceNames;
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
     * @return the format this index is, or is to be, stored in
     */
    public TabixIndexType getIndexType() {
        return indexType;
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
        write(Tribble.tabixIndexPath(featurePath, indexType));
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
        if (indexType == TabixIndexType.TBI) {
            codec.writeInt(MAGIC_NUMBER);
            codec.writeInt(sequenceNames.size());
            writeFormatAndNames(codec);
            binningIndex.writeBaiLayout(codec);
        } else {
            final ByteArrayOutputStream aux = new ByteArrayOutputStream();
            final BinaryCodec auxCodec = new BinaryCodec(aux);
            writeFormatAndNames(auxCodec);
            auxCodec.close();
            binningIndex.writeCsi(codec, aux.toByteArray());
        }
    }

    /** The tabix header proper: the six format fields, the names' length, then the NUL-terminated names. */
    private void writeFormatAndNames(final BinaryCodec codec) {
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
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final TabixIndex index = (TabixIndex) o;

        if (indexType != index.indexType) return false;
        if (!formatSpec.equals(index.formatSpec)) return false;
        if (!binningIndex.equals(index.binningIndex)) return false;
        if (!sequenceNames.equals(index.sequenceNames)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = 31 * indexType.hashCode() + formatSpec.hashCode();
        result = 31 * result + sequenceNames.hashCode();
        result = 31 * result + binningIndex.hashCode();
        return result;
    }
}
