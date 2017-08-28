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

package htsjdk.tribble.writer;

import htsjdk.tribble.Feature;
import htsjdk.tribble.FeatureCodec;
import htsjdk.tribble.SimpleFeature;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.bed.BEDSimpleEncoder;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class FeatureWriterImplUnitTest extends FeatureWriterTester {

    @Test(dataProvider = "bedFiles")
    public void readWriteSimpleBed(final File bedFile) throws Exception {
        testReadWrite(bedFile, (FeatureCodec) new BEDCodec(), (f) -> {
            try {
                return new FeatureWriterImpl<>(new BEDSimpleEncoder(), new FileOutputStream(f));
            } catch (final FileNotFoundException e) {
                throw new AssertionError(e.getMessage());
            }
        });
    }

    @DataProvider
    public Object[][] featuresAndHeaders() {
        return new Object[][] {
                {Collections.singletonList(new SimpleFeature("chr1", 1, 100)),
                        Collections.singletonList("header1")},
                {Arrays.asList(new SimpleFeature("chr1", 10, 100),
                        new SimpleFeature("chr2", 1, 100)), Arrays.asList("header1", "header2")}
        };
    }

    @Test(dataProvider = "featuresAndHeaders")
    public void testWriteHeaderAndFeatures(final List<Feature> featuresToWrite,
            final List<Object> headersToWrite) throws Exception {
        testWriteHeaderAndFeatures(FeatureWriterImpl::new, featuresToWrite, headersToWrite);
    }

}