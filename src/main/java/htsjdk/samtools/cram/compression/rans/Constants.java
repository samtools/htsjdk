package htsjdk.samtools.cram.compression.rans;

final public class Constants {
    public static final int TF_SHIFT = 12;
    public static final int TOTFREQ = (1 << TF_SHIFT); // 4096
    public static final int RANS_BYTE_L_4x8 = 1 << 23;
    public static final int RANS_BYTE_L_Nx16 = 1 << 15;
    public static final int NUMBER_OF_SYMBOLS = 256;
}