package htsjdk.samtools.reference;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleBuilder;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.io.bundle.IOPathResource;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.SAMSequenceDictionary;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple tests for the reference sequence file factory
 */
public class ReferenceSequenceFileFactoryTests extends HtsjdkTest {
    public static final File hg18 = new File("src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta");
    public static final File hg18bgzip = new File("src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta.gz");

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

    @Test public void testBlockCompressedIndexed() {
        final ReferenceSequenceFile f = ReferenceSequenceFileFactory.getReferenceSequenceFile(hg18bgzip, true);
        Assert.assertTrue(f instanceof BlockCompressedIndexedFastaSequenceFile);
    }

    @DataProvider
    public Object[][] canCreateIndexedFastaParams() {
        return new Object[][] {
                {hg18, true},
                {hg18bgzip, true},
                {new File("src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.noindex.fasta"), false},
                {new File("src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.noindex.fasta.gz"), false},
                {new File("src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.nogzindex.fasta.gz"), false}
        };
    }

    @Test(dataProvider = "canCreateIndexedFastaParams")
    public void testCanCreateIndexedFastaReader(final File path, final boolean indexed) {
        Assert.assertEquals(ReferenceSequenceFileFactory.canCreateIndexedFastaReader(path.toPath()), indexed);
    }

    @DataProvider
    public Object[][] fastaNames() {
        return new Object[][] {
                {"break.fa", "break.dict"},
                {"break.txt.txt", "break.txt.dict"},
                {"break.fasta.fasta", "break.fasta.dict"},
                {"break.fa.gz", "break.dict"},
                {"break.txt.gz.txt.gz", "break.txt.gz.dict"},
                {"break.fasta.gz.fasta.gz", "break.fasta.gz.dict"}
        };
    }

    @Test(dataProvider = "fastaNames")
    public void testGetDefaultDictionaryForReferenceSequence(final String fastaFile, final String expectedDict) throws Exception {
        Assert.assertEquals(ReferenceSequenceFileFactory.getDefaultDictionaryForReferenceSequence(new File(fastaFile)), new File(expectedDict));
    }

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
    public void testReferenceSequenceForLocalBundle(
            final IOPath fastaFile,
            final IOPath dictFile,
            final IOPath indexFile,
            final IOPath gziIndexFile // may be null
    ) {
        doBundleTest(fastaFile, dictFile, indexFile, gziIndexFile);
    }

    @Test(dataProvider = "bundleCases")
    public void testReferenceSequenceForNioBundle(
            final IOPath fastaFile,
            final IOPath dictFile,
            final IOPath indexFile,
            final IOPath gziIndexFile // may be null
    ) throws IOException {
        // move everything to a jimfs NIO file system so that each file is in a separate directory, so we can
        // catch any downstream code that makes assumptions that the files are siblings in the same dir
        try (final FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {

            // move the fasta
            final Path fastaDir = jimfs.getPath("fastaDir");
            final Path nioFastaDir = Files.createDirectory(fastaDir);
            Assert.assertEquals(nioFastaDir, fastaDir);
            final IOPath remoteFasta = new HtsPath(
                    Files.copy(
                            fastaFile.toPath(),
                            nioFastaDir.resolve(fastaFile.getBaseName().get() + fastaFile.getExtension().get())).toUri().toString());

            // move the optional dictionary file
            IOPath remoteDict = null;
            if (dictFile != null) {
                final Path dictDir = jimfs.getPath("dictDir");
                final Path nioDictDir = Files.createDirectory(dictDir);
                Assert.assertEquals(nioDictDir, dictDir);
                remoteDict = new HtsPath(
                        Files.copy(
                                dictFile.toPath(),
                                nioDictDir.resolve(dictFile.getBaseName().get() + dictFile.getExtension().get())).toUri().toString());
            }

            // move the optional index
            IOPath remoteIndex = null;
            if (indexFile != null) {
                final Path indexDir = jimfs.getPath("indexDir");
                final Path nioIndexDir = Files.createDirectory(indexDir);
                Assert.assertEquals(nioIndexDir, indexDir);
                remoteIndex = new HtsPath(
                        Files.copy(
                                indexFile.toPath(),
                                nioFastaDir.resolve(indexFile.getBaseName().get() + indexFile.getExtension().get())).toUri().toString());
            }

            // move the optional gzi index
            IOPath remoteGZI = null;
            if (gziIndexFile != null) {
                final Path gziDir = jimfs.getPath("gziDir");
                final Path nioGZIDir = Files.createDirectory(gziDir);
                Assert.assertEquals(nioGZIDir, gziDir);
                remoteGZI =
                        new HtsPath(
                                Files.copy(
                                        gziIndexFile.toPath(),
                                        nioGZIDir.resolve(gziIndexFile.getBaseName().get() + gziIndexFile.getExtension().get())
                                ).toUri().toString()
                        );
            }

            doBundleTest(remoteFasta, remoteDict, remoteIndex, remoteGZI);
        }
    }

    private void doBundleTest(
        final IOPath fastaFile,
        final IOPath dictFile,
        final IOPath indexFile,
        final IOPath gziIndexFile) {

        // create a bundle for all of our resources
        final BundleBuilder bundleBuilder = new BundleBuilder();
        bundleBuilder.addPrimary(new IOPathResource(fastaFile, BundleResourceType.CT_HAPLOID_REFERENCE));
        if (null != dictFile) {
            bundleBuilder.addSecondary(new IOPathResource(dictFile, BundleResourceType.CT_REFERENCE_DICTIONARY));
        }
        if (null != indexFile) {
            bundleBuilder.addSecondary(new IOPathResource(indexFile, BundleResourceType.CT_REFERENCE_INDEX));
        }
        if (null != gziIndexFile) {
            bundleBuilder.addSecondary(new IOPathResource(gziIndexFile, BundleResourceType.CT_REFERENCE_INDEX_GZI));
        }
        final Bundle referenceBundle = bundleBuilder.build();

        final ReferenceSequenceFile rsf = ReferenceSequenceFileFactory.getReferenceSequenceFileFromBundle(referenceBundle, true, true);
        Assert.assertEquals(indexFile != null, rsf.isIndexed());

        if (dictFile != null) {
            final SAMSequenceDictionary samDict = AbstractFastaSequenceFile.loadSequenceDictionary(dictFile);
                Assert.assertNotNull(rsf.getSequenceDictionary());
            Assert.assertEquals(rsf.getSequenceDictionary(), samDict);
        }

        if (indexFile != null) {
            final String seq = rsf.getSubsequenceAt("chrM", 4, 10).getBaseString();
            Assert.assertEquals(seq, "CACAGGT");
        }
    }
}
