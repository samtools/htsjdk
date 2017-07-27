/*
 * The MIT License
 *
 * Copyright (c) 2017 The Broad Institute
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
package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.Iso8601Date;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Date;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Test for SAMReadGroupRecordTest
 */
public class SAMReadGroupRecordTest extends HtsjdkTest {

    @Test
    public void testGetSAMString() {
        SAMReadGroupRecord r = new SAMReadGroupRecord("rg1");
        r.setSample("mysample");
        r.setPlatform("ILLUMINA");
        r.setDescription("my description");
        Assert.assertEquals("@RG\tID:rg1\tSM:mysample\tPL:ILLUMINA\tDS:my description", r.getSAMString());
    }

    @Test
    public void testGettersAndSetters() throws Exception {
        final SAMReadGroupRecord rg = new SAMReadGroupRecord("rg1");
        // test the ID from the two getters
        Assert.assertEquals(rg.getId(), "rg1");
        Assert.assertEquals(rg.getReadGroupId(), "rg1");

        // test that the rest of getters returns null if unset
        testGetterAndSetter(rg, "sample", SAMReadGroupRecord::setSample, SAMReadGroupRecord::getSample);
        testGetterAndSetter(rg, "library", SAMReadGroupRecord::setLibrary, SAMReadGroupRecord::getLibrary);
        testGetterAndSetter(rg, "library", SAMReadGroupRecord::setPlatformUnit, SAMReadGroupRecord::getPlatformUnit);
        testGetterAndSetter(rg, "platform", SAMReadGroupRecord::setPlatform, SAMReadGroupRecord::getPlatform);
        testGetterAndSetter(rg, new Iso8601Date(new Date()), SAMReadGroupRecord::setRunDate, SAMReadGroupRecord::getRunDate);
        testGetterAndSetter(rg, "flow_order", SAMReadGroupRecord::setFlowOrder, SAMReadGroupRecord::getFlowOrder);
        testGetterAndSetter(rg, "key_sequence", SAMReadGroupRecord::setKeySequence, SAMReadGroupRecord::getKeySequence);
        testGetterAndSetter(rg, "sequencing_center", SAMReadGroupRecord::setSequencingCenter, SAMReadGroupRecord::getSequencingCenter);
        testGetterAndSetter(rg, "description", SAMReadGroupRecord::setDescription, SAMReadGroupRecord::getDescription);
        testGetterAndSetter(rg, 10, SAMReadGroupRecord::setPredictedMedianInsertSize, SAMReadGroupRecord::getPredictedMedianInsertSize);
        testGetterAndSetter(rg, "program_group", SAMReadGroupRecord::setProgramGroup, SAMReadGroupRecord::getProgramGroup);
        testGetterAndSetter(rg, "platform_model", SAMReadGroupRecord::setPlatformModel, SAMReadGroupRecord::getPlatformModel);
    }

    private static <T> void testGetterAndSetter(final SAMReadGroupRecord record, final T value,
            final BiConsumer<SAMReadGroupRecord, T> setter,
            final Function<SAMReadGroupRecord, T> getter) {
        Assert.assertNull(getter.apply(record));
        setter.accept(record, value);
        Assert.assertEquals(getter.apply(record), value);
        setter.accept(record, null);
        Assert.assertNull(getter.apply(record));
    }

    @Test
    public void testSetNonIso8601Date() throws Exception {
        final SAMReadGroupRecord rg = new SAMReadGroupRecord("rg1");
        // set not ISO 8601 date
        final Date date = new Date();
        rg.setRunDate(date);
        // and assert that it is correctly wrapped
        Assert.assertEquals(rg.getRunDate(), new Iso8601Date(date));
    }


    @DataProvider
    public Object[][] readGroupsForEquals() {
        final SAMReadGroupRecord empty = new SAMReadGroupRecord("empty");
        final SAMReadGroupRecord withSample = new SAMReadGroupRecord("rg1");
        withSample.setSample("sample1");
        return new Object[][] {
                // same object
                {empty, empty, true},
                {withSample, withSample, true},
                // null or different class
                {empty, null, false},
                {empty, empty.getId(), false},
                // different information set
                {empty, withSample, false},
                {withSample, empty, false},
        };
    }

    @Test(dataProvider = "readGroupsForEquals")
    public void testEqualsAndHashcode(final SAMReadGroupRecord rg, final Object other, final boolean isEqual) throws Exception {
        Assert.assertEquals(rg.equals(other), isEqual);
        if (isEqual) {
            Assert.assertEquals(rg.hashCode(), other.hashCode());
        }
    }

}
