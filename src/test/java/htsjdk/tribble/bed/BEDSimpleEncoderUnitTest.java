/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Daniel Gomez-Sanchez
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package htsjdk.tribble.bed;

import htsjdk.tribble.Feature;
import htsjdk.tribble.SimpleFeature;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class BEDSimpleEncoderUnitTest {

    private static final BEDSimpleEncoder TEST_ENCODER = new BEDSimpleEncoder();

    @DataProvider(name = "featuresToEncode")
    public Object[][] getFeaturesToEncode() {
        return new Object[][] {
                {new SimpleFeature("chr1", 1, 100), "chr1\t0\t100"},
                {new SimpleBEDFeature(100, 200, "chr20"), "chr20\t99\t200"}
        };
    }

    @Test(dataProvider = "featuresToEncode")
    public void testEncodeFeatures(final Feature feature, final String expected) throws Exception {
        final StringBuilder stringBuilder = new StringBuilder();
        TEST_ENCODER.write(stringBuilder, feature);
        Assert.assertEquals(stringBuilder.toString(), expected);
    }

    @Test
    public void testNoOpWriteHeader() throws Exception {
        final StringBuilder builder = new StringBuilder();
        TEST_ENCODER.writeHeader(builder, "StringHeader");
        Assert.assertEquals(builder.toString(), "");
    }

}