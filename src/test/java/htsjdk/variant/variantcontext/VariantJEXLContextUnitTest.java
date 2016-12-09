/*
* Copyright (c) 2012 The Broad Institute
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

import htsjdk.tribble.SimpleFeature;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.VariantContextUtils.JexlVCMatchExp;

import htsjdk.variant.vcf.VCFConstants;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;


/**
 * 
 * @author aaron
 * @author bimber
 * @author hyq
 *
 *
 * Test out parts of the VariantJEXLContext and GenotypeJEXLContext
 */
public class VariantJEXLContextUnitTest extends VariantBaseTest {

    private static final VariantContextUtils.JexlVCMatchExp exp
            = new VariantContextUtils.JexlVCMatchExp("name", VariantContextUtils.engine.get().createExpression("QUAL > 500.0"));

    private static final JexlVCMatchExp missingValueExpression = new VariantContextUtils.JexlVCMatchExp(
            "Zis10", VariantContextUtils.engine.get().createExpression("Z==10"));


    // SNP alleles: A[ref]/T[alt] at chr1:10. One (crappy) sample, one (bare minimum) VC.
    private static final SimpleFeature eventLoc = new SimpleFeature("chr1", 10, 10);
    private static final Allele Aref = Allele.create("A", true);
    private static final Allele Talt = Allele.create("T");
    private static final Genotype gt = new GenotypeBuilder("DummySample", Arrays.asList(Aref, Talt))
                                            .phased(false)
                                            .DP(2)
                                            .noGQ()
                                            .noAD()
                                            .noPL()
                                            .filter("lowDP")
                                            .attribute("WA", "whatEver")
                                            .make();
    private static final VariantContext vc = new VariantContextBuilder("test", eventLoc.getContig(), eventLoc.getStart(), eventLoc.getEnd(), Arrays.asList(Aref, Talt))
                                                .genotypes(gt)
                                                .noID()
                                                .filter("q10")
                                                .attribute("attr", "notEmpty")
                                                .make();

    //////////////////////// testing JEXLMap ////////////////////////
    @Test
    public void testGetValue() {
        final Map<VariantContextUtils.JexlVCMatchExp, Boolean> jexlMap = getJEXLMap();

        // make sure the context has a value
        Assert.assertTrue(!jexlMap.isEmpty());
        Assert.assertEquals(jexlMap.size(), 1);

        // eval our known expression
        Assert.assertTrue(!jexlMap.get(exp));
    }

    @Test(dataProvider = "getMissingValueTestData")
    public void testMissingBehaviorThroughMatch(VariantContext vc, VariantContextUtils.JexlMissingValueTreatment missingValueTreatment, boolean expected, Class<? extends Exception> expectedException){
        if(expectedException == null) {
            Assert.assertEquals(VariantContextUtils.match(vc, null, missingValueExpression, missingValueTreatment), expected);
        } else {
            Assert.assertThrows(expectedException, () -> VariantContextUtils.match(vc, null, missingValueExpression, missingValueTreatment));
        }
    }

    @Test(dataProvider = "getMissingValueTestData")
    public void testMissingBehavior(VariantContext vc, VariantContextUtils.JexlMissingValueTreatment missingValueTreatment, boolean expected, Class<? extends Exception> expectedException){
        final JEXLMap jexlMap = new JEXLMap(Collections.singletonList(missingValueExpression), vc, null, missingValueTreatment);
        if(expectedException == null) {
            Assert.assertEquals((boolean) jexlMap.get(missingValueExpression), expected);
        } else {
            Assert.assertThrows(expectedException, () -> jexlMap.get(missingValueExpression));
        }
    }

    @DataProvider
    public Object[][] getMissingValueTestData(){
        final List<Allele> alleles = Arrays.asList(Aref, Talt);
        VariantContextBuilder vcb = new VariantContextBuilder("test", "chr1", 10, 10, alleles);
        VariantContext noZ = vcb.make();
        VariantContext hasZ = vcb.attribute("Z", 0).make();

        return new Object[][]{
                {noZ, JEXLMap.DEFAULT_MISSING_VALUE_TREATMENT, false, null},
                {hasZ, JEXLMap.DEFAULT_MISSING_VALUE_TREATMENT, false, null}, //the value isn't missing but the expression is false
                {noZ, VariantContextUtils.JexlMissingValueTreatment.MATCH, true, null},
                {hasZ,VariantContextUtils.JexlMissingValueTreatment.MATCH, false, null}, //the value isn't missing but the expression is false
                {noZ, VariantContextUtils.JexlMissingValueTreatment.MISMATCH, false, null},
                {hasZ, VariantContextUtils.JexlMissingValueTreatment.MISMATCH, false, null},
                {noZ, VariantContextUtils.JexlMissingValueTreatment.THROW, false, IllegalArgumentException.class},
                {hasZ, VariantContextUtils.JexlMissingValueTreatment.THROW, false, null}
        };
    }

    // Testing the new 'FT' and 'isPassFT' expressions in the JEXL map
    @Test
    public void testJEXLGenotypeFilters() {
    	
    	final JexlVCMatchExp passFlag = new VariantContextUtils.JexlVCMatchExp(
    			"passFlag", VariantContextUtils.engine.get().createExpression("isPassFT==1"));
    	final JexlVCMatchExp passFT = new VariantContextUtils.JexlVCMatchExp(
    			"FTPASS", VariantContextUtils.engine.get().createExpression("FT==\"PASS\""));
    	final JexlVCMatchExp failFT = new VariantContextUtils.JexlVCMatchExp(
    			"FTBadCall", VariantContextUtils.engine.get().createExpression("FT==\"BadCall\""));
        final JexlVCMatchExp AD1 = new VariantContextUtils.JexlVCMatchExp(
                "AD1", VariantContextUtils.engine.get().createExpression("g.hasAD() && g.getAD().0==1"));
        final JexlVCMatchExp AD2 = new VariantContextUtils.JexlVCMatchExp(
                "AD2", VariantContextUtils.engine.get().createExpression("g.hasAD() && g.getAD().1==2"));

    	final List<JexlVCMatchExp> jexlTests = Arrays.asList(passFlag, passFT, failFT, AD1, AD2);

    	final List<Allele> alleles = Arrays.asList(Aref, Talt);
    	final VariantContextBuilder vcb = new VariantContextBuilder("test", "chr1", 10, 10, alleles);
        final VariantContext vcPass = vcb.filters("PASS").make();
        final VariantContext vcFail = vcb.filters("BadVariant").make();
        final GenotypeBuilder gb = new GenotypeBuilder("SAMPLE", alleles);

        final Genotype genoNull = gb.make();
        final Genotype genoPass = gb.filters("PASS").AD(new int[]{1,2}).DP(3).make();
        final Genotype genoFail = gb.filters("BadCall").AD(null).DP(0).make();

        Map<VariantContextUtils.JexlVCMatchExp, Boolean> jexlMap;

        // Create the JEXL Maps using the combinations above of vc* and geno*
        jexlMap = new JEXLMap(jexlTests, vcPass, genoPass);
        // make sure the context has a value
        Assert.assertTrue(!jexlMap.isEmpty());
        Assert.assertEquals(jexlMap.size(), 5);
        Assert.assertTrue(jexlMap.get(passFlag));
        Assert.assertTrue(jexlMap.get(passFT));
        Assert.assertFalse(jexlMap.get(failFT));
        Assert.assertTrue(jexlMap.get(AD1));
        Assert.assertTrue(jexlMap.get(AD2));

        jexlMap = new JEXLMap(jexlTests, vcPass, genoFail);
        // make sure the context has a value
        Assert.assertTrue(!jexlMap.isEmpty());
        Assert.assertEquals(jexlMap.size(), 5);
        Assert.assertFalse(jexlMap.get(passFlag));
        Assert.assertFalse(jexlMap.get(passFT));
        Assert.assertTrue(jexlMap.get(failFT));
        Assert.assertFalse(jexlMap.get(AD1));
        Assert.assertFalse(jexlMap.get(AD2));

        // Null genotype filter is equivalent to explicit "FT==PASS"
        jexlMap = new JEXLMap(jexlTests, vcPass, genoNull);
        // make sure the context has a value
        Assert.assertTrue(!jexlMap.isEmpty());
        Assert.assertEquals(jexlMap.size(), 5);
        Assert.assertTrue(jexlMap.get(passFlag));
        Assert.assertTrue(jexlMap.get(passFT));
        Assert.assertFalse(jexlMap.get(failFT));
        Assert.assertFalse(jexlMap.get(AD1));
        Assert.assertFalse(jexlMap.get(AD2));
        
        // Variant-level filters should have no effect here
        jexlMap = new JEXLMap(jexlTests, vcFail, genoPass);
        // make sure the context has a value
        Assert.assertTrue(!jexlMap.isEmpty());
        Assert.assertEquals(jexlMap.size(), 5);
        Assert.assertTrue(jexlMap.get(passFlag));
        Assert.assertTrue(jexlMap.get(passFT));
        Assert.assertFalse(jexlMap.get(failFT));
        
        jexlMap = new JEXLMap(jexlTests, vcFail, genoFail);
        // make sure the context has a value
        Assert.assertTrue(!jexlMap.isEmpty());
        Assert.assertEquals(jexlMap.size(), 5);
        Assert.assertFalse(jexlMap.get(passFlag));
        Assert.assertFalse(jexlMap.get(passFT));
        Assert.assertTrue(jexlMap.get(failFT));
        
        jexlMap = new JEXLMap(jexlTests, vcFail, genoNull);
        // make sure the context has a value
        Assert.assertTrue(!jexlMap.isEmpty());
        Assert.assertEquals(jexlMap.size(), 5);
        Assert.assertTrue(jexlMap.get(passFlag));
        Assert.assertTrue(jexlMap.get(passFT));
        Assert.assertFalse(jexlMap.get(failFT));
    }

    @Test(expectedExceptions=UnsupportedOperationException.class)
    public void testContainsValue() {
        final Map<VariantContextUtils.JexlVCMatchExp, Boolean> jexlMap = getJEXLMap();

        jexlMap.containsValue(exp);
    }

    @Test(expectedExceptions=UnsupportedOperationException.class)
    public void testRemove() {
        final Map<VariantContextUtils.JexlVCMatchExp, Boolean> jexlMap = getJEXLMap();

        jexlMap.remove(exp);
    }

    @Test(expectedExceptions=UnsupportedOperationException.class)
    public void testEntrySet() {
        final Map<VariantContextUtils.JexlVCMatchExp, Boolean> jexlMap = getJEXLMap();

        jexlMap.entrySet();
    }

    @Test(expectedExceptions=UnsupportedOperationException.class)
    public void testClear() {
        final Map<VariantContextUtils.JexlVCMatchExp, Boolean> jexlMap = getJEXLMap();

        jexlMap.clear();
    }

    /**
     * @return a JEXLMap for use by actual tests
     */
    private JEXLMap getJEXLMap() {
        return new JEXLMap(Collections.singletonList(exp), vc);
    }

    //////////////////////// testing GenotypeJEXLContext and its base VariantJEXLContext ////////////////////////

    /**
     * Test the various if-else cases in {@link GenotypeJEXLContext#get(String)} and {@link VariantJEXLContext#get(String)}
     * {@link GenotypeJEXLContext#has(String)} is not tested because it simply checks if get() will return null.
     */
    @Test
    public void testVariantJEXLContextGetMethod() {

        final VariantJEXLContext jEXLContext = getJEXLContext();

        // This is not tested because there's no simple test for equality for VariantContext,
        // except exhaustive attributes testing, which is what happening below.
//        Assert.assertEquals(jEXLContext.get("vc"), new VariantContextBuilder("test", "chr1", 10, 10, Arrays.asList(Aref, Talt)).make());

        // GenotypeJEXLContext
        Assert.assertTrue( ((Genotype) jEXLContext.get("g")).sameGenotype(gt, false));
        Assert.assertEquals(jEXLContext.get("isHom"), VariantJEXLContext.false_string);
        Assert.assertEquals(jEXLContext.get("isHomRef"), VariantJEXLContext.false_string);
        Assert.assertEquals(jEXLContext.get("isHomVar"), VariantJEXLContext.false_string);
        Assert.assertEquals(jEXLContext.get("isHet"), VariantJEXLContext.true_string);
        Assert.assertEquals(jEXLContext.get("isCalled"), VariantJEXLContext.true_string);
        Assert.assertEquals(jEXLContext.get("isNoCall"), VariantJEXLContext.false_string);
        Assert.assertEquals(jEXLContext.get("isMixed"), VariantJEXLContext.false_string);
        Assert.assertEquals(jEXLContext.get("isAvailable"), VariantJEXLContext.true_string);
        Assert.assertEquals(jEXLContext.get("isPassFT"), VariantJEXLContext.false_string);
        Assert.assertEquals(jEXLContext.get(VCFConstants.GENOTYPE_KEY), gt.getGenotypeString());
        Assert.assertEquals(jEXLContext.get(VCFConstants.GENOTYPE_FILTER_KEY),"lowDP");
        Assert.assertEquals(jEXLContext.get(VCFConstants.GENOTYPE_QUALITY_KEY),Integer.valueOf(VCFConstants.MISSING_GENOTYPE_QUALITY_v3));
        Assert.assertEquals(jEXLContext.get("WA"),"whatEver"); // hasAnyAttribute->getAnyAttribute
        Assert.assertEquals(jEXLContext.get("lowDP"),VariantJEXLContext.true_string); // getFilters()!=null

        // VariantJEXLContext
        Assert.assertEquals(jEXLContext.get("CHROM"), eventLoc.getContig());
        Assert.assertEquals(jEXLContext.get("POS"), eventLoc.getStart());
        Assert.assertEquals(jEXLContext.get("TYPE"), VariantContext.Type.SNP.name());
        Assert.assertEquals(jEXLContext.get("QUAL"), -10.0); // because of noGQ() when building the genotype
        Assert.assertEquals(jEXLContext.get("ALLELES"), vc.getAlleles());
        Assert.assertEquals(jEXLContext.get("N_ALLELES"), vc.getNAlleles());
        Assert.assertEquals(jEXLContext.get("FILTER"), VariantJEXLContext.true_string);
        Assert.assertEquals(jEXLContext.get("homRefCount"), 0);
        Assert.assertEquals(jEXLContext.get("homVarCount"), 0);
        Assert.assertEquals(jEXLContext.get("hetCount"), 1);
        Assert.assertEquals(jEXLContext.get("attr"), "notEmpty"); // hasAnyAttribute->getAnyAttribute
        Assert.assertEquals(jEXLContext.get("q10"), VariantJEXLContext.true_string); // getFilters()!=null

        // all if-else fall through
        Assert.assertNull(jEXLContext.get("mustBeNull"));
    }

    @Test(expectedExceptions=UnsupportedOperationException.class)
    public void testVariantJEXLContextSetMethodException(){
        getJEXLContext().set("noMatterWhat", "willBlowup");
    }

    /**
     * @return a GenotypeJEXLContext for use by actual tests
     */
    private VariantJEXLContext getJEXLContext(){
        return new GenotypeJEXLContext(vc, gt);
    }
}
