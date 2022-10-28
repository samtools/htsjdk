package htsjdk.variant.variantcontext;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Unit tests for VariantContextComparator.
 */
public class VariantContextComparatorUnitTest extends HtsjdkTest {
    private Allele refA, altG, altT;

    @BeforeSuite
    public void before() {
        refA = Allele.create("A", true);
        altG = Allele.create("G", false);
        altT = Allele.create("T", false);
    }

    @Test
    public void testVariantContextsWithSameSiteSortLexicographicallyByAlleleIdentical() {
        final String contig = "chr1";
        final VariantContextComparator comparator = new VariantContextComparator(Collections.singletonList(contig));
        final VariantContextBuilder builder = new VariantContextBuilder("test", contig, 1, 1, Collections.emptyList());

        final VariantContext variant1 = builder.alleles(Arrays.asList(refA, altG)).make();
        final VariantContext variant2 = builder.alleles(Arrays.asList(refA, altG)).make();

        final int compare = comparator.compare(variant1, variant2);
        Assert.assertEquals(compare, 0); // TODO: What other criteria might we sort by to break this tie?
    }

    @Test
    public void testVariantContextsWithSameSiteSortLexicographicallyByAllele() {
        final String contig = "chr1";
        final VariantContextComparator comparator = new VariantContextComparator(Collections.singletonList(contig));
        final VariantContextBuilder builder = new VariantContextBuilder("test", contig, 1, 1, Collections.emptyList());

        final VariantContext variant1 = builder.alleles(Arrays.asList(refA, altG)).make();
        final VariantContext variant2 = builder.alleles(Arrays.asList(refA, altT)).make();

        final int compare = comparator.compare(variant1, variant2);
        Assert.assertEquals(compare, -1);
    }

    @Test
    public void testVariantContextsWithSameSiteSortLexicographicallyByAlleleThenExtraAllelesForFirstVariant() {
        final String contig = "chr1";
        final VariantContextComparator comparator = new VariantContextComparator(Collections.singletonList(contig));
        final VariantContextBuilder builder = new VariantContextBuilder("test", contig, 1, 1, Collections.emptyList());

        final VariantContext variant1 = builder.alleles(Arrays.asList(refA, altG, altT)).make();
        final VariantContext variant2 = builder.alleles(Arrays.asList(refA, altG)).make();

        final int compare = comparator.compare(variant1, variant2);
        Assert.assertEquals(compare, 1);
    }

    @Test
    public void testVariantContextsWithSameSiteSortLexicographicallyByAlleleThenExtraAllelesForSecondVariant() {
        final String contig = "chr1";
        final VariantContextComparator comparator = new VariantContextComparator(Collections.singletonList(contig));
        final VariantContextBuilder builder = new VariantContextBuilder("test", contig, 1, 1, Collections.emptyList());

        final VariantContext variant1 = builder.alleles(Arrays.asList(refA, altG)).make();
        final VariantContext variant2 = builder.alleles(Arrays.asList(refA, altG, altT)).make();

        final int compare = comparator.compare(variant1, variant2);
        Assert.assertEquals(compare, -1);
    }
}
