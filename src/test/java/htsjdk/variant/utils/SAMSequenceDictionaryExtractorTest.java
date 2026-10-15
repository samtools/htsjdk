/*
 * The MIT License
 *
 * Copyright (c) 2014 The Broad Institute
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
package htsjdk.variant.utils;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.testutil.LogCapture;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author farjoun on 4/9/14.
 */
public class SAMSequenceDictionaryExtractorTest extends HtsjdkTest {
    final String path = "src/test/resources/htsjdk/variant/utils/SamSequenceDictionaryExtractor/";

    @DataProvider(name = "testExtractDictionaries")
    public Object[][] dictionaries() {
        return new Object[][] {
            new Object[] {"test1_comp.interval_list", "test1.dict"},
            new Object[] {"test1.vcf", "test1.dict"},
            new Object[] {"test1.dict", "test1.dict"},
            new Object[] {"empty.interval_list", "test1.dict"},
            new Object[] {"Homo_sapiens_assembly18.trimmed.fasta", "Homo_sapiens_assembly18.trimmed.dict"},
            new Object[] {"test2_comp.interval_list", "Homo_sapiens_assembly18.trimmed.dict"},
            new Object[] {"test5_comp.interval_list.gz", "test1.dict"},
            new Object[] {"ScreenSamReads.100.input.sam", "test3_comp.interval_list"},
            new Object[] {"ScreenSamReads.100.input.sam", "test4_comp.interval_list"},
            new Object[] {"toy.cram", "toy.dict"}
        };
    }

    @Test(dataProvider = "testExtractDictionaries")
    public void testExtractDictionary(final String dictSource, final String dictExpected) throws Exception {
        final Path dictSourceFile = Paths.get(path, dictSource);
        final Path dictExpectedFile = Paths.get(path, dictExpected);
        final SAMSequenceDictionary dict1 = SAMSequenceDictionaryExtractor.extractDictionary(dictSourceFile);
        final SAMSequenceDictionary dict2 = SAMSequenceDictionaryExtractor.extractDictionary(dictExpectedFile);

        Assert.assertTrue(SequenceUtil.areSequenceDictionariesEqual(dict1, dict2));
        Assert.assertTrue(dict1.md5().equals(dict2.md5()));
    }

    @Test
    public void testExtractingTheDictionaryOfAFastaLeavesTheFastaClosed() throws IOException {
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path fasta = fs.getPath("/ref.fasta");
            Files.writeString(fasta, ">chr1\nACGTACGT\n");
            Files.writeString(fs.getPath("/ref.fasta.fai"), "chr1\t8\t6\t8\t9\n");
            Files.writeString(fs.getPath("/ref.dict"), "@HD\tVN:1.6\n@SQ\tSN:chr1\tLN:8\n");
            final FileStore store = Files.getFileStore(fasta);
            final long unallocatedWithTheFasta = store.getUnallocatedSpace();

            final SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(fasta);
            Assert.assertEquals(dict.getSequence("chr1").getSequenceLength(), 8);

            // Jimfs, as a Unix file system does, keeps the storage of a deleted file until it is no longer open
            Files.delete(fasta);
            Assert.assertTrue(store.getUnallocatedSpace() > unallocatedWithTheFasta);
        }
    }

    /** Writes {@code ref.dict} into {@code dir}, with one sequence of length 100 for each of {@code sequenceNames}. */
    private static Path writeDict(final Path dir, final String... sequenceNames) throws IOException {
        final StringBuilder text = new StringBuilder("@HD\tVN:1.6\n");
        for (final String sequenceName : sequenceNames) {
            text.append("@SQ\tSN:").append(sequenceName).append("\tLN:100\n");
        }
        return Files.writeString(dir.resolve("ref.dict"), text);
    }

    private static List<String> namesOf(final SAMSequenceDictionary dictionary) {
        return dictionary.getSequences().stream()
                .map(SAMSequenceRecord::getSequenceName)
                .collect(Collectors.toList());
    }

    @Test
    public void aDictWithNamesOutsideTheRnamePatternIsExtractedWithOneWarning() throws Exception {
        final String tag = UUID.randomUUID().toString();
        final String parenthesised = "Gt(ROSA)26Sor-" + tag;
        final String bracketed = "a[1]-" + tag;
        final Path dir = Files.createTempDirectory("SAMSequenceDictionaryExtractorTest");
        try {
            final Path dict = writeDict(dir, parenthesised, "chr1", bracketed);
            final SAMSequenceDictionary[] dictionary = new SAMSequenceDictionary[1];
            final List<String> lines = LogCapture.linesLoggedContaining(
                    tag,
                    Log.LogLevel.INFO,
                    () -> dictionary[0] = SAMSequenceDictionaryExtractor.extractDictionary(dict));
            Assert.assertEquals(namesOf(dictionary[0]), List.of(parenthesised, "chr1", bracketed));
            Assert.assertEquals(lines.size(), 1, lines.toString());
            Assert.assertTrue(lines.get(0).startsWith(Log.LogLevel.WARNING.name()), lines.get(0));
            Assert.assertTrue(lines.get(0).contains("2 sequence name(s)"), lines.get(0));
            Assert.assertTrue(lines.get(0).contains(dict.toString()), lines.get(0));
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void aDictOfNamesMatchingTheRnamePatternIsExtractedWithoutAWarning() throws Exception {
        final Path dir = Files.createTempDirectory("SAMSequenceDictionaryExtractorTest");
        try {
            // Other test classes log to the captured stream too, so look only for this dictionary's names and path
            final String tag = UUID.randomUUID().toString();
            final String chr1 = "chr1-" + tag;
            final String hla = "HLA-A*01:01-" + tag;
            final Path dict = writeDict(dir, chr1, hla);
            final SAMSequenceDictionary[] dictionary = new SAMSequenceDictionary[1];
            final List<String> lines = LogCapture.linesLoggedContaining(
                    tag,
                    Log.LogLevel.INFO,
                    () -> dictionary[0] = SAMSequenceDictionaryExtractor.extractDictionary(dict));
            Assert.assertEquals(namesOf(dictionary[0]), List.of(chr1, hla));
            Assert.assertEquals(lines, List.of());
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    /** Asserts that the dictionary extracted from {@code vcf} is that of {@code test1.vcf}, which it holds. */
    private void assertHasTheDictionaryOfTest1(final Path vcf) {
        final SAMSequenceDictionary expected =
                SAMSequenceDictionaryExtractor.extractDictionary(Paths.get(path, "test1.dict"));
        final SAMSequenceDictionary actual = SAMSequenceDictionaryExtractor.extractDictionary(vcf);
        Assert.assertEquals(actual.md5(), expected.md5());
    }

    @Test
    public void aDictionaryIsExtractedFromAVcfNamedInUpperCase() throws IOException {
        final Path dir = Files.createTempDirectory("SAMSequenceDictionaryExtractorTest");
        try {
            final Path vcf = Files.copy(Paths.get(path, "test1.vcf"), dir.resolve("test1.VCF"));
            assertHasTheDictionaryOfTest1(vcf);
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void aDictionaryIsExtractedFromABlockCompressedGvcf() throws IOException {
        final Path dir = Files.createTempDirectory("SAMSequenceDictionaryExtractorTest");
        try {
            final Path gvcf = dir.resolve("test1.gvcf.gz");
            try (OutputStream out = new BlockCompressedOutputStream(gvcf)) {
                Files.copy(Paths.get(path, "test1.vcf"), out);
            }
            assertHasTheDictionaryOfTest1(gvcf);
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void anUnrecognisedNameListsTheVcfEndings() {
        final SAMException e = Assert.expectThrows(
                SAMException.class, () -> SAMSequenceDictionaryExtractor.extractDictionary(Paths.get("x.unknown")));
        Assert.assertTrue(e.getMessage().contains(".gvcf"), e.getMessage());
    }
}
