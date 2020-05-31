package htsjdk.samtools.reference;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.CRAMFileReader;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.util.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;

public class FileReaderLocusAndReferenceIteratorTest extends HtsjdkTest {
    static private final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/reference");

    @Test
    public void testFileReaderLocusAndReferenceIterator() {

        final File reference = new File(TEST_DATA_DIR, "Homo_sapiens_assembly18.trimmed.fasta");
        final File cramFile = new File(TEST_DATA_DIR, "simpleSmallFile.cram");
        final ReferenceSequenceFile referenceSequenceFile = new FastaSequenceFile(reference, false);
        final ReferenceSequenceFileWalker referenceSequenceFileWalker = new ReferenceSequenceFileWalker(referenceSequenceFile);

        final ReferenceSource referenceSource = new ReferenceSource(reference);
        final CRAMFileReader cramReader = new CRAMFileReader(cramFile, referenceSource);
        final FileReaderLocusIterator cramLocusIterator = new FileReaderLocusIterator(cramReader);
        final FileReaderLocusAndReferenceIterator cramLocusAndReferences = new FileReaderLocusAndReferenceIterator(
                referenceSequenceFileWalker, cramLocusIterator);

        IntervalList intervalList = new IntervalList(cramReader.getFileHeader());
        intervalList.add(new Interval("chrM", 1, 36));
        intervalList.add(new Interval("chr20", 8401, 8460));

        OverlapDetector<Interval> overlapDetector = new OverlapDetector<>(0, 0);
        overlapDetector.addAll(intervalList.getIntervals(), intervalList.getIntervals());

        for (final SamLocusAndReferenceIterator.SAMLocusAndReference cramLocusAndReference : cramLocusAndReferences) {
            // The sam file only has coverage in the intervals that are within 'intervalList', and there the coverage should
            // be exactly 2 since there are two overlapping, paired reads. This is what this test is testing:
            Assert.assertEquals(cramLocusAndReference.getRecordAndOffsets().size(), overlapDetector.overlapsAny(
                    cramLocusAndReference.getLocus()) ? 2 : 0, "Position:" + cramLocusAndReference.getLocus().toString());

            // all the reads are equal to the reference...this is what this test is testing.
            for (final SamLocusIterator.RecordAndOffset recordAndOffset : cramLocusAndReference.getRecordAndOffsets())
                Assert.assertTrue(SequenceUtil.basesEqual(cramLocusAndReference.getReferenceBase(), recordAndOffset.getReadBase()),
                        "Record: " + recordAndOffset.getRecord() + " Position:" + cramLocusAndReference.getLocus().toString());
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testFileReaderLocusAndReferenceIteratorMismatch() {
        final File reference = new File(TEST_DATA_DIR, "reference_with_trailing_whitespace.fasta");
        final File cramFile = new File(TEST_DATA_DIR, "simpleSmallFile.cram");
        final ReferenceSequenceFile referenceSequenceFile = new FastaSequenceFile(reference, false);
        final ReferenceSequenceFileWalker referenceSequenceFileWalker = new ReferenceSequenceFileWalker(referenceSequenceFile);

        final CRAMFileReader cramReader = new CRAMFileReader(cramFile, (CRAMReferenceSource)null);
        final FileReaderLocusIterator cramLocusIterator = new FileReaderLocusIterator(cramReader);
        new FileReaderLocusAndReferenceIterator(referenceSequenceFileWalker, cramLocusIterator); // should throw
    }

    @Test
    public void testFileReaderLocusAndReferenceIteratorLeadingInsertion() {
        final File reference = new File(TEST_DATA_DIR, "Homo_sapiens_assembly18.trimmed.fasta");
        final File cramFile = new File(TEST_DATA_DIR, "leading_insertion.cram");
        final ReferenceSequenceFile referenceSequenceFile = new FastaSequenceFile(reference, false);
        final ReferenceSequenceFileWalker referenceSequenceFileWalker = new ReferenceSequenceFileWalker(referenceSequenceFile);

        final ReferenceSource referenceSource = new ReferenceSource(reference);
        final CRAMFileReader cramReader = new CRAMFileReader(cramFile, referenceSource);
        final FileReaderLocusIterator cramLocusIterator = new FileReaderLocusIterator(cramReader);
        cramLocusIterator.setIncludeIndels(true);
        final FileReaderLocusAndReferenceIterator cramLocusAndReferences = new FileReaderLocusAndReferenceIterator(
                referenceSequenceFileWalker, cramLocusIterator);

        IntervalList intervalList = new IntervalList(cramReader.getFileHeader());
        intervalList.add(new Interval("chrM", 1, 26));
        intervalList.add(new Interval("chrM", 16546, 16571));

        OverlapDetector<Interval> overlapDetector = new OverlapDetector<>(0, 0);
        overlapDetector.addAll(intervalList.getIntervals(), intervalList.getIntervals());

        // Developer note: these are the # of loci that we have (1) a read base mapped to a reference base, (2) a read
        // base inserted relative the reference, and (3) a read base deleted relative to the reference.  For read bases
        // inserted prior to or after the reference (ex.. at position 1 of a contig), they are counted only once!
        int totalMappedRecords = 0;
        int totalInsertedRecords = 0;
        int totalDeletedRecords = 0;
        byte positionAtZeroBase = (byte) '?';
        for (final SamLocusAndReferenceIterator.SAMLocusAndReference cramLocusAndReference : cramLocusAndReferences) {
            final SamLocusIterator.LocusInfo locus = cramLocusAndReference.getLocus();
            if (locus.getContig() == "chrM" && locus.getPosition() == 0) {
                positionAtZeroBase = cramLocusAndReference.getReferenceBase();
            }

            totalMappedRecords += cramLocusAndReference.getRecordAndOffsets().size();
            totalInsertedRecords += cramLocusAndReference.getLocus().getInsertedInRecord().size();
            totalDeletedRecords += cramLocusAndReference.getLocus().getDeletedInRecord().size();

            if (overlapDetector.overlapsAny(cramLocusAndReference.getLocus())) {
                Assert.assertEquals(cramLocusAndReference.getRecordAndOffsets().size(), 2);
            }
            else {
                Assert.assertEquals(cramLocusAndReference.getRecordAndOffsets().size(), 0);
            }
        }
        Assert.assertEquals(positionAtZeroBase, SamLocusAndReferenceIterator.BASE_BEFORE_REFERENCE_START);
        Assert.assertEquals(totalMappedRecords, (36 - 10) * 4);
        // Developer note: not 10 * 4, since we count all inserted bases prior to or after the reference only once
        Assert.assertEquals(totalInsertedRecords, 4);
        Assert.assertEquals(totalDeletedRecords, 0);
    }
}
