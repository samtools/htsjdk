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

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMTextHeaderCodec;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Writes out the list of intervals to the supplied file.  This class is particularly useful if we have many intervals
 * to write, such that they all cannot be held in memory, for example in an {@link IntervalList}.
 */
public class IntervalListWriter implements Closeable {
    private static final char TAB = '\t';

    private final BufferedWriter out;

    /** Creates a new writer, writing a header to the file.
     * @param path a path to write to.  If exists it will be overwritten.
     */
    public IntervalListWriter(final Path path) {
        this(path, null);
    }

    /** Creates a new writer, writing a header to the file.
     * @param path a file to write to.  If exists it will be overwritten.
     * @param header the header to write.
     */
    public IntervalListWriter(final Path path, final SAMFileHeader header) {
        out = IOUtil.openFileForBufferedWriting(path);

        // Write out the header
        if (header != null) {
            final SAMTextHeaderCodec codec = new SAMTextHeaderCodec();
            codec.encode(out, header);
        }
    }

    /** Writes a single interval list.
     * @param interval the interval to write.
     */
    public void write(final Interval interval) throws IOException {
        out.write(interval.getContig());
        out.write(TAB);
        out.write(Integer.toString(interval.getStart()));
        out.write(TAB);
        out.write(Integer.toString(interval.getEnd()));
        out.write(TAB);
        out.write(interval.isPositiveStrand() ? '+' : '-');
        out.write(TAB);
        out.write(interval.getName() != null ? interval.getName() : ".");
        out.newLine();
    }

    /** Closes the writer. */
    @Override
    public void close() throws IOException {
        out.flush();
        out.close();
    }
}
