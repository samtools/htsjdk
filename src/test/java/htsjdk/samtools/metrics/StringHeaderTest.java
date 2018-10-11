package htsjdk.samtools.metrics;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.TestUtil;
import java.io.IOException;
import org.testng.Assert;
import org.testng.annotations.Test;

public class StringHeaderTest extends HtsjdkTest {

  @Test
  public void testStringHeaderSerialization() throws IOException, ClassNotFoundException {
    final Header header = new StringHeader("some value");
    final Header deserializedHeader = TestUtil.serializeAndDeserialize(header);
    Assert.assertEquals(deserializedHeader, header);
  }
}
