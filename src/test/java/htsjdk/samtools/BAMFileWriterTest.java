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

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.SequenceUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Test that BAM writing doesn't blow up.  For presorted writing, the resulting BAM file is read and contents are
 * compared with the original SAM file.
 */
public class BAMFileWriterTest extends HtsjdkTest {

    private SAMRecordSetBuilder getRecordSetBuilder(final boolean sortForMe, final SAMFileHeader.SortOrder sortOrder) {
        final SAMRecordSetBuilder ret = new SAMRecordSetBuilder(sortForMe, sortOrder);
        ret.addPair("readB", 20, 200, 300);
        ret.addPair("readA", 20, 100, 150);
        ret.addFrag("readC", 20, 140, true);
        ret.addFrag("readD", 20, 140, false);
        return ret;
    }

    /**
     * Parse some SAM text into a SAM object, then write as BAM.  If SAM text was presorted, then the BAM file can
     * be read and compared with the SAM object.
     *
     * @param samRecordSetBuilder source of input {@link SamReader} to be written and compared with
     * @param sortOrder           How the BAM should be written
     * @param presorted           If true, samText is in the order specified by sortOrder
     */
    private void testHelper(final SAMRecordSetBuilder samRecordSetBuilder, final SAMFileHeader.SortOrder sortOrder, final boolean presorted) throws Exception {
        final File bamFile = File.createTempFile("test.", BamFileIoUtils.BAM_FILE_EXTENSION);
        bamFile.deleteOnExit();

        try(
        final SamReader samReader = samRecordSetBuilder.getSamReader();
        final SAMFileWriter bamWriter = new SAMFileWriterFactory().makeSAMOrBAMWriter(samReader.getFileHeader(), presorted, bamFile))
        {
            samReader.getFileHeader().setSortOrder(sortOrder);
            CloseableIterator<SAMRecord> it = samReader.iterator();
            while (it.hasNext()) {
                bamWriter.addAlignment(it.next());
            }
            it.close();
        }
        if (presorted) { // If SAM text input was presorted, then we can compare SAM object to BAM object
            verifyBAMFile(samRecordSetBuilder, bamFile);
        }
    }

    private void verifyBAMFile(final SAMRecordSetBuilder samRecordSetBuilder, final File bamFile) throws IOException {
        try (
                final SamReader bamReader = SamReaderFactory.makeDefault().open(bamFile);
                final SamReader samReader = samRecordSetBuilder.getSamReader();
                final CloseableIterator<SAMRecord> it = samReader.iterator();
                final CloseableIterator<SAMRecord> bamIt = bamReader.iterator();
        ) {
            samReader.getFileHeader().setSortOrder(bamReader.getFileHeader().getSortOrder());
            Assert.assertEquals(bamReader.getFileHeader(), samReader.getFileHeader());
            while (it.hasNext()) {
                Assert.assertTrue(bamIt.hasNext());
                final SAMRecord samRecord = it.next();
                final SAMRecord bamRecord = bamIt.next();

                // SAMRecords don't have this set, so stuff it in there
                samRecord.setIndexingBin(bamRecord.getIndexingBin());

                // Force reference index attributes to be populated
                samRecord.getReferenceIndex();
                bamRecord.getReferenceIndex();
                samRecord.getMateReferenceIndex();
                bamRecord.getMateReferenceIndex();

                Assert.assertEquals(bamRecord, samRecord);
            }
            Assert.assertFalse(bamIt.hasNext());
        }

    }
        @DataProvider(name = "test1")
    public Object[][] createTestData() {
        return new Object[][]{
                {"coordinate sorted", getRecordSetBuilder(false, SAMFileHeader.SortOrder.unsorted), SAMFileHeader.SortOrder.coordinate, false},
                {"query sorted", getRecordSetBuilder(false, SAMFileHeader.SortOrder.unsorted), SAMFileHeader.SortOrder.queryname, false},
                {"unsorted", getRecordSetBuilder(false, SAMFileHeader.SortOrder.unsorted), SAMFileHeader.SortOrder.unsorted, false},
                {"coordinate presorted", getRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate), SAMFileHeader.SortOrder.coordinate, true},
                {"query presorted", getRecordSetBuilder(true, SAMFileHeader.SortOrder.queryname), SAMFileHeader.SortOrder.queryname, true},
        };
    }

    @Test(dataProvider = "test1")
    public void testPositive(final String testName, final SAMRecordSetBuilder samRecordSetBuilder, final SAMFileHeader.SortOrder order, final boolean presorted) throws Exception {

        testHelper(samRecordSetBuilder, order, presorted);
    }

    @Test(dataProvider = "test1")
    public void testNullRecordHeaders(final String testName, final SAMRecordSetBuilder samRecordSetBuilder, final SAMFileHeader.SortOrder order, final boolean presorted) throws Exception {

        // test that BAMFileWriter can write records that have a null header
        final SAMFileHeader samHeader = samRecordSetBuilder.getHeader();
        for (SAMRecord rec : samRecordSetBuilder.getRecords()) {
            rec.setHeader(null);
        }

        // make sure the records can actually be written out
        final File bamFile = File.createTempFile("test.", BamFileIoUtils.BAM_FILE_EXTENSION);
        bamFile.deleteOnExit();
        samHeader.setSortOrder(order);
        final SAMFileWriter bamWriter = new SAMFileWriterFactory().makeSAMOrBAMWriter(samHeader, presorted, bamFile);
        for (final SAMRecord rec : samRecordSetBuilder.getRecords()) {
            bamWriter.addAlignment(rec);
        }
        bamWriter.close();

        if (presorted) {
            verifyBAMFile(samRecordSetBuilder, bamFile);
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNullRecordsMismatchedHeader() throws Exception {

        final SAMRecordSetBuilder samRecordSetBuilder = getRecordSetBuilder(true, SAMFileHeader.SortOrder.queryname);
        for (final SAMRecord rec : samRecordSetBuilder.getRecords()) {
            rec.setHeader(null);
        }

        // create a fake header to make sure the records cannot  be written using an invalid
        // sequence dictionary and unresolvable references
        final SAMFileHeader fakeHeader = new SAMFileHeader();
        fakeHeader.setSortOrder(SAMFileHeader.SortOrder.queryname);
        final File bamFile = File.createTempFile("test.", BamFileIoUtils.BAM_FILE_EXTENSION);
        bamFile.deleteOnExit();

        try (final SAMFileWriter bamWriter = new SAMFileWriterFactory().makeSAMOrBAMWriter(fakeHeader, false, bamFile);) {
            for (SAMRecord rec : samRecordSetBuilder.getRecords()) {
                bamWriter.addAlignment(rec);
            }
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRecordsMismatchedHeader() throws Exception {

        final SAMRecordSetBuilder samRecordSetBuilder = getRecordSetBuilder(true, SAMFileHeader.SortOrder.queryname);

        // create a fake header to make sure the records cannot be written using an invalid
        // sequence dictionary and unresolvable references
        final SAMFileHeader fakeHeader = new SAMFileHeader();
        fakeHeader.setSortOrder(SAMFileHeader.SortOrder.queryname);
        final File bamFile = File.createTempFile("test.", BamFileIoUtils.BAM_FILE_EXTENSION);
        bamFile.deleteOnExit();

        try (final SAMFileWriter bamWriter = new SAMFileWriterFactory().makeSAMOrBAMWriter(fakeHeader, false, bamFile);) {
            for (SAMRecord rec : samRecordSetBuilder.getRecords()) {
                bamWriter.addAlignment(rec);
            }
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNegativePresorted() throws Exception {

        testHelper(getRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate), SAMFileHeader.SortOrder.queryname, true);
        Assert.fail("Exception should be thrown");
    }


    /**
     * A test to check that BAM changes read bases according with {@link SequenceUtil#toBamReadBasesInPlace}.
     */
    @Test
    public void testBAMReadBases() throws IOException {
        final SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord("1", SequenceUtil.getIUPACCodesString().length()));
        header.addReadGroup(new SAMReadGroupRecord("rg1"));

        final SAMRecord originalSAMRecord = new SAMRecord(header);
        originalSAMRecord.setReadName("test");
        originalSAMRecord.setReferenceIndex(0);
        originalSAMRecord.setAlignmentStart(1);
        originalSAMRecord.setReadBases(SequenceUtil.getIUPACCodesString().getBytes());
        originalSAMRecord.setCigarString(originalSAMRecord.getReadLength() + "M");
        originalSAMRecord.setBaseQualities(SAMRecord.NULL_QUALS);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final BAMFileWriter writer = new BAMFileWriter(baos, null)) {
            writer.setHeader(header);
            writer.addAlignment(originalSAMRecord);
        }


        final BAMFileReader reader = new BAMFileReader(new ByteArrayInputStream(baos.toByteArray()), null, true, false, ValidationStringency.SILENT, new DefaultSAMRecordFactory());
        final CloseableIterator<SAMRecord> iterator = reader.getIterator();
        iterator.hasNext();
        final SAMRecord recordFromBAM = iterator.next();

        Assert.assertNotEquals(recordFromBAM.getReadBases(), originalSAMRecord.getReadBases());
        Assert.assertEquals(recordFromBAM.getReadBases(), SequenceUtil.toBamReadBasesInPlace(originalSAMRecord.getReadBases()));
    }


    @DataProvider
    public Object[][] longCigarsData(){
        return new Object[][]{
                {1},
                {10},
                {100},
                {1_000},
                {10_000},
                {100_000},
                {1_000_000}};
    }

    @Test(dataProvider = "longCigarsData")
    public void testLongCigars(int numOps) throws Exception {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);

        final List<CigarOperator> operators = new ArrayList<>(numOps);
        final CigarOperator operatorstoUse[] = new CigarOperator[]{CigarOperator.M, CigarOperator.D, CigarOperator.M, CigarOperator.I};
        for (int i = 0; i < numOps; i++) {
            operators.add(operatorstoUse[i % operatorstoUse.length]);
        }
        operators.add(CigarOperator.M);

        final Cigar cigar = Cigar.fromCigarOperators(operators);

        builder.addFrag("frag1",0,1,false,false,cigar.toString(),null,30);
        builder.addPair("pair1",0,1,100_000,false,false,cigar.toString(),cigar.toString(),true,false,30);

        testHelper(builder, SAMFileHeader.SortOrder.coordinate,true);
    }


    //why is this not breaking?
    @Test(dataProvider = "longCigarsData")
    public void testMisplacedCGTag(int numOps) throws Exception {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);

        final List<CigarOperator> operators = new ArrayList<>(numOps);
        final CigarOperator operatorstoUse[] = new CigarOperator[]{CigarOperator.M, CigarOperator.D, CigarOperator.M, CigarOperator.I};
        for (int i = 0; i < numOps; i++) {
            operators.add(operatorstoUse[i % operatorstoUse.length]);
        }
        operators.add(CigarOperator.M);

        final Cigar cigar = Cigar.fromCigarOperators(operators);

        final SAMRecord record = builder.addFrag("frag1",0,1,false,false,cigar.toString(),null,30);
        record.setAttribute("CG","Ceci n'est pas une pipe!");

        final List<SAMRecord> pairOfReads = builder.addPair("pair1",0,1,100_000,false,false,cigar.toString(),cigar.toString(),true,false,30);
        for(final SAMRecord rec :pairOfReads){
            rec.setAttribute("CG","Ceci n'est pas une pipe!");
        }

        testHelper(builder, SAMFileHeader.SortOrder.coordinate,true);
    }
}
