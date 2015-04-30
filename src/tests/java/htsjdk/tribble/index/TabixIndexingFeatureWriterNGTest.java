/*
 * The MIT License
 *
 * Copyright 2015 sol.
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
package htsjdk.tribble.index;

import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.CloseableTribbleIterator;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.bed.BEDFeature;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.index.tabix.TabixIndexCreator;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.tribble.util.TabixUtils;
import htsjdk.tribble.writers.LineEncoder;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 *
 * @author shenkers
 */
public class TabixIndexingFeatureWriterNGTest {

    public TabixIndexingFeatureWriterNGTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
    }

    @AfterMethod
    public void tearDownMethod() throws Exception {
    }

    @Test
    public void testCreateTabixIndexForBGZF() throws Exception {
        BEDCodec codec = new BEDCodec();
        // we'll read feature data from this file
        String file1 = "testdata/htsjdk/tribble/test.bed";
        // and create an indexed BGZF file at this location
        String file2 = "testdata/htsjdk/tribble/test.tabixindex.bed.gz";

        new File(file2).deleteOnExit();
        new File(file2 + TabixUtils.STANDARD_INDEX_EXTENSION).deleteOnExit();

        TabixIndexCreator tabixIndexCreator = new TabixIndexCreator(TabixFormat.BED);
         LineEncoder<BEDFeature> encoder = new LineEncoder<BEDFeature>() {

            @Override
            public String encode(BEDFeature t) {
                return String.format("%s\t%d\t%d", t.getChr(), t.getStart() - 1, t.getEnd());
            }
        };
        
        // setup the indexing feature writer
        TabixIndexingFeatureWriter instance = new TabixIndexingFeatureWriter(new File(file2), TabixIndexingFeatureWriter.Compression.BGZF, encoder, tabixIndexCreator);

        // read features from the feature file, pass them to the indexer
        AbstractFeatureReader<BEDFeature, LineIterator> featureReader = AbstractFeatureReader.getFeatureReader(file1, codec, false);
        CloseableTribbleIterator<BEDFeature> it = featureReader.iterator();
        while (it.hasNext()) {
            BEDFeature next = it.next();
            instance.add(next);
        }
        // close the indexer, which finalizes and writes the index
        Index index = instance.close();

        // open the generated file
        AbstractFeatureReader<BEDFeature, LineIterator> tifr = AbstractFeatureReader.getFeatureReader(file2, file2 + TabixUtils.STANDARD_INDEX_EXTENSION, codec, false);
        // let's try querying an interval
        CloseableTribbleIterator<BEDFeature> itt = tifr.query("chr1", 101, 201);

        Set<String> overlaps = new HashSet();
        while (itt.hasNext()) {
            BEDFeature next = itt.next();
            overlaps.add(String.format("%s:%d-%d", next.getChr(), next.getStart(), next.getEnd()));
        }

        Set<String> expected = new HashSet();
        expected.add("chr1:101-101");
        expected.add("chr1:201-201");
        assertEquals(overlaps, expected);
    }

    @Test
    public void testCreateTabixIndexForUncompressedFile() throws Exception {
        BEDCodec codec = new BEDCodec();
        // we'll read feature data from this file
        String file1 = "testdata/htsjdk/tribble/test.bed";
        // and create an indexed BGZF file at this location
        String file2 = "testdata/htsjdk/tribble/test.tabixindex.uncompressed.bed";

        new File(file2).deleteOnExit();
        new File(file2 + TabixUtils.STANDARD_INDEX_EXTENSION).deleteOnExit();

        TabixIndexCreator tabixIndexCreator = new TabixIndexCreator(TabixFormat.BED);
        LineEncoder<BEDFeature> encoder = new LineEncoder<BEDFeature>() {

            @Override
            public String encode(BEDFeature t) {
                return String.format("%s\t%d\t%d", t.getChr(), t.getStart() - 1, t.getEnd());
            }
        };

        // setup the indexing feature writer
        TabixIndexingFeatureWriter instance = new TabixIndexingFeatureWriter(new File(file2), TabixIndexingFeatureWriter.Compression.NONE, encoder, tabixIndexCreator);

        // read features from the feature file, pass them to the indexer
        AbstractFeatureReader<BEDFeature, LineIterator> featureReader = AbstractFeatureReader.getFeatureReader(file1, codec, false);
        CloseableTribbleIterator<BEDFeature> it = featureReader.iterator();
        while (it.hasNext()) {
            BEDFeature next = it.next();
            instance.add(next);
        }
        // close the indexer, which finalizes and writes the index
        Index index = instance.close();

        // open the generated file
        AbstractFeatureReader<BEDFeature, LineIterator> tifr = AbstractFeatureReader.getFeatureReader(file2, file2 + TabixUtils.STANDARD_INDEX_EXTENSION, codec, false);
        // let's try querying an interval
        CloseableTribbleIterator<BEDFeature> itt = tifr.query("chr1", 101, 201);

        Set<String> overlaps = new HashSet();
        while (itt.hasNext()) {
            BEDFeature next = itt.next();
            overlaps.add(String.format("%s:%d-%d", next.getChr(), next.getStart(), next.getEnd()));
        }

        Set<String> expected = new HashSet();
        expected.add("chr1:101-101");
        expected.add("chr1:201-201");
        assertEquals(overlaps, expected);
    }

}
