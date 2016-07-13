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
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GenotypeBuilderTest extends VariantBaseTest {

    @Test
    public void testMakeWithShallowCopy() {
        final GenotypeBuilder gb = new GenotypeBuilder("test");
        final List<Allele> alleles = new ArrayList<>(
                Arrays.asList(Allele.create("A", true), Allele.create("T")));
        final int[] ad = new int[]{1,5};
        final int[] pl = new int[]{1,6};
        final int[] first = new int[]{1, 2};
        final int[] second = new int[]{3, 4};
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

}