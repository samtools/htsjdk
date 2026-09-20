/*
 * Copyright (c) 2016 The Broad Institute
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

package htsjdk.variant.variantcontext;

import htsjdk.variant.VariantBaseTest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

public class GenotypeBuilderTest extends VariantBaseTest {

    @Test
    public void testMakeWithShallowCopy() {
        final GenotypeBuilder gb = new GenotypeBuilder("test");
        final List<Allele> alleles = new ArrayList<>(Arrays.asList(Allele.create("A", true), Allele.create("T")));
        final int[] ad = new int[] {1, 5};
        final int[] pl = new int[] {1, 6};
        final int[] first = new int[] {1, 2};
        final int[] second = new int[] {3, 4};
        final Genotype firstG = gb.alleles(alleles).attribute("first", first).makeWithShallowCopy();
        final Genotype secondG = gb.AD(ad).PL(pl).attribute("second", second).makeWithShallowCopy();
        // both genotypes have the first field
        Assert.assertEquals(first, firstG.getExtendedAttribute("first"));
        Assert.assertEquals(first, secondG.getExtendedAttribute("first"));
        // both genotypes have the the alleles
        Assert.assertEquals(alleles, firstG.getAlleles());
        Assert.assertEquals(alleles, secondG.getAlleles());
        // only the second genotype should have the AD field
        Assert.assertNull(firstG.getAD());
        Assert.assertEquals(ad, secondG.getAD());
        // only the second genotype should have the PL field
        Assert.assertNull(firstG.getPL());
        Assert.assertEquals(pl, secondG.getPL());
        // only the second genotype should have the second field
        Assert.assertNull(firstG.getExtendedAttribute("second"));
        Assert.assertEquals(second, secondG.getExtendedAttribute("second"));
        // modification of alleles does not change the genotypes
        alleles.add(Allele.create("C"));
        Assert.assertNotEquals(alleles, firstG.getAlleles());
        Assert.assertNotEquals(alleles, secondG.getAlleles());
        // modification of ad or pl does not change the genotypes
        ad[0] = 0;
        pl[0] = 10;
        Assert.assertNotEquals(ad, secondG.getAD());
        Assert.assertNotEquals(pl, secondG.getPL());
    }

    // per-allele phasing

    private static final Allele REF = Allele.create("A", true);
    private static final Allele ALT1 = Allele.create("C");
    private static final Allele ALT2 = Allele.create("G");

    private static Genotype withAllelePhasing(final List<Allele> alleles, final boolean... allelePhasing) {
        return new GenotypeBuilder("s", alleles).allelePhasing(allelePhasing).make();
    }

    @Test
    public void mixedSeparatorsAreKeptPerAllele() {
        final Genotype g = withAllelePhasing(Arrays.asList(REF, ALT1, ALT2), false, false, true);
        Assert.assertTrue(g.hasPerAllelePhasing());
        Assert.assertFalse(g.isAllelePhased(0));
        Assert.assertFalse(g.isAllelePhased(1));
        Assert.assertTrue(g.isAllelePhased(2));
        Assert.assertTrue(g.isPhased(), "phased if any allele is");
        Assert.assertFalse(g.needsLeadingPhaseIndicator(), "an unphased first allele is what a / elsewhere implies");
    }

    @Test
    public void allPhasedIsStoredAsTheSingleFlag() {
        final Genotype g = withAllelePhasing(Arrays.asList(REF, ALT1), true, true);
        Assert.assertFalse(g.hasPerAllelePhasing());
        Assert.assertTrue(g.isPhased());
        Assert.assertTrue(g.isAllelePhased(0));
        Assert.assertTrue(g.isAllelePhased(1));
    }

    @Test
    public void allUnphasedIsStoredAsTheSingleFlag() {
        final Genotype g = withAllelePhasing(Arrays.asList(REF, ALT1), false, false);
        Assert.assertFalse(g.hasPerAllelePhasing());
        Assert.assertFalse(g.isPhased());
        Assert.assertFalse(g.isAllelePhased(0));
    }

    @Test
    public void aPhasedFirstAlleleBeforeAnUnphasedOneIsKept() {
        final Genotype g = withAllelePhasing(Arrays.asList(REF, ALT1), true, false);
        Assert.assertTrue(g.hasPerAllelePhasing());
        Assert.assertTrue(g.needsLeadingPhaseIndicator());
        Assert.assertTrue(g.isAllelePhased(0));
        Assert.assertFalse(g.isAllelePhased(1));
        Assert.assertTrue(g.isPhased());
    }

    @Test
    public void anUnphasedFirstAlleleBeforeAPhasedOneIsKept() {
        final Genotype g = withAllelePhasing(Arrays.asList(REF, ALT1), false, true);
        Assert.assertTrue(g.hasPerAllelePhasing());
        Assert.assertTrue(g.needsLeadingPhaseIndicator());
        Assert.assertFalse(g.isAllelePhased(0));
        Assert.assertTrue(g.isAllelePhased(1));
    }

    @Test
    public void anExplicitlyUnphasedHaploidAlleleIsKept() {
        final Genotype g = withAllelePhasing(Arrays.asList(ALT1), false);
        Assert.assertTrue(g.hasPerAllelePhasing());
        Assert.assertTrue(g.needsLeadingPhaseIndicator(), "without a leading / this haploid would print as 1");
        Assert.assertFalse(g.isPhased());
    }

    @Test
    public void aPhasedHaploidAlleleIsStoredAsTheSingleFlag() {
        final Genotype g = withAllelePhasing(Arrays.asList(ALT1), true);
        Assert.assertFalse(g.hasPerAllelePhasing());
        Assert.assertTrue(g.isPhased());
    }

    @Test
    public void phasedReplacesAllelePhasing() {
        final Genotype g = new GenotypeBuilder("s", Arrays.asList(REF, ALT1))
                .allelePhasing(new boolean[] {true, false})
                .phased(false)
                .make();
        Assert.assertFalse(g.hasPerAllelePhasing());
        Assert.assertFalse(g.isPhased());
    }

    @Test
    public void copyingAGenotypeKeepsItsAllelePhasing() {
        final Genotype original = withAllelePhasing(Arrays.asList(REF, ALT1, ALT2), false, false, true);
        final Genotype copy = new GenotypeBuilder(original).make();
        Assert.assertTrue(copy.hasPerAllelePhasing());
        Assert.assertFalse(copy.isAllelePhased(1));
        Assert.assertTrue(copy.isAllelePhased(2));
    }

    @Test
    public void aCopyGivenAnotherPloidyKeepsOnlyWhetherItWasPhased() {
        final Genotype original = withAllelePhasing(Arrays.asList(REF, ALT1, ALT2), false, false, true);
        final Genotype diploid =
                new GenotypeBuilder(original).alleles(Arrays.asList(REF, ALT1)).make();
        Assert.assertTrue(diploid.isPhased());
        Assert.assertFalse(diploid.hasPerAllelePhasing());
        Assert.assertEquals(diploid.getGenotypeString(), "A|C");
    }

    @Test
    public void aCopyGivenOtherAllelesOfTheSamePloidyKeepsItsAllelePhasing() {
        final Genotype original = withAllelePhasing(Arrays.asList(REF, ALT1, ALT2), false, false, true);
        final Genotype copy = new GenotypeBuilder(original)
                .alleles(Arrays.asList(REF, ALT2, ALT1))
                .make();
        Assert.assertEquals(copy.getGenotypeString(), "A/G|C");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void allelePhasingOfTheWrongLengthIsRejectedOnACopyToo() {
        final Genotype original = withAllelePhasing(Arrays.asList(REF, ALT1, ALT2), false, false, true);
        new GenotypeBuilder(original)
                .alleles(Arrays.asList(REF, ALT1))
                .allelePhasing(new boolean[] {false, false, true})
                .make();
    }

    @Test
    public void makeWithShallowCopyCopiesTheAllelePhasing() {
        final boolean[] allelePhasing = {false, false, true};
        final Genotype genotype = new GenotypeBuilder("s", Arrays.asList(REF, ALT1, ALT2))
                .allelePhasing(allelePhasing)
                .makeWithShallowCopy();
        allelePhasing[2] = false;
        Assert.assertTrue(genotype.isAllelePhased(2));
    }

    @Test
    public void resetClearsAllelePhasing() {
        final GenotypeBuilder gb =
                new GenotypeBuilder("s", Arrays.asList(REF, ALT1)).allelePhasing(new boolean[] {true, false});
        gb.reset(true);
        Assert.assertFalse(gb.alleles(Arrays.asList(REF, ALT1)).make().hasPerAllelePhasing());
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void allelePhasingOfTheWrongLengthIsRejected() {
        withAllelePhasing(Arrays.asList(REF, ALT1), true, false, true);
    }
}
