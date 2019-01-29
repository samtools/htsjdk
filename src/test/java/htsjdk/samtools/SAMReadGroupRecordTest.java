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

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
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
    public void testReadGroupIdGetters() throws Exception {
        final SAMReadGroupRecord rg = new SAMReadGroupRecord("rg1");
        Assert.assertEquals(rg.getId(), "rg1");
        Assert.assertEquals(rg.getReadGroupId(), "rg1");
    }

    @DataProvider
    public Object[][] gettersAndSetters() {
        final SAMReadGroupRecord rg = new SAMReadGroupRecord("rg");
        return new Object[][] {
                {rg, "sample",
                        (BiConsumer<SAMReadGroupRecord, String>) SAMReadGroupRecord::setSample,
                        (Function<SAMReadGroupRecord, String>) SAMReadGroupRecord::getSample},
                {rg, "library",
                        (BiConsumer<SAMReadGroupRecord, String>) SAMReadGroupRecord::setLibrary,
                        (Function<SAMReadGroupRecord, String>) SAMReadGroupRecord::getLibrary},
                {rg, "library",
                        (BiConsumer<SAMReadGroupRecord, String>) SAMReadGroupRecord::setPlatformUnit,
                        (Function<SAMReadGroupRecord, String>) SAMReadGroupRecord::getPlatformUnit},
                {rg, "platform",
                        (BiConsumer<SAMReadGroupRecord, String>) SAMReadGroupRecord::setPlatform,
                        (Function<SAMReadGroupRecord, String>) SAMReadGroupRecord::getPlatform},
                {rg, new Iso8601Date(new Date()),
                        (BiConsumer<SAMReadGroupRecord, Date>) SAMReadGroupRecord::setRunDate,
                        (Function<SAMReadGroupRecord, Date>) SAMReadGroupRecord::getRunDate},
                {rg, "flow_order",
                        (BiConsumer<SAMReadGroupRecord, String>) SAMReadGroupRecord::setFlowOrder,
                        (Function<SAMReadGroupRecord, String>) SAMReadGroupRecord::getFlowOrder},
                {rg, "key_sequence",
                        (BiConsumer<SAMReadGroupRecord, String>) SAMReadGroupRecord::setKeySequence,
                        (Function<SAMReadGroupRecord, String>) SAMReadGroupRecord::getKeySequence},
                {rg, "sequencing_center",
                        (BiConsumer<SAMReadGroupRecord, String>) SAMReadGroupRecord::setSequencingCenter,
                        (Function<SAMReadGroupRecord, String>) SAMReadGroupRecord::getSequencingCenter},
                {rg, "description",
                        (BiConsumer<SAMReadGroupRecord, String>) SAMReadGroupRecord::setDescription,
                        (Function<SAMReadGroupRecord, String>) SAMReadGroupRecord::getDescription},
                {rg, 10,
                        (BiConsumer<SAMReadGroupRecord, Integer>) SAMReadGroupRecord::setPredictedMedianInsertSize,
                        (Function<SAMReadGroupRecord, Integer>) SAMReadGroupRecord::getPredictedMedianInsertSize},
                {rg, "program_group",
                        (BiConsumer<SAMReadGroupRecord, String>) SAMReadGroupRecord::setProgramGroup,
                        (Function<SAMReadGroupRecord, String>) SAMReadGroupRecord::getProgramGroup},
                {rg, "platform_model",
                        (BiConsumer<SAMReadGroupRecord, String>) SAMReadGroupRecord::setPlatformModel,
                        (Function<SAMReadGroupRecord, String>) SAMReadGroupRecord::getPlatformModel}
        };
    }

    @Test(dataProvider = "gettersAndSetters")
    public <T> void testGetterAndSetter(final SAMReadGroupRecord record, final T value,
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

    @DataProvider
    public Object[][] getBarcodes() {
        return new Object[][] {
                {null, null},
                {Collections.emptyList(), ""},
                {Collections.singletonList("aa"), "aa"},
                {Arrays.asList("aa", "ac"), "aa-ac"},
                {Arrays.asList("aa", "ca", "gg"), "aa-ca-gg"}
        };
    }

    @Test(dataProvider = "getBarcodes")
    public void testGetAndSetBarcodes(List<String> barcodes, String encoded){
        final SAMReadGroupRecord readGroup = new SAMReadGroupRecord("ReadGroup");
        Assert.assertNull(readGroup.getBarcodes());
        Assert.assertNull(readGroup.getAttribute(SAMReadGroupRecord.BARCODE_TAG));
        readGroup.setBarcodes(barcodes);
        Assert.assertEquals(readGroup.getBarcodes(), barcodes);
        Assert.assertEquals(readGroup.getAttribute(SAMReadGroupRecord.BARCODE_TAG), encoded);
    }
}
