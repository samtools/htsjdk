package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.*;
import htsjdk.samtools.cram.build.CRAMReferenceRegion;
import htsjdk.samtools.cram.build.CompressionHeaderFactory;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.encoding.readfeatures.*;
import htsjdk.samtools.util.SequenceUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;

public class CRAMCompressionRecordTest extends HtsjdkTest {

    @DataProvider(name="alignmentEndData")
    public Object[][] getAlignmentEndTestData() {
        return new Object[][] {
                // readLength, alignmentStart, isMapped, readFeatures, expected alignmentEnd
                { 100, 5, true, null, 104},
                { 100, 10, true, Collections.singletonList(new SoftClip(1, "AAA".getBytes())), 100+10-1 -3},
                { 100, 10, true, Collections.singletonList(new Deletion(1, 5)), 100+10-1 +5},
                { 100, 30, true, Collections.singletonList(new Insertion(1, "CCCCCCCCCC".getBytes())), 100+30-1 -10},
                { 100, 40, true, Collections.singletonList(new InsertBase(1, (byte) 'A')), 100+40-1 -1}
        };
    }

    @Test(dataProvider="alignmentEndData")
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
                new byte[]{'a', 'a', 'a', 'a', 'a'},
                0,
                readFeatures);
        Assert.assertEquals(cramCompressionRecord.getAlignmentStart(), alignmentStart);
        Assert.assertEquals(cramCompressionRecord.getAlignmentEnd(), expectedAlignmentEnd);
    }

    @DataProvider(name = "placedTests")
    private Object[][] placedTests() {
        final List<Object[]> retval = new ArrayList<>();

        final int validSeqId = 0;
        final int[] sequenceIds = new int[]{ SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, validSeqId };
        final int validAlignmentStart = 1;
        final int[] starts = new int[]{ SAMRecord.NO_ALIGNMENT_START, validAlignmentStart };
        final boolean[] mappeds = new boolean[] { true, false };

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

                    retval.add(new Object[]{sequenceId, start, mapped, placementExpectation});
                }

            }
        }

        return retval.toArray(new Object[0][0]);
    }

    @DataProvider(name="baseNormalization")
    public Object[][] getBaseNormalization() {
        // ref bases, read bases, cigar string, expected roundtrip read bases
        return new Object[][]{
                {
                        // "acgt"
                        "NNNN", "acgt", "4M", "ACGT"
                },
                {
                        // CRAM turns everything to upper case, and preserves IUPAC codes except for ".", which goes toN
                        // ".aAbBcCdDgGhHkKmMnNrRsStTvVwWyY"
                        "NNNNNNNNNNNNNNNNNNNNNNNNNNNNNNN", SequenceUtil.getIUPACCodesString(), "31M", "NAABBCCDDGGHHKKMMNNRRSSTTVVWWYY"
                }
        };
    }

    /**
     * This checks that all read bases returned in the record from CRAMRecord
     * are from the BAM read base set.
     */
    @Test(dataProvider = "baseNormalization")
    public void testCRAMRecordBaseNormalization(
            final String refBases,
            final String readBases,
            final String cigarString,
            final String expectedReadBases) {
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

        final CompressionHeader compressionHeader = new CompressionHeaderFactory(new CRAMEncodingStrategy())
                .createCompressionHeader(cramRecords, true);
        final Slice slice = new Slice(cramRecords, compressionHeader, 0L, 0L);
        slice.setReferenceMD5(refBases.getBytes());
        slice.normalizeCRAMRecords(
                cramRecords,
                new CRAMReferenceRegion((sequenceRecord, tryNameVariants) -> refBases.getBytes(),
                        CRAMStructureTestHelper.SAM_FILE_HEADER));
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

    @Test(dataProvider="basesTest")
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
                CramVersions.CRAM_v3,
                new CRAMEncodingStrategy(),
                record,
                refBases.getBytes(),
                1,
                new HashMap<>());

        Assert.assertNotEquals(cramCompressionRecord.getReadBases(), record.getReadBases());
        Assert.assertEquals(cramCompressionRecord.getReadBases(), expectedReadBases.getBytes());
    }

    @DataProvider(name = "emptyFeatureListProvider")
    public Object[][] testPositive() {
        return new Object[][]{
                // a matching base
                {"A", "A", "!"},
                // a matching ambiguity base
                {"R", "R", "!"},
        };
    }

    @Test(dataProvider = "emptyFeatureListProvider")
    public void testAddMismatchReadFeaturesNoReadFeaturesForMatch(final String refBases, final String readBases, final String fastqScores) {
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

    private List<ReadFeature> buildMatchOrMismatchReadFeatures(final String refBases, final String readBases, final String scores) {
        final List<ReadFeature> readFeatures = new ArrayList<>();
        final int fromPosInRead = 0;
        final int alignmentStartOffset = 0;
        final int nofReadBases = 1;
        CRAMRecordReadFeatures.addMismatchReadFeatures(refBases.getBytes(),
                1,
                readFeatures,
                fromPosInRead,
                alignmentStartOffset,
                nofReadBases,
                readBases.getBytes(),
                SAMUtils.fastqToPhred(scores));
        return readFeatures;
    }

}
