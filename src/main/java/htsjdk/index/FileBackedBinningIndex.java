package htsjdk.index;

import htsjdk.samtools.BAMFileSpan;
import htsjdk.samtools.SAMFormatException;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import htsjdk.samtools.seekablestream.SeekablePathStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.RuntimeEOFException;
import htsjdk.samtools.util.RuntimeIOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.SoftReference;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.Supplier;

/**
 * A BAI or CSI index read from its file a reference at a time, so that a reader interested in one region of one
 * reference neither parses nor holds the rest of the index.
 *
 * <p>Neither format says where in the file a reference starts, so a reference is found by walking over those
 * before it, reading only the counts needed to skip them. Where each reference starts is remembered, so none is
 * walked over twice and a reference already passed is reached by a seek.
 *
 * <p>Parsed references are held through {@link SoftReference}s and never evicted here. With memory to spare an
 * index read this way ends up wholly in memory; when memory is short the collector reclaims the references least
 * recently used, across every index in the JVM, and one that is asked for again is read again from the file. An
 * index can also be read through once on opening ({@code prefill}), in a single pass over the file, which suits a
 * file that is slow to seek in; its references are reclaimable all the same.
 *
 * <p>Not thread-safe.
 */
public final class FileBackedBinningIndex implements ReferenceBinsSource {
    private static final byte[] BAI_MAGIC = {'B', 'A', 'I', 1};
    private static final VarHandle INT = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle LONG = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    private final Supplier<Source> sourceOpener;
    // The caller's stream, for an index opened from one; closed with the index whether or not it is being read from.
    private final SeekableStream callersStream;
    private Source source;
    private boolean closed;

    private final boolean csiLayout;
    private final int minShift;
    private final int depth;
    private final byte[] aux;
    private final int referenceCount;

    // Where each reference starts in the file, in the source's own terms (a byte offset, or a virtual offset for a
    // BGZF-compressed index), and its length in uncompressed bytes. Known for the first `located` references;
    // referenceOffsets[located] is where the next one starts.
    private final long[] referenceOffsets;
    private final int[] referenceLengths;
    private int located;

    private final SoftReference<ReferenceBins>[] cache;
    private long noCoordinateCount;
    private boolean noCoordinateCountRead;

    /**
     * Opens the index at a path.
     *
     * @param prefill whether to read the whole index now, in one pass
     */
    public static FileBackedBinningIndex open(final Path path, final boolean prefill) {
        final Supplier<Source> opener = () -> {
            try {
                return new StreamSource(new SeekablePathStream(path));
            } catch (final IOException e) {
                throw new RuntimeIOException("Error opening index " + path, e);
            }
        };
        if (!prefill) {
            return new FileBackedBinningIndex(opener, null, null);
        }
        try {
            return new FileBackedBinningIndex(opener, null, Files.readAllBytes(path));
        } catch (final IOException e) {
            throw new RuntimeIOException("Error reading index " + path, e);
        }
    }

    /**
     * Opens the index in a stream, which belongs to the index from then on and is closed with it.
     *
     * @param prefill whether to read the whole index now, in one pass
     */
    public static FileBackedBinningIndex open(final SeekableStream stream, final boolean prefill) {
        final Supplier<Source> opener = () -> new StreamSource(stream);
        if (!prefill) {
            return new FileBackedBinningIndex(opener, stream, null);
        }
        try {
            stream.seek(0);
            final byte[] contents = readToEnd(stream);
            stream.seek(0);
            return new FileBackedBinningIndex(opener, stream, contents);
        } catch (final IOException e) {
            throw new RuntimeIOException("Error reading index " + stream.getSource(), e);
        }
    }

    /** Reads the rest of a stream without ever asking it for zero bytes, which not every stream answers well. */
    private static byte[] readToEnd(final SeekableStream stream) throws IOException {
        final ByteArrayOutputStream contents = new ByteArrayOutputStream();
        final byte[] chunk = new byte[1 << 16];
        for (int n = stream.read(chunk, 0, chunk.length); n > 0; n = stream.read(chunk, 0, chunk.length)) {
            contents.write(chunk, 0, n);
        }
        return contents.toByteArray();
    }

    /**
     * @param sourceOpener opens the file for the reads that fetch a reference
     * @param callersStream the stream the index was opened from, or null if it was opened from a path
     * @param contents the file's bytes if the whole index is to be read now, from them; otherwise null
     */
    @SuppressWarnings("unchecked")
    private FileBackedBinningIndex(
            final Supplier<Source> sourceOpener, final SeekableStream callersStream, final byte[] contents) {
        this.sourceOpener = sourceOpener;
        this.callersStream = callersStream;
        // While prefilling, the file is the copy in memory; the real one is not opened unless a reference that
        // the collector has reclaimed is asked for again.
        if (contents != null) {
            source = new StreamSource(new SeekableMemoryStream(contents, "index"));
        }
        try {
            final Source in = source();
            in.seek(0);
            final byte[] magic = new byte[4];
            in.readBytes(magic);
            if (Arrays.equals(magic, BAI_MAGIC)) {
                csiLayout = false;
                minShift = BinningIndex.BAI_MIN_SHIFT;
                depth = BinningIndex.BAI_DEPTH;
                aux = new byte[0];
            } else if (Arrays.equals(magic, BinningIndex.CSI_MAGIC)) {
                csiLayout = true;
                minShift = in.readInt();
                depth = in.readInt();
                BinningIndex.validateGeometry(minShift, depth);
                aux = new byte[count(in.readInt())];
                in.readBytes(aux);
            } else {
                throw new SAMFormatException("Not a BAI or CSI index: magic number is " + Arrays.toString(magic));
            }
            referenceCount = count(in.readInt());
            referenceOffsets = new long[referenceCount + 1];
            referenceLengths = new int[referenceCount];
            referenceOffsets[0] = in.position();
            cache = new SoftReference[referenceCount];

            if (contents != null) {
                // Held strongly until all are read, or the collector could take the first before the last is in.
                final List<ReferenceBins> all = new ArrayList<>(referenceCount);
                for (int i = 0; i < referenceCount; i++) {
                    all.add(getReference(i));
                }
                getNoCoordinateCount();
                source.close();
                source = null;
            }
        } catch (final RuntimeException e) {
            // The caller gets no index to close, so a file opened here is closed here; a stream they supplied
            // stays theirs.
            if (callersStream == null && source != null) {
                source.close();
            }
            throw e;
        }
    }

    private Source source() {
        if (source == null) {
            source = sourceOpener.get();
        }
        return source;
    }

    @Override
    public int getMinShift() {
        return minShift;
    }

    @Override
    public int getDepth() {
        return depth;
    }

    @Override
    public int getReferenceCount() {
        return referenceCount;
    }

    /** @return the format-specific block of a CSI file; empty for a BAI, which has none */
    public byte[] getAux() {
        return aux.clone();
    }

    /** @return whether the file is a CSI rather than a BAI */
    public boolean isCsi() {
        return csiLayout;
    }

    @Override
    public ReferenceBins getReference(final int referenceIndex) {
        final SoftReference<ReferenceBins> held = cache[referenceIndex];
        final ReferenceBins cached = held == null ? null : held.get();
        if (cached != null) {
            return cached;
        }
        locate(referenceIndex);
        final Source in = source();
        in.seek(referenceOffsets[referenceIndex]);
        final byte[] bytes = new byte[referenceLengths[referenceIndex]];
        in.readBytes(bytes);
        final ReferenceBins reference = BinningIndex.readReference(
                new BinaryCodec(new ByteArrayInputStream(bytes)), referenceIndex, depth, csiLayout);
        cache[referenceIndex] = new SoftReference<>(reference);
        return reference;
    }

    /** A count read from the file. A negative one would send the walk over the file backwards. */
    private static int count(final int value) {
        if (value < 0) {
            throw new SAMFormatException("Malformed index: a count of " + value);
        }
        return value;
    }

    /** Walks over references until the start and length of the given one are known. */
    private void locate(final int referenceIndex) {
        if (referenceIndex < located) {
            return;
        }
        final Source in = source();
        in.seek(referenceOffsets[located]);
        while (located <= referenceIndex) {
            long length = 4;
            final int binCount = count(in.readInt());
            for (int i = 0; i < binCount; i++) {
                in.skip(csiLayout ? 12 : 4); // bin number, and in CSI its loffset
                final int chunkBytes = Math.multiplyExact(16, count(in.readInt()));
                in.skip(chunkBytes);
                length += (csiLayout ? 16 : 8) + chunkBytes;
            }
            if (!csiLayout) {
                final int linearIndexBytes = Math.multiplyExact(8, count(in.readInt()));
                in.skip(linearIndexBytes);
                length += 4 + linearIndexBytes;
            }
            referenceLengths[located] = Math.toIntExact(length);
            referenceOffsets[located + 1] = in.position();
            located++;
        }
    }

    @Override
    public OptionalLong getNoCoordinateCount() {
        if (!noCoordinateCountRead) {
            if (referenceCount > 0) {
                locate(referenceCount - 1);
            }
            final Source in = source();
            in.seek(referenceOffsets[referenceCount]);
            try {
                noCoordinateCount = in.readLong();
            } catch (final RuntimeEOFException e) {
                // The count is optional, and a file cut short within it is taken not to have it, as htslib takes it.
                // Any other failure is a failure to read a count that may well be there.
                noCoordinateCount = -1;
            }
            noCoordinateCountRead = true;
        }
        return noCoordinateCount < 0 ? OptionalLong.empty() : OptionalLong.of(noCoordinateCount);
    }

    @Override
    public BAMFileSpan getSpanOverlapping(final int referenceIndex, final int start, final int end) {
        if (referenceIndex < 0 || referenceIndex >= referenceCount) {
            return BinningIndex.spanOverlapping(ReferenceBins.EMPTY, minShift, depth, start, end);
        }
        return BinningIndex.spanOverlapping(getReference(referenceIndex), minShift, depth, start, end);
    }

    @Override
    public BinningIndex loadAll() {
        final List<ReferenceBins> references = new ArrayList<>(referenceCount);
        for (int i = 0; i < referenceCount; i++) {
            references.add(getReference(i));
        }
        return new BinningIndex(
                minShift, depth, references, getNoCoordinateCount().orElse(-1));
    }

    /** Forgets every parsed reference, as the collector may at any time. */
    void clearCache() {
        Arrays.fill(cache, null);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (source != null) {
            // Closes the stream beneath it, the caller's included.
            source.close();
            source = null;
        } else if (callersStream != null) {
            // A prefilled index has no source open, but still owns the stream it was given.
            try {
                callersStream.close();
            } catch (final IOException e) {
                throw new RuntimeIOException("Error closing index " + callersStream.getSource(), e);
            }
        }
        closed = true;
    }

    /**
     * The few kinds of read the index makes of its file. A position is a byte offset, or for a BGZF-compressed
     * file a virtual offset; either way it is only ever one that {@link #position()} returned.
     */
    private interface Source {
        int readInt();

        long readLong();

        void readBytes(byte[] bytes);

        void skip(int count);

        void seek(long position);

        long position();

        void close();
    }

    /**
     * Reads a plain stream through a window of its own, or a BGZF one through decompression. Finding a reference
     * means reading a count and skipping a few dozen bytes, hundreds of thousands of times over, so for a plain
     * stream a skip is only arithmetic and a count is decoded straight out of the window.
     */
    private static final class StreamSource implements Source {
        private static final int WINDOW_BYTES = 1 << 18;

        private final SeekableStream stream;
        private final BlockCompressedInputStream bgzf;
        private final BinaryCodec bgzfCodec;

        // For a plain stream: the bytes of the file from windowStart, and where in the file the next read is.
        private final byte[] window;
        private long windowStart;
        private int windowLength;
        private long position;

        StreamSource(final SeekableStream stream) {
            this.stream = stream;
            try {
                stream.seek(0);
                final boolean gzipped = stream.read() == 0x1f && stream.read() == 0x8b;
                stream.seek(0);
                if (gzipped) {
                    bgzf = new BlockCompressedInputStream(stream);
                    bgzfCodec = new BinaryCodec(bgzf);
                    window = null;
                } else {
                    bgzf = null;
                    bgzfCodec = null;
                    window = new byte[WINDOW_BYTES];
                }
            } catch (final IOException e) {
                throw new RuntimeIOException("Error reading index " + stream.getSource(), e);
            }
        }

        /** Makes the next {@code count} bytes at the position available in the window; returns their offset in it. */
        private int inWindow(final int count) {
            if (position < windowStart || position + count > windowStart + windowLength) {
                try {
                    stream.seek(position);
                    windowStart = position;
                    windowLength = 0;
                    while (windowLength < window.length) {
                        final int n = stream.read(window, windowLength, window.length - windowLength);
                        if (n <= 0) break;
                        windowLength += n;
                    }
                } catch (final IOException e) {
                    throw new RuntimeIOException(e);
                }
                if (windowLength < count) {
                    throw new RuntimeEOFException("Index ends before the " + count + " bytes at " + position);
                }
            }
            final int offset = (int) (position - windowStart);
            position += count;
            return offset;
        }

        @Override
        public int readInt() {
            return bgzf != null ? bgzfCodec.readInt() : (int) INT.get(window, inWindow(4));
        }

        @Override
        public long readLong() {
            return bgzf != null ? bgzfCodec.readLong() : (long) LONG.get(window, inWindow(8));
        }

        @Override
        public void readBytes(final byte[] bytes) {
            if (bgzf != null) {
                bgzfCodec.readBytes(bytes);
            } else if (bytes.length <= window.length) {
                System.arraycopy(window, inWindow(bytes.length), bytes, 0, bytes.length);
            } else {
                try {
                    stream.seek(position);
                    stream.readFully(bytes);
                    position += bytes.length;
                } catch (final IOException e) {
                    throw new RuntimeIOException(e);
                }
            }
        }

        @Override
        public void skip(final int count) {
            if (bgzf == null) {
                position += count;
                return;
            }
            try {
                long remaining = count;
                while (remaining > 0) {
                    final long skipped = bgzf.skip(remaining);
                    if (skipped <= 0) {
                        throw new RuntimeEOFException("Index ends before the " + count + " bytes to be skipped");
                    }
                    remaining -= skipped;
                }
            } catch (final IOException e) {
                throw new RuntimeIOException(e);
            }
        }

        @Override
        public void seek(final long newPosition) {
            if (bgzf == null) {
                position = newPosition;
                return;
            }
            try {
                bgzf.seek(newPosition);
            } catch (final IOException e) {
                throw new RuntimeIOException(e);
            }
        }

        @Override
        public long position() {
            return bgzf == null ? position : bgzf.getFilePointer();
        }

        @Override
        public void close() {
            try {
                if (bgzf != null) {
                    bgzf.close();
                } else {
                    stream.close();
                }
            } catch (final IOException e) {
                throw new RuntimeIOException(e);
            }
        }
    }
}
