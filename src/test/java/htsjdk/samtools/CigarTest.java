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

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

/**
 * @author alecw@broadinstitute.org
 */
public class CigarTest {

    @DataProvider(name = "positiveTestsData")
    public Object[][] testPositive() {
        return new Object[][]{
                {""},
                {"2M1P4M1P2D1P6D"},
                {"10M5N1I12M"},
                {"10M1I5N1I12M"},
                {"9M1D5N1I12M"},

                // I followed by D and vice versa is now allowed.
                {"1M1I1D1M"},
                {"1M1D1I1M"},

                // Soft-clip inside of hard-clip now allowed.
                {"29M1S15H"},
        };
    }

    @Test(dataProvider = "positiveTestsData")
    public void testPositive(final String cigar) {
        Assert.assertNull(TextCigarCodec.decode(cigar).isValid(null, -1));
    }

    @DataProvider(name = "negativeTestsData")
    public Object[][] negativeTestsData() {

        return new Object[][]{
                // Cannot have two consecutive insertions (of the same type)
                {"1M1D1D1M", SAMValidationError.Type.ADJACENT_INDEL_IN_CIGAR},
                {"1M1I1I1M", SAMValidationError.Type.ADJACENT_INDEL_IN_CIGAR},

                // Soft clip must be at end of read or inside of hard clip
                {"1M1D1S1M",   SAMValidationError.Type.INVALID_CIGAR},
                {"1M1D1S1M1H", SAMValidationError.Type.INVALID_CIGAR},
                {"1M1D1S1S",   SAMValidationError.Type.INVALID_CIGAR},
                {"1M1D1S1S1H", SAMValidationError.Type.INVALID_CIGAR},
                {"1H1S1S1M1D", SAMValidationError.Type.INVALID_CIGAR},
                {"1S1S1M1D",   SAMValidationError.Type.INVALID_CIGAR},

                // Soft clip must be at end of read or inside of hard clip, but there must be something left
                {"1S1S", SAMValidationError.Type.INVALID_CIGAR},
                {"1H1S", SAMValidationError.Type.INVALID_CIGAR},
                {"1S1H", SAMValidationError.Type.INVALID_CIGAR},
                {"1H1H", SAMValidationError.Type.INVALID_CIGAR},
        };
/*
        // Zero length for an element not allowed. TODO: not sure why this is commented out
       {"100M0D10M1D10M", SAMValidationError.Type.INVALID_CIGAR}
*/
    }

    @Test(dataProvider = "negativeTestsData")
    public void testNegative(final String cigar, final SAMValidationError.Type type) {
        final List<SAMValidationError> errors = TextCigarCodec.decode(cigar).isValid(null, -1);
        Assert.assertEquals(errors.size(), 1, String.format("Got %d error, expected exactly one error.", errors.size()));
        Assert.assertEquals(errors.get(0).getType(), type);
    }
    
    @Test
    public void testMakeCigarFromOperators() {
        final List<CigarOperator> cigarOperators = Arrays.asList(
                CigarOperator.S,
                CigarOperator.M,
                CigarOperator.M,
                CigarOperator.M,
                CigarOperator.I,
                CigarOperator.M,
                CigarOperator.D,
                CigarOperator.M
                );
        final Cigar cigar = Cigar.fromCigarOperators(cigarOperators);
        Assert.assertFalse(cigar.isEmpty());
        Assert.assertEquals(cigar.numCigarElements(), 6);
        Assert.assertEquals(cigar.toString(),"1S3M1I1M1D1M");
        Assert.assertFalse(cigar.containsOperator(CigarOperator.N));
        Assert.assertTrue(cigar.containsOperator(CigarOperator.D));
        Assert.assertTrue(cigar.isLeftClipped());
        Assert.assertFalse(cigar.isRightClipped());
        Assert.assertTrue(cigar.isClipped());
    }
}
