package htsjdk.samtools.cram.io;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.TestUtil;
import org.testng.annotations.DataProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class IOTestCases extends HtsjdkTest {
    private static final Random RANDOM = new Random(TestUtil.RANDOM_SEED);

    @DataProvider(name = "littleEndianTests32")
    public static Object[][] littleEndianTests32() {
        return new Object[][] {
                {1, new byte[]{1, 0, 0, 0}},                // 0x01
                {127, new byte[]{127, 0, 0, 0}},            // 0x7F
                {128, new byte[]{-128, 0, 0, 0}},           // 0x80
                {129, new byte[]{-127, 0, 0, 0}},           // 0x81
                {255, new byte[]{-1, 0, 0, 0}},             // 0xFF
                {256, new byte[]{0, 1, 0, 0}},              // 0x0100
                {257, new byte[]{1, 1, 0, 0}},              // 0x0101
                {65535, new byte[]{-1, -1, 0, 0}},          // 0xFFFF
                {65536, new byte[]{0, 0, 1, 0}},            // 0x010000
                {16777216, new byte[]{0, 0, 0, 1}},         // 0x01000000
                {2147483647, new byte[]{-1, -1, -1, 127}},  // 0x7FFFFFFF
                {-2147483648, new byte[]{0, 0, 0, -128}},   // 0x80000000
                {-1, new byte[]{-1, -1, -1, -1}}            // 0xFFFFFFFF
        };
    }

    private static <T> Object[][] asDataProvider(final List<T> list) {
        final Object[][] params = new Object[list.size()][];
        for (int i = 0; i < params.length; i++)
            params[i] = new Object[]{list.get(i)};
        return params;
    }

    private static List<Byte> byteTests() {
        final List<Byte> list = new ArrayList<>();

        // basics:
        list.add((byte)0);
        list.add((byte)1);
        list.add((byte)-1);

        // scan with bits:
        for (int i = 0; i < 7; i++) {
            list.add((byte)((1 << i) - 2));
            list.add((byte)((1 << i) - 1));
            list.add((byte)(1 << i));
            list.add((byte)(-(1 << i) + 2));
            list.add((byte)(-(1 << i) + 1));
            list.add((byte)-(1 << i));
        }

        // special cases:
        list.add(Byte.MAX_VALUE);
        list.add(Byte.MIN_VALUE);

        return list;
    }

    @DataProvider(name = "testByteLists")
    public static Object[][] testByteValues() {
        final List<Byte> byteTests = IOTestCases.byteTests();
        final List<Byte> shuffled = new ArrayList<>(byteTests);
        Collections.shuffle(shuffled, RANDOM);

        return new Object[][]{
                {byteTests},
                {shuffled}
        };
    }

    @DataProvider(name = "testPositiveByteLists")
    public static Object[][] testPositiveByteValues() {
        final List<Byte> positiveByteTests = IOTestCases.byteTests()
                .stream()
                .filter(b -> b >= 0)
                .collect(Collectors.toList());

        final List<Byte> shuffled = new ArrayList<>(positiveByteTests);
        Collections.shuffle(shuffled, RANDOM);

        return new Object[][]{
                {positiveByteTests},
                {shuffled}
        };
    }

    @DataProvider(name = "testByteArrays")
    public static Object[][] testByteArrayValues() {
        final List<Byte> byteTestsList = IOTestCases.byteTests();
        final List<Byte> shuffledList = new ArrayList<>(byteTestsList);
        Collections.shuffle(shuffledList, RANDOM);

        final byte[] byteTests = new byte[byteTestsList.size()];
        for(int i = 0; i < byteTestsList.size(); i++) {
            byteTests[i] = byteTestsList.get(i);
        }

        final byte[] shuffled = new byte[shuffledList.size()];
        for(int i = 0; i < shuffledList.size(); i++) {
            shuffled[i] = shuffledList.get(i);
        }

        return new Object[][]{
                {byteTests},
                {shuffled}
        };
    }

    private static List<Integer> int32Tests() {
        final List<Integer> list = new ArrayList<>();

        // basics:
        list.add(0);
        list.add(1);
        list.add(127);
        list.add(128);
        list.add(255);
        list.add(256);
        list.add(-1);

        // scan with bits:
        for (int i = 0; i <= 32; i++) {
            list.add((1 << i) - 2);
            list.add((1 << i) - 1);
            list.add(1 << i);
            list.add((1 << i) + 1);
            list.add((1 << i) + 1);
        }

        // special cases:
        list.add(Integer.MAX_VALUE);
        list.add(Integer.MIN_VALUE);
        list.add(268435456);

        return list;
    }

    @DataProvider(name = "testInt32")
    public static Object[][] testInt32() {
        return asDataProvider(IOTestCases.int32Tests());
    }

    @DataProvider(name = "testInt32Lists")
    public static Object[][] testValues32() {
        final List<Integer> int32Tests = IOTestCases.int32Tests();
        final List<Integer> shuffled = new ArrayList<>(int32Tests);
        Collections.shuffle(shuffled, RANDOM);

        return new Object[][]{
                {int32Tests},
                {shuffled}
        };
    }

    // Motivation for this test case: we were incorrectly encoding a few CRAM record
    // fields using the wrong data type.  This was surprising, since we would
    // expect catastrophic "frame shift" type failures from this.
    //
    // Instead, we noticed that this conflict would cause no problems in a few specific
    // cases, including:
    //
    // External Integer vs. External Long, if all values are in the range (0 to 0x0F FF FF FF)
    // because ITF8 and LTF8 are equivalent over that range.

    private static List<Integer> uint28Tests() {
        final int max = 1 << 28;
        return int32Tests()
                .stream()
                .filter(i -> i >= 0 && i < max)  // valid range is (0 to 0x0F FF FF FF)
                .collect(Collectors.toList());
    }

    @DataProvider(name = "testUint28Lists")
    public static Object[][] testValuesU28() {
        final List<Integer> uint28Tests = IOTestCases.uint28Tests();
        final List<Integer> shuffled = new ArrayList<>(uint28Tests);
        Collections.shuffle(shuffled, RANDOM);

        return new Object[][]{
                {uint28Tests},
                {shuffled}
        };
    }

    private static List<Long> int64Tests() {
        final List<Long> list = new ArrayList<>() ;

        // basics:
        list.add(0L);
        list.add(0L);
        list.add(1L);
        list.add(127L);
        list.add(128L);
        list.add(255L);
        list.add(256L);

        // scan with bits:
        for (int i = 0; i <= 64; i++) {
            list.add((1L << i) - 2);
            list.add((1L << i) - 1);
            list.add(1L << i);
            list.add((1L << i) + 1);
            list.add((1L << i) + 1);
        }

        // special cases:
        list.add(Long.MAX_VALUE);
        list.add(Long.MIN_VALUE);
        list.add(1125899906842622L) ;
        list.add(1125899906842622L);
        list.add(562949953421312L);
        list.add(4294967296L);
        list.add(268435456L);
        list.add(2147483648L);
        list.add(-1L);

        return list;
    }

    @DataProvider(name = "testInt64")
    public static Object[][] testInt64() {
        return asDataProvider(IOTestCases.int64Tests());
    }

    @DataProvider(name = "testInt64Lists")
    public static Object[][] testValues64() {
        final List<Long> int64Tests = IOTestCases.int64Tests();
        final List<Long> shuffled = new ArrayList<>(int64Tests);
        Collections.shuffle(shuffled, RANDOM);

        return new Object[][]{
                {int64Tests},
                {shuffled}
        };
    }
}
