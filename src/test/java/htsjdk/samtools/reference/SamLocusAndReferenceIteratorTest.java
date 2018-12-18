package htsjdk.samtools.reference;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.util.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;

public class SamLocusAndReferenceIteratorTest extends HtsjdkTest {
    static private final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/reference");

    @Test
    public void testSamLocusAndReferenceIterator() {

        final File reference = new File(TEST_DATA_DIR, "Homo_sapiens_assembly18.trimmed.fasta");
        final File samFile = new File(TEST_DATA_DIR, "simpleSmallFile.sam");
        final ReferenceSequenceFile referenceSequenceFile = new FastaSequenceFile(reference, false);
        final ReferenceSequenceFileWalker referenceSequenceFileWalker = new ReferenceSequenceFileWalker(referenceSequenceFile);

        final SamReader samReader = SamReaderFactory.makeDefault().open(samFile);
        final SamLocusIterator samLocusIterator = new SamLocusIterator(samReader);
        final SamLocusAndReferenceIterator samLocusAndReferences = new SamLocusAndReferenceIterator(referenceSequenceFileWalker, samLocusIterator);

        IntervalList intervalList = new IntervalList(samReader.getFileHeader());
        intervalList.add(new Interval("chrM", 1, 36));
        intervalList.add(new Interval("chr20", 8401, 8460));

        OverlapDetector<Interval> overlapDetector = new OverlapDetector<>(0, 0);
        overlapDetector.addAll(intervalList.getIntervals(), intervalList.getIntervals());

        for (final SamLocusAndReferenceIterator.SAMLocusAndReference samLocusAndReference : samLocusAndReferences) {
        // The sam file only has coverage in the intervals that are within 'intervalList', and there the coverage should
        // be exactly 2 since there are two overlapping, paired reads. This is what this test is testing:
            Assert.assertEquals(samLocusAndReference.getRecordAndOffsets().size(), overlapDetector.overlapsAny(samLocusAndReference.getLocus()) ? 2 : 0, "Position:" + samLocusAndReference.getLocus().toString());

            // all the reads are equal to the reference...this is what this test is testing.
            for (final SamLocusIterator.RecordAndOffset recordAndOffset : samLocusAndReference.getRecordAndOffsets())
                Assert.assertTrue(SequenceUtil.basesEqual(samLocusAndReference.getReferenceBase(), recordAndOffset.getReadBase()), "Record: " + recordAndOffset.getRecord() + " Position:" + samLocusAndReference.getLocus().toString());
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSamLocusAndReferenceIteratorMismatch() {
        final File reference = new File(TEST_DATA_DIR, "reference_with_trailing_whitespace.fasta");
        final File samFile = new File(TEST_DATA_DIR, "simpleSmallFile.sam");
        final ReferenceSequenceFile referenceSequenceFile = new FastaSequenceFile(reference, false);
        final ReferenceSequenceFileWalker referenceSequenceFileWalker = new ReferenceSequenceFileWalker(referenceSequenceFile);

        final SamReader samReader = SamReaderFactory.makeDefault().open(samFile);
        final SamLocusIterator samLocusIterator = new SamLocusIterator(samReader);
        final SamLocusAndReferenceIterator shouldThrow = new SamLocusAndReferenceIterator(referenceSequenceFileWalker, samLocusIterator);
    }
}
