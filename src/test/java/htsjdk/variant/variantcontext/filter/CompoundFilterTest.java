package htsjdk.variant.variantcontext.filter;

import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Created by farjoun on 9/9/15.
 */
public class CompoundFilterTest {

    static AllPassFilter pass = new AllPassFilter();
    static AllFailFilter fail = new AllFailFilter();

    static Allele refA = Allele.create("A", true);
    static Allele G = Allele.create("G", false);

    static VariantContext vc = new VariantContextBuilder("dummy", "chr1", 1, 1, Arrays.asList(refA, G)).make();

    @DataProvider
    Iterator<Object[]> testCompoundFilterProvider() {
        final List<Object[]> filters = new ArrayList<Object[]>(10);

        // requireAll = TRUE
        { // all pass
            final CompoundFilter compoundFilter = new CompoundFilter(true);
            compoundFilter.add(pass);
            compoundFilter.add(pass);
            compoundFilter.add(pass);
            filters.add(new Object[]{compoundFilter, true});
        }
        { // one fail
            final CompoundFilter compoundFilter = new CompoundFilter(true);
            compoundFilter.add(pass);
            compoundFilter.add(fail);
            compoundFilter.add(pass);
            filters.add(new Object[]{compoundFilter, false});
        }
        { // empty
            final CompoundFilter compoundFilter = new CompoundFilter(true);
            filters.add(new Object[]{compoundFilter, true});
        }

        //requireAll = FALSE
        { // all fail
            final CompoundFilter compoundFilter = new CompoundFilter(false);
            compoundFilter.add(fail);
            compoundFilter.add(fail);
            compoundFilter.add(fail);
            filters.add(new Object[]{compoundFilter, false});
        }
        { // one fail
            final CompoundFilter compoundFilter = new CompoundFilter(false);
            compoundFilter.add(pass);
            compoundFilter.add(fail);
            compoundFilter.add(pass);
            filters.add(new Object[]{compoundFilter, true});
        }
        { // empty
            final CompoundFilter compoundFilter = new CompoundFilter(false);
            filters.add(new Object[]{compoundFilter, true});
        }
        return filters.iterator();
    }

    @Test(dataProvider = "testCompoundFilterProvider")
    public void testCompoundFilter(final VariantContextFilter filter, final boolean shouldPass) {
        Assert.assertEquals(filter.test(vc), shouldPass, filter.toString());
    }
}