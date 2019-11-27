package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

public class CRAMReferencelessTest extends HtsjdkTest {

    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/cram");

    @Test
    private void testReadCRAMWithEmbeddedReference() throws IOException {
        try (final SamReader cramReader = SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.LENIENT)
                .referenceSource(new ReferenceSource(new File(TEST_DATA_DIR, "human_g1k_v37.20.21.1-100.fasta")))
                .open(new File(TEST_DATA_DIR, "NA12878.20.21.1-100.100-SeqsPerSlice.500-unMapped.cram"));
             final SamReader cramReaderEmbedded = SamReaderFactory.makeDefault()
                     .validationStringency(ValidationStringency.LENIENT)
                     .open(new File(TEST_DATA_DIR, "referenceEmbedded.NA12878.20.21.1-100.100-SeqsPerSlice.500-unMapped.cram"))) {
            final Iterator<SAMRecord> cramIterator = cramReader.iterator();
            final Iterator<SAMRecord> cramEmbeddedIterator = cramReaderEmbedded.iterator();
            while (cramIterator.hasNext() && cramEmbeddedIterator.hasNext()) {
                final SAMRecord cramRecord = cramIterator.next();
                final SAMRecord cramRecordEmbedded = cramEmbeddedIterator.next();
                Assert.assertEquals(cramRecordEmbedded, cramRecord);
            }
        }
    }

    @Test
    private void testReadCRAMNoReferenceRequired() throws IOException {
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
