/*
* Copyright (c) 2012 The Broad Institute
* 
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
* 
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package htsjdk.variant.variantcontext;

import htsjdk.samtools.util.StringUtil;

import java.util.Arrays;

class ByteArrayAllele implements Allele {

    private static long serialVersionUID = 1L;

    private static final byte[] EMPTY_ALLELE_BASES = new byte[0];

    private boolean isRef = false;
    private boolean isNoCall = false;
    private boolean isSymbolic = false;

    private byte[] bases = null;

    // no public way to create an allele
    protected ByteArrayAllele(final byte[] bases, final boolean isRef) {
        // null alleles are no longer allowed
        if ( Allele.wouldBeNullAllele(bases) ) {
            throw new IllegalArgumentException("Null alleles are not supported");
        }

        // no-calls are represented as no bases
        if ( Allele.wouldBeNoCallAllele(bases) ) {
            this.bases = EMPTY_ALLELE_BASES;
            isNoCall = true;
            if ( isRef ) throw new IllegalArgumentException("Cannot tag a NoCall allele as the reference allele");
            return;
        }

        if ( Allele.wouldBeSymbolicAllele(bases) ) {
            isSymbolic = true;
            if ( isRef ) throw new IllegalArgumentException("Cannot tag a symbolic allele as the reference allele");
        }
        else {
            StringUtil.toUpperCase(bases);
        }

        this.isRef = isRef;
        this.bases = bases;

        if ( ! Allele.acceptableAlleleBases(bases, isRef) )
            throw new IllegalArgumentException("Unexpected base in allele bases \'" + new String(bases)+"\'");
    }

    protected ByteArrayAllele(final String bases, final boolean isRef) {
        this(bases.getBytes(), isRef);
    }

    /**
     * Creates a new allele based on the provided one.  Ref state will be copied unless ignoreRefState is true
     * (in which case the returned allele will be non-Ref).
     *
     * This method is efficient because it can skip the validation of the bases (since the original allele was already validated)
     *
     * @param allele  the allele from which to copy the bases
     * @param ignoreRefState  should we ignore the reference state of the input allele and use the default ref state?
     */
    protected ByteArrayAllele(final ByteArrayAllele allele, final boolean ignoreRefState) {
        this.bases = allele.bases;
        this.isRef = ignoreRefState ? false : allele.isRef;
        this.isNoCall = allele.isNoCall;
        this.isSymbolic = allele.isSymbolic;
    }

    // ---------------------------------------------------------------------------------------------------------
    //
    // creation routines
    //
    // ---------------------------------------------------------------------------------------------------------


    // ---------------------------------------------------------------------------------------------------------
    //
    // accessor routines
    //
    // ---------------------------------------------------------------------------------------------------------

    @Override
    public boolean isPrefixOf(final Allele other) {
        if (other.length() < this.length()) {
            return false;
        } else if (other instanceof ByteArrayAllele) {
            final ByteArrayAllele baOther = (ByteArrayAllele) other;
            return isPrefixOfBytes(baOther.bases);
        } else {
            return isPrefixOfBytes(other.getBases());
        }
    }

    private boolean isPrefixOfBytes(final byte[] otherBases) {
        for (int i = 0; i < bases.length; i++) {
            if (bases[i] != otherBases[i]) {
                return false;
            }
        }
        return true;
    }

    /** @return true if this is the NO_CALL allele */
    @Override
    public boolean isNoCall()           { return isNoCall; }
    // Returns true if this is not the NO_CALL allele
    @Override
    public boolean isCalled()           { return ! isNoCall(); }

    /** @return true if this Allele is the reference allele */
    @Override
    public boolean isReference()        { return isRef; }

    /** @return true if this Allele is not the reference allele */
    @Override
    public boolean isNonReference()     { return ! isReference(); }

    /** @return true if this Allele is symbolic (i.e. no well-defined base sequence), this includes breakpoints and breakends */
    @Override
    public boolean isSymbolic()         { return isSymbolic; }

    /** @return true if this Allele is a breakpoint ( ex: G]17:198982] or ]13:123456]T ) */
    @Override
    public boolean isBreakpoint()         { return Allele.wouldBeBreakpoint(bases); }

    /** @return true if this Allele is a single breakend (ex: .A or A.) */
    @Override
    public boolean isSingleBreakend()         { return Allele.wouldBeSingleBreakend(bases); }

    // Returns a nice string representation of this object
    @Override
    public String toString() {
        return ( isNoCall() ? NO_CALL_STRING : getDisplayString() ) + (isReference() ? "*" : "");
    }

    /**
     * Return the DNA bases segregating in this allele.  Note this isn't reference polarized,
     * so the Null allele is represented by a vector of length 0
     *
     * @return the segregating bases
     */
    @Override
    public byte[] getBases() { return isSymbolic ? EMPTY_ALLELE_BASES : bases; }

    /**
     * Return the DNA bases segregating in this allele in String format.
     * This is useful, because toString() adds a '*' to reference alleles and getBases() returns garbage when you call toString() on it.
     *
     * @return the segregating bases
     */
    @Override
    public String getBaseString() { return isNoCall() ? NO_CALL_STRING : new String(getBases()); }

    /**
     * Return the printed representation of this allele.
     * Same as getBaseString(), except for symbolic alleles.
     * For symbolic alleles, the base string is empty while the display string contains &lt;TAG&gt;.
     *
     * @return the allele string representation
     */
    @Override
    public String getDisplayString() { return new String(bases); }

    /**
     * Same as #getDisplayString() but returns the result as byte[].
     *
     * Slightly faster then getDisplayString()
     *
     * @return the allele string representation
     */
    @Override
    public byte[] getDisplayBases() { return bases; }

    /**
     * @param other  the other allele
     *
     * @return true if these alleles are equal
     */
    @Override
    public boolean equals(Object other) {
        return ( ! (other instanceof ByteArrayAllele) ? false : equals((ByteArrayAllele)other, false) );
    }

    /**
     * @return hash code
     */
    @Override
    public int hashCode() {
        int hash = 1;
        for (int i = 0; i < bases.length; i++)
            hash += (i+1) * bases[i];
        return hash;
    }


    /**
     * Returns true if this and other are equal.  If ignoreRefState is true, then doesn't require both alleles has the
     * same ref tag
     *
     * @param other            allele to compare to
     * @param ignoreRefState   if true, ignore ref state in comparison
     * @return true if this and other are equal
     */
    @Override
    public boolean equals(final Allele other, final boolean ignoreRefState) {
        return this == other || (isRef == other.isReference() || ignoreRefState) && isNoCall == other.isNoCall() && (bases == other.getBases() || Arrays.equals(bases, other.getBases()));
    }

    /**
     * @param test  bases to test against
     *
     * @return  true if this Allele contains the same bases as test, regardless of its reference status; handles Null and NO_CALL alleles
     */
    @Override
    public boolean basesMatch(final byte[] test) { return !isSymbolic && (bases == test || Arrays.equals(bases, test)); }

    /**
     * @param test  bases to test against
     *
     * @return  true if this Allele contains the same bases as test, regardless of its reference status; handles Null and NO_CALL alleles
     */
    @Override
    public boolean basesMatch(final String test) { return basesMatch(test.toUpperCase().getBytes()); }


    /**
     * @param test  allele to test against
     *
     * @return  true if this Allele contains the same bases as test, regardless of its reference status; handles Null and NO_CALL alleles
     */
    @Override
    public boolean basesMatch(final Allele test) { return basesMatch(test.getBases()); }

    /**
     * @return the length of this allele.  Null and NO_CALL alleles have 0 length.
     */
    @Override
    public int length() {
        return isSymbolic ? 0 : bases.length;
    }

    // ---------------------------------------------------------------------------------------------------------
    //
    // useful static functions
    //
    // ---------------------------------------------------------------------------------------------------------

    @Override
    public int compareTo(final Allele other) {
        if ( isReference() && other.isNonReference() )
            return -1;
        else if ( isNonReference() && other.isReference() ) 
            return 1;
        else if (other instanceof ByteArrayAllele) {
            final ByteArrayAllele baOther = (ByteArrayAllele) other;
            return compareBases(baOther.bases);
        } else {
            return compareBases(other.getBases());
        }
    }

    private int compareBases(final byte[] otherBases) {
        final int stop = otherBases.length < bases.length ? otherBases.length : bases.length;
        for (int i = 0; i < stop; i++) {
            final int baseDiff = bases[i] - otherBases[i];
            if (baseDiff != 0) {
                return baseDiff < 0 ? -1 : 1;
            }
        }
        return Integer.compare(bases.length, otherBases.length);
    }

    /**
     *  @return true if Allele is either {@code <NON_REF>} or {@code <*>}
     */
    @Override
    public boolean isNonRefAllele() {
        return equals(NON_REF_ALLELE) || equals(UNSPECIFIED_ALTERNATE_ALLELE);
    }
}
