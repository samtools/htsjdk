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

import htsjdk.samtools.util.TestUtil;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.FeatureCodec;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.bcf2.BCF2Codec;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.tribble.TribbleException;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.vcf.VCFConstants;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import java.io.File;
import java.util.*;


public class VariantContextUnitTest extends VariantBaseTest {
    Allele A, Aref, C, T, Tref;
    Allele del, delRef, ATC, ATCref;

    // A [ref] / T at 10
    String snpLoc = "chr1";
    int snpLocStart = 10;
    int snpLocStop = 10;

    // - / ATC [ref] from 20-22
    String delLoc = "chr1";
    int delLocStart = 20;
    int delLocStop = 22;

    // - [ref] / ATC from 20-20
    String insLoc = "chr1";
    int insLocStart = 20;
    int insLocStop = 20;

    VariantContextBuilder basicBuilder, snpBuilder, insBuilder;

    @BeforeSuite
    public void before() {
        del = Allele.create("A");
        delRef = Allele.create("A", true);

        A = Allele.create("A");
        C = Allele.create("C");
        Aref = Allele.create("A", true);
        T = Allele.create("T");
        Tref = Allele.create("T", true);

        ATC = Allele.create("ATC");
        ATCref = Allele.create("ATC", true);
    }

    @BeforeMethod
    public void beforeTest() {
        basicBuilder = new VariantContextBuilder("test", snpLoc,snpLocStart, snpLocStop, Arrays.asList(Aref, T));
        snpBuilder = new VariantContextBuilder("test", snpLoc,snpLocStart, snpLocStop, Arrays.asList(Aref, T));
        insBuilder = new VariantContextBuilder("test", insLoc, insLocStart, insLocStop, Arrays.asList(delRef, ATC));
    }

    @Test
    public void testDetermineTypes() {
        Allele ACref = Allele.create("AC", true);
        Allele AC = Allele.create("AC");
        Allele AT = Allele.create("AT");
        Allele C = Allele.create("C");
        Allele CAT = Allele.create("CAT");
        Allele TAref = Allele.create("TA", true);
        Allele TA = Allele.create("TA");
        Allele TC = Allele.create("TC");
        Allele symbolic = Allele.create("<FOO>");

        // test REF
        List<Allele> alleles = Arrays.asList(Tref);
        VariantContext vc = snpBuilder.alleles(alleles).stop(snpLocStop).make();
        Assert.assertEquals(vc.getType(), VariantContext.Type.NO_VARIATION);

        // test SNPs
        alleles = Arrays.asList(Tref, A);
        vc = snpBuilder.alleles(alleles).stop(snpLocStop).make();
        Assert.assertEquals(vc.getType(), VariantContext.Type.SNP);

        alleles = Arrays.asList(Tref, A, C);
        vc = snpBuilder.alleles(alleles).stop(snpLocStop).make();
        Assert.assertEquals(vc.getType(), VariantContext.Type.SNP);

        // test MNPs
        alleles = Arrays.asList(ACref, TA);
        vc = snpBuilder.alleles(alleles).stop(snpLocStop+1).make();
        Assert.assertEquals(vc.getType(), VariantContext.Type.MNP);

        alleles = Arrays.asList(ATCref, CAT, Allele.create("GGG"));
        vc = basicBuilder.alleles(alleles).stop(snpLocStop+2).make();
        Assert.assertEquals(vc.getType(), VariantContext.Type.MNP);

        // test INDELs
        alleles = Arrays.asList(Aref, ATC);
        vc = basicBuilder.alleles(alleles).stop(snpLocStop).make();
        Assert.assertEquals(vc.getType(), VariantContext.Type.INDEL);

        alleles = Arrays.asList(ATCref, A);
        vc = basicBuilder.alleles(alleles).stop(snpLocStop+2).make();
        Assert.assertEquals(vc.getType(), VariantContext.Type.INDEL);

        alleles = Arrays.asList(Tref, TA, TC);
        vc = basicBuilder.alleles(alleles).stop(snpLocStop).make();
        Assert.assertEquals(vc.getType(), VariantContext.Type.INDEL);

        alleles = Arrays.asList(ATCref, A, AC);
        vc = basicBuilder.alleles(alleles).stop(snpLocStop+2).make();
        Assert.assertEquals(vc.getType(), VariantContext.Type.INDEL);

        alleles = Arrays.asList(ATCref, A, Allele.create("ATCTC"));
        vc = basicBuilder.alleles(alleles).stop(snpLocStop+2).make();
        Assert.assertEquals(vc.getType(), VariantContext.Type.INDEL);

        // test MIXED
        alleles = Arrays.asList(TAref, T, TC);
        vc = basicBuilder.alleles(alleles).stop(snpLocStop+1).make();
        Assert.assertEquals(vc.getType(), VariantContext.Type.MIXED);

        alleles = Arrays.asList(TAref, T, AC);
        vc = basicBuilder.alleles(alleles).stop(snpLocStop+1).make();
        Assert.assertEquals(vc.getType(), VariantContext.Type.MIXED);

        alleles = Arrays.asList(ACref, ATC, AT);
        vc = basicBuilder.alleles(alleles).stop(snpLocStop+1).make();
        Assert.assertEquals(vc.getType(), VariantContext.Type.MIXED);

        alleles = Arrays.asList(Aref, T, symbolic);
        vc = basicBuilder.alleles(alleles).stop(snpLocStop).make();
        Assert.assertEquals(vc.getType(), VariantContext.Type.MIXED);

        // test SYMBOLIC
        alleles = Arrays.asList(Tref, symbolic);
        vc = basicBuilder.alleles(alleles).stop(snpLocStop).make();
        Assert.assertEquals(vc.getType(), VariantContext.Type.SYMBOLIC);
    }

    @Test
    public void testMultipleSNPAlleleOrdering() {
        final List<Allele> allelesNaturalOrder = Arrays.asList(Aref, C, T);
        final List<Allele> allelesUnnaturalOrder = Arrays.asList(Aref, T, C);
        VariantContext naturalVC = snpBuilder.alleles(allelesNaturalOrder).make();
        VariantContext unnaturalVC = snpBuilder.alleles(allelesUnnaturalOrder).make();
        Assert.assertEquals(new ArrayList<Allele>(naturalVC.getAlleles()), allelesNaturalOrder);
        Assert.assertEquals(new ArrayList<Allele>(unnaturalVC.getAlleles()), allelesUnnaturalOrder);
    }

    @Test
    public void testCreatingSNPVariantContext() {

        List<Allele> alleles = Arrays.asList(Aref, T);
        VariantContext vc = snpBuilder.alleles(alleles).make();

        Assert.assertEquals(vc.getChr(), snpLoc);
        Assert.assertEquals(vc.getStart(), snpLocStart);
        Assert.assertEquals(vc.getEnd(), snpLocStop);
        Assert.assertEquals(vc.getType(), VariantContext.Type.SNP);
        Assert.assertTrue(vc.isSNP());
        Assert.assertFalse(vc.isIndel());
        Assert.assertFalse(vc.isSimpleInsertion());
        Assert.assertFalse(vc.isSimpleDeletion());
        Assert.assertFalse(vc.isSimpleIndel());
        Assert.assertFalse(vc.isMixed());
        Assert.assertTrue(vc.isBiallelic());
        Assert.assertEquals(vc.getNAlleles(), 2);

        Assert.assertEquals(vc.getReference(), Aref);
        Assert.assertEquals(vc.getAlleles().size(), 2);
        Assert.assertEquals(vc.getAlternateAlleles().size(), 1);
        Assert.assertEquals(vc.getAlternateAllele(0), T);

        Assert.assertFalse(vc.hasGenotypes());

        Assert.assertEquals(vc.getSampleNames().size(), 0);
    }

    @Test
    public void testCreatingRefVariantContext() {
        List<Allele> alleles = Arrays.asList(Aref);
        VariantContext vc = snpBuilder.alleles(alleles).make();

        Assert.assertEquals(vc.getChr(), snpLoc);
        Assert.assertEquals(vc.getStart(), snpLocStart);
        Assert.assertEquals(vc.getEnd(), snpLocStop);
        Assert.assertEquals(VariantContext.Type.NO_VARIATION, vc.getType());
        Assert.assertFalse(vc.isSNP());
        Assert.assertFalse(vc.isIndel());
        Assert.assertFalse(vc.isSimpleInsertion());
        Assert.assertFalse(vc.isSimpleDeletion());
        Assert.assertFalse(vc.isSimpleIndel());
        Assert.assertFalse(vc.isMixed());
        Assert.assertFalse(vc.isBiallelic());
        Assert.assertEquals(vc.getNAlleles(), 1);

        Assert.assertEquals(vc.getReference(), Aref);
        Assert.assertEquals(vc.getAlleles().size(), 1);
        Assert.assertEquals(vc.getAlternateAlleles().size(), 0);
        //Assert.assertEquals(vc.getAlternateAllele(0), T);

        Assert.assertFalse(vc.hasGenotypes());
        Assert.assertEquals(vc.getSampleNames().size(), 0);
    }

    @Test
    public void testCreatingDeletionVariantContext() {
        List<Allele> alleles = Arrays.asList(ATCref, del);
        VariantContext vc = new VariantContextBuilder("test", delLoc, delLocStart, delLocStop, alleles).make();

        Assert.assertEquals(vc.getChr(), delLoc);
        Assert.assertEquals(vc.getStart(), delLocStart);
        Assert.assertEquals(vc.getEnd(), delLocStop);
        Assert.assertEquals(vc.getType(), VariantContext.Type.INDEL);
        Assert.assertFalse(vc.isSNP());
        Assert.assertTrue(vc.isIndel());
        Assert.assertFalse(vc.isSimpleInsertion());
        Assert.assertTrue(vc.isSimpleDeletion());
        Assert.assertTrue(vc.isSimpleIndel());
        Assert.assertFalse(vc.isMixed());
        Assert.assertTrue(vc.isBiallelic());
        Assert.assertEquals(vc.getNAlleles(), 2);

        Assert.assertEquals(vc.getReference(), ATCref);
        Assert.assertEquals(vc.getAlleles().size(), 2);
        Assert.assertEquals(vc.getAlternateAlleles().size(), 1);
        Assert.assertEquals(vc.getAlternateAllele(0), del);

        Assert.assertFalse(vc.hasGenotypes());

        Assert.assertEquals(vc.getSampleNames().size(), 0);
    }

    @Test
    public void testCreatingComplexSubstitutionVariantContext() {
        List<Allele> alleles = Arrays.asList(Tref, ATC);
        VariantContext vc = new VariantContextBuilder("test", insLoc, insLocStart, insLocStop, alleles).make();

        Assert.assertEquals(vc.getChr(), insLoc);
        Assert.assertEquals(vc.getStart(), insLocStart);
        Assert.assertEquals(vc.getEnd(), insLocStop);
        Assert.assertEquals(vc.getType(), VariantContext.Type.INDEL);
        Assert.assertFalse(vc.isSNP());
        Assert.assertTrue(vc.isIndel());
        Assert.assertFalse(vc.isSimpleInsertion());
        Assert.assertFalse(vc.isSimpleDeletion());
        Assert.assertFalse(vc.isSimpleIndel());
        Assert.assertFalse(vc.isMixed());
        Assert.assertTrue(vc.isBiallelic());
        Assert.assertEquals(vc.getNAlleles(), 2);

        Assert.assertEquals(vc.getReference(), Tref);
        Assert.assertEquals(vc.getAlleles().size(), 2);
        Assert.assertEquals(vc.getAlternateAlleles().size(), 1);
        Assert.assertEquals(vc.getAlternateAllele(0), ATC);

        Assert.assertFalse(vc.hasGenotypes());

        Assert.assertEquals(vc.getSampleNames().size(), 0);
    }

    @Test
    public void testMatchingAlleles() {
        List<Allele> alleles = Arrays.asList(ATCref, del);
        VariantContext vc = new VariantContextBuilder("test", delLoc, delLocStart, delLocStop, alleles).make();
        VariantContext vc2 = new VariantContextBuilder("test2", delLoc, delLocStart+12, delLocStop+12, alleles).make();

        Assert.assertTrue(vc.hasSameAllelesAs(vc2));
        Assert.assertTrue(vc.hasSameAlternateAllelesAs(vc2));
    }

    @Test
    public void testCreatingInsertionVariantContext() {
        List<Allele> alleles = Arrays.asList(delRef, ATC);
        VariantContext vc = insBuilder.alleles(alleles).make();

        Assert.assertEquals(vc.getChr(), insLoc);
        Assert.assertEquals(vc.getStart(), insLocStart);
        Assert.assertEquals(vc.getEnd(), insLocStop);
        Assert.assertEquals(vc.getType(), VariantContext.Type.INDEL);
        Assert.assertFalse(vc.isSNP());
        Assert.assertTrue(vc.isIndel());
        Assert.assertTrue(vc.isSimpleInsertion());
        Assert.assertFalse(vc.isSimpleDeletion());
        Assert.assertTrue(vc.isSimpleIndel());
        Assert.assertFalse(vc.isMixed());
        Assert.assertTrue(vc.isBiallelic());
        Assert.assertEquals(vc.getNAlleles(), 2);

        Assert.assertEquals(vc.getReference(), delRef);
        Assert.assertEquals(vc.getAlleles().size(), 2);
        Assert.assertEquals(vc.getAlternateAlleles().size(), 1);
        Assert.assertEquals(vc.getAlternateAllele(0), ATC);
        Assert.assertFalse(vc.hasGenotypes());

        Assert.assertEquals(vc.getSampleNames().size(), 0);
    }

    @Test
    public void testCreatingPartiallyCalledGenotype() {
        List<Allele> alleles = Arrays.asList(Aref, C);
        Genotype g = GenotypeBuilder.create("foo", Arrays.asList(C, Allele.NO_CALL));
        VariantContext vc = new VariantContextBuilder("test", snpLoc, snpLocStart, snpLocStop, alleles).genotypes(g).make();

        Assert.assertTrue(vc.isSNP());
        Assert.assertEquals(vc.getNAlleles(), 2);
        Assert.assertTrue(vc.hasGenotypes());
        Assert.assertFalse(vc.isMonomorphicInSamples());
        Assert.assertTrue(vc.isPolymorphicInSamples());
        Assert.assertEquals(vc.getGenotype("foo"), g);
        Assert.assertEquals(vc.getCalledChrCount(), 1); // we only have 1 called chromosomes, we exclude the NO_CALL one isn't called
        Assert.assertEquals(vc.getCalledChrCount(Aref), 0);
        Assert.assertEquals(vc.getCalledChrCount(C), 1);
        Assert.assertFalse(vc.getGenotype("foo").isHet());
        Assert.assertFalse(vc.getGenotype("foo").isHom());
        Assert.assertFalse(vc.getGenotype("foo").isNoCall());
        Assert.assertFalse(vc.getGenotype("foo").isHom());
        Assert.assertTrue(vc.getGenotype("foo").isMixed());
        Assert.assertEquals(vc.getGenotype("foo").getType(), GenotypeType.MIXED);
    }

    @Test (expectedExceptions = Exception.class)
    public void testBadConstructorArgs1() {
        new VariantContextBuilder("test", insLoc, insLocStart, insLocStop, Arrays.asList(delRef, ATCref)).make();
    }

    @Test (expectedExceptions = Exception.class)
    public void testBadConstructorArgs2() {
        new VariantContextBuilder("test", insLoc, insLocStart, insLocStop, Arrays.asList(delRef, del)).make();
    }

    @Test (expectedExceptions = Exception.class)
    public void testBadConstructorArgs3() {
        new VariantContextBuilder("test", insLoc, insLocStart, insLocStop, Arrays.asList(del)).make();
    }

    @Test (expectedExceptions = Throwable.class)
    public void testBadConstructorArgs4() {
        new VariantContextBuilder("test", insLoc, insLocStart, insLocStop, Collections.<Allele>emptyList()).make();
    }

    @Test (expectedExceptions = Exception.class)
    public void testBadConstructorArgsDuplicateAlleles1() {
        new VariantContextBuilder("test", insLoc, insLocStart, insLocStop, Arrays.asList(Aref, T, T)).make();
    }

    @Test (expectedExceptions = Exception.class)
    public void testBadConstructorArgsDuplicateAlleles2() {
        new VariantContextBuilder("test", insLoc, insLocStart, insLocStop, Arrays.asList(Aref, A)).make();
    }

    @Test (expectedExceptions = Throwable.class)
    public void testBadLoc1() {
        List<Allele> alleles = Arrays.asList(Aref, T, del);
        new VariantContextBuilder("test", delLoc, delLocStart, delLocStop, alleles).make();
    }

    @Test (expectedExceptions = Throwable.class)
    public void testBadID1() {
        new VariantContextBuilder("test", delLoc, delLocStart, delLocStop, Arrays.asList(Aref, T)).id(null).make();
    }

    @Test (expectedExceptions = Exception.class)
    public void testBadID2() {
        new VariantContextBuilder("test", delLoc, delLocStart, delLocStop, Arrays.asList(Aref, T)).id("").make();
    }

    @Test (expectedExceptions = Throwable.class)
    public void testBadPError() {
        new VariantContextBuilder("test", insLoc, insLocStart, insLocStop, Arrays.asList(delRef, ATCref)).log10PError(0.5).make();
    }

    @Test
    public void testAccessingSimpleSNPGenotypes() {
        List<Allele> alleles = Arrays.asList(Aref, T);

        Genotype g1 = GenotypeBuilder.create("AA", Arrays.asList(Aref, Aref));
        Genotype g2 = GenotypeBuilder.create("AT", Arrays.asList(Aref, T));
        Genotype g3 = GenotypeBuilder.create("TT", Arrays.asList(T, T));

        VariantContext vc = new VariantContextBuilder("test", snpLoc, snpLocStart, snpLocStop, alleles)
                .genotypes(g1, g2, g3).make();

        Assert.assertTrue(vc.hasGenotypes());
        Assert.assertFalse(vc.isMonomorphicInSamples());
        Assert.assertTrue(vc.isPolymorphicInSamples());
        Assert.assertEquals(vc.getSampleNames().size(), 3);

        Assert.assertEquals(vc.getGenotypes().size(), 3);
        Assert.assertEquals(vc.getGenotypes().get("AA"), g1);
        Assert.assertEquals(vc.getGenotype("AA"), g1);
        Assert.assertEquals(vc.getGenotypes().get("AT"), g2);
        Assert.assertEquals(vc.getGenotype("AT"), g2);
        Assert.assertEquals(vc.getGenotypes().get("TT"), g3);
        Assert.assertEquals(vc.getGenotype("TT"), g3);

        Assert.assertTrue(vc.hasGenotype("AA"));
        Assert.assertTrue(vc.hasGenotype("AT"));
        Assert.assertTrue(vc.hasGenotype("TT"));
        Assert.assertFalse(vc.hasGenotype("foo"));
        Assert.assertFalse(vc.hasGenotype("TTT"));
        Assert.assertFalse(vc.hasGenotype("at"));
        Assert.assertFalse(vc.hasGenotype("tt"));

        Assert.assertEquals(vc.getCalledChrCount(), 6);
        Assert.assertEquals(vc.getCalledChrCount(Aref), 3);
        Assert.assertEquals(vc.getCalledChrCount(T), 3);
    }

    @Test
    public void testAccessingCompleteGenotypes() {
        List<Allele> alleles = Arrays.asList(Aref, T, ATC);

        Genotype g1 = GenotypeBuilder.create("AA", Arrays.asList(Aref, Aref));
        Genotype g2 = GenotypeBuilder.create("AT", Arrays.asList(Aref, T));
        Genotype g3 = GenotypeBuilder.create("TT", Arrays.asList(T, T));
        Genotype g4 = GenotypeBuilder.create("Td", Arrays.asList(T, ATC));
        Genotype g5 = GenotypeBuilder.create("dd", Arrays.asList(ATC, ATC));
        Genotype g6 = GenotypeBuilder.create("..", Arrays.asList(Allele.NO_CALL, Allele.NO_CALL));

        VariantContext vc = new VariantContextBuilder("test", snpLoc, snpLocStart, snpLocStop, alleles)
                .genotypes(g1, g2, g3, g4, g5, g6).make();

        Assert.assertTrue(vc.hasGenotypes());
        Assert.assertFalse(vc.isMonomorphicInSamples());
        Assert.assertTrue(vc.isPolymorphicInSamples());
        Assert.assertEquals(vc.getGenotypes().size(), 6);

        Assert.assertEquals(3, vc.getGenotypes(Arrays.asList("AA", "Td", "dd")).size());

        Assert.assertEquals(10, vc.getCalledChrCount());
        Assert.assertEquals(3, vc.getCalledChrCount(Aref));
        Assert.assertEquals(4, vc.getCalledChrCount(T));
        Assert.assertEquals(3, vc.getCalledChrCount(ATC));
        Assert.assertEquals(2, vc.getCalledChrCount(Allele.NO_CALL));
    }

    @Test
    public void testAccessingRefGenotypes() {
        List<Allele> alleles1 = Arrays.asList(Aref, T);
        List<Allele> alleles2 = Arrays.asList(Aref);
        List<Allele> alleles3 = Arrays.asList(Aref, T);
        for ( List<Allele> alleles : Arrays.asList(alleles1, alleles2, alleles3)) {
            Genotype g1 = GenotypeBuilder.create("AA1", Arrays.asList(Aref, Aref));
            Genotype g2 = GenotypeBuilder.create("AA2", Arrays.asList(Aref, Aref));
            Genotype g3 = GenotypeBuilder.create("..", Arrays.asList(Allele.NO_CALL, Allele.NO_CALL));
            VariantContext vc = new VariantContextBuilder("test", snpLoc, snpLocStart, snpLocStop, alleles)
                    .genotypes(g1, g2, g3).make();

            Assert.assertTrue(vc.hasGenotypes());
            Assert.assertTrue(vc.isMonomorphicInSamples());
            Assert.assertFalse(vc.isPolymorphicInSamples());
            Assert.assertEquals(vc.getGenotypes().size(), 3);

            Assert.assertEquals(4, vc.getCalledChrCount());
            Assert.assertEquals(4, vc.getCalledChrCount(Aref));
            Assert.assertEquals(0, vc.getCalledChrCount(T));
            Assert.assertEquals(2, vc.getCalledChrCount(Allele.NO_CALL));
        }
    }

    @Test
    public void testFilters() {
        List<Allele> alleles = Arrays.asList(Aref, T);
        Genotype g1 = GenotypeBuilder.create("AA", Arrays.asList(Aref, Aref));
        Genotype g2 = GenotypeBuilder.create("AT", Arrays.asList(Aref, T));

        VariantContext vc = new VariantContextBuilder("test", snpLoc, snpLocStart, snpLocStop, alleles).genotypes(g1, g2).make();

        Assert.assertTrue(vc.isNotFiltered());
        Assert.assertFalse(vc.isFiltered());
        Assert.assertEquals(0, vc.getFilters().size());
        Assert.assertFalse(vc.filtersWereApplied());
        Assert.assertNull(vc.getFiltersMaybeNull());

        vc = new VariantContextBuilder(vc).filters("BAD_SNP_BAD!").make();

        Assert.assertFalse(vc.isNotFiltered());
        Assert.assertTrue(vc.isFiltered());
        Assert.assertEquals(1, vc.getFilters().size());
        Assert.assertTrue(vc.filtersWereApplied());
        Assert.assertNotNull(vc.getFiltersMaybeNull());

        Set<String> filters = new HashSet<String>(Arrays.asList("BAD_SNP_BAD!", "REALLY_BAD_SNP", "CHRIST_THIS_IS_TERRIBLE"));
        vc = new VariantContextBuilder(vc).filters(filters).make();

        Assert.assertFalse(vc.isNotFiltered());
        Assert.assertTrue(vc.isFiltered());
        Assert.assertEquals(3, vc.getFilters().size());
        Assert.assertTrue(vc.filtersWereApplied());
        Assert.assertNotNull(vc.getFiltersMaybeNull());
    }

    @Test
    public void testGetGenotypeCounts() {
        List<Allele> alleles = Arrays.asList(Aref, T);
        Genotype g1 = GenotypeBuilder.create("AA", Arrays.asList(Aref, Aref));
        Genotype g2 = GenotypeBuilder.create("AT", Arrays.asList(Aref, T));
        Genotype g3 = GenotypeBuilder.create("TT", Arrays.asList(T, T));
        Genotype g4 = GenotypeBuilder.create("A.", Arrays.asList(Aref, Allele.NO_CALL));
        Genotype g5 = GenotypeBuilder.create("..", Arrays.asList(Allele.NO_CALL, Allele.NO_CALL));

        // we need to create a new VariantContext each time
        VariantContext vc = new VariantContextBuilder("foo", snpLoc, snpLocStart, snpLocStop, alleles).genotypes(g1,g2,g3,g4,g5).make();
        Assert.assertEquals(1, vc.getHetCount());
        vc = new VariantContextBuilder("foo", snpLoc, snpLocStart, snpLocStop, alleles).genotypes(g1,g2,g3,g4,g5).make();
        Assert.assertEquals(1, vc.getHomRefCount());
        vc = new VariantContextBuilder("foo", snpLoc, snpLocStart, snpLocStop, alleles).genotypes(g1,g2,g3,g4,g5).make();
        Assert.assertEquals(1, vc.getHomVarCount());
        vc = new VariantContextBuilder("foo", snpLoc, snpLocStart, snpLocStop, alleles).genotypes(g1,g2,g3,g4,g5).make();
        Assert.assertEquals(1, vc.getMixedCount());
        vc = new VariantContextBuilder("foo", snpLoc, snpLocStart, snpLocStop, alleles).genotypes(g1,g2,g3,g4,g5).make();
        Assert.assertEquals(1, vc.getNoCallCount());
    }

    @Test
    public void testVCFfromGenotypes() {
        List<Allele> alleles = Arrays.asList(Aref, C, T);
        Genotype g1 = GenotypeBuilder.create("AA", Arrays.asList(Aref, Aref));
        Genotype g2 = GenotypeBuilder.create("AT", Arrays.asList(Aref, T));
        Genotype g3 = GenotypeBuilder.create("TT", Arrays.asList(T, T));
        Genotype g4 = GenotypeBuilder.create("..", Arrays.asList(Allele.NO_CALL, Allele.NO_CALL));
        Genotype g5 = GenotypeBuilder.create("AC", Arrays.asList(Aref, C));
        VariantContext vc = new VariantContextBuilder("genotypes", snpLoc, snpLocStart, snpLocStop, alleles).genotypes(g1,g2,g3,g4,g5).make();

        VariantContext vc12 = vc.subContextFromSamples(new HashSet<String>(Arrays.asList(g1.getSampleName(), g2.getSampleName())), true);
        VariantContext vc1 = vc.subContextFromSamples(new HashSet<String>(Arrays.asList(g1.getSampleName())), true);
        VariantContext vc23 = vc.subContextFromSamples(new HashSet<String>(Arrays.asList(g2.getSampleName(), g3.getSampleName())), true);
        VariantContext vc4 = vc.subContextFromSamples(new HashSet<String>(Arrays.asList(g4.getSampleName())), true);
        VariantContext vc14 = vc.subContextFromSamples(new HashSet<String>(Arrays.asList(g1.getSampleName(), g4.getSampleName())), true);
        VariantContext vc125 = vc.subContextFromSamples(new HashSet<String>(Arrays.asList(g1.getSampleName(), g2.getSampleName(), g5.getSampleName())), true);

        Assert.assertTrue(vc12.isPolymorphicInSamples());
        Assert.assertTrue(vc23.isPolymorphicInSamples());
        Assert.assertTrue(vc1.isMonomorphicInSamples());
        Assert.assertTrue(vc4.isMonomorphicInSamples());
        Assert.assertTrue(vc14.isMonomorphicInSamples());
        Assert.assertTrue(vc125.isPolymorphicInSamples());

        Assert.assertTrue(vc12.isSNP());
        Assert.assertTrue(vc12.isVariant());
        Assert.assertTrue(vc12.isBiallelic());

        Assert.assertFalse(vc1.isSNP());
        Assert.assertFalse(vc1.isVariant());
        Assert.assertFalse(vc1.isBiallelic());

        Assert.assertTrue(vc23.isSNP());
        Assert.assertTrue(vc23.isVariant());
        Assert.assertTrue(vc23.isBiallelic());

        Assert.assertFalse(vc4.isSNP());
        Assert.assertFalse(vc4.isVariant());
        Assert.assertFalse(vc4.isBiallelic());

        Assert.assertFalse(vc14.isSNP());
        Assert.assertFalse(vc14.isVariant());
        Assert.assertFalse(vc14.isBiallelic());

        Assert.assertTrue(vc125.isSNP());
        Assert.assertTrue(vc125.isVariant());
        Assert.assertFalse(vc125.isBiallelic());

        Assert.assertEquals(3, vc12.getCalledChrCount(Aref));
        Assert.assertEquals(1, vc23.getCalledChrCount(Aref));
        Assert.assertEquals(2, vc1.getCalledChrCount(Aref));
        Assert.assertEquals(0, vc4.getCalledChrCount(Aref));
        Assert.assertEquals(2, vc14.getCalledChrCount(Aref));
        Assert.assertEquals(4, vc125.getCalledChrCount(Aref));
    }

    public void testGetGenotypeMethods() {
        Genotype g1 = GenotypeBuilder.create("AA", Arrays.asList(Aref, Aref));
        Genotype g2 = GenotypeBuilder.create("AT", Arrays.asList(Aref, T));
        Genotype g3 = GenotypeBuilder.create("TT", Arrays.asList(T, T));
        GenotypesContext gc = GenotypesContext.create(g1, g2, g3);
        VariantContext vc = new VariantContextBuilder("genotypes", snpLoc, snpLocStart, snpLocStop, Arrays.asList(Aref, T)).genotypes(gc).make();

        Assert.assertEquals(vc.getGenotype("AA"), g1);
        Assert.assertEquals(vc.getGenotype("AT"), g2);
        Assert.assertEquals(vc.getGenotype("TT"), g3);
        Assert.assertEquals(vc.getGenotype("CC"), null);

        Assert.assertEquals(vc.getGenotypes(), gc);
        Assert.assertEquals(vc.getGenotypes(Arrays.asList("AA", "AT")), Arrays.asList(g1, g2));
        Assert.assertEquals(vc.getGenotypes(Arrays.asList("AA", "TT")), Arrays.asList(g1, g3));
        Assert.assertEquals(vc.getGenotypes(Arrays.asList("AA", "AT", "TT")), Arrays.asList(g1, g2, g3));
        Assert.assertEquals(vc.getGenotypes(Arrays.asList("AA", "AT", "CC")), Arrays.asList(g1, g2));

        Assert.assertEquals(vc.getGenotype(0), g1);
        Assert.assertEquals(vc.getGenotype(1), g2);
        Assert.assertEquals(vc.getGenotype(2), g3);
    }

    // --------------------------------------------------------------------------------
    //
    // Test allele merging
    //
    // --------------------------------------------------------------------------------

    private class GetAllelesTest {
        List<Allele> alleles;
        String name;

        private GetAllelesTest(String name, Allele... arg) {
            this.name = name;
            this.alleles = Arrays.asList(arg);
        }

        public String toString() {
            return String.format("%s input=%s", name, alleles);
        }
    }

    @DataProvider(name = "getAlleles")
    public Object[][] mergeAllelesData() {
        List<Object[]> tests = new ArrayList<Object[]>();

        tests.add(new Object[]{new GetAllelesTest("A*",   Aref)});
        tests.add(new Object[]{new GetAllelesTest("A*/C", Aref, C)});
        tests.add(new Object[]{new GetAllelesTest("A*/C/T", Aref, C, T)});
        tests.add(new Object[]{new GetAllelesTest("A*/T/C", Aref, T, C)});
        tests.add(new Object[]{new GetAllelesTest("A*/C/T/ATC", Aref, C, T, ATC)});
        tests.add(new Object[]{new GetAllelesTest("A*/T/C/ATC", Aref, T, C, ATC)});
        tests.add(new Object[]{new GetAllelesTest("A*/ATC/T/C", Aref, ATC, T, C)});

        return tests.toArray(new Object[][]{});
    }

    @Test(dataProvider = "getAlleles")
    public void testMergeAlleles(GetAllelesTest cfg) {
        final List<Allele> altAlleles = cfg.alleles.subList(1, cfg.alleles.size());
        final VariantContext vc = new VariantContextBuilder("test", snpLoc, snpLocStart, snpLocStop, cfg.alleles).make();

        Assert.assertEquals(vc.getAlleles(), cfg.alleles, "VC alleles not the same as input alleles");
        Assert.assertEquals(vc.getNAlleles(), cfg.alleles.size(), "VC getNAlleles not the same as input alleles size");
        Assert.assertEquals(vc.getAlternateAlleles(), altAlleles, "VC alt alleles not the same as input alt alleles");


        for ( int i = 0; i < cfg.alleles.size(); i++ ) {
            final Allele inputAllele = cfg.alleles.get(i);

            Assert.assertTrue(vc.hasAllele(inputAllele));
            if ( inputAllele.isReference() ) {
                final Allele nonRefVersion = Allele.create(inputAllele.getBases(), false);
                Assert.assertTrue(vc.hasAllele(nonRefVersion, true));
                Assert.assertFalse(vc.hasAllele(nonRefVersion, false));
            }

            Assert.assertEquals(inputAllele, vc.getAllele(inputAllele.getBaseString()));
            Assert.assertEquals(inputAllele, vc.getAllele(inputAllele.getBases()));

            if ( i > 0 ) { // it's an alt allele
                Assert.assertEquals(inputAllele, vc.getAlternateAllele(i-1));
            }
        }

        final Allele missingAllele = Allele.create("AACCGGTT"); // does not exist
        Assert.assertNull(vc.getAllele(missingAllele.getBases()));
        Assert.assertFalse(vc.hasAllele(missingAllele));
        Assert.assertFalse(vc.hasAllele(missingAllele, true));
    }

    private class SitesAndGenotypesVC {
        VariantContext vc, copy;
        String name;

        private SitesAndGenotypesVC(String name, VariantContext original) {
            this.name = name;
            this.vc = original;
            this.copy = new VariantContextBuilder(original).make();
        }

        public String toString() {
            return String.format("%s input=%s", name, vc);
        }
    }

    @DataProvider(name = "SitesAndGenotypesVC")
    public Object[][] MakeSitesAndGenotypesVCs() {
        Genotype g1 = GenotypeBuilder.create("AA", Arrays.asList(Aref, Aref));
        Genotype g2 = GenotypeBuilder.create("AT", Arrays.asList(Aref, T));
        Genotype g3 = GenotypeBuilder.create("TT", Arrays.asList(T, T));

        VariantContext sites = new VariantContextBuilder("sites", snpLoc, snpLocStart, snpLocStop, Arrays.asList(Aref, T)).make();
        VariantContext genotypes = new VariantContextBuilder(sites).source("genotypes").genotypes(g1, g2, g3).make();

        List<Object[]> tests = new ArrayList<Object[]>();

        tests.add(new Object[]{new SitesAndGenotypesVC("sites", sites)});
        tests.add(new Object[]{new SitesAndGenotypesVC("genotypes", genotypes)});

        return tests.toArray(new Object[][]{});
    }

    // --------------------------------------------------------------------------------
    //
    // Test modifying routines
    //
    // --------------------------------------------------------------------------------
    @Test(dataProvider = "SitesAndGenotypesVC")
    public void runModifyVCTests(SitesAndGenotypesVC cfg) {
        VariantContext modified = new VariantContextBuilder(cfg.vc).loc("chr2", 123, 123).make();
        Assert.assertEquals(modified.getChr(), "chr2");
        Assert.assertEquals(modified.getStart(), 123);
        Assert.assertEquals(modified.getEnd(), 123);

        modified = new VariantContextBuilder(cfg.vc).id("newID").make();
        Assert.assertEquals(modified.getID(), "newID");

        Set<String> newFilters = Collections.singleton("newFilter");
        modified = new VariantContextBuilder(cfg.vc).filters(newFilters).make();
        Assert.assertEquals(modified.getFilters(), newFilters);

        // test the behavior when the builder's attribute object is null
        modified = new VariantContextBuilder(modified).attributes(null).make();
        Assert.assertTrue(modified.getAttributes().isEmpty());
        modified = new VariantContextBuilder(modified).attributes(null).rmAttribute("AC").make();
        Assert.assertTrue(modified.getAttributes().isEmpty());
        modified = new VariantContextBuilder(modified).attributes(null).attribute("AC", 1).make();
        Assert.assertEquals(modified.getAttribute("AC"), 1);

        // test the behavior when the builder's attribute object is not initialized
        modified = new VariantContextBuilder(modified.getSource(), modified.getChr(), modified.getStart(), modified.getEnd(), modified.getAlleles()).attribute("AC", 1).make();

        // test normal attribute modification
        modified = new VariantContextBuilder(cfg.vc).attribute("AC", 1).make();
        Assert.assertEquals(modified.getAttribute("AC"), 1);
        modified = new VariantContextBuilder(modified).attribute("AC", 2).make();
        Assert.assertEquals(modified.getAttribute("AC"), 2);

        Genotype g1 = GenotypeBuilder.create("AA2", Arrays.asList(Aref, Aref));
        Genotype g2 = GenotypeBuilder.create("AT2", Arrays.asList(Aref, T));
        Genotype g3 = GenotypeBuilder.create("TT2", Arrays.asList(T, T));
        GenotypesContext gc = GenotypesContext.create(g1,g2,g3);
        modified = new VariantContextBuilder(cfg.vc).genotypes(gc).make();
        Assert.assertEquals(modified.getGenotypes(), gc);
        modified = new VariantContextBuilder(cfg.vc).noGenotypes().make();
        Assert.assertTrue(modified.getGenotypes().isEmpty());

        // test that original hasn't changed
        Assert.assertEquals(cfg.vc.getChr(), cfg.copy.getChr());
        Assert.assertEquals(cfg.vc.getStart(), cfg.copy.getStart());
        Assert.assertEquals(cfg.vc.getEnd(), cfg.copy.getEnd());
        Assert.assertEquals(cfg.vc.getAlleles(), cfg.copy.getAlleles());
        Assert.assertEquals(cfg.vc.getAttributes(), cfg.copy.getAttributes());
        Assert.assertEquals(cfg.vc.getID(), cfg.copy.getID());
        Assert.assertEquals(cfg.vc.getGenotypes(), cfg.copy.getGenotypes());
        Assert.assertEquals(cfg.vc.getLog10PError(), cfg.copy.getLog10PError());
        Assert.assertEquals(cfg.vc.getFilters(), cfg.copy.getFilters());
    }

    // --------------------------------------------------------------------------------
    //
    // Test subcontext
    //
    // --------------------------------------------------------------------------------
    private class SubContextTest {
        Set<String> samples;
        boolean updateAlleles;

        private SubContextTest(Collection<String> samples, boolean updateAlleles) {
            this.samples = new HashSet<String>(samples);
            this.updateAlleles = updateAlleles;
        }

        public String toString() {
            return String.format("%s samples=%s updateAlleles=%b", "SubContextTest", samples, updateAlleles);
        }
    }

    @DataProvider(name = "SubContextTest")
    public Object[][] MakeSubContextTest() {
        List<Object[]> tests = new ArrayList<Object[]>();

        for ( boolean updateAlleles : Arrays.asList(true, false)) {
            tests.add(new Object[]{new SubContextTest(Collections.<String>emptySet(), updateAlleles)});
            tests.add(new Object[]{new SubContextTest(Collections.singleton("MISSING"), updateAlleles)});
            tests.add(new Object[]{new SubContextTest(Collections.singleton("AA"), updateAlleles)});
            tests.add(new Object[]{new SubContextTest(Collections.singleton("AT"), updateAlleles)});
            tests.add(new Object[]{new SubContextTest(Collections.singleton("TT"), updateAlleles)});
            tests.add(new Object[]{new SubContextTest(Arrays.asList("AA", "AT"), updateAlleles)});
            tests.add(new Object[]{new SubContextTest(Arrays.asList("AA", "AT", "TT"), updateAlleles)});
            tests.add(new Object[]{new SubContextTest(Arrays.asList("AA", "AT", "MISSING"), updateAlleles)});
            tests.add(new Object[]{new SubContextTest(Arrays.asList("AA", "AT", "TT", "MISSING"), updateAlleles)});
            tests.add(new Object[]{new SubContextTest(Arrays.asList("AA", "AT", "AC"), updateAlleles)});
        }

        return tests.toArray(new Object[][]{});
    }

    @Test(dataProvider = "SubContextTest")
    public void runSubContextTest(SubContextTest cfg) {
        Genotype g1 = GenotypeBuilder.create("AA", Arrays.asList(Aref, Aref));
        Genotype g2 = GenotypeBuilder.create("AT", Arrays.asList(Aref, T));
        Genotype g3 = GenotypeBuilder.create("TT", Arrays.asList(T, T));
        Genotype g4 = GenotypeBuilder.create("AC", Arrays.asList(Aref, C));

        GenotypesContext gc = GenotypesContext.create(g1, g2, g3, g4);
        VariantContext vc = new VariantContextBuilder("genotypes", snpLoc, snpLocStart, snpLocStop, Arrays.asList(Aref, C, T)).genotypes(gc).make();
        VariantContext sub = vc.subContextFromSamples(cfg.samples, cfg.updateAlleles);

        // unchanged attributes should be the same
        Assert.assertEquals(sub.getChr(), vc.getChr());
        Assert.assertEquals(sub.getStart(), vc.getStart());
        Assert.assertEquals(sub.getEnd(), vc.getEnd());
        Assert.assertEquals(sub.getLog10PError(), vc.getLog10PError());
        Assert.assertEquals(sub.getFilters(), vc.getFilters());
        Assert.assertEquals(sub.getID(), vc.getID());
        Assert.assertEquals(sub.getAttributes(), vc.getAttributes());

        Set<Genotype> expectedGenotypes = new HashSet<Genotype>();
        if ( cfg.samples.contains(g1.getSampleName()) ) expectedGenotypes.add(g1);
        if ( cfg.samples.contains(g2.getSampleName()) ) expectedGenotypes.add(g2);
        if ( cfg.samples.contains(g3.getSampleName()) ) expectedGenotypes.add(g3);
        if ( cfg.samples.contains(g4.getSampleName()) ) expectedGenotypes.add(g4);
        GenotypesContext expectedGC = GenotypesContext.copy(expectedGenotypes);

        // these values depend on the results of sub
        if ( cfg.updateAlleles ) {
            // do the work to see what alleles should be here, and which not
            List<Allele> expectedAlleles = new ArrayList<Allele>();
            expectedAlleles.add(Aref);

            Set<Allele> genotypeAlleles = new HashSet<Allele>();
            for ( final Genotype g : expectedGC )
                genotypeAlleles.addAll(g.getAlleles());
            genotypeAlleles.remove(Aref);

            // ensure original allele order
            for (Allele allele: vc.getAlleles())
                if (genotypeAlleles.contains(allele))
                    expectedAlleles.add(allele);

            Assert.assertEquals(sub.getAlleles(), expectedAlleles);
        } else {
            // not updating alleles -- should be the same
            Assert.assertEquals(sub.getAlleles(), vc.getAlleles());
        }

        // same sample names => success
        Assert.assertTrue(sub.getGenotypes().getSampleNames().equals(expectedGC.getSampleNames()));
    }

    // --------------------------------------------------------------------------------
    //
    // Test sample name functions
    //
    // --------------------------------------------------------------------------------
    private class SampleNamesTest {
        List<String> sampleNames;
        List<String> sampleNamesInOrder;

        private SampleNamesTest(List<String> sampleNames, List<String> sampleNamesInOrder) {
            this.sampleNamesInOrder = sampleNamesInOrder;
            this.sampleNames = sampleNames;
        }

        public String toString() {
            return String.format("%s samples=%s order=%s", "SampleNamesTest", sampleNames, sampleNamesInOrder);
        }
    }

    @DataProvider(name = "SampleNamesTest")
    public Object[][] MakeSampleNamesTest() {
        List<Object[]> tests = new ArrayList<Object[]>();

        tests.add(new Object[]{new SampleNamesTest(Arrays.asList("1"), Arrays.asList("1"))});
        tests.add(new Object[]{new SampleNamesTest(Arrays.asList("2", "1"), Arrays.asList("1", "2"))});
        tests.add(new Object[]{new SampleNamesTest(Arrays.asList("1", "2"), Arrays.asList("1", "2"))});
        tests.add(new Object[]{new SampleNamesTest(Arrays.asList("1", "2", "3"), Arrays.asList("1", "2", "3"))});
        tests.add(new Object[]{new SampleNamesTest(Arrays.asList("2", "1", "3"), Arrays.asList("1", "2", "3"))});
        tests.add(new Object[]{new SampleNamesTest(Arrays.asList("2", "3", "1"), Arrays.asList("1", "2", "3"))});
        tests.add(new Object[]{new SampleNamesTest(Arrays.asList("3", "1", "2"), Arrays.asList("1", "2", "3"))});
        tests.add(new Object[]{new SampleNamesTest(Arrays.asList("3", "2", "1"), Arrays.asList("1", "2", "3"))});
        tests.add(new Object[]{new SampleNamesTest(Arrays.asList("NA2", "NA1"), Arrays.asList("NA1", "NA2"))});

        return tests.toArray(new Object[][]{});
    }

    private final static void assertGenotypesAreInOrder(Iterable<Genotype> gIt, List<String> names) {
        int i = 0;
        for ( final Genotype g : gIt ) {
            Assert.assertEquals(g.getSampleName(), names.get(i), "Unexpected genotype ordering");
            i++;
        }
    }


    @Test(dataProvider = "SampleNamesTest")
    public void runSampleNamesTest(SampleNamesTest cfg) {
        GenotypesContext gc = GenotypesContext.create(cfg.sampleNames.size());
        for ( final String name : cfg.sampleNames ) {
            gc.add(GenotypeBuilder.create(name, Arrays.asList(Aref, T)));
        }

        VariantContext vc = new VariantContextBuilder("genotypes", snpLoc, snpLocStart, snpLocStop, Arrays.asList(Aref, T)).genotypes(gc).make();

        // same sample names => success
        Assert.assertTrue(vc.getSampleNames().equals(new HashSet<String>(cfg.sampleNames)), "vc.getSampleNames() = " + vc.getSampleNames());
        Assert.assertEquals(vc.getSampleNamesOrderedByName(), cfg.sampleNamesInOrder, "vc.getSampleNamesOrderedByName() = " + vc.getSampleNamesOrderedByName());

        assertGenotypesAreInOrder(vc.getGenotypesOrderedByName(), cfg.sampleNamesInOrder);
        assertGenotypesAreInOrder(vc.getGenotypesOrderedBy(cfg.sampleNames), cfg.sampleNames);
    }

    @Test
    public void testGenotypeCounting() {
        Genotype noCall = GenotypeBuilder.create("nocall", Arrays.asList(Allele.NO_CALL));
        Genotype mixed  = GenotypeBuilder.create("mixed", Arrays.asList(Aref, Allele.NO_CALL));
        Genotype homRef = GenotypeBuilder.create("homRef", Arrays.asList(Aref, Aref));
        Genotype het    = GenotypeBuilder.create("het", Arrays.asList(Aref, T));
        Genotype homVar = GenotypeBuilder.create("homVar", Arrays.asList(T, T));

        List<Genotype> allGenotypes = Arrays.asList(noCall, mixed, homRef, het, homVar);
        final int nCycles = allGenotypes.size() * 10;

        for ( int i = 0; i < nCycles; i++ ) {
            int nNoCall = 0, nNoCallAlleles = 0, nA = 0, nT = 0, nMixed = 0, nHomRef = 0, nHet = 0, nHomVar = 0;
            int nSamples = 0;
            GenotypesContext gc = GenotypesContext.create();
            for ( int j = 0; j < i; j++ ) {
                nSamples++;
                Genotype g = allGenotypes.get(j % allGenotypes.size());
                final String name = String.format("%s_%d%d", g.getSampleName(), i, j);
                gc.add(GenotypeBuilder.create(name, g.getAlleles()));
                switch ( g.getType() ) {
                    case NO_CALL: nNoCall++; nNoCallAlleles++; break;
                    case HOM_REF: nA += 2; nHomRef++; break;
                    case HET: nA++; nT++; nHet++; break;
                    case HOM_VAR: nT += 2; nHomVar++; break;
                    case MIXED: nA++; nNoCallAlleles++; nMixed++; break;
                    default: throw new RuntimeException("Unexpected genotype type " + g.getType());
                }

            }

            VariantContext vc = new VariantContextBuilder("genotypes", snpLoc, snpLocStart, snpLocStop, Arrays.asList(Aref, T)).genotypes(gc).make();
            Assert.assertEquals(vc.getNSamples(), nSamples);
            if ( nSamples > 0 ) {
                Assert.assertEquals(vc.isPolymorphicInSamples(), nT > 0);
                Assert.assertEquals(vc.isMonomorphicInSamples(), nT == 0);
            }
            Assert.assertEquals(vc.getCalledChrCount(), nA + nT);

            Assert.assertEquals(vc.getCalledChrCount(Allele.NO_CALL), nNoCallAlleles);
            Assert.assertEquals(vc.getCalledChrCount(Aref), nA);
            Assert.assertEquals(vc.getCalledChrCount(T), nT);

            Assert.assertEquals(vc.getNoCallCount(), nNoCall);
            Assert.assertEquals(vc.getHomRefCount(), nHomRef);
            Assert.assertEquals(vc.getHetCount(), nHet);
            Assert.assertEquals(vc.getHomVarCount(), nHomVar);
            Assert.assertEquals(vc.getMixedCount(), nMixed);
        }
    }
    @Test
    public void testSetAttribute() {
    	VariantContextBuilder builder = new VariantContextBuilder();
    	builder.attribute("Test", "value");
    }

    // --------------------------------------------------------------------------------
    //
    // Test validation methods
    //
    // --------------------------------------------------------------------------------

    // create a VariantContext object for various alleles and genotypes to test against
    private VariantContext createTestVariantContext(final List<Allele> alleles, final Map<String, Object> attributes, final Genotype... genotypes) {
        final EnumSet<VariantContext.Validation> toValidate = EnumSet.noneOf(VariantContext.Validation.class);
        final Set<String> filters = null;
        final boolean fullyDecoded = false;

        // no genotypes needs to use GenotypesContext.NO_GENOTYPES,
        // otherwise we build up a GenotypesContext from the passed genotypes
        final GenotypesContext gc;
        if (genotypes == null || genotypes.length == 0) {
            gc = GenotypesContext.NO_GENOTYPES;
        } else {
            gc = new GenotypesContext();
            for (final Genotype genotype : genotypes) {
                gc.add(genotype);
            }
        }
        // most of the fields are not important to the tests, we just need alleles and gc set properly
        return new VariantContext("genotypes", VCFConstants.EMPTY_ID_FIELD, snpLoc, snpLocStart, snpLocStop, alleles,
                gc, VariantContext.NO_LOG10_PERROR, filters, attributes,
                fullyDecoded, toValidate);
    }

    // validateReferenceBases: PASS conditions
    @DataProvider
    public Object[][] testValidateReferencesBasesDataProvider() {
        final VariantContext vc = createValidateReferencesContext(Arrays.asList(Aref, T));
        return new Object[][]{
                // null ref will pass validation
                {vc, null, A},
                // A vs A-ref will pass validation
                {vc, Aref, A}
        };
    }
    @Test(dataProvider = "testValidateReferencesBasesDataProvider")
    public void testValidateReferenceBases(final VariantContext vc, final Allele allele1, final Allele allele2) {
        // validateReferenceBases throws exceptions if it fails, so no Asserts here...
        vc.validateReferenceBases(allele1, allele2);
    }
    // validateReferenceBases: FAIL conditions
    @DataProvider
    public Object[][] testValidateReferencesBasesFailureDataProvider() {
        final VariantContext vc = createValidateReferencesContext(Arrays.asList(Aref, T));

        final Allele symbolicAllele = Allele.create("<A>");

        return new Object[][]{
                // T vs A-ref will NOT pass validation
                {vc, Aref, T},
                // symbolic alleles will NOT pass validation
                {vc, Aref, symbolicAllele}
        };
    }
    @Test(dataProvider = "testValidateReferencesBasesFailureDataProvider", expectedExceptions = TribbleException.class)
    public void testValidateReferenceBasesFailure(final VariantContext vc, final Allele allele1, final Allele allele2) {
        // validateReferenceBases throws exceptions if it fails, so no Asserts here...
        vc.validateReferenceBases(allele1, allele2);
    }
    private VariantContext createValidateReferencesContext(final List<Allele> alleles) {
        return createTestVariantContext(alleles, null);
    }


    // validateRSIDs: PASS conditions
    @DataProvider
    public Object[][] testValidateRSIDsDataProvider() {
        final VariantContext vcNoId = createTestVariantContextRsIds(VCFConstants.EMPTY_ID_FIELD);
        final VariantContext vcNonRs = createTestVariantContextRsIds("abc456");
        final VariantContext vc = createTestVariantContextRsIds("rs123");
        final VariantContext vcMultipleRs = createTestVariantContextRsIds("rs123;rs456;rs789");

        return new Object[][]{
                // no ID will pass validation
                {vcNoId, makeRsIDsSet("rs123")},
                // non-rs ID will pass validation
                {vcNonRs, makeRsIDsSet("rs123")},
                // matching ID will pass validation
                {vc, makeRsIDsSet("rs123")},
                // null rsIDs to check will pass validation
                {vc, null},
                // context with multiple rsIDs that are contained within the rsID list will pass
                {vcMultipleRs, makeRsIDsSet("rs123", "rs321", "rs456", "rs654", "rs789")}
        };
    }
    @Test(dataProvider = "testValidateRSIDsDataProvider")
    public void testValidateRSIDs(final VariantContext vc, final Set<String> rsIDs) {
        // validateRSIDs throws exceptions if it fails, so no Asserts here...
        vc.validateRSIDs(rsIDs);
    }
    // validateRSIDs: FAIL conditions
    @DataProvider
    public Object[][] testValidateRSIDsFailureDataProvider() {
        final VariantContext vc = createTestVariantContextRsIds("rs123");
        final VariantContext vcMultipleRs = createTestVariantContextRsIds("rs123;rs456;rs789");

        return new Object[][]{
                // mismatching ID will fail validation
                {vc, makeRsIDsSet("rs123456")},
                // null rsIDs to check will pass validation
                {vcMultipleRs, makeRsIDsSet("rs456")}
        };
    }
    @Test(dataProvider = "testValidateRSIDsFailureDataProvider", expectedExceptions = TribbleException.class)
    public void testValidateRSIDsFailure(final VariantContext vc, final Set<String> rsIDs) {
        // validateRSIDs throws exceptions if it fails, so no Asserts here...
        vc.validateRSIDs(rsIDs);
    }
    // create a VariantContext appropriate for testing rsIDs
    private VariantContext createTestVariantContextRsIds(final String rsId) {
        final EnumSet<VariantContext.Validation> toValidate = EnumSet.noneOf(VariantContext.Validation.class);
        final Set<String> filters = null;
        final Map<String, Object> attributes = null;
        final boolean fullyDecoded = false;

        return new VariantContext("genotypes", rsId, snpLoc, snpLocStart, snpLocStop, Arrays.asList(Aref, T),
                GenotypesContext.NO_GENOTYPES, VariantContext.NO_LOG10_PERROR, filters, attributes,
                fullyDecoded, toValidate);
    }
    private Set<String> makeRsIDsSet(final String... rsIds) {
        return new HashSet<String>(Arrays.asList(rsIds));
    }


    // validateAlternateAlleles: PASS conditions
    @DataProvider
    public Object[][] testValidateAlternateAllelesDataProvider() {
        final Genotype homVarT = GenotypeBuilder.create("homVarT", Arrays.asList(T, T));

        // no genotypes passes validateAlternateAlleles
        final VariantContext vcNoGenotypes =
                // A-ref/T with no GT
                createValidateAlternateAllelesContext(Arrays.asList(Aref, T));

        // genotypes that match ALTs will pass
        final VariantContext vcHasGenotypes =
                // A-ref/T vs T/T
                createValidateAlternateAllelesContext(Arrays.asList(Aref, T), homVarT);

        return new Object[][]{
                {vcNoGenotypes},
                {vcHasGenotypes}
        };
    }
    @Test(dataProvider = "testValidateAlternateAllelesDataProvider")
    public void testValidateAlternateAlleles(final VariantContext vc) {
        // validateAlternateAlleles throws exceptions if it fails, so no Asserts here...
        vc.validateAlternateAlleles();
    }
    // validateAlternateAlleles: FAIL conditions
    @DataProvider
    public Object[][] testValidateAlternateAllelesFailureDataProvider() {
        final Genotype homRef = GenotypeBuilder.create("homRef", Arrays.asList(Aref, Aref));
        final Genotype homVarA = GenotypeBuilder.create("homVarA", Arrays.asList(A, A));

        // alts not observed in the genotypes will fail validation
        // this is the throw in VariantContext from: if ( reportedAlleles.size() != observedAlleles.size() )
        final VariantContext vcHasAltNotObservedInGT =
                // A-ref/T vs A-ref/A-ref
                createValidateAlternateAllelesContext(Arrays.asList(Aref, T), homRef);

        // alts not observed in the genotypes will fail validation
        // but this time it is the second throw in VariantContext after: observedAlleles.retainAll(reportedAlleles);
        final VariantContext vcHasAltNotObservedInGTIntersection =
                // A-ref/T vs A/A
                createValidateAlternateAllelesContext(Arrays.asList(Aref, T), homVarA);

        return new Object[][]{
                {vcHasAltNotObservedInGT},
                {vcHasAltNotObservedInGTIntersection}
        };
    }
    @Test(dataProvider = "testValidateAlternateAllelesFailureDataProvider", expectedExceptions = TribbleException.class)
    public void testValidateAlternateAllelesFailure(final VariantContext vc) {
        // validateAlternateAlleles throws exceptions if it fails, so no Asserts here...
        vc.validateAlternateAlleles();
    }
    private VariantContext createValidateAlternateAllelesContext(final List<Allele> alleles, final Genotype... genotypes) {
        return createTestVariantContext(alleles, null, genotypes);
    }



    // validateChromosomeCounts: PASS conditions
    @DataProvider
    public Object[][] testValidateChromosomeCountsDataProvider() {
        final Genotype homRef = GenotypeBuilder.create("homRef", Arrays.asList(Aref, Aref));
        final Genotype homVarT = GenotypeBuilder.create("homVarT", Arrays.asList(T, T));
        final Genotype hetVarTC = GenotypeBuilder.create("hetVarTC", Arrays.asList(T, C));
        final Genotype homRefNoCall = GenotypeBuilder.create("homRefNoCall", Arrays.asList(Aref, Allele.NO_CALL));


        // no genotypes passes validateChromosomeCounts
        final VariantContext vcNoGenotypes =
                // A-ref/T with no GT
                createValidateChromosomeCountsContext(Arrays.asList(Aref, T), null);

        /** AN : total number of alleles in called genotypes **/
        // with AN set and hom-ref, we expect AN to be 2 for Aref/Aref
        final Map<String, Object> attributesAN = new HashMap<String, Object>();
        attributesAN.put(VCFConstants.ALLELE_NUMBER_KEY, "2");
        final VariantContext vcANSet =
                createValidateChromosomeCountsContext(Arrays.asList(Aref), attributesAN, homRef);

        // with AN set, one no-call (no-calls get ignored by getCalledChrCount() in VariantContext)
        // we expect AN to be 1 for Aref/no-call
        final Map<String, Object> attributesANNoCall = new HashMap<String, Object>();
        attributesANNoCall.put(VCFConstants.ALLELE_NUMBER_KEY, "1");
        final VariantContext vcANSetNoCall =
                createValidateChromosomeCountsContext(Arrays.asList(Aref), attributesANNoCall, homRefNoCall);


        /** AC : allele count in genotypes, for each ALT allele, in the same order as listed **/
        // with AC set, and T/T, we expect AC to be 2 (for 2 counts of ALT T)
        final Map<String, Object> attributesAC = new HashMap<String, Object>();
        attributesAC.put(VCFConstants.ALLELE_COUNT_KEY, "2");
        final VariantContext vcACSet =
                createValidateChromosomeCountsContext(Arrays.asList(Aref, T), attributesAC, homVarT);

        // with AC set and no ALT (GT is 0/0), we expect AC count to be 0
        final Map<String, Object> attributesACNoAlts = new HashMap<String, Object>();
        attributesACNoAlts.put(VCFConstants.ALLELE_COUNT_KEY, "0");
        final VariantContext vcACSetNoAlts =
                createValidateChromosomeCountsContext(Arrays.asList(Aref), attributesACNoAlts, homRef);

        // with AC set, and two different ALTs (T and C), with GT of 1/2, we expect a count of 1 for each.
        // With two ALTs, a list is expected, so we set the attribute as a list of 1,1
        final Map<String, Object> attributesACTwoAlts = new HashMap<String, Object>();
        attributesACTwoAlts.put(VCFConstants.ALLELE_COUNT_KEY, Arrays.asList("1", "1"));
        final VariantContext vcACSetTwoAlts =
                createValidateChromosomeCountsContext(Arrays.asList(Aref, T, C), attributesACTwoAlts, hetVarTC);

        return new Object[][]{
                {vcNoGenotypes},
                {vcANSet},
                {vcANSetNoCall},
                {vcACSet},
                {vcACSetNoAlts},
                {vcACSetTwoAlts}
        };
    }
    @Test(dataProvider = "testValidateChromosomeCountsDataProvider")
    public void testValidateChromosomeCounts(final VariantContext vc) {
        // validateChromosomeCounts throws exceptions if it fails, so no Asserts here...
        vc.validateChromosomeCounts();
    }
    // validateChromosomeCounts: FAIL conditions
    @DataProvider
    public Object[][] testValidateChromosomeCountsFailureDataProvider() {
        final Genotype homRef = GenotypeBuilder.create("homRef", Arrays.asList(Aref, Aref));
        final Genotype hetVarTC = GenotypeBuilder.create("hetVarTC", Arrays.asList(T, C));
        final Genotype homRefNoCall = GenotypeBuilder.create("homRefNoCall", Arrays.asList(Aref, Allele.NO_CALL));

        /** AN : total number of alleles in called genotypes **/
        // with AN set and hom-ref, we expect AN to be 2 for Aref/Aref, so 3 will fail
        final Map<String, Object> attributesAN = new HashMap<String, Object>();
        attributesAN.put(VCFConstants.ALLELE_NUMBER_KEY, "3");
        final VariantContext vcANSet =
                createValidateChromosomeCountsContext(Arrays.asList(Aref), attributesAN, homRef);

        // with AN set, one no-call (no-calls get ignored by getCalledChrCount() in VariantContext)
        // we expect AN to be 1 for Aref/no-call, so 2 will fail
        final Map<String, Object> attributesANNoCall = new HashMap<String, Object>();
        attributesANNoCall.put(VCFConstants.ALLELE_NUMBER_KEY, "2");
        final VariantContext vcANSetNoCall =
                createValidateChromosomeCountsContext(Arrays.asList(Aref), attributesANNoCall, homRefNoCall);

        /** AC : allele count in genotypes, for each ALT allele, in the same order as listed **/
        // with AC set but no ALTs, we expect a count of 0, so the wrong count will fail here
        final Map<String, Object> attributesACWrongCount = new HashMap<String, Object>();
        attributesACWrongCount.put(VCFConstants.ALLELE_COUNT_KEY, "2");
        final VariantContext vcACWrongCount =
                createValidateChromosomeCountsContext(Arrays.asList(Aref), attributesACWrongCount, homRef);

        // with AC set, two ALTs, but AC is not a list with count for each ALT
        final Map<String, Object> attributesACTwoAlts = new HashMap<String, Object>();
        attributesACTwoAlts.put(VCFConstants.ALLELE_COUNT_KEY, "1");
        final VariantContext vcACSetTwoAlts =
                createValidateChromosomeCountsContext(Arrays.asList(Aref, T, C), attributesACTwoAlts, hetVarTC);

        // with AC set, two ALTs, and a list is correctly used, but wrong counts (we expect counts to be 1,1)
        final Map<String, Object> attributesACTwoAltsWrongCount = new HashMap<String, Object>();
        attributesACTwoAltsWrongCount.put(VCFConstants.ALLELE_COUNT_KEY, Arrays.asList("1", "2"));
        final VariantContext vcACSetTwoAltsWrongCount =
                createValidateChromosomeCountsContext(Arrays.asList(Aref, T, C), attributesACTwoAltsWrongCount, hetVarTC);

        // with AC set, two ALTs, but only count for one ALT (we expect two items in the list: 1,1)
        final Map<String, Object> attributesACTwoAltsOneAltCount = new HashMap<String, Object>();
        attributesACTwoAltsOneAltCount.put(VCFConstants.ALLELE_COUNT_KEY, Arrays.asList("1"));
        final VariantContext vcACSetTwoAltsOneAltCount =
                createValidateChromosomeCountsContext(Arrays.asList(Aref, T, C), attributesACTwoAltsOneAltCount, hetVarTC);

        return new Object[][]{
                {vcANSet},
                {vcANSetNoCall},
                {vcACWrongCount},
                {vcACSetTwoAlts},
                {vcACSetTwoAltsWrongCount},
                {vcACSetTwoAltsOneAltCount}
        };
    }
    @Test(dataProvider = "testValidateChromosomeCountsFailureDataProvider", expectedExceptions = TribbleException.class)
    public void testValidateChromosomeCountsFailure(final VariantContext vc) {
        // validateChromosomeCounts throws exceptions if it fails, so no Asserts here...
        vc.validateChromosomeCounts();
    }
    private VariantContext createValidateChromosomeCountsContext(final List<Allele> alleles, final Map<String, Object> attributes, final Genotype... genotypes) {
        return createTestVariantContext(alleles, attributes, genotypes);
    }


    // the extraStrictValidation method calls the other validation methods
    @DataProvider
    public Object[][] testExtraStrictValidationDataProvider() {
        // get the data providers for each of the passing tests of the individual methods
        final Object[][] passingValidateReferenceBasesData = testValidateReferencesBasesDataProvider();
        final Object[][] passingValidateRSIDsData = testValidateRSIDsDataProvider();
        final Object[][] passingValidateAlternateAllelesData = testValidateAlternateAllelesDataProvider();
        final Object[][] passingValidateChromosomeCountsData = testValidateChromosomeCountsDataProvider();

        // the total number of tests we will run here is the sum of each of the test cases
        final int numDataPoints =
                passingValidateReferenceBasesData.length +
                        passingValidateRSIDsData.length +
                        passingValidateAlternateAllelesData.length +
                        passingValidateChromosomeCountsData.length;

        // create the data provider structure for this extra strict test
        final Object[][] extraStrictData = new Object[numDataPoints][];

        int testNum = 0;
        for (final Object[] testRefBases : passingValidateReferenceBasesData) {
            final VariantContext vc = (VariantContext) testRefBases[0];
            final Allele refAllele = (Allele) testRefBases[1];
            final Allele allele = (Allele) testRefBases[2];

            // for this test, rsIds does not matter, so we hold it constant
            extraStrictData[testNum++] = new Object[]{vc, refAllele, allele, null};
        }

        for (final Object[] testRsIDs : passingValidateRSIDsData) {
            final VariantContext vc = (VariantContext) testRsIDs[0];
            final Set<String> rsIDs = (Set<String>) testRsIDs[1];

            // for this test, reportedReference and observedReference does not matter,
            // so we hold it constant
            extraStrictData[testNum++] = new Object[]{vc, Tref, T, rsIDs};
        }

        for (final Object[] testAlternateAlleles : passingValidateAlternateAllelesData) {
            final VariantContext vc = (VariantContext) testAlternateAlleles[0];

            // for this test, only VariantContext is used, so we hold
            // reportedReference, observedReference and rsIds constant
            extraStrictData[testNum++] = new Object[]{vc, Tref, T, null};
        }

        for (final Object[] testChromomeCounts : passingValidateChromosomeCountsData) {
            final VariantContext vc = (VariantContext) testChromomeCounts[0];

            // for this test, only VariantContext is used, so we hold
            // reportedReference, observedReference and rsIds constant
            extraStrictData[testNum++] = new Object[]{vc, Tref, T, null};
        }

        return extraStrictData;
    }

    @DataProvider(name = "serializationTestData")
    public Object[][] getSerializationTestData() {
        return new Object[][] {
                { new File("src/test/resources/htsjdk/variant/HiSeq.10000.vcf"), new VCFCodec() },
                { new File("src/test/resources/htsjdk/variant/serialization_test.bcf"), new BCF2Codec() }
        };
    }

    @Test(dataProvider = "serializationTestData")
    public void testSerialization( final File testFile, final FeatureCodec<VariantContext, ?> codec ) throws Exception {
        final AbstractFeatureReader<VariantContext, ?> featureReader = AbstractFeatureReader.getFeatureReader(testFile.getAbsolutePath(), codec, false);
        final VariantContext initialVC = featureReader.iterator().next();

        final VariantContext vcDeserialized = TestUtil.serializeAndDeserialize(initialVC);

        assertVariantContextsAreEqual(vcDeserialized, initialVC);
    }

    @Test(dataProvider = "testExtraStrictValidationDataProvider")
    public void testExtraStrictValidation(final VariantContext vc, final Allele reportedReference, final Allele observedReference, final Set<String> rsIDs) {
        // extraStrictValidation throws exceptions if it fails, so no Asserts here...
        vc.extraStrictValidation(reportedReference, observedReference, rsIDs);
    }
    @DataProvider
    public Object[][] testExtraStrictValidationFailureDataProvider() {
        // get the data providers for each of the failure tests of the individual methods
        final Object[][] failingValidateReferenceBasesData = testValidateReferencesBasesFailureDataProvider();
        final Object[][] failingValidateRSIDsData = testValidateRSIDsFailureDataProvider();
        final Object[][] failingValidateAlternateAllelesData = testValidateAlternateAllelesFailureDataProvider();
        final Object[][] failingValidateChromosomeCountsData = testValidateChromosomeCountsFailureDataProvider();

        // the total number of tests we will run here is the sum of each of the test cases
        final int numDataPoints =
                failingValidateReferenceBasesData.length +
                        failingValidateRSIDsData.length +
                        failingValidateAlternateAllelesData.length +
                        failingValidateChromosomeCountsData.length;

        // create the data provider structure for this extra strict test
        final Object[][] extraStrictData = new Object[numDataPoints][];

        int testNum = 0;
        for (final Object[] testRefBases : failingValidateReferenceBasesData) {
            final VariantContext vc = (VariantContext) testRefBases[0];
            final Allele refAllele = (Allele) testRefBases[1];
            final Allele allele = (Allele) testRefBases[2];

            // for this test, rsIds does not matter, so we hold it constant
            extraStrictData[testNum++] = new Object[]{vc, refAllele, allele, null};
        }

        for (final Object[] testRsIDs : failingValidateRSIDsData) {
            final VariantContext vc = (VariantContext) testRsIDs[0];
            final Set<String> rsIDs = (Set<String>) testRsIDs[1];

            // for this test, reportedReference and observedReference does not matter,
            // so we hold it constant
            extraStrictData[testNum++] = new Object[]{vc, Tref, T, rsIDs};
        }

        for (final Object[] testAlternateAlleles : failingValidateAlternateAllelesData) {
            final VariantContext vc = (VariantContext) testAlternateAlleles[0];

            // for this test, only VariantContext is used, so we hold
            // reportedReference, observedReference and rsIds constant
            extraStrictData[testNum++] = new Object[]{vc, Tref, T, null};
        }

        for (final Object[] testChromomeCounts : failingValidateChromosomeCountsData) {
            final VariantContext vc = (VariantContext) testChromomeCounts[0];

            // for this test, only VariantContext is used, so we hold
            // reportedReference, observedReference and rsIds constant
            extraStrictData[testNum++] = new Object[]{vc, Tref, T, null};
        }

        return extraStrictData;
    }
    @Test(dataProvider = "testExtraStrictValidationFailureDataProvider", expectedExceptions = TribbleException.class)
    public void testExtraStrictValidationFailure(final VariantContext vc, final Allele reportedReference, final Allele observedReference, final Set<String> rsIDs) {
        // extraStrictValidation throws exceptions if it fails, so no Asserts here...
        vc.extraStrictValidation(reportedReference, observedReference, rsIDs);
    }
}
