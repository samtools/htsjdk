package htsjdk.samtools.cram.compression.rans;

final public class Constants {
    public static final int TOTAL_FREQ_SHIFT = 12;
    public static final int TOTAL_FREQ = (1 << TOTAL_FREQ_SHIFT); // 4096
    public static final int NUMBER_OF_SYMBOLS = 256;
    public static final int RANS_4x8_LOWER_BOUND = 1 << 23;
    public static final int RANS_4x8_ORDER_BYTE_LENGTH = 1;
    public static final int RANS_4x8_COMPRESSED_BYTE_LENGTH = 4;
    public static final int RANS_4x8_RAW_BYTE_LENGTH = 4;
    public static final int RANS_4x8_PREFIX_BYTE_LENGTH = RANS_4x8_ORDER_BYTE_LENGTH + RANS_4x8_COMPRESSED_BYTE_LENGTH + RANS_4x8_RAW_BYTE_LENGTH;
    public static final int RANS_Nx16_LOWER_BOUND = 1 << 15;
}