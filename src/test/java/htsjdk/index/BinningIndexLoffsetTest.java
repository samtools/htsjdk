package htsjdk.index;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * How a bin's {@code loffset} is derived: htslib's way when building for CSI, and from the linear index otherwise.
 */
public class BinningIndexLoffsetTest extends HtsjdkTest {
    private static final int WINDOW = 1 << 14;
    private static final int FIRST_SMALLEST_BIN = 4681;

    private static long offset(final long blockAddress) {
        return BlockCompressedFilePointerUtil.makeFilePointer(blockAddress, 0);
    }

    private static long loffsetOfBin(final ReferenceBins reference, final int binNumber) {
        for (int i = 0; i < reference.getBinCount(); i++) {
            if (reference.getBinNumber(i) == binNumber) return reference.getLoffset(i);
        }
        throw new AssertionError("no bin " + binNumber);
    }

    @Test
    public void testSmallestBinTakesItsOwnWindowsOffset() {
        final BinningIndex.Builder builder = new BinningIndex.Builder(14, 5, true);
        builder.add(0, 10, 10, offset(100), offset(101));
        builder.add(0, WINDOW + 10, WINDOW + 10, offset(200), offset(201));
        final ReferenceBins reference = builder.build(1).getReference(0);
        Assert.assertEquals(loffsetOfBin(reference, FIRST_SMALLEST_BIN), offset(100));
        Assert.assertEquals(loffsetOfBin(reference, FIRST_SMALLEST_BIN + 1), offset(200));
    }

    @Test
    public void testHigherBinTakesTheOffsetOfItsFirstWindow() {
        final BinningIndex.Builder builder = new BinningIndex.Builder(14, 5, true);
        builder.add(0, 10, 10, offset(100), offset(101)); // window 0
        builder.add(0, 3 * WINDOW, 5 * WINDOW, offset(200), offset(201)); // spans windows 2-4: 128 kb bin 585
        final ReferenceBins reference = builder.build(1).getReference(0);
        Assert.assertEquals(loffsetOfBin(reference, 585), offset(100));
    }

    @Test
    public void testBinWhoseFirstWindowIsEmptyTakesTheNextOccupiedWindowsOffsetWhenBuiltForCsi() {
        final BinningIndex.Builder builder = new BinningIndex.Builder(14, 5, true);
        // Windows 0 and 1 hold nothing; the 128 kb bin 585 starts at window 0 all the same.
        builder.add(0, 2 * WINDOW + 10, 2 * WINDOW + 10, offset(300), offset(301));
        builder.add(0, 3 * WINDOW, 5 * WINDOW, offset(400), offset(401));
        final ReferenceBins reference = builder.build(1).getReference(0);
        Assert.assertEquals(loffsetOfBin(reference, 585), offset(300));
    }

    @Test
    public void testBinWhoseFirstWindowIsEmptyTakesTheNextOccupiedWindowsOffsetWhenDerivedFromALinearIndex() {
        // Not built for CSI, so the loffset comes from the stored linear index, which is filled the same way.
        final BinningIndex.Builder builder = new BinningIndex.Builder(14, 5);
        builder.add(0, 2 * WINDOW + 10, 2 * WINDOW + 10, offset(300), offset(301));
        builder.add(0, 3 * WINDOW, 5 * WINDOW, offset(400), offset(401));
        Assert.assertEquals(loffsetOfBin(builder.build(1).getReference(0), 585), offset(300));
    }

    @Test
    public void testBinThatSmallBinsWereFoldedIntoKeepsTheOffsetOfItsFirstWindow() {
        final BinningIndex.Builder builder = new BinningIndex.Builder(14, 5, true);
        builder.add(0, WINDOW, WINDOW + 1, offset(100), offset(101)); // windows 0-1: 128 kb bin 585
        builder.add(0, WINDOW + 10, WINDOW + 10, offset(101), offset(102)); // 16 kb bin 4682, folded into 585
        final ReferenceBins reference = builder.build(1).getReference(0);
        Assert.assertEquals(reference.getBinCount(), 1);
        Assert.assertEquals(loffsetOfBin(reference, 585), offset(100));
    }

    /** One record per 16 kb window across 4 Mb, so every window's linear-index entry is distinct. */
    private static BinningIndex.Builder oneRecordPerWindow() {
        final BinningIndex.Builder builder = new BinningIndex.Builder(14, 5, true);
        for (int w = 0; w < 256; w++) {
            builder.add(0, w * WINDOW + 1, w * WINDOW + 1, offset(1000 + w), offset(1000 + w) + 1);
        }
        return builder;
    }

    @Test
    public void testEveryLevelsFirstAndLastBinTakesTheOffsetOfItsFirstWindow() {
        for (int level = 5; level >= 1; level--) {
            final int levelStart = ((1 << (3 * level)) - 1) / 7;
            final int windowsPerBin = 1 << (3 * (5 - level));
            // Bin 0 of every level; and, for levels whose bins fit in the 4 Mb, the last bin that does.
            final int lastBinUnder4Mb = 256 / windowsPerBin - 1;
            for (final int binInLevel : lastBinUnder4Mb > 0 ? new int[] {0, lastBinUnder4Mb} : new int[] {0}) {
                final BinningIndex.Builder builder = oneRecordPerWindow();
                // A record filling the bin exactly lands in it, after the per-window records in file order.
                final int binStart = binInLevel * windowsPerBin * WINDOW + 1;
                builder.add(0, binStart, binStart + windowsPerBin * WINDOW - 1, offset(5000), offset(5001));
                Assert.assertEquals(
                        loffsetOfBin(builder.build(1).getReference(0), levelStart + binInLevel),
                        offset(1000 + binInLevel * windowsPerBin),
                        "level " + level + " bin " + binInLevel);
            }
        }
    }

    @Test
    public void testIndexLoadedWithALinearIndexDerivesLoffsetsFromIt() {
        final BinningIndex.Builder builder = new BinningIndex.Builder(14, 5);
        builder.add(0, 10, 10, offset(100), offset(101));
        builder.add(0, 3 * WINDOW, 5 * WINDOW, offset(200), offset(201));
        final BinningIndex built = builder.build(1);

        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final BinaryCodec out = new BinaryCodec(bytes);
        built.writeBaiLayout(out);
        out.close();
        final BinningIndex loaded =
                BinningIndex.readBaiLayout(new BinaryCodec(new ByteArrayInputStream(bytes.toByteArray())), 1, 14, 5);
        Assert.assertEquals(loaded, built);
        Assert.assertEquals(loffsetOfBin(loaded.getReference(0), 585), offset(100));
    }
}
