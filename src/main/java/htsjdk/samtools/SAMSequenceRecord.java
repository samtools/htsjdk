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



import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import htsjdk.samtools.util.Locatable;

/**
 * Header information about a reference sequence.  Corresponds to @SQ header record in SAM text header.
 */

public class SAMSequenceRecord extends AbstractSAMHeaderRecord implements Cloneable, Locatable
{
    public static final long serialVersionUID = 1L; // AbstractSAMHeaderRecord implements Serializable
    public final static int UNAVAILABLE_SEQUENCE_INDEX = -1;
    private final String mSequenceName; // Value must be interned() if it's ever set/modified
    private int mSequenceIndex = UNAVAILABLE_SEQUENCE_INDEX;
    private int mSequenceLength = 0;
    public static final String SEQUENCE_NAME_TAG = "SN";
    public static final String SEQUENCE_LENGTH_TAG = "LN";
    public static final String MD5_TAG = "M5";
    public static final String ASSEMBLY_TAG = "AS";
    public static final String URI_TAG = "UR";
    public static final String SPECIES_TAG = "SP";
    public static final String DESCRIPTION_TAG = "DS";

    /** If one sequence has this length, and another sequence had a different length, isSameSequence will
     * not complain that they are different sequences. */
    public static final int UNKNOWN_SEQUENCE_LENGTH = 0;


    /**
     * This is not a valid sequence name, because it is reserved in the RNEXT field of SAM text format
     * to mean "same reference as RNAME field."
     */

    public static final String RESERVED_RNEXT_SEQUENCE_NAME = "=";

    /* use RESERVED_RNEXT_SEQUENCE_NAME instead. */
    @Deprecated
    public static final String RESERVED_MRNM_SEQUENCE_NAME = RESERVED_RNEXT_SEQUENCE_NAME;

    /**
     * The standard tags are stored in text header without type information, because the type of these tags is known.
     */
    public static final Set<String> STANDARD_TAGS =
            new HashSet<>(Arrays.asList(SEQUENCE_NAME_TAG, SEQUENCE_LENGTH_TAG, ASSEMBLY_TAG, MD5_TAG, URI_TAG, SPECIES_TAG));

    // These are the chars matched by \\s.
    private static final char[] WHITESPACE_CHARS = {' ', '\t', '\n', '\013', '\f', '\r'}; // \013 is vertical tab

    private static final Pattern LEGAL_RNAME_PATTERN = Pattern.compile("[0-9A-Za-z!#$%&+./:;?@^_|~-][0-9A-Za-z!#$%&*+./:;=?@^_|~-]*");

    /**
     * @deprecated Use {@link #SAMSequenceRecord(String, int)} instead.
     * sequenceLength is required for the object to be considered valid.
     */
    @Deprecated
    public SAMSequenceRecord(final String name) {
        this(name, UNKNOWN_SEQUENCE_LENGTH);
    }

    public SAMSequenceRecord(final String name, final int sequenceLength) {
        if (name != null) {
            validateSequenceName(name);
            mSequenceName = name.intern();
        } else {
            mSequenceName = null;
        }
        mSequenceLength = sequenceLength;
    }

    public String getSequenceName() { return mSequenceName; }

    public int getSequenceLength() { return mSequenceLength; }
    public void setSequenceLength(final int value) { mSequenceLength = value; }

    public String getAssembly() { return (String) getAttribute(ASSEMBLY_TAG); }
    public void setAssembly(final String value) { setAttribute(ASSEMBLY_TAG, value); }

    public String getSpecies() { return (String) getAttribute(SPECIES_TAG); }
    public void setSpecies(final String value) { setAttribute(SPECIES_TAG, value); }

    public String getMd5() { return (String) getAttribute(MD5_TAG); }
    public void setMd5(final String value) { setAttribute(MD5_TAG, value); }

    public String getDescription() { return getAttribute(DESCRIPTION_TAG);}
    public void setDescription(final String value) { setAttribute(DESCRIPTION_TAG, value);}

    /**
     * @return Index of this record in the sequence dictionary it lives in. 
     */
    public int getSequenceIndex() { return mSequenceIndex; }

    // Private state used only by SAM implementation.
    public void setSequenceIndex(final int value) { mSequenceIndex = value; }

    /**
     * Looser comparison than equals().  We look only at sequence index, sequence length, and MD5 tag value
     * (or sequence names, if there is no MD5 tag in either record.
     */
    public boolean isSameSequence(final SAMSequenceRecord that) {
        if (this == that) return true;
        if (that == null) return false;

        if (mSequenceIndex != that.mSequenceIndex) return false;
        // PIC-439.  Allow undefined length.
        if (mSequenceLength != UNKNOWN_SEQUENCE_LENGTH && that.mSequenceLength != UNKNOWN_SEQUENCE_LENGTH && mSequenceLength != that.mSequenceLength)
            return false;
        if (this.getAttribute(SAMSequenceRecord.MD5_TAG) != null && that.getAttribute(SAMSequenceRecord.MD5_TAG) != null) {
            final BigInteger thisMd5 = new BigInteger((String)this.getAttribute(SAMSequenceRecord.MD5_TAG), 16);
            final BigInteger thatMd5 = new BigInteger((String)that.getAttribute(SAMSequenceRecord.MD5_TAG), 16);
            if (!thisMd5.equals(thatMd5)) {
                return false;
            }
        }
        else {
            if (mSequenceName != that.mSequenceName) return false; // Compare using == since we intern() the Strings
        }

        return true;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof SAMSequenceRecord)) return false;

        final SAMSequenceRecord that = (SAMSequenceRecord) o;

        if (mSequenceIndex != that.mSequenceIndex) return false;
        if (mSequenceLength != that.mSequenceLength) return false;
        if (!attributesEqual(that)) return false;
        if (mSequenceName != that.mSequenceName) return false; // Compare using == since we intern() the name

        return true;
    }

    @Override
    public int hashCode() {
        return mSequenceName != null ? mSequenceName.hashCode() : 0;
    }

    @Override
    Set<String> getStandardTags() {
        return STANDARD_TAGS;
    }

    @Override
    public final SAMSequenceRecord clone() {
        final SAMSequenceRecord ret = new SAMSequenceRecord(this.mSequenceName, this.mSequenceLength);
        ret.mSequenceIndex = this.mSequenceIndex;
        for (final Map.Entry<String, String> entry : this.getAttributes()) {
            ret.setAttribute(entry.getKey(), entry.getValue());
        }
        return ret;
    }

    /**
     * Truncate sequence name at first whitespace.
     */
    public static String truncateSequenceName(final String sequenceName) {
        /*
         * Instead of using regex split, do it manually for better performance.
         */

        int truncateAt = sequenceName.length();
        for (final char c : WHITESPACE_CHARS) {
            int index = sequenceName.indexOf(c);
            if (index != UNAVAILABLE_SEQUENCE_INDEX && index < truncateAt) {
                truncateAt = index;
            }
        }
        return sequenceName.substring(0, truncateAt);
    }

    /**
     * Throw an exception if the sequence name is not valid.
     */
    public static void validateSequenceName(final String name) {
        if (!LEGAL_RNAME_PATTERN.matcher(name).useAnchoringBounds(true).matches()) {
            throw new SAMException(String.format("Sequence name '%s' doesn't match regex: '%s' ", name, LEGAL_RNAME_PATTERN));
        }
    }

    @Override
    public String toString() {
        return String.format(
                "SAMSequenceRecord(name=%s,length=%s,dict_index=%s,assembly=%s)", 
                getSequenceName(), 
                getSequenceLength(), 
                getSequenceIndex(),
                getAssembly()
        );
    }

    @Override
    public String getSAMString() {
        return new SAMTextHeaderCodec().getSQLine(this);
    }
    /** always returns <code>getSequenceName()</code> 
     * @see #getSequenceName()
     * */
    @Override
    public final String getContig() {
        return this.getSequenceName();
    }
    
    /** always returns 1 */
    @Override
    public final int getStart() {
        return 1;
    }
    
    /** always returns <code>getSequenceLength()</code> 
     * @see #getSequenceLength()
     * */
    @Override
    public final int getEnd() {
        return this.getSequenceLength();
    }
}

