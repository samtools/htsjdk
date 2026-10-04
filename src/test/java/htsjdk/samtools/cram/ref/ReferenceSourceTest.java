package htsjdk.samtools.cram.ref;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceDictionaryCodec;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.reference.FastaSequenceIndexCreator;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.SequenceUtil;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Created by vadim on 29/06/2017.
 */
public class ReferenceSourceTest extends HtsjdkTest {

    @Test
    public void testReferenceSourceUpperCasesBases() {
        final String sequenceName = "1";
        final String nonIupacCharacters = "1=eE";
        final byte[] originalRefBases = (nonIupacCharacters + SequenceUtil.getIUPACCodesString()).getBytes();
        SAMSequenceRecord sequenceRecord = new SAMSequenceRecord(sequenceName, originalRefBases.length);

        InMemoryReferenceSequenceFile memoryReferenceSequenceFile = new InMemoryReferenceSequenceFile();
        memoryReferenceSequenceFile.add(sequenceName, Arrays.copyOf(originalRefBases, originalRefBases.length));
        Assert.assertEquals(
                memoryReferenceSequenceFile.getSequence(sequenceName).getBases(), originalRefBases);

        ReferenceSource referenceSource = new ReferenceSource(memoryReferenceSequenceFile);
        byte[] refBasesFromSource = referenceSource.getReferenceBases(sequenceRecord, false);

        Assert.assertNotEquals(refBasesFromSource, originalRefBases);
        Assert.assertEquals(refBasesFromSource, SequenceUtil.upperCase(originalRefBases));
    }

    @Test
    public void contigsAtTheSameIndexInDifferentDictionariesGetTheirOwnBases() {
        final InMemoryReferenceSequenceFile reference = new InMemoryReferenceSequenceFile();
        reference.add("chrA", "AAAA".getBytes());
        reference.add("chrB", "CCCC".getBytes());
        final ReferenceSource source = new ReferenceSource(reference);

        // two CRAMs sharing one source, whose dictionaries list chrA and chrB in opposite orders
        final SAMSequenceRecord chrAFirst = new SAMSequenceRecord("chrA", 4);
        chrAFirst.setSequenceIndex(0);
        final SAMSequenceRecord chrBFirst = new SAMSequenceRecord("chrB", 4);
        chrBFirst.setSequenceIndex(0);

        Assert.assertEquals(source.getReferenceBasesByRegion(chrAFirst, 0, 4), "AAAA".getBytes());
        Assert.assertEquals(source.getReferenceBasesByRegion(chrBFirst, 0, 4), "CCCC".getBytes());
        Assert.assertEquals(source.getReferenceBasesByRegion(chrAFirst, 1, 2), "AA".getBytes());
    }

    private static Path writeFasta(final Path dir) throws IOException {
        final Path fasta = dir.resolve("ref.fa");
        Files.writeString(fasta, ">chr1\nacgtacgt\n>chr2\ngggg\n");
        FastaSequenceIndexCreator.create(fasta, true);
        return fasta;
    }

    @Test
    public void theSequenceDictionaryComesFromTheReferenceFile() throws IOException {
        final Path dir = Files.createTempDirectory("referenceSourceTest");
        try {
            final Path fasta = writeFasta(dir);
            final SAMSequenceDictionary dictionary = new SAMSequenceDictionary(Arrays.asList(
                    new SAMSequenceRecord("chr1", 8).setMd5("a".repeat(32)), new SAMSequenceRecord("chr2", 4)));
            try (BufferedWriter writer = Files.newBufferedWriter(dir.resolve("ref.dict"))) {
                new SAMSequenceDictionaryCodec(writer).encode(dictionary);
            }
            final SAMSequenceDictionary read = new ReferenceSource(fasta).getSequenceDictionary();
            Assert.assertEquals(read.size(), 2);
            Assert.assertEquals(read.getSequence("chr1").getMd5(), "a".repeat(32));
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void theSequenceDictionaryIsNullWithoutOne() throws IOException {
        final Path dir = Files.createTempDirectory("referenceSourceTest");
        try {
            Assert.assertNull(new ReferenceSource(writeFasta(dir)).getSequenceDictionary());
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void theSequenceDictionaryIsNullWithoutAReferenceFile() {
        Assert.assertNull(new ReferenceSource((Path) null).getSequenceDictionary());
    }
}
