/**
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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
 **/


package htsjdk.samtools;

import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.samtools.util.StringUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;


/**
 * @author aaron
 * @version 1.0
 * @date May 20, 2009
 * <p/>
 * Class SamFileHeaderMergerTest
 * <p/>
 * Tests the ability of the SamFileHeaderMerger class to merge sequence dictionaries.
 */
public class SamFileHeaderMergerTest {

    private static File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools");

    /** tests that if we've set the merging to false, we get a SAMException for bam's with different dictionaries. */
    @Test(expectedExceptions = SequenceUtil.SequenceListsDifferException.class)
    public void testMergedException() {
        File INPUT[] = {new File(TEST_DATA_DIR, "SamFileHeaderMergerTest/Chromosome1to10.bam"),
                new File(TEST_DATA_DIR, "SamFileHeaderMergerTest/Chromosome5to9.bam")};
        final List<SAMFileHeader> headers = new ArrayList<SAMFileHeader>();
        for (final File inFile : INPUT) {
            IOUtil.assertFileIsReadable(inFile);
            headers.add(SamReaderFactory.makeDefault().getFileHeader(inFile));
        }
        new SamFileHeaderMerger(SAMFileHeader.SortOrder.unsorted, headers, false);
    }

    /** Tests that we can successfully merge two files with */
    @Test
    public void testMerging() {
        File INPUT[] = {new File(TEST_DATA_DIR, "SamFileHeaderMergerTest/Chromosome1to10.bam"),
                new File(TEST_DATA_DIR, "SamFileHeaderMergerTest/Chromosome5to9.bam")};
        final List<SamReader> readers = new ArrayList<SamReader>();
        final List<SAMFileHeader> headers = new ArrayList<SAMFileHeader>();
        for (final File inFile : INPUT) {
            IOUtil.assertFileIsReadable(inFile);
            // We are now checking for zero-length reads, so suppress complaint about that.
            final SamReader in = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT).open(inFile);

            readers.add(in);
            headers.add(in.getFileHeader());
        }
        final MergingSamRecordIterator iterator;
        final SamFileHeaderMerger headerMerger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.unsorted, headers, true);
        iterator = new MergingSamRecordIterator(headerMerger, readers, false);
        headerMerger.getMergedHeader();

        // count the total reads, and record read counts for each sequence
        Map<Integer, Integer> seqCounts = new HashMap<Integer, Integer>();
        int totalCount = 0;

        while (iterator.hasNext()) {
            SAMRecord r = iterator.next();
            if (seqCounts.containsKey(r.getReferenceIndex())) {
                seqCounts.put(r.getReferenceIndex(), seqCounts.get(r.getReferenceIndex()) + 1);
            } else {
                seqCounts.put(r.getReferenceIndex(), 1);
            }
            ++totalCount;
        }
        assertEquals(totalCount, 1500);
        for (Integer i : seqCounts.keySet()) {
            if (i < 4 || i > 8) {
                // seqeunce 5 - 9 should have 200 reads (indices 4 - 8)
                assertEquals(seqCounts.get(i).intValue(), 100);
            } else {
                // the others should have 100
                assertEquals(seqCounts.get(i).intValue(), 200);
            }
        }
        CloserUtil.close(readers);
    }

    private static final String sq1 = "@SQ\tSN:chr1\tLN:1000\n";
    private static final String sq2 = "@SQ\tSN:chr2\tLN:1000\n";
    private static final String sq3 = "@SQ\tSN:chr3\tLN:1000\n";
    private static final String sq4 = "@SQ\tSN:chr4\tLN:1000\n";
    private static final String sq5 = "@SQ\tSN:chr5\tLN:1000\n";

    @Test
    public void testSequenceDictionaryMerge() {
        final String sd1 = sq1 + sq2 + sq5;
        final String sd2 = sq2 + sq3 + sq4;
        SamReader reader1 = SamReaderFactory.makeDefault().open(SamInputResource.of(new ByteArrayInputStream(StringUtil.stringToBytes(sd1))));
        SamReader reader2 = SamReaderFactory.makeDefault().open(SamInputResource.of(new ByteArrayInputStream(StringUtil.stringToBytes(sd2))));
        final List<SAMFileHeader> inputHeaders = Arrays.asList(reader1.getFileHeader(), reader2.getFileHeader());
        SamFileHeaderMerger merger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.coordinate, inputHeaders, true);
        final SAMFileHeader mergedHeader = merger.getMergedHeader();
        for (final SAMFileHeader inputHeader : inputHeaders) {
            int prevTargetIndex = -1;
            for (final SAMSequenceRecord sequenceRecord : inputHeader.getSequenceDictionary().getSequences()) {
                final int targetIndex = mergedHeader.getSequenceIndex(sequenceRecord.getSequenceName());
                Assert.assertNotSame(targetIndex, -1);
                Assert.assertTrue(prevTargetIndex < targetIndex);
                prevTargetIndex = targetIndex;
            }
        }
        CloserUtil.close(reader1);
        CloserUtil.close(reader2);
    }

    @Test(dataProvider = "data")
    public void testProgramGroupAndReadGroupMerge(File inputFiles[], File expectedOutputFile) throws IOException {

        BufferedReader reader = new BufferedReader(new FileReader(expectedOutputFile));

        String line;
        String expected_output = "";
        while ((line = reader.readLine()) != null) {
            expected_output += line + "\n";
        }

        final List<SamReader> readers = new ArrayList<SamReader>();
        final List<SAMFileHeader> headers = new ArrayList<SAMFileHeader>();
        for (final File inFile : inputFiles) {
            IOUtil.assertFileIsReadable(inFile);

            // We are now checking for zero-length reads, so suppress complaint about that.
            final SamReader in = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT).open(inFile);
            readers.add(in);
            headers.add(in.getFileHeader());
        }
        final MergingSamRecordIterator iterator;

        final SamFileHeaderMerger headerMerger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.coordinate, headers, true);
        iterator = new MergingSamRecordIterator(headerMerger, readers, false);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SAMFileWriter writer = new SAMFileWriterFactory().makeSAMWriter(headerMerger.getMergedHeader(), true, baos);
        while (iterator.hasNext()) {
            writer.addAlignment(iterator.next());
        }
        writer.close();

        String actual_output = StringUtil.bytesToString(baos.toByteArray());

        List<String> actual = Arrays.asList(actual_output.split("\\n"));
        List<String> expected = Arrays.asList(expected_output.split("\\n"));
        for (int i = 0; i < expected.size(); i++) {
            if (expected.get(i).startsWith("@")) {
                Assert.assertTrue(headersEquivalent(actual.get(i), expected.get(i)));
            } else {
                List<String> expectedSamParts = Arrays.asList(expected.get(i).split("\\s*"));
                List<String> actualSamParts = Arrays.asList(actual.get(i).split("\\s*"));
                for (String exp : expectedSamParts) {
                    Assert.assertTrue(actualSamParts.contains(exp));
                }
                for (String act : actualSamParts) {
                    Assert.assertTrue(expectedSamParts.contains(act));
                }
            }
        }
        CloserUtil.close(readers);
    }

    private static final boolean headersEquivalent(String a, String b) {
        if (a.length() != b.length()) return false;
        List<String> remaining = new LinkedList<String>(Arrays.asList(a.split("\\t")));
        for (final String item : b.split("\\t")) {
            if (!remaining.remove(item)) return false;
        }
        return remaining.isEmpty(); 
    }

    @DataProvider(name = "data")
    private Object[][] getProgramGroupAndReadGroupMergeData() {

        return new Object[][]{
                {

                        new File[]{
                                new File(TEST_DATA_DIR, "SamFileHeaderMergerTest/case1/chr11sub_file1.sam"),
                                new File(TEST_DATA_DIR, "SamFileHeaderMergerTest/case1/chr11sub_file2.sam")},
                        new File(TEST_DATA_DIR, "SamFileHeaderMergerTest/case1/expected_output.sam")
                }, {
                new File[]{
                        new File(TEST_DATA_DIR, "SamFileHeaderMergerTest/case2/chr11sub_file1.sam"),
                        new File(TEST_DATA_DIR, "SamFileHeaderMergerTest/case2/chr11sub_file2.sam"),
                        new File(TEST_DATA_DIR, "SamFileHeaderMergerTest/case2/chr11sub_file3.sam"),
                        new File(TEST_DATA_DIR, "SamFileHeaderMergerTest/case2/chr11sub_file4.sam")},
                new File(TEST_DATA_DIR, "SamFileHeaderMergerTest/case2/expected_output.sam")
        }
        };
    }

    @Test(expectedExceptions = {SAMException.class})
    public void testUnmergeableSequenceDictionary() {
        final String sd1 = sq1 + sq2 + sq5;
        final String sd2 = sq2 + sq3 + sq4 + sq1;
        final SamReader reader1 = SamReaderFactory.makeDefault().open(SamInputResource.of(new ByteArrayInputStream(StringUtil.stringToBytes(sd1))));
        final SamReader reader2 = SamReaderFactory.makeDefault().open(SamInputResource.of(new ByteArrayInputStream(StringUtil.stringToBytes(sd2))));
        final List<SAMFileHeader> inputHeaders = Arrays.asList(reader1.getFileHeader(), reader2.getFileHeader());
        new SamFileHeaderMerger(SAMFileHeader.SortOrder.coordinate, inputHeaders, true);
        CloserUtil.close(reader1);
        CloserUtil.close(reader2);
    }

    @DataProvider(name = "fourDigitBase36StrPositiveData")
    public Object[][] positiveFourDigitBase36StrData() {
        return new Object[][]{
                {0, "0"},
                {15, "F"},
                {36, "10"},
                {1200000, "PPXC"},
                {36 * 36 * 36 * 36 - 2, "ZZZY"},
                {36 * 36 * 36 * 36 - 1, "ZZZZ"},
        };
    }

    @Test(dataProvider = "fourDigitBase36StrPositiveData")
    public void fourDigitBase36StrPositiveTest(final int toConvert, final String expectedValue) {
        final SamFileHeaderMerger headerMerger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.coordinate, new ArrayList<SAMFileHeader>(), true);
        Assert.assertEquals(expectedValue, headerMerger.positiveFourDigitBase36Str(toConvert));
    }
}
