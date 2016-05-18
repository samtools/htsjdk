/*
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
 */
package htsjdk.samtools;

import htsjdk.samtools.util.SequenceUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests for MultiIterator
 *
 * @author Dave Tefft
 */
public class MergingSamRecordIteratorTest {

    @Test
    public void testVanillaCoordinateMultiIterator() throws Exception {
        final SAMRecordSetBuilder builder1 = new SAMRecordSetBuilder();
        builder1.addFrag("read_28833_29006_6945", 20, 28833, false); // ok
        builder1.addFrag("read_28701_28881_323b", 22, 28834, false); // ok

        final SamReader samReader = builder1.getSamReader();
        samReader.getFileHeader().setSortOrder(SAMFileHeader.SortOrder.coordinate);

        final SAMRecordSetBuilder builder2 = new SAMRecordSetBuilder();
        builder2.addFrag("read_28833_29006_6945", 20, 30000, false); // ok
        builder2.addFrag("read_28701_28881_323b", 22, 28835, false); // ok
        builder2.addFrag("read_28701_28881_323c", 22, 28835, false); // ok

        final SamReader samReader2 = builder2.getSamReader();
        samReader2.getFileHeader().setSortOrder(SAMFileHeader.SortOrder.coordinate);


        final List<SamReader> readerList = new ArrayList<SamReader>();
        readerList.add(samReader);
        readerList.add(samReader2);

        final List<SAMFileHeader> headerList = new ArrayList<SAMFileHeader>();
        headerList.add(samReader.getFileHeader());
        headerList.add(samReader2.getFileHeader());

        final SamFileHeaderMerger fileHeaderMerger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.coordinate, headerList, false);

        final MergingSamRecordIterator iterator = new MergingSamRecordIterator(fileHeaderMerger, readerList, false);


        int i = 0;

        // This is the correct order for start bases.  The first two are on chr20, the next three on chr22
        final int[] startBasesInOrder = {28833, 30000, 28834, 28835, 28835};

        while (iterator.hasNext()) {
            final SAMRecord rec = iterator.next();
            System.out.println(rec.getSAMString());
            Assert.assertEquals(rec.getAlignmentStart(), startBasesInOrder[i]);
            i++;
        }
        samReader.close();
        samReader2.close();
    }

    @Test
    public void testVanillaReadOrderMultiIterator() throws Exception {
        final SAMRecordSetBuilder builder1 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.queryname);
        builder1.addFrag("a", 20, 28833, false); // ok
        builder1.addFrag("e", 19, 28834, false); // ok

        final SamReader samReader = builder1.getSamReader();

        final SAMRecordSetBuilder builder2 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.queryname);
        builder2.addFrag("b", 20, 30000, false); // ok
        builder2.addFrag("c", 22, 28835, false); // ok
        builder2.addFrag("d", 20, 28835, false); // ok

        final SamReader samReader2 = builder2.getSamReader();


        final List<SamReader> readerList = new ArrayList<SamReader>();
        readerList.add(samReader);
        readerList.add(samReader2);

        final List<SAMFileHeader> headerList = new ArrayList<SAMFileHeader>();
        headerList.add(samReader.getFileHeader());
        headerList.add(samReader2.getFileHeader());

        final SamFileHeaderMerger fileHeaderMerger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.queryname, headerList, false);

        final MergingSamRecordIterator iterator = new MergingSamRecordIterator(fileHeaderMerger, readerList, false);


        int i = 0;

        // This is the correct order for start bases.  The first two are on chr20, the next three on chr22
        final String[] orderedReadNames = {"a", "b", "c", "d", "e"};

        while (iterator.hasNext()) {
            final SAMRecord rec = iterator.next();
            System.out.println(rec.getReadName());
            Assert.assertEquals(rec.getReadName(), orderedReadNames[i]);
            i++;
        }
        samReader.close();
        samReader2.close();
    }

    @Test
    public void testVanillaUnsortedMultiIterator() throws Exception {
        final SAMRecordSetBuilder builder1 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.unsorted);
        builder1.addFrag("b", 20, 28833, false); // ok
        builder1.addFrag("a", 19, 28834, false); // ok

        final SamReader samReader = builder1.getSamReader();

        final SAMRecordSetBuilder builder2 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.unsorted);
        builder2.addFrag("d", 20, 30000, false); // ok
        builder2.addFrag("e", 22, 28835, false); // ok
        builder2.addFrag("c", 20, 28835, false); // ok

        final SamReader samReader2 = builder2.getSamReader();


        final List<SamReader> readerList = new ArrayList<SamReader>();
        readerList.add(samReader);
        readerList.add(samReader2);

        final List<SAMFileHeader> headerList = new ArrayList<SAMFileHeader>();
        headerList.add(samReader.getFileHeader());
        headerList.add(samReader2.getFileHeader());

        final SamFileHeaderMerger fileHeaderMerger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.unsorted, headerList, false);

        final MergingSamRecordIterator iterator = new MergingSamRecordIterator(fileHeaderMerger, readerList, false);


        int i = 0;

        // With unsorted option there is no garantee that order of the names to come back from the iterator
        final String[] readNames = {"b", "a", "d", "e", "c"};

        while (iterator.hasNext()) {
            final SAMRecord rec = iterator.next();
            System.out.println(rec.getReadName());
            i++;
        }
        Assert.assertEquals(i, readNames.length);
        samReader.close();
        samReader2.close();
    }

    @Test(expectedExceptions = SequenceUtil.SequenceListsDifferException.class)
    public void testConflictingHeaders() throws Exception {
        final SAMRecordSetBuilder builder1 = new SAMRecordSetBuilder();
        builder1.addFrag("read_28833_29006_6945", 20, 28833, false); // ok
        builder1.addFrag("read_28701_28881_323b", 22, 28834, false); // ok

        final SamReader samReader = builder1.getSamReader();
        samReader.getFileHeader().setSortOrder(SAMFileHeader.SortOrder.coordinate);

        final SAMRecordSetBuilder builder2 = new SAMRecordSetBuilder();
        builder2.addFrag("read_28833_29006_6945", 20, 30000, false); // ok
        builder2.addFrag("read_28701_28881_323b", 22, 28835, false); // ok
        builder2.addFrag("read_28701_28881_323c", 22, 28835, false); // ok

        final SamReader samReader2 = builder2.getSamReader();
        samReader2.getFileHeader().setSortOrder(SAMFileHeader.SortOrder.coordinate);

        //Change one of the header so they are no longer compatible
        final SAMSequenceRecord sRec = new SAMSequenceRecord("BADSEQ", 0);
        samReader2.getFileHeader().addSequence(sRec);


        final List<SamReader> readerList = new ArrayList<SamReader>();
        readerList.add(samReader);
        readerList.add(samReader2);

        final List<SAMFileHeader> headerList = new ArrayList<SAMFileHeader>();
        headerList.add(samReader.getFileHeader());
        headerList.add(samReader2.getFileHeader());

        final SamFileHeaderMerger samFileHeaderMerger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.coordinate, headerList, false);

        new MergingSamRecordIterator(samFileHeaderMerger, readerList, false);
        Assert.fail("This method should throw exception before getting to this point");
    }


    @Test(expectedExceptions = SAMException.class)
    public void filesNotSortedCorrectly() throws Exception {
        final SAMRecordSetBuilder builder1 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.coordinate);
        builder1.addFrag("read_28833_29006_6945", 20, 28833, false); // ok
        builder1.addFrag("read_28701_28881_323b", 22, 28834, false); // ok

        final SamReader samReader = builder1.getSamReader();

        final SAMRecordSetBuilder builder2 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.unsorted);

        final SamReader samReader2 = builder2.getSamReader();

        builder2.addFrag("read_28701_28881_323b", 22, 28835, false); // ok
        builder2.addFrag("read_28833_29006_6945", 20, 30000, false); // ok
        builder2.addFrag("read_28701_28881_323c", 22, 28835, false); // ok

        final List<SamReader> readerList = new ArrayList<SamReader>();
        readerList.add(samReader);
        readerList.add(samReader2);

        final List<SAMFileHeader> headerList = new ArrayList<SAMFileHeader>();
        headerList.add(samReader.getFileHeader());
        headerList.add(samReader2.getFileHeader());

        final SamFileHeaderMerger fileHeaderMerger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.coordinate, headerList, false);

        new MergingSamRecordIterator(fileHeaderMerger, readerList, false);
        Assert.fail("This method should throw exception before getting to this point");
    }

    @Test
    public void testHeaderCommentMerge() throws Exception {
        final String[] comments1 = {"@CO\tHi, Mom!", "@CO\tHi, Dad!"};
        final String[] comments2 = {"@CO\tHello, World!", "@CO\tGoodbye, Cruel World!"};
        final Set<String> bothComments = new HashSet<String>();
        bothComments.addAll(Arrays.asList(comments1));
        bothComments.addAll(Arrays.asList(comments2));
        final SAMRecordSetBuilder builder1 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.coordinate);
        SAMFileHeader header = builder1.getHeader();
        for (final String comment : comments1) {
            header.addComment(comment);
        }
        final SAMRecordSetBuilder builder2 = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.coordinate);
        header = builder2.getHeader();
        for (final String comment : comments2) {
            header.addComment(comment);
        }
        final SamFileHeaderMerger merger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.coordinate,
                Arrays.asList(builder1.getSamReader().getFileHeader(), builder2.getSamReader().getFileHeader()), false);
        final List<String> mergedComments = merger.getMergedHeader().getComments();
        Assert.assertEquals(mergedComments.size(), bothComments.size());
        for (final String comment : mergedComments) {
            Assert.assertTrue(bothComments.contains(comment));
        }
        builder1.getSamReader().close();
        builder2.getSamReader().close();
    }

    @Test
    public void testReferenceIndexMapping() throws Exception {
        // Create two SamReaders with sequence dictionaries such that a merging iterator with merged
        // headers will require remapping a record's reference index to the merged dictionary
        final SAMRecordSetBuilder builder1 = new SAMRecordSetBuilder();
        SAMSequenceRecord fakeSequenceRec = new SAMSequenceRecord("FAKE_CONTIG_A", 0);
        builder1.getHeader().addSequence(fakeSequenceRec);

        final SAMRecordSetBuilder builder2 = new SAMRecordSetBuilder();
        fakeSequenceRec = new SAMSequenceRecord("FAKE_CONTIG_B", 0);
        builder2.getHeader().addSequence(fakeSequenceRec);

        // create a record with a reference index that will need to be remapped after merging
        SAMRecord recRequiresMapping = new SAMRecord(builder2.getHeader());
        recRequiresMapping.setReadName("fakeread");
        recRequiresMapping.setReferenceName("FAKE_CONTIG_B");
        builder2.addRecord(recRequiresMapping);
        // cache the original reference index
        int originalRefIndex = recRequiresMapping.getReferenceIndex();
        Assert.assertTrue(25 == originalRefIndex);

        // get a merging iterator with a merged header
        final SamReader samReader1 = builder1.getSamReader();
        final SamReader samReader2 = builder2.getSamReader();
        final List<SamReader> readerList = new ArrayList<SamReader>();
        readerList.add(samReader1);
        readerList.add(samReader2);
        final List<SAMFileHeader> headerList = new ArrayList<SAMFileHeader>();
        headerList.add(samReader1.getFileHeader());
        headerList.add(samReader2.getFileHeader());
        final SamFileHeaderMerger samFileHeaderMerger = new SamFileHeaderMerger(SAMFileHeader.SortOrder.coordinate, headerList, true);
        final MergingSamRecordIterator iterator = new MergingSamRecordIterator(samFileHeaderMerger, readerList, false);

        Assert.assertTrue(iterator.hasNext());
        final SAMRecord rec = iterator.next();
        Assert.assertTrue(26  == rec.getReferenceIndex());

        samReader1.close();
        samReader2.close();
    }
}
