package htsjdk.samtools.cram.io;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by vadim on 03/02/2015.
 */
public class LTF8Test {

    private ExposedByteArrayOutputStream ltf8TestBAOS;
    private ByteArrayInputStream ltf8TestBAIS;

    @BeforeClass
    public void initialize() {
        ltf8TestBAOS = new ExposedByteArrayOutputStream();
        ltf8TestBAIS = new ByteArrayInputStream(ltf8TestBAOS.getBuffer());
    }

    @BeforeMethod
    public void reset() {
        ltf8TestBAOS.reset();
        ltf8TestBAIS.reset();
    }


    @DataProvider(name = "testLTF8")
    public static Object[][] testValues() {
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
        list.add(1125899906842622L) ;
        list.add(1125899906842622L);
        list.add(562949953421312L);
        list.add(4294967296L);
        list.add(268435456L);
        list.add(2147483648L);
        list.add(-1L);

        Object[][] params = new Object[list.size()][] ;
        for (int i=0; i<params.length; i++)
            params[i] = new Object[]{list.get(i)} ;
        return params;
    }

    @Test(dataProvider = "testLTF8")
    public void testLTF8(long value) throws IOException {
        int len = LTF8.writeUnsignedLTF8(value, ltf8TestBAOS);
        Assert.assertTrue(len <= (8 * 9));

        long result = LTF8.readUnsignedLTF8(ltf8TestBAIS);
        Assert.assertEquals(value, result);
    }
}
