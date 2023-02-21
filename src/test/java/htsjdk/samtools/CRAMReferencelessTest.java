package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.ref.ReferenceSource;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

public class CRAMReferencelessTest extends HtsjdkTest {

    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/cram");

    @Test
    public void testReadCRAMWithEmbeddedReference() throws IOException {
        try (final SamReader cramReader = SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.LENIENT)
                .referenceSource(new ReferenceSource(new File(TEST_DATA_DIR, "human_g1k_v37.20.21.1-100.fasta")))
                .open(new File(TEST_DATA_DIR, "NA12878.20.21.1-100.100-SeqsPerSlice.500-unMapped.cram"));
             final SamReader cramReaderEmbedded = SamReaderFactory.makeDefault()
                     .validationStringency(ValidationStringency.LENIENT)
                     .open(new File(TEST_DATA_DIR, "referenceEmbedded.NA12878.20.21.1-100.100-SeqsPerSlice.500-unMapped.cram"))) {
            final Iterator<SAMRecord> cramIterator = cramReader.iterator();
            final Iterator<SAMRecord> cramEmbeddedIterator = cramReaderEmbedded.iterator();
            int count = 0;
            while (cramIterator.hasNext() && cramEmbeddedIterator.hasNext()) {
                count++;
                final SAMRecord cramRecord = cramIterator.next();
                final SAMRecord cramRecordEmbedded = cramEmbeddedIterator.next();
                Assert.assertEquals(cramRecordEmbedded, cramRecord);
            }
            Assert.assertTrue( count >0, "Expected reads but there were none.");
        }
    }

    // test for https://github.com/igvteam/igv/issues/1286
    // cram was generated subset from an example cram using samtools and -O no_ref
    @Test
    public void testForNPE() throws IOException {
        try (final SamReader cramReader = SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.LENIENT)
                .open(new File(TEST_DATA_DIR, "testIGV1286.sam"));
             final SamReader cramReaderEmbedded = SamReaderFactory.makeDefault()
                     .validationStringency(ValidationStringency.LENIENT)
                     .open(new File(TEST_DATA_DIR, "testIGV1286.cram"))) {
            final Iterator<SAMRecord> cramIterator = cramReader.iterator();
            final Iterator<SAMRecord> cramEmbeddedIterator = cramReaderEmbedded.iterator();
            int count = 0;
            while (cramIterator.hasNext() && cramEmbeddedIterator.hasNext()) {
                count++;
                final SAMRecord cramRecord = cramIterator.next();
                final SAMRecord cramRecordEmbedded = cramEmbeddedIterator.next();
                Assert.assertEquals(cramRecordEmbedded, cramRecord);
            }
            Assert.assertEquals(count, 2);
            Assert.assertFalse(cramIterator.hasNext());
            Assert.assertFalse(cramEmbeddedIterator.hasNext());
        }
    }

    @Test
    public void testReadCRAMNoReferenceRequired() throws IOException {
        // test reading a cram with no reference compression (RR=false in compression header)
        try (final SamReader samReader = SamReaderFactory.makeDefault()
                             .validationStringency(ValidationStringency.LENIENT)
                             .open(new File(TEST_DATA_DIR, "referenceNotRequired.cram"))) {
            final Iterator<SAMRecord> iterator = samReader.iterator();
            while (iterator.hasNext()) {
                final SAMRecord samRecord1 = iterator.next();
                Assert.assertEquals(samRecord1.getReadString(),
                "CTAACCCTAACCCTACCCCTAACCCTAAACCTACCCCTAACCCTAACCCTAACCCTAACCCTAACCCTAACCATAATCCTAACCCTAAACCTA"
                        + "ACCCTAACCCATAACCACTAAACCCTAACCCCTAACCCCTAACCCTAACCCTAACCC");
                Assert.assertTrue(iterator.hasNext());
                final SAMRecord samRecord2 = iterator.next();
                Assert.assertEquals(samRecord2.getReadString(),
                        "CCCTAACCCTACCCCTAACCCTAACCGTACCCCTAACCCTACCCCAAAACAACCCCAAACCCAAACCCAACCAAAAACCCGAGCCC"
                                + "GCACCCGGACCCTAAACCGAGCCCCCGCGGGGGAGGCACGAGGGGCGGGGGAGACGGGGCGGGG");
                Assert.assertFalse(iterator.hasNext());
            }
        }
    }

}
