package htsjdk.samtools.util;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.junit.Assert.assertEquals;

public class AbstractLocusInfoTest {
    private final byte[] qualities = {30, 50, 50, 60, 60, 70 ,70, 70, 80, 90, 30, 50, 50, 60, 60, 70 ,70, 70, 80, 90};
    private byte[] bases = {'A', 'C', 'G', 'T', 'A', 'C', 'G', 'T', 'T', 'C', 'A', 'C', 'G', 'T', 'A', 'C', 'G', 'T', 'T', 'C'};
    private TypedRecordAndOffset typedRecordAndOffset;
    private TypedRecordAndOffset typedRecordAndOffsetEnd;
    private SAMSequenceRecord sequence = new SAMSequenceRecord("chrM", 100);

    @BeforeTest
    public void setUp(){
        SAMRecord record = new SAMRecord(new SAMFileHeader());
        record.setReadName("testRecord");
        record.setReadBases(bases);
        record.setBaseQualities(qualities);
        typedRecordAndOffset = new TypedRecordAndOffset(record, 10, 10, 10, TypedRecordAndOffset.Type.BEGIN);
        typedRecordAndOffsetEnd = new TypedRecordAndOffset(record, 10, 10, 0, TypedRecordAndOffset.Type.END);

    }

    @Test
    public void testForCreate(){
        AbstractLocusInfo<TypedRecordAndOffset> info = new AbstractLocusInfo<>(sequence, 1);
        assertEquals("chrM", info.getSequenceName());
        assertEquals(0, info.getRecordAndPositions().size());
        assertEquals(100, info.getSequenceLength());
        assertEquals(1, info.getPosition());
    }

    @Test
    public void testForAdd(){
        AbstractLocusInfo<TypedRecordAndOffset> info = new AbstractLocusInfo<>(sequence, 10);
        info.add(typedRecordAndOffset);
        info.add(typedRecordAndOffsetEnd);
        assertEquals(2, info.getRecordAndPositions().size());
        assertEquals(typedRecordAndOffset, info.getRecordAndPositions().get(0));
        assertEquals(typedRecordAndOffsetEnd, info.getRecordAndPositions().get(1));
        assertEquals(10, info.getPosition());
        assertEquals('A', info.getRecordAndPositions().get(0).getReadBase());
        assertEquals('A', info.getRecordAndPositions().get(1).getReadBase());
    }
}