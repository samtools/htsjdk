package htsjdk.samtools.cram.structure;

import htsjdk.samtools.*;
import htsjdk.samtools.cram.encoding.readfeatures.ReadFeature;
import htsjdk.utils.ValidationUtils;
import org.testng.annotations.BeforeTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class CRAMRecordTestHelper {

    final SAMRecordSetBuilder recordSetBuilder = new SAMRecordSetBuilder();
    final Map<String, Integer> readGroupMap = new HashMap<String, Integer>() {{
        readGroupMap.put("chr1", 1);
        readGroupMap.put("chr2", 2);
    }};

    @BeforeTest
    public void setupRecordSetBuilder() {
        recordSetBuilder.setHeader(createSAMHeaderWithTwoReadGroups(SAMFileHeader.SortOrder.coordinate));
    }

    private static byte[] makeUpQualityScoresFor(final byte[] readBases) {
        final byte[] scores = new byte[readBases.length];
        IntStream.range(0, readBases.length).forEach(i -> scores[i] = 30);
        return scores;
    }

    public static CRAMCompressionRecord getCRAMRecordWithTags(
            final String readName,
            final int readLength,
            final int referenceIndex,
            final int alignmentStart,
            final int templateSize,
            final byte[] readBases,
            int readGroupID,
            List<ReadTag> tagList) {
        ValidationUtils.validateArg(readBases != null && readBases.length > 0, "must have read bases");
        return new CRAMCompressionRecord(
                1,
                0,
                CRAMCompressionRecord.CF_QS_PRESERVED_AS_ARRAY,
                readName,
                readLength,
                referenceIndex,
                alignmentStart,
                templateSize,
                0,
                makeUpQualityScoresFor(readBases),
                readBases,
                tagList,
                null,
                readGroupID,
                0,
                SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                SAMRecord.NO_ALIGNMENT_START,
                -1
        );
    }

    public static CRAMCompressionRecord getCRAMRecordWithReadFeatures(
            final String readName,
            final int readLength,
            final int referenceIndex,
            final int alignmentStart,
            final int bamFlags,
            final int templateSize,
            final byte[] readBases,
            int readGroupID,
            List<ReadFeature> readFeatureList) {
        ValidationUtils.validateArg(readBases != null && readBases.length > 0, "must have read bases");
        return new CRAMCompressionRecord(
                1,
                bamFlags,
                CRAMCompressionRecord.CF_QS_PRESERVED_AS_ARRAY,
                readName,
                readLength,
                referenceIndex,
                alignmentStart,
                templateSize,
                0,
                makeUpQualityScoresFor(readBases),
                readBases,
                null,
                readFeatureList,
                readGroupID,
                0,
                SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                SAMRecord.NO_ALIGNMENT_START,
                -1
        );
    }

    private SAMFileHeader createSAMHeaderWithTwoReadGroups(SAMFileHeader.SortOrder sortOrder) {
        final SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(sortOrder);
        header.addSequence(new SAMSequenceRecord("chr1", 123));
        header.addSequence(new SAMSequenceRecord("chr2", 123));
        SAMReadGroupRecord readGroupRecord = new SAMReadGroupRecord("1");
        header.addReadGroup(readGroupRecord);
        return header;
    }
}