package htsjdk.variant.bcf2;

import htsjdk.index.FileBackedBinningIndex;
import htsjdk.index.ReferenceBinsSource;
import htsjdk.samtools.BAMFileSpan;
import htsjdk.samtools.Defaults;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.seekablestream.ISeekableStreamFactory;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.seekablestream.SeekableStreamFactory;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.CloseableTribbleIterator;
import htsjdk.tribble.FeatureCodecHeader;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.readers.PositionalBufferedStream;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * Reads a BGZF-compressed BCF file, with optional CSI-based region queries. Modelled on how htsjdk handles BAM + CSI:
 * the BGZF stream is opened as a {@link BlockCompressedInputStream}, and a query seeks it to virtual-offset chunks
 * from the index, reading records until the chunk end.
 *
 * <p>{@link AbstractFeatureReader#getFeatureReader(String, String, htsjdk.tribble.FeatureCodec, boolean, Function,
 * Function)} opens a BGZF-compressed BCF with this reader, with the CSI it finds or is given.
 *
 * <p>The reader holds the BCF open, and the CSI only in memory. Its iterators and queries all read through that one
 * stream, each from a place of its own: one seeks the stream back to its place only when another has moved it, so they
 * may be used side by side, on different threads, and one left unclosed holds nothing open. Closing the reader closes
 * the stream, and the iterators still open then return nothing more.
 */
public class BCFFileReader extends AbstractFeatureReader<VariantContext, PositionalBufferedStream> {
    private final String dataPath;
    private final long firstRecordOffset;
    private final ReferenceBinsSource index;
    private final BCFDictionary contigDictionary;

    /** The stream every iterator reads through; it is the lock under which one moves it and reads a record. */
    private final BlockCompressedInputStream stream;

    private volatile boolean readerClosed = false;

    /**
     * Opens a BGZF BCF with an optional CSI index, decoding it with a new {@link BCF2Codec}.
     *
     * @param bcfPath the BCF file
     * @param indexPath the CSI index, or null for sequential-only access
     * @throws IOException if either file cannot be opened or read
     */
    public BCFFileReader(final Path bcfPath, final Path indexPath) throws IOException {
        this(
                bcfPath.toUri().toString(),
                indexPath == null ? null : indexPath.toUri().toString(),
                new BCF2Codec(),
                null,
                null);
    }

    /**
     * Opens a BGZF BCF with an optional CSI index.
     *
     * @param featureResource the BCF: a path, a URI that a file system provider handles, or an http(s) or ftp URL
     * @param indexResource the CSI index over it, likewise, or null for sequential-only access
     * @param codec decodes the header and the records; a query looks contigs up in the contig dictionary it builds
     *     from the header, so it must not be shared with another reader
     * @param wrapper applied to the channel opened on the BCF, as in {@link AbstractFeatureReader#getFeatureReader(
     *     String, String, htsjdk.tribble.FeatureCodec, boolean, Function, Function)}; may be null
     * @param indexWrapper applied to the channel opened on the index; may be null
     * @throws IOException if either file cannot be opened or read
     * @throws TribbleException if the BCF is not a BCF, its header is cut short, or the index cannot be read as a CSI
     */
    public BCFFileReader(
            final String featureResource,
            final String indexResource,
            final BCF2Codec codec,
            final Function<SeekableByteChannel, SeekableByteChannel> wrapper,
            final Function<SeekableByteChannel, SeekableByteChannel> indexWrapper)
            throws IOException {
        super(featureResource, codec, wrapper, indexWrapper);
        dataPath = codec.getPathToDataFile(featureResource);
        stream = new BlockCompressedInputStream(openBuffered(dataPath, wrapper));
        try {
            header = readHeader(stream, codec, dataPath);
            firstRecordOffset = stream.getFilePointer();
            index = indexResource == null ? null : loadIndex(indexResource, indexWrapper, dataPath);
        } catch (final IOException | RuntimeException e) {
            CloserUtil.close(stream);
            throw e;
        }
        contigDictionary = codec.getContigDictionary();
    }

    /**
     * Reads the header from the start of {@code stream} by its stated length, so that the stream is left at the
     * first record, which a {@link PositionalBufferedStream}'s read-ahead would carry it past. The magic is checked
     * before the length is trusted, as any other BGZF file would give a length of gigabytes.
     */
    private static FeatureCodecHeader readHeader(
            final BlockCompressedInputStream stream, final BCF2Codec codec, final String dataPath) {
        try {
            // The magic ("BCF", major, minor) and the little-endian length of the header text that follows
            final byte[] prefix = new byte[BCFVersion.MAGIC_HEADER_START.length + 2 + Integer.BYTES];
            readFully(stream, prefix, 0, prefix.length);
            if (!Arrays.equals(
                    prefix,
                    0,
                    BCFVersion.MAGIC_HEADER_START.length,
                    BCFVersion.MAGIC_HEADER_START,
                    0,
                    BCFVersion.MAGIC_HEADER_START.length)) {
                throw new TribbleException.MalformedFeatureFile(
                        "Input stream does not contain a BCF encoded file; BCF magic header info not found", dataPath);
            }
            final int headerLen =
                    ByteBuffer.wrap(prefix).order(ByteOrder.LITTLE_ENDIAN).getInt(prefix.length - Integer.BYTES);
            if (headerLen < 0 || headerLen > Integer.MAX_VALUE - prefix.length) {
                throw new TribbleException.MalformedFeatureFile(
                        "Invalid BCF header length (l_text=" + Integer.toUnsignedString(headerLen)
                                + "): exceeds maximum array size",
                        dataPath);
            }

            // The codec parses the whole header, prefix included, from one buffer
            final byte[] headerBlob = new byte[prefix.length + headerLen];
            System.arraycopy(prefix, 0, headerBlob, 0, prefix.length);
            readFully(stream, headerBlob, prefix.length, headerLen);
            try (PositionalBufferedStream pbs = new PositionalBufferedStream(new ByteArrayInputStream(headerBlob))) {
                return codec.readHeader(pbs);
            }
        } catch (final IOException e) {
            throw new TribbleException.MalformedFeatureFile(
                    "Unable to read the BCF header: " + e.getMessage(), dataPath, e);
        }
    }

    /**
     * Reads the CSI index into memory and closes its file: queries consult it whole, so there is no reason to hold it
     * open.
     *
     * @throws TribbleException if it cannot be read as a CSI
     */
    private static ReferenceBinsSource loadIndex(
            final String indexResource,
            final Function<SeekableByteChannel, SeekableByteChannel> indexWrapper,
            final String dataPath)
            throws IOException {
        final byte[] bytes;
        try (SeekableStream in = openBuffered(indexResource, indexWrapper)) {
            bytes = in.readAllBytes();
        }
        try {
            return FileBackedBinningIndex.open(new SeekableMemoryStream(bytes, indexResource), true);
        } catch (final RuntimeException e) {
            throw new TribbleException(
                    "Unable to read " + indexResource + " as the CSI index of " + dataPath + ": " + e.getMessage(), e);
        }
    }

    /**
     * Opens {@code resource} through a buffer of {@code samjdk.buffer_size} bytes: BGZF reads a block's header and
     * its body separately, and on a network file system each read is a round trip.
     */
    private static SeekableStream openBuffered(
            final String resource, final Function<SeekableByteChannel, SeekableByteChannel> wrapper)
            throws IOException {
        final ISeekableStreamFactory factory = SeekableStreamFactory.getInstance();
        return factory.getBufferedStream(factory.getStreamFor(resource, wrapper), Defaults.BUFFER_SIZE);
    }

    @Override
    public VCFHeader getHeader() {
        return (VCFHeader) header.getHeaderValue();
    }

    @Override
    public List<String> getSequenceNames() {
        final List<String> names = new ArrayList<>();
        for (final SAMSequenceRecord seq : getHeader().getSequenceDictionary().getSequences()) {
            names.add(seq.getSequenceName());
        }
        return names;
    }

    /** @return true if the reader was opened with a CSI index, and so can be queried */
    @Override
    public boolean hasIndex() {
        return index != null;
    }

    /**
     * @return every record in the file
     * @throws IllegalStateException if the reader has been closed
     */
    @Override
    public CloseableTribbleIterator<VariantContext> iterator() throws IOException {
        checkOpen();
        return new SequentialIterator();
    }

    /**
     * @return the records overlapping the region
     * @throws TribbleException if the reader has no index
     * @throws IllegalStateException if the reader has been closed
     */
    @Override
    public CloseableTribbleIterator<VariantContext> query(final String chr, final int start, final int end)
            throws IOException {
        checkOpen();
        if (!hasIndex()) {
            throw new TribbleException(
                    "Cannot query without a CSI index; open the BCF with an index or use iterator() for sequential access");
        }
        final int refIdx = contigDictionary.getIndexOrDefault(chr, -1);
        // an empty or inverted interval overlaps nothing, as with TribbleIndexedFeatureReader and htslib
        if (refIdx < 0 || start > end) {
            return emptyIterator();
        }
        final BAMFileSpan span = (BAMFileSpan) index.getSpanOverlapping(refIdx, start, end);
        final long[] coordinates = span.toCoordinateArray();
        if (coordinates == null || coordinates.length == 0) {
            return emptyIterator();
        }
        return new QueryIterator(chr, start, end, coordinates);
    }

    /** Closes the BCF and the index; iterators still open then return nothing more. */
    @Override
    public void close() throws IOException {
        readerClosed = true;
        try {
            synchronized (stream) {
                stream.close();
            }
        } finally {
            if (index != null) {
                index.close();
            }
        }
    }

    private void checkOpen() {
        if (readerClosed) {
            throw new IllegalStateException("The reader of " + dataPath + " has been closed");
        }
    }

    /**
     * Reads the bytes of the record at {@code iterator}'s place, seeking the stream there first if another iterator
     * has moved it, and moves the iterator's place past the record.
     *
     * @return the record's bytes, or null at the end of the file or once the reader is closed
     */
    private byte[] readRecordBytes(final RecordIterator iterator) throws IOException {
        synchronized (stream) {
            if (readerClosed) {
                return null;
            }
            if (stream.getFilePointer() != iterator.position) {
                stream.seek(iterator.position);
            }
            // The lengths of the site and genotype blocks
            final byte[] sizeBytes = new byte[8];
            final int firstByte = stream.read();
            if (firstByte == -1) {
                return null;
            }
            sizeBytes[0] = (byte) firstByte;
            readFully(stream, sizeBytes, 1, 7);
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

            // The length as a long, so that an overflow is caught before the cast
            final long recordLengthLong = 8L + sitesBlockSize + genotypeBlockSize;
            if (recordLengthLong > Integer.MAX_VALUE - 8) {
                throw new TribbleException("Invalid BCF record: combined size l_shared=" + sitesBlockSize
                        + " + l_indiv=" + genotypeBlockSize + " exceeds maximum array size");
            }
            final byte[] recordBytes = new byte[(int) recordLengthLong];
            System.arraycopy(sizeBytes, 0, recordBytes, 0, 8);
            readFully(stream, recordBytes, 8, sitesBlockSize + genotypeBlockSize);
            iterator.position = stream.getFilePointer();
            return recordBytes;
        }
    }

    /** Decodes a record from its bytes. */
    private VariantContext decode(final byte[] recordBytes) throws IOException {
        final PositionalBufferedStream pbs =
                new PositionalBufferedStream(new ByteArrayInputStream(recordBytes), recordBytes.length);
        final VariantContext vc = codec.decode(pbs);
        pbs.close();
        return vc;
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

    /** Hands out records read from its own place in the reader's stream. */
    private abstract class RecordIterator implements CloseableTribbleIterator<VariantContext> {
        /** Where the next record this iterator reads starts: a BGZF virtual offset. */
        long position;

        private VariantContext next;
        private volatile boolean closed = false;

        RecordIterator(final long startOffset) {
            position = startOffset;
        }

        /** @return the next record to hand out, or null if there are no more */
        abstract VariantContext readNext() throws IOException;

        /** Reads the first record; called last by a subclass's constructor. */
        final void readFirst() {
            advance();
        }

        private void advance() {
            try {
                next = readNext();
            } catch (final IOException e) {
                throw new RuntimeIOException("Error reading BCF record from " + dataPath, e);
            }
        }

        /** @return the next record in the file from this iterator's place, or null at the end */
        final VariantContext readRecord() throws IOException {
            final byte[] recordBytes = readRecordBytes(this);
            return recordBytes == null ? null : decode(recordBytes);
        }

        @Override
        public boolean hasNext() {
            return next != null && !closed && !readerClosed;
        }

        @Override
        public VariantContext next() {
            if (!hasNext()) throw new NoSuchElementException();
            final VariantContext result = next;
            advance();
            return result;
        }

        @Override
        public Iterator<VariantContext> iterator() {
            return this;
        }

        @Override
        public void close() {
            closed = true;
            next = null;
        }
    }

    /** Iterates all records from firstRecordOffset to EOF. */
    private final class SequentialIterator extends RecordIterator {
        SequentialIterator() {
            super(firstRecordOffset);
            readFirst();
        }

        @Override
        VariantContext readNext() throws IOException {
            return readRecord();
        }
    }

    /** Iterates records from the chunks returned by the index, filtering by overlap. */
    private final class QueryIterator extends RecordIterator {
        private final String contig;
        private final int queryStart;
        private final int queryEnd;
        // The start and end virtual offsets of each chunk in turn
        private final long[] coordinates;
        private int coordIndex;
        private long chunkEnd;

        QueryIterator(final String contig, final int start, final int end, final long[] coordinates) {
            super(coordinates[0]);
            this.contig = contig;
            this.queryStart = start;
            this.queryEnd = end;
            this.coordinates = coordinates;
            this.chunkEnd = coordinates[1];
            this.coordIndex = 2;
            readFirst();
        }

        @Override
        VariantContext readNext() throws IOException {
            while (true) {
                // On to the next chunk once past the end of this one
                while (position >= chunkEnd) {
                    if (coordIndex >= coordinates.length) {
                        return null;
                    }
                    position = coordinates[coordIndex++];
                    chunkEnd = coordinates[coordIndex++];
                }

                final VariantContext vc = readRecord();
                if (vc == null) {
                    return null;
                }

                if (vc.getContig().equals(contig)) {
                    // Coordinate-sorted: stop when past the query region
                    if (vc.getStart() > queryEnd) {
                        return null;
                    }
                    // Filter: the record must overlap the query region
                    if (vc.getEnd() >= queryStart) {
                        return vc;
                    }
                }
            }
        }
    }
}
