/*
* Copyright (c) 2012 The Broad Institute
* 
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
* 
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package htsjdk.variant;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFConstants;
import org.testng.Assert;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for test classes within org.broadinstitute.variant
 */
public class VariantBaseTest {

    public static final String variantTestDataRoot = new File("src/test/resources/htsjdk/variant/").getAbsolutePath() + "/";

    /**
     * Creates a temp file that will be deleted on exit after tests are complete.
     * @param name Prefix of the file.
     * @param extension Extension to concat to the end of the file.
     * @return A file in the temporary directory starting with name, ending with extension, which will be deleted after the program exits.
     */
    public static File createTempFile(String name, String extension) {
        try {
            File file = File.createTempFile(name, extension);
            file.deleteOnExit();
            return file;
        } catch (IOException ex) {
            throw new RuntimeException("Cannot create temp file: " + ex.getMessage(), ex);
        }
    }

    private static final double DEFAULT_FLOAT_TOLERANCE = 1e-1;

    public static final void assertEqualsDoubleSmart(final Object actual, final Double expected) {
        Assert.assertTrue(actual instanceof Double, "Not a double");
        assertEqualsDoubleSmart((double)(Double)actual, (double)expected);
    }

    public static final void assertEqualsDoubleSmart(final Object actual, final Double expected, final double tolerance) {
        Assert.assertTrue(actual instanceof Double, "Not a double");
        assertEqualsDoubleSmart((double)(Double)actual, (double)expected, tolerance);
    }

    public static final void assertEqualsDoubleSmart(final double actual, final double expected) {
        assertEqualsDoubleSmart(actual, expected, DEFAULT_FLOAT_TOLERANCE);
    }

    public static final <T> void assertEqualsSet(final Set<T> actual, final Set<T> expected, final String info) {
        final Set<T> actualSet = new HashSet<T>(actual);
        final Set<T> expectedSet = new HashSet<T>(expected);
        Assert.assertTrue(actualSet.equals(expectedSet), info); // note this is necessary due to testng bug for set comps
    }

    public static void assertEqualsDoubleSmart(final double actual, final double expected, final double tolerance) {
        assertEqualsDoubleSmart(actual, expected, tolerance, null);
    }

    public static void assertEqualsDoubleSmart(final double actual, final double expected, final double tolerance, final String message) {
        if ( Double.isNaN(expected) ) // NaN == NaN => false unfortunately
            Assert.assertTrue(Double.isNaN(actual), "expected is nan, actual is not");
        else if ( Double.isInfinite(expected) ) // NaN == NaN => false unfortunately
            Assert.assertTrue(Double.isInfinite(actual), "expected is infinite, actual is not");
        else {
            final double delta = Math.abs(actual - expected);
            final double ratio = Math.abs(actual / expected - 1.0);
            Assert.assertTrue(delta < tolerance || ratio < tolerance, "expected = " + expected + " actual = " + actual
                    + " not within tolerance " + tolerance
                    + (message == null ? "" : "message: " + message));
        }
    }

    public static SAMSequenceDictionary createArtificialSequenceDictionary() {
        final int[] contigLengths = { 249250621, 243199373, 198022430, 191154276, 180915260, 171115067, 159138663, 146364022,
                                      141213431, 135534747, 135006516, 133851895, 115169878, 107349540, 102531392, 90354753,
                                      81195210, 78077248, 59128983, 63025520, 48129895, 51304566, 155270560, 59373566, 16569 };
        List<SAMSequenceRecord> contigs = new ArrayList<SAMSequenceRecord>();

        for ( int contig = 1; contig <= 22; contig++ ) {
            contigs.add(new SAMSequenceRecord(Integer.toString(contig), contigLengths[contig - 1]));
        }

        int position = 22;
        for ( String contigName : Arrays.asList("X", "Y", "MT") ) {
            contigs.add(new SAMSequenceRecord(contigName, contigLengths[position]));
            position++;
        }

        return new SAMSequenceDictionary(contigs);
    }

    /**
     * Asserts that the two provided VariantContext objects are equal.
     *
     * @param actual actual VariantContext object
     * @param expected expected VariantContext to compare against
     */
    public static void assertVariantContextsAreEqual( final VariantContext actual, final VariantContext expected ) {
        Assert.assertNotNull(actual, "VariantContext expected not null");
        Assert.assertEquals(actual.getContig(), expected.getContig(), "chr");
        Assert.assertEquals(actual.getStart(), expected.getStart(), "start");
        Assert.assertEquals(actual.getEnd(), expected.getEnd(), "end");
        Assert.assertEquals(actual.getID(), expected.getID(), "id");
        Assert.assertEquals(actual.getAlleles(), expected.getAlleles(), "alleles for " + expected + " vs " + actual);

        assertAttributesEquals(actual.getAttributes(), expected.getAttributes());
        Assert.assertEquals(actual.filtersWereApplied(), expected.filtersWereApplied(), "filtersWereApplied");
        Assert.assertEquals(actual.isFiltered(), expected.isFiltered(), "isFiltered");
        assertEqualsSet(actual.getFilters(), expected.getFilters(), "filters");
        assertEqualsDoubleSmart(actual.getPhredScaledQual(), expected.getPhredScaledQual());

        Assert.assertEquals(actual.hasGenotypes(), expected.hasGenotypes(), "hasGenotypes");
        if ( expected.hasGenotypes() ) {
            assertEqualsSet(actual.getSampleNames(), expected.getSampleNames(), "sample names set");
            Assert.assertEquals(actual.getSampleNamesOrderedByName(), expected.getSampleNamesOrderedByName(), "sample names");
            final Set<String> samples = expected.getSampleNames();
            for ( final String sample : samples ) {
                assertGenotypesAreEqual(actual.getGenotype(sample), expected.getGenotype(sample));
            }
        }
    }

    /**
     * Asserts that the two provided Genotype objects are equal.
     *
     * @param actual actual Genotype object
     * @param expected expected Genotype object to compare against
     */
    public static void assertGenotypesAreEqual(final Genotype actual, final Genotype expected) {
        Assert.assertEquals(actual.getSampleName(), expected.getSampleName(), "Genotype names");
        Assert.assertEquals(actual.getAlleles(), expected.getAlleles(), "Genotype alleles");
        Assert.assertEquals(actual.getGenotypeString(), expected.getGenotypeString(), "Genotype string");
        Assert.assertEquals(actual.getType(), expected.getType(), "Genotype type");

        // filters are the same
        Assert.assertEquals(actual.getFilters(), expected.getFilters(), "Genotype fields");
        Assert.assertEquals(actual.isFiltered(), expected.isFiltered(), "Genotype isFiltered");

        // inline attributes
        Assert.assertEquals(actual.getDP(), expected.getDP(), "Genotype dp");
        Assert.assertTrue(Arrays.equals(actual.getAD(), expected.getAD()));
        Assert.assertEquals(actual.getGQ(), expected.getGQ(), "Genotype gq");
        Assert.assertEquals(actual.hasPL(), expected.hasPL(), "Genotype hasPL");
        Assert.assertEquals(actual.hasAD(), expected.hasAD(), "Genotype hasAD");
        Assert.assertEquals(actual.hasGQ(), expected.hasGQ(), "Genotype hasGQ");
        Assert.assertEquals(actual.hasDP(), expected.hasDP(), "Genotype hasDP");

        Assert.assertEquals(actual.hasLikelihoods(), expected.hasLikelihoods(), "Genotype haslikelihoods");
        Assert.assertEquals(actual.getLikelihoodsString(), expected.getLikelihoodsString(), "Genotype getlikelihoodsString");
        Assert.assertEquals(actual.getLikelihoods(), expected.getLikelihoods(), "Genotype getLikelihoods");
        Assert.assertTrue(Arrays.equals(actual.getPL(), expected.getPL()));

        Assert.assertEquals(actual.getGQ(), expected.getGQ(), "Genotype phredScaledQual");
        assertAttributesEquals(actual.getExtendedAttributes(), expected.getExtendedAttributes());
        Assert.assertEquals(actual.isPhased(), expected.isPhased(), "Genotype isPhased");
        Assert.assertEquals(actual.getPloidy(), expected.getPloidy(), "Genotype getPloidy");
    }

    /**
     * Asserts that the two sets of attribute mappings are equal. Ignores null-valued attributes in
     * "actual" that are not present in "expected" while performing the comparison.
     *
     * @param actual actual mapping of attributes
     * @param expected expected mapping of attributes
     */
    private static void assertAttributesEquals(final Map<String, Object> actual, Map<String, Object> expected) {
        final Set<String> expectedKeys = new HashSet<String>(expected.keySet());

        for ( final Map.Entry<String, Object> act : actual.entrySet() ) {
            final Object actualValue = act.getValue();
            if ( expected.containsKey(act.getKey()) && expected.get(act.getKey()) != null ) {
                final Object expectedValue = expected.get(act.getKey());
                if ( expectedValue instanceof List ) {
                    final List<Object> expectedList = (List<Object>)expectedValue;
                    Assert.assertTrue(actualValue instanceof List, act.getKey() + " should be a list but isn't");
                    final List<Object> actualList = (List<Object>)actualValue;
                    Assert.assertEquals(actualList.size(), expectedList.size(), act.getKey() + " size");
                    for ( int i = 0; i < expectedList.size(); i++ ) {
                        assertAttributeEquals(act.getKey(), actualList.get(i), expectedList.get(i));
                    }
                }
                else {
                    assertAttributeEquals(act.getKey(), actualValue, expectedValue);
                }
            }
            else {
                // it's ok to have a binding in x -> null that's absent in y
                Assert.assertNull(actualValue, act.getKey() + " present in one but not in the other");
            }
            expectedKeys.remove(act.getKey());
        }

        // now expectedKeys contains only the keys found in expected but not in actual,
        // and they must all be null
        for ( final String missingExpected : expectedKeys ) {
            final Object value = expected.get(missingExpected);
            Assert.assertTrue(isMissingAttribute(value), "Attribute " + missingExpected + " missing in one but not in other" );
        }
    }

    /**
     * Asserts that the two provided attribute values are equal. If the values are Doubles, uses a
     * more lenient comparision with a tolerance of 1e-2.
     *
     * @param key key for the attribute values
     * @param actual actual attribute value
     * @param expected expected attribute value against which to compare
     */
    private static void assertAttributeEquals(final String key, final Object actual, final Object expected) {
        if ( expected instanceof Double ) {
            // must be very tolerant because doubles are being rounded to 2 sig figs
            assertEqualsDoubleSmart(actual, (Double) expected, 1e-2);
        }
        else {
            Assert.assertEquals(actual, expected, "Attribute " + key);
        }
    }

    /**
     * Determines whether the provided attribute value is missing according to the VCF spec.
     * An attribute value is missing if it's null, is equal to {@link VCFConstants#MISSING_VALUE_v4},
     * or if it's a List that is either empty or contains only null values.
     *
     * @param value attribute value to test
     * @return true if value is a missing VCF attribute value, otherwise false
     */
    private static boolean isMissingAttribute(final Object value) {
        if ( value == null || value.equals(VCFConstants.MISSING_VALUE_v4) ) {
            return true;
        }
        else if ( value instanceof List ) {
            // handles the case where all elements are null or the list is empty
            for ( final Object elt : (List)value) {
                if (elt != null) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

}
