package htsjdk.variant.bcf2;

import htsjdk.index.FileBackedBinningIndex;
import htsjdk.index.ReferenceBinsSource;
import htsjdk.samtools.BAMFileSpan;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.tribble.CloseableTribbleIterator;
import htsjdk.tribble.FeatureCodecHeader;
import htsjdk.tribble.FeatureReader;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.readers.PositionalBufferedStream;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Reads a BGZF-compressed BCF file, with optional CSI-based region queries. Modelled on how htsjdk handles BAM + CSI:
 * the BGZF stream is opened as a {@link BlockCompressedInputStream}, and a query seeks it to virtual-offset chunks
 * from the index, reading records until the chunk end.
 *
 * <p>Not thread-safe: one reader per thread.
 */
public class BCFFileReader implements FeatureReader<VariantContext> {
    private final BlockCompressedInputStream bgzfStream;
    private final BCF2Codec codec;
    private final VCFHeader header;
    private final long firstRecordOffset;
    private final ReferenceBinsSource index;
    private final SAMSequenceDictionary dictionary;
    private final BCFDictionary contigDictionary;

    /**
     * Opens a BGZF BCF with an optional CSI index.
     *
     * @param bcfPath the BCF file
     * @param indexPath the CSI index, or null for sequential-only access
     */
    public BCFFileReader(final Path bcfPath, final Path indexPath) {
        BlockCompressedInputStream stream;
        try {
            stream = new BlockCompressedInputStream(bcfPath);
        } catch (final IOException e) {
            throw new RuntimeIOException("Error opening BCF file " + bcfPath, e);
        }

        codec = new BCF2Codec();

        // Read the header from the BGZF stream by reading the exact header bytes, so that the BGZF
        // file pointer sits at the first record after this call.
        try {
            // The magic ("BCF", major, minor) and the little-endian length of the header text that follows
            final byte[] prefix = new byte[BCFVersion.MAGIC_HEADER_START.length + 2 + Integer.BYTES];
            readFully(stream, prefix);
            final int headerLen =
                    ByteBuffer.wrap(prefix).order(ByteOrder.LITTLE_ENDIAN).getInt(prefix.length - Integer.BYTES);
            if (headerLen < 0 || headerLen > Integer.MAX_VALUE - prefix.length) {
                throw new TribbleException("Invalid BCF header length (l_text=" + Integer.toUnsignedString(headerLen)
                        + "): exceeds maximum array size");
            }

            // The codec parses the whole header, prefix included, from one buffer
            final byte[] headerBlob = new byte[prefix.length + headerLen];
            System.arraycopy(prefix, 0, headerBlob, 0, prefix.length);
            readFully(stream, headerBlob, prefix.length, headerLen);

            // The BGZF file pointer now sits at the first record
            firstRecordOffset = stream.getFilePointer();

            final PositionalBufferedStream pbs = new PositionalBufferedStream(new ByteArrayInputStream(headerBlob));
            final FeatureCodecHeader fch = codec.readHeader(pbs);
            pbs.close();
            header = (VCFHeader) fch.getHeaderValue();
        } catch (final IOException e) {
            try {
                stream.close();
            } catch (final IOException ignored) {
            }
            throw new RuntimeIOException("Error reading BCF header", e);
        } catch (final RuntimeException e) {
            try {
                stream.close();
            } catch (final IOException ignored) {
            }
            throw e;
        }

        dictionary = header.getSequenceDictionary();
        contigDictionary = codec.getContigDictionary();

        if (indexPath != null) {
            try {
                index = FileBackedBinningIndex.open(indexPath, true);
            } catch (final RuntimeException e) {
                try {
                    stream.close();
                } catch (final IOException ignored) {
                }
                throw e;
            }
        } else {
            index = null;
        }

        bgzfStream = stream;
    }

    @Override
    public VCFHeader getHeader() {
        return header;
    }

    @Override
    public List<String> getSequenceNames() {
        final List<String> names = new ArrayList<>();
        for (final SAMSequenceRecord seq : dictionary.getSequences()) {
            names.add(seq.getSequenceName());
        }
        return names;
    }

    @Override
    public boolean isQueryable() {
        return index != null;
    }

    @Override
    public CloseableTribbleIterator<VariantContext> iterator() throws IOException {
        bgzfStream.seek(firstRecordOffset);
        return new SequentialIterator();
    }

    @Override
    public CloseableTribbleIterator<VariantContext> query(final String chr, final int start, final int end)
            throws IOException {
        if (!isQueryable()) {
            throw new TribbleException(
                    "Cannot query without a CSI index; open the BCF with an index or use iterator() for sequential access");
        }
        final int refIdx = contigDictionary.getIndexOrDefault(chr, -1);
        if (refIdx < 0) {
            return emptyIterator();
        }
        final BAMFileSpan span = (BAMFileSpan) index.getSpanOverlapping(refIdx, start, end);
        final long[] coordinates = span.toCoordinateArray();
        if (coordinates == null || coordinates.length == 0) {
            return emptyIterator();
        }
        return new QueryIterator(chr, start, end, coordinates);
    }

    @Override
    public void close() throws IOException {
        try {
            if (index != null) {
                index.close();
            }
        } finally {
            bgzfStream.close();
        }
    }

    /** Decodes the next record from the BGZF stream. Returns null at EOF. */
    private VariantContext decodeNextRecord() throws IOException {
        // Read the two block sizes (8 bytes total)
        final byte[] sizeBytes = new byte[8];
        final int firstByte = bgzfStream.read();
        if (firstByte == -1) {
            return null;
        }
        sizeBytes[0] = (byte) firstByte;
        readFully(bgzfStream, sizeBytes, 1, 7);
        final ByteBuffer sizes = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN);
        final int sitesBlockSize = sizes.getInt(0);
        final int genotypeBlockSize = sizes.getInt(Integer.BYTES);
        if (sitesBlockSize < 0) {
            throw new TribbleException("Invalid BCF record: l_shared=" + Integer.toUnsignedString(sitesBlockSize)
                    + " exceeds maximum array size");
        }
        if (genotypeBlockSize < 0) {
            throw new TribbleException("Invalid BCF record: l_indiv=" + Integer.toUnsignedString(genotypeBlockSize)
                    + " exceeds maximum array size");
        }

        // Read the two blocks; compute the length as a long to detect overflow before casting
        final long recordLengthLong = 8L + sitesBlockSize + genotypeBlockSize;
        if (recordLengthLong > Integer.MAX_VALUE - 8) {
            throw new TribbleException("Invalid BCF record: combined size l_shared=" + sitesBlockSize + " + l_indiv="
                    + genotypeBlockSize + " exceeds maximum array size");
        }
        final int recordLength = (int) recordLengthLong;
        final byte[] recordBytes = new byte[recordLength];
        System.arraycopy(sizeBytes, 0, recordBytes, 0, 8);
        readFully(bgzfStream, recordBytes, 8, sitesBlockSize + genotypeBlockSize);

        // Wrap in a PositionalBufferedStream sized to the record and decode
        final PositionalBufferedStream pbs =
                new PositionalBufferedStream(new ByteArrayInputStream(recordBytes), recordLength);
        final VariantContext vc = codec.decode(pbs);
        pbs.close();
        return vc;
    }

    private static void readFully(final BlockCompressedInputStream in, final byte[] buf) throws IOException {
        readFully(in, buf, 0, buf.length);
    }

    private static void readFully(final BlockCompressedInputStream in, final byte[] buf, int off, int len)
            throws IOException {
        while (len > 0) {
            final int n = in.read(buf, off, len);
            if (n <= 0) {
                throw new IOException("Unexpected end of BGZF stream");
            }
            off += n;
            len -= n;
        }
    }

    private static CloseableTribbleIterator<VariantContext> emptyIterator() {
        return new CloseableTribbleIterator<>() {
            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public VariantContext next() {
                throw new NoSuchElementException();
            }

            @Override
            public Iterator<VariantContext> iterator() {
                return this;
            }

            @Override
            public void close() {}
        };
    }

    /** Iterates all records from firstRecordOffset to EOF. */
    private class SequentialIterator implements CloseableTribbleIterator<VariantContext> {
        private VariantContext next;

        SequentialIterator() {
            advance();
        }

        private void advance() {
            try {
                next = decodeNextRecord();
            } catch (final IOException e) {
                throw new RuntimeIOException("Error reading BCF record", e);
            }
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public VariantContext next() {
            if (next == null) throw new NoSuchElementException();
            final VariantContext result = next;
            advance();
            return result;
        }

        @Override
        public Iterator<VariantContext> iterator() {
            return this;
        }

        @Override
        public void close() {}
    }

    /** Iterates records from the chunks returned by the index, filtering by overlap. */
    private class QueryIterator implements CloseableTribbleIterator<VariantContext> {
        private final String contig;
        private final int queryStart;
        private final int queryEnd;
        private final long[] coordinates;
        private int coordIndex;
        private long chunkEnd;
        private VariantContext next;
        private boolean done;

        QueryIterator(final String contig, final int start, final int end, final long[] coordinates) {
            this.contig = contig;
            this.queryStart = start;
            this.queryEnd = end;
            this.coordinates = coordinates;
            this.coordIndex = 0;
            this.chunkEnd = -1;
            advance();
        }

        private void advance() {
            try {
                while (true) {
                    // Advance to next chunk if we've passed the end of the current one
                    while (bgzfStream.getFilePointer() >= chunkEnd) {
                        if (coordIndex >= coordinates.length) {
                            next = null;
                            done = true;
                            return;
                        }
                        final long start = coordinates[coordIndex++];
                        chunkEnd = coordinates[coordIndex++];
                        bgzfStream.seek(start);
                    }

                    final VariantContext vc = decodeNextRecord();
                    if (vc == null) {
                        next = null;
                        done = true;
                        return;
                    }

                    // Coordinate-sorted: stop when past the query region
                    if (vc.getContig().equals(contig) && vc.getStart() > queryEnd) {
                        next = null;
                        done = true;
                        return;
                    }

                    // Filter: the record must overlap the query region on the right contig
                    if (vc.getContig().equals(contig) && vc.getStart() <= queryEnd && vc.getEnd() >= queryStart) {
                        next = vc;
                        return;
                    }
                }
            } catch (final IOException e) {
                throw new RuntimeIOException("Error during BCF query", e);
            }
        }

        @Override
        public boolean hasNext() {
            return !done && next != null;
        }

        @Override
        public VariantContext next() {
            if (done || next == null) throw new NoSuchElementException();
            final VariantContext result = next;
            advance();
            return result;
        }

        @Override
        public Iterator<VariantContext> iterator() {
            return this;
        }

        @Override
        public void close() {}
    }
}
