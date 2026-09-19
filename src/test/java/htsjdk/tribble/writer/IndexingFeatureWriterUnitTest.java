/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 Daniel Gomez-Sanchez
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

import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.tribble.FeatureCodec;
import htsjdk.tribble.Tribble;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.bed.BEDSimpleEncoder;
import htsjdk.tribble.index.DynamicIndexCreator;
import htsjdk.tribble.index.Index;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.index.tabix.TabixIndexCreator;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/**
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class IndexingFeatureWriterUnitTest extends FeatureWriterTester {

    @Test(dataProvider = "indexedBedFiles")
    public void readWriteSimpleTribbleIndexedBed(final File bedFile) throws Exception {

        final File output = testReadWrite(bedFile, ".bed", (FeatureCodec) new BEDCodec(), (f) -> {
            try {
                return new IndexingFeatureWriter<>(new BEDSimpleEncoder(), new FileOutputStream(f),
                        // test with DynamicIndexCreator
                        new DynamicIndexCreator(f, IndexFactory.IndexBalanceApproach.FOR_SEEK_TIME),
                        // test with an index file without a sequence dictionary
                        Tribble.indexFile(f).toPath(), null);
            } catch (final FileNotFoundException e) {
                throw new AssertionError(e.getMessage());
            }
        });

        // assert that the indexes are the same
        // actual index loaded from the output from read-write
        final Index actual = IndexFactory.loadIndex(Tribble.indexFile(output).getAbsolutePath());
        // create an index on-memory for the original to check concordance
        final Index expected = IndexFactory.createDynamicIndex(bedFile, new BEDCodec());

        assertEqualsIndexes(actual, expected);
    }

    @Test(dataProvider = "bgzipIndexedBedFiles")
    public void readWriteSimpleTabixIndexedBed(final File bedFile) throws Exception {

        final File output = testReadWrite(bedFile, ".bed.gz", (FeatureCodec) new BEDCodec(), (f) -> {
            try {
                return new IndexingFeatureWriter<>(new BEDSimpleEncoder(), new BlockCompressedOutputStream(new FileOutputStream(f), f),
                        // test with TabixIndexCreator
                        new TabixIndexCreator(null, TabixFormat.BED),
                        // test with an index file without a sequence dictionary
                        Tribble.tabixIndexFile(f).toPath(), null);
            } catch (final FileNotFoundException e) {
                throw new AssertionError(e.getMessage());
            }
        });

        // assert that the indexes are the same
        // actual index loaded from the output from read-write
        final Index actual = IndexFactory.loadIndex(Tribble.tabixIndexFile(output).getAbsolutePath());
        // create an index on-memory for the original to check concordance
        final Index expected = IndexFactory.createTabixIndex(bedFile, new BEDCodec(), null);

        assertEqualsIndexes(actual, expected);
    }

    public void assertEqualsIndexes(final Index actual, final Index expected) throws Exception {
        Assert.assertEquals(actual.getClass(), expected.getClass(), "index class");
        Assert.assertEquals(actual.getSequenceNames(), expected.getSequenceNames(), "sequences");
        Assert.assertEquals(actual.getProperties(), expected.getProperties(), "properties");
    }
}