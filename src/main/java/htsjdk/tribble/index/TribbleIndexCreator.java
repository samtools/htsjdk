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
package htsjdk.tribble.index;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;

import java.util.LinkedHashMap;

/**
 * Base class for Tribble-specific index creators.
 */
public abstract class TribbleIndexCreator implements IndexCreator {
    // a constant we use for marking sequence dictionary entries in the Tribble index property list
    private static final String SEQUENCE_DICTIONARY_PROPERTY_PREDICATE = "DICT:";

    protected LinkedHashMap<String, String> properties = new LinkedHashMap<String, String>();

    public void addProperty(final String key, final String value) {
        properties.put(key, value);
    }

    /** Set the sequence dictionary entries for the index property list. */
    @Override
    public void setIndexSequenceDictionary(final SAMSequenceDictionary dict) {
        for (final SAMSequenceRecord seq : dict.getSequences()) {
            final String contig = SEQUENCE_DICTIONARY_PROPERTY_PREDICATE + seq.getSequenceName();
            final String length = String.valueOf(seq.getSequenceLength());
            addProperty(contig,length);
        }
    }
}
