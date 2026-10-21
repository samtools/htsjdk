package htsjdk.variant.bcf2;

import htsjdk.index.BinningIndex;
import htsjdk.samtools.Chunk;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.tribble.index.Block;
import htsjdk.tribble.index.Index;
import htsjdk.tribble.util.LittleEndianOutputStream;
import htsjdk.variant.vcf.VCFContigHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The CSI index of a BGZF-compressed BCF, as {@code bcftools index} writes it: a CSI file whose references are the
 * BCF's contigs, numbered as its contig dictionary numbers them, with no tabix header. Made by
 * {@link BCFFileReader#createCsiIndex()}, and by {@link htsjdk.tribble.index.IndexFactory#createIndex} given
 * {@link htsjdk.tribble.index.IndexFactory.IndexType#CSI} and a BCF. The index is used from its file, which names no
 * contigs: write it, then open the BCF through it with {@link BCFFileReader} or
 * {@link htsjdk.tribble.AbstractFeatureReader#getFeatureReader}.
 */
public final class BCFCsiIndex implements Index {
    /** log2 of the span of the smallest bins, as {@code bcftools index} has them. */
    private static final int MIN_SHIFT = 14;

    /** The reach htslib gives the index of a BCF whose contig lines give no lengths: the largest POS a BCF holds. */
    private static final long REACH_WITHOUT_CONTIG_LENGTHS = Integer.MAX_VALUE;

    private final Map<String, Integer> referenceIndexByContig;
    private final List<String> contigs;
    private final BinningIndex binningIndex;

    /**
     * @param referenceIndexByContig each contig's number in the BCF's contig dictionary
     * @param contigs the contigs in the order of their numbers
     * @param binningIndex the index, its references numbered as {@code referenceIndexByContig} numbers them
     */
    BCFCsiIndex(
            final Map<String, Integer> referenceIndexByContig,
            final List<String> contigs,
            final BinningIndex binningIndex) {
        this.referenceIndexByContig = Map.copyOf(referenceIndexByContig);
        this.contigs = List.copyOf(contigs);
        this.binningIndex = binningIndex;
    }

    /**
     * Starts the bins of a BCF's CSI index with the scheme {@code bcftools index} chooses: bins of 2^14 bases at the
     * smallest, and as few levels as the longest contig in the header needs, or as reach 2^31 - 1 bases if no contig
     * line gives a length.
     *
     * @param header the BCF's header
     * @return a builder of an index bound for CSI
     */
    public static BinningIndex.Builder binningIndexBuilderFor(final VCFHeader header) {
        long longestContig = 0;
        for (final VCFContigHeaderLine contig : header.getContigLines()) {
            longestContig =
                    Math.max(longestContig, contig.getSAMSequenceRecord().getSequenceLength());
        }
        final BinningIndex.Geometry geometry = BinningIndex.shallowestCsiGeometry(
                MIN_SHIFT, longestContig > 0 ? longestContig : REACH_WITHOUT_CONTIG_LENGTHS);
        return new BinningIndex.Builder(geometry.minShift(), geometry.depth(), true);
    }

    /** @return the index's bins */
    public BinningIndex getBinningIndex() {
        return binningIndex;
    }

    @Override
    public List<Block> getBlocks(final String chr, final int start, final int end) {
        final Integer referenceIndex = referenceIndexByContig.get(chr);
        if (referenceIndex == null) {
            return Collections.emptyList();
        }
        final List<Chunk> chunks =
                binningIndex.getSpanOverlapping(referenceIndex, start, end).getChunks();
        final List<Block> blocks = new ArrayList<>(chunks.size());
        for (final Chunk chunk : chunks) {
            blocks.add(new Block(chunk.getChunkStart(), chunk.getChunkEnd() - chunk.getChunkStart()));
        }
        return blocks;
    }

    @Override
    public boolean isCurrentVersion() {
        return true;
    }

    @Override
    public List<String> getSequenceNames() {
        return contigs;
    }

    @Override
    public boolean containsChromosome(final String chr) {
        return referenceIndexByContig.containsKey(chr);
    }

    /** Writes the CSI uncompressed, for a stream the caller BGZF-compresses; {@link #write(Path)} compresses. */
    @Override
    public void write(final LittleEndianOutputStream stream) throws IOException {
        // The codec is not closed, since that would close the caller's stream; it holds nothing to flush. The aux
        // block is empty, as bcftools leaves it: a BCF's CSI names no contigs, the BCF's header does.
        binningIndex.writeCsi(new BinaryCodec(stream), new byte[0]);
    }

    @Override
    public void write(final Path indexPath) throws IOException {
        try (final LittleEndianOutputStream stream = new LittleEndianOutputStream(
                new BlockCompressedOutputStream(Files.newOutputStream(indexPath), (Path) null))) {
            write(stream);
        }
    }

    /** Writes the index beside the BCF, as {@code x.bcf.csi}. */
    @Override
    public void writeBasedOnFeaturePath(final Path featurePath) throws IOException {
        if (!Files.isRegularFile(featurePath)) {
            throw new IOException("Cannot write based on a non-regular file: " + featurePath.toUri());
        }
        write(featurePath.resolveSibling(featurePath.getFileName() + FileExtensions.CSI));
    }

    /** @return null, as a CSI holds no properties */
    @Override
    public Map<String, String> getProperties() {
        return null;
    }

    @Override
    public boolean equalsIgnoreProperties(final Object o) {
        return equals(o);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof BCFCsiIndex)) return false;
        final BCFCsiIndex that = (BCFCsiIndex) o;
        return referenceIndexByContig.equals(that.referenceIndexByContig) && binningIndex.equals(that.binningIndex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(referenceIndexByContig, binningIndex);
    }
}
