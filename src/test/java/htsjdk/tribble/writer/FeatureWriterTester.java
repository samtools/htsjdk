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
import htsjdk.tribble.FeatureCodec;
import htsjdk.tribble.FeatureReader;
import org.testng.Assert;
import org.testng.annotations.DataProvider;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public abstract class FeatureWriterTester extends HtsjdkTest {

    @DataProvider
    public static Object[][] bedFiles() {
        final File testDirectory = new File("src/test/resources/htsjdk/tribble/bed");
        return new Object[][] {
                {new File(testDirectory, "disconcontigs.bed")},
                {new File(testDirectory,
                        "NA12878.deletions.10kbp.het.gq99.hand_curated.hg19_fixed.bed")},
                {new File(testDirectory, "Unigene.sample.bed")},
                {new File(testDirectory, "unsorted.bed")}
        };
    }

    /** NoOp OutputStream. */
    protected final static OutputStream NULL_OUTPUT_STREAM = new OutputStream() {
        @Override
        public void write(int b) throws IOException { }
    };


    /**
     * Test encoder that keeps a list with all features added and a list of all headers added
     */
    protected final static class TestEncoder implements FeatureEncoder<Feature> {

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

    /**
     * Tests that reading a file with FeatureReader and writing to a FeatureWriter provides the same
     * result.
     */
    protected final static <T extends Feature> void testReadWrite(final File featureFile,
            final FeatureCodec<T, ?> featureCodec,
            final Function<File, FeatureWriter<T>> writerProvider) throws Exception {
        final File tmpFile = File.createTempFile(featureFile.getName(), ".bed");

        // read the original file and write it down
        final List<T> featureList = new ArrayList<>();
        try (final FeatureReader<T> reader = AbstractFeatureReader
                .getFeatureReader(featureFile.toString(), featureCodec, false);
                final FeatureWriter<T> writer = writerProvider.apply(tmpFile)) {
            for (final T feature : reader.iterator()) {
                // accumulate in the list
                featureList.add(feature);
                // write down
                writer.add(feature);
            }
        }

        // read the written file and check if the features are the same
        try (final FeatureReader<T> reader = AbstractFeatureReader
                .getFeatureReader(tmpFile.toString(), featureCodec, false)) {
            final Iterator<T> it = reader.iterator();
            for (final T feature : featureList) {
                Assert.assertTrue(it.hasNext(), "unexpected end of file");
                final T actual = it.next();
                Assert.assertEquals(actual.getContig(), feature.getContig());
                Assert.assertEquals(actual.getStart(), feature.getStart());
                Assert.assertEquals(actual.getEnd(), feature.getEnd());
            }
        }
    }

    public final static <T extends Feature> void testWriteHeaderAndFeatures(
            final BiFunction<FeatureEncoder<Feature>, OutputStream, FeatureWriter<Feature>> writerProvider,
            final List<T> featuresToWrite,
            final List<Object> headersToWrite) throws Exception {
        final TestEncoder encoder = new TestEncoder();
        try (FeatureWriter<Feature> writer = writerProvider.apply(encoder, NULL_OUTPUT_STREAM)) {
            for (final Object o : headersToWrite) {
                writer.writeHeader(o);
            }
            for (final T f : featuresToWrite) {
                writer.add(f);
            }
        }
        Assert.assertEquals(encoder.addedHeaders, headersToWrite);
        Assert.assertEquals(encoder.addedFeatures, featuresToWrite);
    }

}
