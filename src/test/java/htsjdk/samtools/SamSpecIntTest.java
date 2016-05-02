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

package htsjdk.samtools;

import htsjdk.samtools.util.CloserUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SamSpecIntTest {
    private static final File SAM_INPUT = new File("src/test/resources/htsjdk/samtools/inttest.sam");
    private static final File BAM_INPUT = new File("src/test/resources/htsjdk/samtools/inttest.bam");

    @Test
    public void testSamIntegers() throws IOException {
        final List<String> errorMessages = new ArrayList<String>();
        final SamReader samReader = SamReaderFactory.makeDefault().open(SAM_INPUT);
        final File bamOutput = File.createTempFile("test", ".bam");
        final File samOutput = File.createTempFile("test", ".sam");
        final SAMFileWriter samWriter = new SAMFileWriterFactory().makeWriter(samReader.getFileHeader(), true, samOutput, null);
        final SAMFileWriter bamWriter = new SAMFileWriterFactory().makeWriter(samReader.getFileHeader(), true, bamOutput, null);

        final SAMRecordIterator iterator = samReader.iterator();
        while (iterator.hasNext()) {
            try {
                final SAMRecord rec = iterator.next();
                samWriter.addAlignment(rec);
                bamWriter.addAlignment(rec);
            } catch (final Throwable e) {
                System.out.println(e.getMessage());
                errorMessages.add(e.getMessage());
            }
        }

        CloserUtil.close(samReader);
        samWriter.close();
        bamWriter.close();
        Assert.assertEquals(errorMessages.size(), 0);
        bamOutput.deleteOnExit();
        samOutput.deleteOnExit();
    }

    @Test
    public void testBamIntegers() throws IOException {
        final List<String> errorMessages = new ArrayList<String>();
        final SamReader bamReader = SamReaderFactory.makeDefault().open(BAM_INPUT);
        final File bamOutput = File.createTempFile("test", ".bam");
        final File samOutput = File.createTempFile("test", ".sam");
        final SAMFileWriter samWriter = new SAMFileWriterFactory().makeWriter(bamReader.getFileHeader(), true, samOutput, null);
        final SAMFileWriter bamWriter = new SAMFileWriterFactory().makeWriter(bamReader.getFileHeader(), true, bamOutput, null);
        final SAMRecordIterator iterator = bamReader.iterator();
        while (iterator.hasNext()) {
            try {
                final SAMRecord rec = iterator.next();
                samWriter.addAlignment(rec);
                bamWriter.addAlignment(rec);
            } catch (final Throwable e) {
                System.out.println(e.getMessage());
                errorMessages.add(e.getMessage());
            }
        }

        CloserUtil.close(bamReader);
        samWriter.close();
        bamWriter.close();
        Assert.assertEquals(errorMessages.size(), 0);
        bamOutput.deleteOnExit();
        samOutput.deleteOnExit();
    }

}
