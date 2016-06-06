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
    public void testConstructor(){
        TypedRecordAndOffset typedRecordAndOffset = new TypedRecordAndOffset(record, 0, 10, 3, TypedRecordAndOffset.Type.BEGIN);
        assertArrayEquals(qualities, typedRecordAndOffset.getBaseQualities());
        assertArrayEquals(bases, typedRecordAndOffset.getRecord().getReadBases());
        assertEquals('A', typedRecordAndOffset.getReadBase());
        assertEquals(0, typedRecordAndOffset.getOffset());
        assertEquals(3, typedRecordAndOffset.getRefPos());
        assertEquals(TypedRecordAndOffset.Type.BEGIN, typedRecordAndOffset.getType());
    }

    @Test
    public void  testGetSetStart(){
        TypedRecordAndOffset typedRecordAndOffset = new TypedRecordAndOffset(record, 0, 10, 3, TypedRecordAndOffset.Type.BEGIN);
        TypedRecordAndOffset typedRecordAndOffsetEnd = new TypedRecordAndOffset(record, 9, 10, 3, TypedRecordAndOffset.Type.END);
        typedRecordAndOffsetEnd.setStart(typedRecordAndOffset);
        assertEquals(typedRecordAndOffset, typedRecordAndOffsetEnd.getStart());
    }

    @Test
    public void testNotEqualsTypedRecords(){
        TypedRecordAndOffset typedRecordAndOffset = new TypedRecordAndOffset(record, 0, 10, 3, TypedRecordAndOffset.Type.BEGIN);
        TypedRecordAndOffset secondTypedRecordAndOffset = new TypedRecordAndOffset(record, 5, 10, 3, TypedRecordAndOffset.Type.BEGIN);
        assertNotSame(typedRecordAndOffset.getBaseQuality(), secondTypedRecordAndOffset.getBaseQuality());
        assertArrayEquals(typedRecordAndOffset.getBaseQualities(), secondTypedRecordAndOffset.getBaseQualities());
    }

    @Test
    public void testGetOffset(){
        TypedRecordAndOffset secondTypedRecordAndOffset = new TypedRecordAndOffset(record, 5, 10, 3, TypedRecordAndOffset.Type.BEGIN);
        assertEquals(70, secondTypedRecordAndOffset.getBaseQuality());
        assertEquals('C', secondTypedRecordAndOffset.getReadBase());
    }

    @Test
    public void testGetQualityAtPosition(){
        TypedRecordAndOffset secondTypedRecordAndOffset = new TypedRecordAndOffset(record, 0, 10, 1, TypedRecordAndOffset.Type.BEGIN);
        assertEquals(50, secondTypedRecordAndOffset.getBaseQuality(2));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testWrongSetStart(){
        TypedRecordAndOffset typedRecordAndOffset = new TypedRecordAndOffset(record, 0, 10, 3, TypedRecordAndOffset.Type.BEGIN);
        TypedRecordAndOffset typedRecordAndOffsetEnd = new TypedRecordAndOffset(record, 9, 10, 3, TypedRecordAndOffset.Type.END);
        typedRecordAndOffset.setStart(typedRecordAndOffsetEnd);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testWrongSetStartWithTypeEnd(){
        TypedRecordAndOffset typedRecordAndOffset = new TypedRecordAndOffset(record, 0, 10, 3, TypedRecordAndOffset.Type.END);
        TypedRecordAndOffset typedRecordAndOffsetEnd = new TypedRecordAndOffset(record, 9, 10, 3, TypedRecordAndOffset.Type.END);
        typedRecordAndOffset.setStart(typedRecordAndOffsetEnd);
    }
}