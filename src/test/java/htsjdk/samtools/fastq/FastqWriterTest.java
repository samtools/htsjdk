/*
 * The MIT License
 *
 * Pierre Lindenbaum PhD
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
package htsjdk.samtools.fastq;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import htsjdk.samtools.util.TestUtil;

import java.io.File;
import java.util.ArrayList;

/**
 * test fastq
 */
public class FastqWriterTest {
    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/util/QualityEncodingDetectorTest");

    @DataProvider(name = "fastqsource")
    public Object[][] createTestData() {
        return new Object[][]{
                {"solexa_full_range_as_solexa.fastq"},
                {"5k-30BB2AAXX.3.aligned.sam.fastq"}
        };
    }

    @Test(dataProvider = "fastqsource")
    public void testReadReadWriteFastq(final String basename) throws Exception {
        final File tmpFile = File.createTempFile("test.", ".fastq");
        tmpFile.deleteOnExit();
        final FastqReader fastqReader = new FastqReader(new File(TEST_DATA_DIR,basename));
        final FastqWriterFactory writerFactory = new FastqWriterFactory();
        final FastqWriter fastqWriter = writerFactory.newWriter(tmpFile);
        for(final FastqRecord rec: fastqReader) fastqWriter.write(rec);
        fastqWriter.close();
        fastqReader.close();
    }
    
    @Test(dataProvider = "fastqsource")
    public void testFastqSerialize(final String basename) throws Exception {
        //write 
        final ArrayList<FastqRecord> records = new ArrayList<>();
        final FastqReader fastqReader = new FastqReader(new File(TEST_DATA_DIR,basename));
        for(final FastqRecord rec: fastqReader) {
            records.add(rec);
            if(records.size()>100) break;
        }
        fastqReader.close();
        Assert.assertEquals(TestUtil.serializeAndDeserialize(records),records);
    }
}
