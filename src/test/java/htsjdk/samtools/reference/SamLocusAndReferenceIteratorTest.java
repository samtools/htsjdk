package htsjdk.samtools.reference;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.*;
import htsjdk.samtools.util.*;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

public class SamLocusAndReferenceIteratorTest extends HtsjdkTest {
    static private final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/reference");

    final File referenceAssembly18_trimmed = new File(TEST_DATA_DIR, "Homo_sapiens_assembly18.trimmed.fasta");
    final File referenceWithTrailingNewWhiteSpace = new File(TEST_DATA_DIR, "reference_with_trailing_whitespace.fasta");

    File simpleSmallFileCram;
    File simpleSmallFileSam;
    File simpleSmallFileBam;

    File leadingInsertionCram;
    File leadingInsertionSam;
    File leadingInsertionBam;

    @DataProvider
    Object[][] testSamLocusAndReferenceIteratorData() {
        return new Object[][]{
                {simpleSmallFileSam, false},
                {simpleSmallFileBam, false},
                {simpleSmallFileCram, true}
        };
    }

    @BeforeClass
    void setup() throws IOException {

        simpleSmallFileSam = new File(TEST_DATA_DIR, "simpleSmallFile.sam");
        leadingInsertionSam = new File(TEST_DATA_DIR, "leading_insertion.sam");

        simpleSmallFileCram = createTemporarySamFileFromInput(simpleSmallFileSam, "SamLocusAndReferenceIteratorTest", ".cram", referenceAssembly18_trimmed);
        leadingInsertionCram = createTemporarySamFileFromInput(leadingInsertionSam, "SamLocusAndReferenceIteratorTest", ".cram", referenceAssembly18_trimmed);

        simpleSmallFileBam = createTemporarySamFileFromInput(simpleSmallFileSam, "SamLocusAndReferenceIteratorTest", ".bam", referenceAssembly18_trimmed);
        leadingInsertionBam = createTemporarySamFileFromInput(leadingInsertionSam, "SamLocusAndReferenceIteratorTest", ".bam", referenceAssembly18_trimmed);

    }

    public static File createTemporarySamFileFromInput(final File samFile, final String tempFilePrefix, final String suffix, final File reference) throws IOException {

        final File output = TestUtil.createTemporaryIndexedFile(tempFilePrefix, suffix);

        try (final SamReader in = SamReaderFactory.makeDefault().open(samFile);
             final SAMFileWriter out = new SAMFileWriterFactory().makeWriter(in.getFileHeader().clone(), true, output, reference)) {
            for (final SAMRecord record : in) {
                out.addAlignment(record);
            }
        }
        return output;
    }


    @Test(dataProvider = "testSamLocusAndReferenceIteratorData")
    public void testSamLocusAndReferenceIterator(final File samFile, boolean readerNeedsReference) {

        final ReferenceSequenceFile referenceSequenceFile = new FastaSequenceFile(referenceAssembly18_trimmed, false);
        final ReferenceSequenceFileWalker referenceSequenceFileWalker = new ReferenceSequenceFileWalker(referenceSequenceFile);

        final SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
        if (readerNeedsReference) {
            samReaderFactory.referenceSequence(referenceAssembly18_trimmed);
        }
        final SamReader samReader = samReaderFactory.open(samFile);
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
            for (final SamLocusIterator.RecordAndOffset recordAndOffset : samLocusAndReference.getRecordAndOffsets()) {
                Assert.assertTrue(SequenceUtil.basesEqual(samLocusAndReference.getReferenceBase(), recordAndOffset.getReadBase()), "Record: " + recordAndOffset.getRecord() + " Position:" + samLocusAndReference.getLocus().toString());
            }
        }
    }

    @Test(dataProvider = "testSamLocusAndReferenceIteratorData", expectedExceptions = IllegalArgumentException.class)
    public void testSamLocusAndReferenceIteratorMismatch(final File samFile, boolean readerNeedsReference) {
        final File reference = referenceWithTrailingNewWhiteSpace;
        final ReferenceSequenceFile referenceSequenceFile = new FastaSequenceFile(reference, false);
        final ReferenceSequenceFileWalker referenceSequenceFileWalker = new ReferenceSequenceFileWalker(referenceSequenceFile);

        final SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
        if (readerNeedsReference) {
            samReaderFactory.referenceSequence(reference);
        }
        final SamReader samReader = samReaderFactory.open(samFile);
        final SamLocusIterator samLocusIterator = new SamLocusIterator(samReader);
        new SamLocusAndReferenceIterator(referenceSequenceFileWalker, samLocusIterator); // should throw
    }


    @DataProvider
    Object[][] testSamLocusAndReferenceIteratorLeadingInsertionData() {
        return new Object[][]{
                {leadingInsertionSam, false},
                {leadingInsertionBam, false},
                {leadingInsertionCram, true}
        };
    }

    @Test(dataProvider = "testSamLocusAndReferenceIteratorLeadingInsertionData")
    public void testSamLocusAndReferenceIteratorLeadingInsertion(final File samFile, boolean readerNeedsReference) {
        final File reference = referenceAssembly18_trimmed;
        final ReferenceSequenceFile referenceSequenceFile = new FastaSequenceFile(reference, false);
        final ReferenceSequenceFileWalker referenceSequenceFileWalker = new ReferenceSequenceFileWalker(referenceSequenceFile);

        final SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
        if (readerNeedsReference) {
            samReaderFactory.referenceSequence(reference);
        }
        final SamReader samReader = samReaderFactory.open(samFile);
        final SamLocusIterator samLocusIterator = new SamLocusIterator(samReader);
        samLocusIterator.setIncludeIndels(true);
        final SamLocusAndReferenceIterator samLocusAndReferences = new SamLocusAndReferenceIterator(referenceSequenceFileWalker, samLocusIterator);

        IntervalList intervalList = new IntervalList(samReader.getFileHeader());
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
        for (final SamLocusAndReferenceIterator.SAMLocusAndReference samLocusAndReference : samLocusAndReferences) {
            final SamLocusIterator.LocusInfo locus = samLocusAndReference.getLocus();
            if (locus.getContig() == "chrM" && locus.getPosition() == 0) {
                positionAtZeroBase = samLocusAndReference.getReferenceBase();
            }

            totalMappedRecords += samLocusAndReference.getRecordAndOffsets().size();
            totalInsertedRecords += samLocusAndReference.getLocus().getInsertedInRecord().size();
            totalDeletedRecords += samLocusAndReference.getLocus().getDeletedInRecord().size();

            if (overlapDetector.overlapsAny(samLocusAndReference.getLocus())) {
                Assert.assertEquals(samLocusAndReference.getRecordAndOffsets().size(), 2);
            } else {
                Assert.assertEquals(samLocusAndReference.getRecordAndOffsets().size(), 0);
            }
        }
        Assert.assertEquals(positionAtZeroBase, SamLocusAndReferenceIterator.BASE_BEFORE_REFERENCE_START);
        Assert.assertEquals(totalMappedRecords, (36 - 10) * 4);
        // Developer note: not 10 * 4, since we count all inserted bases prior to or after the reference only once
        Assert.assertEquals(totalInsertedRecords, 4);
        Assert.assertEquals(totalDeletedRecords, 0);
    }
}
