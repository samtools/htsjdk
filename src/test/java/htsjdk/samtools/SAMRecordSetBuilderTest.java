package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import htsjdk.samtools.reference.ReferenceSequenceFileFactory;
import htsjdk.samtools.util.CollectionUtil;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.samtools.util.TestUtil;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Path;

public class SAMRecordSetBuilderTest extends HtsjdkTest {

    @Test
    public void testWriteDefaultReference() throws IOException {

        SAMRecordSetBuilder samRecords = new SAMRecordSetBuilder(true,
                SAMFileHeader.SortOrder.coordinate,
                true,
                50000);


        final Path dir = TestUtil.getTempDirectory("SAMRecordSetBuilderTest", "testWriteRandomReference").toPath();
        final Path fasta = dir.resolve( "output.fa");
        final Path dict = dir.resolve(  "output.dict");

        samRecords.writeRandomReference(fasta);
        checkFastaFile(samRecords.getHeader(), fasta, dict);
        IOUtil.recursiveDelete(dir);
    }

    @Test
    public void testDeterministicWriteRandomReference() throws IOException {
        final Path dir1 = TestUtil.getTempDirectory("SAMRecordSetBuilderTest", "testWriteRandomReference").toPath();
        final Path dir2 = TestUtil.getTempDirectory("SAMRecordSetBuilderTest", "testWriteRandomReference").toPath();

        try {
            for (final Path dir : CollectionUtil.makeSet(dir1, dir2)) {
                final SAMFileHeader header = new SAMFileHeader();
                header.addSequence(new SAMSequenceRecord("contig1", 100));
                header.addSequence(new SAMSequenceRecord("contig2", 200));
                header.addSequence(new SAMSequenceRecord("contig3", 300));
                header.addSequence(new SAMSequenceRecord("chr_order", 50));

                final Path fasta = dir.resolve("output.fasta");
                final Path dict = dir.resolve("output.dict");
                SAMRecordSetBuilder.writeRandomReference(header, fasta);

                checkFastaFile(header, fasta, dict);
            }
            SAMSequenceDictionary dict1 = SAMSequenceDictionaryExtractor.extractDictionary(dir1.resolve("output.dict"));
            SAMSequenceDictionary dict2 = SAMSequenceDictionaryExtractor.extractDictionary(dir2.resolve("output.dict"));

            Assert.assertTrue(SequenceUtil.areSequenceDictionariesEqual(dict1, dict2));

        } finally {
            IOUtil.recursiveDelete(dir1);
            IOUtil.recursiveDelete(dir2);
        }
    }

    static public void checkFastaFile(final SAMFileHeader header, final Path fasta, final Path dict) throws IOException {
        final SAMSequenceDictionary samSequenceDictionary = SAMSequenceDictionaryExtractor.extractDictionary(dict);
        Assert.assertTrue(header.getSequenceDictionary().isSameDictionary(samSequenceDictionary));

        try (ReferenceSequenceFile sequenceFile = ReferenceSequenceFileFactory.getReferenceSequenceFile(fasta)) {

            header.getSequenceDictionary().getSequences().forEach(s -> {

                final ReferenceSequence referenceSequence = sequenceFile.getSequence(s.getSequenceName());
                Assert.assertTrue(sequenceFile.isIndexed());
                Assert.assertEquals(referenceSequence.getBases().length, referenceSequence.length());
                Assert.assertEquals(referenceSequence.length(), s.getSequenceLength());
                Assert.assertEquals(referenceSequence.getName(), s.getSequenceName());
                if (s.getMd5() != null) {
                    Assert.assertEquals(sequenceFile.getSequenceDictionary().getSequence(referenceSequence.getName()).getMd5(), s.getMd5());
                }
            });
            // the md5's should be all different
            if (samSequenceDictionary.size() > 1) {
                final String md5 = samSequenceDictionary.getSequence(0).getMd5();

                if (md5 != null) {
                    samSequenceDictionary.getSequences().stream().skip(1).forEach(s -> {
                        Assert.assertNotEquals(md5, s.getMd5(),
                                "md5s of sequence " + samSequenceDictionary.getSequence(0).getSequenceName() + " and " + s.getSequenceName() + " are the same!");
                    });
                }
            }
        }
    }

}
