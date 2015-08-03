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


// the imports for unit testing.

import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.Allele;

import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

//    public Allele(byte[] bases, boolean isRef) {
//    public Allele(boolean isRef) {
//    public Allele(String bases, boolean isRef) {
//    public boolean isReference()        { return isRef; }
//    public boolean isNonReference()     { return ! isReference(); }
//    public byte[] getBases() { return bases; }
//    public boolean equals(Allele other) {
//    public int length() {

/**
 * Basic unit test for RecalData
 */
public class AlleleUnitTest extends VariantBaseTest {
    Allele ARef, A, T, ATIns, ATCIns, NoCall, SpandDel;
    
    @BeforeSuite
    public void before() {
        A = Allele.create("A");
        ARef = Allele.create("A", true);
        T = Allele.create("T");

        ATIns = Allele.create("AT");
        ATCIns = Allele.create("ATC");

        NoCall = Allele.create(Allele.NO_CALL_STRING);

        SpandDel = Allele.create(Allele.SPAN_DEL_STRING);
    }

    @Test
    public void testCreatingSNPAlleles() {
        Assert.assertTrue(A.isNonReference());
        Assert.assertFalse(A.isReference());
        Assert.assertTrue(A.basesMatch("A"));
        Assert.assertEquals(A.length(), 1);

        Assert.assertTrue(ARef.isReference());
        Assert.assertFalse(ARef.isNonReference());
        Assert.assertTrue(ARef.basesMatch("A"));
        Assert.assertFalse(ARef.basesMatch("T"));

        Assert.assertTrue(T.isNonReference());
        Assert.assertFalse(T.isReference());
        Assert.assertTrue(T.basesMatch("T"));
        Assert.assertFalse(T.basesMatch("A"));
    }

    @Test
    public void testCreatingNoCallAlleles() {
        Assert.assertTrue(NoCall.isNonReference());
        Assert.assertFalse(NoCall.isReference());
        Assert.assertFalse(NoCall.basesMatch(Allele.NO_CALL_STRING));
        Assert.assertEquals(NoCall.length(), 0);
        Assert.assertTrue(NoCall.isNoCall());
        Assert.assertFalse(NoCall.isCalled());
    }

    @Test
    public void testCreatingSpanningDeletionAlleles() {
        Assert.assertTrue(SpandDel.isNonReference());
        Assert.assertFalse(SpandDel.isReference());
        Assert.assertTrue(SpandDel.basesMatch(Allele.SPAN_DEL_STRING));
        Assert.assertEquals(SpandDel.length(), 1);
    }

    @Test
    public void testCreatingIndelAlleles() {
        Assert.assertEquals(ATIns.length(), 2);
        Assert.assertEquals(ATCIns.length(), 3);
        Assert.assertEquals(ATIns.getBases(), "AT".getBytes());
        Assert.assertEquals(ATCIns.getBases(), "ATC".getBytes());
    }


    @Test
    public void testConstructors1() {
        Allele a1 = Allele.create("A");
        Allele a2 = Allele.create("A".getBytes());
        Allele a3 = Allele.create("A");
        Allele a4 = Allele.create("A", true);

        Assert.assertTrue(a1.equals(a2));
        Assert.assertTrue(a1.equals(a3));
        Assert.assertFalse(a1.equals(a4));
    }

    @Test
    public void testInsConstructors() {
        Allele a1 = Allele.create("AC");
        Allele a2 = Allele.create("AC".getBytes());
        Allele a3 = Allele.create("AC");
        Allele a4 = Allele.create("AC", true);

        Assert.assertTrue(a1.equals(a2));
        Assert.assertTrue(a1.equals(a3));
        Assert.assertFalse(a1.equals(a4));
    }
    
    @Test
    public void testVCF42Breakend() {
        Allele a;
        
        a = Allele.create("A.");
        Assert.assertTrue(a.isSymbolic());
        Assert.assertEquals("A.", a.getDisplayString());
        
        a = Allele.create(".A");
        Assert.assertTrue(a.isSymbolic());
        Assert.assertEquals(".A", a.getDisplayString());
        
        Assert.assertTrue(Allele.create("AA.").isSymbolic());
        Assert.assertTrue(Allele.create(".AA").isSymbolic());
    }
    
    @Test
    public void testBreakpoint() {
        Allele a = Allele.create("A[chr1:1[");

        Assert.assertTrue(a.isSymbolic());
        Assert.assertEquals("A[chr1:1[", a.getDisplayString());
        
        Assert.assertTrue(Allele.create("]chr1:1]A").isSymbolic());
        Assert.assertTrue(Allele.create("[chr1:1[A").isSymbolic());
        Assert.assertTrue(Allele.create("A]chr1:1]").isSymbolic());
    }
    
    @Test
    public void testBreakpointSymbolicBreakend() {
        Assert.assertTrue(Allele.create("A[<contig>:1[").isSymbolic());
        Assert.assertTrue(Allele.create("A]<contig>:1]").isSymbolic());
        Assert.assertTrue(Allele.create("]<contig>:1]A").isSymbolic());
        Assert.assertTrue(Allele.create("[<contig>:1[A").isSymbolic());        
    }
    
    @Test
    public void testInsSymbolicShorthand() {
        Assert.assertTrue(Allele.create("A<ctg1>").isSymbolic());
        Assert.assertTrue(Allele.create("<ctg1>A").isSymbolic());
    }
    
    @Test
    public void testTelomericBreakend() {
        Assert.assertTrue(Allele.create(".[1:10]").isSymbolic());
        Assert.assertTrue(Allele.create("[1:10].").isSymbolic());
    }
    
    @Test
    public void testSymbolic() {
        Allele a = Allele.create("<SYMBOLIC>");

        Assert.assertTrue(a.isSymbolic());
        Assert.assertEquals("<SYMBOLIC>", a.getDisplayString());
    }

    @Test
    public void testEquals() {
        Assert.assertTrue(ARef.basesMatch(A));
        Assert.assertFalse(ARef.equals(A));
        Assert.assertFalse(ARef.equals(ATIns));
        Assert.assertFalse(ARef.equals(ATCIns));

        Assert.assertTrue(T.basesMatch(T));
        Assert.assertFalse(T.basesMatch(A));
        Assert.assertFalse(T.equals(A));

        Assert.assertTrue(ATIns.equals(ATIns));
        Assert.assertFalse(ATIns.equals(ATCIns));
        Assert.assertTrue(ATIns.basesMatch("AT"));
        Assert.assertFalse(ATIns.basesMatch("A"));
        Assert.assertFalse(ATIns.basesMatch("ATC"));

        Assert.assertTrue(ATIns.basesMatch("AT"));
        Assert.assertFalse(ATIns.basesMatch("ATC"));
    }

    @Test (expectedExceptions = IllegalArgumentException.class)
    public void testBadConstructorArgs1() {
        byte[] foo = null;
        Allele.create(foo);
    }

    @Test (expectedExceptions = IllegalArgumentException.class)
    public void testBadConstructorArgs2() {
        Allele.create("x");
    }

    @Test (expectedExceptions = IllegalArgumentException.class)
    public void testBadConstructorArgs3() {
        Allele.create("--");
    }

    @Test (expectedExceptions = IllegalArgumentException.class)
    public void testBadConstructorArgs4() {
        Allele.create("-A");
    }

    @Test (expectedExceptions = IllegalArgumentException.class)
    public void testBadConstructorArgs5() {
        Allele.create("A A");
    }
    
    @Test (expectedExceptions = IllegalArgumentException.class)
    public void testBadConstructorArgs6() {
        Allele.create("<symbolic>", true); // symbolic cannot be ref allele
    }

    @Test (expectedExceptions = IllegalArgumentException.class)
    public void testBadNoCallAllelel() {
        Allele.create(Allele.NO_CALL_STRING, true); // no call cannot be ref allele
    }

    @Test (expectedExceptions = IllegalArgumentException.class)
    public void testBadSpanningDeletionAllelel() {
        Allele.create(Allele.SPAN_DEL_STRING, true); // spanning deletion cannot be ref allele
    }

    @Test
    public void testExtend() {
        Assert.assertEquals("AT", Allele.extend(Allele.create("A"), "T".getBytes()).toString());
        Assert.assertEquals("ATA", Allele.extend(Allele.create("A"), "TA".getBytes()).toString());
        Assert.assertEquals("A", Allele.extend(Allele.NO_CALL, "A".getBytes()).toString());
        Assert.assertEquals("ATCGA", Allele.extend(Allele.create("AT"), "CGA".getBytes()).toString());
        Assert.assertEquals("ATCGA", Allele.extend(Allele.create("ATC"), "GA".getBytes()).toString());
    }
}