package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.CRAMEncodingStrategy;
import htsjdk.samtools.reference.FakeReferenceSequenceFile;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Test a set of index queries against a series of CRAM files that are generated using reads content from a known
 * BAM, but with various partitioning permutations (CRAMEncodingStrategy parameters, such as readsPerSlice and
 * slicesPerContainer). Using different permutations of these parameters changes the location of the container
 * and slice partition boundaries, which forces query results to be retrieved from various single and
 * multiple-reference containers, with various numbers of slices of varying size.
 */
public class CRAMIndexPermutationsTests extends HtsjdkTest {

    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/cram");

    // Subset of NA12878 (20:10000000-10004000, 21:10000000-10004000, +2000 unmapped). There is no corresponding
    // reference checked in for this file since its too big, but because we're only using it to validate
    // index query results, we only care that the right the reads are returned, not what the read bases are, so
    // we use a synthetic in-memory fake reference of all 'N's to convert it to CRAM.
    //private static final File originalCRAM = new File(TEST_DATA_DIR, "NA12878.20.21.unmapped.orig.cram");
    // BAM file for comparison with CRAM results
    //TODO: this is a 2MB file
    private static final File truthBAM = new File(TEST_DATA_DIR, "NA12878.20.21.unmapped.orig.bam");

    //Note that these tests can be REALLY slow because although we don't use the real (huge) reference, we use a fake
    // in-memory reference, but the reference sequences in the dictionary in the test file are large (60 mega-bases),
    // and upper-casing them each time they're retrieved can be slowwwwww.
    private static final ReferenceSource fakeReferenceSource =
            new ReferenceSource(new FakeReferenceSequenceFile(
                    SamReaderFactory.makeDefault().getFileHeader(truthBAM).getSequenceDictionary().getSequences()
            ));

    // Partitioning permutations
    final CRAMEncodingStrategy defaultStrategy10000x1 = new CRAMEncodingStrategy();
    final CRAMEncodingStrategy strategy5000x1 = new CRAMEncodingStrategy().setReadsPerSlice(5000);
    final CRAMEncodingStrategy strategy2000x1 = new CRAMEncodingStrategy().setReadsPerSlice(2000);
    // readsPerSlice has to be > minimumSingleReferenceSliceSize, so once the readsPerSlice gets below the default
    // minimumSingleReferenceSliceSize, we need to lower minimumSingleReferenceSliceSize as well
    final CRAMEncodingStrategy strategy1000x1 = new CRAMEncodingStrategy().setMinimumSingleReferenceSliceSize(100).setReadsPerSlice(1000);
    final CRAMEncodingStrategy strategy500x1 = new CRAMEncodingStrategy().setMinimumSingleReferenceSliceSize(100).setReadsPerSlice(500);
    final CRAMEncodingStrategy strategy200x1 = new CRAMEncodingStrategy().setMinimumSingleReferenceSliceSize(100).setReadsPerSlice(200);
    final CRAMEncodingStrategy strategy100x1 = new CRAMEncodingStrategy().setMinimumSingleReferenceSliceSize(100).setReadsPerSlice(100);
    // now try with multiple slices/container
    final CRAMEncodingStrategy strategy5000x2 =
            new CRAMEncodingStrategy().setMinimumSingleReferenceSliceSize(100).setReadsPerSlice(5000).setSlicesPerContainer(2);
    final CRAMEncodingStrategy strategy1000x2 =
            new CRAMEncodingStrategy().setMinimumSingleReferenceSliceSize(100).setReadsPerSlice(1000).setSlicesPerContainer(2);
    final CRAMEncodingStrategy strategy500x2 =
            new CRAMEncodingStrategy().setMinimumSingleReferenceSliceSize(100).setReadsPerSlice(500).setSlicesPerContainer(2);
    final CRAMEncodingStrategy strategy200x2 =
            new CRAMEncodingStrategy().setMinimumSingleReferenceSliceSize(100).setReadsPerSlice(200).setSlicesPerContainer(2);
    final CRAMEncodingStrategy strategy100x2 =
            new CRAMEncodingStrategy().setMinimumSingleReferenceSliceSize(100).setReadsPerSlice(100).setSlicesPerContainer(2);

    @DataProvider(name = "cramIndexQueries")
    public Object[][] getCRAMIndexQueries() {
        final QueryInterval query1 = new QueryInterval(0, 10000182, 10000182);
        final QueryInterval query2 = new QueryInterval(1, 10000150, 10000250);
        final QueryInterval queryAllChr20 = new QueryInterval(0, 1, -1);
        final QueryInterval queryAllChr21 = new QueryInterval(1, 1, -1);

        return new Object[][]{
                {defaultStrategy10000x1, new QueryInterval[]{query1}},
                {strategy5000x1, new QueryInterval[]{query1}},
                {strategy2000x1, new QueryInterval[]{query1}},
                {strategy1000x1, new QueryInterval[]{query1}},
                {strategy500x1, new QueryInterval[]{query1}},
                {strategy200x1, new QueryInterval[]{query1}},
                {strategy100x1, new QueryInterval[]{query1}},
                {strategy5000x2, new QueryInterval[]{query1}},
                {strategy1000x2, new QueryInterval[]{query1}},
                {strategy500x2, new QueryInterval[]{query1}},
                {strategy200x2, new QueryInterval[]{query1}},
                {strategy100x2, new QueryInterval[]{query1}},

                // multiple queries
                {defaultStrategy10000x1, new QueryInterval[]{query1, query2}},
                {strategy5000x1, new QueryInterval[]{query1, query2}},
                {strategy2000x1, new QueryInterval[]{query1, query2}},
                {strategy1000x1, new QueryInterval[]{query1, query2}},
                {strategy500x1, new QueryInterval[]{query1, query2}},
                {strategy200x1, new QueryInterval[]{query1, query2}},
                {strategy100x1, new QueryInterval[]{query1, query2}},
                {strategy5000x2, new QueryInterval[]{query1, query2}},
                {strategy1000x2, new QueryInterval[]{query1, query2}},
                {strategy500x2, new QueryInterval[]{query1, query2}},
                {strategy200x2, new QueryInterval[]{query1, query2}},
                {strategy100x2, new QueryInterval[]{query1, query2}},

                // all of chr20
                {defaultStrategy10000x1, new QueryInterval[]{queryAllChr20}},
                {strategy5000x1, new QueryInterval[]{queryAllChr20}},
                {strategy2000x1, new QueryInterval[]{queryAllChr20}},
                {strategy1000x1, new QueryInterval[]{queryAllChr20}},
                {strategy500x1, new QueryInterval[]{queryAllChr20}},
                {strategy200x1, new QueryInterval[]{queryAllChr20}},
                {strategy100x1, new QueryInterval[]{queryAllChr20}},
                {strategy5000x2, new QueryInterval[]{queryAllChr20}},
                {strategy1000x2, new QueryInterval[]{queryAllChr20}},
                {strategy500x2, new QueryInterval[]{queryAllChr20}},
                {strategy200x2, new QueryInterval[]{queryAllChr20}},
                {strategy100x2, new QueryInterval[]{queryAllChr20}},

                // all of chr21
                {defaultStrategy10000x1, new QueryInterval[]{queryAllChr20}},
                {strategy5000x1, new QueryInterval[]{queryAllChr20}},
                {strategy2000x1, new QueryInterval[]{queryAllChr20}},
                {strategy1000x1, new QueryInterval[]{queryAllChr20}},
                {strategy500x1, new QueryInterval[]{queryAllChr20}},
                {strategy200x1, new QueryInterval[]{queryAllChr20}},
                {strategy100x1, new QueryInterval[]{queryAllChr20}},
                {strategy5000x2, new QueryInterval[]{queryAllChr20}},
                {strategy1000x2, new QueryInterval[]{queryAllChr20}},
                {strategy500x2, new QueryInterval[]{queryAllChr20}},
                {strategy200x2, new QueryInterval[]{queryAllChr20}},
                {strategy100x2, new QueryInterval[]{queryAllChr20}},

                // all chr20 and chr21
                {defaultStrategy10000x1, new QueryInterval[]{queryAllChr20, queryAllChr21}},
                {strategy5000x1, new QueryInterval[]{queryAllChr20, queryAllChr21}},
                {strategy2000x1, new QueryInterval[]{queryAllChr20, queryAllChr21}},
                {strategy1000x1, new QueryInterval[]{queryAllChr20, queryAllChr21}},
                {strategy500x1, new QueryInterval[]{queryAllChr20, queryAllChr21}},
                {strategy200x1, new QueryInterval[]{queryAllChr20, queryAllChr21}},
                {strategy100x1, new QueryInterval[]{queryAllChr20, queryAllChr21}},
                {strategy5000x2, new QueryInterval[]{queryAllChr20, queryAllChr21}},
                {strategy1000x2, new QueryInterval[]{queryAllChr20, queryAllChr21}},
                {strategy500x2, new QueryInterval[]{queryAllChr20, queryAllChr21}},
                {strategy200x2, new QueryInterval[]{queryAllChr20, queryAllChr21}},
                {strategy100x2, new QueryInterval[]{queryAllChr20, queryAllChr21}}
        };
    }

    @Test(dataProvider = "cramIndexQueries")
    public void testQueriesWithBAI(
            final CRAMEncodingStrategy cramEncodingStrategy,
            final QueryInterval[] queryIntervals) throws IOException {

        final File tempCRAM = CRAMIndexTestHelper.createCRAMWithBAIForEncodingStrategy(
                truthBAM,
                fakeReferenceSource,
                cramEncodingStrategy);
        final List<String> cramResults = CRAMIndexTestHelper.getCRAMResultsForQueryIntervals(
                tempCRAM,
                SamFiles.findIndex(tempCRAM),
                fakeReferenceSource,
                queryIntervals);
        final List<String> truthResults = CRAMIndexTestHelper.getBAMResultsForQueryIntervals(
                truthBAM,
                queryIntervals);

        Assert.assertEquals(cramResults, truthResults);
    }

    @Test(dataProvider = "cramIndexQueries")
    public void testQueriesWithCRAI(
            final CRAMEncodingStrategy cramEncodingStrategy,
            final QueryInterval[] queryIntervals) throws IOException {

        final File tempCRAM = CRAMIndexTestHelper.createCRAMWithCRAIForEncodingStrategy(
                truthBAM,
                fakeReferenceSource,
                cramEncodingStrategy);

        final List<String> cramResults = CRAMIndexTestHelper.getCRAMResultsForQueryIntervals(
                tempCRAM,
                SamFiles.findIndex(tempCRAM),
                fakeReferenceSource,
                queryIntervals);
        final List<String> truthResults = CRAMIndexTestHelper.getBAMResultsForQueryIntervals(
                truthBAM,
                queryIntervals);

        Assert.assertEquals(cramResults, truthResults);
    }

    @DataProvider(name = "cramUnmappedTestCases")
    public Object[][] getCRAMUnmappedTestCases() {
        return new Object[][]{
                {defaultStrategy10000x1},
                {strategy5000x1},
                {strategy2000x1,},
                {strategy1000x1},
                {strategy500x1},
                {strategy200x1},
                {strategy100x1},
                {strategy5000x2},
                {strategy1000x2},
                {strategy500x2},
                {strategy200x2},
                {strategy100x2}
        };
    }

    @Test(dataProvider = "cramUnmappedTestCases")
    public void testUnmappedQuery(final CRAMEncodingStrategy cramEncodingStrategy) throws IOException {
        final File tempCRAM = CRAMIndexTestHelper.createCRAMWithCRAIForEncodingStrategy(
                truthBAM,
                fakeReferenceSource,
                cramEncodingStrategy);
        final List<String> cramResults = CRAMIndexTestHelper.getCRAMResultsForUnmapped(
                tempCRAM,
                SamFiles.findIndex(tempCRAM),
                fakeReferenceSource);
        final List<String> truthResults = CRAMIndexTestHelper.getBAMResultsForUnmapped(truthBAM);
        Assert.assertEquals(cramResults, truthResults);
    }

//    //////////////////////////////
//    TODO: remove me
//    @Test
//    public void testLargeRAMIndexPerf() throws IOException {
//        final File largeCRAM = new File("/Users/cnorman/projects/references/NA12878.cram");
//        final File referenceFile = new File("/Users/cnorman/projects/references/hg38/Homo_sapiens_assembly38.fasta");
//
//        final List<String> cramResults = CRAMIndexTestHelper.getCRAMResultsForQueryIntervals(
//                largeCRAM,
//                SamFiles.findIndex(largeCRAM),
//                new ReferenceSource(referenceFile),
//                // 1
//                new QueryInterval[]{
//                        // 1, Read container at 11101534316
//                        new QueryInterval(10, 60014, 60100),
//                        //2, Read container at 12707266531
//                        new QueryInterval(12, 16067510, 16067700)
//                });
//        System.out.println(cramResults.size());
//    }
//    //////////////////////////////

}
