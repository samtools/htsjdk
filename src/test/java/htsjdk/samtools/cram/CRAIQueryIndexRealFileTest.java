package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.BAMFileSpan;
import htsjdk.samtools.BAMIndex;
import htsjdk.samtools.CRAMCRAIIndexer;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Cross-checks {@link CRAIQueryIndex} against the CRAI-to-BAI path on the test-suite CRAI files. BAI
 * bins are coarse, so the BAI path may return extra containers; every container the native path
 * returns must also be returned by the BAI path.
 */
public class CRAIQueryIndexRealFileTest extends HtsjdkTest {

    private static final Path TEST_DATA_DIR = Paths.get("src/test/resources/htsjdk/samtools/cram");

    @DataProvider(name = "cramsWithCrai")
    public Object[][] cramsWithCrai() {
        return new Object[][] {
            {"cramQueryWithCRAI.cram"},
            {"CEUTrio.HiSeq.WGS.b37.NA12878.20.21.v3.0.samtools.cram"},
            {"NA12878.20.21.1-100.100-SeqsPerSlice.0-unMapped.cram"},
            {"NA12878.20.21.1-100.100-SeqsPerSlice.1-unMapped.cram"},
            {"NA12878.20.21.1-100.100-SeqsPerSlice.500-unMapped.cram"},
            {"mitoAlignmentStartTest.cram"},
            {"testIGV1286.cram"},
            {"referenceNotRequired.cram"},
        };
    }

    private static List<CRAIEntry> readEntries(final Path craiPath) throws IOException {
        try (final InputStream stream = Files.newInputStream(craiPath)) {
            return new ArrayList<>(CRAMCRAIIndexer.readIndex(stream).getCRAIEntries());
        }
    }

    /** Container offsets the BAI path would read. */
    private static TreeSet<Long> baiContainers(
            final BAMIndex baiIndex, final int reference, final int start, final int end) {
        final TreeSet<Long> containers = new TreeSet<>();
        final BAMFileSpan span = baiIndex.getSpanOverlapping(reference, start, end);
        final long[] coordinates = span == null ? null : span.toCoordinateArray();
        if (coordinates != null) {
            for (int i = 0; i < coordinates.length; i += 2) {
                containers.add(coordinates[i] >> 16);
            }
        }
        return containers;
    }

    @Test(dataProvider = "cramsWithCrai")
    public void testNativeQueryOnlyReturnsContainersTheBaiPathAlsoReturns(final String cramFileName)
            throws IOException {
        final Path cramPath = TEST_DATA_DIR.resolve(cramFileName);
        final CRAIQueryIndex index = new CRAIQueryIndex(readEntries(TEST_DATA_DIR.resolve(cramFileName + ".crai")));

        int queriesWithHits = 0;
        try (final SamReader reader = SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.SILENT)
                .open(cramPath)) {
            final SAMSequenceDictionary dictionary = reader.getFileHeader().getSequenceDictionary();
            final BAMIndex baiIndex = reader.indexing().getIndex();

            for (int reference = 0; reference < dictionary.size(); reference++) {
                final int sequenceLength = dictionary.getSequence(reference).getSequenceLength();
                for (final int[] window : queryWindows(sequenceLength)) {
                    final long[] nativeContainers = index.getContainerOffsets(reference, window[0], window[1]);
                    if (nativeContainers.length == 0) {
                        continue;
                    }
                    queriesWithHits++;

                    final String where = cramFileName + " " + reference + ":" + window[0] + "-" + window[1];
                    final TreeSet<Long> fromBai = baiContainers(baiIndex, reference, window[0], window[1]);
                    for (final long container : nativeContainers) {
                        Assert.assertTrue(
                                fromBai.contains(container),
                                "Native path would read container " + container + ", which the BAI path (" + fromBai
                                        + ") would not, for " + where);
                    }
                }
            }
        }
        Assert.assertTrue(
                queriesWithHits > 0, "No query hit anything in " + cramFileName + "; the test proved nothing");
    }

    @Test(dataProvider = "cramsWithCrai")
    public void testEveryPlacedEntryIsFoundByQueryingItsOwnSpan(final String cramFileName) throws IOException {
        // Independent of the BAI path.
        final List<CRAIEntry> entries = readEntries(TEST_DATA_DIR.resolve(cramFileName + ".crai"));
        final CRAIQueryIndex index = new CRAIQueryIndex(entries);

        for (final CRAIEntry entry : entries) {
            if (entry.getSequenceId() < 0) {
                continue;
            }
            final int middle = entry.getAlignmentStart() + Math.max(0, (entry.getAlignmentSpan() - 1) / 2);
            final long[] containers = index.getContainerOffsets(entry.getSequenceId(), middle, middle);
            Assert.assertTrue(
                    contains(containers, entry.getContainerStartByteOffset()),
                    "Querying the middle of " + entry + " did not return its own container");
        }
    }

    @Test(dataProvider = "cramsWithCrai")
    public void testContainersComeBackInAscendingFileOrder(final String cramFileName) throws IOException {
        // CramSpanContainerIterator only seeks forward.
        final List<CRAIEntry> entries = readEntries(TEST_DATA_DIR.resolve(cramFileName + ".crai"));
        final CRAIQueryIndex index = new CRAIQueryIndex(entries);

        for (final CRAIEntry entry : entries) {
            if (entry.getSequenceId() < 0) {
                continue;
            }
            final long[] containers = index.getContainerOffsets(entry.getSequenceId(), 1, Integer.MAX_VALUE);
            for (int i = 1; i < containers.length; i++) {
                Assert.assertTrue(
                        containers[i] > containers[i - 1],
                        "Containers out of ascending order in " + cramFileName + " at index " + i);
            }
        }
    }

    private static boolean contains(final long[] values, final long wanted) {
        for (final long value : values) {
            if (value == wanted) {
                return true;
            }
        }
        return false;
    }

    /** A spread of query windows: whole reference, single bases, and small windows. */
    private static List<int[]> queryWindows(final int sequenceLength) {
        final List<int[]> windows = new ArrayList<>();
        windows.add(new int[] {1, sequenceLength});
        for (int i = 1; i <= 20; i++) {
            final int start = Math.max(1, (int) ((long) sequenceLength * i / 21));
            windows.add(new int[] {start, start});
            windows.add(new int[] {start, Math.min(sequenceLength, start + 10_000)});
        }
        return windows;
    }
}
