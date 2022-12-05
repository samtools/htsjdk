package htsjdk.samtools.cram.ref;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.CRAMFileReader;
import htsjdk.samtools.Defaults;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import htsjdk.samtools.util.SequenceUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static htsjdk.utils.SamtoolsTestUtils.getSamtoolsBin;
import static htsjdk.utils.SamtoolsTestUtils.isSamtoolsAvailable;

/**
 * Created by vadim on 29/06/2017.
 */
public class ReferenceSourceTest extends HtsjdkTest{

    private final String TEST_DIR = "src/test/resources/htsjdk/samtools/cram/";
    private final String REF_CACHE_DIR = "src/test/resources/htsjdk/samtools/ref_cache/";
    private final String MD5_REFERENCE = "7ddd8a4b4f2c1dec43476a738b1a9b72";
    private final String[] SAMTOOLS_ENVP = {"REF_CACHE=" + REF_CACHE_DIR};

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

    @Test(groups = {"refCache"})
    public void testReferenceLocalDiskCache() {
        // requires -Dsamjdk.ref_cache=src/test/resources/htsjdk/samtools/ref_cache/%s
        final File cramFile = new File(TEST_DIR + "auxf#values.3.0.cram");
        CRAMReferenceSource refSource = ReferenceSource.getDefaultCRAMReferenceSource();
        CRAMFileReader cramFileReader = new CRAMFileReader(cramFile, refSource);

        // find reference by MD5
        cramFileReader.getIterator().next();
    }

    @Test(groups = {"refCacheMultilevel"})
    public void testReferenceLocalDiskCacheWithSubdirectory() throws IOException {
        String dirName = MD5_REFERENCE.substring(0, 2);
        String refFileName = MD5_REFERENCE.substring(2);
        Path tmpDir = Paths.get(Defaults.REF_CACHE.replaceFirst("%[0-9s/%]*", ""));
        Path subDir = null;
        try {
            // set up two-level cache in a temporary directory
            subDir = Files.createDirectory(tmpDir.resolve(dirName));
            final File cramFile = new File(TEST_DIR + "auxf#values.3.0.cram");
            CRAMReferenceSource refSource = ReferenceSource.getDefaultCRAMReferenceSource();
            CRAMFileReader cramFileReader = new CRAMFileReader(cramFile, refSource);

            // copy reference file to subDir removing the first two characters of the file name
            Path sourceRef = Paths.get(REF_CACHE_DIR + MD5_REFERENCE);
            Files.copy(sourceRef, subDir.resolve(refFileName));

            // find reference by MD5
            cramFileReader.getIterator().next();
        } finally {
            // remove temporary resources
            if (subDir != null) {
                Files.deleteIfExists(subDir.resolve(refFileName));
                Files.deleteIfExists(subDir);
            }
        }
    }

    @Test
    public void testInteroperabilityWithSamtools() throws IOException, InterruptedException {
        if (isSamtoolsAvailable()) {
            final String commandString = getSamtoolsBin() + " view " + TEST_DIR + "auxf#values.3.0.cram";
            // provide path to reference in the REF_CACHE environment variable
            Process process = Runtime.getRuntime().exec(commandString, SAMTOOLS_ENVP);
            process.waitFor();
            Assert.assertEquals(0, process.exitValue());
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            Assert.assertTrue(reader.readLine().startsWith("Fred"));
        }
    }
}
