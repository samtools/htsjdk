package htsjdk.variant.variantcontext;

/**
 *  Provides most common implementations for {@link Allele} methods.
 */
abstract class AbstractAllele implements Allele {

    AbstractAllele() {
    }

    @Override
    public Allele asAlternative() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Allele asReference() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] encodeAsBytes() {
        return encodeAsString().getBytes();
    }

    @Override
    public boolean isAlternative() {
        return false;
    }

    @Override
    public int numberOfBases() {
        return 0;
    }

    @Override
    public byte baseAt(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Allele extend(byte[] tail) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isBreakend() {
        return false;
    }

    @Override
    public boolean isPairedBreakend() {
        return false;
    }

    @Override
    public boolean isContigInsertion() {
        return false;
    }

    @Override
    public StructuralVariantType getStructuralVariantType() {
        return null;
    }

    @Override
    public boolean isStructural() {
        return false;
    }

    @Override
    public boolean isNoCall() {
        return false;
    }

    @Override
    public boolean isCalled() {
        return false;
    }

    @Override
    public boolean isReference() {
        return false;
    }

    @Override
    public boolean isNonReference() {
        return !isReference();
    }

    @Override
    public boolean isSymbolic() {
        return false;
    }

    @Override
    public String getSymbolicID() {
        return null;
    }

    @Override
    public boolean isInline() {
        return false;
    }

    @Override
    public boolean isBreakpoint() {
        return false;
    }

    @Override
    public boolean isSingleBreakend() {
        return false;
    }

    @Override
    public Breakend asBreakend() {
        return null;
    }

    @Override
    public String encodeAsString() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getContigID() {
        return null;
    }

    @Override
    public boolean hasContigID() {
        return false;
    }

    @Override
    public String getBaseString() {
        final int numberOfBases = numberOfBases();
        if (numberOfBases == 0) {
            return "";
        } else if (numberOfBases == 1) {
            return "" + (char) baseAt(0);
        } else {
            final StringBuilder builder = new StringBuilder(numberOfBases);
            for (int i = 0; i < numberOfBases; i++) {
                builder.append((char) baseAt(i));
            }
            return builder.toString();
        }
    }

    @Override
    public String getDisplayString() {
        return encodeAsString();
    }

    @Override
    public byte[] getDisplayBases() {
        return encodeAsBytes();
    }

    @Override
    public boolean isSpanDeletion() { return false; }

    @Override
    public boolean isUnspecifiedAlternative() {
        return false;
    }

    @Override
    public String toString() {
        return encodeAsString() + (isReference() ? "*" : "");
    }
}
