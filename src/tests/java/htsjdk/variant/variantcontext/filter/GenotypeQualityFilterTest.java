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

public class GenotypeQualityFilterTest {

    Allele refA = Allele.create("A", true);
    Allele G = Allele.create("G", false);

    @DataProvider
    public Iterator<Object[]> genotypeProvider() {

        final VariantContextBuilder vc_builder = new VariantContextBuilder("testCode", "chr1", 1, 1, Arrays.asList(refA, G));
        final GenotypeBuilder gt_builder = new GenotypeBuilder("test").alleles(Arrays.asList(refA, G));
        final List<Object[]> variants = new ArrayList<Object[]>(10);

        //without gq
        variants.add(new Object[]{vc_builder.genotypes(gt_builder.make()).make(), null, false});
        variants.add(new Object[]{vc_builder.genotypes(gt_builder.make()).make(), "test", false});

        //without sample
        variants.add(new Object[]{vc_builder.genotypes(gt_builder.GQ( 1).make()).make(), null, false});
        variants.add(new Object[]{vc_builder.genotypes(gt_builder.GQ(10).make()).make(), null, true});
        variants.add(new Object[]{vc_builder.genotypes(gt_builder.GQ(20).make()).make(), null, true});

        //with sample
        variants.add(new Object[]{vc_builder.genotypes(gt_builder.GQ( 1).make()).make(), "test", false});
        variants.add(new Object[]{vc_builder.genotypes(gt_builder.GQ(10).make()).make(), "test", true});
        variants.add(new Object[]{vc_builder.genotypes(gt_builder.GQ(20).make()).make(), "test", true});

        return variants.iterator();
    }

    @Test(dataProvider = "genotypeProvider")
    public void testHetFilter(final VariantContext vc, final String sample, final boolean shouldPass) {
        final GenotypeQualityFilter gqFilter = getFilter(sample);

        Assert.assertEquals(gqFilter.test(vc), shouldPass, vc.toString());
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
        final GenotypeQualityFilter gqFilter = getFilter(sample);

        //should fail
        gqFilter.test(vc);
    }

    private GenotypeQualityFilter getFilter(String sample){
        if (sample == null) {
            return new GenotypeQualityFilter(10);
        } else {
            return new GenotypeQualityFilter(10, sample);
        }
    }
}
