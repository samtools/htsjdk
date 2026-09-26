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
import htsjdk.samtools.SAMException;
import htsjdk.samtools.util.IOUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    /** Writes {@code text} to a FASTA in {@code dir}. */
    private static Path writeFasta(final Path dir, final String text) throws IOException {
        return Files.writeString(dir.resolve("test.fasta"), text, StandardCharsets.US_ASCII);
    }

    /**
     * Returns the text of a FASTA record: a header naming {@code contig}, then {@code bases} in lines of
     * {@code lineWidth}, every line ending in {@code eol}.
     */
    private static String fastaRecord(final String contig, final String bases, final int lineWidth, final String eol) {
        final StringBuilder text = new StringBuilder(">").append(contig).append(eol);
        for (int start = 0; start < bases.length(); start += lineWidth) {
            text.append(bases, start, Math.min(start + lineWidth, bases.length()))
                    .append(eol);
        }
        return text.toString();
    }

    /** Returns the offset in {@code text} just after the header line {@code header}, which must occur once. */
    private static long locationAfter(final String text, final String header) {
        return text.indexOf(header) + header.length();
    }

    private static void assertEntry(
            final FastaSequenceIndexEntry entry,
            final String contig,
            final long size,
            final long location,
            final int basesPerLine,
            final int bytesPerLine) {
        Assert.assertEquals(entry.getContig(), contig);
        Assert.assertEquals(entry.getSize(), size, "size of " + contig);
        Assert.assertEquals(entry.getLocation(), location, "location of " + contig);
        Assert.assertEquals(entry.getBasesPerLine(), basesPerLine, "bases per line of " + contig);
        Assert.assertEquals(entry.getBytesPerLine(), bytesPerLine, "bytes per line of " + contig);
    }

    /** Asserts that {@code fasta} read through {@code index} gives {@code bases} for {@code contig}. */
    private static void assertIndexedBases(
            final Path fasta, final FastaSequenceIndex index, final String contig, final String bases)
            throws IOException {
        try (IndexedFastaSequenceFile reader = new IndexedFastaSequenceFile(fasta, index)) {
            Assert.assertEquals(reader.getSequence(contig).getBaseString(), bases);
        }
    }

    /** Asserts that indexing {@code text} fails with a {@link SAMException} whose message contains {@code expected}. */
    private static void assertIndexingFails(final String text, final String expected) throws IOException {
        final Path dir = IOUtil.createTempDir("FastaSequenceIndexCreatorTest");
        try {
            final Path fasta = writeFasta(dir, text);
            final SAMException e =
                    Assert.expectThrows(SAMException.class, () -> FastaSequenceIndexCreator.buildFromFasta(fasta));
            Assert.assertTrue(e.getMessage().contains(expected), e.getMessage());
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void aBlankLineAtTheEndOfTheFileIsIgnored() throws IOException {
        final String chr1 = "ACGTACGTACGT";
        final String chr2 = "GGCCTTA";
        final String text = fastaRecord("chr1", chr1, 5, "\n") + fastaRecord("chr2", chr2, 4, "\n") + "\n";
        final Path dir = IOUtil.createTempDir("FastaSequenceIndexCreatorTest");
        try {
            final Path fasta = writeFasta(dir, text);
            final FastaSequenceIndex index = FastaSequenceIndexCreator.buildFromFasta(fasta);
            Assert.assertEquals(index.size(), 2);
            assertEntry(index.getIndexEntry("chr1"), "chr1", chr1.length(), locationAfter(text, ">chr1\n"), 5, 6);
            assertEntry(index.getIndexEntry("chr2"), "chr2", chr2.length(), locationAfter(text, ">chr2\n"), 4, 5);
            assertIndexedBases(fasta, index, "chr1", chr1);
            assertIndexedBases(fasta, index, "chr2", chr2);
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void blankLinesAtTheEndOfTheFileAreIgnored() throws IOException {
        final String chr1 = "ACGTACGTACGT";
        final String chr2 = "GGCCTTA";
        final String text = fastaRecord("chr1", chr1, 5, "\n") + fastaRecord("chr2", chr2, 4, "\n") + "\n\n\n";
        final Path dir = IOUtil.createTempDir("FastaSequenceIndexCreatorTest");
        try {
            final Path fasta = writeFasta(dir, text);
            final FastaSequenceIndex index = FastaSequenceIndexCreator.buildFromFasta(fasta);
            Assert.assertEquals(index.size(), 2);
            assertEntry(index.getIndexEntry("chr1"), "chr1", chr1.length(), locationAfter(text, ">chr1\n"), 5, 6);
            assertEntry(index.getIndexEntry("chr2"), "chr2", chr2.length(), locationAfter(text, ">chr2\n"), 4, 5);
            assertIndexedBases(fasta, index, "chr1", chr1);
            assertIndexedBases(fasta, index, "chr2", chr2);
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void blankLinesBetweenSequencesEndTheSequenceBeforeThem() throws IOException {
        final String chr1 = "ACGTACGTACGT";
        final String chr2 = "GGCCTTA";
        final String chr3 = "TTTTAAAACC";
        final String text = fastaRecord("chr1", chr1, 5, "\n")
                + "\n"
                + fastaRecord("chr2", chr2, 4, "\n")
                + "\n\n\n"
                + fastaRecord("chr3", chr3, 3, "\n");
        final Path dir = IOUtil.createTempDir("FastaSequenceIndexCreatorTest");
        try {
            final Path fasta = writeFasta(dir, text);
            final FastaSequenceIndex index = FastaSequenceIndexCreator.buildFromFasta(fasta);
            Assert.assertEquals(index.size(), 3);
            assertEntry(index.getIndexEntry("chr1"), "chr1", chr1.length(), locationAfter(text, ">chr1\n"), 5, 6);
            assertEntry(index.getIndexEntry("chr2"), "chr2", chr2.length(), locationAfter(text, ">chr2\n"), 4, 5);
            assertEntry(index.getIndexEntry("chr3"), "chr3", chr3.length(), locationAfter(text, ">chr3\n"), 3, 4);
            assertIndexedBases(fasta, index, "chr1", chr1);
            assertIndexedBases(fasta, index, "chr2", chr2);
            assertIndexedBases(fasta, index, "chr3", chr3);
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void blankLinesBeforeTheFirstHeaderAreSkipped() throws IOException {
        final String chr1 = "ACGTACGTACGT";
        final String chr2 = "GGCCTTA";
        final String text = "\n\n" + fastaRecord("chr1", chr1, 5, "\n") + fastaRecord("chr2", chr2, 4, "\n");
        final Path dir = IOUtil.createTempDir("FastaSequenceIndexCreatorTest");
        try {
            final Path fasta = writeFasta(dir, text);
            final FastaSequenceIndex index = FastaSequenceIndexCreator.buildFromFasta(fasta);
            Assert.assertEquals(index.size(), 2);
            assertEntry(index.getIndexEntry("chr1"), "chr1", chr1.length(), locationAfter(text, ">chr1\n"), 5, 6);
            assertEntry(index.getIndexEntry("chr2"), "chr2", chr2.length(), locationAfter(text, ">chr2\n"), 4, 5);
            assertIndexedBases(fasta, index, "chr1", chr1);
            assertIndexedBases(fasta, index, "chr2", chr2);
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void blankLinesWithCrlfTerminatorsAreIgnored() throws IOException {
        final String chr1 = "ACGTACGTACGT";
        final String chr2 = "GGCCTTA";
        final String text =
                fastaRecord("chr1", chr1, 5, "\r\n") + "\r\n\r\n" + fastaRecord("chr2", chr2, 4, "\r\n") + "\r\n\r\n";
        final Path dir = IOUtil.createTempDir("FastaSequenceIndexCreatorTest");
        try {
            final Path fasta = writeFasta(dir, text);
            final FastaSequenceIndex index = FastaSequenceIndexCreator.buildFromFasta(fasta);
            Assert.assertEquals(index.size(), 2);
            assertEntry(index.getIndexEntry("chr1"), "chr1", chr1.length(), locationAfter(text, ">chr1\r\n"), 5, 7);
            assertEntry(index.getIndexEntry("chr2"), "chr2", chr2.length(), locationAfter(text, ">chr2\r\n"), 4, 6);
            assertIndexedBases(fasta, index, "chr1", chr1);
            assertIndexedBases(fasta, index, "chr2", chr2);
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void aFileOfOnlyBlankLinesIsAnEmptyFileError() throws IOException {
        assertIndexingFails("\n\n\n", "Cannot index empty file");
    }

    @Test
    public void aSequenceLineAfterABlankLineIsAnError() throws IOException {
        assertIndexingFails(
                ">chr1\nACGT\n\nACGT\n>chr2\nGGCC\n", "A sequence line follows a blank line in sequence 'chr1'");
    }

    @Test
    public void aHeaderFollowedByAnotherHeaderIsAnEmptySequenceError() throws IOException {
        assertIndexingFails(">chr1\n>chr2\nACGT\n", "Empty sequences could not be indexed: sequence 'chr1'");
    }

    @Test
    public void aHeaderFollowedByABlankLineIsAnEmptySequenceError() throws IOException {
        assertIndexingFails(">chr1\n\n>chr2\nACGT\n", "Empty sequences could not be indexed: sequence 'chr1'");
    }

    @Test
    public void aHeaderAtTheEndOfTheFileIsAnEmptySequenceError() throws IOException {
        assertIndexingFails(">chr1\nACGT\n>chr2\n", "Empty sequences could not be indexed: sequence 'chr2'");
    }

    @Test
    public void aLastSequenceLineWithoutANewlineIsIndexed() throws IOException {
        final String chr1 = "ACGTACGTACGT";
        final String chr2 = "ACGTAC";
        final String terminated = fastaRecord("chr1", chr1, 5, "\n") + fastaRecord("chr2", chr2, 4, "\n");
        final String text = terminated.substring(0, terminated.length() - "\n".length());
        final Path dir = IOUtil.createTempDir("FastaSequenceIndexCreatorTest");
        try {
            final Path fasta = writeFasta(dir, text);
            final FastaSequenceIndex index = FastaSequenceIndexCreator.buildFromFasta(fasta);
            Assert.assertEquals(index.size(), 2);
            assertEntry(index.getIndexEntry("chr1"), "chr1", chr1.length(), locationAfter(text, ">chr1\n"), 5, 6);
            assertEntry(index.getIndexEntry("chr2"), "chr2", chr2.length(), locationAfter(text, ">chr2\n"), 4, 5);
            assertIndexedBases(fasta, index, "chr1", chr1);
            assertIndexedBases(fasta, index, "chr2", chr2);
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void aLastSequenceLineWithoutACrlfIsIndexed() throws IOException {
        final String chr1 = "ACGTACGTACGT";
        final String chr2 = "ACGTAC";
        final String terminated = fastaRecord("chr1", chr1, 5, "\r\n") + fastaRecord("chr2", chr2, 4, "\r\n");
        final String text = terminated.substring(0, terminated.length() - "\r\n".length());
        final Path dir = IOUtil.createTempDir("FastaSequenceIndexCreatorTest");
        try {
            final Path fasta = writeFasta(dir, text);
            final FastaSequenceIndex index = FastaSequenceIndexCreator.buildFromFasta(fasta);
            Assert.assertEquals(index.size(), 2);
            assertEntry(index.getIndexEntry("chr1"), "chr1", chr1.length(), locationAfter(text, ">chr1\r\n"), 5, 7);
            assertEntry(index.getIndexEntry("chr2"), "chr2", chr2.length(), locationAfter(text, ">chr2\r\n"), 4, 6);
            assertIndexedBases(fasta, index, "chr1", chr1);
            assertIndexedBases(fasta, index, "chr2", chr2);
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void sequencesAreNumberedFromZero() throws IOException {
        final String text = fastaRecord("chr1", "ACGTACGT", 4, "\n")
                + fastaRecord("chr2", "GGCC", 4, "\n")
                + fastaRecord("chr3", "TT", 4, "\n");
        final Path dir = IOUtil.createTempDir("FastaSequenceIndexCreatorTest");
        try {
            final FastaSequenceIndex index = FastaSequenceIndexCreator.buildFromFasta(writeFasta(dir, text));
            Assert.assertEquals(index.getIndexEntry("chr1").getSequenceIndex(), 0);
            Assert.assertEquals(index.getIndexEntry("chr2").getSequenceIndex(), 1);
            Assert.assertEquals(index.getIndexEntry("chr3").getSequenceIndex(), 2);
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void aSequenceLineLongerThanTheFirstNamesTheContigAndLength() throws IOException {
        assertIndexingFails(">chr1\nACGT\nACGTACG\n", "Sequence line for chr1 was longer than the expected length (4)");
    }

    @Test
    public void twoShortSequenceLinesNameTheContigAndLength() throws IOException {
        assertIndexingFails(">chr1\nACGT\nAC\nAC\n", "Only last line could have less than 4 bases for 'chr1' sequence");
    }
}
