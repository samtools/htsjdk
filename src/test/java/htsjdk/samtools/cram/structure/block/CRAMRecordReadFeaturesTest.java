package htsjdk.samtools.cram.structure.block;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.Cigar;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.TextCigarCodec;
import htsjdk.samtools.cram.encoding.readfeatures.Deletion;
import htsjdk.samtools.cram.encoding.readfeatures.HardClip;
import htsjdk.samtools.cram.encoding.readfeatures.Insertion;
import htsjdk.samtools.cram.encoding.readfeatures.ReadFeature;
import htsjdk.samtools.cram.structure.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class CRAMRecordReadFeaturesTest extends HtsjdkTest {

    @DataProvider(name = "cigarTest")
    public final Object[][] getBasesTests() {
        return new Object[][] {
            // ref bases, read bases, cigar string, expected cigar string
            {"aaaaa", "acgta", "5M", "5M"},
            {"aaaaa", "ttttt", "5X", "5M"}, // X -> M
            {"aaaaa", "aaaaa", "5=", "5M"}, // = -> M
        };
    }

    @Test(dataProvider = "cigarTest")
    public void testCigarFidelity(
            final String readBases, final String refBases, final String cigarString, final String expectedCigarString) {
        final SAMRecord samRecord = CRAMStructureTestHelper.createSAMRecordMapped(0, 1);
        samRecord.setReadBases(readBases.getBytes());
        samRecord.setCigarString(cigarString);

        final CRAMRecordReadFeatures rf =
                new CRAMRecordReadFeatures(samRecord, readBases.getBytes(), refBases.getBytes());
        final Cigar cigar = rf.getCigarForReadFeatures(readBases.length());
        Assert.assertEquals(cigar.toString(), expectedCigarString);
    }

    @DataProvider(name = "readFeatureTestData")
    private Object[][] getReadFeatureTestData() {
        final String testDir = "src/test/resources/htsjdk/samtools/cram/";
        return new Object[][] {
            // cram, sam, reference (may be null)

            // test CRAM file taken from the CRAM test files in hts-specs, has reads with ReadBase ('B') and
            // Bases ('b') feature codes
            {testDir + "0503_mapped.cram", testDir + "0503_mapped.sam", testDir + "ce.fa"},

            // test CRAM file (provided as part of https://github.com/samtools/htsjdk/issues/1379) does not
            // use reference-based compression (requires no reference) and uses Bases ('b') and SoftClip ('S')
            // feature codes; with the sam file created from the cram via samtools
            {testDir + "referenceNotRequired.cram", testDir + "referenceNotRequired.sam", null},

            // test CRAM file taken from the CRAM test files in hts-specs, has Scores ('q') read feature
            // Note: the specs site compliance documentation for this test file says:
            // Quality absent, mapped with diff (1005_qual.cram) As 1004_qual.cram but using 'q' instead of a series
            // of 'Q' features. [ complex to generate! see CRAM.q.gen.patch ]
            {testDir + "1005_qual.cram", testDir + "1005_qual.sam", testDir + "ce.fa"}
        };
    }

    @Test(dataProvider = "readFeatureTestData")
    private void readFeatureTest(final String cramFileName, final String samFileName, final String referenceFileName)
            throws IOException {
        // ensure these are handled correctly on read by comparing the SAMRecords created when reading the
        // CRAM with the SAMRecords from the corresponding truth SAM (see
        // https://github.com/samtools/htsjdk/issues/1379)
        final Path testCRAM = Path.of(cramFileName);
        final Path testSAM = Path.of(samFileName);
        final Path referenceFile = referenceFileName == null ? null : Path.of(referenceFileName);

        try (final SamReader cramReader =
                        SamReaderFactory.make().referenceSequence(referenceFile).open(testCRAM);
                final SamReader samReader =
                        SamReaderFactory.make().referenceSequence(referenceFile).open(testSAM)) {

            final SAMRecordIterator cramIterator = cramReader.iterator();
            final SAMRecordIterator samIterator = samReader.iterator();
            while (samIterator.hasNext() && cramIterator.hasNext()) {
                final SAMRecord samRecord = samIterator.next();
                final SAMRecord cramRecord = cramIterator.next();

                Assert.assertEquals(samRecord.getReadBases(), cramRecord.getReadBases());
                Assert.assertEquals(samRecord.getBaseQualities(), cramRecord.getBaseQualities());
                Assert.assertEquals(samRecord.getCigarString(), cramRecord.getCigarString());
            }
            Assert.assertEquals(samIterator.hasNext(), cramIterator.hasNext());
        }
    }

    private static final String REFERENCE = "ACGTACGTACGTACGTACGTACGTACGTACGTACGTACGT";

    /**
     * The read features htsjdk wrote before 6.0.0 for a mapped read with SEQ "*": it stored RL 0 and encoded the
     * read as if every base were 'N', so each aligned base became a substitution against the reference.
     */
    private static List<ReadFeature> featuresWrittenBefore6(final String cigarString) {
        final SAMRecord samRecord = CRAMStructureTestHelper.createSAMRecordMapped(0, 1);
        samRecord.setCigarString(cigarString);
        final byte[] nBases = new byte[TextCigarCodec.decode(cigarString).getReadLength()];
        Arrays.fill(nBases, (byte) 'N');
        return new CRAMRecordReadFeatures(samRecord, nBases, REFERENCE.getBytes()).getReadFeaturesList();
    }

    /** Decode a record without bases; the reference and substitution matrix are unused for one. */
    private static CRAMRecordReadFeatures.DecodeResult decodeWithoutBases(
            final List<ReadFeature> features, final int readLength) {
        return CRAMRecordReadFeatures.restoreBasesAndTags(features, true, 1, readLength, null, null, false);
    }

    @Test
    public void recordWithoutBasesOrFeaturesHasNoCigar() {
        final CRAMRecordReadFeatures.DecodeResult result = decodeWithoutBases(Collections.emptyList(), 0);
        Assert.assertEquals(result.cigar.toString(), "*");
        Assert.assertEquals(result.readBases, SAMRecord.NULL_SEQUENCE);
    }

    @Test
    public void recordWrittenBefore6WithoutBasesKeepsAMatchOnlyCigar() {
        final CRAMRecordReadFeatures.DecodeResult result = decodeWithoutBases(featuresWrittenBefore6("20M"), 0);
        Assert.assertEquals(result.cigar.toString(), "20M");
        Assert.assertEquals(result.readBases, SAMRecord.NULL_SEQUENCE);
    }

    @Test
    public void recordWrittenBefore6WithoutBasesKeepsItsSoftClips() {
        Assert.assertEquals(
                decodeWithoutBases(featuresWrittenBefore6("5S15M"), 0).cigar.toString(), "5S15M");
    }

    @Test
    public void recordWrittenBefore6WithoutBasesKeepsItsInsertion() {
        Assert.assertEquals(
                decodeWithoutBases(featuresWrittenBefore6("10M2I8M"), 0).cigar.toString(), "10M2I8M");
    }

    @Test
    public void recordWrittenBefore6WithoutBasesKeepsItsDeletion() {
        Assert.assertEquals(
                decodeWithoutBases(featuresWrittenBefore6("4M1D5M"), 0).cigar.toString(), "4M1D5M");
    }

    @Test
    public void recordWrittenBefore6WithoutBasesKeepsItsHardClips() {
        Assert.assertEquals(
                decodeWithoutBases(featuresWrittenBefore6("5H10M5H"), 0).cigar.toString(), "5H10M5H");
    }

    @Test
    public void recordWrittenBefore6WithoutBasesEndsWhereItsCigarEnds() {
        final CRAMRecordReadFeatures features = new CRAMRecordReadFeatures(featuresWrittenBefore6("3S4M1D5M"));
        Assert.assertEquals(features.getAlignmentEnd(100, 0), 109);
    }

    @Test
    public void hardClipOnlyRecordWithoutBasesKeepsItsCigar() {
        Assert.assertEquals(
                decodeWithoutBases(List.of(new HardClip(1, 10)), 0).cigar.toString(), "10H");
    }

    @Test
    public void zeroLengthOperationsAreDroppedFromTheCigar() {
        final List<ReadFeature> features =
                List.of(new HardClip(1, 5), new Insertion(1, new byte[0]), new Deletion(11, 0), new HardClip(11, 5));
        Assert.assertEquals(decodeWithoutBases(features, 10).cigar.toString(), "5H10M5H");
    }

    @Test
    public void recordWithOnlyZeroLengthOperationsHasNoCigar() {
        Assert.assertEquals(
                decodeWithoutBases(List.of(new Insertion(1, new byte[0])), 0)
                        .cigar
                        .toString(),
                "*");
    }
}
