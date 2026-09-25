package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.*;
import htsjdk.samtools.cram.build.CRAMReferenceRegion;
import htsjdk.samtools.cram.build.CompressionHeaderFactory;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.encoding.readfeatures.*;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.util.SequenceUtil;
import java.util.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class CRAMCompressionRecordTest extends HtsjdkTest {

    @DataProvider(name = "alignmentEndData")
    public Object[][] getAlignmentEndTestData() {
        return new Object[][] {
            // readLength, alignmentStart, isMapped, readFeatures, expected alignmentEnd
            {100, 5, true, null, 104},
            {100, 10, true, Collections.singletonList(new SoftClip(1, "AAA".getBytes())), 100 + 10 - 1 - 3},
            {100, 10, true, Collections.singletonList(new Deletion(1, 5)), 100 + 10 - 1 + 5},
            {100, 30, true, Collections.singletonList(new Insertion(1, "CCCCCCCCCC".getBytes())), 100 + 30 - 1 - 10},
            {100, 40, true, Collections.singletonList(new InsertBase(1, (byte) 'A')), 100 + 40 - 1 - 1}
        };
    }

    @Test(dataProvider = "alignmentEndData")
    public void testAlignmentEnd(
            final int readLength,
            final int alignmentStart,
            final boolean isMapped,
            final List<ReadFeature> readFeatures,
            final int expectedAlignmentEnd) {
        final CRAMCompressionRecord cramCompressionRecord = CRAMRecordTestHelper.getCRAMRecordWithReadFeatures(
                "rname",
                readLength,
                0,
                alignmentStart,
                isMapped ? 0 : SAMFlag.READ_UNMAPPED.intValue(),
                0,
                new byte[] {'a', 'a', 'a', 'a', 'a'},
                0,
                readFeatures);
        Assert.assertEquals(cramCompressionRecord.getAlignmentStart(), alignmentStart);
        Assert.assertEquals(cramCompressionRecord.getAlignmentEnd(), expectedAlignmentEnd);
    }

    @DataProvider(name = "placedTests")
    private Object[][] placedTests() {
        final List<Object[]> retval = new ArrayList<>();

        final int validSeqId = 0;
        final int[] sequenceIds = new int[] {SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, validSeqId};
        final int validAlignmentStart = 1;
        final int[] starts = new int[] {SAMRecord.NO_ALIGNMENT_START, validAlignmentStart};
        final boolean[] mappeds = new boolean[] {true, false};

        for (final int sequenceId : sequenceIds) {
            for (final int start : starts) {
                for (final boolean mapped : mappeds) {

                    // logically, unplaced reads should never be mapped.
                    // when isPlaced() sees an unplaced-mapped read, it returns false and emits a log warning.
                    // it does not affect expectations here.

                    boolean placementExpectation = true;

                    // we also expect that read sequenceIds and alignmentStart are both valid or both invalid.
                    // however: we do handle the edge case where only one of the pair is valid
                    // by marking it as unplaced.

                    if (sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                        placementExpectation = false;
                    }

                    if (start == SAMRecord.NO_ALIGNMENT_START) {
                        placementExpectation = false;
                    }

                    retval.add(new Object[] {sequenceId, start, mapped, placementExpectation});
                }
            }
        }

        return retval.toArray(new Object[0][0]);
    }

    @DataProvider(name = "baseNormalization")
    public Object[][] getBaseNormalization() {
        // ref bases, read bases, cigar string, expected roundtrip read bases
        return new Object[][] {
            {
                // "acgt"
                "NNNN", "acgt", "4M", "ACGT"
            },
            {
                // CRAM turns everything to upper case, and preserves IUPAC codes except for ".", which goes toN
                // ".aAbBcCdDgGhHkKmMnNrRsStTvVwWyY"
                "NNNNNNNNNNNNNNNNNNNNNNNNNNNNNNN",
                SequenceUtil.getIUPACCodesString(),
                "31M",
                "NAABBCCDDGGHHKKMMNNRRSSTTVVWWYY"
            }
        };
    }

    /**
     * This checks that all read bases returned in the record from CRAMRecord
     * are from the BAM read base set.
     */
    @Test(dataProvider = "baseNormalization")
    public void testCRAMRecordBaseNormalization(
            final String refBases, final String readBases, final String cigarString, final String expectedReadBases) {
        final SAMRecord samRecord = CRAMStructureTestHelper.createSAMRecordMapped(0, 1);
        samRecord.setReadBases(readBases.getBytes());
        samRecord.setBaseQualities(new byte[readBases.length()]);
        samRecord.setCigarString(cigarString);
        samRecord.setAlignmentStart(1);

        final CRAMCompressionRecord cramRecord = new CRAMCompressionRecord(
                CramVersions.DEFAULT_CRAM_VERSION,
                new CRAMEncodingStrategy(),
                samRecord,
                refBases.getBytes(),
                1,
                new HashMap<>());
        final List<CRAMCompressionRecord> cramRecords = Collections.singletonList(cramRecord);

        final CompressionHeader compressionHeader =
                new CompressionHeaderFactory(new CRAMEncodingStrategy()).createCompressionHeader(cramRecords, true);
        final Slice slice = new Slice(cramRecords, compressionHeader, 0L, 0L);
        final CRAMReferenceSource cramReferenceSource = new CRAMReferenceSource() {
            @Override
            public byte[] getReferenceBases(final SAMSequenceRecord sequenceRecord, final boolean tryNameVariants) {
                return refBases.getBytes();
            }

            @Override
            public byte[] getReferenceBasesByRegion(
                    final SAMSequenceRecord sequenceRecord, final int zeroBasedStart, final int requestedRegionLength) {
                return Arrays.copyOfRange(refBases.getBytes(), zeroBasedStart, requestedRegionLength);
            }
        };
        final CRAMReferenceRegion cramReferenceRegion = new CRAMReferenceRegion(
                cramReferenceSource, CRAMStructureTestHelper.SAM_FILE_HEADER.getSequenceDictionary());
        cramReferenceRegion.fetchReferenceBases(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO);
        slice.setReferenceMD5(cramReferenceRegion);
        slice.normalizeCRAMRecords(cramRecords, cramReferenceRegion);
        final SAMRecord roundTrippedSAMRecord = cramRecord.toSAMRecord(CRAMStructureTestHelper.SAM_FILE_HEADER);

        Assert.assertEquals(roundTrippedSAMRecord.getReadBases(), expectedReadBases.getBytes());
    }

    @DataProvider(name = "basesTest")
    public final Object[][] getBasesTests() {
        return new Object[][] {
            // ref bases, read bases, cigar string, expected read bases
            {"AAAAA", "acgta", "5M", "ACGTA"},
            {"AAAAA", "ttttt", "5X", "TTTTT"},
            {"AAAAA", "ggggg", "5=", "GGGGG"},
        };
    }

    @Test(dataProvider = "basesTest")
    public void testCRAMRecordUpperCasesReadBases(
            final String refBases,
            final String originalReadBases,
            final String cigarString,
            final String expectedReadBases) {
        final SAMFileHeader header = new SAMFileHeader();

        final SAMRecord record = new SAMRecord(header);
        record.setReadName("test");
        record.setReadUnmappedFlag(true);
        record.setReadBases(originalReadBases.getBytes());
        record.setCigarString(cigarString);
        record.setBaseQualities(SAMRecord.NULL_QUALS);

        final CRAMCompressionRecord cramCompressionRecord = new CRAMCompressionRecord(
                CramVersions.CRAM_v3, new CRAMEncodingStrategy(), record, refBases.getBytes(), 1, new HashMap<>());

        Assert.assertNotEquals(cramCompressionRecord.getReadBases(), record.getReadBases());
        Assert.assertEquals(cramCompressionRecord.getReadBases(), expectedReadBases.getBytes());
    }

    @DataProvider(name = "emptyFeatureListProvider")
    public Object[][] testPositive() {
        return new Object[][] {
            // a matching base
            {"A", "A", "!"},
            // a matching ambiguity base
            {"R", "R", "!"},
        };
    }

    @Test(dataProvider = "emptyFeatureListProvider")
    public void testAddMismatchReadFeaturesNoReadFeaturesForMatch(
            final String refBases, final String readBases, final String fastqScores) {
        final List<ReadFeature> readFeatures = buildMatchOrMismatchReadFeatures(refBases, readBases, fastqScores);
        Assert.assertTrue(readFeatures.isEmpty());
    }

    /**
     * Test the outcome of a ACGTN mismatch.
     * The result should always be a {@link Substitution} read feature.
     */
    @Test
    public void testAddMismatchReadFeaturesSingleSubstitution() {
        final List<ReadFeature> readFeatures = buildMatchOrMismatchReadFeatures("A", "C", "!");

        Assert.assertEquals(1, readFeatures.size());

        final ReadFeature rf = readFeatures.get(0);
        Assert.assertTrue(rf instanceof Substitution);
        final Substitution substitution = (Substitution) rf;
        Assert.assertEquals(1, substitution.getPosition());
        Assert.assertEquals('C', substitution.getBase());
        Assert.assertEquals('A', substitution.getReferenceBase());
    }

    /**
     * Test the outcome of non-ACGTN ref and read bases mismatching each other.
     * The result should be explicit read base and score capture via {@link ReadBase}.
     */
    @Test
    public void testAddMismatchReadFeaturesAmbiguityMismatch() {
        final List<ReadFeature> readFeatures = buildMatchOrMismatchReadFeatures("R", "F", "1");
        Assert.assertEquals(1, readFeatures.size());

        final ReadFeature rf = readFeatures.get(0);
        Assert.assertTrue(rf instanceof ReadBase);
        final ReadBase readBaseFeature = (ReadBase) rf;
        Assert.assertEquals(1, readBaseFeature.getPosition());
        Assert.assertEquals('F', readBaseFeature.getBase());
        Assert.assertEquals(SAMUtils.fastqToPhred('1'), readBaseFeature.getQualityScore());
    }

    private List<ReadFeature> buildMatchOrMismatchReadFeatures(
            final String refBases, final String readBases, final String scores) {
        final List<ReadFeature> readFeatures = new ArrayList<>();
        final int fromPosInRead = 0;
        final int alignmentStartOffset = 0;
        final int nofReadBases = 1;
        CRAMRecordReadFeatures.addMismatchReadFeatures(
                refBases.getBytes(),
                1,
                readFeatures,
                fromPosInRead,
                alignmentStartOffset,
                nofReadBases,
                readBases.getBytes(),
                SAMUtils.fastqToPhred(scores));
        return readFeatures;
    }

    /**
     * One read of a pair on reference 0, matching the reference: READ1 on the forward strand and READ2 on the
     * reverse, with mate fields that agree.
     */
    private static SAMRecord pairRead(
            final boolean firstOfPair,
            final int start,
            final int alignedLength,
            final int mateStart,
            final int templateLength) {
        final SAMRecord read = new SAMRecord(CRAMStructureTestHelper.SAM_FILE_HEADER);
        read.setReadName("pair");
        read.setReadPairedFlag(true);
        read.setFirstOfPairFlag(firstOfPair);
        read.setSecondOfPairFlag(!firstOfPair);
        read.setReadNegativeStrandFlag(!firstOfPair);
        read.setMateNegativeStrandFlag(firstOfPair);
        read.setReferenceIndex(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO);
        read.setAlignmentStart(start);
        read.setCigarString(alignedLength + "M");
        final byte[] bases = new byte[alignedLength];
        Arrays.fill(bases, CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO_BYTE);
        read.setReadBases(bases);
        read.setBaseQualities(new byte[alignedLength]);
        read.setMateReferenceIndex(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO);
        read.setMateAlignmentStart(mateStart);
        read.setInferredInsertSize(templateLength);
        return read;
    }

    /** The CRAM record the writer builds for {@code read}. */
    private static CRAMCompressionRecord toCram(final SAMRecord read) {
        final byte[] reference = CRAMStructureTestHelper.REFERENCE_SOURCE.getReferenceBases(
                read.getHeader().getSequence(read.getReferenceIndex()), false);
        return new CRAMCompressionRecord(
                CramVersions.DEFAULT_CRAM_VERSION, new CRAMEncodingStrategy(), read, reference, 0, new HashMap<>());
    }

    @Test
    public void testTemplateLengthsOfAPairStartingTogetherArePositiveForTheReadEndingFirst() {
        final int[] lengths = CRAMCompressionRecord.deriveTemplateLengths(
                List.of(toCram(pairRead(true, 100, 50, 100, 0)), toCram(pairRead(false, 100, 30, 100, 0))));
        Assert.assertEquals(lengths, new int[] {-50, 50});
    }

    @Test
    public void testTemplateLengthsOfAPairOnTheSameBasesArePositiveForTheFirstSegment() {
        final int[] lengths = CRAMCompressionRecord.deriveTemplateLengths(
                List.of(toCram(pairRead(false, 100, 50, 100, 0)), toCram(pairRead(true, 100, 50, 100, 0))));
        Assert.assertEquals(lengths, new int[] {-50, 50});
    }

    @Test
    public void testTemplateLengthsSpanEveryRecordOfATemplate() {
        final SAMRecord middle = pairRead(true, 120, 50, 300, 0);
        middle.setFirstOfPairFlag(false);
        final int[] lengths = CRAMCompressionRecord.deriveTemplateLengths(List.of(
                toCram(pairRead(true, 100, 50, 120, 0)), toCram(middle), toCram(pairRead(false, 300, 50, 100, 0))));
        Assert.assertEquals(lengths, new int[] {250, -250, -250});
    }

    @Test
    public void testTemplateLengthsAreZeroForATemplateOnTwoReferences() {
        final SAMRecord second = pairRead(false, 300, 50, 100, 0);
        second.setReferenceIndex(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE);
        final int[] lengths = CRAMCompressionRecord.deriveTemplateLengths(
                List.of(toCram(pairRead(true, 100, 50, 300, 0)), toCram(second)));
        Assert.assertEquals(lengths, new int[] {0, 0});
    }

    @Test
    public void testTemplateLengthsAreZeroForAPairWithAnUnmappedRead() {
        final SAMRecord unmapped = pairRead(false, 100, 50, 100, 0);
        unmapped.setReadUnmappedFlag(true);
        final SAMRecord mapped = pairRead(true, 100, 50, 100, 0);
        mapped.setMateUnmappedFlag(true);
        final int[] lengths = CRAMCompressionRecord.deriveTemplateLengths(List.of(toCram(mapped), toCram(unmapped)));
        Assert.assertEquals(lengths, new int[] {0, 0});
    }

    @Test
    public void testConsistentPairIsAttached() {
        Assert.assertTrue(CRAMCompressionRecord.decodesUnchangedWhenAttached(
                toCram(pairRead(true, 100, 50, 300, 250)), toCram(pairRead(false, 300, 50, 100, -250))));
    }

    @Test
    public void testPairWhoseTemplateLengthsDecodingWouldSwapIsNotAttached() {
        // READ1 ends last, so decoding would make it negative.
        Assert.assertFalse(CRAMCompressionRecord.decodesUnchangedWhenAttached(
                toCram(pairRead(true, 100, 50, 100, 50)), toCram(pairRead(false, 100, 30, 100, -50))));
    }

    @Test
    public void testPairWhoseMateStrandDisagreesIsNotAttached() {
        final SAMRecord first = pairRead(true, 100, 50, 300, 250);
        first.setMateNegativeStrandFlag(false);
        Assert.assertFalse(CRAMCompressionRecord.decodesUnchangedWhenAttached(
                toCram(first), toCram(pairRead(false, 300, 50, 100, -250))));
    }

    @Test
    public void testPairOfTwoFirstSegmentsIsNotAttached() {
        final SAMRecord second = pairRead(false, 300, 50, 100, -250);
        second.setFirstOfPairFlag(true);
        second.setSecondOfPairFlag(false);
        Assert.assertFalse(CRAMCompressionRecord.decodesUnchangedWhenAttached(
                toCram(pairRead(true, 100, 50, 300, 250)), toCram(second)));
    }
}
