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
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by vadim on 11/01/2016.
 */
public class ContainerParserTest extends HtsjdkTest {

    @Test
    public void testEOFv2() throws IOException {
        final ContainerParser parser = new ContainerParser(new SAMFileHeader());
        try (final ByteArrayOutputStream v2_baos = new ByteArrayOutputStream()) {
            final Version version = CramVersions.CRAM_v2_1;
            CramIO.issueEOF(version, v2_baos);
            final Container container = ContainerIO.readContainer(version, new ByteArrayInputStream(v2_baos.toByteArray()));
            Assert.assertTrue(container.isEOF());
            Assert.assertTrue(parser.getRecords(container, null, ValidationStringency.STRICT).isEmpty());
        }
    }

    @Test
    public void testEOFv3() throws IOException {
        final ContainerParser parser = new ContainerParser(new SAMFileHeader());
        try (final ByteArrayOutputStream v3_baos = new ByteArrayOutputStream()) {
            final Version version = CramVersions.CRAM_v3;
            CramIO.issueEOF(version, v3_baos);
            final Container container = ContainerIO.readContainer(version, new ByteArrayInputStream(v3_baos.toByteArray()));
            Assert.assertTrue(container.isEOF());
            Assert.assertTrue(parser.getRecords(container, null, ValidationStringency.STRICT).isEmpty());
        }
    }

    @Test
    public void testSingleRefContainer() {
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        final ContainerParser parser = new ContainerParser(samFileHeader);

        final Container container = ContainerFactoryTest.getSingleRefContainer(samFileHeader);

        final Map<Integer, AlignmentSpan> referenceSet = parser.getReferences(container, ValidationStringency.STRICT);
        Assert.assertNotNull(referenceSet);
        Assert.assertEquals(referenceSet.size(), 1);
        Assert.assertTrue(referenceSet.containsKey(0));
    }

    @Test
    public void testUnmappedNoReferenceContainer() {
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        final ContainerParser parser = new ContainerParser(samFileHeader);

        final Container container = ContainerFactoryTest.getUnmappedNoRefContainer(samFileHeader);

        final Map<Integer, AlignmentSpan> referenceSet = parser.getReferences(container, ValidationStringency.STRICT);
        Assert.assertNotNull(referenceSet);
        Assert.assertEquals(referenceSet.size(), 1);
        Assert.assertTrue(referenceSet.containsKey(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX));
    }

    @Test
    public void testUnmappedNoAlignmentStartContainer() {
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        final ContainerParser parser = new ContainerParser(samFileHeader);

        final Container container = ContainerFactoryTest.getUnmappedNoStartContainer(samFileHeader);

        final Map<Integer, AlignmentSpan> referenceSet = parser.getReferences(container, ValidationStringency.STRICT);
        Assert.assertNotNull(referenceSet);
        Assert.assertEquals(referenceSet.size(), 1);
        Assert.assertTrue(referenceSet.containsKey(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX));
    }

    @Test
    public void testMultirefContainer() {
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        final ContainerParser parser = new ContainerParser(samFileHeader);

        final Container container = ContainerFactoryTest.getMultiRefContainer(samFileHeader);

        final Map<Integer, AlignmentSpan> referenceSet = parser.getReferences(container, ValidationStringency.STRICT);
        Assert.assertNotNull(referenceSet);
        Assert.assertEquals(referenceSet.size(), 10);
        for (int i = 0; i < 10; i++) {
            Assert.assertTrue(referenceSet.containsKey(i));
            AlignmentSpan span = referenceSet.get(i);
            Assert.assertEquals(span.getCount(), 1);
            Assert.assertEquals(span.getStart(), i + 1);
            Assert.assertEquals(span.getSpan(), 3);
        }
    }

    @Test
    public void testMultirefContainerWithUnmapped() {
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        final ContainerParser parser = new ContainerParser(samFileHeader);

        final List<Container> containers = ContainerFactoryTest.getMultiRefContainersForStateTest(samFileHeader);

        // first container is single-ref

        final Map<Integer, AlignmentSpan> referenceSet0 = parser.getReferences(containers.get(0), ValidationStringency.STRICT);
        Assert.assertNotNull(referenceSet0);
        Assert.assertEquals(referenceSet0.size(), 1);

        Assert.assertTrue(referenceSet0.containsKey(0));
        final AlignmentSpan span0 = referenceSet0.get(0);
        Assert.assertEquals(span0.getCount(), 1);
        Assert.assertEquals(span0.getStart(), 1);
        Assert.assertEquals(span0.getSpan(), 3);

        // when other refs are added, subsequent containers are multiref

        final Map<Integer, AlignmentSpan> referenceSet1 = parser.getReferences(containers.get(1), ValidationStringency.STRICT);
        Assert.assertNotNull(referenceSet1);
        Assert.assertEquals(referenceSet1.size(), 2);

        // contains the span we checked earlier
        Assert.assertTrue(referenceSet1.containsKey(0));
        Assert.assertEquals(referenceSet1.get(0), span0);

        Assert.assertTrue(referenceSet1.containsKey(1));
        final AlignmentSpan span1 = referenceSet1.get(1);
        Assert.assertEquals(span1.getCount(), 1);
        Assert.assertEquals(span1.getStart(), 2);
        Assert.assertEquals(span1.getSpan(), 3);

        final Map<Integer, AlignmentSpan> referenceSet2 = parser.getReferences(containers.get(2), ValidationStringency.STRICT);
        Assert.assertNotNull(referenceSet2);
        Assert.assertEquals(referenceSet2.size(), 3);

        // contains the spans we checked earlier
        Assert.assertTrue(referenceSet2.containsKey(0));
        Assert.assertEquals(referenceSet2.get(0), span0);
        Assert.assertTrue(referenceSet2.containsKey(1));
        Assert.assertEquals(referenceSet2.get(1), span1);

        Assert.assertTrue(referenceSet2.containsKey(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX));
        final AlignmentSpan span2 = referenceSet2.get(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
        Assert.assertEquals(span2.getCount(), 1);
    }
}
