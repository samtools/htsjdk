/*
 * Copyright (c) 2012 The Broad Institute
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

package htsjdk.variant;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.variant.example.PrintVariantsExample;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import org.testng.Assert;
import org.testng.annotations.Test;

public class PrintVariantsExampleTest extends HtsjdkTest {
    @Test
    public void testExampleWriteFile() throws IOException {
        final Path tempFile = Files.createTempFile("example", FileExtensions.VCF);
        tempFile.toFile().deleteOnExit();
        Path f1 = Path.of(
                "src/test/resources/htsjdk/variant/ILLUMINA.wex.broad_phase2_baseline.20111114.both.exome.genotypes.1000.vcf");
        final String[] args = {
            f1.toAbsolutePath().toString(), tempFile.toAbsolutePath().toString()
        };
        Assert.assertEquals(Files.size(tempFile), 0);
        PrintVariantsExample.main(args);
        Assert.assertNotEquals(Files.size(tempFile), 0);

        assertFilesEqualSkipHeaders(tempFile, f1);
    }

    private void assertFilesEqualSkipHeaders(Path tempFile, Path f1) {
        final List<String> lines1 = IOUtil.slurpLines(f1);
        final List<String> lines2 = IOUtil.slurpLines(tempFile);
        final int firstNonComment1 = IntStream.range(0, lines1.size())
                .filter(i -> !lines1.get(i).startsWith("#"))
                .findFirst()
                .getAsInt();
        final int firstNonComment2 = IntStream.range(0, lines2.size())
                .filter(i -> !lines2.get(i).startsWith("#"))
                .findFirst()
                .getAsInt();
        Assert.assertEquals(
                lines1.subList(firstNonComment1, lines1.size()), lines2.subList(firstNonComment2, lines2.size()));
    }
}
