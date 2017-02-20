package htsjdk.samtools.cram.build;

import htsjdk.samtools.*;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Created by vadim on 20/02/2017.
 */
public class Sam2CramRecordFactoryTest {

    @Test
    public void testPaired10M() {
        byte[] ref = "ACGTNACGTN".getBytes();
        SAMFileHeader header = new SAMFileHeader(new SAMSequenceDictionary(Arrays.asList(new SAMSequenceRecord[]{new SAMSequenceRecord("1", ref.length)})));
        Sam2CramRecordFactory factory = new Sam2CramRecordFactory(ref, header, CramVersions.CRAM_v3);

        SAMRecord samRecord = new SAMRecord(header);
        samRecord.setReadName("name");
        samRecord.setReadPairedFlag(true);
        samRecord.setFirstOfPairFlag(true);
        samRecord.setProperPairFlag(true);
        samRecord.setReferenceIndex(0);
        samRecord.setAttribute("AS", 123);
        samRecord.setAlignmentStart(1);
        samRecord.setCigar(new Cigar(Arrays.asList(new CigarElement[]{new CigarElement(10, CigarOperator.M)})));
        samRecord.setReadBases(ref);
        samRecord.setBaseQualities(ref);

        samRecord.setMateReferenceIndex(0);
        samRecord.setMateAlignmentStart(100);
        samRecord.setInferredInsertSize(100);

        CramCompressionRecord cramRecord = factory.createCramRecord(samRecord);
        Assert.assertEquals(cramRecord.readName, samRecord.getReadName());

        Assert.assertEquals(cramRecord.isMultiFragment(), samRecord.getReadPairedFlag());
        Assert.assertEquals(cramRecord.isProperPair(), samRecord.getProperPairFlag());
        Assert.assertEquals(cramRecord.isSegmentUnmapped(), samRecord.getReadUnmappedFlag());
        Assert.assertEquals(cramRecord.isNegativeStrand(), samRecord.getReadNegativeStrandFlag());
        Assert.assertEquals(cramRecord.isMateNegativeStrand(), samRecord.getMateNegativeStrandFlag());
        Assert.assertEquals(cramRecord.isFirstSegment(), samRecord.getFirstOfPairFlag());
        Assert.assertEquals(cramRecord.isLastSegment(), samRecord.getSecondOfPairFlag());
        Assert.assertEquals(cramRecord.isSecondaryAlignment(), samRecord.getNotPrimaryAlignmentFlag());
        Assert.assertEquals(cramRecord.isVendorFiltered(), samRecord.getReadFailsVendorQualityCheckFlag());
        Assert.assertEquals(cramRecord.isDuplicate(), samRecord.getDuplicateReadFlag());
        Assert.assertEquals(cramRecord.isSupplementary(), samRecord.getSupplementaryAlignmentFlag());

        Assert.assertEquals(new Integer(cramRecord.sequenceId), samRecord.getReferenceIndex());
        Assert.assertEquals(cramRecord.alignmentStart, samRecord.getAlignmentStart());
        Assert.assertEquals(cramRecord.mappingQuality, samRecord.getMappingQuality());

        Assert.assertEquals(new Integer(cramRecord.mateSequenceID), samRecord.getMateReferenceIndex());
        Assert.assertEquals(cramRecord.mateAlignmentStart, samRecord.getMateAlignmentStart());

        Assert.assertEquals(cramRecord.readBases, samRecord.getReadBases());
        Assert.assertEquals(cramRecord.qualityScores, samRecord.getBaseQualities());

        List<SAMRecord.SAMTagAndValue> samRecordAttributes = samRecord.getAttributes();
        Assert.assertEquals(cramRecord.tags == null, (samRecordAttributes == null ^ samRecordAttributes.isEmpty()));
        if (samRecordAttributes != null) {
            int cramRecordAttributeCount = 0;
            SAMBinaryTagAndValue tv = cramRecord.tags;
            while (tv != null) {
                tv = tv.getNext();
                cramRecordAttributeCount++;
            }
            Assert.assertEquals(cramRecordAttributeCount, samRecordAttributes.size());

            for (SAMRecord.SAMTagAndValue tv2 : samRecordAttributes) {
                SAMBinaryTagAndValue cramTV = cramRecord.tags.find(SAMTagUtil.getSingleton().makeBinaryTag(tv2.tag));
                Assert.assertEquals(cramTV.isUnsignedArray(), samRecord.isUnsignedArrayAttribute(tv2.tag));
                Assert.assertEquals(cramTV.value, tv2.value);
            }
        }

        // cigar is not stored, test a restored value:
        Assert.assertEquals(Cram2SamRecordFactory.getCigar(cramRecord.readFeatures, cramRecord.readLength), samRecord.getCigar());

        // inferredInsertSize is not stored, calculate by simulating a mate record:
        CramCompressionRecord mate = new CramCompressionRecord();
        mate.alignmentStart = cramRecord.mateAlignmentStart;
        mate.mateAlignmentStart = cramRecord.alignmentStart;
        mate.setSegmentUnmapped(false);
        mate.sequenceId = cramRecord.sequenceId;
        mate.setNegativeStrand(cramRecord.isMateNegativeStrand());
        mate.setMateNegativeStrand(cramRecord.isNegativeStrand());

        mate.setLastSegment(cramRecord.isFirstSegment());
        mate.setFirstSegment(cramRecord.isLastSegment());

        int inferredInsertSize = CramNormalizer.computeInsertSize(cramRecord, mate);
        Assert.assertEquals(inferredInsertSize, samRecord.getInferredInsertSize());
    }

}
