package htsjdk.samtools.cram.encoding;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFlag;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.build.CompressionHeaderFactory;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MultiRefSliceAlignmentSpanReaderTest extends HtsjdkTest {

    private List<CRAMRecord> initSpanRecords() {
        final List<CRAMRecord> cramRecords = new ArrayList<>();

        // note for future refactoring
        // createRecord() calls Sam2CramRecordFactory.createCramRecord()
        // which is the only way to set a record's readFeatures (except via read codec)
        // which would otherwise be null

        final String commonRead = "AAA";

        // span 1:1,3
        cramRecords.add(CRAMRecordTestHelper.getCRAMRecord(
                "rec1",
                commonRead.length(),
                1,
                1,
                3,
                commonRead.getBytes(),
                1
        ));

        // unmapped but placed
        // span 2:2,4
        cramRecords.add(new CRAMRecord(
        1,
                2,
                SAMFlag.READ_UNMAPPED.intValue(),
                0,
                "rec2",
                commonRead.length(),
                2,
                2,
                3,
                0,
                new byte[] { 0x10, 0x20, 0x30 },
                commonRead.getBytes(),
                null,
                null,
                2,
                0,
                SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                SAMRecord.NO_ALIGNMENT_START,
                -1));

        // span 1:3,5
        cramRecords.add(CRAMRecordTestHelper.getCRAMRecord(
                "rec3",
                commonRead.length(),
                1,
                3,
                3,
                commonRead.getBytes(),
                1
        ));

        // span <unmapped>
        cramRecords.add(CRAMRecordTestHelper.getCRAMRecord(
                "rec4",
                commonRead.length(),
                SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                7,
                3,
                commonRead.getBytes(),
                1
        ));

        // span totals -> 1:1,5 and 2:2,4

        return cramRecords;
    }

    @Test
    public void testSpansCoordinateSorted() {
        final List<CRAMRecord> cramRecords = initSpanRecords();

        // note for future refactoring
        // createHeader(records) calls CompressionHeaderBuilder.setTagIdDictionary(buildTagIdDictionary(records));
        // which is the only way to set a record's tagIdsIndex
        // which would otherwise be null

        // NOTE: multiref alignment spans are ony used for CRAI indexing, and only make sense when records are
        // coordinate sorted, so we only test with coordinateSorted = true;
        final CompressionHeader header = new CompressionHeaderFactory().build(cramRecords, true);
        final Slice slice = new Slice(cramRecords, header);
        final Map<ReferenceContext, AlignmentSpan> spans = slice.getMultiRefAlignmentSpans(ValidationStringency.DEFAULT_STRINGENCY);

        Assert.assertEquals(spans.size(), 3);
        Assert.assertEquals(spans.get(new ReferenceContext(1)), new AlignmentSpan(1, 5, 2, 0));
        Assert.assertEquals(spans.get(new ReferenceContext(2)), new AlignmentSpan(2, 3, 0, 1));
        Assert.assertEquals(spans.get(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT), AlignmentSpan.UNPLACED_SPAN);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testSpansNonCoordinateSorted() {
        final List<CRAMRecord> cramRecords = initSpanRecords();

        // NOTE: multiref alignment spans are ony used for CRAI indexing, and only make sense when records are
        // coordinate sorted, so test that we reject coordinateSorted = true;
        final CompressionHeader header = new CompressionHeaderFactory().build(cramRecords, false);
        final Slice slice = new Slice(cramRecords, header);

        slice.getMultiRefAlignmentSpans(ValidationStringency.DEFAULT_STRINGENCY);
    }
}
