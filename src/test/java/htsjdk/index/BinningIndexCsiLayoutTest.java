package htsjdk.index;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.BAMFileSpan;
import htsjdk.samtools.Chunk;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.RuntimeEOFException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import org.testng.Assert;
import org.testng.annotations.Test;

/** {@link BinningIndex#readCsi} and {@link BinningIndex#writeCsi}: the CSI file format. */
public class BinningIndexCsiLayoutTest extends HtsjdkTest {
    private static final byte[] AUX = "some format's header".getBytes(StandardCharsets.US_ASCII);

    private static byte[] write(final BinningIndex index, final byte[] aux) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final BinaryCodec codec = new BinaryCodec(bytes);
        index.writeCsi(codec, aux);
        codec.close();
        return bytes.toByteArray();
    }

    private static BinningIndex.CsiContents read(final byte[] bytes) {
        return BinningIndex.readCsi(new BinaryCodec(new ByteArrayInputStream(bytes)));
    }

    /** Records across 1.5 Gb of one reference under the scheme tabix -C chooses, some long enough for higher bins. */
    private static IndexedRecords deepRecords() {
        final IndexedRecords records = new IndexedRecords();
        for (int i = 0, start = 1; start < 1_500_000_000; i++, start += 900_007) {
            records.add(0, start, start + ((i % 30 == 0) ? 20_000_000 : (i % 7 == 0) ? 50_000 : 0));
        }
        return records;
    }

    @Test
    public void testAuxAndGeometrySurviveARoundTrip() {
        final BinningIndex index = deepRecords().index(14, 6, 1);
        final BinningIndex.CsiContents contents = read(write(index, AUX));
        Assert.assertEquals(contents.aux(), AUX);
        Assert.assertEquals(contents.index().getMinShift(), 14);
        Assert.assertEquals(contents.index().getDepth(), 6);
    }

    @Test
    public void testIndexSurvivesARoundTrip() {
        final BinningIndex built = deepRecords().index(14, 6, 1);
        final BinningIndex loaded = read(write(built, AUX)).index();
        Assert.assertEquals(loaded, BinningIndexTestUtils.asStoredInCsi(built));
    }

    @Test
    public void testLinearIndexIsNotStored() {
        final BinningIndex loaded =
                read(write(deepRecords().index(14, 6, 1), AUX)).index();
        Assert.assertEquals(loaded.getReference(0).getLinearIndex().length, 0);
    }

    @Test
    public void testLoadedIndexAnswersQueriesWithoutALinearIndex() {
        final IndexedRecords records = deepRecords();
        final BinningIndex loaded = read(write(records.index(14, 6, 1), AUX)).index();
        final Random random = new Random(5);
        int overlapsSeen = 0;
        for (int i = 0; i < 300; i++) {
            final int start = 1 + random.nextInt(1_500_000_000);
            final int end = start + random.nextInt(i % 4 == 0 ? 100_000_000 : 2_000_000);
            final BAMFileSpan span = loaded.getSpanOverlapping(0, start, end);
            overlapsSeen += IndexedRecords.assertSpanCoversOverlaps(records.records(), span, 0, start, end);
        }
        Assert.assertTrue(overlapsSeen > 300, "queries should have hit plenty of records");
    }

    @Test
    public void testLoffsetsExcludeChunksThatEndBeforeTheQuery() {
        // A long record's chunk sits in a high-level bin every later query overlaps; without a linear index it is
        // the bins' loffsets that let a query far downstream leave it unread.
        final IndexedRecords records = new IndexedRecords().add(0, 1, 30_000_000);
        for (int start = 10; start < 300_000_000; start += 10_000) records.add(0, start, start);
        final BinningIndex loaded = read(write(records.index(14, 6, 1), AUX)).index();
        final BAMFileSpan span = loaded.getSpanOverlapping(0, 250_000_000, 250_010_000);
        final long longRecordEnd = records.records().get(0).chunkEnd();
        for (final Chunk chunk : span.getChunks()) {
            Assert.assertTrue(Long.compareUnsigned(chunk.getChunkStart(), longRecordEnd) >= 0, span.toString());
        }
        IndexedRecords.assertSpanCoversOverlaps(records.records(), span, 0, 250_000_000, 250_010_000);
    }

    @Test
    public void testMetadataIsWrittenWhenRecordedAndReadBack() {
        final BinningIndex.Builder builder = new BinningIndex.Builder(14, 6, true);
        final IndexedRecords records =
                new IndexedRecords().add(0, 100, 100).add(0, 200, 200).add(2, 300, 300);
        for (final IndexedRecords.Rec rec : records.records()) {
            builder.add(rec.referenceIndex(), rec.start(), rec.end(), rec.chunkStart(), rec.chunkEnd());
        }
        final BinningIndex loaded = read(write(builder.build(3), AUX)).index();
        Assert.assertEquals(
                loaded.getReference(0).getMetadata().orElseThrow(),
                new ReferenceBins.Metadata(
                        records.records().get(0).chunkStart(),
                        records.records().get(1).chunkEnd(),
                        2,
                        0));
        Assert.assertTrue(loaded.getReference(1).getMetadata().isEmpty());
        Assert.assertEquals(loaded.getReference(2).getMetadata().orElseThrow().mappedCount(), 1);
    }

    @Test
    public void testNoCoordinateCountIsAlwaysWritten() {
        final BinningIndex built = new IndexedRecords().add(0, 100, 100).index(14, 6, 1);
        Assert.assertTrue(built.getNoCoordinateCount().isEmpty());
        Assert.assertEquals(
                read(write(built, AUX)).index().getNoCoordinateCount().getAsLong(), 0);
    }

    @Test
    public void testEmptyAuxIsAllowed() {
        final BinningIndex index = new IndexedRecords().add(0, 100, 100).index(14, 5, 1);
        final BinningIndex.CsiContents contents = read(write(index, new byte[0]));
        Assert.assertEquals(contents.aux().length, 0);
        Assert.assertEquals(contents.index().getReference(0).getBinCount(), 1);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testWrongMagicIsRejected() {
        final byte[] bytes = write(new IndexedRecords().add(0, 100, 100).index(14, 5, 1), AUX);
        bytes[0] = 'T';
        read(bytes);
    }

    @Test(expectedExceptions = RuntimeEOFException.class)
    public void testTruncatedFileIsRejected() {
        final byte[] bytes = write(deepRecords().index(14, 6, 1), AUX);
        read(Arrays.copyOf(bytes, bytes.length - 20));
    }
}
