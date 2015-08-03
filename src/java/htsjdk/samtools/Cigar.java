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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A list of CigarElements, which describes how a read aligns with the reference.
 * E.g. the Cigar string 10M1D25M means
 * * match or mismatch for 10 bases
 * * deletion of 1 base
 * * match or mismatch for 25 bases
 *
 * c.f. http://samtools.sourceforge.net/SAM1.pdf for complete CIGAR specification.
 */
public class Cigar implements Serializable {
    public static final long serialVersionUID = 1L;

    private final List<CigarElement> cigarElements = new ArrayList<CigarElement>();

    public Cigar() {
    }

    public Cigar(final List<CigarElement> cigarElements) {
        this.cigarElements.addAll(cigarElements);
    }

    public List<CigarElement> getCigarElements() {
        return Collections.unmodifiableList(cigarElements);
    }

    public CigarElement getCigarElement(final int i) {
        return cigarElements.get(i);
    }

    public void add(final CigarElement cigarElement) {
        cigarElements.add(cigarElement);
    }

    public int numCigarElements() {
        return cigarElements.size();
    }

    public boolean isEmpty() {
        return cigarElements.isEmpty();
    }

    /**
     * @return The number of reference bases that the read covers, excluding padding.
     */
    public int getReferenceLength() {
        int length = 0;
        for (final CigarElement element : cigarElements) {
            switch (element.getOperator()) {
                case M:
                case D:
                case N:
                case EQ:
                case X:
                    length += element.getLength();
            }
        }
        return length;
    }

    /**
     * @return The number of reference bases that the read covers, including padding.
     */
    public int getPaddedReferenceLength() {
        int length = 0;
        for (final CigarElement element : cigarElements) {
            switch (element.getOperator()) {
                case M:
                case D:
                case N:
                case EQ:
                case X:
                case P:
                    length += element.getLength();
            }
        }
        return length;
    }

    /**
     * @return The number of read bases that the read covers.
     */
    public int getReadLength() {
        return getReadLength(cigarElements);
    }

    /**
     * @return The number of read bases that the read covers.
     */
    public static int getReadLength(final List<CigarElement> cigarElements) {
        int length = 0;
        for (final CigarElement element : cigarElements) {
            if (element.getOperator().consumesReadBases()){
                    length += element.getLength();
            }
        }
        return length;
    }

    /**
     * Exhaustive validation of CIGAR.
     * Note that this method deliberately returns null rather than Collections.emptyList() if there
     * are no validation errors, because callers tend to assume that if a non-null list is returned, it is modifiable.
     * @param readName For error reporting only.  May be null if not known.
     * @param recordNumber For error reporting only.  May be -1 if not known.
     * @return List of validation errors, or null if no errors.
     */
    public List<SAMValidationError> isValid(final String readName, final long recordNumber) {
        if (this.isEmpty()) {
            return null;
        }
        List<SAMValidationError> ret = null;
        boolean seenRealOperator = false;
        for (int i = 0; i < cigarElements.size(); ++i) {
            final CigarElement element = cigarElements.get(i);
            if (element.getLength() == 0) {
                if (ret == null) ret = new ArrayList<SAMValidationError>();
                ret.add(new SAMValidationError(SAMValidationError.Type.INVALID_CIGAR,
                        "CIGAR element with zero length", readName, recordNumber));
            }
            // clipping operator can only be at start or end of CIGAR
            final CigarOperator op = element.getOperator();
            if (isClippingOperator(op)) {
                if (op == CigarOperator.H) {
                    if (i != 0 && i != cigarElements.size() - 1) {
                        if (ret == null) ret = new ArrayList<SAMValidationError>();
                        ret.add(new SAMValidationError(SAMValidationError.Type.INVALID_CIGAR,
                                "Hard clipping operator not at start or end of CIGAR", readName, recordNumber));
                    }
                } else {
                    if (op != CigarOperator.S) throw new IllegalStateException("Should never happen: " + op.name());
                    if (i == 0 || i == cigarElements.size() - 1) {
                        // Soft clip at either end is fine
                    } else if (i == 1) {
                        if (cigarElements.size() == 3 && cigarElements.get(2).getOperator() == CigarOperator.H) {
                            // Handle funky special case in which S operator is both one from the beginning and one
                            // from the end.
                        } else if (cigarElements.get(0).getOperator() != CigarOperator.H) {
                            if (ret == null) ret = new ArrayList<SAMValidationError>();
                            ret.add(new SAMValidationError(SAMValidationError.Type.INVALID_CIGAR,
                                "Soft clipping CIGAR operator can only be inside of hard clipping operator",
                                    readName, recordNumber));
                        }
                    } else if (i == cigarElements.size() - 2) {
                        if (cigarElements.get(cigarElements.size() - 1).getOperator() != CigarOperator.H) {
                            if (ret == null) ret = new ArrayList<SAMValidationError>();
                            ret.add(new SAMValidationError(SAMValidationError.Type.INVALID_CIGAR,
                                "Soft clipping CIGAR operator can only be inside of hard clipping operator",
                                    readName, recordNumber));
                        }
                    } else {
                        if (ret == null) ret = new ArrayList<SAMValidationError>();
                        ret.add(new SAMValidationError(SAMValidationError.Type.INVALID_CIGAR,
                            "Soft clipping CIGAR operator can at start or end of read, or be inside of hard clipping operator",
                                readName, recordNumber));
                    }

                }
            } else if (isRealOperator(op)) {
                // Must be at least one real operator (MIDN)
                seenRealOperator = true;
                // There should be an M or P operator between any pair of IDN operators
                if (isInDelOperator(op)) {
                    for (int j = i+1; j < cigarElements.size(); ++j) {
                        final CigarOperator nextOperator = cigarElements.get(j).getOperator();
                        // Allow
                        if ((isRealOperator(nextOperator) && !isInDelOperator(nextOperator)) || isPaddingOperator(nextOperator)) {
                            break;
                        }
                        if (isInDelOperator(nextOperator) && op == nextOperator) {
                            if (ret == null) ret = new ArrayList<SAMValidationError>();
                            ret.add(new SAMValidationError(SAMValidationError.Type.ADJACENT_INDEL_IN_CIGAR,
                                    "No M or N operator between pair of " + op.name() + " operators in CIGAR", readName, recordNumber));
                        }
                    }
                }
            } else if (isPaddingOperator(op)) {
                if (i == 0) {
                    /*
                     * Removed restriction that padding not be the first operator because if a read starts in the middle of a pad
                     * in a padded reference, it is necessary to precede the read with padding so that alignment start refers to a
                     * position on the unpadded reference.
                    */
                } else if (i == cigarElements.size() - 1) {
                    if (ret == null) ret = new ArrayList<SAMValidationError>();
                    ret.add(new SAMValidationError(SAMValidationError.Type.INVALID_CIGAR,
                            "Padding operator not valid at end of CIGAR", readName, recordNumber));
                } else if (!isRealOperator(cigarElements.get(i-1).getOperator()) ||
                        !isRealOperator(cigarElements.get(i+1).getOperator())) {
                    if (ret == null) ret = new ArrayList<SAMValidationError>();
                    ret.add(new SAMValidationError(SAMValidationError.Type.INVALID_CIGAR,
                            "Padding operator not between real operators in CIGAR", readName, recordNumber));
                }
            }
        }
        if (!seenRealOperator) {
            if (ret == null) ret = new ArrayList<SAMValidationError>();
            ret.add(new SAMValidationError(SAMValidationError.Type.INVALID_CIGAR,
                    "No real operator (M|I|D|N) in CIGAR", readName, recordNumber));
        }
        return ret;
    }

    private static boolean isRealOperator(final CigarOperator op) {
        return op == CigarOperator.M || op == CigarOperator.EQ || op == CigarOperator.X || 
               op == CigarOperator.I || op == CigarOperator.D || op == CigarOperator.N;
    }

    private static boolean isInDelOperator(final CigarOperator op) {
        return op == CigarOperator.I || op == CigarOperator.D;
    }

    private static boolean isClippingOperator(final CigarOperator op) {
        return op == CigarOperator.S || op == CigarOperator.H;
    }

    private static boolean isPaddingOperator(final CigarOperator op) {
        return op == CigarOperator.P;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof Cigar)) return false;

        final Cigar cigar = (Cigar) o;

        if (cigarElements != null ? !cigarElements.equals(cigar.cigarElements) : cigar.cigarElements != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return cigarElements != null ? cigarElements.hashCode() : 0;
    }

    public String toString() {
        return TextCigarCodec.encode(this);
    }
}
