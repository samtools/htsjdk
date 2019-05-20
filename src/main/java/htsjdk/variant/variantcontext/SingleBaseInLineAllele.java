package htsjdk.variant.variantcontext;

import htsjdk.samtools.util.SequenceUtil;

final class SingleBaseInLineAllele extends AbstractAllele {

    private final byte base;
    private final boolean isReference;
    private transient String asString;

    SingleBaseInLineAllele(final String asString, final boolean isReference) {
        this.base = (byte) asString.charAt(0);
        this.asString = asString;
        this.isReference = isReference;
    }

    SingleBaseInLineAllele(final byte base, final boolean isReference) {
        this.base = base;
        this.asString = "" + (char) base;
        this.isReference = isReference;
    }

    @Override
    public int numberOfBases() {
        return 1;
    }

    @Override
    public byte baseAt(final int index) {
        if (index == 0) {
            return base;
        } else {
            throw new IndexOutOfBoundsException();
        }
    }

    @Override
    public boolean isReference() {
        return isReference;
    }

    @Override
    public boolean isAlternative() { return !isReference; }

    @Override
    public boolean equals(final Object other) {
        return other == this || (other instanceof Allele && equals((Allele) other, false));
    }

    @Override
    public int hashCode() {
        return base;
    }

    @Override
    public boolean isInline() {
        return true;
    }

    @Override
    public Allele extend(final byte[] tail) {
        if (tail.length == 0) {
            return this;
        } else {
            final byte[] bases = new byte[tail.length + 1];
            bases[0] = base;
            for (int i = 0; i < tail.length; ) {
                final byte b = tail[i];
                if (!AlleleUtils.isValidBase(b)) {
                    throw new AlleleEncodingException("bad bases in input tail: '%s'", new String(tail));
                }
                bases[++i] = b;
            }
            return new MultiBaseInLineAllele(bases, isReference);
        }
    }

    @Override
    public boolean isCalled() {
        return true;
    }

    @Override
    public Allele asAlternative() {
        if (isReference) {
            return AlleleUtils.decodeSingleBaseInline(base, false);
        } else {
            return this;
        }
    }

    @Override
    public Allele asReference() {
        if (isReference) {
            return this;
        } else {
            return AlleleUtils.decodeSingleBaseInline(base, true);
        }
    }

    public String encodeAsString() {
        return asString != null ? asString : (asString = "" + (char) base);
    }

    @Override
    public boolean equals(final Allele other, final boolean ignoreRefState) {
        return other == this
                || (other.isInline()
                    && other.numberOfBases() == 1
                    && (ignoreRefState || other.isReference() == isReference)
                    && SequenceUtil.basesEqual(other.baseAt(0), base));
    }

    @Override
    public String getBaseString() {
        return String.valueOf((char) base);
    }

    // limits cloning due to diserization:
    private Object readResolve() {
        return Allele.inline(base, isReference);
    }
}
