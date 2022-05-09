package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.beta.io.IOPathUtils;
import htsjdk.io.IOPath;
import htsjdk.samtools.cram.build.CramContainerIterator;
import htsjdk.samtools.util.IOUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

public class CRAMContainerStreamRewriterTest extends HtsjdkTest {
    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/cram");

    // use a test file with artificially small containers and slices, since it has multiple containers
    // (most test files in the repo use the default container size and have only one container), includes
    // some unmapped reads, and  already has an index
    public final static File testCRAM = new File(TEST_DATA_DIR, "NA12878.20.21.1-100.100-SeqsPerSlice.500-unMapped.cram");
    public final static File testFASTA = new File(TEST_DATA_DIR, "human_g1k_v37.20.21.1-100.fasta");

    private enum CRAM_TEST_INDEX_TYPE {
        NO_INDEX,
        BAI_INDEX,
        CRAI_INDEX
    }

    @DataProvider(name="containerStreamRewriterTests")
    public Object[] getContainerStreamRewriterTests() {
        return new Object[][] {
                {
                        testCRAM,
                        testFASTA,
                        CRAM_TEST_INDEX_TYPE.NO_INDEX,
                        null },
                {
                        testCRAM,
                        testFASTA,
                        CRAM_TEST_INDEX_TYPE.BAI_INDEX,
                        (Function<SamReader, ?>) (SamReader samReader) -> samReader.query("20", 1, 100000, true),
                },
                {
                        testCRAM,
                        testFASTA,
                        CRAM_TEST_INDEX_TYPE.CRAI_INDEX,
                        (Function<SamReader, ?>) (SamReader samReader) -> samReader.query("20", 1, 100000, true),
                },
                {
                        testCRAM,
                        testFASTA,
                        CRAM_TEST_INDEX_TYPE.BAI_INDEX,
                        (Function<SamReader, ?>) (SamReader samReader) -> samReader.query("20", 34, 134, false),
                },
                {
                        testCRAM,
                        testFASTA,
                        CRAM_TEST_INDEX_TYPE.CRAI_INDEX,
                        (Function<SamReader, ?>) (SamReader samReader) -> samReader.query("20", 34, 134, false),
                },
                {
                        testCRAM,
                        testFASTA,
                        CRAM_TEST_INDEX_TYPE.BAI_INDEX,
                        (Function<SamReader, ?>) (SamReader samReader) -> samReader.queryUnmapped(),
                },
                {
                        testCRAM,
                        testFASTA,
                        CRAM_TEST_INDEX_TYPE.CRAI_INDEX,
                        (Function<SamReader, ?>) (SamReader samReader) -> samReader.queryUnmapped(),
                }
        };
    }
    @Test(dataProvider = "containerStreamRewriterTests")
    private void testCRAMRewriteContainerStream(
            final File testCRAM,
            final File referenceFile,
            final CRAM_TEST_INDEX_TYPE indexType,
            final Function<SamReader, Iterator<SAMRecord>> queryFunction) throws IOException {
        final IOPath tempOutputCRAM = IOPathUtils.createTempPath("cramContainerStreamRewriterTest", ".cram");

        try (final CramContainerIterator cramContainerIterator =
                    new CramContainerIterator(new BufferedInputStream(new FileInputStream(testCRAM.toPath().toFile())));
            final BufferedOutputStream outputStream =
                    new BufferedOutputStream(new FileOutputStream(tempOutputCRAM.toPath().toFile()))
        ) {
            final CRAMContainerStreamRewriter containerStreamRewriter =
                    new CRAMContainerStreamRewriter(
                            outputStream,
                            cramContainerIterator.getCramHeader(),
                            cramContainerIterator.getSamFileHeader(),
                            "test",
                            getIndexerForType(indexType, tempOutputCRAM, cramContainerIterator.getSamFileHeader()));
            while (cramContainerIterator.hasNext()) {
                containerStreamRewriter.rewriteContainer(cramContainerIterator.next());
            }
            containerStreamRewriter.finish();
        }

        // iterate through all the records in the rewritten file and compare them with those in the original file
        try (final SamReader originalReader = SamReaderFactory.makeDefault()
                .referenceSequence(referenceFile)
                .validationStringency(ValidationStringency.SILENT)
                .open(testCRAM);
             final SamReader rewrittenReader = SamReaderFactory.makeDefault()
                     .referenceSequence(referenceFile)
                     .validationStringency(ValidationStringency.SILENT)
                     .open(tempOutputCRAM.toPath())) {
            // rewriting the SAMHeader "upgrades" it's version because it gets re-serialized by the text codec, so we
            // can't compare the headers directly, so settle for a sequence dictionary check
            Assert.assertEquals(
                    rewrittenReader.getFileHeader().getSequenceDictionary(),
                    originalReader.getFileHeader().getSequenceDictionary());

            final Iterator<SAMRecord> originalIterator = originalReader.iterator();
            final Iterator<SAMRecord> rewrittenIterator = rewrittenReader.iterator();
            while (originalIterator.hasNext() && rewrittenIterator.hasNext()) {
                Assert.assertEquals(originalIterator.next(), rewrittenIterator.next());
            }
            Assert.assertEquals(originalIterator.hasNext(), rewrittenIterator.hasNext());
        }

        // now compare the results from a simple index query on the original with the results from the rewritten file
        if (indexType != CRAM_TEST_INDEX_TYPE.NO_INDEX) {
            try (final SamReader originalReader = SamReaderFactory.makeDefault()
                    .referenceSequence(referenceFile)
                    .validationStringency(ValidationStringency.SILENT)
                    .open(testCRAM);
                 final SamReader rewrittenReader = SamReaderFactory.makeDefault()
                         .referenceSequence(referenceFile)
                         .validationStringency(ValidationStringency.SILENT)
                         .open(tempOutputCRAM.toPath())) {
                Assert.assertEquals(queryFunction.apply(originalReader), queryFunction.apply(rewrittenReader));
            }
        }
    }

    @Test
    private void testShardingReassembly() throws IOException {
        // break up a file into multiple shards (1 container per shard), then reassemble, and compare the results
        // of the contents of the original with the contents of the reassembled file
        final List<File> outputShards = new ArrayList<>();
        try (final CramContainerIterator cramContainerIterator =
                     new CramContainerIterator(new BufferedInputStream(new FileInputStream(testCRAM)))) {
            while (cramContainerIterator.hasNext()) {
                final IOPath tempOutputCRAM = IOPathUtils.createTempPath("cramContainerStreamRewriterTest", ".cram");
                outputShards.add(tempOutputCRAM.toPath().toFile());

                try (final BufferedOutputStream outputStream =
                             new BufferedOutputStream(new FileOutputStream(tempOutputCRAM.toPath().toFile()))) {
                    final CRAMContainerStreamRewriter containerStreamRewriter = new CRAMContainerStreamRewriter(
                            outputStream,
                            cramContainerIterator.getCramHeader(),
                            cramContainerIterator.getSamFileHeader(),
                            "test",
                            null);
                    containerStreamRewriter.rewriteContainer(cramContainerIterator.next());
                    containerStreamRewriter.finish();
                }
            }
        }

        // we need to make sure we have at least a few shards for this to be interesting
        Assert.assertEquals(outputShards.size(), 9);

        try (final SamReader originalReader = SamReaderFactory.makeDefault()
                .referenceSequence(testFASTA)
                .validationStringency(ValidationStringency.SILENT).open(testCRAM)) {
            final Iterator<SAMRecord> originalIterator = originalReader.iterator();
            final Iterator<SAMRecord> rewrittenIterator = getIteratorFromShards(outputShards);
            while (originalIterator.hasNext() && rewrittenIterator.hasNext()) {
                Assert.assertEquals(originalIterator.next(), rewrittenIterator.next());
            }
            Assert.assertEquals(originalIterator.hasNext(), rewrittenIterator.hasNext());
        }
    }

    private Iterator<SAMRecord> getIteratorFromShards(final List<File> outputShards) throws IOException {
        final List<SAMRecord> shardedSAMRecords = new ArrayList<>();
        for (final File shardFile: outputShards) {
            try (final SamReader originalReader = SamReaderFactory.makeDefault()
                    .referenceSequence(testFASTA)
                    .validationStringency(ValidationStringency.SILENT).open(shardFile)) {
                for ( final SAMRecord samRecord: originalReader) {
                    shardedSAMRecords.add(samRecord);
                }
            }
        }
        return shardedSAMRecords.iterator();
    }

    private CRAMIndexer getIndexerForType(
            final CRAM_TEST_INDEX_TYPE indexType,
            final IOPath cramFile,
            final SAMFileHeader samFileHeader) throws IOException {
        switch (indexType) {
            case NO_INDEX:
                return null;
            case BAI_INDEX:
                final Path tempOutputBAI = IOUtil.addExtension(cramFile.toPath(), ".bai");
                IOUtil.deleteOnExit(tempOutputBAI);
                return new CRAMBAIIndexer(
                        new BufferedOutputStream(new FileOutputStream(tempOutputBAI.toFile())),
                        samFileHeader);
            case CRAI_INDEX:
                final Path tempOutputCRAI = IOUtil.addExtension(cramFile.toPath(), ".crai");
                IOUtil.deleteOnExit(tempOutputCRAI);
                return new CRAMCRAIIndexer(
                        new BufferedOutputStream(new FileOutputStream(tempOutputCRAI.toFile())),
                        samFileHeader);
            default:
                throw new IllegalArgumentException("Unknown cram index type");
        }
    }

}
