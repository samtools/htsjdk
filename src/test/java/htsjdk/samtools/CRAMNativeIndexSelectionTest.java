package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.index.HtsFileSpan;
import htsjdk.index.HtsQueryIndex;
import htsjdk.samtools.cram.CRAIQueryIndex;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Guards which index engine a CRAM reader uses. Record-level tests would still pass if the reader
 * quietly fell back to converting the CRAI into a BAI, since both paths return the same records.
 */
public class CRAMNativeIndexSelectionTest extends HtsjdkTest {

    private static final Path TEST_DATA_DIR = Paths.get("src/test/resources/htsjdk/samtools/cram");

    @DataProvider(name = "cramsWithCrai")
    public Object[][] cramsWithCrai() {
        return new Object[][] {
            {"cramQueryWithCRAI.cram"},
            {"CEUTrio.HiSeq.WGS.b37.NA12878.20.21.v3.0.samtools.cram"},
            {"NA12878.20.21.1-100.100-SeqsPerSlice.500-unMapped.cram"},
        };
    }

    private static SamReader open(final String cramFileName) {
        return SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.SILENT)
                .open(TEST_DATA_DIR.resolve(cramFileName));
    }

    @Test(dataProvider = "cramsWithCrai")
    public void testACraiIndexedCramQueriesThroughTheNativeEngine(final String cramFileName) throws IOException {
        try (final SamReader reader = open(cramFileName)) {
            Assert.assertTrue(reader.hasIndex());
            Assert.assertTrue(
                    reader.indexing().getHtsIndex() instanceof CRAIQueryIndex,
                    "Expected a CRAIQueryIndex but got "
                            + reader.indexing().getHtsIndex().getClass().getName());
        }
    }

    @Test
    public void testABaiIndexedCramQueriesThroughTheBaiIndex() throws IOException {
        // The BAI path has to keep working: files indexed with a .bai still exist in the wild.
        try (final SamReader reader = SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.SILENT)
                .open(TEST_DATA_DIR.resolve("cramQueryWithBAI.cram"))) {
            Assert.assertTrue(reader.indexing().getHtsIndex() instanceof BAMIndex);
        }
    }

    @Test(dataProvider = "cramsWithCrai")
    public void testTheNativeSpanIsSomethingTheReaderAccepts(final String cramFileName) throws IOException {
        // A span from getHtsIndex() has to satisfy iterator(SAMFileSpan). Records are not decoded
        // here, since that needs each fixture's reference; CRAMIndexPermutationsTests covers that.
        try (final SamReader reader = open(cramFileName)) {
            final SAMSequenceRecord sequence =
                    reader.getFileHeader().getSequenceDictionary().getSequence(0);
            final HtsFileSpan span =
                    reader.indexing().getHtsIndex().getSpanOverlapping(0, 1, sequence.getSequenceLength());

            Assert.assertTrue(span instanceof SAMFileSpan, "Span is not usable with iterator(SAMFileSpan)");
            Assert.assertFalse(span.isEmpty(), "Reference 0 should have records in all of these files");

            final long[] coordinates = ((BAMFileSpan) span).toCoordinateArray();
            Assert.assertEquals(coordinates.length % 2, 0, "Coordinates must come in pairs");
            for (int i = 0; i < coordinates.length; i += 2) {
                Assert.assertTrue(coordinates[i] < coordinates[i + 1], "Pair " + i / 2 + " is not a valid boundary");
                if (i > 0) {
                    Assert.assertTrue(coordinates[i] > coordinates[i - 1], "Pairs are not in ascending file order");
                }
            }
        }
    }

    @Test(dataProvider = "cramsWithCrai")
    public void testTheNativeIndexReportsWhereUnplacedRecordsStart(final String cramFileName) throws IOException {
        try (final SamReader reader = open(cramFileName)) {
            final Optional<HtsFileSpan> unplaced =
                    reader.indexing().getHtsIndex().getSpanOfUnplaced();
            Assert.assertTrue(unplaced.isPresent(), "Every one of these files has placed records");
            Assert.assertFalse(unplaced.get().isEmpty());
        }
    }

    private static Set<Long> containerOffsets(final long[] coordinates) {
        final Set<Long> offsets = new TreeSet<>();
        for (int i = 0; i < coordinates.length; i += 2) {
            offsets.add(coordinates[i] >> 16);
        }
        return offsets;
    }

    @Test(dataProvider = "cramsWithCrai")
    public void testGetIndexStillSynthesisesABaiForCallersThatNeedOne(final String cramFileName) throws IOException {
        // Deprecated, but it has to keep working for the whole of this major release.
        try (final SamReader reader = open(cramFileName)) {
            @SuppressWarnings("deprecation")
            final BAMIndex index = reader.indexing().getIndex();
            Assert.assertNotNull(index);
            Assert.assertNotNull(index.getSpanOverlapping(0, 1, Integer.MAX_VALUE));
        }
    }

    @Test(dataProvider = "cramsWithCrai")
    public void testTheBaiPathReadsEveryContainerTheNativePathReads(final String cramFileName) throws IOException {
        try (final SamReader reader = open(cramFileName)) {
            final HtsQueryIndex nativeIndex = reader.indexing().getHtsIndex();
            @SuppressWarnings("deprecation")
            final BAMIndex baiIndex = reader.indexing().getIndex();
            final SAMSequenceDictionary dictionary = reader.getFileHeader().getSequenceDictionary();

            for (int reference = 0; reference < dictionary.size(); reference++) {
                final int length = dictionary.getSequence(reference).getSequenceLength();
                final BAMFileSpan nativeSpan = (BAMFileSpan) nativeIndex.getSpanOverlapping(reference, 1, length);
                final BAMFileSpan baiSpan = baiIndex.getSpanOverlapping(reference, 1, length);
                if (nativeSpan.isEmpty()) {
                    continue;
                }
                Assert.assertFalse(
                        baiSpan == null || baiSpan.isEmpty(),
                        "Native path found containers the BAI path did not, on reference " + reference);

                final Set<Long> fromBai = containerOffsets(baiSpan.toCoordinateArray());
                for (final long container : containerOffsets(nativeSpan.toCoordinateArray())) {
                    Assert.assertTrue(
                            fromBai.contains(container),
                            "Native path would read container " + container
                                    + ", which the BAI path would not, on reference " + reference);
                }
            }
        }
    }
}
