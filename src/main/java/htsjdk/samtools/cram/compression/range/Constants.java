package htsjdk.samtools.cram.compression.range;

final public class Constants {
    public static final int NUMBER_OF_SYMBOLS = 256;
    public static final int MAX_FREQ = ((1<<16)-17);
    public static final int STEP = 16;
    public static final long MAX_RANGE = 0xFFFFFFFFL;
}