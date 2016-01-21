package htsjdk.samtools.cram.encoding.huffman.codec;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by vadim on 22/04/2015.
 */
public class HuffmanParamsCalculatorTest {

    @Test
    public void test_add_1() {
        final HuffmanParamsCalculator c = new HuffmanParamsCalculator();
        c.addOne(1);
        c.calculate();

        final int[] values = c.getValues();
        final int[] bitLens = c.getBitLens();

        Assert.assertEquals(1, values.length);
        Assert.assertEquals(1, bitLens.length);

        Assert.assertEquals(1, values[0]);
        Assert.assertEquals(0, bitLens[0]);
    }

    @Test
    public void test_add_1_1() {
        final HuffmanParamsCalculator c = new HuffmanParamsCalculator();
        c.add(1, 1);
        c.calculate();

        final int[] values = c.getValues();
        final int[] bitLens = c.getBitLens();

        Assert.assertEquals(1, values.length);
        Assert.assertEquals(1, bitLens.length);

        Assert.assertEquals(1, values[0]);
        Assert.assertEquals(0, bitLens[0]);
    }

    @Test
    public void test_add_many() {
        final HuffmanParamsCalculator c = new HuffmanParamsCalculator();
        c.addOne(1);
        c.addOne(2);
        c.addOne(2);
        c.addOne(3);
        c.addOne(3);
        c.addOne(3);
        c.calculate();

        final Map<Integer, Integer> expectations_Value2BitLen = new HashMap<Integer, Integer>();
        expectations_Value2BitLen.put(1, 2);
        expectations_Value2BitLen.put(2, 2);
        expectations_Value2BitLen.put(3, 1);

        final int[] values = c.getValues();
        final int[] bitLens = c.getBitLens();

        Assert.assertEquals(3, values.length);
        Assert.assertEquals(3, bitLens.length);

        for (int i = 0; i < values.length; i++) {
            final int value = values[i];
            Assert.assertTrue(expectations_Value2BitLen.containsKey(value));

            final int bitLen = expectations_Value2BitLen.get(value);
            Assert.assertEquals(bitLen, bitLens[i], i + ": " + value);

            expectations_Value2BitLen.remove(value);
        }

        Assert.assertTrue(expectations_Value2BitLen.isEmpty());
    }
}
