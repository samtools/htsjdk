package htsjdk.samtools.reference;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.SAMSequenceDictionary;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;

public class AbstractFastaSequenceFileTest extends HtsjdkTest {
    final String dataDir = "src/test/resources/htsjdk/variant/utils/SamSequenceDictionaryExtractor/";

    // simple test to ensure that we can load the dictionary from an nio path
    @Test void testLoadSequenceDictionaryFromNIO() throws IOException {
        final IOPath testDictIOPath = new HtsPath(dataDir + "Homo_sapiens_assembly18.trimmed.dict");
        final SAMSequenceDictionary originalDict = AbstractFastaSequenceFile.loadSequenceDictionary(testDictIOPath);

        try (final FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
            final IOPath remoteDictIOPath = new HtsPath(jimfs.getPath("seqDict.dict").toUri().toString());
            final IOPath remoteDict = new HtsPath(
                    Files.copy(
                            testDictIOPath.toPath(),
                            remoteDictIOPath.toPath()
                    ).toUri().toString());
            Assert.assertEquals(remoteDictIOPath, remoteDict);

            final SAMSequenceDictionary remoteSamDict = AbstractFastaSequenceFile.loadSequenceDictionary(remoteDictIOPath);
            Assert.assertEquals(remoteSamDict, originalDict);
        }
    }

    @Test void testLoadSequenceDictionaryWithNull() {
        final SAMSequenceDictionary dict = AbstractFastaSequenceFile.loadSequenceDictionary(null);
        Assert.assertNull(dict);
    }

}

