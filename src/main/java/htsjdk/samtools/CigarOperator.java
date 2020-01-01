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

/**
 * The operators that can appear in a cigar string, and information about their disk representations.
 */
public enum CigarOperator {
    /** Match or mismatch */
    M(true, true),
    /** Insertion vs. the reference. */
    I(true, false),
    /** Deletion vs. the reference. */
    D(false, true),
    /** Skipped region from the reference. */
    N(false, true),
    /** Soft clip. */
    S(true, false),
    /** Hard clip. */
    H(false, false),
    /** Padding. */
    P(false, false),
    /** Matches the reference. */
    EQ(true, true,  "="),
    /** Mismatches the reference. */
    X(true, true)
    ;

    private final boolean consumesReadBases;
    private final boolean consumesReferenceBases;
    private final char character;
    private final String string;
    private final int mask;

    private static final int INDEL_MASK = I.mask | D.mask;
    private static final int INDEL_OR_SKIP_MASK = INDEL_MASK | N.mask;
    private static final int ALIGNMENT_MASK = M.mask | X.mask | EQ.mask;
    private static final int CLIP_MASK = S.mask | H.mask;

    private static final CigarOperator[] bamCode2Value;

    static {
        final CigarOperator[] values = CigarOperator.values();
        bamCode2Value = new CigarOperator[values.length];
        for (final CigarOperator op : values) {
            final int code = op.bamCode();
            if (bamCode2Value[code] != null) {
                throw new ExceptionInInitializerError("BAM code collision between: " + op + " " + bamCode2Value[code]);
            }
            bamCode2Value[op.bamCode()] = op;
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


    CigarOperator(final boolean consumesReadBases, final boolean consumesReferenceBases) {
        this(consumesReadBases, consumesReferenceBases, null);
    }

    /** Default constructor. */
    CigarOperator(boolean consumesReadBases, boolean consumesReferenceBases, final String string) {
        this.consumesReadBases = consumesReadBases;
        this.consumesReferenceBases = consumesReferenceBases;
        this.string = string == null ? name() : string; // since we will provide a constant it is internalized already.
        this.character = this.string.charAt(0);
        this.mask = 1 << ordinal();
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
     * @deprecated use {@link #fromBamCode(int)}.
     */
    @Deprecated
    public static CigarOperator binaryToEnum(final int i) {
        return fromBamCode(i);
    }

    /**
     * Looks-up the operator given its BAM code.
     * <p>
     *     It is guaranteed that {@code CigarOperator.fromBamCode(op.bamCode()) == op}.
     * </p>
     *
     * @param bamCode the query BAM code.
     * @return never {@code null}.
     * @throws IllegalArgumentException if the input BAM code is not valid.
     * @see #bamCode
     */
    public static CigarOperator fromBamCode(final int bamCode) {
        if (bamCode >= 0 && bamCode < bamCode2Value.length) {
            return bamCode2Value[bamCode];
        } else {
            throw new IllegalArgumentException("Unrecognized CigarOperator: " + bamCode);
        }
    }

    /**
     *
     * @param e CigarOperator enum value.
     * @return CIGAR operator corresponding to the enum value in binary form as appears in a BAMRecord.
     * @deprecated use {@link #bamCode()} instead.
     */
    @Deprecated
    public static int enumToBinary(final CigarOperator e) {
        return e.ordinal();
    }

    /**
     * Returns the binary representation of this operator as it appears in the BAMs.
     * @return a unique value between 0 and 8.
     */
    public int bamCode() {
        return ordinal();
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
        return (CLIP_MASK & mask) != 0;
    }

    /** Returns true if the operator is a Insertion or Deletion operator */
    public boolean isIndel() {
        return (INDEL_MASK & mask) != 0;
    }

    /** Returns true if the operator is a Skipped Region Insertion or Deletion operator */
    public boolean isIndelOrSkippedRegion() {
        return (mask & INDEL_OR_SKIP_MASK) != 0;
    }

    /** Returns true if the operator is a M, a X or a EQ */
    public boolean isAlignment() {
        return (mask & ALIGNMENT_MASK) != 0;
    }
    
    /** Returns true if the operator is a Padding operator */
    public boolean isPadding() {
        return this == P;
    }
    
    /** Returns the cigar operator as it would be seen in a SAM file. */
    @Override
    public String toString() {
        return this.string;
    }
}
