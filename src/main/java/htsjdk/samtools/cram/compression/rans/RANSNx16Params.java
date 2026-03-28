package htsjdk.samtools.cram.compression.rans;

/**
 * Parameters for the rANS Nx16 codec. The format flags byte encodes the combination
 * of transformations (ORDER, N32, STRIPE, NOSZ, CAT, RLE, PACK) to apply.
 */
public final class RANSNx16Params implements RANSParams {

    public static final int ORDER_FLAG_MASK = 0x01;
    public static final int N32_FLAG_MASK = 0x04;
    public static final int STRIPE_FLAG_MASK = 0x08;
    public static final int NOSZ_FLAG_MASK = 0x10;
    public static final int CAT_FLAG_MASK = 0x20;
    public static final int RLE_FLAG_MASK = 0x40;
    public static final int PACK_FLAG_MASK = 0x80;

    private static final int FORMAT_FLAG_MASK = 0xFF;

    private final int formatFlags;

    /**
     * @param formatFlags the raw format flags byte from the compressed stream header
     */
    public RANSNx16Params(final int formatFlags) {
        this.formatFlags = formatFlags;
    }

    @Override
    public String toString() {
        return "RANSNx16Params{" + "formatFlags=" + formatFlags + "}";
    }

    @Override
    public ORDER getOrder() {
        return ORDER.fromInt(formatFlags & ORDER_FLAG_MASK);
    }

    @Override
    public int getFormatFlags() {
        return formatFlags & FORMAT_FLAG_MASK;
    }

    /** @return the number of interleaved rANS states: 4 (default) or 32 (if N32 flag set). */
    public int getNumInterleavedRANSStates() {
        return ((formatFlags & N32_FLAG_MASK) == 0) ? 4 : 32;
    }

    /** @return true if the STRIPE transformation flag is set. */
    public boolean isStripe() {
        return (formatFlags & STRIPE_FLAG_MASK) != 0;
    }

    /** @return true if the NOSZ (no-size) flag is set, meaning output size is externally provided. */
    public boolean isNosz() {
        return (formatFlags & NOSZ_FLAG_MASK) != 0;
    }

    /** @return true if the CAT (concatenation/uncompressed) flag is set. */
    public boolean isCAT() {
        return (formatFlags & CAT_FLAG_MASK) != 0;
    }

    /** @return true if the RLE (run-length encoding) preprocessing flag is set. */
    public boolean isRLE() {
        return (formatFlags & RLE_FLAG_MASK) != 0;
    }

    /** @return true if the PACK (bit-packing) preprocessing flag is set. */
    public boolean isPack() {
        return (formatFlags & PACK_FLAG_MASK) != 0;
    }
}
