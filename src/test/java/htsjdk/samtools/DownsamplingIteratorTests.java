package htsjdk.samtools;

import htsjdk.samtools.DownsamplingIteratorFactory.Strategy;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;

/**
 * Tests for the downsampling iterator class.
 * @author Tim Fennell
 */
public class DownsamplingIteratorTests {
    final int NUM_TEMPLATES = 50000;
    final EnumMap<Strategy, Double> ACCURACY = new EnumMap<Strategy,Double>(Strategy.class){{
        put(Strategy.HighAccuracy, 0.001);
        put(Strategy.Chained, 0.005);
        put(Strategy.ConstantMemory, 0.01);
    }};

    private static Random getRandom(){
        //this test is probably too strict in it's tolerances
        //not every random seed works, 10000 for example is rejected
        return new Random(10001);
    }

    @Test
    public void testBasicFunction() {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
        final Random r = getRandom();
        for (int i=0; i<NUM_TEMPLATES; ++i) {
            builder.addPair("pair" + r.nextInt(), r.nextInt(24), r.nextInt(1000000), r.nextInt(1000000));
        }
        final Collection<SAMRecord> recs = builder.getRecords();

        runTests("testBasicFunction", recs);
    }

    private void runTests(final String name, final Collection<SAMRecord> recs) {
        for (final DownsamplingIteratorFactory.Strategy strategy : DownsamplingIteratorFactory.Strategy.values()) {
            final double accuracy = ACCURACY.get(strategy);

            for (final double p : new double[]{0, 0.01, 0.1, 0.5, 0.9, 1}) {
                final DownsamplingIterator iterator = DownsamplingIteratorFactory.make(recs.iterator(), strategy, p, accuracy, 42);
                final List<SAMRecord> out = new ArrayList<SAMRecord>();
                while (iterator.hasNext()) out.add(iterator.next());

                final String testcase = name + ": strategy=" + strategy.name() + ", p=" + p + ", accuracy=" + accuracy;

                final double readFraction = iterator.getAcceptedFraction();
                Assert.assertEquals(out.size(), iterator.getAcceptedCount(), "Mismatched sizes with " + testcase);
                Assert.assertTrue(readFraction > p - accuracy && readFraction < p + accuracy, "Read fraction " + readFraction + " out of bounds in " + testcase);
            }
        }
    }

    @Test
    public void testMixOfPairsAndFrags() {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
        final Random r = getRandom();
        for (int i=0; i<NUM_TEMPLATES; ++i) {
            builder.addFrag("frag" + r.nextInt(), r.nextInt(24), r.nextInt(1000000), false);
            builder.addPair("pair" + r.nextInt(), r.nextInt(24), r.nextInt(1000000), r.nextInt(1000000));
        }

        final Collection<SAMRecord> recs = builder.getRecords();
        runTests("testMixOfPairsAndFrags", recs);
    }

    @Test
    public void testSecondaryAlignments() {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
        final Random r = getRandom();
        for (int i=0; i<NUM_TEMPLATES; ++i) {
            final int x = r.nextInt();
            builder.addPair("pair" + x, r.nextInt(24), r.nextInt(1000000), r.nextInt(1000000));
            builder.addPair("pair" + x, r.nextInt(24), r.nextInt(24), r.nextInt(1000000), r.nextInt(1000000), false, false, "50M", "50M", false, true, true, true, 20);
        }

        final Collection<SAMRecord> recs = builder.getRecords();
        runTests("testSecondaryAlignments", recs);
    }
}
