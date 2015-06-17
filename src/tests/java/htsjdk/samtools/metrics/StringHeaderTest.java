package htsjdk.samtools.metrics;


import htsjdk.samtools.util.TestUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;

public class StringHeaderTest {

    @Test
    public void testStringHeaderSerialization() throws IOException, ClassNotFoundException {
        final Header header = new StringHeader("some value");
        final Header deserializedHeader = TestUtil.serializeAndDeserialize(header);
        Assert.assertEquals(deserializedHeader, header);
    }

}