package htsjdk.samtools.util;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Created by farjoun on 1/29/16.
 */
public class TupleTest {

    @Test
    public void testEquals() throws Exception {
        Assert.assertEquals(new Tuple<>(1, "hi"), new Tuple<>(1, "hi"));

        Assert.assertEquals(new Tuple<>(1, null), new Tuple<>(1, null));
        Assert.assertEquals(new Tuple<>(null, "hi"), new Tuple<>(null, "hi"));
        Assert.assertEquals(new Tuple<>(null, null), new Tuple<>(null, null));


        Assert.assertNotSame(new Tuple<Integer, Integer>(1, null), new Tuple<Integer, String>(1, null));
        Assert.assertNotSame(new Tuple<Integer, String>(null, "hi"), new Tuple<String, String>(null, "hi"));
        Assert.assertNotSame(new Tuple<Integer, Integer>(null, null), new Tuple<Integer, String>(null, null));


        Assert.assertNotSame(new Tuple<>(1, "hi"), new Tuple<>(1, "bye"));
        Assert.assertNotSame(new Tuple<>(2, "hi"), new Tuple<>(1, "hi"));
        Assert.assertNotSame(new Tuple<>(2, "hi"), new Tuple<>(1, null));
        Assert.assertNotSame(new Tuple<>(2, "hi"), new Tuple<>(null, "hi"));

    }

    @Test
    public void testHashCode() throws Exception {
        Assert.assertEquals(new Tuple<>(1, "hi").hashCode(), new Tuple<>(1, "hi").hashCode());

        Assert.assertEquals(new Tuple<>(1, null).hashCode(), new Tuple<>(1, null).hashCode());
        Assert.assertEquals(new Tuple<>(null, "hi").hashCode(), new Tuple<>(null, "hi").hashCode());
        Assert.assertEquals(new Tuple<>(null, null).hashCode(), new Tuple<>(null, null).hashCode());

        //even though these are of different types, the value is null and so I have to make these equal...
        Assert.assertEquals(new Tuple<Integer, Integer>(1, null).hashCode(), new Tuple<Integer, String>(1, null).hashCode());
        Assert.assertEquals(new Tuple<Integer, String>(null, "hi").hashCode(), new Tuple<String, String>(null, "hi").hashCode());
        Assert.assertEquals(new Tuple<Integer, Integer>(null, null).hashCode(), new Tuple<Integer, String>(null, null).hashCode());

        Assert.assertNotSame(new Tuple<>(1, "hi").hashCode(), new Tuple<>(1, "bye").hashCode());
        Assert.assertNotSame(new Tuple<>(2, "hi").hashCode(), new Tuple<>(1, "hi").hashCode());
        Assert.assertNotSame(new Tuple<>(2, "hi").hashCode(), new Tuple<>(1, null).hashCode());
        Assert.assertNotSame(new Tuple<>(2, "hi").hashCode(), new Tuple<>(null, "hi").hashCode());

    }

    @Test
    public void testToString() throws Exception {
        Assert.assertEquals(new Tuple<>(1, 2).toString(), "[1, 2]");
        Assert.assertEquals(new Tuple<>(1, "hi!").toString(), "[1, hi!]");
        Assert.assertEquals(new Tuple<>(1, new Tuple<>(2, 3)).toString(), "[1, [2, 3]]");

        Assert.assertEquals(new Tuple<>(1, null).toString(), "[1, null]");
        Assert.assertEquals(new Tuple<>(null, null).toString(), "[null, null]");

    }
}