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
package htsjdk.samtools;

import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.Log.LogLevel;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CramFileWriterTest {

    @BeforeClass
    public void initClass() {
        Log.setGlobalLogLevel(LogLevel.ERROR);
    }

    @Test(description = "Test for lossy CRAM compression invariants.")
    public void lossyCramInvariantsTest() throws Exception {
        doTest(createRecords(1000));
    }

    @Test(description = "Tests a unmapped record with sequence and quality fields")
    public void unmappedWithSequenceAndQualityField() throws Exception {
        unmappedSequenceAndQualityFieldHelper(true);
    }

    @Test(description = "Tests a unmapped record with no sequence or quality fields")
    public void unmappedWithNoSequenceAndQualityField() throws Exception {
        unmappedSequenceAndQualityFieldHelper(false);
    }

    private void unmappedSequenceAndQualityFieldHelper(boolean unmappedHasBasesAndQualities) throws Exception {
        List<SAMRecord> list = new ArrayList<SAMRecord>(2);
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
        if (builder.getHeader().getReadGroups().isEmpty()) {
            throw new Exception("Read group expected in the header");
        }

        builder.setUnmappedHasBasesAndQualities(unmappedHasBasesAndQualities);

        builder.addUnmappedFragment("test1");
        builder.addUnmappedPair("test2");

        list.addAll(builder.getRecords());

        Collections.sort(list, new SAMRecordCoordinateComparator());

        doTest(list);
    }

    private List<SAMRecord> createRecords(int count) throws Exception {
        List<SAMRecord> list = new ArrayList<SAMRecord>(count);
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
        if (builder.getHeader().getReadGroups().isEmpty()) {
            throw new Exception("Read group expected in the header");
        }

        int posInRef = 1;
        for (int i = 0; i < count / 2; i++)
            builder.addPair(Integer.toString(i), 0, posInRef += 1,
                    posInRef += 3);
        list.addAll(builder.getRecords());

        Collections.sort(list, new SAMRecordCoordinateComparator());

        return list;
    }

    private void doTest(final List<SAMRecord> samRecords) {
        final SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        header.addSequence(new SAMSequenceRecord("chr1", 123));
        SAMReadGroupRecord readGroupRecord = new SAMReadGroupRecord("1");
        header.addReadGroup(readGroupRecord);

        byte[] refBases = new byte[1024 * 1024];
        Arrays.fill(refBases, (byte) 'A');
        InMemoryReferenceSequenceFile rsf = new InMemoryReferenceSequenceFile();
        rsf.add("chr1", refBases);
        ReferenceSource source = new ReferenceSource(rsf);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        CRAMFileWriter writer = new CRAMFileWriter(os, source, header, null);
        for (SAMRecord record : samRecords) {
            writer.writeAlignment(record);
        }
        writer.finish();
        writer.close();

        CRAMFileReader cReader = new CRAMFileReader(null,
                new ByteArrayInputStream(os.toByteArray()),
                new ReferenceSource(rsf));
        SAMRecordIterator iterator2 = cReader.getIterator();
        int index = 0;
        while (iterator2.hasNext()) {
            SAMRecord actualRecord= iterator2.next();
            SAMRecord expectedRecord = samRecords.get(index++);

            Assert.assertEquals(actualRecord.getReadName(), expectedRecord.getReadName());
            Assert.assertEquals(actualRecord.getFlags(), expectedRecord.getFlags());
            Assert.assertEquals(actualRecord.getAlignmentStart(), expectedRecord.getAlignmentStart());
            Assert.assertEquals(actualRecord.getAlignmentEnd(), expectedRecord.getAlignmentEnd());
            Assert.assertEquals(actualRecord.getReferenceName(), expectedRecord.getReferenceName());
            Assert.assertEquals(actualRecord.getMateAlignmentStart(),
                    expectedRecord.getMateAlignmentStart());
            Assert.assertEquals(actualRecord.getMateReferenceName(),
                    expectedRecord.getMateReferenceName());
            Assert.assertEquals(actualRecord.getReadBases(), expectedRecord.getReadBases());
            Assert.assertEquals(actualRecord.getBaseQualities(), expectedRecord.getBaseQualities());
        }
        cReader.close();
    }
}
