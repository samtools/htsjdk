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

import htsjdk.samtools.util.Log;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.VariantContextUtils.JexlVCMatchExp;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;


/**
 * 
 * @author aaron
 * @author bimber
 * 
 * Class VariantJEXLContextUnitTest
 *
 * Test out parts of the VariantJEXLContext and GenotypeJEXLContext
 */
public class VariantJEXLContextUnitTest extends VariantBaseTest {

    private static String expression = "QUAL > 500.0";
    private static VariantContextUtils.JexlVCMatchExp exp;

    Allele A, Aref, T, Tref;

    Allele ATC, ATCref;
    // A [ref] / T at 10

    // - / ATC [ref] from 20-23

    @BeforeClass
    public void beforeClass() {
        try {
            exp = new VariantContextUtils.JexlVCMatchExp("name", VariantContextUtils.engine.get().createExpression(expression));
        } catch (Exception e) {
            Assert.fail("Unable to create expression" + e.getMessage());
        }
    }

    @BeforeMethod
    public void before() {
        A = Allele.create("A");
        Aref = Allele.create("A", true);
        T = Allele.create("T");
        Tref = Allele.create("T", true);

        ATC = Allele.create("ATC");
        ATCref = Allele.create("ATC", true);
    }


    @Test
    public void testGetValue() {
        Map<VariantContextUtils.JexlVCMatchExp, Boolean> map = getVarContext();

        // make sure the context has a value
        Assert.assertTrue(!map.isEmpty());
        Assert.assertEquals(map.size(), 1);

        // eval our known expression
        Assert.assertTrue(!map.get(exp));
    }
    
    // Testing the new 'FT' and 'isPassFT' expressions in the JEXL map
    @Test
    public void testJEXLGenotypeFilters() {
    	
    	JexlVCMatchExp passFlag = new VariantContextUtils.JexlVCMatchExp(
    			"passFlag", VariantContextUtils.engine.get().createExpression("isPassFT==1"));
    	JexlVCMatchExp passFT = new VariantContextUtils.JexlVCMatchExp(
    			"FTPASS", VariantContextUtils.engine.get().createExpression("FT==\"PASS\""));
    	JexlVCMatchExp failFT = new VariantContextUtils.JexlVCMatchExp(
    			"FTBadCall", VariantContextUtils.engine.get().createExpression("FT==\"BadCall\""));
        JexlVCMatchExp AD1 = new VariantContextUtils.JexlVCMatchExp(
                "AD1", VariantContextUtils.engine.get().createExpression("g.hasAD() && g.getAD().0==1"));
        JexlVCMatchExp AD2 = new VariantContextUtils.JexlVCMatchExp(
                "AD2", VariantContextUtils.engine.get().createExpression("g.hasAD() && g.getAD().1==2"));

    	List<JexlVCMatchExp> jexlTests = Arrays.asList(passFlag, passFT, failFT, AD1, AD2);
    	Map<VariantContextUtils.JexlVCMatchExp, Boolean> map;
    	
    	List<Allele> alleles = Arrays.asList(Aref, T);
    	VariantContextBuilder vcb = new VariantContextBuilder("test", "chr1", 10, 10, alleles);
        VariantContext vcPass = vcb.filters("PASS").make();
        VariantContext vcFail = vcb.filters("BadVariant").make();
        GenotypeBuilder gb = new GenotypeBuilder("SAMPLE", alleles);

        Genotype genoNull = gb.make();
        Genotype genoPass = gb.filters("PASS").AD(new int[]{1,2}).DP(3).make();
        Genotype genoFail = gb.filters("BadCall").AD(null).DP(0).make();

        // Create the JEXL Maps using the combinations above of vc* and geno*
        map = new JEXLMap(jexlTests,vcPass, genoPass);
        // make sure the context has a value
        Assert.assertTrue(!map.isEmpty());
        Assert.assertEquals(map.size(), 5);
        Assert.assertTrue(map.get(passFlag));
        Assert.assertTrue(map.get(passFT));
        Assert.assertFalse(map.get(failFT));
        Assert.assertTrue(map.get(AD1));
        Assert.assertTrue(map.get(AD2));

        map = new JEXLMap(jexlTests, vcPass, genoFail);
        // make sure the context has a value
        Assert.assertTrue(!map.isEmpty());
        Assert.assertEquals(map.size(), 5);
        Assert.assertFalse(map.get(passFlag));
        Assert.assertFalse(map.get(passFT));
        Assert.assertTrue(map.get(failFT));
        Assert.assertFalse(map.get(AD1));
        Assert.assertFalse(map.get(AD2));

        // Null genotype filter is equivalent to explicit "FT==PASS"
        map = new JEXLMap(jexlTests, vcPass, genoNull);
        // make sure the context has a value
        Assert.assertTrue(!map.isEmpty());
        Assert.assertEquals(map.size(), 5);
        Assert.assertTrue(map.get(passFlag));
        Assert.assertTrue(map.get(passFT));
        Assert.assertFalse(map.get(failFT));
        Assert.assertFalse(map.get(AD1));
        Assert.assertFalse(map.get(AD2));
        
        // Variant-level filters should have no effect here
        map = new JEXLMap(jexlTests,vcFail, genoPass);
        // make sure the context has a value
        Assert.assertTrue(!map.isEmpty());
        Assert.assertEquals(map.size(), 5);
        Assert.assertTrue(map.get(passFlag));
        Assert.assertTrue(map.get(passFT));
        Assert.assertFalse(map.get(failFT));
        
        map = new JEXLMap(jexlTests,vcFail, genoFail);
        // make sure the context has a value
        Assert.assertTrue(!map.isEmpty());
        Assert.assertEquals(map.size(), 5);
        Assert.assertFalse(map.get(passFlag));
        Assert.assertFalse(map.get(passFT));
        Assert.assertTrue(map.get(failFT));
        
        map = new JEXLMap(jexlTests,vcFail, genoNull);
        // make sure the context has a value
        Assert.assertTrue(!map.isEmpty());
        Assert.assertEquals(map.size(), 5);
        Assert.assertTrue(map.get(passFlag));
        Assert.assertTrue(map.get(passFT));
        Assert.assertFalse(map.get(failFT));
    }

    @Test(expectedExceptions=UnsupportedOperationException.class)
    public void testContainsValue() {
        Map<VariantContextUtils.JexlVCMatchExp, Boolean> map = getVarContext();

        map.containsValue(exp);
    }

    @Test(expectedExceptions=UnsupportedOperationException.class)
    public void testRemove() {
        Map<VariantContextUtils.JexlVCMatchExp, Boolean> map = getVarContext();

        map.remove(exp);
    }

    @Test(expectedExceptions=UnsupportedOperationException.class)
    public void testEntrySet() {
        Map<VariantContextUtils.JexlVCMatchExp, Boolean> map = getVarContext();

        map.entrySet();
    }

    @Test(expectedExceptions=UnsupportedOperationException.class)
    public void testClear() {
        Map<VariantContextUtils.JexlVCMatchExp, Boolean> map = getVarContext();

        map.clear();
    }

    /**
     * helper method
     * @return a VariantJEXLContext
     */
    private JEXLMap getVarContext() {
        List<Allele> alleles = Arrays.asList(Aref, T);

        VariantContext vc = new VariantContextBuilder("test", "chr1", 10, 10, alleles).make();
        return new JEXLMap(Arrays.asList(exp),vc);
    }
}
