package htsjdk.samtools;

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
public class CRAMSliceMD5Test {

    @Test
    public void testSliceMD5() throws IOException {
        final byte[] referenceBases = SequenceUtil.IUPAC_CODES_STRING.getBytes();
        final byte[] referenceBasesUpperCased = SequenceUtil.upperCase(Arrays.copyOf(referenceBases, referenceBases.length));

        final SAMFileHeader samFileHeader = new SAMFileHeader();
        samFileHeader.addSequence(new SAMSequenceRecord("1", referenceBases.length));
        samFileHeader.addReadGroup(new SAMReadGroupRecord("rg1"));

        // this source does not change ref bases:
        final CRAMReferenceSource referenceSourceMixedCase = (sequenceRecord, tryNameVariants) -> referenceBases;

        final InMemoryReferenceSequenceFile memoryReferenceSequenceFile = new InMemoryReferenceSequenceFile();
        // copy ref bases to avoid the original from upper casing:
        memoryReferenceSequenceFile.add("1", Arrays.copyOf(referenceBases, referenceBases.length));
        // this is the correct reference source, it upper cases ref bases:
        final ReferenceSource referenceSourceUpperCased = new ReferenceSource(memoryReferenceSequenceFile);

        byte[] refBasesFromUCSource = referenceSourceUpperCased.getReferenceBases(samFileHeader.getSequence(0), true);
        // check the ref bases from the source are upper cased indeed:
        Assert.assertEquals(refBasesFromUCSource, referenceBasesUpperCased);
        // check there is no lower case A:
        Assert.assertTrue(!new String(refBasesFromUCSource).contains("a"));

        byte[] refBasesFromMixedCaseSource = referenceSourceMixedCase.getReferenceBases(samFileHeader.getSequence(0), true);
        // check the mixed case source does not change ref base casing:
        Assert.assertEquals(refBasesFromMixedCaseSource, referenceBases);
        // check the mixed case source contains lower case bases:
        Assert.assertTrue(new String(refBasesFromMixedCaseSource).contains("a"));

        final int readLen = referenceBases.length;
        final byte[] bases = new byte[readLen];
        Arrays.fill(bases, (byte) 'A');
        final byte[] scores = new byte[readLen];
        Arrays.fill(scores, (byte) '!');

        final SAMRecord record = new SAMRecord(samFileHeader);
        record.setReadName("test");
        record.setReferenceIndex(0);
        record.setAlignmentStart(1);
        record.setCigarString(readLen + "M");
        record.setReadBases(bases);
        record.setBaseQualities(scores);

        // write a valid CRAM with a valid reference source:
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final CRAMFileWriter writer = new CRAMFileWriter(baos, referenceSourceUpperCased, samFileHeader, "test");
        writer.addAlignment(record);
        writer.close();

        // read the CRAM:
        final ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        final CramHeader cramHeader = CramIO.readCramHeader(bais);
        final Container container = ContainerIO.readContainer(cramHeader.getVersion(), bais);
        final Slice slice = container.slices[0];
        Assert.assertEquals(slice.alignmentStart, 1);
        Assert.assertEquals(slice.alignmentSpan, referenceBases.length);
        // check the slice MD5 is the MD5 of upper-cased ref bases:
        byte[] ucRefMD5 = SequenceUtil.calculateMD5(refBasesFromUCSource, 0, refBasesFromUCSource.length);
        Assert.assertEquals(slice.refMD5, ucRefMD5);

        // check the CRAM file reads:
        CRAMFileReader reader = new CRAMFileReader(new ByteArrayInputStream(baos.toByteArray()), (File) null, referenceSourceUpperCased, ValidationStringency.STRICT);
        SAMRecordIterator iterator = reader.getIterator();
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), record);

        // try reading the CRAM file with the incorrect ref source that does not upper case bases:
        reader = new CRAMFileReader(new ByteArrayInputStream(baos.toByteArray()), (File) null, referenceSourceMixedCase, ValidationStringency.STRICT);
        iterator = reader.getIterator();
        try {
            // expect an exception here due to slice MD5 mismath:
            Assert.assertTrue(iterator.hasNext());
            Assert.fail("Reference MD5s must mismatch.");
        } catch (final CRAMException e) {
            Assert.assertTrue(e.getMessage().startsWith("Reference sequence MD5 mismatch for slice: sequence id 0, start 1, span 31, expected MD5 "));
        }
    }
}