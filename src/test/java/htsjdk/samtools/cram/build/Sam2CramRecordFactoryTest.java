package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMUtils;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.encoding.readfeatures.ReadBase;
import htsjdk.samtools.cram.encoding.readfeatures.ReadFeature;
import htsjdk.samtools.cram.encoding.readfeatures.Substitution;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.util.SequenceUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by vadim on 06/06/2017.
 */
public class Sam2CramRecordFactoryTest {

    /**
     * This checks that all read bases returned in the record from {@link Sam2CramRecordFactory#createCramRecord(SAMRecord)}
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

        final Sam2CramRecordFactory sam2CramRecordFactory = new Sam2CramRecordFactory(null, header, CramVersions.CRAM_v3);
        final CramCompressionRecord cramRecord = sam2CramRecordFactory.createCramRecord(record);

        Assert.assertNotEquals(cramRecord.readBases, record.getReadBases());
        Assert.assertEquals(cramRecord.readBases, SequenceUtil.toBamReadBasesInPlace(record.getReadBases()));
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
        final SAMFileHeader header = new SAMFileHeader();
        final CramCompressionRecord record = new CramCompressionRecord();
        record.alignmentStart = 1;
        final List<ReadFeature> readFeatures = new ArrayList<>();
        final int fromPosInRead = 0;
        final int alignmentStartOffset = 0;
        final int nofReadBases = 1;

        final Sam2CramRecordFactory sam2CramRecordFactory = new Sam2CramRecordFactory(refBases.getBytes(), header, CramVersions.CRAM_v3);
        sam2CramRecordFactory.addMismatchReadFeatures(record.alignmentStart, readFeatures, fromPosInRead, alignmentStartOffset, nofReadBases, readBases.getBytes(), SAMUtils.fastqToPhred(scores));
        return readFeatures;
    }
}
