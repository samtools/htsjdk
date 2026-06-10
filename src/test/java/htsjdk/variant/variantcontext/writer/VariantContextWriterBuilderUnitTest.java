/*
 * Copyright (c) 2014 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package htsjdk.variant.variantcontext.writer;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.samtools.Defaults;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.tribble.Tribble;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder.OutputType;
import htsjdk.variant.vcf.VCFHeader;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class VariantContextWriterBuilderUnitTest extends VariantBaseTest {
    private static final String TEST_BASENAME = "htsjdk-test VariantContextWriterBuilderUnitTest";
    private SAMSequenceDictionary dictionary;

    private Path vcf;
    private Path vcfIdx;
    private Path vcfMD5;
    private Path bcf;
    private Path bcfIdx;
    private Path unknown;

    private List<Path> blockCompressedVCFs;
    private List<Path> blockCompressedIndices;

    @BeforeSuite
    public void before() throws IOException {
        dictionary = createArtificialSequenceDictionary();
        vcf = Files.createTempFile(TEST_BASENAME, FileExtensions.VCF);
        vcf.toFile().deleteOnExit();
        vcfIdx = Tribble.indexPath(vcf);
        vcfIdx.toFile().deleteOnExit();
        vcfMD5 = Path.of(vcf.toAbsolutePath() + ".md5");
        vcfMD5.toFile().deleteOnExit();
        bcf = Files.createTempFile(TEST_BASENAME, FileExtensions.BCF);
        bcf.toFile().deleteOnExit();
        bcfIdx = Tribble.indexPath(bcf);
        bcfIdx.toFile().deleteOnExit();
        unknown = Files.createTempFile(TEST_BASENAME, ".unknown");
        unknown.toFile().deleteOnExit();

        blockCompressedVCFs = new ArrayList<Path>();
        blockCompressedIndices = new ArrayList<Path>();
        for (final String extension : FileExtensions.BLOCK_COMPRESSED) {
            final Path blockCompressed = Files.createTempFile(TEST_BASENAME, FileExtensions.VCF + extension);
            blockCompressed.toFile().deleteOnExit();
            blockCompressedVCFs.add(blockCompressed);

            final Path index = Path.of(blockCompressed.toAbsolutePath() + FileExtensions.TABIX_INDEX);
            index.toFile().deleteOnExit();
            blockCompressedIndices.add(index);
        }
    }

    @Test
    public void testSetOutputFile() throws IOException {
        final VariantContextWriterBuilder builder =
                new VariantContextWriterBuilder().setReferenceDictionary(dictionary);

        VariantContextWriter writer =
                builder.setOutputFile(vcf.toAbsolutePath().toString()).build();
        Assert.assertTrue(writer instanceof VCFWriter, "testSetOutputFile VCF String");
        Assert.assertFalse(
                ((VCFWriter) writer).getOutputStream() instanceof BlockCompressedOutputStream,
                "testSetOutputFile VCF String was compressed");

        writer = builder.setOutputPath(vcf).build();
        Assert.assertTrue(writer instanceof VCFWriter, "testSetOutputFile VCF Path");
        Assert.assertFalse(
                ((VCFWriter) writer).getOutputStream() instanceof BlockCompressedOutputStream,
                "testSetOutputFile VCF Path was compressed");

        for (final String extension : FileExtensions.BLOCK_COMPRESSED) {
            final Path file = Files.createTempFile(TEST_BASENAME + ".setoutput", extension);
            file.toFile().deleteOnExit();
            final String filename = file.toAbsolutePath().toString();

            writer = builder.setOutputFile(filename).build();
            Assert.assertTrue(writer instanceof VCFWriter, "testSetOutputFile " + extension + " String");
            Assert.assertTrue(
                    ((VCFWriter) writer).getOutputStream() instanceof BlockCompressedOutputStream,
                    "testSetOutputFile " + extension + " String was not compressed");

            writer = builder.setOutputPath(file).build();
            Assert.assertTrue(writer instanceof VCFWriter, "testSetOutputFile " + extension + " Path");
            Assert.assertTrue(
                    ((VCFWriter) writer).getOutputStream() instanceof BlockCompressedOutputStream,
                    "testSetOutputFile " + extension + " Path was not compressed");
        }

        writer = builder.setOutputPath(bcf).build();
        Assert.assertTrue(writer instanceof BCF2Writer, "testSetOutputFile BCF String");

        writer = builder.setOutputFile(bcf.toAbsolutePath().toString()).build();
        Assert.assertTrue(writer instanceof BCF2Writer, "testSetOutputFile BCF Path");
    }

    @DataProvider
    public Iterator<Object[]> getFilesAndOutputTypes() {
        List<Object[]> cases = new ArrayList<>();
        cases.add(new Object[] {OutputType.VCF, this.vcf});
        cases.add(new Object[] {OutputType.BCF, this.bcf});
        cases.add(new Object[] {OutputType.VCF_STREAM, Path.of("/dev/stdout")});
        for (final Path file : this.blockCompressedVCFs) {
            cases.add(new Object[] {OutputType.BLOCK_COMPRESSED_VCF, file});
        }
        return cases.iterator();
    }

    @Test(dataProvider = "getFilesAndOutputTypes")
    public void testDetermineOutputType(OutputType expected, Path path) {
        Assert.assertEquals(VariantContextWriterBuilder.determineOutputTypeFromFile(path), expected);
    }

    @Test(dataProvider = "getFilesAndOutputTypes")
    public void testDetermineOutputTypeWithSymlink(OutputType expected, Path path) throws IOException {
        final Path link = setUpSymlink(path);
        Assert.assertEquals(expected, VariantContextWriterBuilder.determineOutputTypeFromFile(link));
    }

    @Test(dataProvider = "getFilesAndOutputTypes")
    public void testDetermineOutputTypeOnPath(OutputType expected, Path path) {
        Assert.assertEquals(VariantContextWriterBuilder.determineOutputTypeFromFile(path), expected);
    }

    @Test(dataProvider = "getFilesAndOutputTypes")
    public void testDetermineOutputTypeWithSymlinkOnPath(OutputType expected, Path path) throws IOException {
        final Path link = setUpSymlink(path);
        Assert.assertEquals(expected, VariantContextWriterBuilder.determineOutputTypeFromFile(link));
    }

    private static Path setUpSymlink(Path target) throws IOException {
        final Path link = Files.createTempFile("foo.", ".tmp");
        Files.deleteIfExists(link);
        Files.createSymbolicLink(link, target);
        link.toFile().deleteOnExit();
        return link;
    }

    @Test
    public void testSetOutputFileType() {
        final VariantContextWriterBuilder builder = new VariantContextWriterBuilder()
                .setReferenceDictionary(dictionary)
                .setOutputPath(unknown);

        VariantContextWriter writer = builder.setOutputFileType(VariantContextWriterBuilder.OutputType.VCF)
                .build();
        Assert.assertTrue(writer instanceof VCFWriter, "testSetOutputFileType VCF");
        Assert.assertFalse(
                ((VCFWriter) writer).getOutputStream() instanceof BlockCompressedOutputStream,
                "testSetOutputFileType VCF was compressed");

        writer = builder.setOption(Options.FORCE_BCF).build();
        Assert.assertTrue(writer instanceof BCF2Writer, "testSetOutputFileType FORCE_BCF set -> expected BCF, was VCF");

        // test that FORCE_BCF remains in effect, overriding the explicit setting of VCF
        writer = builder.setOutputFileType(VariantContextWriterBuilder.OutputType.VCF)
                .build();
        Assert.assertTrue(
                writer instanceof BCF2Writer, "testSetOutputFileType FORCE_BCF set 2 -> expected BCF, was VCF");

        writer = builder.unsetOption(Options.FORCE_BCF).build();
        Assert.assertTrue(
                writer instanceof VCFWriter, "testSetOutputFileType FORCE_BCF unset -> expected VCF, was BCF");
        Assert.assertFalse(
                ((VCFWriter) writer).getOutputStream() instanceof BlockCompressedOutputStream,
                "testSetOutputFileType FORCE_BCF unset was compressed");

        writer = builder.setOutputFileType(VariantContextWriterBuilder.OutputType.BLOCK_COMPRESSED_VCF)
                .build();
        Assert.assertTrue(writer instanceof VCFWriter, "testSetOutputFile BLOCK_COMPRESSED_VCF");
        Assert.assertTrue(
                ((VCFWriter) writer).getOutputStream() instanceof BlockCompressedOutputStream,
                "testSetOutputFileType BLOCK_COMPRESSED_VCF was not compressed");

        writer = builder.setOutputFileType(VariantContextWriterBuilder.OutputType.BCF)
                .build();
        Assert.assertTrue(writer instanceof BCF2Writer, "testSetOutputFileType BCF");
    }

    @Test
    public void testSetOutputStream() {
        final OutputStream stream = new ByteArrayOutputStream();

        final VariantContextWriterBuilder builder = new VariantContextWriterBuilder()
                .setReferenceDictionary(dictionary)
                .unsetOption(Options.INDEX_ON_THE_FLY)
                .setOutputStream(stream);

        VariantContextWriter writer = builder.build();
        Assert.assertTrue(writer instanceof VCFWriter, "testSetOutputStream default");

        writer = builder.setOption(Options.FORCE_BCF).build();
        Assert.assertTrue(
                writer instanceof BCF2Writer,
                "testSetOutputStream FORCE_BCF set -> expected BCF stream, was VCF stream");

        // test that FORCE_BCF remains in effect, overriding the explicit setting of VCF
        writer = builder.setOutputVCFStream(stream).build();
        Assert.assertTrue(
                writer instanceof BCF2Writer,
                "testSetOutputStream FORCE_BCF set 2 -> expected BCF stream, was VCF stream");

        writer = builder.unsetOption(Options.FORCE_BCF).build();
        Assert.assertTrue(
                writer instanceof VCFWriter,
                "testSetOutputStream FORCE_BCF unset -> expected VCF stream, was BCF stream");

        writer = builder.setOutputBCFStream(stream).build();
        Assert.assertTrue(writer instanceof BCF2Writer, "testSetOutputStream BCF");

        writer = builder.setOutputVCFStream(stream).build();
        Assert.assertTrue(writer instanceof VCFWriter, "testSetOutputStream VCF");
    }

    @Test
    public void testAsync() {
        final VariantContextWriterBuilder builder = new VariantContextWriterBuilder()
                .setReferenceDictionary(dictionary)
                .setOutputPath(vcf);

        VariantContextWriter writer = builder.build();
        Assert.assertEquals(
                writer instanceof AsyncVariantContextWriter,
                Defaults.USE_ASYNC_IO_WRITE_FOR_TRIBBLE,
                "testAsync default");

        writer = builder.setOption(Options.USE_ASYNC_IO).build();
        Assert.assertTrue(writer instanceof AsyncVariantContextWriter, "testAsync option=set");

        writer = builder.unsetOption(Options.USE_ASYNC_IO).build();
        Assert.assertFalse(writer instanceof AsyncVariantContextWriter, "testAsync option=unset");
    }

    @Test
    public void testBuffering() {
        final VariantContextWriterBuilder builder = new VariantContextWriterBuilder()
                .setReferenceDictionary(dictionary)
                .setOutputPath(vcf)
                .unsetOption(Options.INDEX_ON_THE_FLY); // so the potential BufferedOutputStream is not wrapped in a
        // PositionalOutputStream

        VariantContextWriter writer = builder.build();
        Assert.assertTrue(
                ((VCFWriter) writer).getOutputStream() instanceof BufferedOutputStream,
                "testBuffering was not buffered by default");

        writer = builder.unsetBuffering().build();
        Assert.assertFalse(
                ((VCFWriter) writer).getOutputStream() instanceof BufferedOutputStream,
                "testBuffering was buffered when unset");

        writer = builder.setBuffer(8192).build();
        Assert.assertTrue(
                ((VCFWriter) writer).getOutputStream() instanceof BufferedOutputStream,
                "testBuffering was not buffered when set");
    }

    @Test
    public void testMD5() throws IOException {
        final VariantContextWriterBuilder builder = new VariantContextWriterBuilder()
                .setReferenceDictionary(dictionary)
                .setOutputPath(vcf);

        VariantContextWriter writer = builder.build();
        writer.close();
        Assert.assertEquals(Files.exists(vcfMD5), Defaults.CREATE_MD5, "MD5 default setting not respected");

        Files.deleteIfExists(vcfMD5);

        writer = builder.setCreateMD5().build();
        writer.close();
        Assert.assertTrue(Files.exists(vcfMD5), "MD5 not created when requested");
        Files.delete(vcfMD5);

        writer = builder.unsetCreateMD5().build();
        writer.close();
        Assert.assertFalse(Files.exists(vcfMD5), "MD5 created when not requested");

        writer = builder.setCreateMD5(false).build();
        writer.close();
        Assert.assertFalse(Files.exists(vcfMD5), "MD5 created when not requested via boolean parameter");

        writer = builder.setCreateMD5(true).build();
        writer.close();
        Assert.assertTrue(Files.exists(vcfMD5), "MD5 not created when requested via boolean parameter");
        Files.delete(vcfMD5);

        for (final Path blockCompressed : blockCompressedVCFs) {
            final Path md5 = Path.of(blockCompressed + ".md5");
            Files.deleteIfExists(md5);
            md5.toFile().deleteOnExit();
            writer = builder.setOutputPath(blockCompressed).build();
            writer.close();
            Assert.assertTrue(Files.exists(md5), "MD5 digest not created for " + blockCompressed);
        }
    }

    @Test
    public void testIndexingOnTheFly() throws IOException {
        final VariantContextWriterBuilder builder = new VariantContextWriterBuilder()
                .setReferenceDictionary(dictionary)
                .setOption(Options.INDEX_ON_THE_FLY);

        Files.deleteIfExists(vcfIdx);
        VariantContextWriter writer = builder.setOutputPath(vcf).build();
        writer.close();
        Assert.assertTrue(Files.exists(vcfIdx), String.format("VCF index not created for %s / %s", vcf, vcfIdx));

        Files.deleteIfExists(bcfIdx);
        writer = builder.setOutputPath(bcf).build();
        writer.close();
        Assert.assertTrue(Files.exists(bcfIdx), String.format("BCF index not created for %s / %s", bcf, bcfIdx));

        for (int i = 0; i < blockCompressedVCFs.size(); i++) {
            final Path blockCompressed = blockCompressedVCFs.get(i);
            final Path index = blockCompressedIndices.get(i);
            Files.deleteIfExists(index);
            writer = builder.setOutputPath(blockCompressed)
                    .setReferenceDictionary(dictionary)
                    .build();
            writer.close();
            Assert.assertTrue(
                    Files.exists(index),
                    String.format("Block-compressed index not created for %s / %s", blockCompressed, index));

            // Tabix does not require a reference dictionary.
            // Tribble does: see tests testRefDictRequiredForVCFIndexOnTheFly / testRefDictRequiredForBCFIndexOnTheFly

            Files.delete(index);
            writer = builder.setReferenceDictionary(null).build();
            writer.close();
            Assert.assertTrue(
                    Files.exists(index),
                    String.format("Block-compressed index not created for %s / %s", blockCompressed, index));
        }
    }

    @Test
    public void testIndexingOnTheFlyForPath() throws IOException {
        final VariantContextWriterBuilder builder = new VariantContextWriterBuilder()
                .setReferenceDictionary(dictionary)
                .setOption(Options.INDEX_ON_THE_FLY);

        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path vcfPath = fs.getPath(vcf.getFileName().toString());
            final Path vcfIdxPath = Tribble.indexPath(vcfPath);
            try (final VariantContextWriter writer =
                    builder.setOutputPath(vcfPath).build()) {
                // deliberately empty
            }

            Assert.assertTrue(
                    Files.exists(vcfIdxPath), String.format("VCF index not created for %s / %s", vcfPath, vcfIdxPath));

            final Path bcfPath = fs.getPath(bcf.getFileName().toString());
            final Path bcfIdxPath = Tribble.indexPath(bcfPath);
            try (final VariantContextWriter writer =
                    builder.setOutputPath(bcfPath).build()) {
                // deliberately empty
            }
            Assert.assertTrue(
                    Files.exists(bcfIdxPath), String.format("BCF index not created for %s / %s", bcfPath, bcfIdxPath));

            for (int i = 0; i < blockCompressedVCFs.size(); i++) {
                final Path blockCompressed = blockCompressedVCFs.get(i);
                final Path index = blockCompressedIndices.get(i);

                final Path blockCompressedPath =
                        fs.getPath(blockCompressed.getFileName().toString());
                final Path indexPath = fs.getPath(index.getFileName().toString());

                Assert.assertFalse(Files.exists(indexPath));
                try (VariantContextWriter writer = builder.setOutputPath(blockCompressedPath)
                        .setReferenceDictionary(dictionary)
                        .build()) {
                    // deliberately empty
                }
                Assert.assertTrue(
                        Files.exists(indexPath),
                        String.format(
                                "Block-compressed index not created for %s / %s", blockCompressedPath, indexPath));

                // Tabix does not require a reference dictionary.
                // Tribble does: see tests testRefDictRequiredForVCFIndexOnTheFly /
                // testRefDictRequiredForBCFIndexOnTheFly
                Files.delete(indexPath);
                try (VariantContextWriter writer =
                        builder.setReferenceDictionary(null).build()) {
                    // deliberately empty
                }
                Assert.assertTrue(
                        Files.exists(indexPath),
                        String.format(
                                "Block-compressed index not created for %s / %s", blockCompressedPath, indexPath));
            }
        }
    }

    @Test(singleThreaded = true, groups = "unix")
    public void testWriteToFifo() throws IOException, InterruptedException, ExecutionException {
        long length = testWriteToPipe(this::innerWriteToFifo);
        // length>0 means we wrote to the named pipe, so all is well.
        Assert.assertTrue(length > 0, "VariantContextWriterBuilder did not write to the named pipe as it should.");
    }

    private void innerWriteToFifo(String pathToFifo) {
        // Do not enable INDEX_OF_THE_FLY because that is not compatible with writing to a pipe.
        final VariantContextWriterBuilder builder =
                new VariantContextWriterBuilder().clearOptions().setReferenceDictionary(dictionary);

        Path vcfPath = Paths.get(pathToFifo);
        VariantContextWriter writer = builder.setOutputPath(vcfPath).build();
        writer.writeHeader(new VCFHeader());
        writer.close();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidImplicitFileType() {
        new VariantContextWriterBuilder()
                .setReferenceDictionary(dictionary)
                .setOutputFile("test.bam")
                .build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSetInvalidFileType() {
        new VariantContextWriterBuilder()
                .setReferenceDictionary(dictionary)
                .setOutputFile("test.bam")
                .setOutputFileType(VariantContextWriterBuilder.OutputType.VCF_STREAM)
                .build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidSetFileTypeForStream() {
        new VariantContextWriterBuilder()
                .setReferenceDictionary(dictionary)
                .setOutputStream(new ByteArrayOutputStream())
                .setOutputFileType(VariantContextWriterBuilder.OutputType.VCF)
                .build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRefDictRequiredForVCFIndexOnTheFly() {
        new VariantContextWriterBuilder()
                .setOutputPath(vcf)
                .setOption(Options.INDEX_ON_THE_FLY)
                .build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRefDictRequiredForBCFIndexOnTheFly() {
        new VariantContextWriterBuilder()
                .setOutputPath(bcf)
                .setOption(Options.INDEX_ON_THE_FLY)
                .build();
    }

    @Test
    public void testClearOptions() {
        // Verify that clearOptions doesn't have a side effect of carrying previously set options
        // forward to subsequent builders
        VariantContextWriterBuilder vcwb = new VariantContextWriterBuilder();
        vcwb.clearOptions().setOption(Options.INDEX_ON_THE_FLY);
        final VariantContextWriterBuilder builder = new VariantContextWriterBuilder().clearOptions();
        Assert.assertTrue(builder.options.isEmpty());
    }

    @Test
    public void testModifyOption() {
        final VariantContextWriterBuilder builder = new VariantContextWriterBuilder().clearOptions();
        for (final Options option : Options.values()) {
            Assert.assertFalse(builder.isOptionSet(option)); // shouldn't be set
            builder.modifyOption(option, false);
            Assert.assertFalse(builder.isOptionSet(option)); // still shouldn't be set
            builder.modifyOption(option, true);
            Assert.assertTrue(builder.isOptionSet(option)); // now is set
            builder.modifyOption(option, false);
            Assert.assertFalse(builder.isOptionSet(option)); // has been unset
        }
    }

    @Test
    public void testStdOut() {
        final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOutputFile("/dev/stdout")
                .clearOptions()
                .build();
        OutputStream s = ((VCFWriter) writer).getOutputStream();
        Assert.assertNotNull(((VCFWriter) writer).getOutputStream());
        Assert.assertNotEquals(((VCFWriter) writer).getStreamName(), IndexingVariantContextWriter.DEFAULT_READER_NAME);
    }

    @Test(expectedExceptions = java.util.concurrent.ExecutionException.class)
    public void demonstrateTestWriteToPipeDoesNotHang_1() throws Exception {
        testWriteToPipe((str) -> {
            throw new RuntimeException("oops");
        });
    }

    @Test(expectedExceptions = java.util.concurrent.ExecutionException.class)
    public void demonstrateTestWriteToPipeDoesNotHang_2() throws Exception {
        long x = testWriteToPipe((str) -> {
            try {
                Files.write(Paths.get(str), "hello world".getBytes());
            } catch (IOException ex) {
            }
            ;
            throw new RuntimeException("oops");
        });
    }

    /**
     * Create a named pipe, call "codeThatWritesToAFilename" with the name as argument,
     * consumes the output as it's being run and returns the number of bytes that were output.
     *
     * This only works on Unix. Use this decoration for the tests that use it:
     * @Test(singleThreaded = true, groups = "unix")
     */
    private static long testWriteToPipe(final Consumer<String> codeThatWritesToAFilename)
            throws IOException, InterruptedException, ExecutionException {
        final Path fifo = makeFifo();

        // run the code in a separate thread
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final Future<?> writeResult = executor.submit(() -> {
                try {
                    codeThatWritesToAFilename.accept(fifo.toAbsolutePath().toString());
                } catch (Exception x) {
                    // Print to the console to aid debugging, in case the exception doesn't surface.
                    x.printStackTrace();
                    throw (x);
                }
            });

            // drain the pipe with the output.
            final Future<Integer> readResult = executor.submit(() -> {
                try (final InputStream inputStream = Files.newInputStream(fifo, StandardOpenOption.READ)) {
                    int count = 0;
                    while (inputStream.read() >= 0) {
                        count++;
                    }
                    return count;
                }
            });

            // done. If it was in error, the line below will throw the exception.
            writeResult.get();
            return readResult.get();
        } finally {
            executor.shutdownNow();
        }
    }

    private static Path makeFifo() throws IOException, InterruptedException {
        // make the fifo
        final Path fifo = Files.createTempFile("fifo.for.testWriteToPipe", "");
        Files.delete(fifo);
        fifo.toFile().deleteOnExit();
        final Process exec = new ProcessBuilder("mkfifo", fifo.toAbsolutePath().toString()).start();
        exec.waitFor(1, TimeUnit.MINUTES);
        Assert.assertEquals(exec.exitValue(), 0, "mkfifo failed with exit code " + 0);
        return fifo;
    }
}
