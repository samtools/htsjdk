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

package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.variant.vcf.VCFFileReader;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Tests the IntervalList class
 */
public class IntervalListTest extends HtsjdkTest {

    final SAMFileHeader fileHeader;
    final IntervalList list1, list2, list3, empty;
    static final public Path TEST_DIR = new File("src/test/resources/htsjdk/samtools/intervallist").toPath();

    public IntervalListTest() {
        fileHeader = IntervalList.fromPath(TEST_DIR.resolve("IntervalListchr123_empty.interval_list")).getHeader();
        fileHeader.setSortOrder(SAMFileHeader.SortOrder.unsorted);

        list1 = new IntervalList(fileHeader);
        list2 = new IntervalList(fileHeader);
        list3 = new IntervalList(fileHeader);
        empty = new IntervalList(fileHeader);


        list1.add(new Interval("1", 1, 100));     //de-facto: 1:1-200 1:202-300     2:100-150 2:200-300
        list1.add(new Interval("1", 101, 200));
        list1.add(new Interval("1", 202, 300));
        list1.add(new Interval("2", 200, 300));
        list1.add(new Interval("2", 100, 150));

        list2.add(new Interval("1", 50, 150));   //de-facto 1:50-150 1:301-500      2:1-150 2:250-270 2:290-400
        list2.add(new Interval("1", 301, 500));
        list2.add(new Interval("2", 1, 150));
        list2.add(new Interval("2", 250, 270));
        list2.add(new Interval("2", 290, 400));

        list3.add(new Interval("1", 25, 400));    //de-facto 1:25-400                2:200-600                            3:50-470
        list3.add(new Interval("2", 200, 600));
        list3.add(new Interval("3", 50, 470));
    }


    @Test
    public void testIntervalListFrom() throws IOException {
        final String testPath = TEST_DIR.resolve("IntervalListFromVCFTestComp.interval_list").toString();
        final IntervalList fromFileList = IntervalList.fromFile(new File(testPath));
        final IntervalList fromPathList = IntervalList.fromPath(IOUtil.getPath(testPath));
        fromFileList.getHeader().getSequenceDictionary().assertSameDictionary(fromPathList.getHeader().getSequenceDictionary());
        Assert.assertEquals(CollectionUtil.makeCollection(fromFileList.iterator()), CollectionUtil.makeCollection(fromPathList.iterator()));
    }

    @DataProvider(name = "intersectData")
    public Object[][] intersectData() {
        final IntervalList intersect123 = new IntervalList(fileHeader);
        final IntervalList intersect12 = new IntervalList(fileHeader);
        final IntervalList intersect13 = new IntervalList(fileHeader);
        final IntervalList intersect23 = new IntervalList(fileHeader);

        intersect123.add(new Interval("1", 50, 150));
        intersect123.add(new Interval("2", 250, 270));
        intersect123.add(new Interval("2", 290, 300));

        intersect12.add(new Interval("1", 50, 150));
        intersect12.add(new Interval("2", 100, 150));
        intersect12.add(new Interval("2", 250, 270));
        intersect12.add(new Interval("2", 290, 300));

        intersect13.add(new Interval("1", 25, 200));
        intersect13.add(new Interval("1", 202, 300));
        intersect13.add(new Interval("2", 200, 300));

        intersect23.add(new Interval("1", 50, 150));
        intersect23.add(new Interval("1", 301, 400));
        intersect23.add(new Interval("2", 250, 270));
        intersect23.add(new Interval("2", 290, 400));

        return new Object[][]{
                new Object[]{Arrays.asList(list1, list2, list3), intersect123},
                new Object[]{Arrays.asList(list1, list2), intersect12},
                new Object[]{Arrays.asList(list2, list1), intersect12},
                new Object[]{Arrays.asList(list2, list3), intersect23},
                new Object[]{Arrays.asList(list3, list2), intersect23},
                new Object[]{Arrays.asList(list1, list3), intersect13},
                new Object[]{Arrays.asList(list3, list1), intersect13}
        };
    }

    @Test(dataProvider = "intersectData")
    public void testIntersectIntervalLists(final List<IntervalList> lists, final IntervalList list) {
        Assert.assertEquals(
                CollectionUtil.makeCollection(IntervalList.intersection(lists).iterator()),
                CollectionUtil.makeCollection(list.iterator()));
    }

    @DataProvider(name = "mergeData")
    public Object[][] mergeData() {
        final IntervalList merge123 = new IntervalList(fileHeader);
        final IntervalList merge12 = new IntervalList(fileHeader);
        final IntervalList merge23 = new IntervalList(fileHeader);
        final IntervalList merge13 = new IntervalList(fileHeader);

        merge123.add(new Interval("1", 1, 100));     //de-facto: 1:1-200 1:202-300     2:100-150 2:200-300
        merge123.add(new Interval("1", 101, 200));
        merge123.add(new Interval("1", 202, 300));
        merge123.add(new Interval("2", 200, 300));
        merge123.add(new Interval("2", 100, 150));

        merge123.add(new Interval("1", 50, 150));   //de-facto 1:50-150 1:301-500      2:1-150 2:250-270 2:290-400
        merge123.add(new Interval("1", 301, 500));
        merge123.add(new Interval("2", 1, 150));
        merge123.add(new Interval("2", 250, 270));
        merge123.add(new Interval("2", 290, 400));

        merge123.add(new Interval("1", 25, 400));    //de-facto 1:25-400                2:200-600                            3:50-470
        merge123.add(new Interval("2", 200, 600));
        merge123.add(new Interval("3", 50, 470));

        merge12.add(new Interval("1", 1, 100));     //de-facto: 1:1-200 1:202-300     2:100-150 2:200-300
        merge12.add(new Interval("1", 101, 200));
        merge12.add(new Interval("1", 202, 300));
        merge12.add(new Interval("2", 200, 300));
        merge12.add(new Interval("2", 100, 150));

        merge12.add(new Interval("1", 50, 150));   //de-facto 1:50-150 1:301-500      2:1-150 2:250-270 2:290-400
        merge12.add(new Interval("1", 301, 500));
        merge12.add(new Interval("2", 1, 150));
        merge12.add(new Interval("2", 250, 270));
        merge12.add(new Interval("2", 290, 400));

        merge23.add(new Interval("1", 50, 150));   //de-facto 1:50-150 1:301-500      2:1-150 2:250-270 2:290-400
        merge23.add(new Interval("1", 301, 500));
        merge23.add(new Interval("2", 1, 150));
        merge23.add(new Interval("2", 250, 270));
        merge23.add(new Interval("2", 290, 400));

        merge23.add(new Interval("1", 25, 400));    //de-facto 1:25-400                2:200-600                            3:50-470
        merge23.add(new Interval("2", 200, 600));
        merge23.add(new Interval("3", 50, 470));

        merge13.add(new Interval("1", 1, 100));     //de-facto: 1:1-200 1:202-300     2:100-150 2:200-300
        merge13.add(new Interval("1", 101, 200));
        merge13.add(new Interval("1", 202, 300));
        merge13.add(new Interval("2", 200, 300));
        merge13.add(new Interval("2", 100, 150));

        merge13.add(new Interval("1", 25, 400));    //de-facto 1:25-400                2:200-600                            3:50-470
        merge13.add(new Interval("2", 200, 600));
        merge13.add(new Interval("3", 50, 470));

        return new Object[][]{
                new Object[]{Arrays.asList(list1, list2, list3), merge123},
                new Object[]{Arrays.asList(list1, list2), merge12},
                new Object[]{Arrays.asList(list2, list3), merge23},
                new Object[]{Arrays.asList(list1, list3), merge13}
        };
    }

    @Test(dataProvider = "mergeData")
    public void testMergeIntervalLists(final List<IntervalList> lists, final IntervalList list) {
        Assert.assertEquals(
                CollectionUtil.makeCollection(IntervalList.concatenate(lists).iterator()),
                CollectionUtil.makeCollection(list.iterator()));
    }

    @DataProvider(name = "unionData")
    public Object[][] unionData() {
        final IntervalList union123 = new IntervalList(fileHeader);
        final IntervalList union12 = new IntervalList(fileHeader);
        final IntervalList union13 = new IntervalList(fileHeader);
        final IntervalList union23 = new IntervalList(fileHeader);

        union123.add(new Interval("1", 1, 500));
        union123.add(new Interval("2", 1, 150));
        union123.add(new Interval("2", 200, 600));
        union123.add(new Interval("3", 50, 470));

        union12.add(new Interval("1", 1, 200));
        union12.add(new Interval("1", 202, 500));
        union12.add(new Interval("2", 1, 150));
        union12.add(new Interval("2", 200, 400));

        union23.add(new Interval("1", 25, 500));
        union23.add(new Interval("2", 1, 150));
        union23.add(new Interval("2", 200, 600));
        union23.add(new Interval("3", 50, 470));

        union13.add(new Interval("1", 1, 400));
        union13.add(new Interval("2", 100, 150));
        union13.add(new Interval("2", 200, 600));
        union13.add(new Interval("3", 50, 470));


        return new Object[][]{
                new Object[]{Arrays.asList(list1, list2, list3), union123},
                new Object[]{Arrays.asList(list1, list2), union12},
                new Object[]{Arrays.asList(list2, list3), union23},
                new Object[]{Arrays.asList(list1, list3), union13},
        };
    }

    @Test(dataProvider = "unionData")
    public void testUnionIntervalLists(final List<IntervalList> lists, final IntervalList list) {
        Assert.assertEquals(
                CollectionUtil.makeCollection(IntervalList.union(lists).iterator()),
                CollectionUtil.makeCollection(list.iterator()));
    }


    @Test
    public void testUnionSamePosition() {
        final IntervalList iList= new IntervalList(fileHeader);

        final List<Interval> intervals = Arrays.asList(
                new Interval("1", 2, 100, true, "test1"),
                new Interval("1", 2, 100, true, "test2")
        );
        iList.addall(intervals);
        final List<Interval> uniqued = iList.uniqued().getIntervals();
        Assert.assertEquals(uniqued.size(),1);
        Assert.assertEquals(uniqued.get(0).getName(),"test1|test2");
    }

    @DataProvider(name = "invertData")
    public Object[][] invertData() {
        final IntervalList invert1 = new IntervalList(fileHeader);
        final IntervalList invert2 = new IntervalList(fileHeader);
        final IntervalList invert3 = new IntervalList(fileHeader);

        final IntervalList full = new IntervalList(fileHeader);
        final IntervalList fullChopped = new IntervalList(fileHeader);
        final IntervalList empty = new IntervalList(fileHeader);

        invert1.add(new Interval("1", 201, 201));
        invert1.add(new Interval("1", 301, fileHeader.getSequence("1").getSequenceLength()));
        invert1.add(new Interval("2", 1, 99));
        invert1.add(new Interval("2", 151, 199));
        invert1.add(new Interval("2", 301, fileHeader.getSequence("2").getSequenceLength()));
        invert1.add(new Interval("3", 1, fileHeader.getSequence("3").getSequenceLength()));

        invert2.add(new Interval("1", 1, 49));
        invert2.add(new Interval("1", 151, 300));
        invert2.add(new Interval("1", 501, fileHeader.getSequence("1").getSequenceLength()));
        invert2.add(new Interval("2", 151, 249));
        invert2.add(new Interval("2", 271, 289));
        invert2.add(new Interval("2", 401, fileHeader.getSequence("2").getSequenceLength()));
        invert2.add(new Interval("3", 1, fileHeader.getSequence("3").getSequenceLength()));

        invert3.add(new Interval("1", 1, 24));
        invert3.add(new Interval("1", 401, fileHeader.getSequence("1").getSequenceLength()));
        invert3.add(new Interval("2", 1, 199));
        invert3.add(new Interval("2", 601, fileHeader.getSequence("2").getSequenceLength()));
        invert3.add(new Interval("3", 1, 49));
        invert3.add(new Interval("3", 471, fileHeader.getSequence("3").getSequenceLength()));

        for (final SAMSequenceRecord samSequenceRecord : fileHeader.getSequenceDictionary().getSequences()) {
            full.add(new Interval(samSequenceRecord.getSequenceName(), 1, samSequenceRecord.getSequenceLength()));

            fullChopped.add(new Interval(samSequenceRecord.getSequenceName(), 1, samSequenceRecord.getSequenceLength() / 2));
            fullChopped.add(new Interval(samSequenceRecord.getSequenceName(), samSequenceRecord.getSequenceLength() / 2 + 1, samSequenceRecord.getSequenceLength()));
        }

        return new Object[][]{
                new Object[]{list1, invert1},
                new Object[]{list2, invert2},
                new Object[]{list3, invert3},
                new Object[]{full, empty},
                new Object[]{empty, full},
                new Object[]{fullChopped, empty}
        };
    }

    @Test(dataProvider = "invertData")
    public void testInvertSquared(final IntervalList list, @SuppressWarnings("UnusedParameters") final IntervalList ignored) throws Exception {
        final IntervalList inverseSquared = IntervalList.invert(IntervalList.invert(list));
        final IntervalList originalClone = new IntervalList(list.getHeader());

        for (final Interval interval : list) {
            originalClone.add(interval);
        }

        Assert.assertEquals(
                CollectionUtil.makeCollection(inverseSquared.iterator()),
                CollectionUtil.makeCollection(originalClone.uniqued().iterator()));
    }

    @Test(dataProvider = "invertData")
    public void testInvert(final IntervalList list, final IntervalList inverse) throws Exception {
        Assert.assertEquals(
                CollectionUtil.makeCollection(IntervalList.invert(list).iterator()),
                CollectionUtil.makeCollection(inverse.iterator()));
    }

    @DataProvider(name = "subtractSingletonData")
    public Object[][] subtractSingletonData() {
        final IntervalList subtract1_from_2 = new IntervalList(fileHeader);
        final IntervalList subtract2_from_3 = new IntervalList(fileHeader);
        final IntervalList subtract1_from_3 = new IntervalList(fileHeader);
        final IntervalList subtract3_from_1 = new IntervalList(fileHeader);

        subtract1_from_2.add(new Interval("1", 301, 500));
        subtract1_from_2.add(new Interval("2", 1, 99));
        subtract1_from_2.add(new Interval("2", 301, 400));

        subtract2_from_3.add(new Interval("1", 25, 49));
        subtract2_from_3.add(new Interval("1", 151, 300));
        subtract2_from_3.add(new Interval("2", 200, 249));
        subtract2_from_3.add(new Interval("2", 271, 289));
        subtract2_from_3.add(new Interval("2", 401, 600));
        subtract2_from_3.add(new Interval("3", 50, 470));

        subtract1_from_3.add(new Interval("1", 201, 201));
        subtract1_from_3.add(new Interval("1", 301, 400));
        subtract1_from_3.add(new Interval("2", 301, 600));
        subtract1_from_3.add(new Interval("3", 50, 470));

        subtract3_from_1.add(new Interval("1", 1, 49));    //de-facto 1:25-400                2:200-600                            3:50-470
        subtract3_from_1.add(new Interval("2", 100, 150));

        return new Object[][]{
                new Object[]{list2, list1, subtract1_from_2},
                new Object[]{list3, list2, subtract2_from_3},
                new Object[]{list3, list1, subtract1_from_3},
        };
    }

    @DataProvider(name = "subtractData")
    public Object[][] subtractData() {
        final IntervalList subtract12_from_3 = new IntervalList(fileHeader);

        subtract12_from_3.add(new Interval("1", 201, 201));
        subtract12_from_3.add(new Interval("2", 401, 600));
        subtract12_from_3.add(new Interval("3", 50, 470));

        return new Object[][]{
                new Object[]{CollectionUtil.makeList(list3), CollectionUtil.makeList(list1, list2), subtract12_from_3},
        };
    }

    @Test(dataProvider = "subtractData")
    public void testSubtractIntervalLists(final List<IntervalList> fromLists, final List<IntervalList> whatLists, final IntervalList list) {
        Assert.assertEquals(
                CollectionUtil.makeCollection(IntervalList.subtract(fromLists, whatLists).iterator()),
                CollectionUtil.makeCollection(list.iterator()));
    }

    @Test(dataProvider = "subtractSingletonData")
    public void testSubtractSingletonIntervalLists(final IntervalList fromLists, final IntervalList whatLists, final IntervalList list) {
        Assert.assertEquals(
                CollectionUtil.makeCollection(IntervalList.subtract(fromLists, whatLists).iterator()),
                CollectionUtil.makeCollection(list.iterator()));
    }

    @Test(dataProvider = "subtractSingletonData")
    public void testSubtractSingletonAsListIntervalList(final IntervalList fromLists, final IntervalList whatLists, final IntervalList list) {
        Assert.assertEquals(
                CollectionUtil.makeCollection(IntervalList.subtract(Collections.singletonList(fromLists), Collections.singletonList(whatLists)).iterator()),
                CollectionUtil.makeCollection(list.iterator()));
    }

    @DataProvider(name = "overlapsSingletonData")
    public Object[][] overlapSingletonData() {
        final IntervalList two_overlaps_one   = new IntervalList(fileHeader);
        final IntervalList three_overlaps_two = new IntervalList(fileHeader);
        final IntervalList three_overlaps_one = new IntervalList(fileHeader);
        final IntervalList one_overlaps_three = new IntervalList(fileHeader);

        // NB: commented lines below are there to show the intervals in the first list that will not be in the resulting list

        two_overlaps_one.add(new Interval("1", 50, 150));
        //two_overlaps_one.add(new Interval("1", 301, 500));
        two_overlaps_one.add(new Interval("2", 1, 150));
        two_overlaps_one.add(new Interval("2", 250, 270));
        two_overlaps_one.add(new Interval("2", 290, 400));

        three_overlaps_two.add(new Interval("1", 25, 400));
        three_overlaps_two.add(new Interval("2", 200, 600));
        //three_overlaps_two.add(new Interval("3", 50, 470));

        three_overlaps_one.add(new Interval("1", 25, 400));
        three_overlaps_one.add(new Interval("2", 200, 600));
        //three_overlaps_one.add(new Interval("3", 50, 470));

        one_overlaps_three.add(new Interval("1", 1, 100));
        one_overlaps_three.add(new Interval("1", 101, 200));
        one_overlaps_three.add(new Interval("1", 202, 300));
        one_overlaps_three.add(new Interval("2", 200, 300));
        //one_overlaps_three.add(new Interval("2", 100, 150));

        return new Object[][]{
                new Object[]{list1, list1, list1}, // should return itself
                new Object[]{list1, IntervalList.invert(list1), new IntervalList(list1.getHeader())}, // should be empty
                new Object[]{list2, list1, two_overlaps_one},
                new Object[]{list3, list2, three_overlaps_two},
                new Object[]{list3, list1, three_overlaps_one},
                new Object[]{list1, list3, one_overlaps_three}
        };
    }

    @DataProvider(name = "overlapsData")
    public Object[][] overlapData() {
        final IntervalList three_overlaps_one_and_two = new IntervalList(fileHeader);

        three_overlaps_one_and_two.add(new Interval("1", 25, 400));
        three_overlaps_one_and_two.add(new Interval("2", 200, 600));
        //three_overlaps_one_and_two.add(new Interval("3", 50, 470));

        return new Object[][]{
                new Object[]{CollectionUtil.makeList(list3), CollectionUtil.makeList(list1, list2), three_overlaps_one_and_two},
        };
    }

    @Test(dataProvider = "overlapsData")
    public void testOverlapsIntervalLists(final List<IntervalList> fromLists, final List<IntervalList> whatLists, final IntervalList list) {
        Assert.assertEquals(
                CollectionUtil.makeCollection(IntervalList.overlaps(fromLists, whatLists).iterator()),
                CollectionUtil.makeCollection(list.iterator()));
    }

    @Test(dataProvider = "overlapsSingletonData")
    public void testOverlapsSingletonIntervalLists(final IntervalList fromLists, final IntervalList whatLists, final IntervalList list) {
        Assert.assertEquals(
                CollectionUtil.makeCollection(IntervalList.overlaps(fromLists, whatLists).iterator()),
                CollectionUtil.makeCollection(list.iterator()));
    }

    @Test(dataProvider = "overlapsSingletonData")
    public void testOverlapsSingletonAsListIntervalList(final IntervalList fromLists, final IntervalList whatLists, final IntervalList list) {
        Assert.assertEquals(
                CollectionUtil.makeCollection(IntervalList.overlaps(Collections.singletonList(fromLists), Collections.singletonList(whatLists)).iterator()),
                CollectionUtil.makeCollection(list.iterator()));
    }

    @Test
    public void testOverlapsEmptyFirstList() {
        Assert.assertEquals(
                IntervalList.overlaps(empty, list1), empty);
    }

    @Test
    public void testOverlapsEmptySecondList() {
        Assert.assertEquals(
                IntervalList.overlaps(list1, empty), empty);
    }

    @DataProvider(name = "VCFCompData")
    public Object[][] VCFCompData() {
        final Path intervalListFromVcf = TEST_DIR.resolve("IntervalListFromVCFTest.vcf");
        final Path intervalListFromVcfManual = TEST_DIR.resolve("IntervalListFromVCFTestManual.vcf");

        return new Object[][]{
                new Object[]{intervalListFromVcf, TEST_DIR.resolve("IntervalListFromVCFTestComp.interval_list"), false},
                new Object[]{intervalListFromVcf, TEST_DIR.resolve("IntervalListFromVCFTestCompInverse.interval_list"), true},
                new Object[]{intervalListFromVcfManual, TEST_DIR.resolve("IntervalListFromVCFTestManualComp.interval_list"), false},
                new Object[]{intervalListFromVcfManual, TEST_DIR.resolve("IntervalListFromVCFTestCompInverseManual.interval_list"), true}
        };
    }

    @Test(dataProvider = "VCFCompData")
    public void testFromVCF(final Path vcf, final Path compInterval, final boolean invertVCF) {

        final IntervalList compList = IntervalList.fromPath(compInterval);
        final IntervalList list = invertVCF ? IntervalList.invert(VCFFileReader.toIntervalList(vcf)) : VCFFileReader.toIntervalList(vcf);

        compList.getHeader().getSequenceDictionary().assertSameDictionary(list.getHeader().getSequenceDictionary());

        final Collection<Interval> intervals = CollectionUtil.makeCollection(list.iterator());
        final Collection<Interval> compIntervals = CollectionUtil.makeCollection(compList.iterator());

        //assert that the intervals correspond
        Assert.assertEquals(intervals, compIntervals);

        final List<String> intervalNames = new LinkedList<>();
        final List<String> compIntervalNames = new LinkedList<>();

        for (final Interval interval : intervals) {
            intervalNames.add(interval.getName());
        }
        for (final Interval interval : compIntervals) {
            compIntervalNames.add(interval.getName());
        }
        //assert that the names match
        Assert.assertEquals(intervalNames, compIntervalNames);
    }


    @Test(dataProvider = "VCFCompData")
    public void testFromVCFWithPath(final Path vcf, final Path compInterval, final boolean invertVCF) {


        final IntervalList compList = IntervalList.fromPath(compInterval);
        final IntervalList list = invertVCF ? IntervalList.invert(VCFFileReader.toIntervalList(vcf)) : VCFFileReader.toIntervalList(vcf);

        compList.getHeader().getSequenceDictionary().assertSameDictionary(list.getHeader().getSequenceDictionary());

        final Collection<Interval> intervals = CollectionUtil.makeCollection(list.iterator());
        final Collection<Interval> compIntervals = CollectionUtil.makeCollection(compList.iterator());

        //assert that the intervals correspond
        Assert.assertEquals(intervals, compIntervals);

        final List<String> intervalNames = new LinkedList<>();
        final List<String> compIntervalNames = new LinkedList<>();

        for (final Interval interval : intervals) {
            intervalNames.add(interval.getName());
        }
        for (final Interval interval : compIntervals) {
            compIntervalNames.add(interval.getName());
        }
        //assert that the names match
        Assert.assertEquals(intervalNames, compIntervalNames);
    }


    @DataProvider
    public Object[][] testFromSequenceData() {
        final Path intervalList = TEST_DIR.resolve("IntervalListFromVCFTestComp.interval_list");
        return new Object[][]{
                new Object[]{intervalList, "1", 249250621},
                new Object[]{intervalList, "2", 243199373},
                new Object[]{intervalList, "3", 198022430},
        };
    }

    @Test(dataProvider = "testFromSequenceData")
    public void testFromSequenceName(final Path intervalList, final String referenceName, final Integer length) {

        final IntervalList intervals = IntervalList.fromPath(intervalList);
        final IntervalList test = IntervalList.fromName(intervals.getHeader(), referenceName);
        Assert.assertEquals(test.getIntervals(), CollectionUtil.makeList(new Interval(referenceName, 1, length)));
    }

    @DataProvider
    public Object[][] getMergeTestCases() {
        final String contig = "1";
        final Interval foo = new Interval(contig, 500, 600, false, "foo");
        final Interval bar = new Interval(contig, 550, 650, false, "bar");
        final Interval splat = new Interval(contig, 625, 699, false, "splat");
        final List<Interval> threeInOrderIntervals = Arrays.asList(foo, bar, splat);
        final List<Interval> threeOutOfOrderIntervals = Arrays.asList(bar, foo, splat);
        final Interval zeroLengthInterval = new Interval(contig, 626, 625);
        final Interval interval600To601 = new Interval(contig, 600, 601);
        final Interval interval600To625 = new Interval(contig, 600, 625);
        final Interval normalInterval = new Interval(contig, 626, 629, true, "whee");
        final Interval zeroInterval10To9 = new Interval(contig, 10, 9);
        return new Object[][]{
                {threeInOrderIntervals, true, new Interval(contig, 500, 699, false, "foo|bar|splat")},
                {threeInOrderIntervals, false, new Interval(contig, 500, 699, false, "foo")},
                {threeOutOfOrderIntervals, true, new Interval(contig, 500, 699, false, "bar|foo|splat")},
                {threeOutOfOrderIntervals, false, new Interval(contig, 500, 699, false, "bar")},
                {Collections.singletonList(normalInterval), true, normalInterval},
                {Collections.singletonList(normalInterval), false, normalInterval},
                {Collections.singletonList(zeroLengthInterval), true, zeroLengthInterval},
                {Collections.singletonList(zeroLengthInterval), false, zeroLengthInterval},
                {Arrays.asList(zeroLengthInterval, interval600To601), true, interval600To625},
                {Arrays.asList(zeroLengthInterval, interval600To601), false, interval600To625},
                {Arrays.asList(zeroLengthInterval, interval600To601), true, interval600To625},
                {Arrays.asList(interval600To601, new Interval(contig, 100, 200, false, "hasName")), true, new Interval(contig, 100, 601, false, "hasName")},
                {Arrays.asList(interval600To601, new Interval(contig, 100, 200, false, "hasName")), false, new Interval(contig, 100, 601, false, "hasName")},
                {Arrays.asList(zeroInterval10To9, new Interval(contig, 11, 15)), false, new Interval(contig, 10, 15)},
                {Arrays.asList(zeroInterval10To9, new Interval(contig, 10, 15)), true, new Interval(contig, 10, 15)},
                {Arrays.asList(zeroInterval10To9, new Interval(contig, 9,15)), false, new Interval(contig, 9, 15)},
                {Arrays.asList(zeroInterval10To9, new Interval(contig, 8, 9)), true, new Interval(contig, 8, 9)}
        };
    }

    @Test(dataProvider = "getMergeTestCases")
    public void testMerges(Iterable<Interval> intervals, boolean concatNames, Interval expected) {
        final Interval merged = IntervalList.merge(intervals, concatNames);
        Assert.assertEquals(merged.getContig(), expected.getContig());
        Assert.assertEquals(merged.getStart(), expected.getStart());
        Assert.assertEquals(merged.getStrand(), expected.getStrand());
        Assert.assertEquals(merged.getName(), expected.getName());
    }

    @Test
    public void testBreakAtBands() {
        final List<Interval> intervals = new ArrayList<Interval>() {{
            add(new Interval("A", 1, 99, false, "foo"));
            add(new Interval("A", 98, 99, true, "psyduck"));
            add(new Interval("1", 500, 600, false, "foo")); // -> 2
            add(new Interval("1", 550, 650, false, "bar")); // -> 2
            add(new Interval("1", 625, 699, false, "splat"));
            add(new Interval("2", 99, 201, false, "geodude")); // -> 3
            add(new Interval("3", 100, 99, false, "charizard"));  // Empty Interval
            add(new Interval("3", 101, 100, false, "golduck"));   // Empty Interval
        }};

        final List<Interval> brokenIntervals = IntervalList.breakIntervalsAtBandMultiples(intervals, 100);

        Assert.assertEquals(brokenIntervals.size(), 12);
        Assert.assertEquals(brokenIntervals.get(0), new Interval("A", 1, 99, false, "foo"));

        Assert.assertEquals(brokenIntervals.get(1), new Interval("A", 98, 99, true, "psyduck"));

        Assert.assertEquals(brokenIntervals.get(2), new Interval("1", 500, 599, false, "foo.1"));
        Assert.assertEquals(brokenIntervals.get(3), new Interval("1", 600, 600, false, "foo.2"));

        Assert.assertEquals(brokenIntervals.get(4), new Interval("1", 550, 599, false, "bar.1"));
        Assert.assertEquals(brokenIntervals.get(5), new Interval("1", 600, 650, false, "bar.2"));

        Assert.assertEquals(brokenIntervals.get(6), new Interval("1", 625, 699, false, "splat"));

        Assert.assertEquals(brokenIntervals.get(7), new Interval("2", 99, 99, false, "geodude.1"));
        Assert.assertEquals(brokenIntervals.get(8), new Interval("2", 100, 199, false, "geodude.2"));
        Assert.assertEquals(brokenIntervals.get(9), new Interval("2", 200, 201, false, "geodude.3"));

        Assert.assertEquals(brokenIntervals.get(10), new Interval("3", 100, 99, false, "charizard"));
        Assert.assertEquals(brokenIntervals.get(11), new Interval("3", 101, 100, false, "golduck"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void TestFailAdd() {
        IntervalList test = new IntervalList(this.fileHeader);
        test.add(new Interval("blarg", 1, 1));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void TestFailAddAll() {
        IntervalList test = new IntervalList(this.fileHeader);
        test.addall(CollectionUtil.makeList(new Interval("blarg", 1, 1), new Interval("bloorg", 1, 1)));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void changeHeader() {
        final SAMFileHeader clonedHeader = fileHeader.clone();
        clonedHeader.addSequence(new SAMSequenceRecord("4", 1000));
        final IntervalList usingClone1 = new IntervalList(clonedHeader);
        usingClone1.add(new Interval("4", 1, 100));
        final IntervalList usingClone2 = new IntervalList(clonedHeader);
        usingClone2.add(new Interval("4", 10, 20));


        final IntervalList expected = new IntervalList(clonedHeader);
        expected.add(new Interval("4", 1, 9));
        expected.add(new Interval("4", 21, 100));

        //pull rug from underneath (one call will change all the headers, since there's actually only one)
        usingClone1.getHeader().setSequenceDictionary(fileHeader.getSequenceDictionary());

        //now interval lists are in "illegal state" since they contain contigs that are not in the header.
        //this next step should fail
        IntervalList.subtract(usingClone1, usingClone2);

    }

    @Test public void uniqueIntervalsWithoutNames() {
        final IntervalList test = new IntervalList(this.fileHeader);
        test.add(new Interval("1", 100, 200));
        test.add(new Interval("1", 500, 600));
        test.add(new Interval("1", 550, 700));

        for (final boolean concat : new boolean[]{true, false}) {
            final IntervalList unique = test.uniqued(concat);
            Assert.assertEquals(unique.size(), 2);
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testContigsAbsentInHeader() {
        String vcf = TEST_DIR.resolve("IntervalListFromVCFNoContigLines.vcf").toString();
        final File vcfFile = new File(vcf);
        VCFFileReader.toIntervalList(vcfFile.toPath());
    }


    @DataProvider
    public static Object[][] brokenFiles() {
        return new Object[][]{
                {TEST_DIR.resolve("broken.end.extends.too.far.interval_list")},
                {TEST_DIR.resolve("broken.start.bigger.than.end.interval_list")},
                {TEST_DIR.resolve("broken.unallowed.strand.interval_list")},
                {TEST_DIR.resolve("broken.zero.start.interval_list")},
        };
    }

    @Test(dataProvider = "brokenFiles", expectedExceptions = IllegalArgumentException.class)
    public void testBreaks(final Path brokenIntervalFile){
        IntervalList.fromPath(brokenIntervalFile);
    }
}
