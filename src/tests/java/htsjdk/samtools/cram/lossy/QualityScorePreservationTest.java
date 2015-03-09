package htsjdk.samtools.cram.lossy;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.cram.build.CramNormalizer;
import htsjdk.samtools.cram.build.Sam2CramRecordFactory;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.ref.ReferenceTracks;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.util.CloserUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.testng.Assert.*;

public class QualityScorePreservationTest {

    @Test
    public void test1() {
        QualityScorePreservation p = new QualityScorePreservation("m999_8");
        List<PreservationPolicy> policies = p.getPreservationPolicies();

        assertNotNull(p);
        assertEquals(policies.size(), 1);

        PreservationPolicy policy0 = policies.get(0);
        assertEquals(policy0.readCategory.type,
                ReadCategoryType.LOWER_MAPPING_SCORE);

        assertEquals(policy0.readCategory.param, 999);

        if (policy0.baseCategories != null)
            assertEquals(policy0.baseCategories.isEmpty(), true);

        QualityScoreTreatment treatment = policy0.treatment;
        assertNotNull(treatment);

        assertEquals(treatment.type, QualityScoreTreatmentType.BIN);
        assertEquals(treatment.param, 8);
    }

    @Test
    public void test2() {
        QualityScorePreservation p = new QualityScorePreservation("R8-N40");
        List<PreservationPolicy> policies = p.getPreservationPolicies();

        assertNotNull(p);
        assertEquals(policies.size(), 2);

        {
            PreservationPolicy policy0 = policies.get(0);
            assertNull(policy0.readCategory);

            List<BaseCategory> baseCategories = policy0.baseCategories;
            assertNotNull(baseCategories);
            assertEquals(baseCategories.size(), 1);

            BaseCategory c0 = baseCategories.get(0);
            assertEquals(c0.type, BaseCategoryType.MATCH);
            assertEquals(c0.param, -1);

            QualityScoreTreatment treatment = policy0.treatment;
            assertNotNull(treatment);

            assertEquals(treatment.type, QualityScoreTreatmentType.BIN);
            assertEquals(treatment.param, 8);
        }

        {
            PreservationPolicy policy1 = policies.get(1);
            assertNull(policy1.readCategory);

            List<BaseCategory> baseCategories = policy1.baseCategories;
            assertNotNull(baseCategories);
            assertEquals(baseCategories.size(), 1);

            BaseCategory c0 = baseCategories.get(0);
            assertEquals(c0.type, BaseCategoryType.MISMATCH);
            assertEquals(c0.param, -1);

            QualityScoreTreatment treatment = policy1.treatment;
            assertNotNull(treatment);

            assertEquals(treatment.type, QualityScoreTreatmentType.PRESERVE);
            assertEquals(treatment.param, 40);
        }
    }

    private SAMFileHeader samFileHeader = new SAMFileHeader();

    private SAMRecord buildSAMRecord(String seqName, String line) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write("@HD\tVN:1.0\tGO:none SO:coordinate\n".getBytes());
            baos.write(("@SQ\tSN:" + seqName + "\tLN:247249719\n").getBytes());
            baos.write(line.replaceAll("\\s+", "\t").getBytes());
            baos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        SamReader reader = SamReaderFactory.makeDefault().open(SamInputResource.of(bais));
        try {
            return reader.iterator().next();
        } finally {
            CloserUtil.close(reader);
        }
    }

    @Test
    public void test3() {
        String line1 = "98573 0 20 1 10 40M * 0 0 AAAAAAAAAA !!!!!!!!!!";
        String seqName = "20";

        byte[] ref = new byte[40];
        Arrays.fill(ref, (byte) 'A');
        SAMRecord record = buildSAMRecord(seqName, line1);
        byte[] b1 = new byte[40];
        Arrays.fill(b1, (byte) 'A');
        byte[] s1 = new byte[40];
        for (int i = 0; i < s1.length; i++)
            s1[i] = 0;

        record.setReadBases(b1);
        record.setBaseQualities(s1);

        QualityScorePreservation p = new QualityScorePreservation("R8-*40");
        byte[] scores = compressScores(record, ref, p);
        assertEquals(record.getBaseQualities(), scores);

        Arrays.fill(record.getReadBases(), (byte)'C') ;
        scores = compressScores(record, ref, p);
        assertEquals(record.getBaseQualities(), scores);
        for (int i=0; i<scores.length; i++) {
            Assert.assertEquals(scores[i], Binning.ILLUMINA_BINNING_MATRIX[record.getBaseQualities()[i]]);
        }
    }

    private byte[] compressScores (SAMRecord record, byte[] ref, QualityScorePreservation p) {
        ReferenceTracks tracks = new ReferenceTracks(0, record.getReferenceName(), ref);

        Sam2CramRecordFactory f = new Sam2CramRecordFactory(ref, record.getHeader(), CramVersions.CRAM_v3);
        CramCompressionRecord cramRecord = f.createCramRecord(record);

        p.addQualityScores(record, cramRecord, tracks);
        if (!cramRecord.isForcePreserveQualityScores()) {
            CramNormalizer.restoreQualityScores((byte) 30, Collections.singletonList(cramRecord));
        }
        return cramRecord.qualityScores;
    }
}
