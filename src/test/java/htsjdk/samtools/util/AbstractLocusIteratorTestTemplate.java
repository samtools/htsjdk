/*
 * The MIT License
 *
 * Copyright (c) 2016 The Broad Institute
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
import htsjdk.samtools.SAMRecordSetBuilder;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;

/**
 * Common template for testing classes, that extend AbstractLocusIterator.
 * 
 * @author Mariia_Zueva@epam.com, EPAM Systems, Inc. <www.epam.com>
 * 
 */
public abstract class AbstractLocusIteratorTestTemplate extends HtsjdkTest {

    /** Coverage for tests with the same reads */
    final static int coverage = 2;

    /** the read length for the tests */
    final static int readLength = 36;

    final static SAMFileHeader header = new SAMFileHeader();

    static {
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        SAMSequenceDictionary dict = new SAMSequenceDictionary();
        dict.addSequence(new SAMSequenceRecord("chrM", 100000));
        header.setSequenceDictionary(dict);
    }

    /** Get the record builder for the tests with the default parameters that are needed */
    static SAMRecordSetBuilder getRecordBuilder() {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
        builder.setHeader(header);
        builder.setReadLength(readLength);
        return builder;
    }

    public abstract void testBasicIterator();
    public abstract void testEmitUncoveredLoci();
    public abstract void testSimpleGappedAlignment();
    public abstract void testOverlappingGappedAlignmentsWithoutIndels();
}
