package htsjdk.samtools.cram.io;

import htsjdk.HtsjdkTest;
import org.testng.annotations.DataProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IOTestCases extends HtsjdkTest {

    private static <T> Object[][] asDataProvider(List<T> list) {
        Object[][] params = new Object[list.size()][] ;
        for (int i=0; i<params.length; i++)
            params[i] = new Object[]{list.get(i)} ;
        return params;
    }

    static List<Integer> int32Tests() {
        List<Integer> list = new ArrayList<Integer>();

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

    @DataProvider(name = "testInt32Arrays")
    public static Object[][] testValues32() {
        List<Integer> int32Tests = IOTestCases.int32Tests();
        List<Integer> shuffled = new ArrayList<>(int32Tests);
        Collections.shuffle(shuffled);

        return new Object[][]{
                {int32Tests},
                {shuffled}
        };
    }

    static List<Long> int64Tests() {
        List<Long> list = new ArrayList<Long>() ;

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

    @DataProvider(name = "testInt64Arrays")
    public static Object[][] testValues64() {
        List<Long> int64Tests = IOTestCases.int64Tests();
        List<Long> shuffled = new ArrayList<>(int64Tests);
        Collections.shuffle(shuffled);

        return new Object[][]{
                {int64Tests},
                {shuffled}
        };
    }
}
