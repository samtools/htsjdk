/*
 * The MIT License
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
package htsjdk.tribble.gtf;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.TestUtils;
import htsjdk.tribble.readers.LineIterator;


public class GtfCodecTest extends HtsjdkTest {

    final static String DATA_DIR = TestUtils.DATA_DIR + "/gtf/";

    private final Path gencode_human_scn5a = Paths.get(DATA_DIR + "gencode.v43.annotation.gtf");
    private final Path gencode_mouse_scn5a_bgzip = Paths.get(DATA_DIR + "gencode.vM32.annotation.gtf.gz");


    @DataProvider(name = "basicDecodeDataProvider")
    Object[][] basicDecodeDataProvider() {
        	return new Object[][]{
                {gencode_human_scn5a, 434},
                {gencode_mouse_scn5a_bgzip, 5}
        };
    }


    @Test(dataProvider = "basicDecodeDataProvider")
    public void countFeaturesTest(final Path inputGtf, final int expectedTotalFeatures) throws IOException {
        final Set<String> skip_attributes = new HashSet<>(Arrays.asList("version","rank","gene_type","mgi_id","havana_gene","tag"));
        final GtfCodec codec = new GtfCodec(S->skip_attributes.contains(S));
        final String filename  = inputGtf.toAbsolutePath().toString();
        Assert.assertTrue(codec.canDecode(filename));
        try(final AbstractFeatureReader<GtfFeature, LineIterator> reader = AbstractFeatureReader.getFeatureReader(filename, null,codec, false)) {
	        int countTotalFeatures = 0;
	        for (final GtfFeature feature : reader.iterator()) {
	            for(final String key : skip_attributes) {
	                Assert.assertNull(feature.getAttribute(key));
	            }
	        countTotalFeatures++;
	        }
        Assert.assertEquals(countTotalFeatures, expectedTotalFeatures);
        }
    }


    @Test(dataProvider = "basicDecodeDataProvider")
    public void equalityTest(final Path inputGtf, final int expectedTotalFeatures) throws IOException {
        final GtfCodec codec1 = new GtfCodec();
        Assert.assertTrue(codec1.canDecode(inputGtf.toAbsolutePath().toString()));
        final GtfCodec codec2 = new GtfCodec();
        Assert.assertTrue(codec2.canDecode(inputGtf.toAbsolutePath().toString()));
        final String filename  = inputGtf.toAbsolutePath().toString();
        GtfFeature previous = null;
        try(final AbstractFeatureReader<GtfFeature, LineIterator> reader1 = AbstractFeatureReader.getFeatureReader( filename, null,codec1, false)) {
        	  try(final AbstractFeatureReader<GtfFeature, LineIterator> reader2 = AbstractFeatureReader.getFeatureReader( filename, null,codec2, false)) {
        		Iterator<GtfFeature> iter1 = reader1.iterator();
        		Iterator<GtfFeature> iter2 = reader2.iterator();
        		while(iter1.hasNext()) {
        			
        			GtfFeature feat1 = iter1.next();
        			GtfFeature feat2 = iter2.next();
        			Assert.assertTrue(feat1.equals(feat2));
        			Assert.assertEquals(feat1.hashCode(), feat2.hashCode());
        			Assert.assertFalse(feat1.equals(previous));
        			previous = feat1;
        		}
        	Assert.assertFalse(iter2.hasNext());
        	}
        }
    }

    
}