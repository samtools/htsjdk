package htsjdk.variant.variantcontext;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.VariantBaseTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class VariantContextBuilderTest extends VariantBaseTest {
    static final Allele Aref = Allele.REF_A;
    static final Allele Tref = Allele.REF_T;
    static final Allele G = Allele.ALT_G;
    static final Allele C = Allele.ALT_C;

    String snpChr = "chr1";
    String snpSource = "test";
    long snpLocStart = 10;
    long snpLocStop = 10;

    @DataProvider(name = "trueFalse")
    public Object[][] testAttributesWorksTest() {
        return new Object[][]{{true}, {false}};
    }

    @Test(dataProvider = "trueFalse")
    public void testAttributeResettingWorks(final boolean leaveModifyableAsIs) {
        final VariantContextBuilder root1 = new VariantContextBuilder(snpSource, snpChr, snpLocStart, snpLocStop, Arrays.asList(Aref, C));
        final VariantContextBuilder root2 = new VariantContextBuilder(snpSource, snpChr, snpLocStart, snpLocStop, Arrays.asList(Aref, C));

        final VariantContext result1 = root1.attribute("AC", 1).make(leaveModifyableAsIs);

        //this is a red-herring and should not change anything, however, if leaveModifyableAsIs is true, it does change result1.
        final VariantContext ignored = root1.attribute("AC", 2).make(leaveModifyableAsIs);

        final VariantContext result2 = root2.attribute("AC", 1).make(leaveModifyableAsIs);

        if (leaveModifyableAsIs) {
            Assert.assertNotSame(result1.getAttribute("AC"), result2.getAttribute("AC"));
        } else {
            Assert.assertEquals(result1.getAttribute("AC"), result2.getAttribute("AC"));
        }
    }


    @Test()
    public void testBulkAttributeResettingWorks() {
        final VariantContextBuilder root1 = new VariantContextBuilder(snpSource, snpChr, snpLocStart, snpLocStop, Arrays.asList(Aref, C));
        final VariantContext result1 = root1
                .attribute("AC", 1)
                .attribute("AN", 2)
                .make();

        final VariantContextBuilder root2 = new VariantContextBuilder(result1);

        //this is a red-herring and should not change anything.
        final VariantContext ignored = root1.attribute("AC", 2).make();

        final VariantContext result2 = root2.make();

        Assert.assertEquals(result1.getAttribute("AC"), result2.getAttribute("AC"));
        Assert.assertEquals(result1.getAttribute("AN"), result2.getAttribute("AN"));

        final HashMap<String, Object> map = new HashMap<>();
        map.put("AC", 2);
        map.put("AN", 4);
        root2.attributes(map);

        final VariantContext result3 = root2.make();

        Assert.assertEquals(result1.getAttribute("AC"), 1);
        Assert.assertEquals(result1.getAttribute("AN"), 2);

        Assert.assertEquals(result2.getAttribute("AC"), 1);
        Assert.assertEquals(result2.getAttribute("AN"), 2);

        Assert.assertEquals(result3.getAttribute("AC"), 2);
        Assert.assertEquals(result3.getAttribute("AN"), 4);
    }

    enum VCBuilderScheme {
        NEW_ON_BUILDER {
            @Override
            VariantContextBuilder getOtherBuilder(final VariantContextBuilder builder, final VariantContext vc) {
                return new VariantContextBuilder(builder);
            }
        },
        NEW_ON_VC {
            @Override
            VariantContextBuilder getOtherBuilder(final VariantContextBuilder builder, final VariantContext vc) {
                return new VariantContextBuilder(vc);
            }
        },
        SAME {
            @Override
            VariantContextBuilder getOtherBuilder(final VariantContextBuilder builder, final VariantContext vc) {
                return builder;
            }
        };

        abstract VariantContextBuilder getOtherBuilder(final VariantContextBuilder builder, VariantContext vc);
    }

    @DataProvider
    public Object[][] builderSchemes() {
        return new Object[][]{
                {VCBuilderScheme.NEW_ON_BUILDER},
                {VCBuilderScheme.NEW_ON_VC},
                {VCBuilderScheme.SAME}};
    }

    @Test(dataProvider = "builderSchemes")
    public void testAttributeResettingWorks(final VCBuilderScheme builderScheme) {
        final VariantContextBuilder root1 = new VariantContextBuilder(snpSource, snpChr, snpLocStart, snpLocStop, Arrays.asList(Aref, C));

        final VariantContext result1 = root1.attribute("AC", 1).make();
        final VariantContextBuilder root2 = builderScheme.getOtherBuilder(root1, result1)
                .source(snpSource)
                .chr(snpChr)
                .start(snpLocStart)
                .stop(snpLocStop)
                .alleles(Arrays.asList(Aref, C));

        //this is a red-herring and should not change anything.
        final VariantContext ignored = root1.attribute("AC", 2).make();
        final VariantContext result2 = root2.attribute("AC", 1).make();

        Assert.assertEquals(result1.getAttribute("AC"), 1);
        Assert.assertEquals(result2.getAttribute("AC"), 1);
    }

    @Test(dataProvider = "builderSchemes")
    public void testAttributeResettingWorksPreMake1(final VCBuilderScheme builderScheme) {
        final VariantContextBuilder root1 = new VariantContextBuilder(snpSource, snpChr, snpLocStart, snpLocStop, Arrays.asList(Aref, C))
                .attribute("AC", 1);

        final VariantContext result1 = root1.make();

        final VariantContextBuilder root2 = builderScheme.getOtherBuilder(root1, root1.make())
                .source(snpSource)
                .chr(snpChr)
                .start(snpLocStart)
                .stop(snpLocStop)
                .alleles(Arrays.asList(Aref, C));

        //this is a red-herring and should not change anything.
        final VariantContext result2 = root2.attribute("AC", 2).make();

        Assert.assertEquals(result1.getAttribute("AC"), 1);
        Assert.assertEquals(result2.getAttribute("AC"), 2);
    }


    @Test(dataProvider = "builderSchemes")
    public void testAttributeResettingWorksPreMake2(final VCBuilderScheme builderScheme) {
        final VariantContextBuilder root1 = new VariantContextBuilder(snpSource, snpChr, snpLocStart, snpLocStop, Arrays.asList(Aref, C)).attribute("AC", 1);

        final VariantContextBuilder root2 = builderScheme.getOtherBuilder(root1, root1.make())
                .source(snpSource)
                .chr(snpChr)
                .start(snpLocStart)
                .stop(snpLocStop)
                .alleles(Arrays.asList(Aref, C));

        //this is a red-herring and should not change anything.
        final VariantContext result1 = root1.make();
        final VariantContext result2 = root2.attribute("AC", 2).make();

        Assert.assertEquals(result1.getAttribute("AC"), 1);
        Assert.assertEquals(result2.getAttribute("AC"), 2);
    }


    @Test(dataProvider = "builderSchemes")
    public void testAttributeResettingWorks2(final VCBuilderScheme builderScheme) {
        final VariantContextBuilder root1 = new VariantContextBuilder(snpSource, snpChr, snpLocStart, snpLocStop, Arrays.asList(Aref, C))
                .attribute("AC", 1);

        final VariantContext result1 = root1.make();
        final VariantContextBuilder root2 = builderScheme.getOtherBuilder(root1, result1)
                .source(snpSource)
                .chr(snpChr)
                .start(snpLocStart)
                .stop(snpLocStop)
                .alleles(Arrays.asList(Aref, C));

        final VariantContext result2 = root2.attribute("AC", 2).make();

        Assert.assertEquals(result1.getAttribute("AC"), 1);
        Assert.assertEquals(result2.getAttribute("AC"), 2);
    }

    @Test(dataProvider = "builderSchemes")
    public void testAttributeResettingWorks3(final VCBuilderScheme builderScheme) {
        final VariantContextBuilder root1 = new VariantContextBuilder(snpSource, snpChr, snpLocStart, snpLocStop, Arrays.asList(Aref, C));

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("AC", 1);

        final VariantContext result1 = root1.attributes(attributes).make();
        final VariantContextBuilder root2 = builderScheme.getOtherBuilder(root1, result1)
                .source(snpSource).chr(snpChr).start(snpLocStart).stop(snpLocStop).alleles(Arrays.asList(Aref, C));

        attributes.put("AC", 2);

        final VariantContext result2 = root2.attributes(attributes).make();

        Assert.assertEquals(result1.getAttribute("AC"), 1);
        Assert.assertEquals(result2.getAttribute("AC"), 2);
    }

    @Test(dataProvider = "builderSchemes")
    public void testFiltersUnaffectedByClonedVariants(final VCBuilderScheme builderScheme) {
        final VariantContextBuilder builder = new VariantContextBuilder("source", "contig", 1, 1, Arrays.asList(Tref, C)).filter("TEST");
        final VariantContext vc1 = builder.make();
        final VariantContext vc2 = builderScheme.getOtherBuilder(builder, vc1).filters("TEST2").make();
        Assert.assertNotEquals(vc2.getFilters(), vc1.getFilters(), "The two lists of filters should be different");
    }

    @Test(dataProvider = "builderSchemes")
    public void testFilterExternalSetUnaffectedByClonedVariantsBuilders(final VCBuilderScheme builderScheme) {
        final Set<String> filters = new HashSet<>();
        filters.add("TEST");
        final VariantContextBuilder builder = new VariantContextBuilder("source", "contig", 1, 1, Arrays.asList(Tref, C)).filters(filters);
        final VariantContext vc1 = builder.make();

        filters.clear();
        filters.add("TEST2");

        final VariantContext vc2 = builderScheme.getOtherBuilder(builder, vc1).filters(filters).make();
        Assert.assertNotEquals(vc2.getFilters(), vc1.getFilters(), "The two lists of filters should be different");
    }

    @Test
    public void testFilterCanUseUnmodifiableSet() {
        final Set<String> filters = new HashSet<>();
        filters.add("TEST");
        final VariantContextBuilder builder = new VariantContextBuilder("source", "contig", 1, 1, Arrays.asList(Tref, C)).filters(Collections.unmodifiableSet(filters));
        builder.filter("CanIHazAFilter?");
        final VariantContext vc1 = builder.make();

        Assert.assertEquals(vc1.getFilters().size(),2);
    }

    @DataProvider
    public static Object[][] illegalFilterStrings() {
        return new Object[][]{
                {"Tab\t"},
                {"newLine\n"},
                {"space "},
                {"semicolon;"},
                {"carriage return\r"}
        };
    }

    @Test(dataProvider = "illegalFilterStrings", expectedExceptions = TribbleException.class)
    public void testFilterCannotUseBadFilters(final String filter) {
        final Set<String> filters = new HashSet<>();
        filters.add(filter);
        final VariantContextBuilder builder = new VariantContextBuilder("source", "contig", 1, 1, Arrays.asList(Tref, C)).filters(Collections.unmodifiableSet(filters));
        final VariantContext vc1 = builder.make();

        //shouldn't have gotten here
        Assert.fail("a bad filter should have not been permitted: '" + filter + "'");
    }


    @Test(dataProvider = "builderSchemes")
    public void testAllelesUnaffectedByClonedVariants(final VCBuilderScheme builderScheme) {
        final VariantContextBuilder builder = new VariantContextBuilder("source", "contig", 1, 1, Arrays.asList(Tref, C)).filter("TEST");
        final VariantContext ignored = builder.make();
        final VariantContext vc2 = builderScheme.getOtherBuilder(builder, ignored).alleles(Collections.singleton(Aref)).make();

        final VariantContext vc1 = builder.make();

        if (builderScheme == VCBuilderScheme.SAME) {
            Assert.assertEquals(vc2.getAlleles(), vc1.getAlleles(), "The two lists of alleles should be the same");
        } else {
            Assert.assertNotEquals(vc2.getAlleles(), vc1.getAlleles(), "The two lists of alleles should be different");
        }
    }

    @Test(dataProvider = "builderSchemes")
    public void testGenotypesUnaffectedByClonedVariants(final VCBuilderScheme builderScheme) {
        if (builderScheme == VCBuilderScheme.NEW_ON_VC) {
            return;
        }

        final VariantContextBuilder builder = new VariantContextBuilder("source", "contig", 1, 1, Arrays.asList(Tref, C, G)).filter("TEST");

        final Genotype sample1 = GenotypeBuilder.create("sample1", Arrays.asList(Tref, C));
        final Genotype sample2 = GenotypeBuilder.create("sample2", Arrays.asList(Tref, G));

        GenotypesContext gc = GenotypesContext.create(sample1);
        builder.genotypes(gc);
        try {
            gc.add(sample2);
            Assert.fail("shouldn't have gotten here");
        } catch (UnsupportedOperationException e) {
            // The exception is expected since calling builder.genotypes(gc) should make the
            // gc object immutable (to protect the builder from unexpected changes)

            // By making a new gc object and setting it to sample2 the resulting vc1 and vc2 will be different
            gc = GenotypesContext.create(sample2);
        }

        final VariantContext vc1 = builder.make();
        final VariantContext vc2 = builderScheme.getOtherBuilder(builder, null).genotypes(gc).make();

        Assert.assertNotEquals(vc2.getGenotypes(), vc1.getGenotypes(), "The two genotype lists should be different. only saw " + vc1.getGenotypes().toString());
    }

    @Test
    public void testCanResetFilters() {
        final VariantContextBuilder builder = new VariantContextBuilder("source", "contig", 1, 1, Arrays.asList(Tref, C, G)).filter("TEST");
        builder.unfiltered();
        builder.filter("mayIPlease?");
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testCantCreateNullFilter(){
        final VariantContextBuilder builder = new VariantContextBuilder("source", "contig", 1, 1, Arrays.asList(Tref, C, G)).filter("TEST");
        builder.filters((String)null);
        builder.make();
    }

    @Test
    public void testNullFilterArray(){
        final VariantContextBuilder builder = new VariantContextBuilder("source", "contig", 1, 1, Arrays.asList(Tref, C, G)).filter("TEST");
        builder.filters((String[])null);
    }

    @Test
    public void testNullFilterSet(){
        final VariantContextBuilder builder = new VariantContextBuilder("source", "contig", 1, 1, Arrays.asList(Tref, C, G)).filter("TEST");
        builder.filters((Set<String>)null);
        builder.make();
    }
}
