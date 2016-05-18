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
package htsjdk.samtools.filter;

import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.util.IOUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Filter by a set of specified readnames
 * <p/>
 * $Id$
 */
public class ReadNameFilter implements SamRecordFilter {

    private boolean includeReads = false;
    private Set<String> readNameFilterSet = new HashSet<String>();

    public ReadNameFilter(final File readNameFilterFile, final boolean includeReads) {

        IOUtil.assertFileIsReadable(readNameFilterFile);
        IOUtil.assertFileSizeNonZero(readNameFilterFile);

        try {
            final BufferedReader in = IOUtil.openFileForBufferedReading(readNameFilterFile);

            String line = null;

            while ((line = in.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    readNameFilterSet.add(line.split("\\s+")[0]);
                }
            }

            in.close();
        } catch (IOException e) {
            throw new SAMException(e.getMessage(), e);
        }

        this.includeReads = includeReads;
    }

    public ReadNameFilter(final Set<String> readNameFilterSet, final boolean includeReads) {
        this.readNameFilterSet = readNameFilterSet;
        this.includeReads = includeReads;
    }

    /**
     * Determines whether a SAMRecord matches this filter
     *
     * @param record the SAMRecord to evaluate
     *
     * @return true if the SAMRecord matches the filter, otherwise false
     */
    public boolean filterOut(final SAMRecord record) {
        if (includeReads) {
            if (readNameFilterSet.contains(record.getReadName())) {
                return false;
            }
        } else {
            if (!readNameFilterSet.contains(record.getReadName())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Determines whether a pair of SAMRecords matches this filter
     *
     * @param first  the first SAMRecord to evaluate
     * @param second the second SAMRecord to evaluate
     *
     * @return true if the pair of records matches filter, otherwise false
     */
    public boolean filterOut(final SAMRecord first, final SAMRecord second) {
        if (includeReads) {
            if (readNameFilterSet.contains(first.getReadName()) &&
                readNameFilterSet.contains(second.getReadName())) {
                return false;
            }
        } else {
            if (!readNameFilterSet.contains(first.getReadName()) &&
                !readNameFilterSet.contains(second.getReadName())) {
                return false;
            }
        }

        return true;
    }

}