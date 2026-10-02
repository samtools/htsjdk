package htsjdk.samtools.cram.compression;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.range.RangeDecode;
import htsjdk.samtools.cram.compression.range.RangeEncode;
import htsjdk.samtools.cram.compression.range.RangeParams;
import htsjdk.samtools.cram.compression.rans.RANSNx16Decode;
import htsjdk.samtools.cram.compression.rans.RANSNx16Encode;
import htsjdk.samtools.cram.compression.rans.RANSNx16Params;
import java.nio.ByteBuffer;
import java.util.Random;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests for the STRIPE helper methods in CompressionUtils and end-to-end STRIPE round-trip tests.
 * Test cases ported from noodles: noodles-cram/src/codecs/aac/encode/stripe.rs
 *                            and: noodles-cram/src/codecs/rans_nx16/encode/stripe.rs
 */
public class CompressionUtilsTest extends HtsjdkTest {

    @Test
    public void symbolCountsCountsEveryByteIncludingThoseAfterTheLastFour() {
        final int[] counts = CompressionUtils.symbolCounts(new byte[] {5, 5, (byte) 200, 0, 5, (byte) 200, 5});
        Assert.assertEquals(counts[5], 4);
        Assert.assertEquals(counts[200], 2);
        Assert.assertEquals(counts[0], 1);
        Assert.assertEquals(java.util.Arrays.stream(counts).sum(), 7);
    }

    /** Packs {@code values} as the rANS and Range encoders do: each distinct value gets its rank as its code. */
    private static byte[] pack(final byte[] values, final ByteBuffer header) {
        final int[] counts = CompressionUtils.symbolCounts(values);
        final int[] codes = new int[256];
        int numSymbols = 0;
        for (int s = 0; s < 256; s++) {
            if (counts[s] > 0) codes[s] = numSymbols++;
        }
        return CompressionUtils.encodePack(values, header, counts, codes, numSymbols);
    }

    @Test
    public void twoSymbolsPackEightValuesToAByteFirstValueLowest() {
        // codes 0 1 1 0 0 0 0 1 | 1
        final byte[] values = {10, 20, 20, 10, 10, 10, 10, 20, 20};
        Assert.assertEquals(pack(values, ByteBuffer.allocate(16)), new byte[] {(byte) 0b10000110, 1});
    }

    @Test
    public void fourSymbolsPackFourValuesToAByteFirstValueLowest() {
        // codes 0 1 2 3 | 1
        final byte[] values = {10, 20, 30, 40, 20};
        Assert.assertEquals(pack(values, ByteBuffer.allocate(16)), new byte[] {(byte) 0b11100100, 1});
    }

    @Test
    public void sixteenSymbolsPackTwoValuesToAByteFirstValueLowest() {
        final byte[] values = new byte[17];
        for (int i = 0; i < 16; i++) values[i] = (byte) (i * 3);
        values[16] = 21; // code 7
        final byte[] packed = pack(values, ByteBuffer.allocate(64));
        Assert.assertEquals(packed.length, 9);
        Assert.assertEquals(packed[0], (byte) 0x10); // codes 0 and 1
        Assert.assertEquals(packed[7], (byte) 0xFE); // codes 14 and 15
        Assert.assertEquals(packed[8], (byte) 7);
    }

    @Test
    public void oneSymbolPacksToNothing() {
        Assert.assertEquals(pack(new byte[] {9, 9, 9}, ByteBuffer.allocate(16)), new byte[0]);
    }

    @Test
    public void packWritesTheSymbolCountTheSymbolsAndThePackedLength() {
        final ByteBuffer header = ByteBuffer.allocate(16);
        pack(new byte[] {30, 10, 20, 10, 30}, header);
        Assert.assertEquals(java.util.Arrays.copyOf(header.array(), header.position()), new byte[] {3, 10, 20, 30, 2});
    }

    @Test
    public void packedValuesUnpackToTheInputForEveryWidthAndTailLength() {
        final Random random = new Random(7);
        for (final int numSymbols : new int[] {2, 3, 4, 5, 16}) {
            for (int length = 1; length <= 20; length++) {
                final byte[] values = new byte[length];
                for (int i = 0; i < length; i++) values[i] = (byte) random.nextInt(numSymbols);
                final ByteBuffer header = ByteBuffer.allocate(64);
                final byte[] packed = pack(values, header);
                final int[] counts = CompressionUtils.symbolCounts(values);
                final byte[] symbols = new byte[256];
                int distinct = 0;
                for (int s = 0; s < 256; s++) {
                    if (counts[s] > 0) symbols[distinct++] = (byte) s;
                }
                final ByteBuffer unpacked =
                        CompressionUtils.decodePack(CompressionUtils.wrap(packed), symbols, distinct, length);
                Assert.assertEquals(unpacked.array(), values, numSymbols + " symbols, length " + length);
            }
        }
    }

    @Test
    public void testBuildStripeUncompressedSizesEvenlyDivisible() {
        // 12 bytes / 4 streams = 3 each
        final int[] sizes = CompressionUtils.buildStripeUncompressedSizes(12);
        Assert.assertEquals(sizes, new int[] {3, 3, 3, 3});
    }

    @Test
    public void testBuildStripeUncompressedSizesWithRemainder() {
        // 13 bytes / 4 streams = 3+1, 3, 3, 3 (first stream gets extra byte)
        // Ported from noodles test_build_uncompressed_sizes (with N=4 instead of N=3)
        final int[] sizes = CompressionUtils.buildStripeUncompressedSizes(13);
        Assert.assertEquals(sizes, new int[] {4, 3, 3, 3});
    }

    @Test
    public void testBuildStripeUncompressedSizesRemainder2() {
        // 14 bytes / 4 streams = 4, 4, 3, 3
        final int[] sizes = CompressionUtils.buildStripeUncompressedSizes(14);
        Assert.assertEquals(sizes, new int[] {4, 4, 3, 3});
    }

    @Test
    public void testBuildStripeUncompressedSizesRemainder3() {
        // 15 bytes / 4 streams = 4, 4, 4, 3
        final int[] sizes = CompressionUtils.buildStripeUncompressedSizes(15);
        Assert.assertEquals(sizes, new int[] {4, 4, 4, 3});
    }

    @Test
    public void testBuildStripeUncompressedSizesSmall() {
        // 1 byte = only first stream gets it
        final int[] sizes = CompressionUtils.buildStripeUncompressedSizes(1);
        Assert.assertEquals(sizes, new int[] {1, 0, 0, 0});
    }

    @Test
    public void testStripeTranspose() {
        // Ported from noodles test_transpose, adapted for N=4 streams
        // Input: "aA1!bB2@cC3#dD4$" (16 bytes, 4 per stream)
        // Stream 0: a, b, c, d (positions 0, 4, 8, 12)
        // Stream 1: A, B, C, D (positions 1, 5, 9, 13)
        // Stream 2: 1, 2, 3, 4 (positions 2, 6, 10, 14)
        // Stream 3: !, @, #, $ (positions 3, 7, 11, 15)
        final byte[] input = "aA1!bB2@cC3#dD4$".getBytes();
        final int[] sizes = CompressionUtils.buildStripeUncompressedSizes(input.length);
        final ByteBuffer[] chunks = CompressionUtils.stripeTranspose(CompressionUtils.wrap(input), sizes);

        Assert.assertEquals(chunks.length, 4);
        Assert.assertEquals(toBytes(chunks[0]), "abcd".getBytes());
        Assert.assertEquals(toBytes(chunks[1]), "ABCD".getBytes());
        Assert.assertEquals(toBytes(chunks[2]), "1234".getBytes());
        Assert.assertEquals(toBytes(chunks[3]), "!@#$".getBytes());
    }

    @Test
    public void testStripeTransposeWithRemainder() {
        // 13 bytes: "aA1!bB2@cC3#d"
        // Sizes: [4, 3, 3, 3]
        // Stream 0: positions 0, 4, 8, 12 = a, b, c, d
        // Stream 1: positions 1, 5, 9     = A, B, C
        // Stream 2: positions 2, 6, 10    = 1, 2, 3
        // Stream 3: positions 3, 7, 11    = !, @, #
        final byte[] input = "aA1!bB2@cC3#d".getBytes();
        final int[] sizes = CompressionUtils.buildStripeUncompressedSizes(input.length);
        final ByteBuffer[] chunks = CompressionUtils.stripeTranspose(CompressionUtils.wrap(input), sizes);

        Assert.assertEquals(chunks.length, 4);
        Assert.assertEquals(toBytes(chunks[0]), "abcd".getBytes());
        Assert.assertEquals(toBytes(chunks[1]), "ABC".getBytes());
        Assert.assertEquals(toBytes(chunks[2]), "123".getBytes());
        Assert.assertEquals(toBytes(chunks[3]), "!@#".getBytes());
    }

    @Test
    public void testBuildStripeUncompressedSizesZero() {
        final int[] sizes = CompressionUtils.buildStripeUncompressedSizes(0);
        Assert.assertEquals(sizes, new int[] {0, 0, 0, 0});
    }

    @Test
    public void testBuildStripeUncompressedSizesTwoBytes() {
        final int[] sizes = CompressionUtils.buildStripeUncompressedSizes(2);
        Assert.assertEquals(sizes, new int[] {1, 1, 0, 0});
    }

    @Test
    public void testBuildStripeUncompressedSizesThreeBytes() {
        final int[] sizes = CompressionUtils.buildStripeUncompressedSizes(3);
        Assert.assertEquals(sizes, new int[] {1, 1, 1, 0});
    }

    @Test
    public void testBuildStripeUncompressedSizesFourBytes() {
        // Exactly 4 bytes = 1 per stream
        final int[] sizes = CompressionUtils.buildStripeUncompressedSizes(4);
        Assert.assertEquals(sizes, new int[] {1, 1, 1, 1});
    }

    // --- End-to-end STRIPE round-trip tests for Range and rANS Nx16 codecs ---

    @DataProvider(name = "stripeRoundTripData")
    public Object[][] stripeRoundTripData() {
        final Random random = new Random(42);
        final byte[] large = new byte[100_000];
        random.nextBytes(large);

        final byte[] allSame = new byte[256];
        java.util.Arrays.fill(allSame, (byte) 42);

        return new Object[][] {
            {"single byte", new byte[] {0x42}},
            {"two bytes", new byte[] {0x01, 0x02}},
            {"three bytes", new byte[] {0x01, 0x02, 0x03}},
            {"four bytes", new byte[] {0x01, 0x02, 0x03, 0x04}},
            {"five bytes", new byte[] {0x01, 0x02, 0x03, 0x04, 0x05}},
            {"all same bytes", allSame},
            {"large random", large},
        };
    }

    @Test(dataProvider = "stripeRoundTripData")
    public void testRangeStripeRoundTrip(final String description, final byte[] data) {
        final RangeEncode encoder = new RangeEncode();
        final RangeDecode decoder = new RangeDecode();
        final ByteBuffer input = CompressionUtils.wrap(data);
        final ByteBuffer compressed = encoder.compress(input, new RangeParams(RangeParams.STRIPE_FLAG_MASK));
        final ByteBuffer decompressed = decoder.uncompress(compressed);
        input.rewind();
        Assert.assertEquals(decompressed, input, "Range STRIPE round-trip failed for: " + description);
    }

    @Test(dataProvider = "stripeRoundTripData")
    public void testRangeStripeOrder1RoundTrip(final String description, final byte[] data) {
        final RangeEncode encoder = new RangeEncode();
        final RangeDecode decoder = new RangeDecode();
        final ByteBuffer input = CompressionUtils.wrap(data);
        final ByteBuffer compressed =
                encoder.compress(input, new RangeParams(RangeParams.STRIPE_FLAG_MASK | RangeParams.ORDER_FLAG_MASK));
        final ByteBuffer decompressed = decoder.uncompress(compressed);
        input.rewind();
        Assert.assertEquals(decompressed, input, "Range STRIPE+ORDER1 round-trip failed for: " + description);
    }

    @Test(dataProvider = "stripeRoundTripData")
    public void testRansNx16StripeRoundTrip(final String description, final byte[] data) {
        final RANSNx16Encode encoder = new RANSNx16Encode();
        final RANSNx16Decode decoder = new RANSNx16Decode();
        final byte[] compressed = encoder.compress(data, new RANSNx16Params(RANSNx16Params.STRIPE_FLAG_MASK));
        final byte[] decompressed = decoder.uncompress(compressed);
        Assert.assertEquals(decompressed, data, "rANS Nx16 STRIPE round-trip failed for: " + description);
    }

    @Test(dataProvider = "stripeRoundTripData")
    public void testRansNx16StripeOrder1RoundTrip(final String description, final byte[] data) {
        final RANSNx16Encode encoder = new RANSNx16Encode();
        final RANSNx16Decode decoder = new RANSNx16Decode();
        final byte[] compressed = encoder.compress(
                data, new RANSNx16Params(RANSNx16Params.STRIPE_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK));
        final byte[] decompressed = decoder.uncompress(compressed);
        Assert.assertEquals(decompressed, data, "rANS Nx16 STRIPE+ORDER1 round-trip failed for: " + description);
    }

    private static byte[] toBytes(final ByteBuffer buf) {
        final byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        buf.rewind();
        return bytes;
    }
}
