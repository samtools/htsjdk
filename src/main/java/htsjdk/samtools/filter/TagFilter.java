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

import java.util.Collections;
import java.util.List;

/**
 * Filter class for matching tag attributes in SAMRecords
 *
 * $Id$
 */
public class TagFilter implements SamRecordFilter {

    private final String tag;           // The key of the tag to match
    private final List<Object> values;  // The list of matching values
    private Boolean includeReads;

    /**
     * Constructor for a single value
     *
     * @param tag       the key of the tag to match
     * @param value     the value to match
     */
    public TagFilter(String tag, Object value) {
        this(tag, Collections.singletonList(value), null);
    }

    /**
     * Constructor for multiple values
     *
     * @param tag       the key of the tag to match
     * @param values    the matching values
     */
    public TagFilter(String tag, List<Object> values) {
        this(tag, values, null);
    }

    /**
     * Constructor for a single value
     *
     * @param tag           the key of the tag to match
     * @param value         the value to match
     * @param includeReads  whether to include or not include reads that match filter
     */
    public TagFilter(String tag, Object value, final Boolean includeReads) {
        this(tag, Collections.singletonList(value), includeReads);
    }

    /**
     * Constructor for multiple values
     *
     * @param tag           the key of the tag to match
     * @param values        the matching values
     * @param includeReads  whether to include or not include reads that match filter
     */
    public TagFilter(String tag, List<Object> values, final Boolean includeReads) {
        this.tag = tag;
        this.values = values;
        this.includeReads = includeReads == null ? false : includeReads;
    }

    /**
     * Determines whether a SAMRecord matches this filter
     *
     * @param record    the SAMRecord to evaluate
     * @return  the XOR of SAMRecord matches the filter and includeReads.
     */
    @Override
    public boolean filterOut(SAMRecord record) {
        return values.contains(record.getAttribute(tag)) != includeReads;
    }

    /**
     * Determines whether a paired of SAMRecord matches this filter
     *
     * @param first  the first SAMRecord to evaluate
     * @param second the second SAMRecord to evaluate
     *
     * @return  true if includeReads is true and neither SAMRecord matches filter
     *          true if includeReads is false and both SAMRecords match filter
     *          otherwise false
     */
    @Override
    public boolean filterOut(final SAMRecord first, final SAMRecord second) {
        // With includeReads==true, allow any pairs through that contain the tag value
        // With includeReads==false, exclude pairs where both reads contain the tag value
        if (includeReads) {
            return !(values.contains(first.getAttribute(tag)) || values.contains(second.getAttribute(tag)));
        } else {
            return values.contains(first.getAttribute(tag)) && values.contains(second.getAttribute(tag));
        }
    }
}
