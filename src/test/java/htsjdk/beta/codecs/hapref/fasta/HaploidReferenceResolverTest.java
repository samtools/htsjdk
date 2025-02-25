package htsjdk.beta.codecs.hapref.fasta;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleResource;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.plugin.registry.HaploidReferenceResolver;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class HaploidReferenceResolverTest extends HtsjdkTest {

    @DataProvider
    public Object[][] bundleCases() {
        final String dataDir = "src/test/resources/htsjdk/samtools/reference";

        return new Object[][] {
                {
                        new HtsPath(new File(dataDir, "Homo_sapiens_assembly18.trimmed.fasta").getAbsolutePath()),
                        null,
                        null,
                        null
                },
                {
                        new HtsPath(new File(dataDir, "Homo_sapiens_assembly18.trimmed.fasta").getAbsolutePath()),
                        new HtsPath(new File(dataDir, "Homo_sapiens_assembly18.trimmed.dict").getAbsolutePath()),
                        null,
                        null
                },
                {
                        new HtsPath(new File(dataDir, "Homo_sapiens_assembly18.trimmed.fasta").getAbsolutePath()),
                        new HtsPath(new File(dataDir, "Homo_sapiens_assembly18.trimmed.dict").getAbsolutePath()),
                        new HtsPath(new File(dataDir, "Homo_sapiens_assembly18.trimmed.fasta.fai").getAbsolutePath()),
                        null
                },
                {
                        new HtsPath(new File(dataDir, "Homo_sapiens_assembly18.trimmed.fasta.gz").getAbsolutePath()),
                        new HtsPath(new File(dataDir, "Homo_sapiens_assembly18.trimmed.dict").getAbsolutePath()),
                        new HtsPath(new File(dataDir, "Homo_sapiens_assembly18.trimmed.fasta.gz.fai").getAbsolutePath()),
                        new HtsPath(new File(dataDir, "Homo_sapiens_assembly18.trimmed.fasta.gz.gzi").getAbsolutePath()),
                },
        };
    }

    @Test(dataProvider = "bundleCases")
    public void testReferenceBundleFromPath(
            final IOPath fastaFile,
            final IOPath dictFile,
            final IOPath indexFile,
            final IOPath gziIndexFile // may be null
    ) throws IOException {
        // move whatever subset of files are provided into a jimfs NIO file system, with each file in the same
        // directory, so we can test inference of siblings and tolerance of missing siblings
        try (final FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path fastaDir = jimfs.getPath("fastaDir");
            final Path nioFastaDir = Files.createDirectory(fastaDir);
            Assert.assertEquals(nioFastaDir, fastaDir);

            // move the required fasta
            final IOPath remoteFasta = new HtsPath(
                    Files.copy(
                            fastaFile.toPath(),
                            nioFastaDir.resolve(fastaFile.getBaseName().get() + fastaFile.getExtension().get())).toUri().toString());

            // move the optional dictionary file
            IOPath remoteDict = null;
            if (dictFile != null) {
                remoteDict = new HtsPath(
                        Files.copy(
                                dictFile.toPath(),
                                nioFastaDir.resolve(dictFile.getBaseName().get() + dictFile.getExtension().get())).toUri().toString());
            }

            // move the optional index
            IOPath remoteIndex = null;
            if (indexFile != null) {
                remoteIndex = new HtsPath(
                        Files.copy(
                                indexFile.toPath(),
                                nioFastaDir.resolve(indexFile.getBaseName().get() + indexFile.getExtension().get())).toUri().toString());
            }

            // move the optional gzi index
            IOPath remoteGZI = null;
            if (gziIndexFile != null) {
                remoteGZI =
                        new HtsPath(
                                Files.copy(
                                        gziIndexFile.toPath(),
                                        nioFastaDir.resolve(gziIndexFile.getBaseName().get() + gziIndexFile.getExtension().get())
                                ).toUri().toString()
                        );
            }

            final Bundle bundle = HaploidReferenceResolver.referenceBundleFromFastaPath(remoteFasta, HtsPath::new);
            Assert.assertNotNull(bundle);

            if (dictFile != null) {
                final Optional<BundleResource> optDictResource = bundle.get(BundleResourceType.CT_REFERENCE_DICTIONARY);
                Assert.assertTrue(optDictResource.isPresent());
                final IOPath actualDictPath = optDictResource.get().getIOPath().get();
                Assert.assertEquals(actualDictPath, remoteDict);
            }

            if (indexFile != null) {
                final Optional<BundleResource> optIndexResource = bundle.get(BundleResourceType.CT_REFERENCE_INDEX);
                Assert.assertTrue(optIndexResource.isPresent());
                final IOPath actualindexPath = optIndexResource.get().getIOPath().get();
                Assert.assertEquals(remoteIndex, actualindexPath);
            }

            if (gziIndexFile!= null) {
                final Optional<BundleResource> optGZIResource = bundle.get(BundleResourceType.CT_REFERENCE_INDEX_GZI);
                Assert.assertTrue(optGZIResource.isPresent());
                final IOPath actualGZIPath = optGZIResource.get().getIOPath().get();
                Assert.assertEquals(remoteGZI, actualGZIPath);
            }
        }
    }
}
