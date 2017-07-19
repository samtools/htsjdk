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

import htsjdk.HtsjdkTest;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.Feature;
import htsjdk.tribble.FeatureReader;
import htsjdk.tribble.SimpleFeature;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.bed.BEDFeature;
import htsjdk.tribble.bed.BEDSimpleEncoder;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class FeatureWriterImplUnitTest extends HtsjdkTest {

    @DataProvider
    public Object[][] bedFiles() {
        final File testDirectory = new File("src/test/resources/htsjdk/tribble/bed");
        return new Object[][] {
                {new File(testDirectory, "disconcontigs.bed")},
                {new File(testDirectory, "NA12878.deletions.10kbp.het.gq99.hand_curated.hg19_fixed.bed")},
                {new File(testDirectory, "Unigene.sample.bed")},
                {new File(testDirectory, "unsorted.bed")}
        };
    }

    @Test(dataProvider = "bedFiles")
    public void readWriteSimpleBed(final File bedFile) throws Exception {
        final File tmpFile = File.createTempFile(bedFile.getName(), ".bed");

        // read the original file and write it down
        final List<BEDFeature> featureList = new ArrayList<>();
        try (final FeatureReader<BEDFeature> reader = AbstractFeatureReader.getFeatureReader(bedFile.toString(), new BEDCodec(), false);
             final FeatureWriter<Feature> writer = new FeatureWriterImpl<>(new BEDSimpleEncoder(), new FileOutputStream(tmpFile))) {
            for (final BEDFeature feature : reader.iterator()) {
                // accumulate in the list
                featureList.add(feature);
                // write down
                writer.add(feature);
            }
        }

        // read the written file and check if the features are the same
        try (final FeatureReader<BEDFeature> reader = AbstractFeatureReader.getFeatureReader(tmpFile.toString(), new BEDCodec(), false)) {
            final Iterator<BEDFeature> it = reader.iterator();
            for (final BEDFeature feature : featureList) {
                Assert.assertTrue(it.hasNext(), "unexpected end of file");
                final BEDFeature actual = it.next();
                Assert.assertEquals(actual.getContig(), feature.getContig());
                Assert.assertEquals(actual.getStart(), feature.getStart());
                Assert.assertEquals(actual.getEnd(), feature.getEnd());
            }
        }
    }

    private final static OutputStream NULL_OUTPUT_STREAM = new OutputStream() {
        @Override
        public void write(int b) throws IOException { }
    };

    // test encoder that keeps a list with all features added and a list of all headers added
    private final static class TestEncoder implements FeatureEncoder<Feature> {

        List<Feature> addedFeatures = new ArrayList<>();
        List<Object> addedHeaders = new ArrayList<>();

        @Override
        public void write(Appendable out, Feature feature) throws IOException {
            addedFeatures.add(feature);
        }

        @Override
        public void writeHeader(Appendable out, Object header) throws IOException {
            addedHeaders.add(header);
        }
    }


    @DataProvider
    public Object[][] featuresAndHeaders() {
        return new Object[][] {
                {Collections.singletonList(new SimpleFeature("chr1", 1, 100)), Collections.singletonList("header1")},
                {Arrays.asList(new SimpleFeature("chr1", 10, 100), new SimpleFeature("chr2", 1, 100)), Arrays.asList("header1", "header2")}
        };
    }

    @Test(dataProvider = "featuresAndHeaders")
    public void testWriteHeaderAndFeatures(final List<Feature> featuresToWrite, final List<Object> headersToWrite) throws Exception {
        final TestEncoder encoder = new TestEncoder();
        try(FeatureWriter<Feature> writer = new FeatureWriterImpl<>(encoder, NULL_OUTPUT_STREAM)) {
            for (final Object o: headersToWrite) {
                writer.writeHeader(o);
            }
            for (final Feature f: featuresToWrite) {
                writer.add(f);
            }
        }
        Assert.assertEquals(encoder.addedHeaders, headersToWrite);
        Assert.assertEquals(encoder.addedFeatures, featuresToWrite);
    }

}