/*
 * Copyright (c) 2019, The Broad Institute
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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package htsjdk.tribble.IntervalList;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.IntervalList;
import htsjdk.samtools.util.IntervalListTest;
import htsjdk.tribble.*;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class IntervalListCodecTest extends HtsjdkTest {

    @DataProvider()
    public Object[][] simpleDecodeData() {
        return new Object[][]{
                {"chr1\t1\t3\t-\thi, Mom!", new Interval("chr1", 1, 3, true, "hi, Mom!")},
                {"chr1\t1\t3\t+\thi, Mom!", new Interval("chr1", 1, 3, false, "hi, Mom!")},
                {"chr1\t4\t3\t-\thi, Mom!", new Interval("chr1", 4, 3, true, "hi, Mom!")},
                {"chr2\t1\t0\t-\thi, Mom!", new Interval("chr2", 1, 0, true, "hi, Mom!")},
        };
    }

    @Test(dataProvider = "simpleDecodeData")
    public void testSimpleDecode(final String decodeThis, final Interval expectedInterval) throws IOException {
        final SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(IOUtil.getPath(TestUtils.DATA_DIR + "interval_list/example.dict"));
        final IntervalListCodec codec = new IntervalListCodec(dict);
        final Interval interval;

        interval = codec.decode(decodeThis);
        Assert.assertTrue(interval.equalsWithStrandAndName(expectedInterval));
    }

    @DataProvider
    Object[][] TribbleDecodeData(){
        return new Object[][]{
                {new File(TestUtils.DATA_DIR, "interval_list/shortExample.interval_list")},
                {new File(TestUtils.DATA_DIR, "interval_list/shortExampleWithEmptyLine.interval_list")}
        };
    }

    @Test(dataProvider = "TribbleDecodeData")
    public void testTribbleDecode(final File file) throws IOException {
        final IntervalList intervalListLocal = IntervalList.fromFile(file);
        try (final FeatureReader<Interval> intervalListReader = AbstractFeatureReader.getFeatureReader(file.getAbsolutePath(), new IntervalListCodec(), false);
             final CloseableTribbleIterator<Interval> iterator = intervalListReader.iterator()) {
            Assert.assertEquals(intervalListLocal.getHeader(), intervalListReader.getHeader());

            for (final Interval interval : intervalListLocal) {
                Assert.assertTrue(iterator.hasNext());
                Assert.assertTrue(interval.equalsWithStrandAndName(iterator.next()));
            }
            Assert.assertFalse(iterator.hasNext());
        }
    }

    /**
     * Test reading a IntervalList file which is malformed.
     */
    @Test(expectedExceptions = RuntimeException.class, dataProvider = "brokenFiles", dataProviderClass = IntervalListTest.class)
    public void testDecodeIntervalListFile_bad(Path file) throws Exception {
        IntervalListCodec codec = new IntervalListCodec();

        try (FeatureReader<Interval> intervalListReader = AbstractFeatureReader.getFeatureReader(IOUtil.getFullCanonicalPath(file.toFile()), codec, false);
             CloseableTribbleIterator<Interval> iter = intervalListReader.iterator()) {
            for (final Feature unused : iter) {
            }
        }
    }

    // Once someone implement tabix interval-lists, this should fail (and they should make a test that passes...)
    @Test(expectedExceptions = TribbleException.class)
    public void testGetTabixFormat() {
        new IntervalListCodec().getTabixFormat();
    }


    @Test
    public void testCanDecode() {
        final IntervalListCodec codec = new IntervalListCodec();
        final String pattern = "filename.interval_list";
        Assert.assertTrue(codec.canDecode(pattern));
    }
}
