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
package htsjdk.samtools;

import java.util.List;

/**
 * Misc methods for SAM-related unit tests.  These are in the src tree rather than the tests tree
 * so that they will be included in sam.jar, and therefore can be used by tests outside of htsjdk.samtools.
 */
public class SAMTestUtil {

    /**
     * Indicates that a required sanity-check condition was not met.
     */
    public static class SanityCheckFailedException extends RuntimeException {
        public SanityCheckFailedException(String message) {
            super(message);
        }
    }

    /**
     * Basic sanity check for a pair of SAMRecords.
     * @throws SanityCheckFailedException if the sanity check failed
     */
    public void assertPairValid(final SAMRecord firstEnd, final SAMRecord secondEnd) throws SanityCheckFailedException {
        assertEquals(firstEnd.getReadName(), secondEnd.getReadName());
        assertTrue(firstEnd.getFirstOfPairFlag());
        assertTrue(secondEnd.getSecondOfPairFlag());
        assertFalse(secondEnd.getFirstOfPairFlag());
        assertFalse(firstEnd.getSecondOfPairFlag());
        if (!firstEnd.getReadUnmappedFlag() && !secondEnd.getReadUnmappedFlag()) {
            assertNotSame(firstEnd.getReadNegativeStrandFlag(),
                    secondEnd.getReadNegativeStrandFlag());
        }
    }

    /**
     * Basic sanity check for a SAMRecord. Print errors to screen.
     * @param read SAM record
     * @throws IllegalArgumentException if read is null
     * @throws SanityCheckFailedException if errors
     */
    public static void assertReadValid(final SAMRecord read) throws SanityCheckFailedException {
        if (read == null) {
            throw new IllegalArgumentException("SAMRecord is null");
        }

        final List<SAMValidationError> errors = read.isValid(false);
        if ( errors != null) {
            errors.forEach(v -> System.out.println(v.toString()));
        }
        assertTrue(errors.isEmpty());
    }

    private static <T> void assertEquals(T a, T b) {
        if (a == null) {
            if (b != null) {
                throw new SanityCheckFailedException("\"" + a + "\" does not equal \"" + b + "\"");
            }
        } else if (!a.equals(b)) {
            throw new SanityCheckFailedException("\"" + a + "\" does not equal \"" + b + "\"");
        }
    }

    private static <T> void assertNotSame(T a, T b) {
        if (a != b) {
            throw new SanityCheckFailedException("\"" + a + "\" and \"" + b + "\" are not the same object");
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new SanityCheckFailedException("The condition is false");
        }
    }

    private static void assertFalse(boolean condition) {
        if (condition) {
            throw new SanityCheckFailedException("The condition is true");
        }
    }
}
