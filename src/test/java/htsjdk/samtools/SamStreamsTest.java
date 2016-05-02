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

package htsjdk.samtools;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;

public class SamStreamsTest {

    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools");

    @Test(dataProvider = "makeData")
    public void testDataFormat(final String inputFile, final boolean isGzippedSAMFile, final boolean isBAMFile, final boolean isCRAMFile) throws Exception {
        final File input = new File(TEST_DATA_DIR, inputFile);
        try(final InputStream fis = new BufferedInputStream(new FileInputStream(input))) { //must be buffered or the isGzippedSAMFile will blow up
            Assert.assertEquals(SamStreams.isGzippedSAMFile(fis), isGzippedSAMFile, "isGzippedSAMFile:" + inputFile);
            Assert.assertEquals(SamStreams.isBAMFile(fis), isBAMFile,   "isBAMFile:" + inputFile);
            Assert.assertEquals(SamStreams.isCRAMFile(fis), isCRAMFile, "isCRAMFile:" + inputFile);
        }
    }

    @DataProvider(name = "makeData")
    public Object[][] makeData() {
        final Object[][] scenarios = new Object[][]{
                //isGzippedSAMFile isBAMFile isCRAMFile
                {"block_compressed.sam.gz", true,  false, false},
                {"uncompressed.sam",        false, false, false},
                {"compressed.sam.gz",       true,  false, false},
                {"compressed.bam",          true,  true,  false}, //this is slightly weird (responding true to isGzippedSAMFile)
                {"cram_query_sorted.cram",  false, false, true},
        };
        return scenarios;
    }

}
