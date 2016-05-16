/*
* Copyright (c) 2012 The Broad Institute
*
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
*
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package htsjdk.variant;

import htsjdk.samtools.util.IOUtil;
import htsjdk.variant.example.PrintVariantsExample;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;

public class PrintVariantsExampleTest {
    @Test
    public void testExampleWriteFile() throws IOException {
        final File tempFile = File.createTempFile("example", ".vcf");
        tempFile.deleteOnExit();
        File f1 = new File("src/test/resources/htsjdk/variant/ILLUMINA.wex.broad_phase2_baseline.20111114.both.exome.genotypes.1000.vcf");
        final String[] args = {
                f1.getAbsolutePath(),
                tempFile.getAbsolutePath()
        };
        Assert.assertEquals(tempFile.length(), 0);
        PrintVariantsExample.main(args);
        Assert.assertNotEquals(tempFile.length(), 0);

        assertFilesEqualSkipHeaders(tempFile, f1);
    }

    private void assertFilesEqualSkipHeaders(File tempFile, File f1) throws FileNotFoundException {
        final List<String> lines1 = IOUtil.slurpLines(f1);
        final List<String> lines2 = IOUtil.slurpLines(tempFile);
        final int firstNonComment1 = IntStream.range(0, lines1.size()).filter(i -> !lines1.get(i).startsWith("#")).findFirst().getAsInt();
        final int firstNonComment2 = IntStream.range(0, lines2.size()).filter(i -> !lines2.get(i).startsWith("#")).findFirst().getAsInt();
        Assert.assertEquals(lines1.subList(firstNonComment1, lines1.size()), lines2.subList(firstNonComment2,lines2.size()));
    }
}
