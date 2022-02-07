package htsjdk.samtools.cram.compression.rans40;

class Constants {
    static final int TF_SHIFT = 12;
    static final int TOTFREQ = (1 << TF_SHIFT); // 4096
    static final int RANS_BYTE_L = 1 << 23;
}
