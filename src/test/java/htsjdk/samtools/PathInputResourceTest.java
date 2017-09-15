package htsjdk.samtools;

import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.function.Function;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class PathInputResourceTest extends HtsjdkTest {
  final String localBam = "src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam";

  @Test
  public void testWrappersAreAccessed() throws Exception {
    Path path = Paths.get(localBam);
    Path indexPath = Paths.get(localBam + ".bai");
    HashMap<String, Boolean> fired = new HashMap<>();
    Function<SeekableByteChannel, SeekableByteChannel> wrapData = (SeekableByteChannel in) -> {
      fired.put("data", true);
      return in;
    };
    Function<SeekableByteChannel, SeekableByteChannel> wrapIndex = (SeekableByteChannel in) -> {
      fired.put("index", true);
      return in;
    };
    SamInputResource in = SamInputResource.of(path, wrapData);
    in.index(indexPath, wrapIndex);
    InputResource indexResource = in.indexMaybe();
    Assert.assertNotNull(indexResource);

    Assert.assertFalse(fired.containsKey("data"));
    Assert.assertFalse(fired.containsKey("index"));

    indexResource.asUnbufferedSeekableStream();

    Assert.assertFalse(fired.containsKey("data"));
    Assert.assertTrue(fired.containsKey("index"));

    in.data().asUnbufferedSeekableStream();

    Assert.assertTrue(fired.containsKey("data"));
    Assert.assertTrue(fired.containsKey("index"));
  }

}
