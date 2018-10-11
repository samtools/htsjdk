package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import java.util.Arrays;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class BAMFileSpanTest extends HtsjdkTest {
  @Test(dataProvider = "testRemoveContentsBeforeProvider")
  public void testRemoveContentsBefore(
      BAMFileSpan originalSpan, BAMFileSpan cutoff, BAMFileSpan expectedSpan) {
    // only start value in cutoff is used
    Assert.assertEquals(
        ((BAMFileSpan) originalSpan.removeContentsBefore(cutoff)).getChunks(),
        expectedSpan.getChunks());
  }

  @DataProvider(name = "testRemoveContentsBeforeProvider")
  private Object[][] testRemoveContentsBeforeProvider() {
    return new Object[][] {
      {span(chunk(6, 10), chunk(11, 15)), null, span(chunk(6, 10), chunk(11, 15))},
      {span(chunk(6, 10), chunk(11, 15)), span(), span(chunk(6, 10), chunk(11, 15))},
      {span(chunk(6, 10), chunk(11, 15)), span(chunk(6, 0)), span(chunk(6, 10), chunk(11, 15))},
      {span(chunk(6, 10), chunk(11, 15)), span(chunk(7, 0)), span(chunk(7, 10), chunk(11, 15))},
      {span(chunk(6, 10), chunk(11, 15)), span(chunk(9, 0)), span(chunk(9, 10), chunk(11, 15))},
      {span(chunk(6, 10), chunk(11, 15)), span(chunk(10, 0)), span(chunk(11, 15))},
      {span(chunk(6, 10), chunk(11, 15)), span(chunk(11, 0)), span(chunk(11, 15))},
      {span(chunk(6, 10), chunk(11, 15)), span(chunk(12, 0)), span(chunk(12, 15))},
      {span(chunk(6, 10), chunk(11, 15)), span(chunk(15, 0)), span()},
      {span(chunk(6, 10), chunk(11, 15)), span(chunk(16, 0)), span()},
      {
        span(chunk(6, 10), chunk(11, 15)),
        span(chunk(6, 10), chunk(7, 16)),
        span(chunk(6, 10), chunk(11, 15))
      },
      {span(chunk(6, 10), chunk(11, 15)), span(chunk(16, 17), chunk(18, 19)), span()},
    };
  }

  @Test(dataProvider = "testRemoveContentsAfterProvider")
  public void testRemoveContentsAfter(
      BAMFileSpan originalSpan, BAMFileSpan cutoff, BAMFileSpan expectedSpan) {
    // only end value in cutoff is used
    Assert.assertEquals(
        ((BAMFileSpan) originalSpan.removeContentsAfter(cutoff)).getChunks(),
        expectedSpan.getChunks());
  }

  @DataProvider(name = "testRemoveContentsAfterProvider")
  private Object[][] testRemoveContentsAfterProvider() {
    return new Object[][] {
      {span(chunk(6, 10), chunk(11, 15)), null, span(chunk(6, 10), chunk(11, 15))},
      {span(chunk(6, 10), chunk(11, 15)), span(), span(chunk(6, 10), chunk(11, 15))},
      {span(chunk(6, 10), chunk(11, 15)), span(chunk(0, 6)), span()},
      {span(chunk(6, 10), chunk(11, 15)), span(chunk(0, 7)), span(chunk(6, 7))},
      {span(chunk(6, 10), chunk(11, 15)), span(chunk(0, 9)), span(chunk(6, 9))},
      {span(chunk(6, 10), chunk(11, 15)), span(chunk(0, 10)), span(chunk(6, 10))},
      {span(chunk(6, 10), chunk(11, 15)), span(chunk(0, 11)), span(chunk(6, 10))},
      {span(chunk(6, 10), chunk(11, 15)), span(chunk(0, 12)), span(chunk(6, 10), chunk(11, 12))},
      {span(chunk(6, 10), chunk(11, 15)), span(chunk(0, 15)), span(chunk(6, 10), chunk(11, 15))},
      {span(chunk(6, 10), chunk(11, 15)), span(chunk(0, 16)), span(chunk(6, 10), chunk(11, 15))},
      {span(chunk(6, 10), chunk(11, 15)), span(chunk(0, 6), chunk(7, 10)), span(chunk(6, 10))},
      {
        span(chunk(6, 10), chunk(11, 15)),
        span(chunk(0, 6), chunk(7, 16)),
        span(chunk(6, 10), chunk(11, 15))
      },
    };
  }

  private BAMFileSpan span(Chunk... chunks) {
    return new BAMFileSpan(Arrays.asList(chunks));
  }

  private Chunk chunk(long start, long end) {
    return new Chunk(start, end);
  }
}
