package htsjdk.samtools.metrics;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class MetricBaseTest {

    private static class TestMetric extends MetricBase{
        public Object anyObject;

        public TestMetric(final Object anyObject) {
            this.anyObject = anyObject;
        }
    }

    @Test
    public void testHashCodeWithNull(){
        TestMetric metric = new TestMetric(null);
        metric.hashCode(); //test that it can get a hashcode without crashing
    }

    @DataProvider(name = "equalityTest")
    public Object[][] equalityTestProvider(){
        return new Object[][]{
                {null,null, true},
                {null, 1, false},
                {1, null, false},
                {1, 1, true},
                {"Hi", "Hi", true},
                {"Hi", "There", false},
                {"1","1.0000000000000000001d", false},
                {"1", 1.0000000000000000001d, true}, /* Object fields are saved using instance of which performs rounding, but loaded as a string*/
                {1.00000000000000001d, 1.0000000002918d, true}, /* precision limit is set by {@link FormatUtil#DECIMAL_DIGITS_TO_PRINT}, if that changes this test may fail */
                {1.0000, 1.0001, false},
                {1.0, 1.0, true},
                {1, 2, false}
        };
    }

    @Test(dataProvider = "equalityTest")
    public void testEqualsNull(Object a, Object b, boolean shouldBeEqual){
        TestMetric metricA = new TestMetric(a);
        TestMetric metricB = new TestMetric(b);
        Assert.assertEquals(metricA.equals(metricB), shouldBeEqual);

        //check that hashcodes are the same if they're equal
        if(shouldBeEqual) {
            Assert.assertEquals(metricA.hashCode(), metricB.hashCode());
        }
    }


    public class A extends MetricBase {
        public int a = 1;
    }
    public class B extends A{
        public int b = 1;
    }

    @Test
    public void testSubclassEquality(){
        final A a = new A();
        final B b = new B();
        Assert.assertFalse(a.equals(b));
        Assert.assertFalse(b.equals(a));
    }

    @Test void testSelfEquality(){
        final A a = new A();
        final B b = new B();
        Assert.assertTrue(a.equals(a));
        Assert.assertTrue(b.equals(b));
    }
}
