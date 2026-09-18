package htsjdk.index;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.RuntimeEOFException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import org.testng.Assert;
import org.testng.annotations.Test;

/** {@link BinningIndex#readBaiLayout} and {@link BinningIndex#writeBaiLayout}: the body that BAI and TBI share. */
public class BinningIndexBaiLayoutTest extends HtsjdkTest {
    private static final int MIN_SHIFT = BinningIndex.BAI_MIN_SHIFT;
    private static final int DEPTH = BinningIndex.BAI_DEPTH;
    private static final int METADATA_BIN = 37450;

    private static byte[] write(final BinningIndex index) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final BinaryCodec codec = new BinaryCodec(bytes);
        index.writeBaiLayout(codec);
        codec.close();
        return bytes.toByteArray();
    }

    private static BinningIndex read(final byte[] bytes, final int referenceCount) {
        return BinningIndex.readBaiLayout(
                new BinaryCodec(new ByteArrayInputStream(bytes)), referenceCount, MIN_SHIFT, DEPTH);
    }

    /** Hand-builds an index body, little-endian, for cases the builder cannot produce. */
    private static final class Body {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final BinaryCodec codec = new BinaryCodec(bytes);

        Body ints(final int... values) {
            for (final int value : values) codec.writeInt(value);
            return this;
        }

        Body longs(final long... values) {
            for (final long value : values) codec.writeLong(value);
            return this;
        }

        byte[] toBytes() {
            codec.close();
            return bytes.toByteArray();
        }
    }

    private static BinningIndex threeReferenceIndex() {
        final IndexedRecords records = new IndexedRecords();
        for (int start = 1; start < 5_000_000; start += 2_500)
            records.add(0, start, start + (start % 7 == 0 ? 90_000 : 0));
        for (int start = 1; start < 1_000_000; start += 900) records.add(2, start, start);
        return records.index(MIN_SHIFT, DEPTH, 3);
    }

    @Test
    public void testIndexSurvivesARoundTrip() {
        final BinningIndex index = threeReferenceIndex();
        Assert.assertEquals(read(write(index), 3), index);
    }

    @Test
    public void testReferenceWithoutRecordsIsWrittenAsZeroBinsAndZeroWindows() {
        // The layout gives every reference both counts; omitting the second misaligns every reader that
        // follows the specification, htslib included.
        final BinningIndex index = new BinningIndex.Builder(MIN_SHIFT, DEPTH).build(2);
        Assert.assertEquals(write(index), new Body().ints(0, 0, 0, 0).toBytes());
    }

    @Test
    public void testReferenceAfterAnEmptyOneIsReadCorrectly() {
        final byte[] body = new Body()
                .ints(0, 0) // reference 0: no bins, no windows
                .ints(1, 4681, 1)
                .longs(100, 200)
                .ints(1)
                .longs(100)
                .toBytes();
        final BinningIndex index = read(body, 2);
        Assert.assertTrue(index.getReference(0).isEmpty());
        Assert.assertEquals(index.getReference(1).getBinNumber(0), 4681);
        Assert.assertEquals(index.getReference(1).getLinearIndex(), new long[] {100});
    }

    @Test
    public void testBinsListedOutOfOrderAreSortedOnRead() {
        final byte[] body = new Body()
                .ints(2)
                .ints(4682, 1)
                .longs(300, 400)
                .ints(4681, 1)
                .longs(100, 200)
                .ints(0)
                .toBytes();
        final ReferenceBins reference = read(body, 1).getReference(0);
        Assert.assertEquals(reference.getBinNumber(0), 4681);
        Assert.assertEquals(reference.getBinNumber(1), 4682);
        Assert.assertEquals(reference.getChunks(0).get(0).getChunkStart(), 100);
    }

    @Test
    public void testMetadataPseudoBinIsKeptApartFromRealBinsAndWrittenBack() {
        final byte[] body = new Body()
                .ints(2)
                .ints(4681, 1)
                .longs(100, 200)
                .ints(METADATA_BIN, 2)
                .longs(100, 200, 12, 3)
                .ints(1)
                .longs(100)
                .toBytes();
        final BinningIndex index = read(body, 1);
        Assert.assertEquals(index.getReference(0).getBinCount(), 1);
        Assert.assertEquals(
                index.getReference(0).getMetadata().orElseThrow(), new ReferenceBins.Metadata(100, 200, 12, 3));
        Assert.assertEquals(write(index), body);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testMetadataPseudoBinWithTheWrongChunkCountIsRejected() {
        read(new Body().ints(1, METADATA_BIN, 1).longs(100, 200).ints(0).toBytes(), 1);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testDuplicatedBinIsRejected() {
        read(
                new Body()
                        .ints(2)
                        .ints(4681, 1)
                        .longs(1, 2)
                        .ints(4681, 1)
                        .longs(3, 4)
                        .ints(0)
                        .toBytes(),
                1);
    }

    @Test
    public void testBinWithoutChunksIsIgnored() {
        final ReferenceBins reference = read(
                        new Body()
                                .ints(2)
                                .ints(4681, 0)
                                .ints(4682, 1)
                                .longs(1, 2)
                                .ints(0)
                                .toBytes(),
                        1)
                .getReference(0);
        Assert.assertEquals(reference.getBinCount(), 1);
        Assert.assertEquals(reference.getBinNumber(0), 4682);
    }

    @Test(expectedExceptions = RuntimeEOFException.class)
    public void testTruncatedBodyIsRejected() {
        final byte[] whole = write(threeReferenceIndex());
        read(Arrays.copyOf(whole, whole.length - 5), 3);
    }
}
