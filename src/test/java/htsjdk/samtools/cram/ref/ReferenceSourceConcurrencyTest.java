package htsjdk.samtools.cram.ref;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * {@link ReferenceSource} caches the bases of the most recently requested contig alongside that contig's index.
 * Those two values are only meaningful together, so concurrent callers must never be able to observe bases from
 * one contig paired with the index of another. See https://github.com/samtools/htsjdk/issues/1643.
 */
public class ReferenceSourceConcurrencyTest extends HtsjdkTest {

    private static final int CONTIG_COUNT = 4;
    private static final int CONTIG_LENGTH = 5000;

    /**
     * Builds a reference where every contig is filled with a single distinct base, so that bases belonging to
     * the wrong contig are immediately recognisable.
     */
    private static byte[] basesForContig(final int contigIndex) {
        final byte[] bases = new byte[CONTIG_LENGTH];
        Arrays.fill(bases, (byte) "ACGT".charAt(contigIndex % 4));
        return bases;
    }

    private static String contigName(final int contigIndex) {
        return "contig" + contigIndex;
    }

    /** A reference file that serves each contig as a uniform run of one base. */
    private static final class UniformBaseReferenceFile implements ReferenceSequenceFile {
        @Override
        public SAMSequenceDictionary getSequenceDictionary() {
            return null;
        }

        @Override
        public ReferenceSequence nextSequence() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reset() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isIndexed() {
            return true;
        }

        @Override
        public ReferenceSequence getSequence(final String contig) {
            final int index = Integer.parseInt(contig.substring("contig".length()));
            return new ReferenceSequence(contig, index, basesForContig(index));
        }

        @Override
        public ReferenceSequence getSubsequenceAt(final String contig, final long start, final long stop) {
            final int index = Integer.parseInt(contig.substring("contig".length()));
            final byte[] all = basesForContig(index);
            return new ReferenceSequence(contig, index, Arrays.copyOfRange(all, (int) start - 1, (int) stop));
        }

        @Override
        public void close() {}
    }

    /**
     * Hammers a single ReferenceSource from many threads, each repeatedly asking for a different contig, and
     * verifies every thread only ever receives bases belonging to the contig it asked for.
     */
    @Test
    public void concurrent_region_requests_never_return_another_contigs_bases() throws Exception {
        final ReferenceSource source = new ReferenceSource(new UniformBaseReferenceFile());

        final int iterations = 2000;
        final ExecutorService executor = Executors.newFixedThreadPool(CONTIG_COUNT);
        final List<Callable<String>> tasks = new ArrayList<>();

        for (int contigIndex = 0; contigIndex < CONTIG_COUNT; contigIndex++) {
            final int index = contigIndex;
            tasks.add(() -> {
                final SAMSequenceRecord record = new SAMSequenceRecord(contigName(index), CONTIG_LENGTH);
                record.setSequenceIndex(index);
                final byte expected = (byte) "ACGT".charAt(index % 4);

                for (int i = 0; i < iterations; i++) {
                    final byte[] bases = source.getReferenceBasesByRegion(record, 0, 100);
                    for (final byte base : bases) {
                        if (base != expected) {
                            return String.format(
                                    "contig %d expected all '%c' but saw '%c'", index, expected, (char) base);
                        }
                    }
                }
                return null;
            });
        }

        final List<Future<String>> results = executor.invokeAll(tasks);
        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(2, TimeUnit.MINUTES), "test threads did not finish in time");

        for (final Future<String> result : results) {
            final String failure = result.get();
            Assert.assertNull(failure, "ReferenceSource returned bases for the wrong contig: " + failure);
        }
    }

    /** The single-threaded caching behaviour must be unaffected. */
    @Test
    public void repeated_requests_for_the_same_contig_return_correct_bases() {
        final ReferenceSource source = new ReferenceSource(new UniformBaseReferenceFile());
        final SAMSequenceRecord record = new SAMSequenceRecord(contigName(1), CONTIG_LENGTH);
        record.setSequenceIndex(1);

        for (int i = 0; i < 5; i++) {
            final byte[] bases = source.getReferenceBasesByRegion(record, 10, 50);
            Assert.assertEquals(bases.length, 50);
            for (final byte base : bases) {
                Assert.assertEquals((char) base, 'C', "expected contig1 to be all 'C'");
            }
        }
    }

    /** Alternating contigs on one thread must still return the right bases each time. */
    @Test
    public void alternating_contigs_return_correct_bases() {
        final ReferenceSource source = new ReferenceSource(new UniformBaseReferenceFile());

        for (int i = 0; i < 20; i++) {
            final int index = i % CONTIG_COUNT;
            final SAMSequenceRecord record = new SAMSequenceRecord(contigName(index), CONTIG_LENGTH);
            record.setSequenceIndex(index);
            final byte[] bases = source.getReferenceBasesByRegion(record, 0, 20);
            for (final byte base : bases) {
                Assert.assertEquals(
                        (char) base, "ACGT".charAt(index % 4), "wrong bases returned after switching contigs");
            }
        }
    }
}
