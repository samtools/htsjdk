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

package htsjdk.variant.bcf2;

import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.utils.GeneralUtils;
import htsjdk.variant.vcf.VCFContigHeaderLine;
import htsjdk.variant.vcf.VCFFilterHeaderLine;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineCount;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Tests for BCF2Utils
 */
public final class BCF2UtilsUnitTest extends VariantBaseTest {

    /**
     * Wrapper class for HeaderOrderTestProvider test cases to prevent TestNG from calling toString()
     * on the VCFHeaders and spamming the log output.
     */
    private static class HeaderOrderTestCase {
        public final VCFHeader inputHeader;
        public final VCFHeader testHeader;
        public final boolean expectedConsistent;

        public HeaderOrderTestCase(final VCFHeader inputHeader, final VCFHeader testHeader, final boolean expectedConsistent) {
            this.inputHeader = inputHeader;
            this.testHeader = testHeader;
            this.expectedConsistent = expectedConsistent;
        }
    }

    @DataProvider(name = "HeaderOrderTestProvider")
    public Object[][] makeHeaderOrderTestProvider() {
        final List<VCFHeaderLine> inputLines = new ArrayList<>();
        final List<VCFHeaderLine> extraLines = new ArrayList<>();

        int counter = 0;
        inputLines.add(new VCFFilterHeaderLine("l" + counter++));
        inputLines.add(new VCFFilterHeaderLine("l" + counter++));
        inputLines.add(new VCFContigHeaderLine(Collections.singletonMap("ID", "l" + counter++), counter));
        inputLines.add(new VCFContigHeaderLine(Collections.singletonMap("ID", "l" + counter++), counter));
        inputLines.add(new VCFInfoHeaderLine("l" + counter++, VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "x"));
        inputLines.add(new VCFInfoHeaderLine("l" + counter++, VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "x"));
        inputLines.add(new VCFFormatHeaderLine("l" + counter++, VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "x"));
        inputLines.add(new VCFFormatHeaderLine("l" + counter++, VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "x"));
        final int inputLineCounter = counter;
        final VCFHeader inputHeader = new VCFHeader(new LinkedHashSet<>(inputLines));

        extraLines.add(new VCFFilterHeaderLine("l" + counter++));
        extraLines.add(new VCFContigHeaderLine(Collections.singletonMap("ID", "l" + counter++), counter));
        extraLines.add(new VCFInfoHeaderLine("l" + counter++, VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "x"));
        extraLines.add(new VCFFormatHeaderLine("l" + counter++, VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "x"));
        extraLines.add(new VCFHeaderLine("x", "misc"));
        extraLines.add(new VCFHeaderLine("y", "misc"));

        final List<Object[]> tests = new ArrayList<>();
        for (final int extrasToTake : Arrays.asList(0, 1, 2, 3)) {
            final List<VCFHeaderLine> empty = Collections.emptyList();
            final List<List<VCFHeaderLine>> permutations = extrasToTake == 0
                ? Collections.singletonList(empty)
                : GeneralUtils.makePermutations(extraLines, extrasToTake, false);
            for (final List<VCFHeaderLine> permutation : permutations) {
                for (int i = -1; i < inputLines.size(); i++) {
                    final List<VCFHeaderLine> allLines = new ArrayList<>(inputLines);
                    if (i >= 0)
                        allLines.remove(i);
                    allLines.addAll(permutation);
                    final VCFHeader testHeader = new VCFHeader(new LinkedHashSet<>(allLines));
                    final boolean expectedConsistent = expectedConsistent(testHeader, inputLineCounter);
                    tests.add(new Object[]{new HeaderOrderTestCase(inputHeader, testHeader, expectedConsistent)});
                }
            }
        }

        // sample name tests
        final List<List<String>> sampleNameTests = Arrays.asList(
            new ArrayList<>(),
            Collections.singletonList("A"),
            Arrays.asList("A", "B"),
            Arrays.asList("A", "B", "C"));
        for (final List<String> inSamples : sampleNameTests) {
            for (final List<String> testSamples : sampleNameTests) {
                final VCFHeader inputHeaderWithSamples = new VCFHeader(inputHeader.getMetaDataInInputOrder(), inSamples);

                final List<List<String>> permutations = testSamples.isEmpty()
                    ? Collections.singletonList(testSamples)
                    : GeneralUtils.makePermutations(testSamples, testSamples.size(), false);
                for (final List<String> testSamplesPermutation : permutations) {
                    final VCFHeader testHeaderWithSamples = new VCFHeader(inputHeader.getMetaDataInInputOrder(), testSamplesPermutation);
                    final boolean expectedConsistent = testSamples.equals(inSamples);
                    tests.add(new Object[]{new HeaderOrderTestCase(inputHeaderWithSamples, testHeaderWithSamples, expectedConsistent)});
                }
            }
        }

        return tests.toArray(new Object[][]{});
    }

    private static boolean expectedConsistent(final VCFHeader combinationHeader, final int minCounterForInputLines) {
        final List<Integer> ids = new ArrayList<Integer>();
        for ( final VCFHeaderLine line : combinationHeader.getMetaDataInInputOrder() ) {
            if ( line.isIDHeaderLine()) {
                // Substring to strip off "l" prefix
                ids.add(Integer.valueOf(line.getID().substring(1)));
            }
        }

        // as long as the start contains all of the ids up to minCounterForInputLines in order
        for (int i = 0; i < minCounterForInputLines; i++)
            if (i >= ids.size() || ids.get(i) != i)
                return false;

        return true;
    }

    //
    // Test to make sure that we detect correctly the case where we can preserve the genotypes data in a BCF2
    // even when the header file is slightly different
    //
    @Test(dataProvider = "HeaderOrderTestProvider")
    public void testHeaderOrder(final HeaderOrderTestCase testCase) {
        final boolean actualOrderConsistency = BCF2Utils.headerLinesAreOrderedConsistently(testCase.testHeader, testCase.inputHeader);
        Assert.assertEquals(actualOrderConsistency, testCase.expectedConsistent);
    }
}
