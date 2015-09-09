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
 * Created by farjoun on 9/9/15.
 */
public class SnpFilterTest {
    Allele refA = Allele.create("A", true);
    Allele refAG = Allele.create("AG", true);

    Allele G = Allele.create("G", false);
    Allele T = Allele.create("T", false);
    Allele AG = Allele.create("AG", false);
    Allele AT = Allele.create("AT", false);
    Allele star = Allele.create("<*>", false);


    @DataProvider()
    public Iterator<Object[]> variantProvider() {

        final VariantContextBuilder vc_builder = new VariantContextBuilder("testCode", "chr1", 1, 1, Collections.<Allele>emptyList());
        final List<Object[]> variants = new ArrayList<Object[]>(10);

        variants.add(new Object[]{vc_builder.alleles(Arrays.asList(refA, G))         .make(), true});    // SNP
        variants.add(new Object[]{vc_builder.alleles(Arrays.asList(refA, G, T))      .make(), true});    // SNP

        variants.add(new Object[]{vc_builder.alleles(Arrays.asList(refA, AG))         .make(), false}); // INDEL
        variants.add(new Object[]{vc_builder.alleles(Arrays.asList(refA, G, AG))      .make(), false}); // MIXED
        variants.add(new Object[]{vc_builder.alleles(Arrays.asList(refA, star))       .make(), false}); // SYMBOLIC
        variants.add(new Object[]{vc_builder.stop(2).alleles(Arrays.asList(refAG, T)) .make(), false}); // INDEL
        variants.add(new Object[]{vc_builder.stop(2).alleles(Arrays.asList(refAG, AT)).make(), false}); // MNP

        return variants.iterator();
    }

    @Test(dataProvider = "variantProvider")
    public void testSnpFilter(final VariantContext vc, final boolean shouldPass) {
        final SnpFilter snpFilter = new SnpFilter();

        Assert.assertEquals(snpFilter.test(vc), shouldPass, vc.toString());
    }
}
