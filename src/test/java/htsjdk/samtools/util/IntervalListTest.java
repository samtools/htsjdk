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

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.variant.vcf.VCFFileReader;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Tests the IntervalList class
 */
public class IntervalListTest {

    final SAMFileHeader fileHeader;
    final IntervalList list1, list2, list3;

    public IntervalListTest() {
        fileHeader = IntervalList.fromFile(new File("src/test/resources/htsjdk/samtools/intervallist/IntervalListchr123_empty.interval_list")).getHeader();
        fileHeader.setSortOrder(SAMFileHeader.SortOrder.unsorted);

        list1 = new IntervalList(fileHeader);
        list2 = new IntervalList(fileHeader);
        list3 = new IntervalList(fileHeader);

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
                new Object[]{Arrays.asList(list1, list2), union12},
                new Object[]{Arrays.asList(list2, list3), union23},
                new Object[]{Arrays.asList(list2, list3), union23},
                new Object[]{Arrays.asList(list1, list3), union13},
                new Object[]{Arrays.asList(list1, list3), union13}
        };
    }

    @Test(dataProvider = "unionData", enabled = true)
    public void testUnionIntervalLists(final List<IntervalList> lists, final IntervalList list) {
        Assert.assertEquals(
                CollectionUtil.makeCollection(IntervalList.union(lists).iterator()),
                CollectionUtil.makeCollection(list.iterator()));
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
    public void testSubtractSingletonasListIntervalList(final IntervalList fromLists, final IntervalList whatLists, final IntervalList list) {
        Assert.assertEquals(
                CollectionUtil.makeCollection(IntervalList.subtract(Collections.singletonList(fromLists), Collections.singletonList(whatLists)).iterator()),
                CollectionUtil.makeCollection(list.iterator()));
    }

    @DataProvider(name = "VCFCompData")
    public Object[][] VCFCompData() {
        return new Object[][]{
                new Object[]{"src/test/resources/htsjdk/samtools/intervallist/IntervalListFromVCFTest.vcf", "src/test/resources/htsjdk/samtools/intervallist/IntervalListFromVCFTestComp.interval_list", false},
                new Object[]{"src/test/resources/htsjdk/samtools/intervallist/IntervalListFromVCFTest.vcf", "src/test/resources/htsjdk/samtools/intervallist/IntervalListFromVCFTestCompInverse.interval_list", true},
                new Object[]{"src/test/resources/htsjdk/samtools/intervallist/IntervalListFromVCFTestManual.vcf", "src/test/resources/htsjdk/samtools/intervallist/IntervalListFromVCFTestManualComp.interval_list", false},
                new Object[]{"src/test/resources/htsjdk/samtools/intervallist/IntervalListFromVCFTestManual.vcf", "src/test/resources/htsjdk/samtools/intervallist/IntervalListFromVCFTestCompInverseManual.interval_list", true}
        };
    }

    @Test(dataProvider = "VCFCompData")
    public void testFromVCF(final String vcf, final String compInterval, final boolean invertVCF) {

        final File vcfFile = new File(vcf);
        final File compIntervalFile = new File(compInterval);

        final IntervalList compList = IntervalList.fromFile(compIntervalFile);
        final IntervalList list = invertVCF ? IntervalList.invert(VCFFileReader.fromVcf(vcfFile)) : VCFFileReader.fromVcf(vcfFile);

        compList.getHeader().getSequenceDictionary().assertSameDictionary(list.getHeader().getSequenceDictionary());

        final Collection<Interval> intervals = CollectionUtil.makeCollection(list.iterator());
        final Collection<Interval> compIntervals = CollectionUtil.makeCollection(compList.iterator());

        //assert that the intervals correspond
        Assert.assertEquals(intervals, compIntervals);

        final List<String> intervalNames = new LinkedList<String>();
        final List<String> compIntervalNames = new LinkedList<String>();

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
        return new Object[][]{
                new Object[]{"src/test/resources/htsjdk/samtools/intervallist/IntervalListFromVCFTestComp.interval_list", "1", 249250621},
                new Object[]{"src/test/resources/htsjdk/samtools/intervallist/IntervalListFromVCFTestComp.interval_list", "2", 243199373},
                new Object[]{"src/test/resources/htsjdk/samtools/intervallist/IntervalListFromVCFTestComp.interval_list", "3", 198022430},
        };
    }

    @Test(dataProvider = "testFromSequenceData")
    public void testFromSequenceName(final String intervalList, final String referenceName, final Integer length) {

        final IntervalList intervals = IntervalList.fromFile(new File(intervalList));
        final IntervalList test = IntervalList.fromName(intervals.getHeader(), referenceName);
        Assert.assertEquals(test.getIntervals(), CollectionUtil.makeList(new Interval(referenceName, 1, length)));
    }

    @Test
    public void testMerges() {
        final SortedSet<Interval> intervals = new TreeSet<Interval>() {{
            add(new Interval("1", 500, 600, false, "foo"));
            add(new Interval("1", 550, 650, false, "bar"));
            add(new Interval("1", 625, 699, false, "splat"));
        }};

        Interval out = IntervalList.merge(intervals, false);
        Assert.assertEquals(out.getStart(), 500);
        Assert.assertEquals(out.getEnd(), 699);

        intervals.add(new Interval("1", 626, 629, false, "whee"));
        out = IntervalList.merge(intervals, false);
        Assert.assertEquals(out.getStart(), 500);
        Assert.assertEquals(out.getEnd(), 699);
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
        SAMFileHeader clonedHeader = fileHeader.clone();
        clonedHeader.addSequence(new SAMSequenceRecord("4", 1000));
        IntervalList usingClone1 = new IntervalList(clonedHeader);
        usingClone1.add(new Interval("4", 1, 100));
        IntervalList usingClone2 = new IntervalList(clonedHeader);
        usingClone2.add(new Interval("4", 10, 20));


        IntervalList expected = new IntervalList(clonedHeader);
        expected.add(new Interval("4", 1, 9));
        expected.add(new Interval("4", 21, 100));

        //pull rug from underneath (one call will change all the headers, since there's actually only one)
        usingClone1.getHeader().setSequenceDictionary(fileHeader.getSequenceDictionary());

        //now interval lists are in "illegal state" since they contain contigs that are not in the header.
        //this next step should fail
        IntervalList.subtract(usingClone1, usingClone2);

        Assert.assertTrue(false);

    }
}
