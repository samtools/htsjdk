package htsjdk.samtools.cram.compression.rans;

final class Constants {
    static final int TF_SHIFT = 12;
    static final int TOTFREQ = (1 << TF_SHIFT); // 4096
    static final int RANS_BYTE_L_4x8 = 1 << 23;
    static final int RANS_BYTE_L_Nx16 = 1 << 15;
    static final int NUMBER_OF_SYMBOLS = 256;
}