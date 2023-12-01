package htsjdk.samtools.cram.compression.rans.ransnx16;

import htsjdk.samtools.cram.compression.rans.RANSParams;

public class RANSNx16Params implements RANSParams {

    // RANS Nx16 Bit Flags
    public static final int ORDER_FLAG_MASK = 0x01;
    public static final int N32_FLAG_MASK = 0x04;
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

    public RANSNx16Params(final int formatFlags) {
        this.formatFlags = formatFlags;
    }

    @Override
    public String toString() {
        return "RANSNx16Params{" + "formatFlags=" + formatFlags + "}";
    }

    @Override
    public ORDER getOrder() {
        // Rans Order ZERO or ONE encoding
        return ORDER.fromInt(formatFlags & ORDER_FLAG_MASK); //convert into order type
    }

    public int getFormatFlags(){
        // first byte of the encoded stream
        return formatFlags & FORMAT_FLAG_MASK;
    }

    public int getNumInterleavedRANSStates(){
        // Interleave N = 32 rANS states (else N = 4)
        return ((formatFlags & N32_FLAG_MASK) == 0) ? 4 : 32;
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