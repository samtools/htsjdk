package htsjdk.variant.variantcontext;

final class UnspecifiedAlternativeAllele extends AbstractAllele {

    static UnspecifiedAlternativeAllele NON_REF = new UnspecifiedAlternativeAllele("NON_REF");
    static UnspecifiedAlternativeAllele UNSPECIFIED_ALT = new UnspecifiedAlternativeAllele("*");

    private final String id;
    private final String encodeAsString;

    private UnspecifiedAlternativeAllele(final String id) {
        this.id = id;
        this.encodeAsString = '<' + id + '>';
    }

    @Override
    public boolean equals(final Allele other, boolean ignoreRefState) {
        return other == this || (other instanceof UnspecifiedAlternativeAllele);
    }

    @Override
    public final boolean equals(final Object other) {
        return other == this || (other instanceof UnspecifiedAlternativeAllele);
    }

    @Override
    public int hashCode() {
        return getClass().getName().hashCode();
    }

    @Override
    public boolean isAlternative() { return true; }

    @Override
    public boolean isSymbolic() {
        return true;
    }

    @Override
    public String getSymbolicID() {
        return id;
    }

    @Override
    public String encodeAsString() {
        return encodeAsString;
    }

    @Override
    public boolean isUnspecifiedAlternative() {
        return true;
    }

    @Override
    public String getBaseString() {
        return "";
    }

    // prevents cloning by deserialization of NON_REF and U_ALT instances at least.
    private Object readResolve() {
        if (id.equals(Allele.NON_REF_ID)) {
            return Allele.NON_REF;
        } else if (id.equals(Allele.UNSPECIFIED_ALT_ID)) {
            return Allele.UNSPECIFIED_ALT;
        } else {
            throw new IllegalStateException("invalid id: " + id);
        }
    }
}
