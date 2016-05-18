package htsjdk.samtools;

/**
 * How strict to be when reading a SAM or BAM, beyond bare minimum validation.
 */
public enum ValidationStringency {
    /**
     * Do the right thing, throw an exception if something looks wrong.
     */
    STRICT,
    /**
     * Emit warnings but keep going if possible.
     */
    LENIENT,
    /**
     * Like LENIENT, only don't emit warning messages.
     */
    SILENT;

    public static final ValidationStringency DEFAULT_STRINGENCY = STRICT;
}
