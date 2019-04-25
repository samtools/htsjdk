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

import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
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
    private Allele ARef, A, T, ATIns, ATCIns, NoCall, SpandDel, NonRef, UnspecifiedAlternate;
    
    @BeforeSuite
    public void before() {
        A = Allele.create("A");
        ARef = Allele.create("A", true);
        T = Allele.create("T");

        ATIns = Allele.create("AT");
        ATCIns = Allele.create("ATC");

        NoCall = Allele.NO_CALL;

        SpandDel = Allele.SPAN_DEL;

        NonRef = Allele.NON_REF;
        UnspecifiedAlternate = Allele.UNSPECIFIED_ALT;
    }

    @Test
    public void testCreatingSNPAlleles() {
        Assert.assertTrue(A.isAlternative());
        Assert.assertFalse(A.isReference());
        Assert.assertTrue(A.equalBases("A"));
        Assert.assertEquals(A.numberOfBases(), 1);

        Assert.assertTrue(ARef.isReference());
        Assert.assertFalse(ARef.isAlternative());
        Assert.assertTrue(ARef.equalBases("A"));
        Assert.assertFalse(ARef.equalBases("T"));

        Assert.assertTrue(T.isAlternative());
        Assert.assertFalse(T.isReference());
        Assert.assertTrue(T.equalBases("T"));
        Assert.assertFalse(T.equalBases("A"));
    }

    @Test
    public void testCreatingNoCallAlleles() {
        Assert.assertFalse(NoCall.isAlternative()); //Note: no-call is neither ref or alt... is nothing.
        Assert.assertFalse(NoCall.isReference());
        Assert.assertTrue(NoCall.equalBases(new byte[0]));
        Assert.assertEquals(NoCall.encodeAsString(), Allele.NO_CALL_STRING);
        Assert.assertEquals(NoCall.numberOfBases(), 0);
        Assert.assertTrue(NoCall.isNoCall());
        Assert.assertFalse(NoCall.isCalled());
    }

    @Test
    public void testCreatingSpanningDeletionAlleles() {
        Assert.assertFalse(SpandDel.isAlternative()); // a Span-del is a lack of a copy due to a overlapping deletion. so is neither reference or alt as far as the containging variant-context is concerned.
        Assert.assertFalse(SpandDel.isReference());
        Assert.assertTrue(SpandDel.encodeAsString().equals(Allele.SPAN_DEL_STRING));
        Assert.assertEquals(SpandDel.numberOfBases(), 0); // actual bases is 0.
    }

    @Test
    public void testCreatingIndelAlleles() {
        Assert.assertEquals(ATIns.numberOfBases(), 2);
        Assert.assertEquals(ATCIns.numberOfBases(), 3);
        Assert.assertEquals(ATIns.copyBases(), "AT".getBytes());
        Assert.assertEquals(ATCIns.copyBases(), "ATC".getBytes());
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
        Assert.assertEquals("A.", a.encodeAsString());
        
        a = Allele.create(".A");
        Assert.assertTrue(a.isSymbolic());
        Assert.assertEquals(".A", a.encodeAsString());
        
        Assert.assertTrue(Allele.create("AA.").isSymbolic());
        Assert.assertTrue(Allele.create(".AA").isSymbolic());
    }
    
    @Test
    public void testBreakpoint() {
        Allele a = Allele.create("A[chr1:1[");

        Assert.assertTrue(a.isSymbolic());
        Assert.assertEquals("A[chr1:1[", a.encodeAsString());
        
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
        // This is not allowed by the spec. these allele would simply be reported after the previous nucleotide.
        //Assert.assertTrue(Allele.decode("<ctg1>A").isSymbolic());
    }

    //todo this legacy test was testing non-valid allele encodings!?
    //1. A break end is either ..[...[.. or ..]...].., never ..[...]... nor ..]...[..
    //2. '.' is only allowed as a prefix. as a suffix it would indicate that the insert is
    // before the position after the last base in the contig yet this would be actually represented
    // as following the last base in the contig:
    // So instead of [1:10[. it should be A[1:10[ where 'A' happens to be the last base on the contig.
    @Test(enabled = false)
    public void testTelomericBreakend() {
        Assert.assertTrue(Allele.create(".[1:10]").isSymbolic()); //todo this is invalid.
        Assert.assertTrue(Allele.create("[1:10].").isSymbolic()); //todo in fact this is not valid.
    }
    
    @Test
    public void testSymbolic() {
        Allele a = Allele.create("<SYMBOLIC>");

        Assert.assertTrue(a.isSymbolic());
        Assert.assertEquals("<SYMBOLIC>", a.encodeAsString());
    }

    @Test
    public void testNonRefAllele() {
        Assert.assertTrue(NonRef.isUnspecifiedAlternative());
        Assert.assertTrue(UnspecifiedAlternate.isUnspecifiedAlternative());

        Assert.assertFalse(T.isUnspecifiedAlternative());
        Assert.assertFalse(ATIns.isUnspecifiedAlternative());

        Assert.assertTrue(Allele.NON_REF.isUnspecifiedAlternative());
        Assert.assertTrue(Allele.UNSPECIFIED_ALT.isUnspecifiedAlternative());

        Allele a = Allele.create(new String("<*>"));
        Assert.assertTrue(a.isUnspecifiedAlternative());
    }


    @Test
    public void testEquals() {
        Assert.assertTrue(ARef.equalBases(A));
        Assert.assertFalse(ARef.equals(A));
        Assert.assertFalse(ARef.equals(ATIns));
        Assert.assertFalse(ARef.equals(ATCIns));

        Assert.assertTrue(T.equalBases(T));
        Assert.assertFalse(T.equalBases(A));
        Assert.assertFalse(T.equals(A));

        Assert.assertTrue(ATIns.equals(ATIns));
        Assert.assertFalse(ATIns.equals(ATCIns));
        Assert.assertTrue(ATIns.equalBases("AT"));
        Assert.assertFalse(ATIns.equalBases("A"));
        Assert.assertFalse(ATIns.equalBases("ATC"));

        Assert.assertTrue(ATIns.equalBases("AT"));
        Assert.assertFalse(ATIns.equalBases("ATC"));
    }

    @Test (expectedExceptions = RuntimeException.class)
    public void testBadConstructorArgs1() {
        byte[] foo = null;
        Allele.create(foo);
    }

    @Test (expectedExceptions = RuntimeException.class)
    public void testBadConstructorArgs2() {
        Allele.create("x");
    }

    @Test (expectedExceptions = RuntimeException.class)
    public void testBadConstructorArgs3() {
        Allele.create("--");
    }

    @Test (expectedExceptions = RuntimeException.class)
    public void testBadConstructorArgs4() {
        Allele.create("-A");
    }

    @Test (expectedExceptions = RuntimeException.class)
    public void testBadConstructorArgs5() {
        Allele.create("A A");
    }
    
    @Test (expectedExceptions = RuntimeException.class)
    public void testBadConstructorArgs6() {
        Allele.create("<symbolic>", true); // symbolic cannot be ref allele
    }

    @Test (expectedExceptions = RuntimeException.class)
    public void testBadNoCallAllelel() {
        Allele.create(Allele.NO_CALL_STRING, true); // no call cannot be ref allele
    }

    @Test (expectedExceptions = RuntimeException.class)
    public void testBadSpanningDeletionAllelel() {
        Allele.create(Allele.SPAN_DEL_STRING, true); // spanning deletion cannot be ref allele
    }

    @DataProvider
    public Object[][] getExtendTests() {
        return new Object[][]{
                {Allele.create("A"), "T", "AT"},
                {Allele.create("A"), "TA", "ATA"},
         //       {Allele.NO_CALL, "A", "A"}, // Why would ever support such a thing!!!
                {Allele.create("AT"), "CGA", "ATCGA"},
                {Allele.create("ATC"), "GA", "ATCGA"}
        };
    }

    @Test(dataProvider = "getExtendTests")
    public void testExtend(Allele toExtend, String extension, String expected) {
        final Allele extended = toExtend.extend(extension.getBytes());
        Assert.assertEquals(extended, Allele.create(expected));
    }

    @DataProvider
    public Object[][] getTestCasesForCheckingSymbolicAlleles(){
        return new Object[][]{
                //allele, isSymbolic, isBreakpoint, isSingleBreakend
                {"<DEL>",               true, false, false},
                {"G]17:198982]",        true, true, false},
                {"]13:123456]T",        true, true, false},
                {"AAAAAA[chr1:1234[",   true, true, false},
                {"AAAAAA]chr1:1234]",   true, true, false},
                {"A.",                  true, false, true},
                {".A",                  true, false, true},
                {"AA",                  false, false, false},
                {"A",                   false, false, false}
        };
    }

    // Breakend = Breakpoint but for some reason in thecode this is not the case!?
    @Test(dataProvider = "getTestCasesForCheckingSymbolicAlleles", enabled = false)
    public void testWouldBeSymbolic(String baseString, boolean isSymbolic, boolean isBreakpoint, boolean isBreakend) {
        Assert.assertEquals(Allele.wouldBeSymbolicAllele(baseString.getBytes()), isSymbolic);
    }

    // Breakend = Breakpoint but for some reason in thecode this is not the case!?
    @Test(dataProvider = "getTestCasesForCheckingSymbolicAlleles", enabled = false)
    public void testWouldBeBreakpoint(String baseString, boolean isSymbolic, boolean isBreakpoint, boolean isBreakend) {
        Assert.assertEquals(Allele.wouldBeBreakpoint(baseString.getBytes()), isBreakpoint);
    }

    @Test(dataProvider = "getTestCasesForCheckingSymbolicAlleles")
    public void testWouldBeBreakend(String baseString, boolean isSymbolic, boolean isBreakpoint, boolean isBreakend) {
        Assert.assertEquals(Allele.wouldBeSingleBreakend(baseString.getBytes()), isBreakend);
    }

    public void checkConstraints(final Allele a) {
        if (a.isReference()) {
            Assert.assertTrue(a.isInline()); // only inline alleles can be reference.
        }
        Assert.assertEquals(a.encodeAsString(), new String(a.encodeAsBytes()));

        if (a.isInline()) {
            Assert.assertEquals(a.numberOfBases(), a.encodeAsBytes().length);
            Assert.assertEquals(a.numberOfBases(), a.encodeAsString().length());
        }
        // it must be one and only one:
        int typeCount = 0;
        if (a.isInline()) typeCount++;
        if (a.isSymbolic()) typeCount++;
        if (a.isNoCall()) typeCount++;
        if (a.isSpanDeletion()) typeCount++;
        if (a.isUnspecifiedAlternative()) typeCount++;
        Assert.assertEquals(typeCount, 1);

        if (a.getSymbolicID() != null) {
            Assert.assertTrue(a.isSymbolic());
        }
        if (a.isBreakend()) {
            Assert.assertTrue(a.isSymbolic());
            Assert.assertNull(a.getSymbolicID());
            Assert.assertTrue(a.isSingleBreakend() ^ a.isPairedBreakend());
        }
        if (a.isContigInsertion()) {
            Assert.assertTrue(a.isSymbolic());
            Assert.assertNull(a.getSymbolicID());
        }

    }
}
