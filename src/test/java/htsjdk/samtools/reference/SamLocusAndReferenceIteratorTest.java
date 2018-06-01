package htsjdk.samtools.reference;

import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.util.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;

/**
 * Created by farjoun on 6/1/18.
 */
public class SamLocusAndReferenceIteratorTest {
    static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/reference");

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
            Assert.assertEquals(samLocusAndReference.getRecordAndOffsets().size(), overlapDetector.overlapsAny(samLocusAndReference.locus) ? 2 : 0, "Position:" + samLocusAndReference.locus.toString());

            for (final SamLocusIterator.RecordAndOffset recordAndOffset : samLocusAndReference.getRecordAndOffsets())
                Assert.assertTrue(SequenceUtil.basesEqual(samLocusAndReference.referenceBase, recordAndOffset.getReadBase()), "Record: " + recordAndOffset.getRecord() + " Position:" + samLocusAndReference.locus.toString());
        }
    }
}