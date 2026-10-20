package htsjdk.tribble;

import static org.testng.Assert.*;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.samtools.FileTruncatedException;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtilTest;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.TestUtil;
import htsjdk.testutil.LogCapture;
import htsjdk.testutil.ftp.LocalFtpServer;
import htsjdk.testutil.http.LocalHttpServer;
import htsjdk.testutil.streams.RecordingChannelWrapper;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.bed.BEDFeature;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.tribble.util.LittleEndianOutputStream;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.bcf2.BCF2Codec;
import htsjdk.variant.bcf2.BCFVersion;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderVersion;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;
import java.util.zip.GZIPOutputStream;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author jacob
 * @date 2013-Apr-10
 */
public class AbstractFeatureReaderTest extends HtsjdkTest {

    static final String LOCAL_MIRROR_HTTP_INDEXED_VCF_PATH = VariantBaseTest.variantTestDataRoot + "ex2.vcf";

    // the "mangled" versions of the files have an extra byte added to the front of the file that makes them invalid
    private static final String TEST_PATH = "src/test/resources/htsjdk/tribble/AbstractFeatureReaderTest/";
    private static final String MANGLED_VCF = TEST_PATH + "mangledBaseVariants.vcf";
    private static final String MANGLED_VCF_INDEX = TEST_PATH + "mangledBaseVariants.vcf.idx";
    private static final String VCF = TEST_PATH + "baseVariants.vcf";
    private static final String VCF_INDEX = TEST_PATH + "baseVariants.vcf.idx";
    private static final String VCF_TABIX_BLOCK_GZIPPED = TEST_PATH + "baseVariants.vcf.gz";
    private static final String VCF_TABIX_INDEX = TEST_PATH + "baseVariants.vcf.gz.tbi";
    private static final String MANGLED_VCF_TABIX_BLOCK_GZIPPED = TEST_PATH + "baseVariants.mangled.vcf.gz";
    private static final String MANGLED_VCF_TABIX_INDEX = TEST_PATH + "baseVariants.mangled.vcf.gz.tbi";
    private static final String CORRUPTED_VCF_INDEX = TEST_PATH + "corruptedBaseVariants.vcf.idx";

    // wrapper which skips the first byte of a file and leaves the rest unchanged
    private static final Function<SeekableByteChannel, SeekableByteChannel> WRAPPER = SkippingByteChannel::new;

    /**
     * Asserts readability and correctness of VCF over HTTP.  The VCF is indexed and requires and index.
     */
    @Test
    public void testVcfOverHTTP() throws IOException {
        final VCFCodec codec = new VCFCodec();
        final Path localVcf = Path.of(LOCAL_MIRROR_HTTP_INDEXED_VCF_PATH);
        final ByteArrayOutputStream index = new ByteArrayOutputStream();
        try (LittleEndianOutputStream out = new LittleEndianOutputStream(index)) {
            IndexFactory.createDynamicIndex(localVcf, codec).write(out);
        }
        try (LocalHttpServer server = new LocalHttpServer()
                        .addFile("/picard/testdata/ex2.vcf", localVcf)
                        .addFile("/picard/testdata/ex2.vcf.idx", index.toByteArray());
                AbstractFeatureReader<VariantContext, LineIterator> featureReaderHttp =
                        AbstractFeatureReader.getFeatureReader(
                                server.url("/picard/testdata/ex2.vcf").toString(), codec, true); // Require an index to
                AbstractFeatureReader<VariantContext, LineIterator> featureReaderLocal =
                        AbstractFeatureReader.getFeatureReader(LOCAL_MIRROR_HTTP_INDEXED_VCF_PATH, codec, false)) {
            final CloseableTribbleIterator<VariantContext> localIterator = featureReaderLocal.iterator();
            for (final Feature feat : featureReaderHttp.iterator()) {
                assertEquals(feat.toString(), localIterator.next().toString());
            }
            assertFalse(localIterator.hasNext());
        }
    }

    @Test
    public void testLoadBEDFTP() throws Exception {
        final String bed = "chr1\t100\t200\tcpg1\nchr1\t300\t450\tcpg2\nchr2\t50\t60\tcpg3\n";
        try (LocalFtpServer server = new LocalFtpServer()
                .addFile("/igv/cpgIslands with spaces.hg18.bed", bed.getBytes(StandardCharsets.US_ASCII))) {
            final String path =
                    server.url("/igv/cpgIslands with spaces.hg18.bed").toString();
            try (AbstractFeatureReader<BEDFeature, LineIterator> bfs =
                    AbstractFeatureReader.getFeatureReader(path, new BEDCodec(), false)) {
                final List<String> names = new ArrayList<>();
                for (final BEDFeature feat : bfs.iterator()) {
                    names.add(feat.getContig() + ":" + feat.getStart() + "-" + feat.getEnd() + " " + feat.getName());
                }
                assertEquals(names, List.of("chr1:101-200 cpg1", "chr1:301-450 cpg2", "chr2:51-60 cpg3"));
            }
        }
    }

    @Test(dataProvider = "blockCompressedExtensionExtensionStrings", dataProviderClass = IOUtilTest.class)
    public void testBlockCompressionExtensionString(final String testString, final boolean expected) {
        Assert.assertEquals(AbstractFeatureReader.hasBlockCompressedExtension(testString), expected);
    }

    @Test(dataProvider = "blockCompressedExtensionExtensionStrings", dataProviderClass = IOUtilTest.class)
    public void testBlockCompressionExtensionFile(final String testString, final boolean expected) {
        Assert.assertEquals(AbstractFeatureReader.hasBlockCompressedExtension(Path.of(testString)), expected);
    }

    @Test(dataProvider = "blockCompressedExtensionExtensionURIStrings", dataProviderClass = IOUtilTest.class)
    public void testBlockCompressionExtension(final String testURIString, final boolean expected) {
        URI testURI = URI.create(testURIString);
        Assert.assertEquals(AbstractFeatureReader.hasBlockCompressedExtension(testURI), expected);
    }

    @Test(dataProvider = "blockCompressedExtensionExtensionURIStrings", dataProviderClass = IOUtilTest.class)
    public void testBlockCompressionExtensionStringVersion(final String testURIString, final boolean expected) {
        Assert.assertEquals(AbstractFeatureReader.hasBlockCompressedExtension(testURIString), expected);
    }

    @Test
    public void testVCF4_4() {
        final AbstractFeatureReader<VariantContext, ?> fr = AbstractFeatureReader.getFeatureReader(
                Paths.get("src/test/resources/htsjdk/variant/", "VCF4_4HeaderTest.vcf")
                        .toString(),
                new VCFCodec(),
                false);
        final VCFHeader vcfHeader = (VCFHeader) fr.getHeader();
        Assert.assertEquals(vcfHeader.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_4);
    }

    @DataProvider(name = "vcfFileAndWrapperCombinations")
    private static Object[][] vcfFileAndWrapperCombinations() {
        return new Object[][] {
            {VCF, VCF_INDEX, null, null},
            {MANGLED_VCF, MANGLED_VCF_INDEX, WRAPPER, WRAPPER},
            {VCF, MANGLED_VCF_INDEX, null, WRAPPER},
            {MANGLED_VCF, VCF_INDEX, WRAPPER, null},
            {MANGLED_VCF_TABIX_BLOCK_GZIPPED, MANGLED_VCF_TABIX_INDEX, WRAPPER, WRAPPER},
            {VCF_TABIX_BLOCK_GZIPPED, MANGLED_VCF_TABIX_INDEX, null, WRAPPER},
            {MANGLED_VCF_TABIX_BLOCK_GZIPPED, VCF_TABIX_INDEX, WRAPPER, null},
            {VCF_TABIX_BLOCK_GZIPPED, VCF_TABIX_INDEX, null, null},
        };
    }

    @Test(dataProvider = "vcfFileAndWrapperCombinations")
    public void testGetFeatureReaderWithPathAndWrappers(
            String file,
            String index,
            Function<SeekableByteChannel, SeekableByteChannel> wrapper,
            Function<SeekableByteChannel, SeekableByteChannel> indexWrapper)
            throws IOException {
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
                final AbstractFeatureReader<VariantContext, ?> featureReader =
                        getFeatureReader(file, index, wrapper, indexWrapper, new VCFCodec(), fs)) {
            Assert.assertTrue(featureReader.hasIndex());
            Assert.assertEquals(featureReader.iterator().toList().size(), 26);
            Assert.assertEquals(featureReader.query("1", 190, 210).toList().size(), 3);
            Assert.assertEquals(featureReader.query("2", 190, 210).toList().size(), 1);
        }
    }

    @DataProvider(name = "failsWithoutWrappers")
    public static Object[][] failsWithoutWrappers() {
        return new Object[][] {
            {MANGLED_VCF, MANGLED_VCF_INDEX},
            {VCF, CORRUPTED_VCF_INDEX},
            {VCF, MANGLED_VCF_INDEX},
            {MANGLED_VCF, VCF_INDEX},
            {MANGLED_VCF_TABIX_BLOCK_GZIPPED, MANGLED_VCF_TABIX_INDEX},
            {VCF_TABIX_BLOCK_GZIPPED, MANGLED_VCF_TABIX_INDEX},
            {MANGLED_VCF_TABIX_BLOCK_GZIPPED, VCF_TABIX_INDEX},
        };
    }

    @Test(
            dataProvider = "failsWithoutWrappers",
            expectedExceptions = {TribbleException.class, FileTruncatedException.class})
    public void testFailureIfNoWrapper(String file, String index) throws IOException {
        try (final FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
                final FeatureReader<?> reader = getFeatureReader(file, index, null, null, new VCFCodec(), fs)) {
            // should have exploded by now
        }
    }

    private static <T extends Feature> AbstractFeatureReader<T, ?> getFeatureReader(
            String vcf,
            String index,
            Function<SeekableByteChannel, SeekableByteChannel> wrapper,
            Function<SeekableByteChannel, SeekableByteChannel> indexWrapper,
            FeatureCodec<T, ?> codec,
            FileSystem fileSystem)
            throws IOException {
        final Path vcfInJimfs = TestUtils.getTribbleFileInJimfs(vcf, index, fileSystem);
        return AbstractFeatureReader.getFeatureReader(
                vcfInJimfs.toUri().toString(), null, codec, true, wrapper, indexWrapper);
    }

    /**
     * skip the first byte of a SeekableByteChannel
     */
    private static class SkippingByteChannel implements SeekableByteChannel {
        private final int toSkip;
        private final SeekableByteChannel input;

        private SkippingByteChannel(SeekableByteChannel input) {
            this.toSkip = 1;
            try {
                this.input = input;
                input.position(toSkip);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean isOpen() {
            return input.isOpen();
        }

        @Override
        public void close() throws IOException {
            input.close();
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            return input.read(dst);
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            throw new UnsupportedOperationException("Read only");
        }

        @Override
        public long position() throws IOException {
            return input.position() - toSkip;
        }

        @Override
        public SeekableByteChannel position(long newPosition) throws IOException {
            if (newPosition < 0) {
                throw new RuntimeException("negative position not allowed");
            }
            return input.position(newPosition + toSkip);
        }

        @Override
        public long size() throws IOException {
            return input.size() - toSkip;
        }

        @Override
        public SeekableByteChannel truncate(long size) throws IOException {
            return input.truncate(size + toSkip);
        }
    }

    @DataProvider
    public Object[][] getVcfRedirects() {
        return new Object[][] {
            {VCFRedirectCodec.REDIRECTING_CODEC_TEST_FILE_ROOT + "vcf.redirect"},
            {VCFRedirectCodec.REDIRECTING_CODEC_TEST_FILE_ROOT + "vcf.gz.redirect"}
        };
    }

    /**
     * Test a codec that uses {@link FeatureCodec#getPathToDataFile(String)} in order to specify a data file that's
     * different than the file it identifies with {@link FeatureCodec#canDecode}).
     */
    @Test(dataProvider = "getVcfRedirects")
    public void testCodecWithGetPathToDataFile(String vcfRedirect) throws IOException {
        final VCFRedirectCodec vcfRedirectCodec = new VCFRedirectCodec();
        final String vcf = VCFRedirectCodec.REDIRECTING_CODEC_TEST_FILE_ROOT + "dataFiles/test.vcf";
        Assert.assertTrue(vcfRedirectCodec.canDecode(vcfRedirect), "should have been able to decode " + vcfRedirect);
        try (FeatureReader<VariantContext> redirectReader =
                        AbstractFeatureReader.getFeatureReader(vcfRedirect, vcfRedirectCodec, false);
                FeatureReader<VariantContext> directReader =
                        AbstractFeatureReader.getFeatureReader(vcf, new VCFCodec(), false)) {
            Assert.assertEquals(
                    redirectReader.getHeader().toString(),
                    directReader.getHeader().toString());
            final int redirectVcfSize = redirectReader.iterator().toList().size();
            Assert.assertTrue(redirectVcfSize > 0, "iterator found " + redirectVcfSize + " records");
            Assert.assertEquals(
                    redirectVcfSize, directReader.iterator().toList().size());

            final int redirectQuerySize =
                    redirectReader.query("20", 1, 20000).toList().size();
            Assert.assertTrue(redirectQuerySize > 0, "query found " + redirectVcfSize + " records");
            Assert.assertEquals(
                    redirectQuerySize,
                    directReader.query("20", 1, 20000).toList().size());
        }
    }

    // ============================================================
    // BCF: BGZF with a CSI, raw with a Tribble index, and indexes of the wrong kind
    // ============================================================

    private Path bcfTempDir;

    @BeforeClass(alwaysRun = true)
    private void createBcfTempDir() {
        bcfTempDir = TestUtil.getTempDirectoryAsPath("AbstractFeatureReaderTest", "bcf");
    }

    @AfterClass(alwaysRun = true)
    private void deleteBcfTempDir() {
        TestUtil.recursiveDelete(bcfTempDir);
    }

    /** A new empty directory for one test's files. */
    private Path newBcfTestDir() throws IOException {
        return Files.createTempDirectory(bcfTempDir, "test");
    }

    /** A header with two contigs and no samples. */
    private static VCFHeader bcfTestHeader() {
        final VCFHeader header = new VCFHeader(new LinkedHashSet<>(), List.of());
        header.setSequenceDictionary(new SAMSequenceDictionary(
                List.of(new SAMSequenceRecord("chr1", 100_000), new SAMSequenceRecord("chr2", 50_000))));
        return header;
    }

    /**
     * Writes records at chr1:100, chr1:300 and chr2:200 to {@code path} as {@code type}, with the index the writer
     * makes on the fly if {@code index}.
     *
     * @param bcfVersion the BCF version to write, or null for the writer's default (BGZF BCF 2.2 with a CSI)
     */
    private static Path writeVariants(
            final Path path,
            final VariantContextWriterBuilder.OutputType type,
            final BCFVersion bcfVersion,
            final boolean index)
            throws IOException {
        final VCFHeader header = bcfTestHeader();
        final VariantContextWriterBuilder builder = new VariantContextWriterBuilder()
                .setOutputPath(path)
                .setOutputFileType(type)
                .setReferenceDictionary(header.getSequenceDictionary())
                .setBCFVersion(bcfVersion);
        if (index) {
            builder.setOption(Options.INDEX_ON_THE_FLY);
        } else {
            builder.unsetOption(Options.INDEX_ON_THE_FLY);
        }
        try (VariantContextWriter writer = builder.build()) {
            writer.writeHeader(header);
            for (final String contigAndStart : List.of("chr1:100", "chr1:300", "chr2:200")) {
                final String[] parts = contigAndStart.split(":");
                final int start = Integer.parseInt(parts[1]);
                writer.add(
                        new VariantContextBuilder("test", parts[0], start, start, List.of(Allele.REF_A, Allele.ALT_C))
                                .make());
            }
        }
        return path;
    }

    /** Writes the records of {@link #writeVariants} as BGZF BCF 2.2, with a CSI beside it if {@code index}. */
    private static Path writeBgzfBcf(final Path path, final boolean index) throws IOException {
        return writeVariants(path, VariantContextWriterBuilder.OutputType.BCF, null, index);
    }

    /** Writes the records of {@link #writeVariants} as raw BCF 2.1, with a Tribble index beside it if {@code index}. */
    private static Path writeRawBcf(final Path path, final boolean index) throws IOException {
        return writeVariants(path, VariantContextWriterBuilder.OutputType.BCF, BCFVersion.BCF_2_1, index);
    }

    /** Writes the records of {@link #writeVariants} as a bgzipped VCF in {@code dir} and returns its TBI index. */
    private static Path writeTbi(final Path dir) throws IOException {
        final Path vcf = writeVariants(
                dir.resolve("for-tbi.vcf.gz"), VariantContextWriterBuilder.OutputType.BLOCK_COMPRESSED_VCF, null, true);
        return Path.of(vcf + FileExtensions.TABIX_INDEX);
    }

    /** Writes a BGZF BCF with a CSI in {@code dir} and returns the CSI. */
    private static Path writeCsi(final Path dir) throws IOException {
        return Path.of(writeBgzfBcf(dir.resolve("for-csi.bcf"), true) + FileExtensions.CSI);
    }

    /** Writes a raw BCF with a Tribble index in {@code dir} and returns the index. */
    private static Path writeTribbleIndex(final Path dir) throws IOException {
        return Path.of(writeRawBcf(dir.resolve("for-idx.bcf"), true) + FileExtensions.TRIBBLE_INDEX);
    }

    private static AbstractFeatureReader<VariantContext, ?> openBcf(final Path bcf, final boolean requireIndex) {
        return AbstractFeatureReader.getFeatureReader(bcf.toUri().toString(), new BCF2Codec(), requireIndex);
    }

    private static AbstractFeatureReader<VariantContext, ?> openBcf(
            final Path bcf, final Path index, final boolean requireIndex) {
        return AbstractFeatureReader.getFeatureReader(
                bcf.toUri().toString(), index.toUri().toString(), new BCF2Codec(), requireIndex);
    }

    /** @return the start of each record {@code iterator} returns, having closed it */
    private static List<Integer> starts(final CloseableTribbleIterator<VariantContext> iterator) {
        try (iterator) {
            return iterator.stream().map(VariantContext::getStart).toList();
        }
    }

    /** Asserts that {@code reader} answers queries on both contigs with the records written there. */
    private static void assertQueriesFindTheRecords(final FeatureReader<VariantContext> reader) throws IOException {
        Assert.assertTrue(reader.isQueryable());
        Assert.assertEquals(starts(reader.query("chr1", 50, 150)), List.of(100));
        Assert.assertEquals(starts(reader.query("chr1", 1, 1000)), List.of(100, 300));
        Assert.assertEquals(starts(reader.query("chr2", 1, 1000)), List.of(200));
    }

    @Test
    public void anIndexedBcfWrittenByHtsjdkIsQueryableWithoutRequiringAnIndex() throws IOException {
        final Path bcf = writeBgzfBcf(newBcfTestDir().resolve("x.bcf"), true);
        try (AbstractFeatureReader<VariantContext, ?> reader = openBcf(bcf, false)) {
            assertQueriesFindTheRecords(reader);
        }
    }

    @Test
    public void anIndexedBcfWrittenByHtsjdkIsQueryableWhenAnIndexIsRequired() throws IOException {
        final Path bcf = writeBgzfBcf(newBcfTestDir().resolve("x.bcf"), true);
        try (AbstractFeatureReader<VariantContext, ?> reader = openBcf(bcf, true)) {
            assertQueriesFindTheRecords(reader);
        }
    }

    @Test
    public void aReaderOfAnIndexedBcfSaysItHasAnIndex() throws IOException {
        final Path bcf = writeBgzfBcf(newBcfTestDir().resolve("x.bcf"), true);
        // callers that hold a FeatureReader cast it back to ask whether it has an index
        try (FeatureReader<VariantContext> reader = openBcf(bcf, false)) {
            Assert.assertTrue(((AbstractFeatureReader<?, ?>) reader).hasIndex());
        }
    }

    @Test
    public void aBgzfBcfNamedBcfGzWithACsiBesideItIsQueryable() throws IOException {
        final Path written = writeBgzfBcf(newBcfTestDir().resolve("x.bcf"), true);
        final Path bcf = Files.move(written, written.resolveSibling("x.bcf.gz"));
        Files.move(Path.of(written + FileExtensions.CSI), Path.of(bcf + FileExtensions.CSI));
        try (AbstractFeatureReader<VariantContext, ?> reader = openBcf(bcf, true)) {
            assertQueriesFindTheRecords(reader);
        }
    }

    @Test
    public void anExplicitCsiWithAnUnrelatedNameIsUsed() throws IOException {
        final Path bcf = writeBgzfBcf(newBcfTestDir().resolve("x.bcf"), true);
        final Path index = Files.move(Path.of(bcf + FileExtensions.CSI), bcf.resolveSibling("index.bin"));
        try (AbstractFeatureReader<VariantContext, ?> reader = openBcf(bcf, index, true)) {
            assertQueriesFindTheRecords(reader);
        }
    }

    @Test
    public void aCsiNamedWithoutTheBcfExtensionIsFound() throws IOException {
        final Path bcf = writeBgzfBcf(newBcfTestDir().resolve("x.bcf"), true);
        Files.move(Path.of(bcf + FileExtensions.CSI), bcf.resolveSibling("x" + FileExtensions.CSI));
        try (AbstractFeatureReader<VariantContext, ?> reader = openBcf(bcf, true)) {
            assertQueriesFindTheRecords(reader);
        }
    }

    @Test
    public void aBgzfBcfWithoutACsiIsReadWholeButIsNotQueryable() throws IOException {
        final Path bcf = writeBgzfBcf(newBcfTestDir().resolve("x.bcf"), false);
        try (AbstractFeatureReader<VariantContext, ?> reader = openBcf(bcf, false)) {
            Assert.assertFalse(reader.hasIndex());
            Assert.assertFalse(reader.isQueryable());
            Assert.assertEquals(starts(reader.iterator()), List.of(100, 300, 200));
        }
    }

    @Test
    public void aBgzfBcfWithoutACsiIsRefusedWhenAnIndexIsRequired() throws IOException {
        final Path bcf = writeBgzfBcf(newBcfTestDir().resolve("x.bcf"), false);
        final TribbleException e = Assert.expectThrows(TribbleException.class, () -> openBcf(bcf, true));
        Assert.assertTrue(e.getMessage().contains("x.bcf.csi"), e.getMessage());
        Assert.assertTrue(e.getMessage().contains("bcftools index"), e.getMessage());
    }

    @Test
    public void aRawBcfWithATribbleIndexIsQueryable() throws IOException {
        final Path bcf = writeRawBcf(newBcfTestDir().resolve("x.bcf"), true);
        try (AbstractFeatureReader<VariantContext, ?> reader = openBcf(bcf, true)) {
            Assert.assertTrue(
                    reader instanceof TribbleIndexedFeatureReader,
                    reader.getClass().getName());
            assertQueriesFindTheRecords(reader);
        }
    }

    @Test
    public void aRawBcfWithAGzippedTribbleIndexIsQueryable() throws IOException {
        final Path bcf = writeRawBcf(newBcfTestDir().resolve("x.bcf"), true);
        final Path index = Path.of(bcf + FileExtensions.TRIBBLE_INDEX);
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(Path.of(index + ".gz")))) {
            Files.copy(index, out);
        }
        Files.delete(index);
        try (AbstractFeatureReader<VariantContext, ?> reader = openBcf(bcf, true)) {
            assertQueriesFindTheRecords(reader);
        }
    }

    @Test
    public void anExplicitTbiForABcfIsRefused() throws IOException {
        final Path dir = newBcfTestDir();
        final Path bcf = writeBgzfBcf(dir.resolve("x.bcf"), false);
        final Path tbi = writeTbi(dir);
        final TribbleException e = Assert.expectThrows(TribbleException.class, () -> openBcf(bcf, tbi, false));
        Assert.assertTrue(e.getMessage().contains(tbi.getFileName().toString()), e.getMessage());
        Assert.assertTrue(e.getMessage().contains("TBI"), e.getMessage());
        Assert.assertTrue(e.getMessage().contains("CSI"), e.getMessage());
    }

    @Test
    public void anExplicitCsiForARawBcfIsRefused() throws IOException {
        final Path dir = newBcfTestDir();
        final Path bcf = writeRawBcf(dir.resolve("x.bcf"), false);
        final Path csi = writeCsi(dir);
        final TribbleException e = Assert.expectThrows(TribbleException.class, () -> openBcf(bcf, csi, false));
        Assert.assertTrue(e.getMessage().contains(csi.getFileName().toString()), e.getMessage());
        Assert.assertTrue(e.getMessage().contains("CSI"), e.getMessage());
        Assert.assertTrue(e.getMessage().contains("BGZF"), e.getMessage());
    }

    @Test
    public void anExplicitTribbleIndexForABgzfBcfIsRefused() throws IOException {
        final Path dir = newBcfTestDir();
        final Path bcf = writeBgzfBcf(dir.resolve("x.bcf"), false);
        final Path idx = writeTribbleIndex(dir);
        final TribbleException e = Assert.expectThrows(TribbleException.class, () -> openBcf(bcf, idx, false));
        Assert.assertTrue(e.getMessage().contains(idx.getFileName().toString()), e.getMessage());
        Assert.assertTrue(e.getMessage().contains("Tribble"), e.getMessage());
        Assert.assertTrue(e.getMessage().contains("CSI"), e.getMessage());
    }

    @Test
    public void aTribbleIndexBesideABgzfBcfIsIgnoredWithOneWarning() throws Exception {
        final Path dir = newBcfTestDir();
        final Path bcf = writeBgzfBcf(dir.resolve("tribble-beside-bgzf.bcf"), false);
        Files.copy(writeTribbleIndex(dir), Path.of(bcf + FileExtensions.TRIBBLE_INDEX));
        final List<String> warnings =
                LogCapture.linesLoggedContaining("tribble-beside-bgzf.bcf", Log.LogLevel.WARNING, () -> {
                    try (AbstractFeatureReader<VariantContext, ?> reader = openBcf(bcf, false)) {
                        Assert.assertFalse(reader.isQueryable());
                        Assert.assertEquals(starts(reader.iterator()), List.of(100, 300, 200));
                    }
                });
        Assert.assertEquals(warnings.size(), 1, warnings.toString());
        Assert.assertTrue(warnings.get(0).contains("Tribble"), warnings.get(0));
    }

    @Test
    public void aTribbleIndexBesideABgzfBcfIsRefusedWhenAnIndexIsRequired() throws IOException {
        final Path dir = newBcfTestDir();
        final Path bcf = writeBgzfBcf(dir.resolve("x.bcf"), false);
        Files.copy(writeTribbleIndex(dir), Path.of(bcf + FileExtensions.TRIBBLE_INDEX));
        final TribbleException e = Assert.expectThrows(TribbleException.class, () -> openBcf(bcf, true));
        Assert.assertTrue(e.getMessage().contains("x.bcf.idx"), e.getMessage());
        Assert.assertTrue(e.getMessage().contains("Tribble"), e.getMessage());
    }

    @Test
    public void aTbiBesideABgzfBcfIsRefusedWhenAnIndexIsRequired() throws IOException {
        final Path dir = newBcfTestDir();
        final Path bcf = writeBgzfBcf(dir.resolve("x.bcf"), false);
        Files.copy(writeTbi(dir), Path.of(bcf + FileExtensions.TABIX_INDEX));
        final TribbleException e = Assert.expectThrows(TribbleException.class, () -> openBcf(bcf, true));
        Assert.assertTrue(e.getMessage().contains("x.bcf.tbi"), e.getMessage());
        Assert.assertTrue(e.getMessage().contains("TBI"), e.getMessage());
    }

    @Test
    public void aCsiBesideARawBcfIsIgnoredWithOneWarning() throws Exception {
        final Path dir = newBcfTestDir();
        final Path bcf = writeRawBcf(dir.resolve("csi-beside-raw.bcf"), false);
        Files.copy(writeCsi(dir), Path.of(bcf + FileExtensions.CSI));
        final List<String> warnings =
                LogCapture.linesLoggedContaining("csi-beside-raw.bcf", Log.LogLevel.WARNING, () -> {
                    try (AbstractFeatureReader<VariantContext, ?> reader = openBcf(bcf, false)) {
                        Assert.assertFalse(reader.isQueryable());
                        Assert.assertEquals(starts(reader.iterator()), List.of(100, 300, 200));
                    }
                });
        Assert.assertEquals(warnings.size(), 1, warnings.toString());
        Assert.assertTrue(warnings.get(0).contains("CSI"), warnings.get(0));
    }

    @Test
    public void anEmptyFileNamedAsTheCsiBesideABgzfBcfIsIgnoredWithOneWarning() throws Exception {
        final Path dir = newBcfTestDir();
        final Path bcf = writeBgzfBcf(dir.resolve("empty-csi.bcf"), false);
        Files.createFile(Path.of(bcf + FileExtensions.CSI));
        final List<String> warnings = LogCapture.linesLoggedContaining("empty-csi.bcf", Log.LogLevel.WARNING, () -> {
            try (AbstractFeatureReader<VariantContext, ?> reader = openBcf(bcf, false)) {
                Assert.assertFalse(reader.isQueryable());
                Assert.assertEquals(starts(reader.iterator()), List.of(100, 300, 200));
            }
        });
        Assert.assertEquals(warnings.size(), 1, warnings.toString());
        Assert.assertTrue(warnings.get(0).contains("not an index"), warnings.get(0));
    }

    @Test
    public void anEmptyFileNamedAsTheCsiBesideABgzfBcfIsRefusedWhenAnIndexIsRequired() throws IOException {
        final Path dir = newBcfTestDir();
        final Path bcf = writeBgzfBcf(dir.resolve("x.bcf"), false);
        Files.createFile(Path.of(bcf + FileExtensions.CSI));
        final TribbleException e = Assert.expectThrows(TribbleException.class, () -> openBcf(bcf, true));
        Assert.assertTrue(e.getMessage().contains("x.bcf.csi"), e.getMessage());
    }

    @Test
    public void aTbiNamedAsTheCsiBesideABgzfBcfIsIgnoredWithOneWarning() throws Exception {
        final Path dir = newBcfTestDir();
        final Path bcf = writeBgzfBcf(dir.resolve("tbi-as-csi.bcf"), false);
        Files.copy(writeTbi(dir), Path.of(bcf + FileExtensions.CSI));
        final List<String> warnings = LogCapture.linesLoggedContaining("tbi-as-csi.bcf", Log.LogLevel.WARNING, () -> {
            try (AbstractFeatureReader<VariantContext, ?> reader = openBcf(bcf, false)) {
                Assert.assertFalse(reader.isQueryable());
            }
        });
        Assert.assertEquals(warnings.size(), 1, warnings.toString());
        Assert.assertTrue(warnings.get(0).contains("TBI"), warnings.get(0));
    }

    @Test
    public void aBgzfVcfNamedBcfIsRefusedAsNotABcf() throws IOException {
        final Path dir = newBcfTestDir();
        final Path notABcf = writeVariants(
                dir.resolve("vcf-named.bcf"), VariantContextWriterBuilder.OutputType.BLOCK_COMPRESSED_VCF, null, false);
        final TribbleException e = Assert.expectThrows(TribbleException.class, () -> openBcf(notABcf, false));
        Assert.assertTrue(e.getMessage().contains("BCF magic"), e.getMessage());
    }

    @Test
    public void aCsiBesideARawBcfIsRefusedWhenAnIndexIsRequired() throws IOException {
        final Path dir = newBcfTestDir();
        final Path bcf = writeRawBcf(dir.resolve("x.bcf"), false);
        Files.copy(writeCsi(dir), Path.of(bcf + FileExtensions.CSI));
        final TribbleException e = Assert.expectThrows(TribbleException.class, () -> openBcf(bcf, true));
        Assert.assertTrue(e.getMessage().contains("x.bcf.csi"), e.getMessage());
        Assert.assertTrue(e.getMessage().contains("BGZF"), e.getMessage());
    }

    @Test
    public void aCsiBesideABgzfBcfIsUsedOverAStrayTribbleIndexWithoutAWarning() throws Exception {
        final Path dir = newBcfTestDir();
        final Path bcf = writeBgzfBcf(dir.resolve("csi-and-tribble.bcf"), true);
        Files.copy(writeTribbleIndex(dir), Path.of(bcf + FileExtensions.TRIBBLE_INDEX));
        final List<String> warnings =
                LogCapture.linesLoggedContaining("csi-and-tribble.bcf", Log.LogLevel.WARNING, () -> {
                    try (AbstractFeatureReader<VariantContext, ?> reader = openBcf(bcf, true)) {
                        assertQueriesFindTheRecords(reader);
                    }
                });
        Assert.assertEquals(warnings, List.of());
    }

    @Test
    public void aBgzfBcfWithACsiOnAnotherFileSystemIsQueryable() throws IOException {
        final Path written = writeBgzfBcf(newBcfTestDir().resolve("x.bcf"), true);
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path bcf = Files.copy(written, fs.getPath("/x.bcf"));
            Files.copy(Path.of(written + FileExtensions.CSI), fs.getPath("/x.bcf" + FileExtensions.CSI));
            try (AbstractFeatureReader<VariantContext, ?> reader = openBcf(bcf, true)) {
                assertQueriesFindTheRecords(reader);
            }
        }
    }

    @Test
    public void aBgzfBcfWithACsiOnAnotherFileSystemIsReadThroughTheWrappers() throws IOException {
        final Path written = writeBgzfBcf(newBcfTestDir().resolve("x.bcf"), true);
        final RecordingChannelWrapper dataChannels = new RecordingChannelWrapper();
        final RecordingChannelWrapper indexChannels = new RecordingChannelWrapper();
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path bcf = Files.copy(written, fs.getPath("/x.bcf"));
            Files.copy(Path.of(written + FileExtensions.CSI), fs.getPath("/x.bcf" + FileExtensions.CSI));
            try (AbstractFeatureReader<VariantContext, ?> reader = AbstractFeatureReader.getFeatureReader(
                    bcf.toUri().toString(), null, new BCF2Codec(), true, dataChannels, indexChannels)) {
                assertQueriesFindTheRecords(reader);
            }
            Assert.assertTrue(dataChannels.openedCount() > 0);
            Assert.assertTrue(indexChannels.openedCount() > 0);
            // checked before the file system is closed, as that closes every channel open on it
            Assert.assertEquals(dataChannels.stillOpenCount(), 0);
            Assert.assertEquals(indexChannels.stillOpenCount(), 0);
        }
    }

    @Test
    public void aBgzfBcfWithACsiOverHttpIsQueryable() throws IOException {
        final Path bcf = writeBgzfBcf(newBcfTestDir().resolve("x.bcf"), true);
        try (LocalHttpServer server = new LocalHttpServer()
                .addFile("/data/x.bcf", bcf)
                .addFile("/data/x.bcf" + FileExtensions.CSI, Path.of(bcf + FileExtensions.CSI))) {
            try (AbstractFeatureReader<VariantContext, ?> reader = AbstractFeatureReader.getFeatureReader(
                    server.url("/data/x.bcf").toString(), new BCF2Codec(), true)) {
                assertQueriesFindTheRecords(reader);
            }
        }
    }
}
