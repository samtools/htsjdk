/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Daniel Gomez-Sanchez
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package htsjdk.samtools.reference;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.IOUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class FastaSequenceIndexCreatorTest extends HtsjdkTest {
    private static Path TEST_DATA_DIR = Path.of("src/test/resources/htsjdk/samtools/reference");

    @DataProvider(name = "indexedSequences")
    public Object[][] getIndexedSequences() {
        return new Object[][] {
            {TEST_DATA_DIR.resolve("Homo_sapiens_assembly18.trimmed.fasta")},
            {TEST_DATA_DIR.resolve("Homo_sapiens_assembly18.trimmed.fasta.gz")},
            {TEST_DATA_DIR.resolve("header_with_white_space.fasta")},
            {TEST_DATA_DIR.resolve("crlf.fasta")}
        };
    }

    @Test(dataProvider = "indexedSequences")
    public void testBuildFromFasta(final Path indexedFile) throws Exception {
        final FastaSequenceIndex original = new FastaSequenceIndex(Path.of(indexedFile.toAbsolutePath() + ".fai"));
        final FastaSequenceIndex build = FastaSequenceIndexCreator.buildFromFasta(indexedFile);
        Assert.assertEquals(original, build);
    }

    @Test(dataProvider = "indexedSequences")
    public void testCreate(final Path indexedFile) throws Exception {
        // copy the file to index
        final Path tempDir = IOUtil.createTempDir("FastaSequenceIndexCreatorTest.testCreate");
        final Path copied = tempDir.resolve(indexedFile.getFileName());
        copied.toFile().deleteOnExit();
        Files.copy(indexedFile, copied);

        // create the index for the copied file
        FastaSequenceIndexCreator.create(copied, false);

        // test if the expected .fai and the created one are the same
        final Path expectedFai = Path.of(indexedFile.toAbsolutePath() + ".fai");
        final Path createdFai = Path.of(copied.toAbsolutePath() + ".fai");

        // read all the files and compare line by line
        try (final Stream<String> expected = Files.lines(expectedFai);
                final Stream<String> created = Files.lines(createdFai)) {
            final List<String> expectedLines = expected.filter(String::isEmpty).collect(Collectors.toList());
            final List<String> createdLines = created.filter(String::isEmpty).collect(Collectors.toList());
            Assert.assertEquals(expectedLines, createdLines);
        }

        // load the tmp index and check that both are the same
        Assert.assertEquals(new FastaSequenceIndex(createdFai), new FastaSequenceIndex(expectedFai));
    }
}
