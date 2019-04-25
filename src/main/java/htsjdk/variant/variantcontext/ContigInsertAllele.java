package htsjdk.variant.variantcontext;

import htsjdk.samtools.util.SequenceUtil;

import java.util.Objects;

final class ContigInsertAllele extends AbstractAllele {

    private static long serialVersionUID = 1L;

    private final byte[] bases;
    private final String assemblyContig;
    private transient String encodingAsString;

    ContigInsertAllele(final byte[] bases, final String assemblyContig) {
        this.bases = Objects.requireNonNull(bases);
        this.assemblyContig = Objects.requireNonNull(assemblyContig);
    }

    public String getContigID() {
        return assemblyContig;
    }

    @Override
    public boolean hasContigID() {
        return true;
    }

    @Override
    public boolean equals(final Allele other, boolean ignoreRefState) {
        return other instanceof ContigInsertAllele && equals((ContigInsertAllele) other);
    }

    @Override
    public String encodeAsString() {
        if (encodingAsString == null) {
            final StringBuilder builder = new StringBuilder(bases.length + assemblyContig.length() + 2);
            for (final byte b : bases) {
                builder.append((char) b);
            }
            builder.append('<');
            builder.append(assemblyContig);
            builder.append('>');
            encodingAsString = builder.toString();
        }
        return encodingAsString;
    }

    @Override
    public int numberOfBases() {
        return bases.length;
    }

    @Override
    public byte baseAt(final int index) {
        return bases[index];
    }

    @Override
    public boolean isCalled() { return true; }

    @Override
    public boolean isAlternative() {
        return true;
    }

    @Override
    public boolean isSymbolic() {
        return true;
    }

    @Override
    public boolean isStructural() { return true; }

    @Override
    public StructuralVariantType getStructuralVariantType() { return StructuralVariantType.INS; }

    @Override
    public boolean equals(final Object other) {
        return other instanceof ContigInsertAllele && equals((ContigInsertAllele) other);
    }

    @Override
    public int hashCode() {
        return SequenceUtil.hashCode(bases) * 31 + assemblyContig.hashCode();
    }

    private boolean equals(final ContigInsertAllele other) {
        return SequenceUtil.equals(bases, other.bases) && other.assemblyContig.equals(assemblyContig);
    }
}
