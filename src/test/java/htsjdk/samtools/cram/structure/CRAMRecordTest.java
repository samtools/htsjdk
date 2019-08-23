package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.*;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.encoding.readfeatures.*;
import htsjdk.samtools.util.SequenceUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;

public class CRAMRecordTest extends HtsjdkTest {

    //TODO: need round trip tests (CRAMRecord fromSAMRecord/toSAMRecord) based on SAMRecord cigar strings

    @DataProvider(name="getAlignmentEndData")
    public Object[][] getAlignmentEndTestData() {
        return new Object[][] {
                // readLength, alignmentStart, isMapped, readFeatures, expected alignmentEnd
                { 100, 5, true, null, 104},
                { 100, 10, true, Collections.singletonList(new SoftClip(1, "AAA".getBytes())), 100-1+10-3},
                //TODO: why does a Deletion result in +5
                { 100, 10, true, Collections.singletonList(new Deletion(1, 5)), 100-1+10+5},
                //TODO: why does an Insertion result in -10
                { 100, 30, true, Collections.singletonList(new Insertion(1, "CCCCCCCCCC".getBytes())), 100-1+30-10},
                //TODO: why does an InsertBase result in -10
                { 100, 40, true, Collections.singletonList(new InsertBase(1, (byte) 'A')), 100-1+40-1}
        };
    }

    @Test(dataProvider="getAlignmentEndData")
    public void testAlignmentEnd(
            final int readLength,
            final int alignmentStart,
            final boolean isMapped,
            final List<ReadFeature> readFeatures,
            final int expectedAlignmentEnd) {
        final CRAMRecord cramRecord = CRAMRecordTestHelper.getCRAMRecordWithReadFeatures(
                "rname",
                readLength,
                0,
                alignmentStart,
                isMapped ? 0 : SAMFlag.READ_UNMAPPED.intValue(),
                0,
                new byte[]{'a', 'a', 'a', 'a', 'a'},
                0,
                readFeatures);
        Assert.assertEquals(cramRecord.getAlignmentStart(), alignmentStart);
        Assert.assertEquals(cramRecord.getAlignmentEnd(), expectedAlignmentEnd);
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

//    @Test(dataProvider = "placedTests")
//    public void test_isPlaced(final int sequenceId,
//                              final int alignmentStart,
//                              final boolean mapped,
//                              final boolean placementExpectation) {
//        final CramCompressionRecord r = new CramCompressionRecord();
//        r.sequenceId = sequenceId;
//        r.alignmentStart = alignmentStart;
//        r.setSegmentUnmapped(!mapped);
//        Assert.assertEquals(r.isPlaced(), placementExpectation);
//    }
//
//    @Test(dataProvider = "placedTests")
//    public void test_placementForAlignmentSpanAndEnd(final int sequenceId,
//                                                     final int alignmentStart,
//                                                     final boolean mapped,
//                                                     final boolean placementExpectation) {
//        final CramCompressionRecord r = new CramCompressionRecord();
//        r.sequenceId = sequenceId;
//        r.alignmentStart = alignmentStart;
//        r.setSegmentUnmapped(!mapped);
//        final int readLength = 100;
//        r.readLength = readLength;
//
//        if (placementExpectation) {
//            Assert.assertEquals(r.getAlignmentSpan(), readLength);
//            Assert.assertEquals(r.getAlignmentEnd(), alignmentStart + readLength - 1);
//        }
//        else {
//            Assert.assertEquals(r.getAlignmentSpan(), Slice.NO_ALIGNMENT_SPAN);
//            Assert.assertEquals(r.getAlignmentEnd(), Slice.NO_ALIGNMENT_END);
//        }
//    }

//    @Test
//    public void testEqualsAndHashCodeAreConsistent() {
//        final List<CramCompressionRecord> records = new ArrayList<>();
//
//        final List<ReadFeature> features = new ArrayList<>();
//        String softClip = "AAA";
//        features.add(new SoftClip(1, softClip.getBytes()));
//        String insertion = "CCCCCCCCCC";
//        features.add(new Insertion(1, insertion.getBytes()));
//
//        for (int alignmentStart : new int[] {0, 1}) {
//            for (int readLength : new int[] {100, 101}) {
//                for (int flags : new int[] {0, 0x4}) {
//                    for (List<ReadFeature> readFeatures : Lists.<List<ReadFeature>>newArrayList(null, new ArrayList<>(), features)) {
//                        for (String readName : new String[] {null, "", "r"}) {
//                            for (byte[] readBases : new byte[][]{null, new byte[]{(byte) 'A', (byte) 'C'}}) {
//                                for (byte[] qualityScores : new byte[][]{null, new byte[]{(byte) 1, (byte) 2}}) {
//                                    final CramCompressionRecord r = new CramCompressionRecord();
//                                    r.alignmentStart = alignmentStart;
//                                    r.readLength = readLength;
//                                    r.flags = flags;
//                                    r.readFeatures = readFeatures;
//                                    r.readName = readName;
//                                    r.readBases = readBases;
//                                    r.qualityScores = qualityScores;
//                                    records.add(r);
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        for (CramCompressionRecord r1 : records) {
//            for (CramCompressionRecord r2 : records) {
//                if (r1.equals(r2)) {
//                    Assert.assertEquals(r1.hashCode(), r2.hashCode(), String.format("Comparing %s and %s", r1, r2));
//                }
//            }
//        }
//    }
    /**
     * This checks that all read bases returned in the record from CRAMRecord
     * are from the BAM read base set.
     */
    @Test
    public void testReadBaseNormalization() {
        final SAMFileHeader header = new SAMFileHeader();

        final SAMRecord record = new SAMRecord(header);
        record.setReadName("test");
        record.setReadUnmappedFlag(true);
        record.setReadBases(SequenceUtil.getIUPACCodesString().getBytes());
        record.setBaseQualities(SAMRecord.NULL_QUALS);

        final CRAMRecord cramRecord = new CRAMRecord(
                CramVersions.CRAM_v3,
                new CRAMEncodingStrategy(),
                record,
                new byte[1],
                1,
                new HashMap<>());

        Assert.assertNotEquals(cramRecord.getReadBases(), record.getReadBases());
        Assert.assertEquals(cramRecord.getReadBases(), SequenceUtil.toBamReadBasesInPlace(record.getReadBases()));
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
//        final CramCompressionRecord record = new CramCompressionRecord();
//        record.alignmentStart = 1;
        final List<ReadFeature> readFeatures = new ArrayList<>();
        final int fromPosInRead = 0;
        final int alignmentStartOffset = 0;
        final int nofReadBases = 1;

//        final Sam2CramRecordFactory sam2CramRecordFactory = new Sam2CramRecordFactory(
//                new CRAMEncodingStrategy(),
//                refBases.getBytes(),
//                header,
//                CramVersions.CRAM_v3);
//        final byte[] refBases,
//        final int alignmentStart,
//        final List<ReadFeature> features,
//        final int fromPosInRead,
//        final int alignmentStartOffset,
//        final int nofReadBases,
//        final byte[] bases,
//        final byte[] qualityScore)
        CRAMRecord.addMismatchReadFeatures(refBases.getBytes(),
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
