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

import htsjdk.samtools.SAMRecord;

import java.util.Arrays;
import java.util.List;

/**
 * Filter class for matching tag attributes in SAMRecords
 *
 * $Id$
 */
public class TagFilter implements SamRecordFilter {

    private final String tag;           // The key of the tag to match
    private final List<Object> values;  // The list of matching values
    private boolean includeReads;

    /**
     * Constructor for a single value
     *
     * @param tag       the key of the tag to match
     * @param value     the value to match
     */
    public TagFilter(String tag, Object value, final boolean includeReads) {
        this.tag = tag;
        this.values = Arrays.asList(value);
        this.includeReads = includeReads;
    }

    /**
     * Constructor for multiple values
     *
     * @param tag       the key of the tag to match
     * @param values    the matching values
     */
    public TagFilter(String tag, List<Object> values, final boolean includeReads) {
        this.tag = tag;
        this.values = values;
        this.includeReads = includeReads;
    }

    /**
     * Determines whether a SAMRecord matches this filter
     *
     * @param record    the SAMRecord to evaluate
     * @return  true if the SAMRecord matches the filter, otherwise false
     */
    @Override
    public boolean filterOut(SAMRecord record) {
        if (includeReads) {
            if (values.contains(record.getAttribute(tag))) {
                return false;
            }
        } else {
            if (!values.contains(record.getAttribute(tag))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines whether a paired of SAMRecord matches this filter
     *
     * @param first  the first SAMRecord to evaluate
     * @param second the second SAMRecord to evaluate
     *
     * @return true if the SAMRecords matches the filter, otherwise false
     */
    @Override
    public boolean filterOut(final SAMRecord first, final SAMRecord second) {
        // both first and second must have the tag in order for it to be filtered out
        if (includeReads) {
            if (values.contains(first.getAttribute(tag)) && values.contains(second.getAttribute(tag))) {
                return false;
            }
        } else {
            if (!values.contains(first.getAttribute(tag)) && values.contains(second.getAttribute(tag))) {
                return false;
            }
        }
        return true;
    }
}
