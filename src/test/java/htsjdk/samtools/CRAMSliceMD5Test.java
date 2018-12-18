package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.cram.structure.Slice;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import htsjdk.samtools.util.SequenceUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Created by vadim on 03/07/2017.
 */
public class CRAMSliceMD5Test extends HtsjdkTest{

    @Test
    public void testSliceMD5() throws IOException {
        final CramTestCase test = new CramTestCase();

        // read the CRAM:
        final ByteArrayInputStream bais = new ByteArrayInputStream(test.cramData);
        final CramHeader cramHeader = CramIO.readCramHeader(bais);
        final Container container = ContainerIO.readContainer(cramHeader.getVersion(), bais);
        final Slice slice = container.slices[0];
        Assert.assertEquals(slice.alignmentStart, 1);
        Assert.assertEquals(slice.alignmentSpan, test.referenceBases.length);
        // check the slice MD5 is the MD5 of upper-cased ref bases:
        final byte[] ucRefMD5 = SequenceUtil.calculateMD5(test.refBasesFromUCSource, 0, test.refBasesFromUCSource.length);
        Assert.assertEquals(slice.refMD5, ucRefMD5);

        // check the CRAM file reads:
        final CRAMFileReader reader = new CRAMFileReader(new ByteArrayInputStream(test.cramData), (File) null, test.referenceSourceUpperCased, ValidationStringency.STRICT);
        final SAMRecordIterator iterator = reader.getIterator();
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), test.record);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testExceptionWhileReadingWithWrongReference() throws IOException {
        final CramTestCase test = new CramTestCase();

        // try reading the CRAM file with the incorrect ref source that does not upper case bases:
        final CRAMFileReader reader = new CRAMFileReader(new ByteArrayInputStream(test.cramData), (File) null, test.referenceSourceMixedCase, ValidationStringency.STRICT);
        final SAMRecordIterator iterator = reader.getIterator();
        // expect an exception here due to slice MD5 mismatch:
        iterator.hasNext();
    }


    /**
     * A test case to demonstrate the effect of upper casing of reference bases.
     * The class contains some assertions in the constructor to stress out reference bases case expectations.
     */
    private static class CramTestCase {
        private final byte[] referenceBases;
        private final byte[] referenceBasesUpperCased;
        private final SAMFileHeader samFileHeader;
        /**
         * An invalid reference source that does not change bases:
         */
        private final CRAMReferenceSource referenceSourceMixedCase;
        private final InMemoryReferenceSequenceFile memoryReferenceSequenceFile;
        /**
         * A valid reference source that uppercases reference bases:
         */
        private final ReferenceSource referenceSourceUpperCased;
        private final byte[] refBasesFromUCSource;
        private final byte[] refBasesFromMixedCaseSource;
        private final SAMRecord record;
        private final byte[] cramData;

        private CramTestCase() {
            referenceBases = SequenceUtil.getIUPACCodesString().getBytes();
            referenceBasesUpperCased = SequenceUtil.upperCase(Arrays.copyOf(referenceBases, referenceBases.length));

            samFileHeader = new SAMFileHeader();
            samFileHeader.addSequence(new SAMSequenceRecord("1", referenceBases.length));
            samFileHeader.addReadGroup(new SAMReadGroupRecord("rg1"));

            // this source does not change ref bases:
            referenceSourceMixedCase = (sequenceRecord, tryNameVariants) -> referenceBases;

            memoryReferenceSequenceFile = new InMemoryReferenceSequenceFile();
            // copy ref bases to avoid the original from upper casing:
            memoryReferenceSequenceFile.add("1", Arrays.copyOf(referenceBases, referenceBases.length));
            // this is the correct reference source, it upper cases ref bases:
            referenceSourceUpperCased = new ReferenceSource(memoryReferenceSequenceFile);

            refBasesFromUCSource = referenceSourceUpperCased.getReferenceBases(samFileHeader.getSequence(0), true);
            // check the ref bases from the source are upper cased indeed:
            Assert.assertEquals(refBasesFromUCSource, referenceBasesUpperCased);
            // check there is no lower case A:
            Assert.assertTrue(!new String(refBasesFromUCSource).contains("a"));

            refBasesFromMixedCaseSource = referenceSourceMixedCase.getReferenceBases(samFileHeader.getSequence(0), true);
            // check the mixed case source does not change ref base casing:
            Assert.assertEquals(refBasesFromMixedCaseSource, referenceBases);
            // check the mixed case source contains lower case bases:
            Assert.assertTrue(new String(refBasesFromMixedCaseSource).contains("a"));

            final int readLen = referenceBases.length;
            final byte[] bases = new byte[readLen];
            Arrays.fill(bases, (byte) 'A');
            final byte[] scores = new byte[readLen];
            Arrays.fill(scores, (byte) '!');

            record = new SAMRecord(samFileHeader);
            record.setReadName("test");
            record.setReferenceIndex(0);
            record.setAlignmentStart(1);
            record.setCigarString(readLen + "M");
            record.setReadBases(bases);
            record.setBaseQualities(scores);

            // write a valid CRAM with a valid reference source:
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (final CRAMFileWriter writer = new CRAMFileWriter(baos, referenceSourceUpperCased, samFileHeader, "test")) {
                writer.addAlignment(record);
            }
            cramData = baos.toByteArray();
        }
    }


}