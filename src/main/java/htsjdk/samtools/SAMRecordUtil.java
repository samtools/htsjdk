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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author alecw@broadinstitute.org
 *
 * @deprecated 10/27/2016 Use {@link SAMRecord} constants and functions
 */
@Deprecated
public class SAMRecordUtil {
    /**
     * @deprecated 6/5/2017 Use {@link SAMRecord#TAGS_TO_REVERSE_COMPLEMENT}
     */
    @Deprecated
    public static List<String> TAGS_TO_REVERSE_COMPLEMENT = Arrays.asList(SAMTag.E2.name(), SAMTag.SQ.name());

    /**
     * @deprecated 6/5/2017 Use {@link SAMRecord#TAGS_TO_REVERSE}
     */
    @Deprecated
    public static List<String> TAGS_TO_REVERSE            = Arrays.asList(SAMTag.OQ.name(), SAMTag.U2.name());

    /**
     * Reverse-complement bases and reverse quality scores along with known optional attributes that
     * need the same treatment. Changes made in-place, instead of making a copy of the bases, qualities,
     * or attributes. If a copy is needed use {@link #reverseComplement(SAMRecord, boolean)}.
     * See {@link #TAGS_TO_REVERSE_COMPLEMENT} {@link #TAGS_TO_REVERSE}
     * for the default set of tags that are handled.
     *
     * @deprecated  6/5/2017 Use {@link SAMRecord#reverseComplement} but note that the default behavior there is different
     * It will default to making a copy, not reverse-complementing in-place!
     */
    @Deprecated
    public static void reverseComplement(final SAMRecord rec) {
        rec.reverseComplement(TAGS_TO_REVERSE_COMPLEMENT, TAGS_TO_REVERSE, true);
    }

    /**
     * Reverse-complement bases and reverse quality scores along with known optional attributes that
     * need the same treatment. Optionally makes a copy of the bases, qualities or attributes instead
     * of altering them in-place. See {@link #TAGS_TO_REVERSE_COMPLEMENT} {@link #TAGS_TO_REVERSE}
     * for the default set of tags that are handled.
     *
     * @param rec Record to reverse complement.
     * @param inplace Setting this to false will clone all attributes, bases and qualities before changing the values.
     *
     * @deprecated  6/5/2017 Use {@link SAMRecord#reverseComplement}
     */
    @Deprecated
    public static void reverseComplement(final SAMRecord rec, boolean inplace) {
        rec.reverseComplement(TAGS_TO_REVERSE_COMPLEMENT, TAGS_TO_REVERSE, inplace);
    }

    /**
     * Reverse complement bases and reverse quality scores. In addition reverse complement any
     * non-null attributes specified by tagsToRevcomp and reverse and non-null attributes
     * specified by tagsToReverse.
     *
     * @deprecated  6/5/2017 Use {@link SAMRecord#reverseComplement}
     */
    @Deprecated
    public static void reverseComplement(final SAMRecord rec, final Collection<String> tagsToRevcomp, final Collection<String> tagsToReverse, boolean inplace) {
        rec.reverseComplement(tagsToRevcomp, tagsToReverse, inplace);
    }
}
