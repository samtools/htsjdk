package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import htsjdk.samtools.reference.ReferenceSequenceFileFactory;
import htsjdk.samtools.util.TestUtil;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.stream.Collectors;

public class SAMRecordSetBuilderTest extends HtsjdkTest {

    @Test
    public void testWriteDefaultReference() throws IOException {

        SAMRecordSetBuilder samRecords = new SAMRecordSetBuilder(true,
                SAMFileHeader.SortOrder.coordinate,
                true,
                50000);


        final File dir = TestUtil.getTempDirectory("SAMRecordSetBuilderTest", "testWriteRandomReference");
        final File fasta = new File(dir, "output.fa");
        final File dict = new File(dir, "output.dict");

        samRecords.writeRandomReference(fasta.toPath());
        checkFastaFile(samRecords.getHeader(),fasta,dict);
        TestUtil.recursiveDelete(dir);
    }

    @DataProvider
    Iterator<Object[]> fastaExtensions() {
        return ReferenceSequenceFileFactory.FASTA_EXTENSIONS.stream()
                .filter(s -> !s.endsWith(".gz"))
                .map(s -> new Object[]{s})
                .collect(Collectors.toList())
                .iterator();
    }

    @Test(dataProvider = "fastaExtensions")
    public void testWriteRandomReference(final String extension) throws IOException {
        final File dir = TestUtil.getTempDirectory("SAMRecordSetBuilderTest", "testWriteRandomReference");

        try {
            final SAMFileHeader header = new SAMFileHeader();
            header.addSequence(new SAMSequenceRecord("contig1", 100));
            header.addSequence(new SAMSequenceRecord("contig2", 200));
            header.addSequence(new SAMSequenceRecord("contig3", 300));
            header.addSequence(new SAMSequenceRecord("chr_order", 50));

            final File fasta = new File(dir, "output" + extension);
            final File dict = new File(dir, "output.dict");
            SAMRecordSetBuilder.writeRandomReference(header, fasta.toPath());

            checkFastaFile(header, fasta, dict);
        } finally {
            TestUtil.recursiveDelete(dir);
        }
    }

    static private void checkFastaFile(final SAMFileHeader header, final File fasta, final File dict) throws IOException {
        final SAMSequenceDictionary samSequenceDictionary = SAMSequenceDictionaryExtractor.extractDictionary(dict.toPath());
        Assert.assertTrue(header.getSequenceDictionary().isSameDictionary(samSequenceDictionary));

        try (ReferenceSequenceFile sequenceFile = ReferenceSequenceFileFactory.getReferenceSequenceFile(fasta.toPath())) {

            header.getSequenceDictionary().getSequences().forEach(s -> {

                final ReferenceSequence referenceSequence = sequenceFile.getSequence(s.getSequenceName());
                Assert.assertTrue(sequenceFile.isIndexed());
                Assert.assertEquals(referenceSequence.getBases().length, referenceSequence.length());
                Assert.assertEquals(referenceSequence.length(), s.getSequenceLength());
                Assert.assertEquals(referenceSequence.getName(), s.getSequenceName());
            });
        }
    }
}
