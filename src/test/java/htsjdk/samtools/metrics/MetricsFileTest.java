/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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

package htsjdk.samtools.metrics;

import htsjdk.samtools.SAMException;
import htsjdk.samtools.util.FormatUtil;
import htsjdk.samtools.util.Histogram;
import htsjdk.samtools.util.TestUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;

/**
 * Tests for the various classes in the metrics package.  Constructs a MetricsFile,
 * populates it with various items and then ensure that it can be written to disk
 * and read back without altering any values.
 *
 * @author Tim Fennell
 */
public class MetricsFileTest {
    public enum TestEnum {One, Two, Three}

    public static class TestMetric extends MetricBase implements Cloneable, Serializable {
        private static final long serialVersionUID = 1l;

        public String    STRING_PROP;
        public Date      DATE_PROP;
        public Short     SHORT_PROP;
        public Integer   INTEGER_PROP;
        public Long      LONG_PROP;
        public Float     FLOAT_PROP;
        public Double    DOUBLE_PROP;
        public TestEnum  ENUM_PROP;
        public Boolean   BOOLEAN_PROP;
        public Character CHARACTER_PROP;
        public short     SHORT_PRIMITIVE;
        public int       INT_PRIMITIVE;
        public long      LONG_PRIMITIVE;
        public float     FLOAT_PRIMITIVE;
        public double    DOUBLE_PRIMITIVE;
        public boolean   BOOLEAN_PRIMITIVE;
        public char      CHAR_PRIMITIVE;

        @Override
        public TestMetric clone()  {
            try { return (TestMetric) super.clone(); }
            catch (CloneNotSupportedException cnse) { throw new SAMException("That's Unpossible!"); }
        }
    }

    public static class FloatingPointMetric extends MetricBase{
        public double DOUBLE_PRIMITIVE;
        public Double DOUBLE_PROP;
        public float  FLOAT_PRIMITIVE;
        public Float FLOAT_PROP;
    }

    @Test
    public void testFloatingPointEquality() throws IOException {
        MetricsFile<FloatingPointMetric,Integer> file = new MetricsFile<FloatingPointMetric,Integer>();

        FloatingPointMetric metric = new FloatingPointMetric();
        metric.DOUBLE_PRIMITIVE = .0000000000000000001d;
        metric.DOUBLE_PROP = .0000000000000000001d;
        metric.FLOAT_PRIMITIVE = .0000000000000000001f;
        metric.FLOAT_PROP = .0000000000000000001f;
        file.addMetric(metric);

        MetricsFile<FloatingPointMetric,Integer> file2 = writeThenReadBack(file);
        Assert.assertEquals(file, file2);



    }

    @Test
    public void testWriteMetricsFile() throws IOException, ClassNotFoundException {
        MetricsFile<TestMetric,Integer> file = new MetricsFile<TestMetric,Integer>();
        TestMetric metric = new TestMetric();
        metric.STRING_PROP       = "Hello World";
        metric.DATE_PROP         = new FormatUtil().parseDate("2008-12-31");
        metric.SHORT_PROP        = 123;
        metric.INTEGER_PROP      = null;
        metric.LONG_PROP         = Long.MAX_VALUE;
        metric.FLOAT_PROP        = 456.789f;
        metric.DOUBLE_PROP       = 0.713487;
        metric.ENUM_PROP         = TestEnum.Two;
        metric.BOOLEAN_PROP      = false;
        metric.CHARACTER_PROP    = 'A';
        metric.SHORT_PRIMITIVE   = 123;
        metric.INT_PRIMITIVE     = 919834781;
        metric.LONG_PRIMITIVE    = Long.MAX_VALUE - Integer.MAX_VALUE;
        metric.FLOAT_PRIMITIVE   = 0.55694f;
        metric.DOUBLE_PRIMITIVE  = 0.229233;
        metric.BOOLEAN_PRIMITIVE = true;
        metric.CHAR_PRIMITIVE    = 'B';
        file.addMetric(metric);

        MetricsFile<TestMetric,Integer> file2 = writeThenReadBack(file);
        Assert.assertEquals(file, file2);

        // Now add some headers and run the test again
        StringHeader stringHeader = new StringHeader();
        stringHeader.setValue("Hello, I'm a String Header!");
        file.addHeader(stringHeader);

        VersionHeader version = new VersionHeader();
        version.setVersionedItem("MetricsFileTest");
        version.setVersionString("1.0");
        file.addHeader(version);

        version = new VersionHeader();
        version.setVersionedItem("Nada");
        version.setVersionString("0.0alpha1");
        file.addHeader(version);

        file2 = writeThenReadBack(file);
        Assert.assertEquals(file, file2);

        // Now add a Histogram and make sure it still works
        Histogram<Integer> histo = new Histogram<Integer>();
        histo.setBinLabel("small_number");
        histo.setValueLabel("big_number");
        histo.increment(1, 101);
        histo.increment(2, 202);
        histo.increment(3, 4000);
        histo.increment(5, 123981);
        histo.increment(1000, 10981982);
        file.setHistogram(histo);

        file2 = writeThenReadBack(file);
        Assert.assertEquals(file, file2);

        // And lastly add some more metrics rows to the file
        TestMetric metric2 = metric.clone();
        metric2.ENUM_PROP = TestEnum.One;
        metric2.FLOAT_PROP = 0.998f;
        metric2.STRING_PROP = "Wheeeee!";
        file.addMetric(metric2);

        metric2 = metric.clone();
        metric2.ENUM_PROP = TestEnum.Three;
        metric2.DOUBLE_PRIMITIVE = 1.299d;
        file.addMetric(metric2);

        file2 = writeThenReadBack(file);
        Assert.assertEquals(file, file2);

        //Test that we can serialize and deserialize this whole thing
        MetricsFile<TestMetric, Integer> file3 = TestUtil.serializeAndDeserialize(file);

        Assert.assertEquals(file, file3);
    }

    @Test
    public void areMetricsFilesEqualTest(){
        final File TEST_DIR = new File("src/test/resources/htsjdk/samtools/metrics/");
        final File file1 = new File(TEST_DIR,"metricsOne.metrics");
        final File file2 = new File(TEST_DIR,"metricsOneCopy.metrics");
        final File fileModifiedHist = new File(TEST_DIR,"metricsOneModifiedHistogram.metrics");
        final File fileModifiedMet = new File(TEST_DIR,"metricsOneModifiedMetrics.metrics");

        Assert.assertTrue(MetricsFile.areMetricsEqual(file1, file2));
        Assert.assertTrue(MetricsFile.areMetricsEqual(file1, fileModifiedHist));

        Assert.assertFalse(MetricsFile.areMetricsAndHistogramsEqual(file1, fileModifiedHist));
        Assert.assertFalse(MetricsFile.areMetricsEqual(file1, fileModifiedMet));
        Assert.assertFalse(MetricsFile.areMetricsAndHistogramsEqual(file1, fileModifiedMet));
    }

    /** Helper method to persist metrics to file and read them back again. */
    private <METRIC extends MetricBase> MetricsFile<METRIC, Integer> writeThenReadBack(MetricsFile<METRIC,Integer> in) throws IOException {
        File f = File.createTempFile("test", ".metrics");
        f.deleteOnExit();
        FileWriter out = new FileWriter(f);
        in.write(out);

        MetricsFile<METRIC,Integer> retval = new MetricsFile<METRIC,Integer>();
        retval.read(new FileReader(f));
        return retval;
    }



}
