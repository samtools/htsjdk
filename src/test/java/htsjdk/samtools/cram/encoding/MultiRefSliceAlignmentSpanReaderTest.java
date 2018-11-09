package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.encoding.reader.MultiRefSliceAlignmentSpanReader;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.DefaultBitInputStream;
import htsjdk.samtools.cram.structure.AlignmentSpan;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.Slice;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

        // span 1:7,9
        initialRecords.get(3).sequenceId = 1;
        initialRecords.get(3).alignmentStart = 7;
        initialRecords.get(3).readBases = commonRead.getBytes();
        initialRecords.get(3).readLength = commonRead.length();

        // span totals -> 1:1,9 and 2:2,4

        return initialRecords;
    }

    @Test
    public void testSpans() throws IOException {
        final List<CramCompressionRecord> initialRecords = initSpanRecords();

        // note for future refactoring
        // createHeader(records) calls CompressionHeaderBuilder.setTagIdDictionary(buildTagIdDictionary(records));
        // which is the only way to set a record's tagIdsIndex
        // which would otherwise be null

        final boolean sorted = false;
        final CompressionHeader header = createHeader(initialRecords, sorted);

        final int refId = Slice.MULTI_REFERENCE;
        final Map<Integer, ByteArrayOutputStream> outputMap = createOutputMap(header);
        final byte[] written = write(initialRecords, header, refId, outputMap);

        final Map<Integer, InputStream> inputMap = createInputMap(outputMap);
        try (final ByteArrayInputStream is = new ByteArrayInputStream(written)) {
            final BitInputStream bis = new DefaultBitInputStream(is);

            final MultiRefSliceAlignmentSpanReader reader = new MultiRefSliceAlignmentSpanReader(bis, inputMap, header, ValidationStringency.DEFAULT_STRINGENCY, 0, initialRecords.size());
            final Map<Integer, AlignmentSpan> spans = reader.getReferenceSpans();

            Assert.assertEquals(spans.size(), 2);
            Assert.assertTrue(spans.containsKey(1));
            Assert.assertTrue(spans.containsKey(2));
            Assert.assertEquals(spans.get(1), new AlignmentSpan(1, 9, 3));
            Assert.assertEquals(spans.get(2), new AlignmentSpan(2, 3, 1));
        }
    }
}
