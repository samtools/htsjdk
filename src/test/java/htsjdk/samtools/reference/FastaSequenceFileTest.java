/*
 * The MIT License
 *
 * Copyright (c) 2010 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools.reference;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.Defaults;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.StringUtil;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author alecw@broadinstitute.org
 */
public class FastaSequenceFileTest extends HtsjdkTest {
    @Test
    public void testTrailingWhitespace() throws Exception {
        final Path fasta = Files.createTempFile("test", ".fasta");
        fasta.toFile().deleteOnExit();
        final PrintWriter writer = new PrintWriter(fasta.toFile());
        final String chr1 = "chr1";
        writer.println(">" + chr1);
        final String sequence = "ACGTACGT";
        writer.println(sequence);
        writer.println(sequence + " \t");
        writer.close();
        final FastaSequenceFile fastaReader = new FastaSequenceFile(fasta, true);
        final ReferenceSequence referenceSequence = fastaReader.nextSequence();
        Assert.assertEquals(referenceSequence.getName(), chr1);
        Assert.assertEquals(StringUtil.bytesToString(referenceSequence.getBases()), sequence + sequence);
    }

    @Test
    public void testIntermediateWhitespace() throws Exception {
        final Path fasta = Files.createTempFile("test", ".fasta");
        fasta.toFile().deleteOnExit();
        final PrintWriter writer = new PrintWriter(fasta.toFile());
        final String chr1 = "chr1";
        writer.println(">" + chr1 + " extra stuff after sequence name");
        final String sequence = "ACGTACGT";
        writer.println(sequence + "  ");
        writer.println(sequence + " \t");
        writer.println(sequence);
        writer.close();
        final FastaSequenceFile fastaReader = new FastaSequenceFile(fasta, true);
        final ReferenceSequence referenceSequence = fastaReader.nextSequence();
        Assert.assertEquals(referenceSequence.getName(), chr1);
        Assert.assertEquals(StringUtil.bytesToString(referenceSequence.getBases()), sequence + sequence + sequence);
    }

    // There was a bug when reading a fasta with trailing whitespace, only when a sequence dictionary exists.
    @Test
    public void testTrailingWhitespaceWithPreexistingSequenceDictionary() throws Exception {
        final Path fasta =
                Path.of("src/test/resources/htsjdk/samtools/reference/reference_with_trailing_whitespace.fasta");
        final FastaSequenceFile fastaReader = new FastaSequenceFile(fasta, true);
        ReferenceSequence referenceSequence = fastaReader.nextSequence();
        Assert.assertEquals(referenceSequence.getName(), "chr1");
        Assert.assertEquals(StringUtil.bytesToString(referenceSequence.getBases()), "ACGTACGT");
        referenceSequence = fastaReader.nextSequence();
        Assert.assertEquals(referenceSequence.getName(), "chr2");
        Assert.assertEquals(StringUtil.bytesToString(referenceSequence.getBases()), "TCGATCGA");
    }

    @Test
    public void testStream() throws Exception {
        final Path fasta = Files.createTempFile("test", ".fasta");
        fasta.toFile().deleteOnExit();
        final PrintWriter writer = new PrintWriter(fasta.toFile());
        final String chr1 = "chr1";
        writer.println(">" + chr1);
        final String sequence = "ACGTACGT";
        writer.println(sequence);
        writer.println(sequence + " \t");
        writer.close();
        try (SeekableStream seekableStream = new SeekableFileStream(fasta)) {
            final FastaSequenceFile fastaReader =
                    new FastaSequenceFile(fasta.toAbsolutePath().toString(), seekableStream, null, true);
            final ReferenceSequence referenceSequence1 = fastaReader.nextSequence();
            Assert.assertEquals(referenceSequence1.getName(), chr1);
            Assert.assertEquals(StringUtil.bytesToString(referenceSequence1.getBases()), sequence + sequence);
            // try to reset and re-read the first sequence
            fastaReader.reset();
            final ReferenceSequence referenceSequence2 = fastaReader.nextSequence();
            Assert.assertEquals(referenceSequence2.getName(), chr1);
            Assert.assertEquals(StringUtil.bytesToString(referenceSequence2.getBases()), sequence + sequence);
        }
    }

    @Test
    public void nextBufferLengthDoubles() {
        Assert.assertEquals(FastaSequenceFile.nextBufferLength(1000, "chr1", "test.fasta"), 2000);
    }

    @Test
    public void nextBufferLengthStopsAtTheLargestArray() {
        Assert.assertEquals(FastaSequenceFile.nextBufferLength(1 << 30, "chr1", "test.fasta"), Integer.MAX_VALUE - 8);
        Assert.assertEquals(
                FastaSequenceFile.nextBufferLength((Integer.MAX_VALUE - 8) / 2 + 1, "chr1", "test.fasta"),
                Integer.MAX_VALUE - 8);
    }

    @Test
    public void nextBufferLengthFailsClearlyAtTheLargestArray() {
        final SAMException e = Assert.expectThrows(
                SAMException.class,
                () -> FastaSequenceFile.nextBufferLength(Integer.MAX_VALUE - 8, "chrHuge", "huge.fasta"));
        Assert.assertTrue(e.getMessage().contains("chrHuge"), e.getMessage());
        Assert.assertTrue(e.getMessage().contains("huge.fasta"), e.getMessage());
    }

    @Test
    public void sequencesLongerThanTheInitialBufferAreReadWithoutADictionary() throws Exception {
        // One sequence exactly fills the initial buffer and the next needs it grown twice
        final Random random = new Random(42);
        final String[] sequences = {
            randomBases(random, Defaults.NON_ZERO_BUFFER_SIZE),
            randomBases(random, 2 * Defaults.NON_ZERO_BUFFER_SIZE + 1)
        };
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < sequences.length; i++) {
            text.append(">chr").append(i + 1).append('\n');
            for (int start = 0; start < sequences[i].length(); start += 60) {
                text.append(sequences[i], start, Math.min(start + 60, sequences[i].length()))
                        .append('\n');
            }
        }
        final Path dir = IOUtil.createTempDir("FastaSequenceFileTest");
        try {
            final Path fasta = Files.writeString(dir.resolve("long.fasta"), text, StandardCharsets.US_ASCII);
            try (FastaSequenceFile reader = new FastaSequenceFile(fasta, true)) {
                Assert.assertNull(reader.getSequenceDictionary());
                for (int i = 0; i < sequences.length; i++) {
                    final ReferenceSequence sequence = reader.nextSequence();
                    Assert.assertEquals(sequence.getName(), "chr" + (i + 1));
                    Assert.assertEquals(sequence.getBaseString(), sequences[i]);
                }
                Assert.assertNull(reader.nextSequence());
            }
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    private static String randomBases(final Random random, final int length) {
        final char[] bases = new char[length];
        for (int i = 0; i < length; i++) {
            bases[i] = "ACGT".charAt(random.nextInt(4));
        }
        return new String(bases);
    }
}
