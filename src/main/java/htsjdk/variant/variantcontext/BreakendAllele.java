package htsjdk.variant.variantcontext;

/**
 * Subclass of Allele spezialized in representing breakend alleles.
 * <p>
 *     It does not offer any new operation, nor it is requirement for all breakend encoding alleles to be represeted by this class.
 *     It simply provides more efficient handling Breakend related methods declared in {@link Allele} when we
 *     can assue that the allele is indeed a break-end allele.
 * </p>
 */
final class BreakendAllele extends AbstractAllele {

    private final Breakend breakend;

    BreakendAllele(final Breakend breakend) {
        this.breakend = breakend;
    }

    @Override
    public boolean isBreakend() {
        return true;
    }

    @Override
    public boolean isPairedBreakend() {
        return !breakend.isSingle();
    }

    @Override
    public boolean isNoCall() {
        return false;
    }

    @Override
    public boolean isSymbolic() {
        return true;
    }

    @Override
    public boolean isAlternative() { return true; }

    @Override
    public StructuralVariantType getStructuralVariantType() {
        return StructuralVariantType.BND;
    }

    @Override
    public boolean isStructural() { return true; }

    @Override
    public boolean isBreakpoint() {
        return true;
    }

    @Override
    public boolean isSingleBreakend() {
        return breakend.isSingle();
    }

    @Override
    public Breakend asBreakend() {
        return breakend;
    }

    @Override
    public String encodeAsString() {
        return breakend.encodeAsString();
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || (other instanceof BreakendAllele && ((BreakendAllele) other).breakend.equals(breakend));
    }

    @Override
    public boolean equals(final Allele other, final boolean ignoreRefState) {
        return other == this || (other instanceof BreakendAllele && equals((BreakendAllele) other));
    }

    @Override
    public int hashCode() {
        return breakend.hashCode();
    }

    private boolean equals(final BreakendAllele other) {
        return other != null && other.breakend.equals(breakend);
    }

    @Override
    public String getContigID() {
        return breakend.getMateContig();
    }

    @Override
    public boolean hasContigID() {
        return breakend.getMateContig() != null;
    }

    @Override
    public int numberOfBases() {
        return breakend.numberOfBases();
    }

    @Override
    public byte baseAt(final int index) {
        return breakend.baseAt(index);
    }

    @Override
    public void copyBases(final int offset, final byte[] dest, final int destOffset, final int length) {
        breakend.copyBases(offset, dest, destOffset, length);
    }

    @Override
    public int compareBases(final int offset, final byte[] other, final int otherOffset, final int length) {
        return breakend.compareBases(offset, other, otherOffset, length);
    }

    @Override
    public int compareBases(final int offset, final BaseSequence other, final int otherOffset, final int length) {
        return breakend.compareBases(offset, other, otherOffset, length);
    }

    @Override
    public String getBaseString() {
        return new String(breakend.copyBases());
    }
}
