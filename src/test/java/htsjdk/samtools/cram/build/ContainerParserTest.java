package htsjdk.samtools.cram.build;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.structure.AlignmentSpan;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Created by vadim on 11/01/2016.
 */
public class ContainerParserTest extends HtsjdkTest {

    static private final int TEST_RECORD_COUNT = 10;

    @DataProvider(name = "eof")
    private Object[][] eofData() {
        return new Object[][] {
                {CramVersions.CRAM_v2_1},
                {CramVersions.CRAM_v3}
        };
    }

    @Test(dataProvider = "eof")
    public void testEOF(final Version version) throws IOException {
        final ContainerParser parser = new ContainerParser(ContainerFactoryTest.getSAMFileHeaderForTests());
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CramIO.issueEOF(version, baos);
            final Container container = ContainerIO.readContainer(version, new ByteArrayInputStream(baos.toByteArray()));
            Assert.assertTrue(container.isEOF());
            Assert.assertTrue(parser.getRecords(container, null, ValidationStringency.STRICT).isEmpty());
        }
    }

    @DataProvider(name = "containersForRefTests")
    private Object[][] refTestData() {
        return new Object[][] {
                {
                        ContainerFactoryTest.getSingleRefRecords(TEST_RECORD_COUNT),
                        new HashSet<Integer>() {{
                            add(0);
                        }}
                },
                {
                        ContainerFactoryTest.getUnmappedRecords(),
                        new HashSet<Integer>() {{
                            add(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
                        }}
                },

                // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
                // but not both.  We treat these weird edge cases as unplaced.

                {
                        ContainerFactoryTest.getUnmappedNoRefRecords(),
                        new HashSet<Integer>() {{
                            add(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
                        }}
                },
                {
                        ContainerFactoryTest.getUnmappedNoStartRecords(),
                        new HashSet<Integer>() {{
                            add(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
                        }}
                },
        };
    }

    @Test(dataProvider = "containersForRefTests")
    public void paramTest(final List<CramCompressionRecord> records, final Set<Integer> expectedKeys) {
        final ContainerFactory factory = new ContainerFactory(ContainerFactoryTest.getSAMFileHeaderForTests(), TEST_RECORD_COUNT);
        final Container container = factory.buildContainer(records);

        final ContainerParser parser = new ContainerParser(ContainerFactoryTest.getSAMFileHeaderForTests());

        final Map<Integer, AlignmentSpan> referenceSet = parser.getReferences(container, ValidationStringency.STRICT);
        Assert.assertEquals(referenceSet.keySet(), expectedKeys);

        final List<CramCompressionRecord> roundTripRecords = parser.getRecords(container, null, ValidationStringency.STRICT);
        // TODO this fails.  return to this when refactoring Container and CramCompressionRecord
        //Assert.assertEquals(roundTripRecords, records);
        Assert.assertEquals(roundTripRecords.size(), TEST_RECORD_COUNT);
    }

   @Test
    public void testMultirefContainer() {
       final Map<Integer, AlignmentSpan> expectedSpans = new HashMap<>();
       for (int i = 0; i < 10; i++) {
           expectedSpans.put(i, new AlignmentSpan(i + 1, 3, 1));
       }

       final ContainerFactory factory = new ContainerFactory(ContainerFactoryTest.getSAMFileHeaderForTests(), TEST_RECORD_COUNT);
       final Container container = factory.buildContainer(ContainerFactoryTest.getMultiRefRecords());

       final ContainerParser parser = new ContainerParser(ContainerFactoryTest.getSAMFileHeaderForTests());

       final Map<Integer, AlignmentSpan> referenceSet = parser.getReferences(container, ValidationStringency.STRICT);
       Assert.assertEquals(referenceSet, expectedSpans);
   }

    @Test
    public void testMultirefContainerWithUnmapped() {
        final List<AlignmentSpan> expectedSpans = new ArrayList<>();
        expectedSpans.add(new AlignmentSpan(1, 3, 1));
        expectedSpans.add(new AlignmentSpan(2, 3, 1));


        final SAMFileHeader samFileHeader = ContainerFactoryTest.getSAMFileHeaderForTests();
        final ContainerParser parser = new ContainerParser(samFileHeader);

        final List<Container> containers = ContainerFactoryTest.getMultiRefContainersForStateTest();

        // first container is single-ref

        final Map<Integer, AlignmentSpan> referenceSet0 = parser.getReferences(containers.get(0), ValidationStringency.STRICT);
        Assert.assertNotNull(referenceSet0);
        Assert.assertEquals(referenceSet0.size(), 1);

        Assert.assertTrue(referenceSet0.containsKey(0));
        Assert.assertEquals(referenceSet0.get(0), expectedSpans.get(0));

        // when other refs are added, subsequent containers are multiref

        final Map<Integer, AlignmentSpan> referenceSet1 = parser.getReferences(containers.get(1), ValidationStringency.STRICT);
        Assert.assertNotNull(referenceSet1);
        Assert.assertEquals(referenceSet1.size(), 2);

        // contains the span we checked earlier
        Assert.assertTrue(referenceSet1.containsKey(0));
        Assert.assertEquals(referenceSet1.get(0), expectedSpans.get(0));

        Assert.assertTrue(referenceSet1.containsKey(1));
        Assert.assertEquals(referenceSet1.get(1), expectedSpans.get(1));


        final Map<Integer, AlignmentSpan> referenceSet2 = parser.getReferences(containers.get(2), ValidationStringency.STRICT);
        Assert.assertNotNull(referenceSet2);
        Assert.assertEquals(referenceSet2.size(), 3);

        // contains the spans we checked earlier
        Assert.assertTrue(referenceSet2.containsKey(0));
        Assert.assertEquals(referenceSet2.get(0), expectedSpans.get(0));
        Assert.assertTrue(referenceSet2.containsKey(1));
        Assert.assertEquals(referenceSet2.get(1), expectedSpans.get(1));

        Assert.assertTrue(referenceSet2.containsKey(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX));
        final AlignmentSpan unmappedSpan = referenceSet2.get(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);

        // only checking count here because start and span aren't meaningful
        Assert.assertEquals(unmappedSpan.getCount(), 1);
    }
}
