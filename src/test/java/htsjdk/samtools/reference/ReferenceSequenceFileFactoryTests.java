package htsjdk.samtools.reference;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;

/**
 * Simple tests for the reference sequence file factory
 */
public class ReferenceSequenceFileFactoryTests {
    public static final File hg18 = new File("src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta");

    @Test public void testPositivePath() {
        final ReferenceSequenceFile f = ReferenceSequenceFileFactory.getReferenceSequenceFile(hg18);
        Assert.assertTrue(f instanceof AbstractFastaSequenceFile);
    }

    @Test public void testGetIndexedReader() {
        final ReferenceSequenceFile f = ReferenceSequenceFileFactory.getReferenceSequenceFile(hg18, true, true);
        Assert.assertTrue(f instanceof IndexedFastaSequenceFile, "Got non-indexed reader when expecting indexed reader.");
    }

    @Test public void testGetNonIndexedReader1() {
        final ReferenceSequenceFile f = ReferenceSequenceFileFactory.getReferenceSequenceFile(hg18, false, true);
        Assert.assertTrue(f instanceof FastaSequenceFile, "Got indexed reader when truncating at whitespace! FAI must truncate.");
    }

    @Test public void testGetNonIndexedReader2() {
        final ReferenceSequenceFile f = ReferenceSequenceFileFactory.getReferenceSequenceFile(hg18, true, false);
        Assert.assertTrue(f instanceof FastaSequenceFile, "Got indexed reader when requesting non-indexed reader.");
    }

    @Test public void testDefaultToIndexed() {
        final ReferenceSequenceFile f = ReferenceSequenceFileFactory.getReferenceSequenceFile(hg18, true);
        Assert.assertTrue(f instanceof IndexedFastaSequenceFile, "Got non-indexed reader by default.");
    }

}
