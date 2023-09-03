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

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.Tuple;
import htsjdk.variant.example.PrintVariantsExample;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class PrintVariantsExampleTest extends HtsjdkTest {
    @Test
    public void testExampleWriteFile() throws IOException {
        final File tempFile = File.createTempFile("example", FileExtensions.VCF);
        tempFile.deleteOnExit();
        final File f1 = new File("src/test/resources/htsjdk/variant/ILLUMINA.wex.broad_phase2_baseline.20111114.both.exome.genotypes.1000.vcf");
        final String[] args = {
            f1.getAbsolutePath(),
            tempFile.getAbsolutePath()
        };
        Assert.assertEquals(tempFile.length(), 0);
        PrintVariantsExample.main(args);
        Assert.assertNotEquals(tempFile.length(), 0);

        assertFilesEqualSkipHeaders(tempFile, f1);
    }

    private static void assertFilesEqualSkipHeaders(final File tempFile, final File f1) {
        final Tuple<VCFHeader, List<VariantContext>> vcf1 = VariantBaseTest.readEntireVCFIntoMemory(f1.toPath());
        final Tuple<VCFHeader, List<VariantContext>> vcf2 = VariantBaseTest.readEntireVCFIntoMemory(tempFile.toPath());
        final VCFHeader header = vcf1.a;

        final List<VariantContext> vcs1 = vcf1.b;
        final List<VariantContext> vcs2 = vcf2.b;
        Assert.assertEquals(vcs1.size(), vcs2.size());
        for (int i = 0; i < vcs1.size(); i++) {
            VariantBaseTest.assertVariantContextsAreEqual(
                vcs1.get(i).fullyDecode(header, false),
                vcs2.get(i).fullyDecode(header, false)
            );
        }
    }
}
