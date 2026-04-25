package htsjdk.samtools.cram;

import htsjdk.samtools.cram.build.CramContainerIterator;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import java.io.File;
import java.util.*;

/**
 * Dumps per-DataSeries compression statistics from a CRAM file. Useful for comparing
 * compression methods and sizes between different CRAM implementations.
 */
public class CramStats {

    /**
     * Entry point. Dumps per-DataSeries compression statistics for each CRAM file argument.
     *
     * @param args one or more paths to CRAM files
     */
    public static void main(final String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: CramStats <file.cram> [file2.cram ...]");
            System.exit(1);
        }

        for (final String path : args) {
            dumpStats(path);
            System.out.println();
        }
    }

    private static void dumpStats(final String path) throws Exception {
        System.out.printf("=== %s ===%n", path);

        // Track per-contentID totals: compressed size, uncompressed size, method
        final Map<Integer, long[]> compressedByContentId = new TreeMap<>(); // contentId -> [compressed, uncompressed]
        final Map<Integer, Map<BlockCompressionMethod, Integer>> methodsByContentId = new TreeMap<>();
        long totalCompressed = 0;
        long totalUncompressed = 0;
        int containerCount = 0;
        int sliceCount = 0;
        int recordCount = 0;

        try (final SeekableFileStream stream = new SeekableFileStream(new File(path))) {
            final CramContainerIterator iter = new CramContainerIterator(stream);
            System.out.printf("CRAM version: %s%n", iter.getCramHeader().getCRAMVersion());
            while (iter.hasNext()) {
                final Container container = iter.next();
                containerCount++;
                recordCount += container.getContainerHeader().getNumberOfRecords();

                for (final Slice slice : container.getSlices()) {
                    sliceCount++;
                    final SliceBlocks blocks = slice.getSliceBlocks();

                    // Core block
                    final Block core = blocks.getCoreBlock();
                    accumulate(compressedByContentId, methodsByContentId, -1, core);
                    totalCompressed += core.getCompressedContentSize();
                    totalUncompressed += core.getUncompressedContentSize();

                    // External blocks
                    for (final int contentId : blocks.getExternalContentIDs()) {
                        final Block block = blocks.getExternalBlock(contentId);
                        accumulate(compressedByContentId, methodsByContentId, contentId, block);
                        totalCompressed += block.getCompressedContentSize();
                        totalUncompressed += block.getUncompressedContentSize();
                    }
                }
            }
        }

        System.out.printf("Containers: %d, Slices: %d, Records: %,d%n", containerCount, sliceCount, recordCount);
        System.out.printf(
                "Total: compressed=%,d  uncompressed=%,d  ratio=%.1f%%%n%n",
                totalCompressed,
                totalUncompressed,
                totalUncompressed > 0 ? (100.0 * totalCompressed / totalUncompressed) : 0);

        // Map content IDs to data series names
        final Map<Integer, String> contentIdNames = new HashMap<>();
        for (final DataSeries ds : DataSeries.values()) {
            contentIdNames.put(ds.getExternalBlockContentId().intValue(), ds.getCanonicalName());
        }
        contentIdNames.put(-1, "CORE");

        // Print per-content-ID stats
        System.out.printf(
                "%-6s %-14s %12s %12s %7s  %s%n", "ID", "Series", "Compressed", "Uncompressed", "Ratio", "Methods");
        System.out.println("-".repeat(80));

        for (final Map.Entry<Integer, long[]> entry : compressedByContentId.entrySet()) {
            final int id = entry.getKey();
            final long[] sizes = entry.getValue();
            final String name = contentIdNames.getOrDefault(id, "TAG:" + id);
            final String methods =
                    methodsByContentId.getOrDefault(id, Collections.emptyMap()).toString();
            System.out.printf(
                    "%-6d %-14s %,12d %,12d %6.1f%%  %s%n",
                    id, name, sizes[0], sizes[1], sizes[1] > 0 ? (100.0 * sizes[0] / sizes[1]) : 0, methods);
        }
    }

    private static void accumulate(
            final Map<Integer, long[]> sizeMap,
            final Map<Integer, Map<BlockCompressionMethod, Integer>> methodMap,
            final int contentId,
            final Block block) {
        sizeMap.computeIfAbsent(contentId, k -> new long[2]);
        sizeMap.get(contentId)[0] += block.getCompressedContentSize();
        sizeMap.get(contentId)[1] += block.getUncompressedContentSize();

        methodMap.computeIfAbsent(contentId, k -> new EnumMap<>(BlockCompressionMethod.class));
        methodMap.get(contentId).merge(block.getCompressionMethod(), 1, Integer::sum);
    }
}
