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
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class FastaSequenceIndexCreatorTest extends HtsjdkTest {
    private static File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/reference");


    @DataProvider(name = "indexedSequences")
    public Object[][] getIndexedSequences() {
        return new Object[][]{
                {new File(TEST_DATA_DIR, "Homo_sapiens_assembly18.trimmed.fasta")},
                {new File(TEST_DATA_DIR, "Homo_sapiens_assembly18.trimmed.fasta.gz")},
                {new File(TEST_DATA_DIR, "header_with_white_space.fasta")},
                {new File(TEST_DATA_DIR, "crlf.fasta")}
        };
    }

    @Test(dataProvider = "indexedSequences")
    public void testBuildFromFasta(final File indexedFile) throws Exception {
        final FastaSequenceIndex original = new FastaSequenceIndex(new File(indexedFile.getAbsolutePath() + ".fai"));
        final FastaSequenceIndex build = FastaSequenceIndexCreator.buildFromFasta(indexedFile.toPath());
        Assert.assertEquals(original, build);
    }

    @Test(dataProvider = "indexedSequences")
    public void testCreate(final File indexedFile) throws Exception {
        // copy the file to index
        final File tempDir = IOUtil.createTempDir("FastaSequenceIndexCreatorTest", "testCreate");
        final File copied = new File(tempDir, indexedFile.getName());
        copied.deleteOnExit();
        Files.copy(indexedFile.toPath(), copied.toPath());

        // create the index for the copied file
        FastaSequenceIndexCreator.create(copied.toPath(), false);

        // test if the expected .fai and the created one are the same
        final File expectedFai = new File(indexedFile.getAbsolutePath() +  ".fai");
        final File createdFai = new File(copied.getAbsolutePath() + ".fai");

        // read all the files and compare line by line
        try(final Stream<String> expected = Files.lines(expectedFai.toPath());
                final Stream<String> created = Files.lines(createdFai.toPath())) {
            final List<String> expectedLines = expected.filter(String::isEmpty).collect(Collectors.toList());
            final List<String> createdLines = created.filter(String::isEmpty).collect(Collectors.toList());
            Assert.assertEquals(expectedLines, createdLines);
        }

        // load the tmp index and check that both are the same
        Assert.assertEquals(new FastaSequenceIndex(createdFai), new FastaSequenceIndex(expectedFai));
    }

}