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

import htsjdk.samtools.util.SequenceUtil;
import htsjdk.samtools.util.StringUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author alecw@broadinstitute.org
 */
public class SAMRecordUtil {
    public static List<String> TAGS_TO_REVERSE_COMPLEMENT = Arrays.asList(SAMTag.E2.name(), SAMTag.SQ.name());
    public static List<String> TAGS_TO_REVERSE            = Arrays.asList(SAMTag.OQ.name(), SAMTag.U2.name());

    /**
     * Reverse-complement bases and reverse quality scores along with known optional attributes that
     * need the same treatment.  See {@link #TAGS_TO_REVERSE_COMPLEMENT} {@link #TAGS_TO_REVERSE}
     * for the default set of tags that are handled.
     */
    public static void reverseComplement(final SAMRecord rec) {
        reverseComplement(rec, TAGS_TO_REVERSE_COMPLEMENT, TAGS_TO_REVERSE);
    }

    /**
     * Reverse complement bases and reverse quality scores. In addition reverse complement any
     * non-null attributes specified by tagsToRevcomp and reverse and non-null attributes
     * specified by tagsToReverse.
     */
    public static void reverseComplement(final SAMRecord rec, final Collection<String> tagsToRevcomp, final Collection<String> tagsToReverse) {
        final byte[] readBases = rec.getReadBases();
        SequenceUtil.reverseComplement(readBases);
        rec.setReadBases(readBases);
        final byte qualities[] = rec.getBaseQualities();
        reverseArray(qualities);
        rec.setBaseQualities(qualities);

        // Deal with tags that need to be reverse complemented
        if (tagsToRevcomp != null) {
            for (final String tag: tagsToRevcomp) {
                Object value = rec.getAttribute(tag);
                if (value != null) {
                    if (value instanceof byte[]) SequenceUtil.reverseComplement((byte[]) value);
                    else if (value instanceof String) value = SequenceUtil.reverseComplement((String) value);
                    else throw new UnsupportedOperationException("Don't know how to reverse complement: " + value);
                    rec.setAttribute(tag, value);
                }
            }
        }

        // Deal with tags that needed to just be reversed
        if (tagsToReverse != null) {
            for (final String tag : tagsToReverse) {
                Object value = rec.getAttribute(tag);
                if (value != null) {
                    if (value instanceof String) {
                        value = StringUtil.reverseString((String) value);
                    }
                    else if (value.getClass().isArray()) {
                        if (value instanceof byte[]) reverseArray((byte[]) value);
                        else if (value instanceof short[]) reverseArray((short[]) value);
                        else if (value instanceof int[]) reverseArray((int[]) value);
                        else if (value instanceof float[]) reverseArray((float[]) value);
                        else throw new UnsupportedOperationException("Reversing array attribute of type " + value.getClass().getComponentType() + " not supported.");
                    }
                    else throw new UnsupportedOperationException("Don't know how to reverse: " + value);

                    rec.setAttribute(tag, value);
                }
            }
        }
    }

    private static void reverseArray(final byte[] array) {
        for (int i=0, j=array.length-1; i<j; ++i, --j) {
            final byte tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
    }

    private static void reverseArray(final short[] array) {
        for (int i=0, j=array.length-1; i<j; ++i, --j) {
            final short tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
    }

    private static void reverseArray(final int[] array) {
        for (int i=0, j=array.length-1; i<j; ++i, --j) {
            final int tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
    }

    private static void reverseArray(final float[] array) {
        for (int i=0, j=array.length-1; i<j; ++i, --j) {
            final float tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
    }
}
