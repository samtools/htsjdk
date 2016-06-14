/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SAMTextWriterTest {

    private SAMRecordSetBuilder getSAMReader(final boolean sortForMe, final SAMFileHeader.SortOrder sortOrder) {
        final SAMRecordSetBuilder ret = new SAMRecordSetBuilder(sortForMe, sortOrder);
        ret.addPair("readB", 20, 200, 300);
        ret.addPair("readA", 20, 100, 150);
        ret.addFrag("readC", 20, 140, true);
        ret.addFrag("readD", 20, 140, false);
        return ret;
    }

    @Test
    public void testNullHeader() throws Exception {
        final SAMRecordSetBuilder recordSetBuilder = getSAMReader(true, SAMFileHeader.SortOrder.coordinate);
        for (final SAMRecord rec : recordSetBuilder.getRecords()) {
            rec.setHeader(null);
        }
        doTest(recordSetBuilder);
    }

    @Test
    public void testBasic() throws Exception {
        doTest(SamFlagField.DECIMAL);
    }

    @Test
    public void testBasicHexFlag() throws Exception {
        doTest(SamFlagField.HEXADECIMAL);
    }

    @Test
    public void testBasicOctalFlag() throws Exception {
        doTest(SamFlagField.OCTAL);
    }

    @Test
    public void testBasicStringFlag() throws Exception {
        doTest(SamFlagField.STRING);
    }

    private void doTest(final SAMRecordSetBuilder recordSetBuilder) throws Exception {
        doTest(recordSetBuilder, SamFlagField.DECIMAL);
    }

    private void doTest(final SamFlagField samFlagField) throws Exception {
        doTest(getSAMReader(true, SAMFileHeader.SortOrder.coordinate), samFlagField);
    }

    private void doTest(final SAMRecordSetBuilder recordSetBuilder, final SamFlagField samFlagField) throws Exception {
        SamReader inputSAM = recordSetBuilder.getSamReader();
        final File samFile = File.createTempFile("tmp.", ".sam");
        samFile.deleteOnExit();
        final Map<String, Object> tagMap = new HashMap<String, Object>();
        tagMap.put("XC", new Character('q'));
        tagMap.put("XI", 12345);
        tagMap.put("XF", 1.2345f);
        tagMap.put("XS", "Hi,Mom!");
        for (final Map.Entry<String, Object> entry : tagMap.entrySet()) {
            inputSAM.getFileHeader().setAttribute(entry.getKey(), entry.getValue().toString());
        }
        final SAMFileWriter samWriter = new SAMFileWriterFactory().setSamFlagFieldOutput(samFlagField).makeSAMWriter(inputSAM.getFileHeader(), false, samFile);
        for (final SAMRecord samRecord : inputSAM) {
            samWriter.addAlignment(samRecord);
        }
        samWriter.close();

        // Read it back in and confirm that it matches the input
        inputSAM = recordSetBuilder.getSamReader();
        // Stuff in the attributes again since this has been created again.
        for (final Map.Entry<String, Object> entry : tagMap.entrySet()) {
            inputSAM.getFileHeader().setAttribute(entry.getKey(), entry.getValue().toString());
        }

        final SamReader newSAM = SamReaderFactory.makeDefault().open(samFile);
        Assert.assertEquals(newSAM.getFileHeader(), inputSAM.getFileHeader());
        final Iterator<SAMRecord> inputIt = inputSAM.iterator();
        final Iterator<SAMRecord> newSAMIt = newSAM.iterator();
        while (inputIt.hasNext()) {
            Assert.assertTrue(newSAMIt.hasNext());
            final SAMRecord inputSAMRecord = inputIt.next();
            final SAMRecord newSAMRecord = newSAMIt.next();

            // Force reference index attributes to be populated
            inputSAMRecord.getReferenceIndex();
            newSAMRecord.getReferenceIndex();
            inputSAMRecord.getMateReferenceIndex();
            newSAMRecord.getMateReferenceIndex();

            // Force these to be equal
            newSAMRecord.setIndexingBin(inputSAMRecord.getIndexingBin());

            Assert.assertEquals(newSAMRecord, inputSAMRecord);
        }
        Assert.assertFalse(newSAMIt.hasNext());
        inputSAM.close();
    }
}
