/*
 * The MIT License
 *
 * Copyright (c) 2019 Nils Homer
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
package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.*;

public class IntervalCodecTest extends HtsjdkTest {

    private SAMSequenceDictionary dict;

    @BeforeTest
    void setup() {
        this.dict = new SAMSequenceDictionary();
        this.dict.addSequence(new SAMSequenceRecord("chr1", 10000));
        this.dict.addSequence(new SAMSequenceRecord("chr2", 20000));
        this.dict.addSequence(new SAMSequenceRecord("chr3", 30000));
    }

    @Test
    public void testEndToEnd() throws IOException  {
        final File tempFile = File.createTempFile("IntervalCodecTest.", ".interval_list");
        tempFile.deleteOnExit();

        final IntervalList expectedList = new IntervalList(this.dict);
        final IntervalList actualList = new IntervalList(this.dict);

        expectedList.add(new Interval("chr1", 50, 150));
        expectedList.add(new Interval("chr1", 150, 250));
        expectedList.add(new Interval("chr2", 50, 150));
        expectedList.add(new Interval("chr3", 50, 150));
        expectedList.add(new Interval("chr1", 50, 150, true, "number-5"));
        expectedList.add(new Interval("chr1", 150, 250, false, "number-6"));

        final OutputStream outputStream = new FileOutputStream(tempFile);
        final IntervalCodec writeCodec = new IntervalCodec(this.dict);
        writeCodec.setOutputStream(outputStream);
        for (final Interval interval : expectedList.getIntervals()) {
            writeCodec.encode(interval);
        }
        outputStream.close();

        final IntervalCodec readCodec = new IntervalCodec(this.dict);
        final InputStream inputStream = new FileInputStream(tempFile);
        readCodec.setInputStream(inputStream);
        while (true) {
            final Interval interval = readCodec.decode();
            if (interval == null) {
                break;
            }
            else {
                actualList.add(interval);
            }
        }
        inputStream.close();

        Assert.assertEquals(
                actualList.getIntervals(),
                expectedList.getIntervals()
        );
    }
}
