package htsjdk.samtools.metrics;

import htsjdk.samtools.util.TestUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;


public class VersionHeaderTest {

    @Test
    public void testSerializeVersionHeader() throws IOException, ClassNotFoundException {
        final VersionHeader versionHeader = new VersionHeader();
        versionHeader.setVersionedItem("SomeThing");
        versionHeader.setVersionString("1.0.1");
        final VersionHeader deserialized = TestUtil.serializeAndDeserialize(versionHeader);
        Assert.assertEquals(deserialized, versionHeader);

    }

}