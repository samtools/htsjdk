/*
 * The MIT License
 *
 * Copyright (c) 2013 The Broad Institute
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
package htsjdk.tribble.readers;

import htsjdk.index.BinningIndex;
import htsjdk.samtools.Chunk;
import htsjdk.samtools.seekablestream.ISeekableStreamFactory;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.seekablestream.SeekableStreamFactory;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.BlockCompressedStreamConstants;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.index.tabix.TabixIndex;
import htsjdk.tribble.util.ParsingUtils;
import htsjdk.tribble.util.TabixUtils;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * @author Heng Li <hengli@broadinstitute.org>
 */
public class TabixReader implements AutoCloseable {
    private final String mFilePath;
    private final String mIndexPath;
    private final Function<SeekableByteChannel, SeekableByteChannel> mIndexWrapper;
    private final BlockCompressedInputStream mFp;

    private int mPreset;
    private int mSc;
    private int mBc;
    private int mEc;
    private int mMeta;

    // private int mSkip; (not used)
    private String[] mSeq;

    private Map<String, Integer> mChr2tid;

    /** default buffer size for <code>readLine()</code> */
    private static final int DEFAULT_BUFFER_SIZE = 1000;

    private BinningIndex mIndex;

    private static class TIntv {
        int tid, beg, end;
    }

    private static boolean less64(final long u, final long v) { // unsigned 64-bit comparison
        return (u < v) ^ (u < 0) ^ (v < 0);
    }

    /**
     * @param filePath path to the data file/uri
     */
    public TabixReader(final String filePath) throws IOException {
        this(
                filePath,
                null,
                SeekableStreamFactory.getInstance()
                        .getBufferedStream(SeekableStreamFactory.getInstance().getStreamFor(filePath)));
    }

    /**
     * @param filePath path to the of the data file/uri
     * @param indexPath Full path to the index file. Auto-generated if null
     */
    public TabixReader(final String filePath, final String indexPath) throws IOException {
        this(
                filePath,
                indexPath,
                SeekableStreamFactory.getInstance()
                        .getBufferedStream(SeekableStreamFactory.getInstance().getStreamFor(filePath)));
    }

    /**
     * @param filePath path to the data file/uri
     * @param indexPath Full path to the index file. Auto-generated if null
     * @param wrapper a wrapper to apply to the raw byte stream of the data file if is a uri representing a {@link java.nio.file.Path}
     * @param indexWrapper a wrapper to apply to the raw byte stream of the index file if it is a uri representing a {@link java.nio.file.Path}
     */
    public TabixReader(
            final String filePath,
            final String indexPath,
            final Function<SeekableByteChannel, SeekableByteChannel> wrapper,
            final Function<SeekableByteChannel, SeekableByteChannel> indexWrapper)
            throws IOException {
        this(
                filePath,
                indexPath,
                SeekableStreamFactory.getInstance()
                        .getBufferedStream(SeekableStreamFactory.getInstance().getStreamFor(filePath, wrapper)),
                indexWrapper);
    }

    /**
     * @param filePath Path to the data file  (used for error messages only)
     * @param stream Seekable stream from which the data is read
     */
    public TabixReader(final String filePath, SeekableStream stream) throws IOException {
        this(filePath, null, stream);
    }

    /**
     * @param filePath Path to the data file  (used for error messages only)
     * @param indexPath Full path to the index file. Auto-generated if null
     * @param stream Seekable stream from which the data is read
     */
    public TabixReader(final String filePath, final String indexPath, SeekableStream stream) throws IOException {
        this(filePath, indexPath, stream, null);
    }

    /**
     * @param filePath Path to the data file (used for error messages only)
     * @param indexPath Full path to the index file. Auto-generated if null
     * @param indexWrapper a wrapper to apply to the raw byte stream of the index file if it is a uri representing a {@link java.nio.file.Path}
     * @param stream Seekable stream from which the data is read
     */
    public TabixReader(
            final String filePath,
            final String indexPath,
            SeekableStream stream,
            Function<SeekableByteChannel, SeekableByteChannel> indexWrapper)
            throws IOException {
        mFilePath = filePath;
        requireBlockCompressed(filePath, stream);
        mFp = new BlockCompressedInputStream(stream);
        mIndexWrapper = indexWrapper;
        if (indexPath == null) {
            mIndexPath = TabixUtils.findIndex(filePath);
            if (mIndexPath == null) {
                throw new TribbleException(String.format(
                        "No tabix index found for %s: neither %s nor %s exists",
                        filePath,
                        ParsingUtils.appendToPath(filePath, FileExtensions.CSI),
                        ParsingUtils.appendToPath(filePath, FileExtensions.TABIX_INDEX)));
            }
        } else {
            mIndexPath = indexPath;
        }
        readIndex();
    }

    /**
     * A tabix index only makes sense over BGZF, and a file that is merely gzipped would otherwise fail obscurely
     * on the first seek. Reads the first block header and rewinds.
     */
    private static void requireBlockCompressed(final String filePath, final SeekableStream stream) throws IOException {
        final boolean valid = BlockCompressedInputStream.isValidFile(
                new BufferedInputStream(stream, BlockCompressedStreamConstants.BLOCK_HEADER_LENGTH));
        stream.seek(0);
        if (!valid) {
            throw new TribbleException(filePath + " is not BGZF (block-compressed) and so cannot be tabix-indexed;"
                    + " it may be plain gzip, in which case recompress it with bgzip");
        }
    }

    /** return the source (filename/URL) of that reader */
    public String getSource() {
        return this.mFilePath;
    }

    public static int readInt(final InputStream is) throws IOException {
        byte[] buf = new byte[4];
        is.read(buf);
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    public static long readLong(final InputStream is) throws IOException {
        final byte[] buf = new byte[8];
        is.read(buf);
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    public static String readLine(final InputStream is) throws IOException {
        return readLine(is, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Reads a line of UTF-8 encoded text terminated by {@code '\n'}. A final line that ends at the end of the
     * stream without a terminator is returned like any other.
     *
     * @param is the input stream
     * @param bufferCapacity the initial buffer size, must be greater than 0
     * @return the line (without terminator) or null if there is no more input
     * @throws IOException if an I/O error occurs
     */
    private static String readLine(final InputStream is, final int bufferCapacity) throws IOException {
        byte[] buf = new byte[bufferCapacity];
        int len = 0;
        int c;
        while ((c = is.read()) >= 0 && c != '\n') {
            if (len == buf.length) {
                buf = Arrays.copyOf(buf, buf.length * 2);
            }
            buf[len++] = (byte) c;
        }
        if (c < 0 && len == 0) return null;
        return new String(buf, 0, len, StandardCharsets.UTF_8);
    }

    /**
     * Read the Tabix index from a file
     *
     * @param fp File pointer
     */
    private void readIndex(final SeekableStream fp) throws IOException {
        if (fp == null) return;
        final TabixIndex index;
        try (final BlockCompressedInputStream is = new BlockCompressedInputStream(fp)) {
            index = new TabixIndex(is);
        }
        final TabixFormat format = index.getFormatSpec();
        mPreset = format.flags;
        mSc = format.sequenceColumn;
        mBc = format.startPositionColumn;
        mEc = format.endPositionColumn;
        mMeta = format.metaCharacter;
        mSeq = index.getSequenceNames().toArray(new String[0]);
        mChr2tid = new LinkedHashMap<String, Integer>(this.mSeq.length);
        for (int i = 0; i < mSeq.length; i++) mChr2tid.put(mSeq[i], i);
        mIndex = index.getBinningIndex();
    }

    /**
     * Read the Tabix index from the default file.
     */
    private void readIndex() throws IOException {
        final ISeekableStreamFactory ssf = SeekableStreamFactory.getInstance();
        readIndex(ssf.getBufferedStream(ssf.getStreamFor(mIndexPath, mIndexWrapper), 128000));
    }

    /**
     * Read one line from the data file.
     */
    public String readLine() throws IOException {
        return readLine(mFp, DEFAULT_BUFFER_SIZE);
    }

    /** return chromosome ID or -1 if it is unknown */
    public int chr2tid(final String chr) {
        final Integer tid = this.mChr2tid.get(chr);
        return tid == null ? -1 : tid;
    }

    /** return the chromosomes in that tabix file */
    public Set<String> getChromosomes() {
        return Collections.unmodifiableSet(this.mChr2tid.keySet());
    }

    /**
     * Parse a region in the format of "chr1", "chr1:100" or "chr1:100-1000"
     *
     * @param reg Region string
     * @return An array where the three elements are sequence_id,
     *         region_begin and region_end. On failure, sequence_id==-1.
     */
    public int[] parseReg(final String reg) { // FIXME: NOT working when the sequence name contains : or -.
        String chr;
        int colon, hyphen;
        int[] ret = new int[3];
        colon = reg.indexOf(':');
        hyphen = reg.indexOf('-');
        chr = colon >= 0 ? reg.substring(0, colon) : reg;
        ret[1] = colon >= 0 ? Integer.parseInt(reg.substring(colon + 1, hyphen >= 0 ? hyphen : reg.length())) - 1 : 0;
        ret[2] = hyphen >= 0 ? Integer.parseInt(reg.substring(hyphen + 1)) : 0x7fffffff;
        ret[0] = this.chr2tid(chr);
        return ret;
    }

    private TIntv getIntv(final String s) {
        TIntv intv = new TIntv();
        int col = 0, end = 0, beg = 0;
        while ((end = s.indexOf('\t', beg)) >= 0 || end == -1) {
            ++col;
            if (col == mSc) {
                intv.tid = chr2tid(end != -1 ? s.substring(beg, end) : s.substring(beg));
            } else if (col == mBc) {
                intv.beg = intv.end = Integer.parseInt(end != -1 ? s.substring(beg, end) : s.substring(beg));
                if ((mPreset & 0x10000) != 0) ++intv.end;
                else --intv.beg;
                if (intv.beg < 0) intv.beg = 0;
                if (intv.end < 1) intv.end = 1;
            } else { // FIXME: SAM supports are not tested yet
                if ((mPreset & 0xffff) == 0) { // generic
                    if (col == mEc) intv.end = Integer.parseInt(end != -1 ? s.substring(beg, end) : s.substring(beg));
                } else if ((mPreset & 0xffff) == 1) { // SAM
                    if (col == 6) { // CIGAR
                        int l = 0, i, j;
                        String cigar = s.substring(beg, end);
                        for (i = j = 0; i < cigar.length(); ++i) {
                            if (cigar.charAt(i) > '9') {
                                int op = cigar.charAt(i);
                                if (op == 'M' || op == 'D' || op == 'N') l += Integer.parseInt(cigar.substring(j, i));
                                j = i + 1;
                            }
                        }
                        intv.end = intv.beg + l;
                    }
                } else if ((mPreset & 0xffff) == 2) { // VCF
                    String alt;
                    alt = end >= 0 ? s.substring(beg, end) : s.substring(beg);
                    if (col == 4) { // REF
                        if (!alt.isEmpty()) intv.end = intv.beg + alt.length();
                    } else if (col == 8) { // INFO
                        int e_off = -1, i = alt.indexOf("END=");
                        if (i == 0) e_off = 4;
                        else if (i > 0) {
                            i = alt.indexOf(";END=");
                            if (i >= 0) e_off = i + 5;
                        }
                        if (e_off > 0) {
                            i = alt.indexOf(';', e_off);
                            intv.end = Integer.parseInt(i > e_off ? alt.substring(e_off, i) : alt.substring(e_off));
                        }
                    }
                }
            }
            if (end == -1) break;
            beg = end + 1;
        }
        return intv;
    }

    public interface Iterator {
        /** return null when there is no more data to read */
        public String next() throws IOException;
    }

    /** iterator returned instead of null when there is no more data */
    private static final Iterator EOF_ITERATOR = new Iterator() {
        @Override
        public String next() throws IOException {
            return null;
        }
    };

    /** default implementation of Iterator */
    private class IteratorImpl implements Iterator {
        private int i;
        // private int n_seeks;
        private int tid, beg, end;
        private final List<Chunk> off;
        private long curr_off;
        private boolean iseof;

        private IteratorImpl(final int _tid, final int _beg, final int _end, final List<Chunk> _off) {
            i = -1;
            // n_seeks = 0;
            curr_off = 0;
            iseof = false;
            off = _off;
            tid = _tid;
            beg = _beg;
            end = _end;
        }

        @Override
        public String next() throws IOException {
            if (iseof) return null;
            for (; ; ) {
                if (curr_off == 0 || !less64(curr_off, off.get(i).getChunkEnd())) { // then jump to the next chunk
                    if (i == off.size() - 1) break; // no more chunks
                    if (i >= 0) assert (curr_off == off.get(i).getChunkEnd()); // otherwise bug
                    if (i < 0
                            || off.get(i).getChunkEnd()
                                    != off.get(i + 1).getChunkStart()) { // not adjacent chunks; then seek
                        mFp.seek(off.get(i + 1).getChunkStart());
                        curr_off = mFp.getFilePointer();
                        // ++n_seeks;
                    }
                    ++i;
                }
                String s;
                if ((s = readLine(mFp, DEFAULT_BUFFER_SIZE)) != null) {
                    TIntv intv;
                    curr_off = mFp.getFilePointer();
                    if (s.isEmpty() || s.charAt(0) == mMeta) continue;
                    intv = getIntv(s);
                    if (intv.tid != tid || intv.beg >= end) break; // no need to proceed
                    else if (intv.end > beg && intv.beg < end) return s; // overlap; return
                } else break; // end of file
            }
            iseof = true;
            return null;
        }
    }

    /**
     * Get an iterator for an interval specified by the sequence id and begin and end coordinates
     * @param tid Sequence id, if non-existent returns EOF_ITERATOR
     * @param beg beginning of interval, genomic coords (0-based, closed-open)
     * @param end end of interval, genomic coords (0-based, closed-open)
     * @return an iterator over the specified interval
     */
    public Iterator query(final int tid, final int beg, final int end) {
        if (tid < 0 || beg < 0 || end <= 0 || tid >= this.mSeq.length) return EOF_ITERATOR;
        // The index takes 1-based inclusive coordinates, in which a 0-based exclusive end is unchanged.
        final List<Chunk> chunks = mIndex.getSpanOverlapping(tid, beg + 1, end).getChunks();
        if (chunks.isEmpty()) return EOF_ITERATOR;
        return new TabixReader.IteratorImpl(tid, beg, end, chunks);
    }

    /**
     *
     * @see #parseReg(String)
     * @param reg A region string of the form acceptable by {@link #parseReg(String)}
     * @return an iterator over the specified interval
     */
    public Iterator query(final String reg) {
        int[] x = parseReg(reg);
        return query(x[0], x[1], x[2]);
    }

    /**
     * Get an iterator for an interval specified by the sequence id and begin and end coordinates
     * @see #parseReg(String)
     * @param reg a chromosome
     * @param start start interval
     * @param end end interval
     * @return a tabix iterator over the specified interval
     */
    public Iterator query(final String reg, int start, int end) {
        int tid = this.chr2tid(reg);
        return query(tid, start, end);
    }

    // ADDED BY JTR
    @Override
    public void close() {
        if (mFp != null) {
            try {
                mFp.close();
            } catch (IOException e) {

            }
        }
    }

    @Override
    public String toString() {
        return "TabixReader: filename:" + getSource();
    }
}
