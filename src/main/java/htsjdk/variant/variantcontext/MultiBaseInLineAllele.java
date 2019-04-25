package htsjdk.variant.variantcontext;

import java.util.Arrays;

/**
 * Represent in-line resolved base sequence alleles.
 *
 * <p>
 *     This class accept any number of bases (0 to Integer.MAX_VALUE) however when
 *     the number of bases is exactly one you should consider to use {@link SingleBaseInLineAllele}
 *     instead.
 * </p>
 */
final class MultiBaseInLineAllele extends AbstractAllele {

    private final byte[] bases;
    private final boolean isReference;
    private transient int hashCode;
    private transient String encodingAsString;

    /**
     * No checks are performed here, the calling code must make sure that:
     * <il>
     *     <li>bases is not null, </li>
     *     <li>and that it only contains valid base values.</li>
     * </il>
     *
     * @param bases
     * @param isReference
     */
    MultiBaseInLineAllele(final byte[] bases, boolean isReference) {
        this.bases = bases;
        this.isReference = isReference;
    }

    @Override
    public Allele extend(final byte[] tail) {
        final int tailLength = tail.length;
        if (tailLength == 0) {
            return this;
        } else if (!AlleleUtils.areValidBases(tail)) {
            throw AlleleEncodingException.invalidBases(tail);
        } else {
            final byte[] extendedBases = Arrays.copyOf(bases, bases.length + tailLength);
            System.arraycopy(tail, 0, extendedBases, bases.length, tailLength);
            return new MultiBaseInLineAllele(extendedBases, isReference);
        }
    }

    @Override
    public boolean isCalled() {
        return true;
    }

    @Override
    public boolean isReference() {
        return isReference;
    }

    @Override
    public boolean isAlternative() { return !isReference; }

    @Override
    public boolean isSymbolic() {
        return false;
    }

    @Override
    public boolean isInline() {
        return true;
    }


    @Override
    public String encodeAsString() {
        if (encodingAsString == null) {
            encodingAsString = new String(bases);
        }
        return encodingAsString;
    }

    @Override
    public boolean equals(final Allele other, final boolean ignoreRefState) {
        return other == this
                || (other != null
                    && other.isInline()
                    && (ignoreRefState || other.isReference() == isReference)
                    && other.equalBases(bases));
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof Allele && equals((Allele) other, false);
    }

    @Override
    public int hashCode() {
       if (hashCode == 0) {
           hashCode = 0;
           for (int i = 0; i < bases.length; i++) {
               hashCode = hashCode * 31 + bases[i];
           }
           if (isReference) {
               hashCode *= 31;
           }
       }
       return hashCode;
    }

    @Override
    public int numberOfBases() {
        return bases.length;
    }

    @Override
    public byte baseAt(int index) {
        return bases[index];
    }

    @Override
    public String getBaseString() {
        return new String(bases);
    }
}
