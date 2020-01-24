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

/* Disjoint categorization of cigar-operators in coarser classes. */
enum CigarOperatorClass {
    ALIGNMENT, CLIPPING, INDEL, SKIP, PADDING
}

/**
 * The operators that can appear in a cigar string, and information about their disk representations.
 */
public enum CigarOperator {

    /** Match or mismatch */
    M(true, true, "M", 0, CigarOperatorClass.ALIGNMENT),
    /** Insertion vs. the reference. */
    I(true, false, "I", 1, CigarOperatorClass.INDEL),
    /** Deletion vs. the reference. */
    D(false, true, "D", 2, CigarOperatorClass.INDEL),
    /** Skipped region from the reference. */
    N(false, true, "N", 3, CigarOperatorClass.SKIP),
    /** Soft clip. */
    S(true, false, "S", 4, CigarOperatorClass.CLIPPING),
    /** Hard clip. */
    H(false, false, "H", 5, CigarOperatorClass.CLIPPING),
    /** Padding. */
    P(false, false, "P", 6, CigarOperatorClass.PADDING),
    /** Matches the reference. */
    EQ(true, true,  "=", 7, CigarOperatorClass.ALIGNMENT),
    /** Mismatches the reference. */
    X(true, true, "X", 8, CigarOperatorClass.ALIGNMENT)
    ;

    private final boolean consumesReadBases;
    private final boolean consumesReferenceBases;
    private final char character;
    private final String string;
    private final int bamEncoding;
    private final boolean isAlignment;
    private final boolean isClipping;
    private final boolean isIndel;
    private final boolean isIndelOrSkip;
    private final boolean isPadding;
    private final boolean isSkip;

    private static final CigarOperator[] bamEncoding2Value;

    static {
        final CigarOperator[] values = CigarOperator.values();
        bamEncoding2Value = new CigarOperator[values.length];
        for (final CigarOperator op : values) {
            final int code = op.getBamEncoding();
            if (bamEncoding2Value[code] != null) {
                throw new ExceptionInInitializerError("BAM code collision between: " + op + " " + bamEncoding2Value[code]);
            }
            bamEncoding2Value[op.getBamEncoding()] = op;
        }
    }

    private static final CigarOperator[] byte2Value;

    static {
        byte2Value = new CigarOperator[Byte.MAX_VALUE];
        for (final CigarOperator op : values()) {
            final int index = op.character & 0x7F;
            if (byte2Value[index] != null) {
                throw new ExceptionInInitializerError("BAM code collision between: " + op + " " + byte2Value[index]);
            }
            byte2Value[index] = op;
        }
    }

    // Readable synonyms of the above enums
    public static final CigarOperator MATCH_OR_MISMATCH = M;
    public static final CigarOperator INSERTION = I;
    public static final CigarOperator DELETION = D;
    public static final CigarOperator SKIPPED_REGION = N;
    public static final CigarOperator SOFT_CLIP = S;
    public static final CigarOperator HARD_CLIP = H;
    public static final CigarOperator PADDING = P;

    /** Default constructor. */
    CigarOperator(boolean consumesReadBases, boolean consumesReferenceBases, final String string, final int bamCode,
                  final CigarOperatorClass clazz) {
        this.consumesReadBases = consumesReadBases;
        this.consumesReferenceBases = consumesReferenceBases;
        this.string = string == null ? name() : string; // since we will provide a constant it is internalized already.
        this.character = this.string.charAt(0);
        this.bamEncoding = bamCode;
        this.isAlignment = clazz == CigarOperatorClass.ALIGNMENT;
        this.isIndel = clazz == CigarOperatorClass.INDEL;
        this.isClipping = clazz == CigarOperatorClass.CLIPPING;
        this.isSkip = clazz == CigarOperatorClass.SKIP;
        this.isIndelOrSkip = isIndel || isSkip;
        this.isPadding = clazz == CigarOperatorClass.PADDING;
    }

    /** If true, represents that this cigar operator "consumes" bases from the read bases. */
    public boolean consumesReadBases() { return consumesReadBases; }

    /** If true, represents that this cigar operator "consumes" bases from the reference sequence. */
    public boolean consumesReferenceBases() { return consumesReferenceBases; }

    /**
     * @param b CIGAR operator in character form as appears in a text CIGAR string
     * @return CigarOperator enum value corresponding to the given character.
     * @deprecated use {@link #fromChar(int)} instead.
     */
    @Deprecated
    public static CigarOperator characterToEnum(final int b) {
        return fromChar(b);
    }

    public static CigarOperator fromChar(final int ch) {
       if ((ch & ~0x7F) == 0) {
           final CigarOperator result = byte2Value[ch & 0x7F];
           if (result == null) {
               throw new IllegalArgumentException("Unrecognized CigarOperator: " + ch);
           }
           return result;
       } else {
           throw new IllegalArgumentException("Unrecognized CigarOperator: " + ch);
       }
    }

    /**
     * @param i CIGAR operator in binary form as appears in a BAMRecord.
     * @return CigarOperator enum value corresponding to the given int value.
     * @deprecated use {@link #fromBamEncoding(int)}.
     */
    @Deprecated
    public static CigarOperator binaryToEnum(final int i) {
        return fromBamEncoding(i);
    }

    /**
     * Looks-up the operator given its BAM code.
     * <p>
     *     It is guaranteed that {@code CigarOperator.fromBamEncoding(op.bamCode()) == op}.
     * </p>
     *
     * @param bamCode the query BAM code.
     * @return never {@code null}.
     * @throws IllegalArgumentException if the input BAM code is not valid.
     * @see #bamEncoding
     */
    public static CigarOperator fromBamEncoding(final int bamCode) {
        if (bamCode >= 0 && bamCode < bamEncoding2Value.length) {
            return bamEncoding2Value[bamCode];
        } else {
            throw new IllegalArgumentException("Unrecognized CigarOperator: " + bamCode);
        }
    }

    /**
     *
     * @param e CigarOperator enum value.
     * @return CIGAR operator corresponding to the enum value in binary form as appears in a BAMRecord.
     * @deprecated use {@link #getBamEncoding()} instead.
     */
    @Deprecated
    public static int enumToBinary(final CigarOperator e) {
        return e.bamEncoding;
    }

    /**
     * Returns the binary representation of this operator as it appears in the BAMs.
     * @return a unique value between 0 and 8.
     */
    public int getBamEncoding() {
        return bamEncoding;
    }

    /** Returns the character that should be used within a SAM file.
     *
     * @deprecated use {@link #asByte()} or {@link #asChar()}
     */
    @Deprecated
    public static byte enumToCharacter(final CigarOperator e) {
        return (byte) e.character;
    }

    /**
     * Returns the operator in its single byte representation.
     * @return same as the first character (cast to byte) of the string returned by {@link #toString()}.
     */
    public byte asByte() {
        return (byte) character;
    }

    /**
     * Returns the operator in its single byte representation.
     * @return same as the first character of the string returned by {@link #toString()}.
     */
    public char asChar() {
        return character;
    }

    /** Returns true if the operator is a clipped (hard or soft) operator */
    public boolean isClipping() {
        return isClipping;
    }

    /** Returns true if the operator is a Insertion or Deletion operator */
    public boolean isIndel() {
        return isIndel;
    }

    public boolean isSkip() {
        return isSkip;
    }

    /** Returns true if the operator is a Skipped Region Insertion or Deletion operator */
    public boolean isIndelOrSkippedRegion() {
        return isIndelOrSkip;
    }

    /** Returns true if the operator is a M, a X or a EQ */
    public boolean isAlignment() {
        return isAlignment;
    }
    
    /** Returns true if the operator is a Padding operator */
    public boolean isPadding() {
        return isPadding;
    }
    
    /** Returns the cigar operator as it would be seen in a SAM file. */
    @Override
    public String toString() {
        return this.string;
    }
}
