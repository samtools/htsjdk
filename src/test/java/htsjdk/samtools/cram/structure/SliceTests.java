package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.CRAMFileReader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.AlignmentContext;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.build.CompressionHeaderFactory;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.util.SequenceUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by vadim on 07/12/2015.
 */
public class SliceTests extends HtsjdkTest {
    private static final int TEST_RECORD_COUNT = 10;
    private static final int READ_LENGTH_FOR_TEST_RECORDS = CRAMStructureTestUtil.READ_LENGTH_FOR_TEST_RECORDS;

    @Test
    public void testUnmappedValidateRef() {
        final Slice slice = new Slice(AlignmentContext.UNMAPPED_UNPLACED_CONTEXT);

        Assert.assertTrue(slice.validateRefMD5(null));
        Assert.assertTrue(slice.validateRefMD5(new byte[0]));
        Assert.assertTrue(slice.validateRefMD5(new byte[1024]));
    }

    @Test
    public void test_validateRef() {
        byte[] ref = "AAAAA".getBytes();
        final byte[] md5 = SequenceUtil.calculateMD5(ref, 0, Math.min(5, ref.length));
        final Slice slice = new Slice(new AlignmentContext(new ReferenceContext(0), 1, 5));
        slice.setRefMD5(ref);

        Assert.assertEquals(slice.refMD5, md5);
        Assert.assertTrue(slice.validateRefMD5(ref));
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testFailsMD5Check() throws IOException {
        // auxf.alteredForMD5test.fa has been altered slightly from the original reference
        // to cause the CRAM md5 check to fail
        final File CRAMFile = new File("src/test/resources/htsjdk/samtools/cram/auxf#values.3.0.cram");
        final File refFile = new File("src/test/resources/htsjdk/samtools/cram/auxf.alteredForMD5test.fa");
        ReferenceSource refSource = new ReferenceSource(refFile);
        CRAMFileReader reader = null;
        try {
            reader = new CRAMFileReader(
                    CRAMFile,
                    null,
                    refSource,
                    ValidationStringency.STRICT);
            Iterator<SAMRecord> it = reader.getIterator();
            while (it.hasNext()) {
                it.next();
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    @DataProvider(name = "sliceStateTestCases")
    private Object[][] sliceStateTestCases() {
        final int mappedSequenceId = 0;  // arbitrary
        final ReferenceContext mappedRefContext = new ReferenceContext(mappedSequenceId);
        final List<Object[]> retval = new ArrayList<>();
        final boolean[] coordinateSorteds = new boolean[] { true, false };
        for (final boolean coordSorted : coordinateSorteds) {
            retval.add(new Object[]
                {
                        CRAMStructureTestUtil.getSingleRefRecords(TEST_RECORD_COUNT, mappedSequenceId),
                        coordSorted,
                        new AlignmentContext(mappedRefContext, 1,
                                READ_LENGTH_FOR_TEST_RECORDS + TEST_RECORD_COUNT - 1)
                });
            retval.add(new Object[]
                {
                        CRAMStructureTestUtil.getMultiRefRecords(TEST_RECORD_COUNT),
                        coordSorted,
                        AlignmentContext.MULTIPLE_REFERENCE_CONTEXT
                });
            retval.add(new Object[]
                {
                        CRAMStructureTestUtil.getUnplacedRecords(TEST_RECORD_COUNT),
                        coordSorted,
                        AlignmentContext.UNMAPPED_UNPLACED_CONTEXT
                });


                // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
                // but not both.  We treat these weird edge cases as unplaced.

            retval.add(new Object[]
                {
                        CRAMStructureTestUtil.getHalfUnplacedNoRefRecords(TEST_RECORD_COUNT),
                        coordSorted,
                        AlignmentContext.UNMAPPED_UNPLACED_CONTEXT
                });

            retval.add(new Object[]
                {
                        CRAMStructureTestUtil.getHalfUnplacedNoStartRecords(TEST_RECORD_COUNT, mappedSequenceId),
                        coordSorted,
                        AlignmentContext.UNMAPPED_UNPLACED_CONTEXT,
                });
        }

        return retval.toArray(new Object[0][0]);
    }

    @Test(dataProvider = "sliceStateTestCases")
    public void sliceStateTest(final List<CramCompressionRecord> records,
                               final boolean coordinateSorted,
                               final AlignmentContext expectedAlignmentContext) {
        final CompressionHeader header = new CompressionHeaderFactory().build(records, null, coordinateSorted);
        final Slice slice = Slice.buildSlice(records, header);
        final int expectedBaseCount = TEST_RECORD_COUNT * READ_LENGTH_FOR_TEST_RECORDS;
        CRAMStructureTestUtil.assertSliceState(slice, expectedAlignmentContext, TEST_RECORD_COUNT, expectedBaseCount);
    }

    // show that a slice with a single ref will initially be built as single-ref
    // but adding an additional ref will make it multiref
    // and more will keep it multiref (mapped or otherwise)

    @Test
    public void testBuildStates() {
        final List<CramCompressionRecord> records = new ArrayList<>();

        int index = 0;
        final int alignmentStart = 100;  // arbitrary
        final CramCompressionRecord record1 = CRAMStructureTestUtil.createMappedRecord(index, index, alignmentStart);
        records.add(record1);
        buildSliceAndAssert(records, new AlignmentContext(new ReferenceContext(index), alignmentStart, READ_LENGTH_FOR_TEST_RECORDS));

        index++;
        final CramCompressionRecord record2 = CRAMStructureTestUtil.createMappedRecord(index, index, alignmentStart);
        records.add(record2);
        buildSliceAndAssert(records, AlignmentContext.MULTIPLE_REFERENCE_CONTEXT);

        index++;
        final CramCompressionRecord record3 = CRAMStructureTestUtil.createMappedRecord(index, index, alignmentStart);
        records.add(record3);
        buildSliceAndAssert(records, AlignmentContext.MULTIPLE_REFERENCE_CONTEXT);

        index++;
        final CramCompressionRecord unmapped = CRAMStructureTestUtil.createUnmappedUnplacedRecord(index);
        records.add(unmapped);
        buildSliceAndAssert(records, AlignmentContext.MULTIPLE_REFERENCE_CONTEXT);
    }

    @Test
    public void testSingleAndUnmappedBuild() {
        final List<CramCompressionRecord> records = new ArrayList<>();

        final int mappedSequenceId = 0;  // arbitrary
        final int alignmentStart = 10;  // arbitrary
        int index = 0;
        final CramCompressionRecord single = CRAMStructureTestUtil.createMappedRecord(index, mappedSequenceId, alignmentStart);
        single.readLength = 20;
        records.add(single);

        index++;
        final CramCompressionRecord unmapped = CRAMStructureTestUtil.createUnmappedUnplacedRecord(index);
        unmapped.readLength = 35;
        records.add(unmapped);

        final CompressionHeader header = new CompressionHeaderFactory().build(records, null, true);

        final Slice slice = Slice.buildSlice(records, header);
        final int expectedBaseCount = single.readLength + unmapped.readLength;
        CRAMStructureTestUtil.assertSliceState(slice, AlignmentContext.MULTIPLE_REFERENCE_CONTEXT, records.size(), expectedBaseCount);
    }

    @Test(dataProvider = "uninitializedBAIParameterTestCases", dataProviderClass = CRAMStructureTestUtil.class, expectedExceptions = CRAMException.class)
    public void uninitializedBAIParameterTest(final Slice s) {
        s.baiIndexInitializationCheck();
    }

    @Test(dataProvider = "uninitializedCRAIParameterTestCases", dataProviderClass = CRAMStructureTestUtil.class, expectedExceptions = CRAMException.class)
    public void uninitializedCRAIParameterTest(final Slice s) {
        s.craiIndexInitializationCheck();
    }

    private static void buildSliceAndAssert(final List<CramCompressionRecord> records,
                                            final AlignmentContext expectedAlignmentContext) {
        final CompressionHeader header = new CompressionHeaderFactory().build(records, null, true);
        final Slice slice = Slice.buildSlice(records, header);
        final int expectedBaseCount = records.size() * READ_LENGTH_FOR_TEST_RECORDS;
        CRAMStructureTestUtil.assertSliceState(slice, expectedAlignmentContext, records.size(), expectedBaseCount);
    }

}
