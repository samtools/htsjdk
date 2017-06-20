package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMUtils;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.encoding.readfeatures.ReadBase;
import htsjdk.samtools.cram.encoding.readfeatures.ReadFeature;
import htsjdk.samtools.cram.encoding.readfeatures.Substitution;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by vadim on 06/06/2017.
 */
public class Sam2CramRecordFactoryTest {


    /**
     * Test the outcome of a matching base.
     * The result should be empty - no base read features issued.
     */
    @Test
    public void testAddSubstitutionsAndMaskedBasesMatch() {
        final List<ReadFeature> readFeatures = buildMatchOrMismatchReadFeatures("A", "A", "!");
        Assert.assertTrue(readFeatures.isEmpty());
    }

    /**
     * Test the outcome of a matching ambiguity base.
     * The result should be empty - no base read features issued.
     */
    @Test
    public void testAddSubstitutionsAndMaskedBasesAmbiguityMatch() {
        final List<ReadFeature> readFeatures = buildMatchOrMismatchReadFeatures("R", "R", "!");
        Assert.assertTrue(readFeatures.isEmpty());
    }

    /**
     * Test the outcome of a ACTGN mismatch.
     * The result should always be a {@link Substitution} read feature.
     */
    @Test
    public void testAddSubstitutionsAndMaskedBasesSingleSubstitution() {
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
    public void testAddSubstitutionsAndMaskedBasesAmbiguityMismatch() {
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
