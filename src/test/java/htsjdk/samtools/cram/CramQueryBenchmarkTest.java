package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.CramQueryBenchmark.Region;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.testng.Assert;
import org.testng.annotations.Test;

/** The region sampler behind {@link CramQueryBenchmark}. */
public class CramQueryBenchmarkTest extends HtsjdkTest {

    private static SAMSequenceDictionary dictionary() {
        return new SAMSequenceDictionary(List.of(
                new SAMSequenceRecord("chr1", 100_000),
                new SAMSequenceRecord("chr2", 50_000),
                new SAMSequenceRecord("chrTiny", 500)));
    }

    @Test
    public void testSameSeedProducesSameRegions() {
        final List<Region> first = CramQueryBenchmark.sampleRegions(dictionary(), null, 50, 1000, 7);
        final List<Region> second = CramQueryBenchmark.sampleRegions(dictionary(), null, 50, 1000, 7);
        Assert.assertEquals(second, first);
    }

    @Test
    public void testDifferentSeedProducesDifferentRegions() {
        final List<Region> first = CramQueryBenchmark.sampleRegions(dictionary(), null, 50, 1000, 7);
        final List<Region> second = CramQueryBenchmark.sampleRegions(dictionary(), null, 50, 1000, 8);
        Assert.assertNotEquals(second, first);
    }

    @Test
    public void testRegionsAreSortedInDictionaryOrder() {
        final SAMSequenceDictionary dictionary = dictionary();
        final List<Region> regions = CramQueryBenchmark.sampleRegions(dictionary, null, 100, 1000, 7);
        for (int i = 1; i < regions.size(); i++) {
            final Region previous = regions.get(i - 1);
            final Region current = regions.get(i);
            final int previousIndex = dictionary.getSequenceIndex(previous.sequence());
            final int currentIndex = dictionary.getSequenceIndex(current.sequence());
            Assert.assertTrue(
                    previousIndex < currentIndex
                            || (previousIndex == currentIndex && previous.start() <= current.start()),
                    "Regions out of coordinate order at " + i + ": " + previous + " then " + current);
        }
    }

    @Test
    public void testRegionsHaveRequestedWidth() {
        for (final Region region : CramQueryBenchmark.sampleRegions(dictionary(), null, 100, 1000, 7)) {
            Assert.assertEquals(region.end() - region.start() + 1, 1000);
        }
    }

    @Test
    public void testRegionsStayWithinSequenceBounds() {
        final SAMSequenceDictionary dictionary = dictionary();
        for (final Region region : CramQueryBenchmark.sampleRegions(dictionary, null, 100, 1000, 7)) {
            Assert.assertTrue(region.start() >= 1, "Start before the first base: " + region);
            Assert.assertTrue(
                    region.end() <= dictionary.getSequence(region.sequence()).getSequenceLength(),
                    "End past the last base: " + region);
        }
    }

    @Test
    public void testSequencesShorterThanTheRegionAreSkipped() {
        for (final Region region : CramQueryBenchmark.sampleRegions(dictionary(), null, 100, 1000, 7)) {
            Assert.assertNotEquals(region.sequence(), "chrTiny");
        }
    }

    @Test
    public void testOnlyRequestedSequencesAreSampled() {
        final List<Region> regions = CramQueryBenchmark.sampleRegions(dictionary(), Set.of("chr2"), 30, 1000, 7);
        Assert.assertEquals(regions.size(), 30);
        for (final Region region : regions) {
            Assert.assertEquals(region.sequence(), "chr2");
        }
    }

    @Test
    public void testNoSequenceLongEnoughProducesNoRegions() {
        final List<Region> regions = CramQueryBenchmark.sampleRegions(dictionary(), null, 30, 200_000, 7);
        Assert.assertTrue(regions.isEmpty());
    }

    @Test
    public void testEverySequenceLongEnoughIsReachable() {
        // Both long sequences must appear in a large enough length-weighted sample.
        final List<Region> regions = CramQueryBenchmark.sampleRegions(dictionary(), null, 500, 1000, 7);
        final Set<String> sampled = regions.stream().map(Region::sequence).collect(Collectors.toSet());
        Assert.assertEquals(sampled, Set.of("chr1", "chr2"));
    }
}
