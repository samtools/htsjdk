package htsjdk.samtools.util;

/**
 * Miscellaneous util methods that don't fit anywhere else.
 */
public class CodeUtil {

    /** Mimic of Oracle's nvl() - returns the first value if not null, otherwise the second value. */
    public static <T> T getOrElse(final T value1, final T value2) {
        if (value1 != null) return value1;
        else return value2;
    }

}
