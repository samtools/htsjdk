package htsjdk.samtools.util;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;

import static java.lang.Math.abs;
import static java.lang.StrictMath.pow;

public class HistogramTest {

    @Test(dataProvider = "histogramData")
    public void testHistogramFunctions(final int[] values, final double mean, final double stdev, final Integer trimByWidth) {
        final Histogram<Integer> histo = new Histogram<>();
        for (int value : values) {
            histo.increment(value);
        }

        if (trimByWidth != null) histo.trimByWidth(trimByWidth);
        final double m = histo.getMean();
        final double sd = histo.getStandardDeviation();

        Assert.assertEquals(round(mean), round(m), "Means are not equal");
        Assert.assertEquals(round(stdev), round(sd), "Stdevs are not equal");
    }

    @DataProvider(name = "histogramData")
    public Object[][] histogramData() {
        return new Object[][] {
            new Object[] {new int[] {1,2,3,4,5,6,7,8,9,10} , 5.5d, 3.027650d, null },
            new Object[] {new int[] {1,2,2,3,3,3,4,4,4,4,5,5,5,5,5,6,6,6,6,6,6,7,7,7,7,7,7,7,8,8,8,8,8,8,8,8,9,9,9,9,9,9,9,9,9}, 6.333333d, 2.236068d, null  },
            new Object[] {new int[] {-5, -4, -3, -2, -1,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15}, 5d, 6.204837d, null  },
                new Object[] {new int[] {1,2,3,4,5,6,7,8,9,10, 11, 11, 12, 100, 1000} , 5.5d, 3.027650d, 10 },
                new Object[] {new int[] {1,2,2,3,3,3,4,4,4,4,5,5,5,5,5,6,6,6,6,6,6,7,7,7,7,7,7,7,8,8,8,8,8,8,8,8,9,9,9,9,9,9,9,9,9, 20, 20, 21, 25, 25}, 6.333333d, 2.236068d, 11  },
                new Object[] {new int[] {-5, -4, -3, -2, -1,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 101, 102, 103, 200, 2000}, 5d, 6.204837d, 20  }
        };
    }

    @Test
    public void testGeometricMean() {
        final int[] is = {4,4,4,4,5,5,5,5,6,6,6,6,6,6,7,7,7,7,7,7,7,8,8,8,8,8,8,8,8};
        final Histogram<Integer> histo = new Histogram<>();
        for (final int i : is) histo.increment(i);
        Assert.assertTrue(abs(histo.getGeometricMean() - 6.216797) < 0.00001);
    }

    @Test
    public void testGetSum() {
        final int[] is = {4,4,5,5,5};
        final Histogram<Integer> histo = new Histogram<>();
        for (final int i : is) histo.increment(i);
        Assert.assertEquals(histo.getSum(), (double)(2*4+3*5), 0.000001);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testGetSumBlowup() {
        final String[] is = {"foo", "foo", "bar"};
        final Histogram<String> histo = new Histogram<>();
        for (final String i : is) histo.increment(i);
        histo.getSum();//blow up
    }

    @Test
    public void testGetSumOfValues() {
        final int[] is = {4,4,5,5,5};
        final Histogram<Integer> histo = new Histogram<>();
        for (final int i : is) histo.increment(i);
        Assert.assertEquals(histo.getSumOfValues(), (double)(2+3), 0.000001);
    }

    @Test
    public void testGetMeanBinSize() {
        final int[] is = {4,4,5,5,5};
        final Histogram<Integer> histo = new Histogram<>();
        for (final int i : is) histo.increment(i);
        Assert.assertEquals(histo.getMeanBinSize(), (2+3)/2.0, 0.000001);
    }

    @Test
    public void testGetStandardDeviationBinSize() {
        final int[] is = {4,4,5,5,5};
        final Histogram<Integer> histo = new Histogram<>();
        for (final int i : is) histo.increment(i);
        final double std = Math.sqrt((pow(2.0-2.5, 2)+pow(3.0-2.5, 2.0))); //sample variance so dividing by 1
        Assert.assertEquals(histo.getStandardDeviationBinSize(histo.getMeanBinSize()), std, 0.000001);
    }

    @Test
    public void testGetKeySet() {
        final int[] is = {4,4,5,5,5};
        final Histogram<Integer> histo = new Histogram<>();
        for (final int i : is) histo.increment(i);

        Assert.assertEquals(histo.keySet(), new HashSet<>(Arrays.asList(4,5)));
    }

    @Test
    public void testLabelsAndComparator() {
        final String[] is = {"a", "B", "a"};
        final Histogram<String> histo = new Histogram<>("FOO", "BAR", String.CASE_INSENSITIVE_ORDER);
        for (final String i : is) histo.increment(i);
        Assert.assertEquals(histo.get("a").getValue(), 2.0);
        Assert.assertEquals(histo.get("B").getValue(), 1.0);
        Assert.assertEquals(histo.get("a").getId(), "a");
        Assert.assertEquals(histo.get("B").getId(), "B");
    }


    @Test
    public void testPrefillBins() {
        final int[] is = {4,4,5,5,5};
        final Histogram<Integer> histo = new Histogram<>();
        Assert.assertEquals(histo.get(4), null);
        Assert.assertEquals(histo.get(5), null);
        histo.prefillBins(4);
        Assert.assertEquals(histo.get(4).getValue(),0.0);
        Assert.assertEquals(histo.get(5), null);

        for (final int i : is) histo.increment(i);
        Assert.assertEquals(histo.get(4).getValue(),2.0);
        Assert.assertEquals(histo.get(5).getValue(),3.0);
    }

    @Test
    public void testLabels() {
        final int[] is = {4,4,5,5,5};
        final Histogram<Integer> histo = new Histogram<>("FOO", "BAR");
        for (final int i : is) histo.increment(i);
        Assert.assertEquals(histo.getBinLabel(),"FOO");
        Assert.assertEquals(histo.getValueLabel(),"BAR");
    }

    @Test
    public void testCopyCtor() {
        final int[] is = {4,4,5,5,5};
        final Histogram<Integer> histo1 = new Histogram<>();
        for (final int i : is) histo1.increment(i);

        final Histogram<Integer> histo2 = new Histogram<>(histo1);
        Assert.assertEquals(histo1, histo2);
        Assert.assertEquals(histo2, histo1);
    }

    @Test
    public void testGet() {
        final int[] is = {4,4,5,5,5};
        final Histogram<Integer> histo = new Histogram<>();
        for (final int i : is) histo.increment(i);

        Assert.assertEquals(histo.get(4).getValue(), 2.0);
        Assert.assertEquals(histo.get(5).getValue(), 3.0);
        Assert.assertEquals(histo.get(6), null);
    }

    @Test
    public void testAddHistogram() {
        final int[] is1 = {4,4,5,5,5};
        final Histogram<Integer> histo1 = new Histogram<>();
        Assert.assertTrue(histo1.isEmpty());
        for (final int i : is1) histo1.increment(i);

        Assert.assertFalse(histo1.isEmpty());

        final int[] is2 = {5,5, 6,6,6,6};
        final Histogram<Integer> histo2 = new Histogram<>();
        for (final int i : is2) histo2.increment(i);

        Assert.assertEquals(histo1.get(4).getValue(), 2.0);
        Assert.assertEquals(histo1.get(5).getValue(), 3.0);
        Assert.assertEquals(histo1.get(6), null);

        histo1.addHistogram(histo2);

        Assert.assertEquals(histo1.get(4).getValue(), 2.0);
        Assert.assertEquals(histo1.get(5).getValue(), 5.0);
        Assert.assertEquals(histo1.get(6).getValue(), 4.0);
    }

    @Test
    public void testGetCumulativeProbability() {
        final int[] is = {4,4,5,5,5,6,6,6,6};
        final Histogram<Integer> histo = new Histogram<>();
        for (final int i : is) histo.increment(i);
        Assert.assertEquals(histo.getCumulativeProbability(2.0), 0.0);
        Assert.assertEquals(histo.getCumulativeProbability(4.0), 2.0/9);
        Assert.assertEquals(histo.getCumulativeProbability(5.0), 5.0/9);
        Assert.assertEquals(histo.getCumulativeProbability(6.0), 9.0/9);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testGetCumulativeProbabilityBlowup() {
        final String[] is = {"foo"};
        final Histogram<String> histo = new Histogram<>();
        for (final String i : is) histo.increment(i);
        histo.getCumulativeProbability(2.0);
    }

    @Test
    public void testPercentile() {
        final int[] is = {4,4,5,5,5,6,6,6,6};
        final Histogram<Integer> histo = new Histogram<>();
        for (final int i : is) histo.increment(i);
        Assert.assertEquals(histo.getPercentile(0.01), 4.0);
        Assert.assertEquals(histo.getPercentile(2.0/9), 4.0);
        Assert.assertEquals(histo.getPercentile(5.0/9), 5.0);
        Assert.assertEquals(histo.getPercentile(0.99999), 6.0);
    }

    @Test
    public void testGetMinMax() {
        final int[] is = {4,4,5,5,5,6,6,6,6};
        final Histogram<Integer> histo = new Histogram<>();
        for (final int i : is) histo.increment(i);
        Assert.assertEquals(histo.getMin(), 4.0);
        Assert.assertEquals(histo.getMax(), 6.0);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testGetMinBlowup() {
        final String[] is = {"foo", "bar", "bar"};
        final Histogram<String> histo = new Histogram<>();
        for (final String i : is) histo.increment(i);
        histo.getMin();//blow up
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testGetMaxBlowup() {
        final String[] is = {"foo", "bar", "bar"};
        final Histogram<String> histo = new Histogram<>();
        for (final String i : is) histo.increment(i);
        histo.getMax();//blow up
    }

    @Test
    public void testGetMedianBinSize() {
        final int[] is = {4,4,5,5,5,6,6,6,6};
        final Histogram<Integer> histo = new Histogram<>();
        Assert.assertEquals(histo.getMedianBinSize(), 0, 0.000001); //empty
        for (final int i : is) histo.increment(i);
        Assert.assertEquals(histo.getMedianBinSize(), 3, 0.000001); //three fives
    }

    @Test
    public void testGetMedianBinSize_Even() {
        final int[] is = {4,4,5,5,5};
        final Histogram<Integer> histo = new Histogram<>();
        Assert.assertEquals(histo.getMedianBinSize(), 0, 0.000001); //empty
        for (final int i : is) histo.increment(i);
        Assert.assertEquals(histo.getMedianBinSize(), (2+3)/2.0, 0.000001); //even split
    }

    @Test
    public void testSize() {
        final int[] is = {4,4,5,5,5};
        final Histogram<Integer> histo = new Histogram<>();
        for (final int i : is) histo.increment(i);
        Assert.assertEquals(histo.size(), 2); //2 unique values
    }


    @Test
    public void testMode() {
        final int[] is = {4,4,5,5,5,6};
        final Histogram<Integer> histo = new Histogram<>();
        for (final int i : is) histo.increment(i);
        Assert.assertEquals(histo.getMode(), 5.0);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testModeBlowup() {
        final String[] is = {"foo"};
        final Histogram<String> histo = new Histogram<>();
        for (final String i : is) histo.increment(i);
        histo.getMode();//blowup
    }

    @Test
    public void testComparator() {
        final int[] is = {4,4,5,5,5};
        final Histogram<Integer> histo1 = new Histogram<>();
        for (final int i : is) histo1.increment(i);
        Assert.assertNull(histo1.comparator());

        final Histogram<Integer> histo2 = new Histogram<>(Comparator.comparingInt(Integer::intValue));
        Comparator<Integer> comp = (Comparator<Integer>) histo2.comparator();
        Assert.assertNotNull(comp);
        Assert.assertEquals(comp.compare(4,5), -1);
    }

    @Test
    public void testEquals() {
        final int[] is = {4,4,4,4,5,5,5,5,6,6,6,6,6,6,7,7,7,7,7,7,7,8,8,8,8,8,8,8,8};
        final Histogram<Integer> histo1 = new Histogram<>();
        final Histogram<Integer> histo2 = new Histogram<>();
        for (final int i : is) histo1.increment(i);
        for (final int i : is) histo2.increment(i);
        Assert.assertEquals(histo1, histo1);
        Assert.assertEquals(histo2, histo1);
        Assert.assertEquals(histo1, histo2);

        Assert.assertEquals(histo1.hashCode(), histo2.hashCode());

        Assert.assertNotEquals(null, histo1);
        Assert.assertNotEquals(histo1, null);

        histo2.increment(4);//make them not equal
        Assert.assertEquals(histo1, histo1);
        Assert.assertNotEquals(histo2, histo1);
        Assert.assertNotEquals(histo1, histo2);
        Assert.assertNotEquals(histo1.hashCode(), histo2.hashCode());


    }

    @Test(dataProvider = "medianTestData")
    public void testMedian(final int [] values, final double median) {
        final Histogram<Integer> histo = new Histogram<>();
        for (final int i : values) histo.increment(i);
        Assert.assertEquals(histo.getMedian(), median);
    }

    @DataProvider(name = "medianTestData")
    public Object[][] medianTestData() {
        return new Object[][] {
                new Object[] {new int[] {} , 0d},
                new Object[] {new int[] {999} , 999d},
                new Object[] {new int[] {1,2,3,4,5,6} , 3.5d},
                new Object[] {new int[] {5,5,5,5,5,6,6} , 5d},
                new Object[] {new int[] {5,5,5,5,5,6,6,6,6,6} , 5.5d},
        };
    }

    @Test
    public void testMad() {
        final int[] is = {4,4,4,4,5,5,5,5,6,6,6,6,6,6,7,7,7,7,7,7,7,8,8,8,8,8,8,8,8};
        final Histogram<Integer> histo = new Histogram<>();
        for (final int i : is) histo.increment(i);

        Assert.assertEquals(7d, histo.getMedian());
        Assert.assertEquals(1d, histo.getMedianAbsoluteDeviation());
        Assert.assertTrue(abs(histo.estimateSdViaMad() - 1.4826) < 0.0001);
    }


    @Test(dataProvider = "histogramData") //this data provider has several extra variables that we don't make use of here
    public void testSerializeHistogram(final int[] values, final double mean, final double stdev, final Integer trimByWidth) throws IOException, ClassNotFoundException {
        final Histogram<Integer> histo = new Histogram<>();
        for (int value : values) {
            histo.increment(value);
        }

        Histogram<Integer> deserializedHistogram = TestUtil.serializeAndDeserialize(histo);
        Assert.assertEquals(deserializedHistogram, histo);
    }

    private double round(final double in) {
        long l = (long) (in * 10000);
        return l / 10000d;
    }

}
