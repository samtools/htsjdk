package htsjdk.samtools.cram.ref;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.CRAMFileReader;
import htsjdk.samtools.Defaults;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import htsjdk.samtools.util.SequenceUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Created by vadim on 29/06/2017.
 */
public class ReferenceSourceTest extends HtsjdkTest{

    private final String TEST_DIR = "src/test/resources/htsjdk/samtools/cram/";
    private final String MD5_REFERENCE = "7ddd8a4b4f2c1dec43476a738b1a9b72";

    @Test
    public void testReferenceSourceUpperCasesBases() {
        final String sequenceName = "1";
        final String nonIupacCharacters = "1=eE";
        final byte[] originalRefBases = (nonIupacCharacters + SequenceUtil.getIUPACCodesString()).getBytes();
        SAMSequenceRecord sequenceRecord = new SAMSequenceRecord(sequenceName, originalRefBases.length);

        InMemoryReferenceSequenceFile memoryReferenceSequenceFile = new InMemoryReferenceSequenceFile();
        memoryReferenceSequenceFile.add(sequenceName, Arrays.copyOf(originalRefBases, originalRefBases.length));
        Assert.assertEquals(memoryReferenceSequenceFile.getSequence(sequenceName).getBases(), originalRefBases);

        ReferenceSource referenceSource = new ReferenceSource(memoryReferenceSequenceFile);
        byte[] refBasesFromSource = referenceSource.getReferenceBases(sequenceRecord, false);

        Assert.assertNotEquals(refBasesFromSource, originalRefBases);
        Assert.assertEquals(refBasesFromSource, SequenceUtil.upperCase(originalRefBases));
    }

    @Test
    public void testReferenceLocalDiskCache() {
        System.setProperty("samjdk.ref_cache", TEST_DIR + "%s");
        final File cramFile = new File(TEST_DIR + "auxf#values.3.0.cram");
        CRAMReferenceSource refSource = ReferenceSource.getDefaultCRAMReferenceSource();
        CRAMFileReader cramFileReader = new CRAMFileReader(cramFile, refSource);

        // find reference by MD5, maps to "src/test/resources/htsjdk/samtools/cram/7ddd8a4b4f2c1dec43476a738b1a9b72"
        cramFileReader.getIterator().next();
    }

    @Test
    public void testReferenceLocalDiskCacheWithSubdirectory() throws IOException {
        System.setProperty("samjdk.ref_cache", TEST_DIR + "%2s/%s");
        final File cramFile = new File(TEST_DIR + "auxf#values.3.0.cram");
        CRAMReferenceSource refSource = ReferenceSource.getDefaultCRAMReferenceSource();
        CRAMFileReader cramFileReader = new CRAMFileReader(cramFile, refSource);

        // copy reference file to "src/test/resources/htsjdk/samtools/cram/7d/dd8a4b4f2c1dec43476a738b1a9b72"
        String dirName = MD5_REFERENCE.substring(0, 2);
        Path subDir = Paths.get(TEST_DIR + dirName);
        if (!Files.exists(subDir))
            Files.createDirectory(subDir);
        Path sourceRef = Paths.get(TEST_DIR + MD5_REFERENCE);
        Files.copy(sourceRef, subDir.resolve(MD5_REFERENCE.substring(2)));

        // find reference by MD5
        cramFileReader.getIterator().next();

        // remove temporary resources
        Files.delete(subDir.resolve(MD5_REFERENCE.substring(2)));
        Files.delete(subDir);
    }
}
