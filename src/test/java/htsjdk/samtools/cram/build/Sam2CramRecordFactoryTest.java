package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMUtils;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.encoding.readfeatures.Bases;
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

    @Test
    public void testIsACGTN() {
        for (byte base = Byte.MIN_VALUE; base < Byte.MAX_VALUE; base++) {
            if (base == 'A' || base == 'C' || base == 'G' || base == 'T' || base == 'N') {
                Assert.assertTrue(Sam2CramRecordFactory.isACGTN(base));
            } else {
                Assert.assertFalse(Sam2CramRecordFactory.isACGTN(base));
            }
        }
    }

    /**
     * Test the outcome of a matching base.
     * The result should be empty - no base read features issued.
     */
    @Test
    public void testAddSubstitutionsAndMaskedBasesMatch() {
        final byte[] refBases = "A".getBytes();
        final byte[] readBases = "A".getBytes();
        final byte[] scores = "!".getBytes();

        final SAMFileHeader header = new SAMFileHeader();
        final CramCompressionRecord record = new CramCompressionRecord();
        record.alignmentStart = 1;
        final List<ReadFeature> readFeatures = new ArrayList<>();
        final int fromPosInRead = 0;
        final int alignmentStartOffset = 0;
        final int nofReadBases = 1;

        final Sam2CramRecordFactory s2mFactory_v2 = new Sam2CramRecordFactory(refBases, header, CramVersions.CRAM_v2_1);
        s2mFactory_v2.addSubstitutionsAndMaskedBases(record, readFeatures, fromPosInRead, alignmentStartOffset, nofReadBases, readBases, scores);
        Assert.assertEquals(0, readFeatures.size());

        final Sam2CramRecordFactory s2mFactory_v3 = new Sam2CramRecordFactory(refBases, header, CramVersions.CRAM_v3);
        s2mFactory_v3.addSubstitutionsAndMaskedBases(record, readFeatures, fromPosInRead, alignmentStartOffset, nofReadBases, readBases, scores);
        Assert.assertEquals(0, readFeatures.size());
    }

    /**
     * Test the outcome of a matching ambiguity base.
     * The result should be empty - no base read features issued.
     */
    @Test
    public void testAddSubstitutionsAndMaskedBasesAmbiguityMatch() {
        final byte[] refBases = "R".getBytes();
        final byte[] readBases = "R".getBytes();
        final byte[] scores = "!".getBytes();

        final SAMFileHeader header = new SAMFileHeader();
        final CramCompressionRecord record = new CramCompressionRecord();
        record.alignmentStart = 1;
        final List<ReadFeature> readFeatures = new ArrayList<>();
        final int fromPosInRead = 0;
        final int alignmentStartOffset = 0;
        final int nofReadBases = 1;

        final Sam2CramRecordFactory s2mFactory_v2 = new Sam2CramRecordFactory(refBases, header, CramVersions.CRAM_v2_1);
        s2mFactory_v2.addSubstitutionsAndMaskedBases(record, readFeatures, fromPosInRead, alignmentStartOffset, nofReadBases, readBases, scores);
        Assert.assertEquals(0, readFeatures.size());

        final Sam2CramRecordFactory s2mFactory_v3 = new Sam2CramRecordFactory(refBases, header, CramVersions.CRAM_v3);
        s2mFactory_v3.addSubstitutionsAndMaskedBases(record, readFeatures, fromPosInRead, alignmentStartOffset, nofReadBases, readBases, scores);
        Assert.assertEquals(0, readFeatures.size());
    }

    /**
     * Test the outcome of a ACTGN mismatch.
     * The result should always be a {@link Substitution} read feature.
     */
    @Test
    public void testAddSubstitutionsAndMaskedBasesSingleSubstitution() {
        final byte[] refBases = "A".getBytes();
        final byte[] readBases = "C".getBytes();
        final byte[] scores = "!".getBytes();

        final SAMFileHeader header = new SAMFileHeader();
        final CramCompressionRecord record = new CramCompressionRecord();
        record.alignmentStart = 1;
        final List<ReadFeature> readFeatures = new ArrayList<>();
        final int fromPosInRead = 0;
        final int alignmentStartOffset = 0;
        final int nofReadBases = 1;

        final Sam2CramRecordFactory s2mFactory_v2 = new Sam2CramRecordFactory(refBases, header, CramVersions.CRAM_v2_1);
        s2mFactory_v2.addSubstitutionsAndMaskedBases(record, readFeatures, fromPosInRead, alignmentStartOffset, nofReadBases, readBases, scores);
        Assert.assertEquals(1, readFeatures.size());

        ReadFeature rf = readFeatures.get(0);
        Assert.assertTrue(rf instanceof Substitution);
        Substitution substitution = (Substitution) rf;
        Assert.assertEquals(1, substitution.getPosition());
        Assert.assertEquals(readBases[0], substitution.getBase());
        Assert.assertEquals(refBases[0], substitution.getReferenceBase());

        readFeatures.clear();
        final Sam2CramRecordFactory s2mFactory_v3 = new Sam2CramRecordFactory(refBases, header, CramVersions.CRAM_v3);
        s2mFactory_v3.addSubstitutionsAndMaskedBases(record, readFeatures, fromPosInRead, alignmentStartOffset, nofReadBases, readBases, scores);
        Assert.assertEquals(1, readFeatures.size());

        rf = readFeatures.get(0);
        Assert.assertTrue(rf instanceof Substitution);
        substitution = (Substitution) rf;
        Assert.assertEquals(1, substitution.getPosition());
        Assert.assertEquals(readBases[0], substitution.getBase());
        Assert.assertEquals(refBases[0], substitution.getReferenceBase());
    }

    /**
     * Test the outcome of non-ACGTN ref and read bases mismatching each other.
     * The result should be explicit read base and score capture via {@link ReadBase}.
     */
    @Test
    public void testAddSubstitutionsAndMaskedBasesAmbiguityMismatch() {
        final byte[] refBases = "R".getBytes();
        final byte[] readBases = "F".getBytes();
        final byte[] scores = SAMUtils.fastqToPhred("!");

        final SAMFileHeader header = new SAMFileHeader();
        final CramCompressionRecord record = new CramCompressionRecord();
        record.alignmentStart = 1;
        final List<ReadFeature> readFeatures = new ArrayList<>();
        final int fromPosInRead = 0;
        final int alignmentStartOffset = 0;
        final int nofReadBases = 1;

        final Sam2CramRecordFactory s2mFactory_v2 = new Sam2CramRecordFactory(refBases, header, CramVersions.CRAM_v2_1);
        s2mFactory_v2.addSubstitutionsAndMaskedBases(record, readFeatures, fromPosInRead, alignmentStartOffset, nofReadBases, readBases, scores);
        Assert.assertEquals(1, readFeatures.size());

        ReadFeature rf = readFeatures.get(0);
        Assert.assertTrue(rf instanceof ReadBase);
        ReadBase readBaseFeature = (ReadBase) rf;
        Assert.assertEquals(1, readBaseFeature.getPosition());
        Assert.assertEquals(readBases[0], readBaseFeature.getBase());
        Assert.assertEquals(scores[0], readBaseFeature.getQualityScore());


        readFeatures.clear();
        final Sam2CramRecordFactory s2mFactory_v3 = new Sam2CramRecordFactory(refBases, header, CramVersions.CRAM_v3);
        s2mFactory_v3.addSubstitutionsAndMaskedBases(record, readFeatures, fromPosInRead, alignmentStartOffset, nofReadBases, readBases, scores);
        Assert.assertEquals(1, readFeatures.size());

        rf = readFeatures.get(0);
        Assert.assertTrue(rf instanceof ReadBase);
        readBaseFeature = (ReadBase) rf;
        Assert.assertEquals(1, readBaseFeature.getPosition());
        Assert.assertEquals(readBases[0], readBaseFeature.getBase());
        Assert.assertEquals(scores[0], readBaseFeature.getQualityScore());
    }
}
