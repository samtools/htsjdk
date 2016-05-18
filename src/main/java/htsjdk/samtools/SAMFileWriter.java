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
package htsjdk.samtools;

import java.io.Closeable;

import htsjdk.samtools.util.ProgressLoggerInterface;

/**
 * Interface for SAMText and BAM file writers.  Clients need not care which they write to,
 * once the object is constructed.
 */
public interface SAMFileWriter extends Closeable {

	void addAlignment(SAMRecord alignment);

    SAMFileHeader getFileHeader();

	/**
	 * Sets a ProgressLogger on this writer. This is useful when pulling, for instance, from a
	 * SortingCollection.
	 */
	void setProgressLogger(final ProgressLoggerInterface progress);

    /**
     * Must be called to flush or file will likely be defective. 
     */
    void close();
}
