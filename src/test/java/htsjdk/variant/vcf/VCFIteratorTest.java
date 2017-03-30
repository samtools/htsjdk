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
import java.io.IOException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

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

    private int countVariants(VCFIterator r) {
        return (int) StreamSupport.stream(Spliterators.spliteratorUnknownSize(r, Spliterator.ORDERED), true).count();
    }

    @Test(dataProvider = "VcfFiles")
    public void testUsingUri(String uri, int nVariants) throws IOException {
        VCFIterator r = new VCFIteratorBuilder().open(uri);
        Assert.assertNotNull(r.getFileHeader());
        Assert.assertEquals(countVariants(r), nVariants);
        r.close();
    }

    @Test(dataProvider = "VcfFiles")
    public void testUsingFile(String uri, int nVariants) throws IOException {
        VCFIterator r = new VCFIteratorBuilder().open(new File(uri));
        Assert.assertNotNull(r.getFileHeader());
        Assert.assertEquals(countVariants(r), nVariants);
        r.close();
    }

    @Test(dataProvider = "VcfFiles")
    public void testUsingStreams(String uri, int nVariants) throws IOException {
        VCFIterator r = new VCFIteratorBuilder().open(uri);
        Assert.assertNotNull(r.getFileHeader());
        Assert.assertEquals(countVariants(r), nVariants);
        r.close();
    }

}
