package htsjdk.variant.variantcontext;

import htsjdk.variant.VariantBaseTest;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;

public class VariantContextBuilderTest extends VariantBaseTest {
    Allele Aref, C;
    Allele Tref, G;

    String snpChr = "chr1";
    String snpSource = "test";
    long snpLocStart = 10;
    long snpLocStop = 10;

    @BeforeTest
    public void before() {

        C = Allele.create("C");
        Aref = Allele.create("A", true);
        G = Allele.create("G");
        Tref = Allele.create("T", true);
    }

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
    public Object[][] BuilderSchemes() {
        return new Object[][]{
                {VCBuilderScheme.NEW_ON_BUILDER},
                {VCBuilderScheme.NEW_ON_VC},
                {VCBuilderScheme.SAME}};
    }

    @Test(dataProvider = "BuilderSchemes")
    public void testAttributeResettingWorks(final VCBuilderScheme builderScheme) {
        final VariantContextBuilder root1 = new VariantContextBuilder(snpSource, snpChr, snpLocStart, snpLocStop, Arrays.asList(Aref, C));

        final VariantContext result1 = root1.attribute("AC", 1).make();
        final VariantContextBuilder root2 = builderScheme.getOtherBuilder(root1, result1)
                .source(snpSource).chr(snpChr).start(snpLocStart).stop(snpLocStop).alleles(Arrays.asList(Aref, C));

        //this is a red-herring and should not change anything.
        final VariantContext ignored = root1.attribute("AC", 2).make();
        final VariantContext result2 = root2.attribute("AC", 1).make();

        Assert.assertEquals(result1.getAttribute("AC"), result2.getAttribute("AC"));
    }

    @Test(dataProvider = "BuilderSchemes")
    public void testAttributeResettingWorks2(final VCBuilderScheme builderScheme) {
        final VariantContextBuilder root1 = new VariantContextBuilder(snpSource, snpChr, snpLocStart, snpLocStop, Arrays.asList(Aref, C));

        final VariantContext result1 = root1.attribute("AC", 1).make();
        final VariantContextBuilder root2 = builderScheme.getOtherBuilder(root1, result1)
                .source(snpSource).chr(snpChr).start(snpLocStart).stop(snpLocStop).alleles(Arrays.asList(Aref, C));

        final VariantContext result2 = root2.attribute("AC", 2).make();

        Assert.assertNotEquals(result1.getAttribute("AC"), result2.getAttribute("AC"));
    }

    @Test(dataProvider = "BuilderSchemes")
    public void testAttributeResettingWorks3(final VCBuilderScheme builderScheme) {
        final VariantContextBuilder root1 = new VariantContextBuilder(snpSource, snpChr, snpLocStart, snpLocStop, Arrays.asList(Aref, C));

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("AC", 1);

        final VariantContext result1 = root1.attributes(attributes).make();
        final VariantContextBuilder root2 = builderScheme.getOtherBuilder(root1, result1)
                .source(snpSource).chr(snpChr).start(snpLocStart).stop(snpLocStop).alleles(Arrays.asList(Aref, C));

        attributes.put("AC", 2);

        final VariantContext result2 = root2.attributes(attributes).make();

        Assert.assertNotEquals(result1.getAttribute("AC"), result2.getAttribute("AC"), "AC attributes should be different, found: " + result2.getAttribute("AC").toString());
    }

    @Test(dataProvider = "BuilderSchemes")
    public void testFilterUnaffectedByClonedVariants(final VCBuilderScheme builderScheme) {
        final VariantContextBuilder builder = new VariantContextBuilder("source", "contig", 1, 1, Arrays.asList(Tref, C)).filter("TEST");
        final VariantContext vc1 = builder.make();
        final VariantContext vc2 = builderScheme.getOtherBuilder(builder, vc1).filter("TEST2").make();
        Assert.assertNotEquals(vc2.getFilters(), vc1.getFilters(), "The two lists of filters should be different");
    }

    @Test(dataProvider = "BuilderSchemes")
    public void testFiltersUnaffectedByClonedVariants(final VCBuilderScheme builderScheme) {
        final VariantContextBuilder builder = new VariantContextBuilder("source", "contig", 1, 1, Arrays.asList(Tref, C)).filter("TEST");
        final VariantContext vc1 = builder.make();
        final VariantContext vc2 = builderScheme.getOtherBuilder(builder, vc1).filters("TEST2").make();
        Assert.assertNotEquals(vc2.getFilters(), vc1.getFilters(), "The two lists of filters should be different");
    }

    @Test(dataProvider = "BuilderSchemes")
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
    public void testFilterCanUseUnmodifyableSet() {
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

    @Test(dataProvider = "illegalFilterStrings",expectedExceptions = IllegalStateException.class)
    public void testFilterCannotUseBadFilters(final String filter) {
        final Set<String> filters = new HashSet<>();
        filters.add(filter);
        final VariantContextBuilder builder = new VariantContextBuilder("source", "contig", 1, 1, Arrays.asList(Tref, C)).filters(Collections.unmodifiableSet(filters));
        final VariantContext vc1 = builder.make();

        //shouldn't have gotten here
        Assert.fail("a bad filter should have not been permitted: '" + filter + "'");
    }


    @Test(dataProvider = "BuilderSchemes")
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

    @Test(dataProvider = "BuilderSchemes")
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
        } catch (IllegalAccessError e) {
            // nice work...
            gc = GenotypesContext.create(sample2);
        }

        final VariantContext vc1 = builder.make();
        final VariantContext vc2 = builderScheme.getOtherBuilder(builder, null).genotypes(gc).make();

        Assert.assertNotEquals(vc2.getGenotypes(), vc1.getGenotypes(), "The two genotype lists should be different. only saw " + vc1.getGenotypes().toString());
    }
}
