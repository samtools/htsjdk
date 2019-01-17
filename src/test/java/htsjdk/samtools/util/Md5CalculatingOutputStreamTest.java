/*
 * The MIT License
 *
 * Copyright (c) 2014 The Broad Institute
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

import com.google.common.base.Charsets;
import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public class Md5CalculatingOutputStreamTest extends HtsjdkTest {
    @DataProvider(name = "fileContents")
    public Object[][] createDBQTestData() {
        return new Object[][]{
                {"", "d41d8cd98f00b204e9800998ecf8427e"}, // No zeroes at the start
                {"a", "0cc175b9c0f1b6a831c399e269772661"}, // One zero
                {"jk8ssl", "0000000018e6137ac2caab16074784a6"}, // Many zeroes, thanks @delta14 at StackOverflow
        };
    }

    @Test(dataProvider = "fileContents")
    public void testMd5(final String contents, final String expectedMd5) throws IOException {
        byte[] bytes = contents.getBytes(Charsets.US_ASCII);
        OutputStream outputStream = new ByteArrayOutputStream(bytes.length);
        Md5CalculatingOutputStream md5 = new Md5CalculatingOutputStream(outputStream, (File) null);
        md5.write(bytes);
        md5.close(); // Cannot use try-with-resources because we need a value after closing the stream
        Assert.assertEquals(md5.md5(), expectedMd5);
    }
}
