package htsjdk.samtools.cram.compression;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.fqzcomp.FQZCompDecode;
import htsjdk.samtools.cram.compression.fqzcomp.FQZCompEncode;
import htsjdk.samtools.cram.compression.nametokenisation.NameTokenisationDecode;
import htsjdk.samtools.cram.compression.nametokenisation.NameTokenisationEncode;
import htsjdk.samtools.cram.compression.range.*;
import htsjdk.samtools.cram.compression.rans.*;
import htsjdk.samtools.util.TestUtil;
import java.nio.ByteBuffer;
import java.util.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Property-based roundtrip tests for all CRAM codecs. Verifies that {@code decode(encode(X)) == X}
 * across a variety of input data patterns designed to exercise edge cases and boundary conditions
 * in each codec's implementation. Complements the existing per-codec unit tests by focusing on
 * data-pattern coverage rather than parameter-combination coverage.
 *
 * <p>Encoder/decoder instances are shared across test cases to avoid excessive memory allocation.
 * !!!This precludes running these tests in PARALLEL!!!
 */
@Test(singleThreaded = true)
public class CodecRoundTripPropertyTest extends HtsjdkTest {
    private final Random random = new Random(TestUtil.RANDOM_SEED);

    // Shared codec instances to avoid allocating large internal tables per test case
    private final RANS4x8Encode rans4x8Encoder = new RANS4x8Encode();
    private final RANS4x8Decode rans4x8Decoder = new RANS4x8Decode();
    private final RANSNx16Encode ransNx16Encoder = new RANSNx16Encode();
    private final RANSNx16Decode ransNx16Decoder = new RANSNx16Decode();
    private final RangeEncode rangeEncoder = new RangeEncode();
    private final RangeDecode rangeDecoder = new RangeDecode();

    // ---- Data generators that target specific codec edge cases ----

    /** Data with exactly N distinct byte values, for testing PACK boundary (threshold = 16). */
    private byte[] dataWithNDistinctSymbols(final int length, final int nSymbols) {
        final byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (i % nSymbols);
        }
        return data;
    }

    /** Data with long runs of the same value, for testing RLE. */
    private byte[] dataWithLongRuns(final int length, final int runLength) {
        final byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (i / runLength);
        }
        return data;
    }

    /** Uniform random data (maximum entropy, incompressible). */
    private byte[] uniformRandomData(final int length) {
        final byte[] data = new byte[length];
        random.nextBytes(data);
        return data;
    }

    /** Alternating two values with the specified period. */
    private byte[] alternatingData(final int length, final int period) {
        final byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) ((i / period) % 2 == 0 ? 0 : 255);
        }
        return data;
    }

    // Wraps byte[] for display in test output without IntelliJ serializing the full array
    private static class TestData {
        final byte[] data;
        final String description;

        TestData(final byte[] data, final String description) {
            this.data = data;
            this.description = description;
        }

        public String toString() {
            return description;
        }
    }

    // ---- Test data set covering edge-case patterns ----

    private TestData[] buildPropertyTestData() {
        return new TestData[] {
            // Symbol count edge cases for PACK (boundary at 16)
            new TestData(dataWithNDistinctSymbols(500, 1), "1 distinct symbol"),
            new TestData(dataWithNDistinctSymbols(500, 2), "2 distinct symbols"),
            new TestData(dataWithNDistinctSymbols(500, 4), "4 distinct symbols"),
            new TestData(dataWithNDistinctSymbols(500, 15), "15 distinct symbols"),
            new TestData(dataWithNDistinctSymbols(500, 16), "16 distinct symbols (PACK boundary)"),
            new TestData(dataWithNDistinctSymbols(500, 17), "17 distinct symbols (PACK skipped)"),
            new TestData(dataWithNDistinctSymbols(768, 256), "all 256 symbols"),

            // RLE-friendly patterns
            new TestData(dataWithLongRuns(1000, 10), "runs of 10"),
            new TestData(dataWithLongRuns(1000, 100), "runs of 100"),
            new TestData(dataWithLongRuns(1000, 1), "runs of 1 (no RLE benefit)"),

            // Alternating patterns
            new TestData(alternatingData(500, 1), "alternating every byte"),
            new TestData(alternatingData(500, 4), "alternating every 4 bytes"),

            // Incompressible data
            new TestData(uniformRandomData(500), "uniform random 500"),
            new TestData(uniformRandomData(5000), "uniform random 5000"),

            // Size edge cases
            new TestData(new byte[] {42}, "single byte"),
            new TestData(new byte[] {0, 0}, "two zeros"),
            new TestData(new byte[0], "empty"),
        };
    }

    // ---- RANS4x8 property tests ----

    @DataProvider(name = "rans4x8Properties")
    public Object[][] rans4x8Properties() {
        final TestData[] data = buildPropertyTestData();
        final RANSParams.ORDER[] orders = {RANSParams.ORDER.ZERO, RANSParams.ORDER.ONE};
        final List<Object[]> cases = new ArrayList<>();
        for (final TestData td : data) {
            for (final RANSParams.ORDER order : orders) {
                cases.add(new Object[] {td, new RANS4x8Params(order)});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "rans4x8Properties")
    public void testRANS4x8RoundTrip(final TestData td, final RANS4x8Params params) {
        final byte[] compressed = rans4x8Encoder.compress(td.data, params);
        final byte[] decompressed = rans4x8Decoder.uncompress(compressed);
        Assert.assertEquals(decompressed, td.data, "RANS4x8 roundtrip failed for: " + td + " with " + params);
    }

    // ---- RANSNx16 property tests ----

    // Key flag combinations that exercise different code paths
    private static final int[] RANS_NX16_KEY_FLAGS = {
        0x00, // plain order-0
        RANSNx16Params.ORDER_FLAG_MASK, // order-1
        RANSNx16Params.PACK_FLAG_MASK, // PACK only
        RANSNx16Params.PACK_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK, // PACK + order-1
        RANSNx16Params.RLE_FLAG_MASK, // RLE only
        RANSNx16Params.RLE_FLAG_MASK | RANSNx16Params.PACK_FLAG_MASK, // RLE + PACK
        RANSNx16Params.STRIPE_FLAG_MASK, // STRIPE
        RANSNx16Params.N32_FLAG_MASK, // N32 (32-way interleave)
        RANSNx16Params.CAT_FLAG_MASK, // CAT (uncompressed)
    };

    @DataProvider(name = "ransNx16Properties")
    public Object[][] ransNx16Properties() {
        final TestData[] data = buildPropertyTestData();
        final List<Object[]> cases = new ArrayList<>();
        for (final TestData td : data) {
            for (final int flags : RANS_NX16_KEY_FLAGS) {
                cases.add(new Object[] {td, new RANSNx16Params(flags)});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "ransNx16Properties")
    public void testRANSNx16RoundTrip(final TestData td, final RANSNx16Params params) {
        final byte[] compressed = ransNx16Encoder.compress(td.data, params);
        final byte[] decompressed = ransNx16Decoder.uncompress(compressed);
        Assert.assertEquals(decompressed, td.data, "RANSNx16 roundtrip failed for: " + td + " with " + params);
    }

    // ---- Range codec property tests ----

    private static final int[] RANGE_KEY_FLAGS = {
        0x00, // plain order-0
        RangeParams.ORDER_FLAG_MASK, // order-1
        RangeParams.PACK_FLAG_MASK, // PACK only
        RangeParams.PACK_FLAG_MASK | RangeParams.ORDER_FLAG_MASK, // PACK + order-1
        RangeParams.RLE_FLAG_MASK, // RLE only
        RangeParams.RLE_FLAG_MASK | RangeParams.PACK_FLAG_MASK, // RLE + PACK
        RangeParams.STRIPE_FLAG_MASK, // STRIPE
        RangeParams.CAT_FLAG_MASK, // CAT (uncompressed)
        RangeParams.EXT_FLAG_MASK, // EXT (bzip2 fallback)
    };

    @DataProvider(name = "rangeProperties")
    public Object[][] rangeProperties() {
        final TestData[] data = buildPropertyTestData();
        final List<Object[]> cases = new ArrayList<>();
        for (final TestData td : data) {
            for (final int flags : RANGE_KEY_FLAGS) {
                cases.add(new Object[] {td, new RangeParams(flags)});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "rangeProperties")
    public void testRangeRoundTrip(final TestData td, final RangeParams params) {
        final ByteBuffer input = CompressionUtils.wrap(td.data);
        final ByteBuffer compressed = rangeEncoder.compress(input, params);
        final ByteBuffer decompressed = rangeDecoder.uncompress(compressed);
        input.rewind();
        Assert.assertEquals(decompressed, input, "Range roundtrip failed for: " + td + " with " + params);
    }

    // ---- FQZComp property tests ----

    @DataProvider(name = "fqzProperties")
    public Object[][] fqzProperties() {
        return new Object[][] {
            // Quality patterns targeting FQZ's model selection
            {"single value repeated", fillQuals(1000, (byte) 30), makeRecordLengths(10, 100)},
            {"two alternating values", twoValueQuals(1000, 10, 30), makeRecordLengths(10, 100)},
            {"ascending sawtooth", sawtoothQuals(1000, 42), makeRecordLengths(10, 100)},
            {"descending quality per record", descendingQuals(10, 10), makeRecordLengths(10, 10)},
            {"all zero quals", fillQuals(500, (byte) 0), makeRecordLengths(5, 100)},
            {"max quality (93)", fillQuals(500, (byte) 93), makeRecordLengths(5, 100)},
            {"single record single byte", new byte[] {20}, new int[] {1}},
            {"variable length records", sawtoothQuals(55, 42), new int[] {10, 5, 20, 15, 5}},
        };
    }

    @Test(dataProvider = "fqzProperties")
    public void testFQZCompRoundTrip(final String description, final byte[] qualityData, final int[] recordLengths) {
        final ByteBuffer input = CompressionUtils.wrap(qualityData);
        final ByteBuffer compressed = new FQZCompEncode().compress(input, recordLengths);
        final ByteBuffer decompressed = FQZCompDecode.uncompress(compressed);
        input.rewind();
        Assert.assertEquals(decompressed, input, "FQZComp roundtrip failed for: " + description);
    }

    // ---- NameTokenisation property tests ----

    @DataProvider(name = "nameTokenProperties")
    public Object[][] nameTokenProperties() {
        final String sep = new String(new byte[] {NameTokenisationDecode.NAME_SEPARATOR});
        return new Object[][] {
            {"single simple name", ("readA" + sep).getBytes(), true},
            {"numeric-only names", ("123" + sep + "456" + sep + "789" + sep).getBytes(), false},
            {
                "names with varying token counts",
                ("A:1:2:3:4" + sep + "B:5" + sep + "C:6:7:8:9:10" + sep).getBytes(),
                true
            },
            {"names with empty-ish tokens", (":::" + sep + ":::" + sep).getBytes(), false},
            {"long names", (longName(200) + sep + longName(200) + sep).getBytes(), true},
            {"many identical names", repeatName("dup:1:2:3", 50, sep).getBytes(), false},
            {"incrementing flowcell-style names", buildFlowcellNames(100, sep).getBytes(), true},
            {
                "incrementing flowcell-style names (Golomb)",
                buildFlowcellNames(100, sep).getBytes(),
                false
            },
        };
    }

    @Test(dataProvider = "nameTokenProperties")
    public void testNameTokenisationRoundTrip(final String description, final byte[] nameData, final boolean useArith) {
        final NameTokenisationEncode encoder = new NameTokenisationEncode();
        final NameTokenisationDecode decoder = new NameTokenisationDecode();

        final ByteBuffer input = ByteBuffer.wrap(nameData);
        final ByteBuffer compressed = encoder.compress(input, useArith, NameTokenisationDecode.NAME_SEPARATOR);
        final ByteBuffer decompressed =
                CompressionUtils.wrap(decoder.uncompress(compressed, NameTokenisationDecode.NAME_SEPARATOR));
        input.rewind();
        Assert.assertEquals(
                decompressed,
                input,
                "NameTokenisation roundtrip failed for: " + description + " (arith=" + useArith + ")");
    }

    // ---- Quality score helpers ----

    private static byte[] fillQuals(final int length, final byte value) {
        final byte[] quals = new byte[length];
        Arrays.fill(quals, value);
        return quals;
    }

    private static byte[] twoValueQuals(final int length, final int value1, final int value2) {
        final byte[] quals = new byte[length];
        for (int i = 0; i < length; i++) {
            quals[i] = (byte) (i % 2 == 0 ? value1 : value2);
        }
        return quals;
    }

    private static byte[] sawtoothQuals(final int length, final int maxVal) {
        final byte[] quals = new byte[length];
        for (int i = 0; i < length; i++) {
            quals[i] = (byte) (i % maxVal);
        }
        return quals;
    }

    private static byte[] descendingQuals(final int numRecords, final int recordLength) {
        final byte[] quals = new byte[numRecords * recordLength];
        for (int r = 0; r < numRecords; r++) {
            for (int i = 0; i < recordLength; i++) {
                quals[r * recordLength + i] = (byte) (40 - (i * 40 / recordLength));
            }
        }
        return quals;
    }

    private static int[] makeRecordLengths(final int numRecords, final int recordLength) {
        final int[] lengths = new int[numRecords];
        Arrays.fill(lengths, recordLength);
        return lengths;
    }

    // ---- Name helpers ----

    private static String longName(final int length) {
        final StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('A' + (i % 26)));
        }
        return sb.toString();
    }

    private static String repeatName(final String name, final int count, final String sep) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(name).append(sep);
        }
        return sb.toString();
    }

    private static String buildFlowcellNames(final int count, final String sep) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(String.format("HWUSI-EAS100R:6:73:%d:%d", 1000 + i, 20000 + i * 3))
                    .append(sep);
        }
        return sb.toString();
    }
}
