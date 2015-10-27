package htsjdk.samtools.cram.io;

import htsjdk.samtools.util.Tuple;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by vadim on 03/02/2015.
 */
public class ITF8Test {

    private ExposedByteArrayOutputStream testBAOS;
    private ByteArrayInputStream testBAIS;

    @BeforeClass
    public void initialize() {
        testBAOS = new ExposedByteArrayOutputStream();
        testBAIS = new ByteArrayInputStream(testBAOS.getBuffer());
    }

    @BeforeMethod
    public void reset() {
        testBAOS.reset();
        testBAIS.reset();
    }


    @DataProvider(name = "testITF8")
    public static Object[][] testValues() {
        List<Integer> list = new ArrayList<Integer>() ;

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
        list.add(Integer.MAX_VALUE) ;
        list.add(Integer.MIN_VALUE);
        list.add(268435456);

        Object[][] params = new Object[list.size()][] ;
        for (int i=0; i<params.length; i++)
            params[i] = new Object[]{list.get(i)} ;
        return params;
    }

    @Test(dataProvider = "testITF8")
    public void testITF8(int value) throws IOException {
        int len = ITF8.writeUnsignedITF8(value, testBAOS);
        Assert.assertTrue(len <= (8 * 9));

        long result = ITF8.readUnsignedITF8(testBAIS);
        Assert.assertEquals(value, result);
    }

    @DataProvider(name = "predefined")
    public static Object[][] predefinedProvider() {
        List<Tuple<Integer, byte[]>> list = new ArrayList<Tuple<Integer, byte[]>>() ;
        list.add(new Tuple<Integer, byte[]>(4542278, new byte[]{(byte) (0xFF & 224), 69, 79, 70})) ;
        list.add(new Tuple<Integer, byte[]>(16384, new byte[]{-64, 64, 0})) ;
        list.add(new Tuple<Integer, byte[]>(192, new byte[]{-128, -64})) ;
        list.add(new Tuple<Integer, byte[]>(-4757, new byte[]{-1, -1, -2, -42, 107})) ;

        Object[][] params = new Object[list.size()][] ;
        for (int i=0; i<params.length; i++)
            params[i] = new Object[]{list.get(i).a, list.get(i).b} ;
        return params;
    }

    @Test(dataProvider = "predefined")
    public void testPredefined (int value, byte[] itf8) {
        final byte[] bytes = ITF8.writeUnsignedITF8(value);
        Assert.assertEquals(itf8, bytes);

        int n = ITF8.readUnsignedITF8(itf8);
        Assert.assertEquals(value, n);
    }
}
