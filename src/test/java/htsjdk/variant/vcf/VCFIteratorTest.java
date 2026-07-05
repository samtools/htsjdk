/*
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
package htsjdk.variant.vcf;

import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.variant.VariantBaseTest;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.zip.GZIPOutputStream;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class VCFIteratorTest extends VariantBaseTest {

    @DataProvider(name = "VariantFiles")
    public Object[][] getVariantFiles() {
        return new Object[][] {
            new Object[] {"src/test/resources/htsjdk/tribble/tabix/testTabixIndex.vcf", 25},
            new Object[] {"src/test/resources/htsjdk/tribble/tabix/testTabixIndex.vcf.gz", 25},
            new Object[] {"src/test/resources/htsjdk/variant/serialization_test.bcf", 12}
        };
    }

    @DataProvider(name = "VcfFiles")
    public Object[][] getVcfFiles() {
        return new Object[][] {
            new Object[] {"src/test/resources/htsjdk/tribble/tabix/testTabixIndex.vcf", 25},
            new Object[] {"src/test/resources/htsjdk/tribble/tabix/testTabixIndex.vcf.gz", 25}
        };
    }

    private void assertExpectedNumberOfVariants(final VCFIterator r, final int expectVariants) {
        try {
            Assert.assertNotNull(r.getHeader());
            final int nVariants = (int) r.stream().count();
            Assert.assertEquals(nVariants, expectVariants);
        } finally {
            r.close();
        }
    }

    @Test(dataProvider = "VariantFiles")
    public void testUsingUri(final String uri, final int nVariants) throws IOException {
        final VCFIterator r = new VCFIteratorBuilder().open(uri);
        assertExpectedNumberOfVariants(r, nVariants);
    }

    @Test(dataProvider = "VariantFiles")
    public void testUsingFile(final String file, final int nVariants) throws IOException {
        final VCFIterator r = new VCFIteratorBuilder().open(Path.of(file));
        assertExpectedNumberOfVariants(r, nVariants);
    }

    private void testUsingZippedInput(
            final String filepath, final int nVariants, final Function<Path, OutputStream> outputStreamProvider)
            throws IOException {
        Path tmp = Path.of(filepath);
        /* TODO fix this when VCFFileReader will support BCF see
         * https://github.com/samtools/htsjdk/pull/837#discussion_r139490218
         * https://github.com/samtools/htsjdk/issues/946
         */
        if (tmp.getFileName().toString().endsWith(FileExtensions.VCF)) {
            tmp = Files.createTempFile("tmp", FileExtensions.COMPRESSED_VCF);
            tmp.toFile().deleteOnExit();
            try (FileInputStream in = new FileInputStream(filepath);
                    OutputStream out = outputStreamProvider.apply(tmp); ) {
                IOUtil.copyStream(in, out);
                out.flush();
            } catch (final IOException err) {
                throw err;
            }
        }
        try (final VCFIterator r = new VCFIteratorBuilder().open(tmp)) {
            assertExpectedNumberOfVariants(r, nVariants);
        }
    }

    @Test(dataProvider = "VcfFiles")
    public void testUsingBGZippedInput(final String filepath, final int nVariants) throws IOException {
        testUsingZippedInput(filepath, nVariants, (P) -> new BlockCompressedOutputStream(P));
    }

    @Test(dataProvider = "VcfFiles")
    public void testUsingGZippedInput(final String filepath, final int nVariants) throws IOException {
        testUsingZippedInput(filepath, nVariants, (P) -> {
            try {
                return new GZIPOutputStream(Files.newOutputStream(P));
            } catch (final IOException err) {
                throw new RuntimeIOException(err);
            }
        });
    }

    @Test(dataProvider = "VariantFiles")
    public void testUsingStreams(final String filepath, final int nVariants) throws IOException {
        final InputStream in = new FileInputStream(filepath);
        final VCFIterator r = new VCFIteratorBuilder().open(in);
        assertExpectedNumberOfVariants(r, nVariants);
        in.close();
    }

    @Test(dataProvider = "VariantFiles")
    public void testUsingPath(final String path, final int nVariants) throws IOException {
        final Path a_path = Path.of(path);
        final VCFIterator r = new VCFIteratorBuilder().open(a_path);
        assertExpectedNumberOfVariants(r, nVariants);
    }
}
