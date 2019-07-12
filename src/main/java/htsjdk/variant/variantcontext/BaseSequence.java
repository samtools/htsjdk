package htsjdk.variant.variantcontext;

import htsjdk.samtools.util.SequenceUtil;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Common interface for (nucleotide) base sequences.
 * <p>
 *     It provides a common interface to access individual bases and sections of the base sequence.
 * </p>
 * <p/>
 *     All the operation herein are read-only in the sense that they are not supposed to change the
 *     sequence nor to expose the internals of the sequence otherwise.
 * </p>
 * <p>
 *     Nonetheless mutable sequence may implement this interface thus the underlying sequence may change between
 *     (or during!) method invocations.
 * </p>
 */
public interface BaseSequence {

    /**
     * Returns the number of bases in the sequence.
     * @return 0 or greater.
     */
    int numberOfBases();

    /**
     * Returns the base at a particular position of the sequence.
     * @param index the requested position 0-based.
     * @return a valid base as determined by {@link SequenceUtil#isValidBase}..
     * @throws IndexOutOfBoundsException if the index provided is out of bounds.
     */
    byte baseAt(final int index);

    /**
     * Returns a copy of the base sequence as a naked byte array.
     * <p>
     *     Changes in the returned array won't affect this sequence and <i>vice-versa</i>.
     * </p>
     * @return never {@code null} but a zero-length array if {@code numberOfBases() == 0}.
     */
    default byte[] copyBases() {
        final int numberOfBases = numberOfBases();
        if (numberOfBases == 0) {
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        } else {
            final byte[] result = new byte[numberOfBases];
            copyBases(0, result, 0, numberOfBases);
            return result;
        }
    }

    /**
     * Copies a section of the sequence into a new byte array.
     * <p>
     *     Changes in the returned array won't affect this sequence and <i>vice-versa</i>.
     * </p>
     * @param offset the first base position to copy.
     * @param length the number of base to copy.
     * @return never {@code null}.
     * @throws IndexOutOfBoundsException if {@code length} > 0 and in combination with {@code offset} it points
     * outside the boundaries of this sequence.
     */
    default byte[] copyBases(final int offset, final int length) {
        if (length == 0) {
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        } else {
            final byte[] result = new byte[length];
            copyBases(offset, result, 0, length);
            return result;
        }
    }

    /**
     * Copies a range of the base sequence in a new byte array.
     * <p>
     *     Changes in the returned array won't affect this sequence and <i>vice-versa</i>.
     * </p>
     * @param from the first position to copy.
     * @param to the position after the last one to copy.
     * @return never {@code null}.
     * @throws IndexOutOfBoundsException if the range is not empty (i.e. {@code to > from}) and {@code from} or {@code to}
     *   point outside the bounds of the sequence.
     */
    default byte[] copyBasesRange(final int from, final int to) {
        final int length = to - from;
        if (length == 0) {
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        } else {
            final byte[] result = new byte[to - from];
            copyBases(from, result, 0, length);
            return result;
        }
    }

    /**
     * Copies the bases in the sequence onto an existing byte array.
     * @param offset position of the first base to copy.
     * @param dest where to copy the base to.
     * @param destOffset where to start copying the bases in the destination array.
     * @param length the number of consecutive bases to copy.
     * @throws NullPointerException if {@code dest} is {@code null}.
     * @throws IndexOutOfBoundsException if the indexes and length provided result in stepping outside the boundaries
     *  of this sequence or the destination array.
     */
    default void copyBases(final int offset, final byte[] dest, final int destOffset, final int length) {
        if (length == 0 && dest == null) { // fail with an NPE on a null destination even if length is 0.
            throw new NullPointerException();
        }
        final int to = offset + length;
        for (int i = offset, j = destOffset; i < to; i++, j++) {
            dest[j] = baseAt(i);
        }
    }

    default void copyBasesRange(final int from, final int to, final byte[] dest, final int destOffset) {
        final int length = to - from;
        copyBases(from, dest, destOffset, length);
    }

    default void copyBasesRange(final int from, final int to, final byte[] dest) {
        final int length = to - from;
        copyBases(from, dest, 0, length);
    }

    default void copyBases(final byte[] dest) {
        copyBases(0, dest, 0, numberOfBases());
    }

    default void copyBases(final byte[] dest, final int destOffset) {
        copyBases(0, dest, destOffset, numberOfBases());
    }

    /**
     * Compares this sequence
     * Implementations are not allow to alter then content of the input base array.
     *
     * Returns 0 if both sequences are equal ignoring base case.
     * If this base-sequence is smaller lexicographically the value returned is strictly negative equal to {@code -i -1} where {@code i}
     * is the first position to differ.
     * If this base-sequence is larger lexicographcially the value returned is strictly positive equal to {@code -i -1} where {@code i}
     * is the first position to differ.
     * It will return a negative value if this sequence is smaller lexicographically where the absolute value indicates
     * the first position that differs (i) as {@code -i -1}.
     */
    default int compareBases(final int offset, final byte[] other, final int otherOffset, final int length) {
        for (int i = offset, j = otherOffset, k = 0; k < length; k++) {
            final byte a = baseAt(i++);
            final byte b = other[j++];
            final int comp = SequenceUtil.compareBases(a, b);
            if (comp != 0) {
                return comp < 0 ? -k -1 : k + 1;
            }
        }
        return 0;
    }

    default int compareBases(final int offset, final CharSequence other, final int otherOffset, final int length) {
        for (int i = offset, j = otherOffset, k = 0; k < length; k++) {
            final byte a = baseAt(i++);
            final byte b = (byte) other.charAt(j++);
            final int comp = SequenceUtil.compareBases(a, b);
            if (comp != 0) {
                return comp < 0 ? -k -1 : k + 1;
            }
        }
        return 0;
    }

    default int compareBases(final int offset, final BaseSequence other, final int otherOffset, final int length) {
        if (other == this && offset == otherOffset) { // short cut in case of a trivial comparison with itself.
            return 0;
        }
        for (int i = offset, j = otherOffset, k = 0; k < length; k++) {
            final byte a = baseAt(i++);
            final byte b = other.baseAt(j++);
            final int comp = SequenceUtil.compareBases(a, b);
            if (comp != 0) {
                return comp < 0 ? -k -1 : k + 1;
            }
        }
        return 0;
    }

    default boolean equalBases(final int offset, final byte[] other, final int otherOffset, final int length) {
        return compareBases(offset, other, otherOffset, length) == 0;
    }

    default boolean equalBases(final int offset, final CharSequence other, final int otherOffset, final int length) {
        return compareBases(offset, other, otherOffset, length) == 0;
    }

    default boolean equalBases(final CharSequence other) {
        final int numberOfBases = numberOfBases();
        if (other.length() != numberOfBases) {
            return false;
        } else {
            return compareBases(0, other, 0, numberOfBases()) == 0;
        }
    }

    default boolean equalBases(final int offset, final BaseSequence other, final int otherOffset, final int length) {
        return compareBases(offset, other, otherOffset, length) == 0;
    }

    default boolean equalBases(final byte[] other) {
        if (other.length != numberOfBases()) {
            return false;
        } else {
            return equalBases(0, other, 0, other.length);
        }
    }

    default boolean equalBases(final BaseSequence other) {
        if (other.numberOfBases() != numberOfBases()) {
            return false;
        } else {
            return equalBases(0, other, 0, numberOfBases());
        }
    }
}
