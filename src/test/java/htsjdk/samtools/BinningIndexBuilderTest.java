package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class BinningIndexBuilderTest extends HtsjdkTest {

  private static final int REFERNCE_SEQUENCE_INDEX = 19;

  @DataProvider(name = "BinningFeatures")
  public Object[][] getBinningFeatures() {
    return new Object[][] {
      { // single feature in first bin at offset 0
        Collections.singletonList(new MockIndexableFeature(REFERNCE_SEQUENCE_INDEX, 1, 10, 0, 25)),
        Collections.singletonList(new Chunk(0, 25)),
        new long[] {0L}
      },
      { // single feature in first bin at non-zero offset
        Collections.singletonList(
            new MockIndexableFeature(REFERNCE_SEQUENCE_INDEX, 1, 10, 100, 125)),
        Collections.singletonList(new Chunk(100, 125)),
        new long[] {100L}
      },
      { // two features spanning two bins at non-zero offsets
        Arrays.asList(
            new MockIndexableFeature(REFERNCE_SEQUENCE_INDEX, 2, 13, 100, 125),
            new MockIndexableFeature(REFERNCE_SEQUENCE_INDEX, 22222, 22223, 22222, 22225)),
        Arrays.asList(new Chunk(100, 125), new Chunk(22222, 22225)),
        new long[] {100L, 22222L}
      },
      { // two features in first bin, one at offset 0, plus one feature in the second bin
        // https://github.com/samtools/htsjdk/issues/943
        Arrays.asList(
            new MockIndexableFeature(REFERNCE_SEQUENCE_INDEX, 1, 10, 0, 25),
            new MockIndexableFeature(REFERNCE_SEQUENCE_INDEX, 2, 13, 100, 125),
            new MockIndexableFeature(REFERNCE_SEQUENCE_INDEX, 22222, 22223, 22222, 22225)),
        Arrays.asList(new Chunk(0, 125), new Chunk(22222, 22225)),
        new long[] {0L, 22222L}
      }
    };
  }

  @Test(dataProvider = "BinningFeatures")
  public void testFeatureAtOffsetZero(
      final List<MockIndexableFeature> mockFeatures,
      final List<Chunk> expectedChunks,
      final long[] expectedBins) {
    // use a sequence length that spans at least two (16k) binning blocks
    final BinningIndexBuilder bib = new BinningIndexBuilder(REFERNCE_SEQUENCE_INDEX, 40000);

    mockFeatures.forEach(bib::processFeature);

    final BinningIndexContent bic = bib.generateIndexContent();

    Assert.assertEquals(expectedBins, bic.getLinearIndex().getIndexEntries());
    Assert.assertEquals(expectedChunks, bic.getAllChunks());
  }

  private static class MockIndexableFeature implements BinningIndexBuilder.FeatureToBeIndexed {
    private final int referenceIndex;
    private final int startCoordinate;
    private final int endCoordinate;
    private final long startOffset;
    private long endOffset;

    private MockIndexableFeature(
        final int referenceIndex,
        final int startCoordinate,
        final int endCoordinate,
        final long startOffset,
        final long endOffset) {
      this.referenceIndex = referenceIndex;
      this.startCoordinate = startCoordinate;
      this.endCoordinate = endCoordinate;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
    }

    @Override
    public int getStart() {
      return startCoordinate;
    }

    @Override
    public int getEnd() {
      return endCoordinate;
    }

    @Override
    public Integer getIndexingBin() {
      return null;
    }

    @Override
    public Chunk getChunk() {
      return new Chunk(startOffset, endOffset);
    }
  }
}
