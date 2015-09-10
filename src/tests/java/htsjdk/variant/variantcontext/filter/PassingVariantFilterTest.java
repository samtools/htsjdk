package htsjdk.variant.variantcontext.filter;

import htsjdk.variant.variantcontext.Allele;
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

/**
 * Created by farjoun on 9/10/15.
 */
public class PassingVariantFilterTest {
    Allele refA = Allele.create("A", true);
    Allele G = Allele.create("G", false);

    @DataProvider()
    public Iterator<Object[]> variantProvider() {

        final VariantContextBuilder vc_builder = new VariantContextBuilder("test", "chr1", 1, 1, Arrays.asList(refA, G));
        final List<Object[]> variants = new ArrayList<Object[]>(10);

        // unfiltered
        variants.add(new Object[]{vc_builder.alleles(Arrays.asList(refA, G)).make(), true});
        // passing
        variants.add(new Object[]{vc_builder.alleles(Arrays.asList(refA, G)).passFilters().make(), true});

        // failing
        variants.add(new Object[]{vc_builder.alleles(Arrays.asList(refA, G)).filters(Collections.singleton("FILTER")).make(), false});

        return variants.iterator();
    }

    @Test(dataProvider = "variantProvider")
    public void testPassingVariantFilter(final VariantContext vc, final boolean shouldPass) {
        final PassingVariantFilter passingVariantFilter = new PassingVariantFilter();

        Assert.assertEquals(passingVariantFilter.test(vc), shouldPass, vc.toString());
    }
}