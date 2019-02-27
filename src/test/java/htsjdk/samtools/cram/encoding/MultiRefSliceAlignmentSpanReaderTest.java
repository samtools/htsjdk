package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.encoding.reader.MultiRefSliceAlignmentSpanReader;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.DefaultBitInputStream;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.AlignmentSpan;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class MultiRefSliceAlignmentSpanReaderTest extends CramRecordTestHelper {

    private List<CramCompressionRecord> initSpanRecords() {
        final List<CramCompressionRecord> initialRecords = createRecords();

        // note for future refactoring
        // createRecord() calls Sam2CramRecordFactory.createCramRecord()
        // which is the only way to set a record's readFeatures (except via read codec)
        // which would otherwise be null

        final String commonRead = "AAA";

        // span 1:1,3
        initialRecords.get(0).sequenceId = 1;
        initialRecords.get(0).alignmentStart = 1;
        initialRecords.get(0).readBases = commonRead.getBytes();
        initialRecords.get(0).readLength = commonRead.length();

        // span 2:2,4
        initialRecords.get(1).sequenceId = 2;
        initialRecords.get(1).alignmentStart = 2;
        initialRecords.get(1).readBases = commonRead.getBytes();
        initialRecords.get(1).readLength = commonRead.length();

        // span 1:3,5
        initialRecords.get(2).sequenceId = 1;
        initialRecords.get(2).alignmentStart = 3;
        initialRecords.get(2).readBases = commonRead.getBytes();
        initialRecords.get(2).readLength = commonRead.length();

        // span <unmapped>
        initialRecords.get(3).sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        initialRecords.get(3).alignmentStart = 7;
        initialRecords.get(3).readBases = commonRead.getBytes();
        initialRecords.get(3).readLength = commonRead.length();

        // span totals -> 1:1,5 and 2:2,4

        return initialRecords;
    }

    @DataProvider(name = "coordSortedTrueFalse")
    private Object[][] tf() {
        return new Object[][] { {true}, {false}};
    }

    @Test(dataProvider = "coordSortedTrueFalse")
    public void testSpans(final boolean coordinateSorted) throws IOException {
        final List<CramCompressionRecord> initialRecords = initSpanRecords();

        // note for future refactoring
        // createHeader(records) calls CompressionHeaderBuilder.setTagIdDictionary(buildTagIdDictionary(records));
        // which is the only way to set a record's tagIdsIndex
        // which would otherwise be null

        final CompressionHeader header = createHeader(initialRecords, coordinateSorted);

        final ReferenceContext refId = ReferenceContext.MULTIPLE;
        final Map<Integer, ByteArrayOutputStream> outputMap = createOutputMap(header);
        int initialAlignmentStart = initialRecords.get(0).alignmentStart;
        final byte[] written = write(initialRecords, outputMap, header, refId, initialAlignmentStart);

        final Map<Integer, ByteArrayInputStream> inputMap = createInputMap(outputMap);
        try (final ByteArrayInputStream is = new ByteArrayInputStream(written);
            final BitInputStream bis = new DefaultBitInputStream(is)) {

            final MultiRefSliceAlignmentSpanReader reader = new MultiRefSliceAlignmentSpanReader(bis, inputMap, header, ValidationStringency.DEFAULT_STRINGENCY, initialAlignmentStart, initialRecords.size());
            final Map<ReferenceContext, AlignmentSpan> spans = reader.getReferenceSpans();

            Assert.assertEquals(spans.size(), 3);
            Assert.assertEquals(spans.get(new ReferenceContext(1)), new AlignmentSpan(1, 5, 2));
            Assert.assertEquals(spans.get(new ReferenceContext(2)), new AlignmentSpan(2, 3, 1));
            Assert.assertNotNull(spans.get(ReferenceContext.UNMAPPED));
        }
    }
}
