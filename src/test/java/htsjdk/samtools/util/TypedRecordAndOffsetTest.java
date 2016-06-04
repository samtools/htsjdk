package htsjdk.samtools.util;


import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class TypedRecordAndOffsetTest {
    private final byte[] qualities = {30, 50, 50, 60, 60, 70 ,70, 70, 80, 90};
    private final byte[] bases = {'A', 'C', 'G', 'T', 'A', 'C', 'G', 'T', 'T', 'C'};
    private SAMRecord record;

    @BeforeTest
    public void setUp(){
        record = new SAMRecord(new SAMFileHeader());
        record.setReadName("testRecord");
        record.setReadBases(bases);
        record.setBaseQualities(qualities);
    }

    @Test
    public void testForCreate(){
        TypedRecordAndOffset typedRecordAndOffset = new TypedRecordAndOffset(record, 0, 10, 3, TypedRecordAndOffset.Type.BEGIN);
        assertArrayEquals(qualities, typedRecordAndOffset.getBaseQualities());
        assertArrayEquals(bases, typedRecordAndOffset.getRecord().getReadBases());
        assertEquals('A', typedRecordAndOffset.getReadBase());
        assertEquals(0, typedRecordAndOffset.getOffset());
        assertEquals(3, typedRecordAndOffset.getRefPos());
        assertEquals(TypedRecordAndOffset.Type.BEGIN, typedRecordAndOffset.getType());
    }

    @Test
    public void  testForGetSetStart(){
        TypedRecordAndOffset typedRecordAndOffset = new TypedRecordAndOffset(record, 0, 10, 3, TypedRecordAndOffset.Type.BEGIN);
        TypedRecordAndOffset typedRecordAndOffsetEnd = new TypedRecordAndOffset(record, 9, 10, 3, TypedRecordAndOffset.Type.END);
        typedRecordAndOffsetEnd.setStart(typedRecordAndOffset);
        assertEquals(typedRecordAndOffset, typedRecordAndOffsetEnd.getStart());
    }

    @Test
    public void testForNotEqualsTypedRecords(){
        TypedRecordAndOffset typedRecordAndOffset = new TypedRecordAndOffset(record, 0, 10, 3, TypedRecordAndOffset.Type.BEGIN);
        TypedRecordAndOffset secondtypedRecordAndOffset = new TypedRecordAndOffset(record, 5, 10, 3, TypedRecordAndOffset.Type.BEGIN);
        assertNotSame(typedRecordAndOffset.getBaseQuality(), secondtypedRecordAndOffset.getBaseQuality());
        assertArrayEquals(typedRecordAndOffset.getBaseQualities(), secondtypedRecordAndOffset.getBaseQualities());
    }

    @Test
    public void testForGetOffset(){
        TypedRecordAndOffset secondtypedRecordAndOffset = new TypedRecordAndOffset(record, 5, 10, 3, TypedRecordAndOffset.Type.BEGIN);
        assertEquals(70, secondtypedRecordAndOffset.getBaseQuality());
        assertEquals('C', secondtypedRecordAndOffset.getReadBase());
    }

    @Test
    public void testForGetQualityAtPosition(){
        TypedRecordAndOffset secondtypedRecordAndOffset = new TypedRecordAndOffset(record, 0, 10, 1, TypedRecordAndOffset.Type.BEGIN);
        assertEquals(50, secondtypedRecordAndOffset.getBaseQuality(2));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testForWrongSetStart(){
        TypedRecordAndOffset typedRecordAndOffset = new TypedRecordAndOffset(record, 0, 10, 3, TypedRecordAndOffset.Type.BEGIN);
        TypedRecordAndOffset typedRecordAndOffsetEnd = new TypedRecordAndOffset(record, 9, 10, 3, TypedRecordAndOffset.Type.END);
        typedRecordAndOffset.setStart(typedRecordAndOffsetEnd);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testForWrongSetStartWithTypeEnd(){
        TypedRecordAndOffset typedRecordAndOffset = new TypedRecordAndOffset(record, 0, 10, 3, TypedRecordAndOffset.Type.END);
        TypedRecordAndOffset typedRecordAndOffsetEnd = new TypedRecordAndOffset(record, 9, 10, 3, TypedRecordAndOffset.Type.END);
        typedRecordAndOffset.setStart(typedRecordAndOffsetEnd);
    }
}