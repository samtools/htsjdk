/**
 * ****************************************************************************
 * Copyright 2013 EMBL-EBI
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package htsjdk.samtools.cram.common;

/**
 * Mutable integer class suitable for use with collection classes that take a type parameter.
 */
public final class MutableInt {
    public int value;

    /**
     * Create a mutable integer with initial value 0.
     */
    public MutableInt() {
        this(0);
    }

    /**
     * Create a mutable integer with initial value {@code initialValue}.
     * @param initialValue
     */
    public MutableInt(final int initialValue) {
        value = initialValue;
    }

    /**
     * Increment the current value by {@code initialValue}.
     * @param increment
     * @return The updated object
     */
    public MutableInt incrementValue(final int increment) {
        value += increment;
        return this;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MutableInt that = (MutableInt) o;

        return value == that.value;
    }
}
