/*
 * The MIT License
 *
 * Copyright (c) 2015 The Broad Institute
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
package htsjdk.variant.variantcontext.filter;

import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class HeterozygosityFilterTest {

    Allele refA = Allele.create("A", true);
    Allele G = Allele.create("G", false);

    @DataProvider(name = "Hets")
    public Iterator<Object[]> hetsProvider() {

        final VariantContextBuilder vc_builder = new VariantContextBuilder("testCode", "chr1", 1, 1, Arrays.asList(refA, G));
        final GenotypeBuilder gt_builder = new GenotypeBuilder("test");
        final List<Object[]> hets = new ArrayList<Object[]>(10);

        hets.add(new Object[]{vc_builder.genotypes(gt_builder.alleles(Arrays.asList(refA, G)).make()).make(), null, true});
        hets.add(new Object[]{vc_builder.genotypes(gt_builder.alleles(Arrays.asList(refA, G)).make()).make(), "test", true});

        //non-variant
        hets.add(new Object[]{vc_builder.genotypes(gt_builder.alleles(Collections.singletonList(refA)).make()).make(), "test", false});
        hets.add(new Object[]{vc_builder.genotypes(gt_builder.alleles(Collections.singletonList(refA)).make()).make(), null, false});

        return hets.iterator();
    }

    @Test(dataProvider = "Hets")
    public void testHetFilter(final VariantContext vc, final String sample, final boolean shouldPass) {
        final HeterozygosityFilter hf = getFilter(shouldPass, sample);

        Assert.assertTrue(hf.test(vc));
    }

    @DataProvider(name = "badSamplesProvider")
    public Iterator<Object[]> badSamplesProvider() {

        final VariantContextBuilder vc_builder = new VariantContextBuilder("testCode", "chr1", 1, 1, Arrays.asList(refA, G));
        final GenotypeBuilder gt_builder = new GenotypeBuilder();
        final List<Object[]> hets = new ArrayList<Object[]>(10);

        hets.add(new Object[]{vc_builder.make(), null});
        hets.add(new Object[]{vc_builder.genotypes(Arrays.asList(gt_builder.name("test1").make(), gt_builder.name("test2").make())).make(), "notNull"});
        hets.add(new Object[]{vc_builder.genotypes(Collections.singleton(gt_builder.name("This").make())).make(), "That"});

        return hets.iterator();
    }

    @Test(dataProvider = "badSamplesProvider", expectedExceptions = IllegalArgumentException.class)
    public void testbadSample(final VariantContext vc, final String sample) {
        final HeterozygosityFilter hf = getFilter(true, sample);

        //should fail
        hf.test(vc);
    }

    @DataProvider(name = "variantsProvider")
    public Object[][] variantsProvider() {

        final VariantContextBuilder vc_builder = new VariantContextBuilder("testCode", "chr1", 1, 1, Arrays.asList(refA, G));
        final GenotypeBuilder gt_builder = new GenotypeBuilder("test");
        final List<VariantContext> vcs = new ArrayList<VariantContext>(10);

        //hets:
        vcs.add(vc_builder.genotypes(gt_builder.alleles(Arrays.asList(refA, G)).make()).make());
        vcs.add(vc_builder.loc("chr1", 10, 10).genotypes(gt_builder.alleles(Arrays.asList(refA, G)).make()).make());

        //non-variant:
        vcs.add(vc_builder.loc("chr1", 20, 20).genotypes(gt_builder.alleles(Collections.singletonList(refA)).make()).make());
        vcs.add(vc_builder.loc("chr1", 30, 30).genotypes(gt_builder.alleles(Collections.singletonList(refA)).make()).make());

        return new Object[][]{new Object[]{vcs.iterator(), new int[]{1, 10}}};
    }

    @Test(dataProvider = "variantsProvider")
    public void testFilteringIterator(final Iterator<VariantContext> vcs, final int[] passingPositions) {
        final Iterator<VariantContext> filteringIterator = new FilteringVariantContextIterator(vcs, new HeterozygosityFilter(true, "test"));

        int i = 0;
        while (filteringIterator.hasNext()) {
            final VariantContext vc = filteringIterator.next();
            Assert.assertTrue(i < passingPositions.length);
            Assert.assertEquals(vc.getStart(), passingPositions[i++]);
        }
    }

    private HeterozygosityFilter getFilter(final boolean shouldPass, String sample) {
        if (sample == null) {
            return new HeterozygosityFilter(shouldPass);
        } else {
            return new HeterozygosityFilter(shouldPass, sample);
        }
    }
}
