package htsjdk.variant.variantcontext;

final class SpanDelAllele extends AbstractAllele {

    static final Allele INSTANCE = new SpanDelAllele();

    private static final long serialVersionUID = 1;

    private SpanDelAllele() {
    }


    @Override
    public Allele asAlternative() {
        return this;
    }

    @Override
    public String encodeAsString() {
        return Allele.SPAN_DEL_STRING;
    }

    @Override
    public boolean equals(Allele other, boolean ignoreRefState) {
        return other == this || other instanceof SpanDelAllele;
    }

    @Override
    public boolean isAlternative() {
        return true;
    }

    @Override
    public boolean isCalled() {
        return true;
    }

    @Override
    public boolean isSpanDeletion() {
        return true;
    }

    @Override
    public boolean equals(final Object other) {
        return other == this || other instanceof SpanDelAllele;
    }

    @Override
    public int hashCode() {
        return Allele.SPAN_DEL_STRING.hashCode();
    }

    // declared to prevent cloning by deserialization of this singleton.
    private Object readResolve() {
        return INSTANCE;
    }
}
