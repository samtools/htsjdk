package htsjdk.samtools.cram.compression.rans;

public class RANSNx16Params implements RANSParams{

    // format is the first byte of the compressed data stream,
    // which consists of all the bit-flags detailing the type of transformations
    // and entropy encoders to be combined
    private int formatFlags;

    // To get the least significant 7 bits of format byte
    private static final int FORMAT_FLAG_MASK = 0x7f;

    // RANS Nx16 Bit Flags
    private static final int ORDER_FLAG_MASK = 0x01;
    private static final int X32_FLAG_MASK = 0x04;
    private static final int STRIPE_FLAG_MASK = 0x08;
    private static final int NOSZ_FLAG_MASK = 0x10;
    private static final int CAT_FLAG_MASK = 0x20;
    private static final int RLE_FLAG_MASK = 0x40;
    private static final int PACK_FLAG_MASK = 0x80;

    // output length. Used as input param to RANS Nx16 uncompress method
    private final int nOut = 0;

    public RANSNx16Params(int formatFlags) {
        this.formatFlags = formatFlags;
    }

    @Override
    public ORDER getOrder() {
        // Rans Order ZERO or ONE encoding
        return ORDER.fromInt(formatFlags & ORDER_FLAG_MASK); //convert into order type
    }

    protected int getFormatFlags(){
        // Least significant 7 bits of the format
        return formatFlags & FORMAT_FLAG_MASK;
    }

    public void setFormatFlags(int formatFlags) {
        this.formatFlags = formatFlags;
    }

    protected boolean getX32(){
        // Interleave N = 32 rANS states (else N = 4)
        return ((formatFlags & X32_FLAG_MASK)!=0);
    }

    protected boolean getStripe(){
        // multiway interleaving of byte streams
        return ((formatFlags & STRIPE_FLAG_MASK)!=0);
    }

    protected boolean getNosz(){
        // original size is not recorded (for use by Stripe)
        return ((formatFlags & NOSZ_FLAG_MASK)!=0);
    }

    protected boolean getCAT(){
        // Data is uncompressed
        return ((formatFlags & CAT_FLAG_MASK)!=0);
    }

    protected boolean getRLE(){
        // Run length encoding, with runs and literals encoded separately
        return ((formatFlags & RLE_FLAG_MASK)!=0);
    }

    protected boolean getPack(){
        // Pack 2, 4, 8 or infinite symbols per byte
        return ((formatFlags & PACK_FLAG_MASK)!=0);
    }

    public int getnOut() {
        // nOut is the length of uncompressed data
        // used in uncompress method
        return nOut;
    }

}