/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package htsjdk.utils;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Simple functions that streamline the checking of values.
 */
public class ValidationUtils {
    /**
     * Checks that an Object {@code object} is not null and returns the same object or throws an {@link IllegalArgumentException}
     * @param object any Object
     * @return the same object
     * @throws IllegalArgumentException if a {@code o == null}
     */
    public static <T> T nonNull(final T object) {
        return ValidationUtils.nonNull(object, "Null object is not allowed here.");
    }

    /**
     * Checks that an {@link Object} is not {@code null} and returns the same object or throws an {@link IllegalArgumentException}
     * @param object any Object
     * @param message the text message that would be passed to the exception thrown when {@code o == null}.
     * @return the same object
     * @throws IllegalArgumentException if a {@code o == null}
     */
    public static <T> T nonNull(final T object, final String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
        return object;
    }

    /**
     * Checks that an {@link Object} is not {@code null} and returns the same object or throws an {@link IllegalArgumentException}
     * @param object any Object
     * @param message the text message that would be passed to the exception thrown when {@code o == null}.
     * @return the same object
     * @throws IllegalArgumentException if a {@code o == null}
     */
    public static <T> T nonNull(final T object, final Supplier<String> message) {
        if (object == null) {
            throw new IllegalArgumentException(message.get());
        }
        return object;
    }


    /**
     * Checks that a {@link Collection} is not {@code null} and that it is not empty.
     * If it's non-null and non-empty it returns the input, otherwise it throws an {@link IllegalArgumentException}
     * @param collection any Collection
     * @param message a message to include in the output
     * @return the original collection
     * @throws IllegalArgumentException if collection is null or empty
     */
    public static <I, T extends Collection<I>> T nonEmpty(T collection, String message){
        nonNull(collection, "The collection is null: " + message);
        if(collection.isEmpty()){
            throw new IllegalArgumentException("The collection is empty: " + message);
        } else {
            return collection;
        }
    }

    /**
     * Checks that a {@link Collection} is not {@code null} and that it is not empty.
     * If it's non-null and non-empty it returns the true
     * @param collection any Collection
     * @return true if the collection exists and has elements
     */
    public static boolean isNonEmpty(Collection<?> collection){
        return collection != null && !collection.isEmpty();
    }

    /**
     * Checks that a {@link String} is not {@code null} and that it is not empty.
     * If it's non-null and non-empty it returns the input, otherwise it throws an {@link IllegalArgumentException}
     * @param string any String
     * @param message a message to include in the output
     * @return the original string
     * @throws IllegalArgumentException if string is null or empty
     */
    public static String nonEmpty(String string, String message){
        nonNull(string, "The string is null: " + message);
        if(string.isEmpty()){
            throw new IllegalArgumentException("The string is empty: " + message);
        } else {
            return string;
        }
    }

    /**
     * Checks that a {@link String} is not {@code null} and that it is not empty.
     * If it's non-null and non-empty it returns the input, otherwise it throws an {@link IllegalArgumentException}
     * @param string any String
     * @return the original string
     * @throws IllegalArgumentException if string is null or empty
     */
    public static String nonEmpty(final String string){
        return nonEmpty(string, "string must not be null or empty");
    }

    /**
     * Checks that a {@link Collection} is not {@code null} and that it is not empty.
     * If it's non-null and non-empty it returns the input, otherwise it throws an {@link IllegalArgumentException}
     * @param collection any Collection
     * @return the original collection
     * @throws IllegalArgumentException if collection is null or empty
     */
    public static  <I, T extends Collection<I>> T nonEmpty(T collection){
        return nonEmpty(collection, "collection must not be null or empty.");
    }

    public static void validateArg(final boolean condition, final String msg){
        if (!condition){
            throw new IllegalArgumentException(msg);
        }
    }

    public static void validateArg(final boolean condition, final Supplier<String> msg){
        if (!condition){
            throw new IllegalArgumentException(msg.get());
        }
    }
}
