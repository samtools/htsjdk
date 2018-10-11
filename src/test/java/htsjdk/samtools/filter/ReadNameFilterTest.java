package htsjdk.samtools.filter;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMRecordSetBuilder;
import htsjdk.samtools.util.CollectionUtil;
import java.io.File;
import java.util.Collections;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Created by farjoun on 5/27/17. */
public class ReadNameFilterTest extends HtsjdkTest {

  private static final File TEST_DIR = new File("src/test/resources/htsjdk/samtools/filter");
  private static final List<String> names =
      CollectionUtil.makeList(
          "Read1_filter", "read3_stay", "Read2_filter", "read4_stay", "Hello_filter", "goodbye");

  @Test
  public void testFilterNames() {
    SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
    names.forEach(builder::addUnmappedFragment);

    FilteringSamIterator filteringSamIterator =
        new FilteringSamIterator(
            builder.getRecords().iterator(),
            new ReadNameFilter(new File(TEST_DIR, "names.txt"), true));

    Assert.assertEquals(
        filteringSamIterator
            .stream()
            .peek(s -> Assert.assertTrue(s.getReadName().contains("filter")))
            .count(),
        3);
  }

  @DataProvider(name = "TrueFalse")
  public Object[][] TrueFalse() {
    return new Object[][] {{true}, {false}};
  }

  @Test(dataProvider = "TrueFalse")
  public void testFilterNamesEmptySetTrue(boolean include) {
    SAMRecordSetBuilder builder = new SAMRecordSetBuilder();

    names.forEach(builder::addUnmappedFragment);

    FilteringSamIterator filteringSamIterator =
        new FilteringSamIterator(
            builder.getRecords().iterator(), new ReadNameFilter(Collections.emptySet(), include));

    Assert.assertEquals(filteringSamIterator.hasNext(), !include);
    Assert.assertEquals(filteringSamIterator.stream().count(), include ? 0 : names.size());
  }
}
