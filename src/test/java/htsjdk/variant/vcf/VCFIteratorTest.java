/*
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
package htsjdk.variant.vcf;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import htsjdk.variant.VariantBaseTest;

public class VCFIteratorTest extends VariantBaseTest {

    @DataProvider(name = "VcfFiles")
    public Object[][] getVcfFiles() {
        return new Object[][] { 
                new Object[] { "src/test/resources/htsjdk/tribble/tabix/testTabixIndex.vcf", 25 },
                new Object[] { "src/test/resources/htsjdk/tribble/tabix/testTabixIndex.vcf.gz", 25 },
                new Object[] { "src/test/resources/htsjdk/variant/serialization_test.bcf", 12 } };
    }

    private void assertExpectedNumberOfVariants(final VCFIterator r, final int expectVariants) {
        try {
            Assert.assertNotNull(r.getHeader());
            final int nVariants =  (int)r.stream().count();
            Assert.assertNotNull(r.getHeader());
            Assert.assertEquals(nVariants, expectVariants);
        } finally {
            r.close();
        }
    }

    @Test(dataProvider = "VcfFiles")
    public void testUsingUri(final String uri, final int nVariants) throws IOException {
        final VCFIterator r = new VCFIteratorBuilder().open(uri);
        countVariants(r, nVariants);
    }

    @Test(dataProvider = "VcfFiles")
    public void testUsingFile(final String file, final int nVariants) throws IOException {
        final VCFIterator r = new VCFIteratorBuilder().open(new File(file));
        countVariants(r, nVariants);

    }

    @Test(dataProvider = "VcfFiles")
    public void testUsingStreams(final String filepath, final int nVariants) throws IOException {
        final InputStream in = new FileInputStream(filepath); 
        final VCFIterator r = new VCFIteratorBuilder().open(in);
        countVariants(r, nVariants);
        in.close();
    }
    
    @Test(dataProvider = "VcfFiles")
    public void testUsingPath(final String path, final int nVariants) throws IOException {
        final Path a_path = Paths.get(path);
        final VCFIterator r = new VCFIteratorBuilder().open(a_path);
        countVariants(r, nVariants);
    }
}
