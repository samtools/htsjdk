package htsjdk.samtools.cram.common;

public class CramVersions {
    public static final Version CRAM_v2_1 = new Version(2, 1, 0);
    public static final Version CRAM_v3 = new Version(3, 0, 0);

    /**
     * The default CRAM version when creating a new CRAM output file or stream.
     */
    public static final Version DEFAULT_CRAM_VERSION = CRAM_v3;
}
