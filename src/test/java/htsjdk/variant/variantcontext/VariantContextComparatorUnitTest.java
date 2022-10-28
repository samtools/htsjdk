package htsjdk.variant.variantcontext;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.Locatable;
import htsjdk.tribble.SimpleFeature;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for VariantContextComparator.
 */
public class VariantContextComparatorUnitTest extends HtsjdkTest{

    private static final String CHR1 = "chr1" , CHR2 = "chr2";

    @DataProvider
    public Object[][] getVCs(){
        final Locatable chr1_1_1 = new SimpleFeature(CHR1, 1, 1);
        final Locatable chr1_11_11 = new SimpleFeature(CHR1, 11, 11);
        final Locatable chr2_1_1 = new SimpleFeature(CHR2, 1, 1);
        final Locatable chr2_10_10 = new SimpleFeature(CHR2, 10,10);
        final VariantContext chr1_1_1_A_G = make(chr1_1_1, Allele.REF_A, Allele.ALT_G);
        final VariantContext chr2_1_1_A_G = make(chr2_1_1, Allele.REF_A, Allele.ALT_G);

        final VariantContext chr2_10_10_A_G = make(chr2_10_10, Allele.REF_A, Allele.ALT_G);
        final VariantContext chr1_11_11_A_G = make(chr1_11_11, Allele.REF_A, Allele.ALT_G);
        final VariantContext chr1_1_A = make(chr1_1_1, Allele.REF_A);
        return new Object[][] {
                {chr1_1_1_A_G, chr1_1_1_A_G, 0}, //identical should match
                {chr1_1_1_A_G, chr2_1_1_A_G, -1}, //chr2 comes after chr1
                {chr1_1_1_A_G, chr2_10_10_A_G, -1},
                {chr1_11_11_A_G, chr2_10_10_A_G, -1}, //chr before pos
                {chr1_11_11_A_G, chr1_1_1_A_G, 1},
                {chr1_1_1_A_G, make(chr1_1_1, Allele.REF_A, Allele.ALT_T), -1}, //alleles sort lexicographichally
                {make(chr1_1_1, Allele.REF_A, Allele.ALT_G, Allele.ALT_T), chr1_1_1_A_G, 1},//extra alleles sort after fewer
                {chr1_1_1_A_G, make(chr1_1_1, Allele.REF_G, Allele.ALT_A), -1}, //mismatch ref takes precendence
                {make(chr1_1_1, Allele.REF_T, Allele.ALT_C, Allele.create("AAA"), Allele.ALT_G),  // allele order doesn't matter
                        make(chr1_1_1, Allele.REF_T, Allele.ALT_G, Allele.create("AAA"), Allele.ALT_C), 0},
                {chr1_1_1_A_G, chr1_1_A, 1}, // no alt allele
                {chr1_1_A, chr1_1_A, 0} // no alt alleles at all

        };
    }

    private static VariantContext make(Locatable pos, Allele ref, Allele ... alt){
        final List<Allele> alleles = new ArrayList<>();
        alleles.add(ref);
        alleles.addAll(Arrays.asList(alt));
        return new VariantContextBuilder("test", pos.getContig(), pos.getStart(), pos.getEnd(), alleles).make();
    }

    @Test(dataProvider = "getVCs")
    public void testVariantContextComparator(VariantContext left, VariantContext right, int expectedResult){
        final VariantContextComparator comparator = new VariantContextComparator(Arrays.asList(CHR1, CHR2));
        Assert.assertEquals(comparator.compare(left, right), expectedResult);
        Assert.assertEquals(comparator.compare(right, left), -expectedResult); // make sure it's symmetrical
    }
}
