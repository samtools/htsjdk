package htsjdk.samtools.reference;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.CollectionUtil;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class FastaReferenceWriterTest extends HtsjdkTest {

    @Test(expectedExceptions = IllegalStateException.class)
    public void testEmptySequence() throws IOException {
        final File testOutput = File.createTempFile("fwr-test", ".fasta");
        Assert.assertTrue(testOutput.delete());
        testOutput.deleteOnExit();

        try (final FastaReferenceWriter writer = new FastaReferenceWriterBuilder().setFastaFile(testOutput.toPath()).setMakeFaiOutput(false).setMakeDictOutput(false).build()) {
            writer.startSequence("seq1");
            writer.appendBases(SequenceUtil.getRandomBases(new Random(113), 100));
            writer.startSequence("seq2");
            writer.startSequence("seq3");
        } finally {
            Assert.assertTrue(testOutput.delete());
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testEmptyReference() throws IOException {
        final File testOutput = File.createTempFile("fwr-test", ".fasta");
        Assert.assertTrue(testOutput.delete());
        testOutput.deleteOnExit();

        try {
            new FastaReferenceWriterBuilder().setFastaFile(testOutput).setMakeFaiOutput(false).setMakeDictOutput(false).build().close();
        } finally {
            Assert.assertTrue(testOutput.delete());
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testStartSequenceAfterClose() throws IOException {
        final File testOutput = File.createTempFile("fwr-test", ".fasta");
        Assert.assertTrue(testOutput.delete());
        testOutput.deleteOnExit();

        final FastaReferenceWriter writer = new FastaReferenceWriterBuilder().setFastaFile(testOutput).setMakeFaiOutput(false).setMakeDictOutput(false).build();
        writer.startSequence("seq1").appendBases(new byte[]{'A', 'C', 'G', 'T'});
        writer.close();
        try {
            writer.startSequence("seq2");
        } finally {
            Assert.assertTrue(testOutput.delete());
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testAddBasesAfterClose() throws IOException {
        final File testOutput = File.createTempFile("fwr-test", ".fasta");
        Assert.assertTrue(testOutput.delete());
        testOutput.deleteOnExit();

        final FastaReferenceWriter writer = new FastaReferenceWriterBuilder().setFastaFile(testOutput).setMakeFaiOutput(false).setMakeDictOutput(false).build();
        writer.startSequence("seq1").appendBases(new byte[]{'A', 'C', 'G', 'T'});
        writer.close();
        try {
            writer.appendBases(new byte[]{'A', 'A', 'A'});
        } finally {
            Assert.assertTrue(testOutput.delete());
        }
    }

    @Test(dataProvider = "invalidBplData", expectedExceptions = IllegalArgumentException.class)
    public void testBadDefaultBasesPerLine(final int invalidBpl) throws IOException {
        final File testOutput = File.createTempFile("fwr-test", ".fasta");
        Assert.assertTrue(testOutput.delete());
        testOutput.deleteOnExit();

        final File testIndexOutput = ReferenceSequenceFileFactory.getFastaIndexFileName(testOutput.toPath()).toFile();
        testIndexOutput.deleteOnExit();

        final File testDictOutput = ReferenceSequenceFileFactory.getDefaultDictionaryForReferenceSequence(testOutput);
        testDictOutput.deleteOnExit();
        try (FastaReferenceWriter unused = new FastaReferenceWriterBuilder().setBasesPerLine(invalidBpl).setFastaFile(testOutput).build()) {
            // no-op
        } finally {
            // make sure that no output file was created:
            Assert.assertFalse(testOutput.delete());
            Assert.assertFalse(testIndexOutput.delete());
            Assert.assertFalse(testDictOutput.delete());
        }
    }

    @Test(dataProvider = "invalidBplData", expectedExceptions = IllegalArgumentException.class)
    public void testBadSequenceBasesPerLine(final int invalidBpl) throws IOException {
        final File testOutput = File.createTempFile("fwr-test", ".fasta");
        Assert.assertTrue(testOutput.delete());
        testOutput.deleteOnExit();

        try (final FastaReferenceWriter writer = new FastaReferenceWriterBuilder().setFastaFile(testOutput).setMakeFaiOutput(false).setMakeDictOutput(false).build()) {
            writer.startSequence("seq1", invalidBpl);
        } finally {
            Assert.assertTrue(testOutput.delete());
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testEmptySequenceAtTheEnd() throws IOException {
        final File testOutput = File.createTempFile("fwr-test", ".fasta");
        testOutput.deleteOnExit();

        Assert.assertTrue(testOutput.delete());
        try (final FastaReferenceWriter writer = new FastaReferenceWriterBuilder().setFastaFile(testOutput).setMakeFaiOutput(false).setMakeDictOutput(false).build()) {
            writer.startSequence("seq1");
            writer.appendBases(SequenceUtil.getRandomBases(new Random(113), 100));
            writer.startSequence("seq2");
            writer.appendBases(SequenceUtil.getRandomBases(new Random(13), 1001));
            writer.startSequence("seq3");
        } finally {
            Assert.assertTrue(testOutput.delete());
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testAppendBasesBeforeStartingSequence() throws IOException {
        final File testOutput = File.createTempFile("fwr-test", ".fasta");
        testOutput.deleteOnExit();
        Assert.assertTrue(testOutput.delete());
        try (final FastaReferenceWriter writer = new FastaReferenceWriterBuilder().setFastaFile(testOutput).setMakeFaiOutput(false).setMakeDictOutput(false).build()) {
            writer.appendBases(SequenceUtil.getRandomBases(new Random(113), 100));
        } finally {
            Assert.assertTrue(testOutput.delete());
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testAddingSameSequenceTwice() throws IOException {
        final File testOutput = File.createTempFile("fwr-test", ".fasta");
        testOutput.deleteOnExit();
        Assert.assertTrue(testOutput.delete());
        try (final FastaReferenceWriter writer = new FastaReferenceWriterBuilder().setFastaFile(testOutput).setMakeFaiOutput(false).setMakeDictOutput(false).build()) {
            writer.startSequence("seq1");
            writer.appendBases(SequenceUtil.getRandomBases(new Random(113), 100));
            writer.startSequence("seq2");
            writer.appendBases(SequenceUtil.getRandomBases(new Random(114), 300));
            writer.startSequence("seq1");
        } finally {
            Assert.assertTrue(testOutput.delete());
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testAddingSameSequenceRightAfter() throws IOException {
        final File testOutput = File.createTempFile("fwr-test", ".fasta");
        Assert.assertTrue(testOutput.delete());
        testOutput.deleteOnExit();

        try (final FastaReferenceWriter writer = new FastaReferenceWriterBuilder().setFastaFile(testOutput).setMakeFaiOutput(false).setMakeDictOutput(false).build()) {
            writer.startSequence("seq1");
            writer.appendBases(SequenceUtil.getRandomBases(new Random(113), 100));
            writer.startSequence("seq1");
        } finally {
            Assert.assertTrue(testOutput.delete());
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "invalidNameData")
    public void testAddingInvalidSequenceName(final String invalidName) throws IOException {
        final File testOutput = File.createTempFile("fwr-test", ".fasta");
        Assert.assertTrue(testOutput.delete());
        testOutput.deleteOnExit();

        try (final FastaReferenceWriter writer = new FastaReferenceWriterBuilder().setFastaFile(testOutput).setMakeFaiOutput(false).setMakeDictOutput(false).build()) {
            writer.startSequence(invalidName);
        } finally {
            Assert.assertTrue(testOutput.delete());
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "invalidDescriptionData")
    public void testAddingInvalidDescription(final String invalidDescription) throws IOException {
        final File testOutput = File.createTempFile("fwr-test", ".fasta");
        Assert.assertTrue(testOutput.delete());
        testOutput.deleteOnExit();

        try (final FastaReferenceWriter writer = new FastaReferenceWriterBuilder().setFastaFile(testOutput).setMakeFaiOutput(false).setMakeDictOutput(false).build()) {
            writer.startSequence("seq1", invalidDescription);
        } finally {
            Assert.assertTrue(testOutput.delete());
        }
    }

    @DataProvider(name = "invalidBplData")
    public Object[][] invalidBplData() {
        return IntStream.of(0, -1, -110)
                .mapToObj(i -> new Object[]{i}).toArray(Object[][]::new);
    }

    @DataProvider(name = "invalidNameData")
    public Object[][] invalidNameData() {
        return Stream.of("seq with spaces", "seq\twith\ttabs", "with blank", " ", "", "nnn\n", "rrr\r", null)
                .map(s -> new Object[]{s}).toArray(Object[][]::new);
    }

    @DataProvider(name = "invalidDescriptionData")
    public Object[][] invalidDescriptionData() {
        return Stream.of("\nwith control chars\nthat are not\0tabs\r", "with the null\0", "with nl\n")
                .map(s -> new Object[]{s}).toArray(Object[][]::new);
    }

    @Test(dataProvider = "testData")
    public void testWriter(final SAMSequenceDictionary dictionary, final boolean withIndex, final boolean withDictionary,
                           final boolean withDescriptions, final int defaultBpl,
                           final int minBpl, final int maxBpl, final int seed)
            throws IOException, GeneralSecurityException, URISyntaxException {
        final Map<String, byte[]> bases = new LinkedHashMap<>(dictionary.getSequences().size());
        final Map<String, Integer> bpl = new LinkedHashMap<>(dictionary.getSequences().size());
        final Random rdn = new Random(seed);
        generateRandomBasesAndBpls(dictionary, minBpl, maxBpl, bases, bpl, rdn);
        final File fastaFile = File.createTempFile("fwr-test", ".fa");
        Assert.assertTrue(fastaFile.delete());
        fastaFile.deleteOnExit();

        final File fastaIndexFile = new File(fastaFile.getParentFile(), fastaFile.getName() + ".fai");
        final File dictFile = new File(fastaFile.getParentFile(), fastaFile.getName().replaceAll("\\.fa", ".dict"));
        fastaIndexFile.deleteOnExit();
        dictFile.deleteOnExit();

        final FastaReferenceWriterBuilder builder = new FastaReferenceWriterBuilder().setFastaFile(fastaFile).setMakeFaiOutput(withIndex).setMakeDictOutput(withDictionary);
        if (defaultBpl > 0) {
            builder.setBasesPerLine(defaultBpl);
        }

        try (final FastaReferenceWriter writer = builder.build()) {
            writeReference(writer, withDescriptions, rdn, dictionary, bases, bpl);
        }

        assertOutput(fastaFile.toPath(), withIndex, withDictionary, withDescriptions, dictionary, defaultBpl, bases, bpl);
        Assert.assertTrue(fastaFile.delete());
        Assert.assertEquals(fastaIndexFile.delete(), withIndex);
        Assert.assertEquals(dictFile.delete(), withDictionary);
    }

    @Test
    public void testSingleSequenceStaticWithBpl() throws IOException, GeneralSecurityException, URISyntaxException {
        final File testOutputFile = File.createTempFile("fwr-test", ".random0.fasta");
        testOutputFile.deleteOnExit();

        final Map<String, byte[]> seqs =
                Collections.singletonMap("seqA", SequenceUtil.getRandomBases(new Random(1241), 100));
        final Map<String, Integer> bpls = Collections.singletonMap("seqA", 42);
        final SAMSequenceDictionary dictionary = new SAMSequenceDictionary(
                Collections.singletonList(new SAMSequenceRecord("seqA", 100))
        );
        FastaReferenceWriter.writeSingleSequenceReference(testOutputFile.toPath(), 42,
                true, true, "seqA", null, seqs.get("seqA"));
        assertOutput(testOutputFile.toPath(), true, true, false, dictionary, 42, seqs, bpls);
        Assert.assertTrue(testOutputFile.delete());
        Assert.assertTrue(ReferenceSequenceFileFactory.getDefaultDictionaryForReferenceSequence(testOutputFile).delete());
        Assert.assertTrue(ReferenceSequenceFileFactory.getFastaIndexFileName(testOutputFile.toPath()).toFile().delete());
    }

    @Test
    public void testSingleSequenceStatic() throws IOException, GeneralSecurityException, URISyntaxException {
        final File testOutputFile = File.createTempFile("fwr-test", ".random0.fasta");
        testOutputFile.deleteOnExit();

        final Map<String, byte[]> seqs = Collections.singletonMap("seqA", SequenceUtil.getRandomBases(new Random(1341), 100));
        final Map<String, Integer> bpls = Collections.singletonMap("seqA", FastaReferenceWriter.DEFAULT_BASES_PER_LINE);
        final SAMSequenceDictionary dictionary = new SAMSequenceDictionary(
                Collections.singletonList(new SAMSequenceRecord("seqA", 100))
        );
        FastaReferenceWriter.writeSingleSequenceReference(testOutputFile.toPath(),
                true, true, "seqA", null, seqs.get("seqA"));
        assertOutput(testOutputFile.toPath(), true, true, false, dictionary, FastaReferenceWriter.DEFAULT_BASES_PER_LINE, seqs, bpls);
        Assert.assertTrue(testOutputFile.delete());
        Assert.assertTrue(ReferenceSequenceFileFactory.getDefaultDictionaryForReferenceSequence(testOutputFile).delete());
        Assert.assertTrue(ReferenceSequenceFileFactory.getFastaIndexFileName(testOutputFile.toPath()).toFile().delete());
    }

    @Test
    public void testCopyReference() throws IOException, GeneralSecurityException, URISyntaxException {

        final int basesPerLine = 80;
        final Path testOutputFile = File.createTempFile("fwr-test", ".copy0.fasta").toPath();
        testOutputFile.toFile().deleteOnExit();

        final Path testIndexOutputFile = ReferenceSequenceFileFactory.getFastaIndexFileName(testOutputFile);
        testIndexOutputFile.toFile().deleteOnExit();

        final Path testDictOutputFile = ReferenceSequenceFileFactory.getDefaultDictionaryForReferenceSequence(testOutputFile);
        testDictOutputFile.toFile().deleteOnExit();

        final File source = new File("src/test/resources/htsjdk/samtools/hg19mini.fasta");

        final ReferenceSequenceFile sourceFasta = ReferenceSequenceFileFactory.getReferenceSequenceFile(source);
        final Map<String, byte[]> seqs = new HashMap<>();

        try (final FastaReferenceWriter fastaReferenceWriter = new FastaReferenceWriterBuilder().setBasesPerLine(basesPerLine).setFastaFile(testOutputFile).build()) {
            ReferenceSequence referenceSequence;

            while ((referenceSequence = sourceFasta.nextSequence()) != null) {
                seqs.put(referenceSequence.getName(), referenceSequence.getBases());
                fastaReferenceWriter.appendSequence(referenceSequence.getName(), referenceSequence.getBases());
            }
        }
        // Can't compare files directly since discription isn't read by ReferenceSequenceFile and so it isn't written to new fasta.
        final SAMSequenceDictionary testDictionary = SAMSequenceDictionaryExtractor.extractDictionary(testDictOutputFile);
        assertFastaContent(testOutputFile, false, testDictionary, basesPerLine, seqs, new CollectionUtil.DefaultingMap<String, Integer>(k -> -1, false));
        assertFastaIndexContent(testOutputFile, testIndexOutputFile, testDictionary, seqs);
        assertFastaDictionaryContent(testDictOutputFile, testDictionary);

        Assert.assertTrue(Files.deleteIfExists(testOutputFile));
        Assert.assertTrue(Files.deleteIfExists(testIndexOutputFile));
        Assert.assertTrue(Files.deleteIfExists(testDictOutputFile));
    }

    @Test
    public void testAlternativeIndexAndDictFileNames() throws IOException, GeneralSecurityException, URISyntaxException {
        final File testOutputFile = File.createTempFile("fwr-test", ".random0.fasta");
        testOutputFile.deleteOnExit();

        final File testIndexOutputFile = File.createTempFile("fwr-test", ".random1.fai");
        testIndexOutputFile.deleteOnExit();

        final File testDictOutputFile = File.createTempFile("fwr-test", ".random2.dict");
        testDictOutputFile.deleteOnExit();

        final SAMSequenceDictionary testDictionary = new SAMSequenceDictionary(
                Collections.singletonList(new SAMSequenceRecord("seq1", 100))
        );
        final Map<String, byte[]> seqs = Collections.singletonMap("seq1", SequenceUtil.getRandomBases(new Random(1341), 100));
        final Map<String, Integer> bpls = Collections.singletonMap("seq1", -1);
        try (final FastaReferenceWriter writer = new FastaReferenceWriterBuilder().setFastaFile(testOutputFile).setIndexFile(testIndexOutputFile).setDictFile(testDictOutputFile).build()) {
            writer.startSequence("seq1");
            writer.appendBases(seqs.get("seq1"));
        }
        assertFastaContent(testOutputFile.toPath(), false, testDictionary, -1, seqs, bpls);
        assertFastaIndexContent(testOutputFile.toPath(), testIndexOutputFile.toPath(), testDictionary, seqs);
        assertFastaDictionaryContent(testDictOutputFile.toPath(), testDictionary);
        Assert.assertTrue(testOutputFile.delete());
        Assert.assertTrue(testIndexOutputFile.delete());
        Assert.assertTrue(testDictOutputFile.delete());
    }

    @Test
    public void testDirectOutputStreams() throws IOException, GeneralSecurityException, URISyntaxException {
        final File testOutputFile = File.createTempFile("fwr-test", ".random0.fasta");
        testOutputFile.deleteOnExit();

        final File testIndexOutputFile = File.createTempFile("fwr-test", ".random1.fai");
        testIndexOutputFile.deleteOnExit();
        final File testDictOutputFile = File.createTempFile("fwr-test", ".random2.dict");
        testDictOutputFile.deleteOnExit();
        final SAMSequenceDictionary testDictionary = new SAMSequenceDictionary(
                Collections.singletonList(new SAMSequenceRecord("seq1", 100))
        );
        final Map<String, byte[]> seqs = Collections.singletonMap("seq1", SequenceUtil.getRandomBases(new Random(1341), 100));
        final Map<String, Integer> bpls = Collections.singletonMap("seq1", -1);
        try (final OutputStream testOutputStream = new FileOutputStream(testOutputFile);
             final OutputStream testIndexOutputStream = new FileOutputStream(testIndexOutputFile);
             final OutputStream testDictOutputStream = new FileOutputStream(testDictOutputFile)) {
            try (final FastaReferenceWriter writer = new FastaReferenceWriterBuilder().setBasesPerLine(50).setFastaOutput(testOutputStream).setIndexOutput(testIndexOutputStream).setDictOutput(testDictOutputStream).build()) {
                writer.startSequence("seq1");
                writer.appendBases(seqs.get("seq1"));
            }
        }
        assertFastaContent(testOutputFile.toPath(), false, testDictionary, 50, seqs, bpls);
        assertFastaIndexContent(testOutputFile.toPath(), testIndexOutputFile.toPath(), testDictionary, seqs);
        assertFastaDictionaryContent(testDictOutputFile.toPath(), testDictionary);
        Assert.assertTrue(testOutputFile.delete());
        Assert.assertTrue(testIndexOutputFile.delete());
        Assert.assertTrue(testDictOutputFile.delete());
    }

    private void generateRandomBasesAndBpls(SAMSequenceDictionary dictionary, int minBpl, int maxBpl, Map<String, byte[]> bases, Map<String, Integer> bpl, Random rdn) {
        final Random random = new Random(rdn.nextLong());
        // We avoid to use the obvious first choice {@link RandomDNA#nextFasta} as these may actually use
        // this writer to do its job eventually.
        for (final SAMSequenceRecord sequence : dictionary.getSequences()) {
            bases.put(sequence.getSequenceName(), SequenceUtil.getRandomBases(random, sequence.getSequenceLength()));
            if (rdn.nextDouble() < 0.333333) { // 1/3 of the time we will use the default.
                bpl.put(sequence.getSequenceName(), -1);
            } else {
                bpl.put(sequence.getSequenceName(), rdn.nextInt(maxBpl - minBpl + 1) + minBpl);
            }
        }
    }

    private void writeReference(final FastaReferenceWriter writer, final boolean withDescriptions,
                                final Random rdn, final SAMSequenceDictionary dictionary,
                                final Map<String, byte[]> seqs,
                                final Map<String, Integer> basesPerLine)
            throws IOException {
        for (final SAMSequenceRecord sequence : dictionary.getSequences()) {
            final int bpl = basesPerLine.get(sequence.getSequenceName());
            final boolean onOneGo = rdn.nextDouble() < 0.25; // 25% of times we just write the whole sequence of one go.
            final boolean useAppendSequence = onOneGo && rdn.nextBoolean();
            if (withDescriptions) {
                final String description = String.format("index=%d\tlength=%d",
                        dictionary.getSequenceIndex(sequence.getSequenceName()),
                        sequence.getSequenceLength());
                if (bpl < 0) {
                    if (useAppendSequence) {
                        Assert.assertSame(writer.appendSequence(sequence.getSequenceName(), description, seqs.get(sequence.getSequenceName())), writer);
                    } else {
                        Assert.assertSame(writer.startSequence(sequence.getSequenceName(), description), writer);
                    }
                } else {
                    if (useAppendSequence) {
                        Assert.assertSame(writer.appendSequence(sequence.getSequenceName(), description, bpl, seqs.get(sequence.getSequenceName())), writer);
                    } else {
                        Assert.assertSame(writer.startSequence(sequence.getSequenceName(), description, bpl), writer);
                    }
                }
            } else {
                if (bpl < 0) {
                    if (useAppendSequence) {
                        Assert.assertSame(writer.appendSequence(sequence.getSequenceName(), seqs.get(sequence.getSequenceName())), writer);
                    } else {
                        Assert.assertSame(writer.startSequence(sequence.getSequenceName()), writer);
                    }
                } else {
                    if (useAppendSequence) {
                        Assert.assertSame(writer.appendSequence(sequence.getSequenceName(), null, bpl, seqs.get(sequence.getSequenceName())), writer);
                    } else {
                        Assert.assertSame(writer.startSequence(sequence.getSequenceName(), bpl), writer);
                    }
                }
            }
            if (useAppendSequence) {
                // added already.
            } else if (onOneGo) {
                Assert.assertSame(writer.appendBases(seqs.get(sequence.getSequenceName())), writer);
            } else {
                int done = 0;
                while (done < seqs.get(sequence.getSequenceName()).length) {
                    final boolean useBpl = bpl > 0 && rdn.nextDouble() < 0.10; // 10% of times we exactly add the same number of bases as bases-per-line, remaining bases permitting.
                    int left = sequence.getSequenceLength() - done;
                    final int length = useBpl ? Math.min(bpl, left) : rdn.nextInt(left) + 1;
                    Assert.assertSame(writer.appendBases(seqs.get(sequence.getSequenceName()), done, length), writer);
                    done += length;
                    left -= length;
                    if (useBpl && rdn.nextDouble() < 0.10) { // 10% of the time we align with bpl so that it will start a new line on the next write.
                        final int lengthToEndOfLine = Math.min(left, bpl - (done % bpl));
                        Assert.assertSame(writer.appendBases(seqs.get(sequence.getSequenceName()), done, lengthToEndOfLine), writer);
                        done += lengthToEndOfLine;
                    }
                    if (rdn.nextDouble() < 0.10) { // 10% of the time we do a stupid zero length append.
                        Assert.assertSame(writer.appendBases(seqs.get(sequence.getSequenceName()), done, 0), writer);
                    }
                }
            }
        }
    }

    private void assertOutput(final Path path, final boolean mustHaveIndex, final boolean mustHaveDictionary,
                              final boolean withDescriptions, final SAMSequenceDictionary dictionary, final int defaultBpl,
                              final Map<String, byte[]> bases, final Map<String, Integer> basesPerLine)
            throws GeneralSecurityException, IOException, URISyntaxException {
        assertFastaContent(path, withDescriptions, dictionary, defaultBpl, bases, basesPerLine);
        if (mustHaveDictionary) {
            assertFastaDictionaryContent(ReferenceSequenceFileFactory.getDefaultDictionaryForReferenceSequence(path), dictionary);
        }
        if (mustHaveIndex) {
            assertFastaIndexContent(path, ReferenceSequenceFileFactory.getFastaIndexFileName(path), dictionary, bases);
        }
    }

    private void assertFastaContent(final Path path, final boolean withDescriptions, final SAMSequenceDictionary dictionary, final int defaultBpl,
                                    final Map<String, byte[]> bases, final Map<String, Integer> basesPerLine)
            throws IOException {
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(path.getFileSystem().provider().newInputStream(path)))) {
            for (final SAMSequenceRecord sequence : dictionary.getSequences()) {
                final String description = String.format("index=%d\tlength=%d",
                        dictionary.getSequenceIndex(sequence.getSequenceName()), sequence.getSequenceLength());
                final String expectedHeader =
                        FastaReferenceWriter.HEADER_START_CHAR + sequence.getSequenceName()
                                + ((withDescriptions) ? FastaReferenceWriter.HEADER_NAME_AND_DESCRIPTION_SEPARATOR + description : "");
                Assert.assertEquals(reader.readLine(), expectedHeader);
                final byte[] expectedBases = bases.get(sequence.getSequenceName());
                final int bpl_ = basesPerLine.get(sequence.getSequenceName());
                final int bpl = bpl_ < 0 ? (defaultBpl < 0 ? FastaReferenceWriter.DEFAULT_BASES_PER_LINE : defaultBpl) : bpl_;
                int offset = 0;
                while (offset < expectedBases.length) {
                    final int expectedLength = Math.min(expectedBases.length - offset, bpl);
                    final byte[] expectedBaseLine = SequenceUtil.upperCase(Arrays.copyOfRange(expectedBases, offset, offset + expectedLength));
                    final byte[] actualBaseLine = SequenceUtil.upperCase(reader.readLine().getBytes());
                    Assert.assertEquals(actualBaseLine, expectedBaseLine);
                    offset += expectedLength;
                }
            }
        }
    }

    private void assertFastaIndexContent(final Path path, final Path indexPath, final SAMSequenceDictionary dictionary,
                                         final Map<String, byte[]> bases)
            throws IOException {
        final FastaSequenceIndex index = new FastaSequenceIndex(indexPath);
        final IndexedFastaSequenceFile indexedFasta = new IndexedFastaSequenceFile(path, index);
        for (final SAMSequenceRecord sequence : dictionary.getSequences()) {
            final String name = sequence.getSequenceName();
            final int length = sequence.getSequenceLength();
            final ReferenceSequence start = indexedFasta.getSubsequenceAt(name, 1, Math.min(length, 30));
            final ReferenceSequence end = indexedFasta.getSubsequenceAt(name, Math.max(1, length - 29), length);
            final int middlePos = Math.max(1, Math.min(length, length / 2));
            final ReferenceSequence middle = indexedFasta.getSubsequenceAt(name, middlePos, Math.min(middlePos + 29, length));
            Assert.assertEquals(start.getBases(), Arrays.copyOfRange(bases.get(name), 0, start.length()));
            Assert.assertEquals(end.getBases(), Arrays.copyOfRange(bases.get(name), Math.max(0, length - 30), length));
            Assert.assertEquals(middle.getBases(), Arrays.copyOfRange(bases.get(name), middlePos - 1, middlePos - 1 + middle.length()));
        }
    }

    private void assertFastaDictionaryContent(final Path dictPath, final SAMSequenceDictionary dictionary)
            throws IOException, GeneralSecurityException, URISyntaxException {
        final SAMSequenceDictionary actualDictionary = SAMSequenceDictionaryExtractor.extractDictionary(dictPath);
        dictionary.assertSameDictionary(actualDictionary);
    }

    @DataProvider(name = "testData")
    public Object[][] testData() {
        // data-signature: (SAMSequenceDictionary dictionary, boolean withDescriptions, int defaultBpl, int minBpl, int maxBpl, int seed
        // defaultBpl == -1 means to use the default {@link FastaReferenceWriter#DEFAULT_BASE_PER_LINE}.
        // [minBpl , manBpl] range for possible bpl when the default for the file is not to be used.
        final Random rdn = new Random(113);
        final SAMSequenceDictionary typicalDictionary = new SAMSequenceDictionary(
                Arrays.asList(new SAMSequenceRecord("chr1", 10_000),
                        new SAMSequenceRecord("chr2", 20_000),
                        new SAMSequenceRecord("chr3", 20_000),
                        new SAMSequenceRecord("chr4", 2_000),
                        new SAMSequenceRecord("chr5", 200),
                        new SAMSequenceRecord("chr6", 3_010),
                        new SAMSequenceRecord("X", 1_000)
                ));

        final SAMSequenceDictionary manyBPLMatchingSequences = new SAMSequenceDictionary(
                IntStream.range(0, 100)
                        .mapToObj(i -> new SAMSequenceRecord("" + (i + 1), FastaReferenceWriter.DEFAULT_BASES_PER_LINE * (rdn.nextInt(10) + 1)))
                        .collect(Collectors.toList())
        );

        final SAMSequenceDictionary singleSequence = new SAMSequenceDictionary(Collections.singletonList(new SAMSequenceRecord("seq", 2_000)));

        final SAMSequenceDictionary oneBaseSequencesContaining = new SAMSequenceDictionary(Arrays.asList(new SAMSequenceRecord("chr1", 10_000),
                new SAMSequenceRecord("chr2", 20_000),
                new SAMSequenceRecord("chr2.small", 1),
                new SAMSequenceRecord("X", 1_000),
                new SAMSequenceRecord("MT", 1))
        );

        final SAMSequenceDictionary[] testDictionaries = new SAMSequenceDictionary[]{typicalDictionary, manyBPLMatchingSequences, singleSequence, oneBaseSequencesContaining};
        final int[] testBpls = new int[]{-1, FastaReferenceWriter.DEFAULT_BASES_PER_LINE, 1, 100, 51, 63};
        final boolean[] testWithDescriptions = new boolean[]{true, false};
        final boolean[] testWithIndex = new boolean[]{true, false};
        final boolean[] testWithDictionary = new boolean[]{true, false};
        final int[] testSeeds = new int[]{31, 113, 73};
        final List<Object[]> result = new ArrayList<>();
        for (final SAMSequenceDictionary dictionary : testDictionaries) {
            for (final boolean withIndex : testWithIndex) {
                for (final boolean withDictionary : testWithDictionary) {
                    for (final boolean withDescriptions : testWithDescriptions) {
                        for (final int bpl : testBpls) {
                            for (final int seed : testSeeds) {
                                result.add(new Object[]{dictionary, withIndex, withDictionary, withDescriptions, bpl, 1, (bpl < 0 ? FastaReferenceWriter.DEFAULT_BASES_PER_LINE : bpl) * 2, seed});
                            }
                        }
                    }
                }
            }
        }
        return result.toArray(new Object[result.size()][]);
    }
}
