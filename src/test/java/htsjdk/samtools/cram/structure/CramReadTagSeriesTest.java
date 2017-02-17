/*
 * The MIT License
 *
 * Copyright (c) 2015 The Broad Institute
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
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CramReadTagSeriesTest {

    @Test
    public void testConstructors() {
        String tagName = "AB";
        byte tagValueType = 'i';
        short bamTagCode = SAMTagUtil.getSingleton().makeBinaryTag(tagName);
        int cramTagId = (((tagName.getBytes()[0]) & 0xFF) << 16) | (((tagName.getBytes()[1]) & 0xFF) << 8) | (0xFF & tagValueType);
        byte[] bytes = new byte[]{tagName.getBytes()[0], tagName.getBytes()[1], tagValueType};

        CramReadTagSeries series = new CramReadTagSeries(tagName, tagValueType);
        Assert.assertEquals(series.tagName, tagName);
        Assert.assertEquals(series.valueType, tagValueType);
        Assert.assertEquals(series.bamTagCode, bamTagCode);
        Assert.assertEquals(series.cramTagId, cramTagId);

        series = new CramReadTagSeries(cramTagId);
        Assert.assertEquals(series.tagName, tagName);
        Assert.assertEquals(series.valueType, tagValueType);
        Assert.assertEquals(series.bamTagCode, bamTagCode);
        Assert.assertEquals(series.cramTagId, cramTagId);

        series = new CramReadTagSeries(bytes);
        Assert.assertEquals(series.tagName, tagName);
        Assert.assertEquals(series.valueType, tagValueType);
        Assert.assertEquals(series.bamTagCode, bamTagCode);
        Assert.assertEquals(series.cramTagId, cramTagId);
    }

    @Test
    public void testMethods() {
        String tagName = "AB";
        byte tagValueType = 'i';
        short bamTagCode = SAMTagUtil.getSingleton().makeBinaryTag(tagName);
        int cramTagId = (((tagName.getBytes()[0]) & 0xFF) << 16) | (((tagName.getBytes()[1]) & 0xFF) << 8) | (0xFF & tagValueType);
        byte[] bytes = new byte[]{tagName.getBytes()[0], tagName.getBytes()[1], tagValueType};

        Assert.assertEquals(cramTagId, CramReadTagSeries.readCramTagId(bytes));
        Assert.assertEquals(cramTagId, CramReadTagSeries.tagIntId(bamTagCode, tagValueType));
        Assert.assertEquals(bamTagCode, CramReadTagSeries.cramTagIdToBamTagCode(cramTagId));
        Assert.assertEquals(cramTagId, CramReadTagSeries.readCramTagId(bytes));
        Assert.assertEquals(cramTagId, CramReadTagSeries.tagIntId(new SAMBinaryTagAndValue(bamTagCode, 0)));

        ByteBuffer buffer = ByteBuffer.allocate(10);
        CramReadTagSeries.writeCramTagId(cramTagId, buffer);
        buffer.flip();
        Assert.assertEquals(buffer.limit(), 3);
        byte[] writtenBytes = new byte[3];
        buffer.get(writtenBytes);
        Assert.assertEquals(writtenBytes, bytes);
    }
}
