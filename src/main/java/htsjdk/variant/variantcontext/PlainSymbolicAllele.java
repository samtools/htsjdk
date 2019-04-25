package htsjdk.variant.variantcontext;

import java.util.Objects;

/**
 * Represent symbolic alleles that are encoded by a ID only such as {@code <DEL> or <DUP:TANDEM>}.
 */
final class PlainSymbolicAllele extends AbstractAllele {

    private static long serialVersionUID = 1;

    private final String id;
    private transient String encodingAsString;
    private final StructuralVariantType svType;

    PlainSymbolicAllele(final String id) {
        this(id, null);
    }

    PlainSymbolicAllele(final String id, final StructuralVariantType svType) {
        this.id = id;
        this.svType = svType;
    }

    public String getSymbolicID() {
        return id;
    }

    @Override
    public boolean isBreakpoint() {
        return svType == StructuralVariantType.BND;
    }

    @Override
    public boolean isBreakend() {
        return svType == StructuralVariantType.BND;
    }

    public String encodeAsString() {
        if (encodingAsString == null) {
            encodingAsString = "<" + id + ">";
        }
        return encodingAsString;
    }

    @Override
    public boolean equals(final Allele other, final boolean ignoreRefState) {
        return equals(other);
    }

    @Override
    public boolean equals(final Object other) {
        return other == this || (other instanceof PlainSymbolicAllele && equals((PlainSymbolicAllele) other));
    }

    @Override
    public StructuralVariantType getStructuralVariantType() {
        return svType;
    }

    @Override
    public boolean isStructural() {
        return svType != null;
    }

    @Override
    public String getBaseString() {
        return "";
    }

    @Override
    public boolean isAlternative() { return true; }

    @Override
    public boolean isSymbolic() { return true; }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    private boolean equals(final PlainSymbolicAllele other) {
        return id.equals(other.id);
    }

    // limits cloning by deserialization with those symbolics that have be
    // registered.
    private Object readResolve() {
        final Allele registered = AlleleUtils.lookupSymbolic(id);
        if (registered instanceof PlainSymbolicAllele && Objects.equals(svType, ((PlainSymbolicAllele) registered).svType)) {
            return registered;
        } else {
            return this;
        }
    }

}
