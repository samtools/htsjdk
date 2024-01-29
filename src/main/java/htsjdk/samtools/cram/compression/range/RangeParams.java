package htsjdk.samtools.cram.compression.range;

public class RangeParams {
    public static final int ORDER_FLAG_MASK = 0x01;
    public static final int EXT_FLAG_MASK = 0x04;
    public static final int STRIPE_FLAG_MASK = 0x08;
    public static final int NOSZ_FLAG_MASK = 0x10;
    public static final int CAT_FLAG_MASK = 0x20;
    public static final int RLE_FLAG_MASK = 0x40;
    public static final int PACK_FLAG_MASK = 0x80;


    // format is the first byte of the compressed data stream,
    // which consists of all the bit-flags detailing the type of transformations
    // and entropy encoders to be combined
    private int formatFlags;

    private static final int FORMAT_FLAG_MASK = 0xFF;

    public enum ORDER {
        ZERO, ONE;

        public static RangeParams.ORDER fromInt(final int orderValue) {
            try {
                ORDER[] x = ORDER.values();
                return x[orderValue];
            } catch (final ArrayIndexOutOfBoundsException e) {
                throw new IllegalArgumentException("Unknown Range order: " + orderValue, e);
            }
        }
    }

    public RangeParams(final int formatFlags) {
        this.formatFlags = formatFlags;
    }

    @Override
    public String toString() {
        return "RangeParams{" + "formatFlags=" + formatFlags + "}";
    }

    public int getFormatFlags(){
        // first byte of the encoded stream
        return formatFlags & FORMAT_FLAG_MASK;
    }

    public ORDER getOrder() {
        // Range Order ZERO or ONE encoding
        return ORDER.fromInt(formatFlags & ORDER_FLAG_MASK); //convert into order type
    }

    public boolean isExternalCompression(){
        // “External” compression via bzip2
        return ((formatFlags & EXT_FLAG_MASK)!=0);
    }

    public boolean isStripe(){
        // multiway interleaving of byte streams
        return ((formatFlags & STRIPE_FLAG_MASK)!=0);
    }

    public boolean isNosz(){
        // original size is not recorded (for use by Stripe)
        return ((formatFlags & NOSZ_FLAG_MASK)!=0);
    }

    public boolean isCAT(){
        // Data is uncompressed
        return ((formatFlags & CAT_FLAG_MASK)!=0);
    }

    public boolean isRLE(){
        // Run length encoding, with runs and literals encoded separately
        return ((formatFlags & RLE_FLAG_MASK)!=0);
    }

    public boolean isPack(){
        // Pack 2, 4, 8 or infinite symbols per byte
        return ((formatFlags & PACK_FLAG_MASK)!=0);
    }

}