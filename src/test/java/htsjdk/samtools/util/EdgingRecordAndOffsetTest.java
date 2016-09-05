/*
 * The MIT License
 *
 * Copyright (c) 2016 The Broad Institute
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
import static org.junit.Assert.assertNotSame;

/**
 * 
 * @author Mariia_Zueva@epam.com, EPAM Systems, Inc. <www.epam.com>
 *
 */

public class EdgingRecordAndOffsetTest {
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
    public void testConstructor(){
        EdgingRecordAndOffset typedRecordAndOffset = new EdgingRecordAndOffset(record, 0, 10, 3, EdgingRecordAndOffset.Type.BEGIN);
        assertArrayEquals(qualities, typedRecordAndOffset.getBaseQualities());
        assertArrayEquals(bases, typedRecordAndOffset.getRecord().getReadBases());
        assertEquals('A', typedRecordAndOffset.getReadBase());
        assertEquals(0, typedRecordAndOffset.getOffset());
        assertEquals(3, typedRecordAndOffset.getRefPos());
        assertEquals(EdgingRecordAndOffset.Type.BEGIN, typedRecordAndOffset.getType());
    }

    @Test
    public void  testGetSetStart(){
        EdgingRecordAndOffset typedRecordAndOffset = new EdgingRecordAndOffset(record, 0, 10, 3, EdgingRecordAndOffset.Type.BEGIN);
        EdgingRecordAndOffset typedRecordAndOffsetEnd = new EdgingRecordAndOffset(record, 9, 10, 3, EdgingRecordAndOffset.Type.END);
        typedRecordAndOffsetEnd.setStart(typedRecordAndOffset);
        assertEquals(typedRecordAndOffset, typedRecordAndOffsetEnd.getStart());
    }

    @Test
    public void testNotEqualsTypedRecords(){
        EdgingRecordAndOffset typedRecordAndOffset = new EdgingRecordAndOffset(record, 0, 10, 3, EdgingRecordAndOffset.Type.BEGIN);
        EdgingRecordAndOffset secondEdgingRecordAndOffset = new EdgingRecordAndOffset(record, 5, 10, 3, EdgingRecordAndOffset.Type.BEGIN);
        assertNotSame(typedRecordAndOffset.getBaseQuality(), secondEdgingRecordAndOffset.getBaseQuality());
        assertArrayEquals(typedRecordAndOffset.getBaseQualities(), secondEdgingRecordAndOffset.getBaseQualities());
    }

    @Test
    public void testGetOffset(){
        EdgingRecordAndOffset secondEdgingRecordAndOffset = new EdgingRecordAndOffset(record, 5, 10, 3, EdgingRecordAndOffset.Type.BEGIN);
        assertEquals(70, secondEdgingRecordAndOffset.getBaseQuality());
        assertEquals('C', secondEdgingRecordAndOffset.getReadBase());
    }

    @Test
    public void testGetQualityAtPosition(){
        EdgingRecordAndOffset secondEdgingRecordAndOffset = new EdgingRecordAndOffset(record, 0, 10, 1, EdgingRecordAndOffset.Type.BEGIN);
        assertEquals(50, secondEdgingRecordAndOffset.getBaseQuality(2));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testWrongSetStart(){
        EdgingRecordAndOffset typedRecordAndOffset = new EdgingRecordAndOffset(record, 0, 10, 3, EdgingRecordAndOffset.Type.BEGIN);
        EdgingRecordAndOffset typedRecordAndOffsetEnd = new EdgingRecordAndOffset(record, 9, 10, 3, EdgingRecordAndOffset.Type.END);
        typedRecordAndOffset.setStart(typedRecordAndOffsetEnd);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testWrongSetStartWithTypeEnd(){
        EdgingRecordAndOffset typedRecordAndOffset = new EdgingRecordAndOffset(record, 0, 10, 3, EdgingRecordAndOffset.Type.END);
        EdgingRecordAndOffset typedRecordAndOffsetEnd = new EdgingRecordAndOffset(record, 9, 10, 3, EdgingRecordAndOffset.Type.END);
        typedRecordAndOffset.setStart(typedRecordAndOffsetEnd);
    }
}