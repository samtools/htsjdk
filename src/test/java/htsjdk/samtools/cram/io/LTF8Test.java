package htsjdk.samtools.cram.io;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Created by vadim on 03/02/2015.
 */
public class LTF8Test extends HtsjdkTest {

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


    @Test(dataProvider = "testInt64", dataProviderClass = IOTestCases.class)
    public void testLTF8(long value) throws IOException {
        int len = LTF8.writeUnsignedLTF8(value, ltf8TestBAOS);
        Assert.assertTrue(len <= (8 * 9));

        long result = LTF8.readUnsignedLTF8(ltf8TestBAIS);
        Assert.assertEquals(value, result);
    }
}
