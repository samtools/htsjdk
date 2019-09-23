/*
 * The MIT License
 *
 * Copyright (c) 2019 The Broad Institute
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

package htsjdk.samtools.util;

import java.util.function.Consumer;

/**
 * Miscellaneous util methods that don't fit anywhere else.
 */
public class CodeUtil {

    /**
     * Mimic of Oracle's nvl() - returns the first value if not null, otherwise the second value.
     */
    public static <T> T getOrElse(final T value1, final T value2) {
        if (value1 != null) {
            return value1;
        } else {
            return value2;
        }
    }

    /**
     * Applied the Consumer on the input if the input is not null,
     * serves as a way to avoid writing:
     * <code>
     *     if(input != null){
     *         action.apply(input);
     *     }
     * </code>
     * and replacing it with
     * <code>
     *     applyIfNotNull(input,action);
     * </code>
     * @param input a nullable object
     * @param action a Consumer that will be applied to the input if it isn't null.
     * @param <T> the type of the input.
     */
    public static <T> void applyIfNotNull(final T input, Consumer<T> action) {
        if (input != null) {
            action.accept(input);
        }
    }
}
