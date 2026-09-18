package htsjdk.samtools;

import htsjdk.index.BinningIndex;
import htsjdk.index.FileBackedBinningIndex;
import htsjdk.index.ReferenceBins;
import htsjdk.index.ReferenceBinsSource;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.RuntimeIOException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The index of a BAM, or the BAI of a CRAM, in the terms {@link BAMIndex} and {@link BrowseableBAMIndex} ask for,
 * over a {@link ReferenceBinsSource}. It serves BAI and CSI alike: the difference between them is a binning scheme
 * and where a query's lower bound comes from, both of which the source knows.
 */
final class BinningBAMIndex implements BrowseableBAMIndex {
    // Below this an index is read whole under IndexLoading.AUTO: it costs under a millisecond, and saves the seeks.
    private static final long SMALL_INDEX_BYTES = 1 << 20;

    private final ReferenceBinsSource source;

    BinningBAMIndex(final ReferenceBinsSource source) {
        this.source = source;
    }

    static BinningBAMIndex open(final Path path, final IndexLoading loading) {
        return new BinningBAMIndex(FileBackedBinningIndex.open(path, readsWholeIndex(path, loading)));
    }

    /** A stream is read whole unless {@link IndexLoading#LAZY} is asked for, since it may be slow to seek in. */
    static BinningBAMIndex open(final SeekableStream stream, final IndexLoading loading) {
        return new BinningBAMIndex(FileBackedBinningIndex.open(stream, loading != IndexLoading.LAZY));
    }

    private static boolean readsWholeIndex(final Path path, final IndexLoading loading) {
        if (loading != IndexLoading.AUTO) {
            return loading == IndexLoading.EAGER;
        }
        if (!path.getFileSystem().equals(FileSystems.getDefault())) {
            return true;
        }
        try {
            return Files.size(path) < SMALL_INDEX_BYTES;
        } catch (final IOException e) {
            throw new RuntimeIOException("Error reading index " + path, e);
        }
    }

    ReferenceBinsSource getSource() {
        return source;
    }

    int getNumberOfReferences() {
        return source.getReferenceCount();
    }

    /** @return the count of records without a position, or null if the index does not record it */
    Long getNoCoordinateCount() {
        return source.getNoCoordinateCount().isPresent()
                ? source.getNoCoordinateCount().getAsLong()
                : null;
    }

    @Override
    public BAMFileSpan getSpanOverlapping(final int referenceIndex, final int startPos, final int endPos) {
        return (BAMFileSpan) source.getSpanOverlapping(referenceIndex, startPos, endPos);
    }

    /**
     * The offset of some record at or before the last placed one, from which a reader looks for the reads without
     * a position that follow them: the last entry of the last linear index, or in a CSI, which has none, the
     * largest {@code loffset}.
     */
    @Override
    public long getStartOfLastLinearBin() {
        // Offsets grow with the reference in a coordinate-sorted file, so the last reference with any has the largest.
        for (int i = source.getReferenceCount() - 1; i >= 0; i--) {
            final ReferenceBins reference = source.getReference(i);
            final long[] linearIndex = reference.getLinearIndex();
            if (linearIndex.length > 0) {
                return linearIndex[linearIndex.length - 1];
            }
            if (reference.getBinCount() > 0) {
                long largest = reference.getLoffset(0);
                for (int bin = 1; bin < reference.getBinCount(); bin++) {
                    if (Long.compareUnsigned(reference.getLoffset(bin), largest) > 0) {
                        largest = reference.getLoffset(bin);
                    }
                }
                return largest;
            }
        }
        return -1;
    }

    /** @return the reference's counts and offsets, or null if the index has no such reference */
    @Override
    public BAMIndexMetaData getMetaData(final int reference) {
        if (!hasReference(reference)) {
            return null;
        }
        final List<Chunk> chunks = new ArrayList<>(2);
        source.getReference(reference).getMetadata().ifPresent(metadata -> {
            chunks.add(new Chunk(metadata.firstOffset(), metadata.lastOffset()));
            chunks.add(new Chunk(metadata.mappedCount(), metadata.unmappedCount()));
        });
        return new BAMIndexMetaData(chunks);
    }

    private boolean hasReference(final int referenceIndex) {
        return referenceIndex >= 0 && referenceIndex < source.getReferenceCount();
    }

    private int levels() {
        return source.getDepth() + 1;
    }

    private static int firstBinInLevel(final int level) {
        return ((1 << 3 * level) - 1) / 7;
    }

    private long maxSpan() {
        return 1L << source.getMinShift() + 3 * source.getDepth();
    }

    @Override
    public int getLevelSize(final int levelNumber) {
        if (levelNumber < 0 || levelNumber >= levels()) {
            throw new SAMException("Level number is too big (" + levelNumber + ").");
        }
        return 1 << 3 * levelNumber;
    }

    @Override
    public int getLevelForBin(final Bin bin) {
        if (bin == null || bin.getBinNumber() >= firstBinInLevel(levels())) {
            throw new SAMException("Tried to get level for invalid bin.");
        }
        int level = levels() - 1;
        while (bin.getBinNumber() < firstBinInLevel(level)) {
            level--;
        }
        return level;
    }

    @Override
    public int getFirstLocusInBin(final Bin bin) {
        final int level = getLevelForBin(bin);
        final long binSpan = maxSpan() / getLevelSize(level);
        return (int) ((bin.getBinNumber() - firstBinInLevel(level)) * binSpan + 1);
    }

    @Override
    public int getLastLocusInBin(final Bin bin) {
        final int level = getLevelForBin(bin);
        final long binSpan = maxSpan() / getLevelSize(level);
        return (int) Math.min(Integer.MAX_VALUE, (bin.getBinNumber() - firstBinInLevel(level) + 1) * binSpan);
    }

    @Override
    public BinList getBinsOverlapping(final int referenceIndex, final int startPos, final int endPos) {
        return new BinList(
                referenceIndex, GenomicIndexUtil.regionToBins(startPos, endPos, source.getMinShift(), levels()));
    }

    /** The chunks of the bin and of every bin above it, less those that end before the bin's first record. */
    @Override
    public BAMFileSpan getSpanOverlapping(final Bin bin) {
        if (bin == null) {
            return null;
        }
        if (!hasReference(bin.getReferenceSequence())) {
            return new BAMFileSpan();
        }
        final ReferenceBins reference = source.getReference(bin.getReferenceSequence());
        final List<Chunk> chunks = new ArrayList<>();
        for (int binNumber = bin.getBinNumber(); ; binNumber = (binNumber - 1) >> 3) {
            for (int i = 0; i < reference.getBinCount(); i++) {
                if (reference.getBinNumber(i) == binNumber) {
                    reference.getChunks(i).forEach(chunk -> chunks.add(chunk.clone()));
                }
            }
            if (binNumber == 0) {
                break;
            }
        }
        final long minimumOffset =
                BinningIndex.minimumOffset(reference, source.getMinShift(), source.getDepth(), getFirstLocusInBin(bin));
        return new BAMFileSpan(Chunk.optimizeChunkList(chunks, minimumOffset));
    }

    @Override
    public void close() {
        source.close();
    }
}
