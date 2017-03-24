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

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Test for SAMReadGroupRecordTest
 */
public class SAMSequenceRecordTest {

    @Test
    public void testGetSAMString() {
        SAMSequenceRecord r = new SAMSequenceRecord("chr5_but_without_a_prefix", 271828);
        r.setSpecies("Psephophorus terrypratchetti");
        r.setAssembly("GRCt01");
        r.setMd5("7a6dd3d307de916b477e7bf304ac22bc");
        Assert.assertEquals("@SQ\tSN:chr5_but_without_a_prefix\tLN:271828\tSP:Psephophorus terrypratchetti\tAS:GRCt01\tM5:7a6dd3d307de916b477e7bf304ac22bc", r.getSAMString());
    }
}
