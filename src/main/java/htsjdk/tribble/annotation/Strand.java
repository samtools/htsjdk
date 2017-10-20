/*
 * The MIT License
 *
 * Copyright (c) 2013 The Broad Institute
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
package htsjdk.tribble.annotation;

/**
 * Enum for strand, which can be encoded as a string
 */
public enum Strand {

    /**
     * Represents the positive or forward strand.
     */
    POSITIVE('+'),

    /**
     * Represents the negative or reverse strand.
     */
    NEGATIVE('-'),

    /**
     * Denotes that a strand designation is not applicable
     * or is unknown.
     */
    NONE('.');  // not really sure what we should do for the NONE Enum

    /**
     * Common alias for the {@link #POSITIVE} strand.
     */
    public static final Strand FORWARD = POSITIVE;

    /**
     * Common alias for the {@link #NEGATIVE} strand.
     */
    public static final Strand REVERSE = NEGATIVE;

    /**
     * Cached array of instances.
     */
    private final static Strand[] VALUES = values();

    /**
     * How we represent the strand as a single {@code char}.
     */
    private final char charEncoding;

    /**
     * How we represent the strand as a {@link String}.
     */
    private final String stringEncoding;

    Strand(final char ch) {
        charEncoding = ch;
        stringEncoding = String.valueOf(charEncoding);
    }

    /**
     * provide a way to take an encoding string, and produce a Strand
     * @param encoding the encoding string
     * @return a Strand object, if an appropriate one cannot be located an IllegalArg exception
     * @deprecated please use {@link #decode} instead.
     */
    @Deprecated
    public static Strand toStrand(final String encoding) {
        return decode(encoding);
    }

    /**
     * Returns the {@link Strand} that a {@code char} value represents.
     * @param ch the char encoding for a Strand.
     * @return never {@code null}, a value so that {@code decode(c).encodeAsChar() == c} or {@link #NONE} if
     *   the encoding char is not recognized.
     */
    public static Strand decode(final char ch) {
        for(final Strand value : VALUES) {
            if (value.charEncoding == ch) {
                return value;
            }
        }
        return NONE;
    }

    /**
     * Returns the {@link Strand} that a {@link String} encodes for.
     * @param encoding the strand string representation.
     * @return never {@code null}, a value so that {@code decode(s).encode().equals(s)}, or {@link #NONE} if
     * the encoding string is not recognized.
     */
    public static Strand decode(final String encoding) {
        if (encoding != null && encoding.length() == 1) {
            return decode(encoding.charAt(0));
        }
        return NONE;
    }

    /**
     * Returns a string representation of this {@link Strand}
     * @return never {@code null}, a value so that {@code decode(encode(X)) == X}.
     */
    public String encode() {
        return stringEncoding;
    }

    /**
     * Returns a single char encoding of this {@link Strand}.
     * @return a value so that {@code decode(encodeAsChar(X)) == X}.
     */
    public char encodeAsChar() {
        return charEncoding;
    }

    @Override
    public String toString() {
        return stringEncoding;
    }
}