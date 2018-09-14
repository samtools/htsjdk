package org.htsjdk.core.utils;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * General utilities to check and validate conditions for constructors/methods.
 *
 * <p>All the methods in this class throw {@link IllegalArgumentException} if conditions are not
 * met.
 */
public final class ParamUtils {

    // cannot be instantiated
    private ParamUtils() {}

    /**
     * Checks that the condition is {@code true}.
     *
     * @param condition required condition.
     * @param message   the text message that would be passed to the exception thrown.
     */
    public static void validate(final boolean condition, final Supplier<String> message) {
        if (!condition) {
            throw new IllegalArgumentException(message.get());
        }
    }

    /**
     * Checks whether an index is within the bounds [0, length).
     *
     * @param index  query index.
     * @param length collection or array size.
     *
     * @return same value as the input {@code index}.
     */
    public static int validateIndex(final int index, final int length) {
        validate(index >= 0, () -> "index cannot be negative: " + index);
        validate(index < length, () -> "index out of bounds: " + index + ">" + (length - 1));
        return index;
    }

    /**
     * Checks that an {@link Object} is not {@code null}.
     *
     * @param object  any {@link Object}.
     * @param message the text message that would be passed to the exception thrown.
     * @param <T>     object type.
     *
     * @return the same object (if non-null).
     *
     * @throws IllegalArgumentException if a {@code object == null}.
     */
    public static <T> T nonNull(final T object, final Supplier<String> message) {
        if (object != null) {
            return object;
        }
        throw new IllegalArgumentException(message.get());
    }

    /**
     * Checks that an {@link Object} is not {@code null}.
     *
     * @param object  any {@link Object}.
     * @param <T>     object type.
     *
     * @return the same object (if non-null).
     *
     * @throws IllegalArgumentException if a {@code object == null}.
     */
    public static <T> T nonNull(final T object) {
        return nonNull(object, () -> "object cannot be null");
    }

    /**
     * Checks that a {@link Collection} is not {@code null} and that it is not empty.
     *
     * @param collection any Collection
     * @param message    the text message that would be passed to the exception thrown.
     * @param <T>        collection type.
     *
     * @return the same collection (if non-empty).
     *
     * @throws IllegalArgumentException if collection is {@code null} or empty.
     */
    public static <T extends Collection<?>> T nonEmpty(final T collection, final Supplier<String> message) {
        if (!nonNull(collection, message).isEmpty()) {
            return collection;
        }
        throw new IllegalArgumentException(message.get());
    }

    /**
     * Checks that a {@link Collection} is not {@code null} and that it is not empty.
     *
     * @param collection any Collection
     * @param <T>        collection type.
     *
     * @return the same collection (if non-empty).
     *
     * @throws IllegalArgumentException if collection is {@code null} or empty.
     */
    public static <T extends Collection<?>> T nonEmpty(final T collection) {
        return nonEmpty(collection, () -> "collection cannot be null or empty");
    }
}
