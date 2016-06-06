/*
 * The MIT License
 *
 * Copyright (c) 2010 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
    public void testConstructor(){
        AbstractRecordAndOffset abstractRecordAndOffset = new AbstractRecordAndOffset(record, 0, 10, 3);
        assertArrayEquals(qualities, abstractRecordAndOffset.getBaseQualities());
        assertArrayEquals(bases, abstractRecordAndOffset.getRecord().getReadBases());
        assertEquals('A', abstractRecordAndOffset.getReadBase());
        assertEquals(30, abstractRecordAndOffset.getBaseQuality());
        assertEquals(0, abstractRecordAndOffset.getOffset());
        assertEquals(3, abstractRecordAndOffset.getRefPos());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testWrongLengthException(){
        new AbstractRecordAndOffset(record, 2, 101, 3);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testWrongOffsetException(){
        new AbstractRecordAndOffset(record, 101, 10, 3);
    }

    @Test
    public void testGetQualityByReferenceIndex(){
        AbstractRecordAndOffset abstractRecordAndOffset = new AbstractRecordAndOffset(record, 0, 10, 0);
        assertEquals(80, abstractRecordAndOffset.getBaseQuality(5));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testGetQualityByWrongIndexIndex(){
        AbstractRecordAndOffset abstractRecordAndOffset = new AbstractRecordAndOffset(record, 0, 10, 0);
        abstractRecordAndOffset.getBaseQuality(15);
    }

}