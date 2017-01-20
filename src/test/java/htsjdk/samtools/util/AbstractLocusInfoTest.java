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

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Mariia_Zueva@epam.com, EPAM Systems, Inc. <www.epam.com>
 */

public class AbstractLocusInfoTest extends HtsjdkTest {
    private final byte[] qualities = {30, 50, 50, 60, 60, 70, 70, 70, 80, 90, 30, 50, 50, 60, 60, 70, 70, 70, 80, 90};
    private byte[] bases = {'A', 'C', 'G', 'T', 'A', 'C', 'G', 'T', 'T', 'C', 'A', 'C', 'G', 'T', 'A', 'C', 'G', 'T', 'T', 'C'};
    private EdgingRecordAndOffset typedRecordAndOffset;
    private EdgingRecordAndOffset typedRecordAndOffsetEnd;
    private SAMSequenceRecord sequence = new SAMSequenceRecord("chrM", 100);

    @BeforeTest
    public void setUp() {
        SAMRecord record = new SAMRecord(new SAMFileHeader());
        record.setReadName("testRecord");
        record.setReadBases(bases);
        record.setBaseQualities(qualities);
        typedRecordAndOffset = EdgingRecordAndOffset.createBeginRecord(record, 10, 10, 10);
        typedRecordAndOffsetEnd = EdgingRecordAndOffset.createEndRecord(typedRecordAndOffset);
    }

    @Test
    public void testConstructor() {
        AbstractLocusInfo<EdgingRecordAndOffset> info = new AbstractLocusInfo<>(sequence, 1);
        assertEquals("chrM", info.getSequenceName());
        assertEquals(0, info.getRecordAndOffsets().size());
        assertEquals(100, info.getSequenceLength());
        assertEquals(1, info.getPosition());
    }

    @Test
    public void testAdd() {
        AbstractLocusInfo<EdgingRecordAndOffset> info = new AbstractLocusInfo<>(sequence, 10);
        info.add(typedRecordAndOffset);
        info.add(typedRecordAndOffsetEnd);
        assertEquals(2, info.getRecordAndOffsets().size());
        assertEquals(typedRecordAndOffset, info.getRecordAndOffsets().get(0));
        assertEquals(typedRecordAndOffsetEnd, info.getRecordAndOffsets().get(1));
        assertEquals(10, info.getPosition());
        assertEquals('A', info.getRecordAndOffsets().get(0).getReadBase());
        assertEquals('A', info.getRecordAndOffsets().get(1).getReadBase());
        assertEquals(30, info.getRecordAndOffsets().get(0).getBaseQuality());
        assertEquals(30, info.getRecordAndOffsets().get(1).getBaseQuality());
    }
}
