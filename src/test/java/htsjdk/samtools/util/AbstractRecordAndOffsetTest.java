package htsjdk.samtools.util;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class AbstractRecordAndOffsetTest {

    private final byte[] qualities = {30, 40, 50, 60, 70, 80 ,90, 70, 80, 90};
    private byte[] bases = {'A', 'C', 'G', 'T', 'A', 'C', 'G', 'T', 'T', 'C'};
    SAMRecord record;

    @BeforeTest
    public void setUp(){
        record = new SAMRecord(new SAMFileHeader());
        record.setReadName("testRecord");
        record.setReadBases(bases);
        record.setBaseQualities(qualities);
    }
    @Test
    public void testForCreate(){
        AbstractRecordAndOffset abstractRecordAndOffset = new AbstractRecordAndOffset(record, 0, 10, 3);
        assertArrayEquals(qualities, abstractRecordAndOffset.getBaseQualities());
        assertArrayEquals(bases, abstractRecordAndOffset.getRecord().getReadBases());
        assertEquals('A', abstractRecordAndOffset.getReadBase());
        assertEquals(0, abstractRecordAndOffset.getOffset());
        assertEquals(3, abstractRecordAndOffset.getRefPos());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testForWrongLength(){
        AbstractRecordAndOffset abstractRecordAndOffset = new AbstractRecordAndOffset(record, 2, 101, 3);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testForWrongOffset(){
        AbstractRecordAndOffset abstractRecordAndOffset = new AbstractRecordAndOffset(record, 101, 10, 3);
    }
    @Test
    public void testForGettingQualByIndex(){
        AbstractRecordAndOffset abstractRecordAndOffset = new AbstractRecordAndOffset(record, 0, 10, 0);
        assertEquals(80, abstractRecordAndOffset.getBaseQuality(5));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testForExceptionInGettinQualByIndex(){
        AbstractRecordAndOffset abstractRecordAndOffset = new AbstractRecordAndOffset(record, 0, 10, 0);
        abstractRecordAndOffset.getBaseQuality(15);
    }
}