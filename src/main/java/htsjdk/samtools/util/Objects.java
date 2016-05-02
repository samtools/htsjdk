package htsjdk.samtools.util;

/**
 * Subset of JDK7's {@link java.util.Objects} for non-JDK7 support. 
 * 
 * @author mccowan
 */
public class Objects {
    public static boolean equals(final Object a, final Object b) {
        return (a == b) || (a != null && a.equals(b));
    } 
}
