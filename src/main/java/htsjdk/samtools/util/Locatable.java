package htsjdk.samtools.util;

import java.util.Objects;

/**
 * Any class that has a single logical mapping onto the genome should implement Locatable
 * positions should be reported as 1-based and closed at both ends
 *
 */
public interface Locatable {

    /**
     * Gets the contig name for the contig this is mapped to.  May return null if there is no unique mapping.
     * @return name of the contig this is mapped to, potentially null
     */
    String getContig();

    /**
     * @return 1-based start position, undefined if getContig() == null
     */
    int getStart();

    /**
     * @return 1-based closed-ended position, undefined if getContig() == null
     */
    int getEnd();


    /**
     * @return number of bases of reference covered by this interval
     */
    default int getLengthOnReference() {
        return CoordMath.getLength(getStart(), getEnd());
    }

    /**
     * Determines whether this interval overlaps the provided locatable.
     *
     * @param other interval to check
     * @return true if this interval overlaps other, otherwise false
     */
    default boolean overlaps(Locatable other) {
        return withinDistanceOf(other, 0);
    }

    /**
     * Determines whether this interval comes within "margin" of overlapping the provided locatable.
     * This is the same as plain overlaps if margin=0.
     *
     * @param other interval to check
     * @param margin how many bases may be between the two interval for us to still consider them overlapping.
     * @return true if this interval overlaps other, otherwise false
     */
    default boolean withinDistanceOf(Locatable other, int margin) {
        return contigsMatch(other) &&
                CoordMath.overlaps(getStart(), getEnd(), other.getStart()-margin, other.getEnd()+margin);
    }

    /**
     * Determines whether this interval contains the entire region represented by other
     * (in other words, whether it covers it).
     *
     * @param other interval to check
     * @return true if this interval contains all of the bases spanned by other, otherwise false
     */
    default boolean contains(Locatable other) {
        return contigsMatch(other) && CoordMath.encloses(getStart(), getEnd(), other.getStart(), other.getEnd());
    }

    /**
     * Determine if this is on the same contig as other
     * @return true if this is
     */
    default boolean contigsMatch(Locatable other) {
        return getContig() != null && other != null && Objects.equals(this.getContig(), other.getContig());
    }


}
