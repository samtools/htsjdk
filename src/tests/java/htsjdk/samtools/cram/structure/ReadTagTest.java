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

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
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

public class ReadTagTest {

    @Test
    public void test () {
        SAMFileHeader h = new SAMFileHeader();
        SAMRecord r = new SAMRecord(h);
        r.setAttribute("OQ", "A:SOME:RANDOM:NONSENSE".getBytes());
        r.setAttribute("XA", 1333123);
        r.setAttribute("XB", (byte) 31);
        r.setAttribute("XB", 'Q');
        r.setAttribute("XC", "A STRING");

        int intValue = 1123123123;
        byte[] data = ReadTag.writeSingleValue((byte) 'i', intValue, false);
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        Object value = ReadTag.readSingleValue((byte) 'i', byteBuffer, ValidationStringency.DEFAULT_STRINGENCY);
        Assert.assertEquals (((Integer) value).intValue(), intValue);

        String sValue = "value";
        data = ReadTag.writeSingleValue((byte) 'Z', sValue, false);
        byteBuffer = ByteBuffer.wrap(data);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        value = ReadTag.readSingleValue((byte) 'Z', byteBuffer, ValidationStringency.DEFAULT_STRINGENCY);
        Assert.assertEquals(sValue, value);

        byte[] baValue = "value".getBytes();
        data = ReadTag.writeSingleValue((byte) 'B', baValue, false);
        byteBuffer = ByteBuffer.wrap(data);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        value = ReadTag.readSingleValue((byte) 'B', byteBuffer, ValidationStringency.DEFAULT_STRINGENCY);
        Assert.assertEquals((byte[]) value, baValue);
    }

    @Test
    public void testUnsignedInt() {
        long intValue = Integer.MAX_VALUE+1L;
        byte[] data = ReadTag.writeSingleValue((byte) 'I', intValue, false);
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        Object value = ReadTag.readSingleValue((byte) 'I', byteBuffer, ValidationStringency.SILENT);
        Assert.assertTrue(value instanceof Long);
        long lValue = (Long)value;
        Assert.assertEquals (lValue & 0xFFFFFFFF, intValue);
    }

    @Test
    public void testParallelReadTag() throws Exception {
        // NOTE: testng 5.5 (circa 2007) doesn't support parallel data providers, but modern versions do.
        // For now, roll our own.
        final Object[][] allArgs = getParallelReadTagData();
        final long timeout = 1000L * 5; // just in case
        final List<Thread> threads = new ArrayList<Thread>(allArgs.length);
        final Map<Object[], Exception> results = Collections.synchronizedMap(new HashMap<Object[], Exception>());
        for (final Object[] argLine: allArgs) {
            threads.add(new Thread() {
                @Override
                public void run() {
                    try {
                        testParallelReadTag((Byte)argLine[0], argLine[1]);
                    } catch (final Exception e) {
                        Assert.assertNull(results.put(argLine, e));
                    }
                }
            });
        }
        for (final Thread thread: threads) {
            thread.start();
        }
        for (final Thread thread: threads) {
            thread.join(timeout);
        }
        for (final Map.Entry<Object[], Exception> result: results.entrySet()) {
            // Will fail only on the first, for now, but a debugger will be able to see all the results.
            Assert.fail("failed: " + Arrays.toString(result.getKey()), result.getValue());
        }
    }

    //@Test(dataProvider = "parallelReadTagData")
    public void testParallelReadTag(final byte tagType, final Object originalValue) {
        // refactored from ReadTag.main()
        final byte[] data = ReadTag.writeSingleValue(tagType, originalValue, false);
        final ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        final Object readValue = ReadTag.readSingleValue(tagType, byteBuffer, ValidationStringency.DEFAULT_STRINGENCY);
        Assert.assertEquals(readValue, originalValue);
    }

    //@DataProvider(name = "parallelReadTagData", parallel = true)
    public Object[][] getParallelReadTagData() {
        final int testCount = 10;
        final Object[][] testData = new Object[testCount][];
        for (int i = 0; i < testCount; i++) {
            testData[i] = new Object[]{(byte)'Z', "test" + i};
        }
        return testData;
    }
}
