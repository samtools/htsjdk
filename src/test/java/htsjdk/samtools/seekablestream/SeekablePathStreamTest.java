/*
 * The MIT License
 *
 * Copyright (c) 2013 The Broad Institute
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
package htsjdk.samtools.seekablestream;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SeekablePathStreamTest {

    @Test
    public void testRead() throws Exception {
        Path testPath = new File("src/test/resources/htsjdk/samtools/seekablestream/seekTest.txt").toPath();
        SeekablePathStream is = new SeekablePathStream(testPath);
        Assert.assertEquals(is.position(), 0);
        Assert.assertEquals(is.read(), (int) 'a');
        Assert.assertEquals(is.position(), 1);
        is.seek(20);
        Assert.assertEquals(is.position(), 20);
        byte[] buf = new byte[2];
        Assert.assertEquals(is.read(buf, 0, buf.length), 2);
        Assert.assertEquals(buf, new byte[] { (byte) 'c', (byte) 'c' });
        Assert.assertEquals(is.skip(8), 8);
        Assert.assertEquals(is.position(), 30);
        Assert.assertEquals(is.length(), Files.size(testPath));
        is.close();
    }
}
