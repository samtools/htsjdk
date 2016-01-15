/*
 * The MIT License
 *
 * Copyright (c) 2015 Pierre Lindenbaum @yokofakun Institut du Thorax Nantes France
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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import htsjdk.samtools.util.CloserUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SAMXmlWriterTest {
    private static final File TEST_DATA_DIR = new File("testdata/htsjdk/samtools");

    @DataProvider(name = "bamfiles")
    public Object[][] bamFiles() {
        return new Object[][]{
                {"block_compressed.sam.gz"},
                {"uncompressed.sam"},
                {"compressed.sam.gz"},
                {"compressed.bam"},
        };
    }
    
    @Test(dataProvider = "bamfiles")
    public void samRecordFactoryTest(final String inputFile) throws Exception {
        final File input = new File(TEST_DATA_DIR, inputFile);
        final SamReader reader = SamReaderFactory.makeDefault().open(input);
        final File samFile = File.createTempFile("tmp.", ".xml");
        samFile.deleteOnExit();
        final SAMFileWriter xmlWriter= new SAMXmlWriter(
                reader.getFileHeader(),
                samFile
                );
                
        for (final SAMRecord rec : reader) {
            xmlWriter.addAlignment(rec);
        }
        CloserUtil.close(reader);
        CloserUtil.close(xmlWriter);
    }


    private SAMRecordSetBuilder getSAMReader(final boolean sortForMe, final SAMFileHeader.SortOrder sortOrder) {
        final SAMRecordSetBuilder ret = new SAMRecordSetBuilder(sortForMe, sortOrder);
        ret.addPair("readB", 20, 200, 300);
        ret.addPair("readA", 20, 100, 150);
        ret.addFrag("readC", 20, 140, true);
        ret.addFrag("readD", 20, 140, false);
        return ret;
    }

    @Test
    public void testBasic() throws Exception {
        doTest(getSAMReader(true, SAMFileHeader.SortOrder.coordinate));
    }

    @Test
    public void testNullHeader() throws Exception {
        final SAMRecordSetBuilder recordSetBuilder = getSAMReader(true, SAMFileHeader.SortOrder.coordinate);
        for (final SAMRecord rec : recordSetBuilder.getRecords()) {
            rec.setHeader(null);
        }
        doTest(recordSetBuilder);
    }

    private void doTest(final SAMRecordSetBuilder recordSetBuilder) throws Exception{
        final SamReader inputSAM = recordSetBuilder.getSamReader();
        final File samFile = File.createTempFile("tmp.", ".xml");
        samFile.deleteOnExit();
        final Map<String, Object> tagMap = new HashMap<String, Object>();
        tagMap.put("XC", new Character('q'));
        tagMap.put("XI", 12345);
        tagMap.put("XF", 1.2345f);
        tagMap.put("XS", "Hi,Mom!");
        for (final Map.Entry<String, Object> entry : tagMap.entrySet()) {
            inputSAM.getFileHeader().setAttribute(entry.getKey(), entry.getValue().toString());
        }
        final SAMXmlWriter samWriter = new SAMXmlWriter(inputSAM.getFileHeader(), samFile);
        for (final SAMRecord samRecord : inputSAM) {
            samWriter.addAlignment(samRecord);
        }
        samWriter.close();
        }
}
