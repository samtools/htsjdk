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
import htsjdk.index.BinningIndex;
import htsjdk.index.FileBackedBinningIndex;
import htsjdk.samtools.metrics.MetricsFile;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.BlockCompressedStreamConstants;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.utils.SamtoolsTestUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

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
    private void testHelper(
            final SAMRecordSetBuilder samRecordSetBuilder,
            final SAMFileHeader.SortOrder sortOrder,
            final boolean presorted)
            throws Exception {
        final Path bamFile = Files.createTempFile("test.", FileExtensions.BAM);
        bamFile.toFile().deleteOnExit();

        try (final SamReader samReader = samRecordSetBuilder.getSamReader()) {
            samReader.getFileHeader().setSortOrder(sortOrder);
            try (final SAMFileWriter bamWriter = new SAMFileWriterFactory()
                            .makeSAMOrBAMWriter(samReader.getFileHeader(), presorted, bamFile);
                    final CloseableIterator<SAMRecord> it = samReader.iterator()) {
                while (it.hasNext()) {
                    bamWriter.addAlignment(it.next());
                }
            }
        }
        if (presorted) { // If SAM text input was presorted, then we can compare SAM object to BAM object
            verifyBAMFile(samRecordSetBuilder, bamFile);
        }

        final Path tempMetrics = Files.createTempFile("CGTagTest", ".validation_metrics");
        try (final SamReader samReader = SamReaderFactory.makeDefault().open(bamFile);
                final OutputStream outputStream = Files.newOutputStream(tempMetrics);
                final PrintWriter printWriter = new PrintWriter(outputStream)) {

            new SamFileValidator(printWriter, 100).validateSamFileSummary(samReader, null);
        }

        final MetricsFile<SamFileValidator.ValidationMetrics, String> validationMetrics = new MetricsFile<>();
        try (final Reader reader = Files.newBufferedReader(tempMetrics)) {
            validationMetrics.read(reader);
        }

        Assert.assertNull(validationMetrics.getHistogram().get("ERROR:CG_TAG_FOUND_IN_ATTRIBUTES"));
    }

    private void verifyBAMFile(final SAMRecordSetBuilder samRecordSetBuilder, final Path bamFile) throws IOException {
        try (final SamReader bamReader = SamReaderFactory.makeDefault().open(bamFile);
                final SamReader samReader = samRecordSetBuilder.getSamReader()) {
            verifySamReadersEqual(samReader, bamReader);
        }
    }

    private void verifySamReadersEqual(final SamReader reader2, final SamReader reader1) {

        reader2.getFileHeader().setSortOrder(reader1.getFileHeader().getSortOrder());
        Assert.assertEquals(reader1.getFileHeader(), reader2.getFileHeader());

        try (final CloseableIterator<SAMRecord> samIt1 = reader1.iterator();
                final CloseableIterator<SAMRecord> samIt2 = reader2.iterator()) {
            while (samIt2.hasNext()) {
                Assert.assertTrue(samIt1.hasNext());
                final SAMRecord samRecord1 = samIt1.next();
                final SAMRecord samRecord2 = samIt2.next();

                // Force reference index attributes to be populated
                samRecord1.getReferenceIndex();
                samRecord2.getReferenceIndex();
                samRecord1.getMateReferenceIndex();
                samRecord2.getMateReferenceIndex();

                Assert.assertEquals(samRecord1, samRecord2);
            }
            Assert.assertFalse(samIt1.hasNext());
        }
    }

    @DataProvider(name = "test1")
    public Object[][] createTestData() {
        return new Object[][] {
            {
                "coordinate sorted",
                getRecordSetBuilder(false, SAMFileHeader.SortOrder.unsorted),
                SAMFileHeader.SortOrder.coordinate,
                false
            },
            {
                "query sorted",
                getRecordSetBuilder(false, SAMFileHeader.SortOrder.unsorted),
                SAMFileHeader.SortOrder.queryname,
                false
            },
            {
                "unsorted",
                getRecordSetBuilder(false, SAMFileHeader.SortOrder.unsorted),
                SAMFileHeader.SortOrder.unsorted,
                false
            },
            {
                "coordinate presorted",
                getRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate),
                SAMFileHeader.SortOrder.coordinate,
                true
            },
            {
                "query presorted",
                getRecordSetBuilder(true, SAMFileHeader.SortOrder.queryname),
                SAMFileHeader.SortOrder.queryname,
                true
            },
        };
    }

    @Test(dataProvider = "test1")
    public void testPositive(
            final String testName,
            final SAMRecordSetBuilder samRecordSetBuilder,
            final SAMFileHeader.SortOrder order,
            final boolean presorted)
            throws Exception {

        testHelper(samRecordSetBuilder, order, presorted);
    }

    @Test(dataProvider = "test1")
    public void testNullRecordHeaders(
            final String testName,
            final SAMRecordSetBuilder samRecordSetBuilder,
            final SAMFileHeader.SortOrder order,
            final boolean presorted)
            throws Exception {

        // test that BAMFileWriter can write records that have a null header
        final SAMFileHeader samHeader = samRecordSetBuilder.getHeader();
        for (SAMRecord rec : samRecordSetBuilder.getRecords()) {
            rec.setHeader(null);
        }

        // make sure the records can actually be written out
        final Path bamFile = Files.createTempFile("test.", FileExtensions.BAM);
        bamFile.toFile().deleteOnExit();
        samHeader.setSortOrder(order);
        try (final SAMFileWriter bamWriter =
                new SAMFileWriterFactory().makeSAMOrBAMWriter(samHeader, presorted, bamFile)) {
            for (final SAMRecord rec : samRecordSetBuilder.getRecords()) {
                bamWriter.addAlignment(rec);
            }
        }
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
        final Path bamFile = Files.createTempFile("test.", FileExtensions.BAM);
        bamFile.toFile().deleteOnExit();

        try (final SAMFileWriter bamWriter =
                new SAMFileWriterFactory().makeSAMOrBAMWriter(fakeHeader, false, bamFile); ) {
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
        final Path bamFile = Files.createTempFile("test.", FileExtensions.BAM);
        bamFile.toFile().deleteOnExit();

        try (final SAMFileWriter bamWriter =
                new SAMFileWriterFactory().makeSAMOrBAMWriter(fakeHeader, false, bamFile); ) {
            for (SAMRecord rec : samRecordSetBuilder.getRecords()) {
                bamWriter.addAlignment(rec);
            }
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNegativePresorted() throws Exception {

        testHelper(
                getRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate), SAMFileHeader.SortOrder.queryname, true);
        Assert.fail("Exception should be thrown");
    }

    /**
     * A test to check that BAM changes read bases according with {@link SequenceUtil#toBamReadBasesInPlace}.
     */
    @Test
    public void testBAMReadBases() throws IOException {
        final SAMFileHeader header = new SAMFileHeader();
        header.addSequence(
                new SAMSequenceRecord("1", SequenceUtil.getIUPACCodesString().length()));
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

        final BAMFileReader reader = new BAMFileReader(
                new ByteArrayInputStream(baos.toByteArray()),
                (Path) null,
                true,
                false,
                ValidationStringency.SILENT,
                new DefaultSAMRecordFactory());
        final CloseableIterator<SAMRecord> iterator = reader.getIterator();
        iterator.hasNext();
        final SAMRecord recordFromBAM = iterator.next();

        Assert.assertNotEquals(recordFromBAM.getReadBases(), originalSAMRecord.getReadBases());
        Assert.assertEquals(
                recordFromBAM.getReadBases(), SequenceUtil.toBamReadBasesInPlace(originalSAMRecord.getReadBases()));
    }

    @Test(dataProvider = "longCigarsData")
    public void testClearAttributesDoesntVoidLongCigar(final int numOps) throws Exception {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        final Cigar cigar = Cigar.fromCigarOperators(getCigarOperatorsForTest(numOps));

        builder.addFrag("frag1", 0, 1, false, false, cigar.toString(), null, 30);

        // encode as BAM into ByteArray
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final BAMFileWriter writer = new BAMFileWriter(baos, null)) {
            writer.setHeader(builder.getHeader());
            builder.getRecords().forEach(writer::addAlignment);
        }

        // read from ByteArray
        final BAMFileReader reader = new BAMFileReader(
                new ByteArrayInputStream(baos.toByteArray()),
                (Path) null,
                false,
                false,
                ValidationStringency.SILENT,
                new DefaultSAMRecordFactory());
        final CloseableIterator<SAMRecord> iterator = reader.getIterator();
        iterator.hasNext();
        final SAMRecord recordFromBAM = iterator.next();

        // clear attributes before explicitly accessing cigar or attributes
        recordFromBAM.clearAttributes();

        // see that cigar is unscathed
        Assert.assertNotNull(recordFromBAM.getCigar());
        Assert.assertFalse(BAMRecord.isSentinelCigar(recordFromBAM.getCigar(), recordFromBAM.getReadLength()));
    }

    @Test(dataProvider = "longCigarsData")
    public void testSetCigarRemovesCgTagWhenNoLongerLong(final int numOps) throws Exception {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        final Cigar cigar = Cigar.fromCigarOperators(getCigarOperatorsForTest(numOps));
        final SAMRecord frag1 = builder.addFrag("frag1", 0, 1, false, false, cigar.toString(), null, 30);

        frag1.setCigarString(String.format("%dM", cigar.getReadLength()));
        testHelper(builder, SAMFileHeader.SortOrder.coordinate, true);
    }

    @Test(dataProvider = "longCigarsData")
    public void testSetCigarRemovesCgTagWhenStillLong(final int numOps) throws Exception {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        final Cigar cigar = Cigar.fromCigarOperators(getCigarOperatorsForTest(numOps));
        final SAMRecord frag1 = builder.addFrag("frag1", 0, 1, false, false, cigar.toString(), null, 30);
        final List<CigarOperator> cigarOperatorsForTest = getCigarOperatorsForTest(numOps);

        cigarOperatorsForTest.add(CigarOperator.H);
        final Cigar cigar2 = Cigar.fromCigarOperators(cigarOperatorsForTest);

        frag1.setCigar(cigar2);
        testHelper(builder, SAMFileHeader.SortOrder.coordinate, true);
    }

    @Test(dataProvider = "longCigarsData")
    public void testSetCigarStringRemovesCgTagWhenNoLongerLong(final int numOps) throws Exception {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        final Cigar cigar = Cigar.fromCigarOperators(getCigarOperatorsForTest(numOps));

        final SAMRecord frag1 = builder.addFrag("frag1", 0, 1, false, false, cigar.toString(), null, 30);

        frag1.setCigarString(String.format("%dM", cigar.getReadLength()));
        testHelper(builder, SAMFileHeader.SortOrder.coordinate, true);
    }

    @Test(dataProvider = "longCigarsData")
    public void testSetCigarStringRemovesCgTagWhenStillLong(final int numOps) throws Exception {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        final Cigar cigar = Cigar.fromCigarOperators(getCigarOperatorsForTest(numOps));

        final SAMRecord frag1 = builder.addFrag("frag1", 0, 1, false, false, cigar.toString(), null, 30);

        final List<CigarOperator> cigarOperatorsForTest = getCigarOperatorsForTest(numOps);
        cigarOperatorsForTest.add(CigarOperator.H);
        final Cigar cigar2 = Cigar.fromCigarOperators(cigarOperatorsForTest);

        frag1.setCigarString(cigar2.toString());
        testHelper(builder, SAMFileHeader.SortOrder.coordinate, true);
    }

    @Test(dataProvider = "longCigarsData")
    public void testBinNotNullWhenLargeCigarIsLoaded(final int numOps) throws Exception {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        final Cigar cigar = Cigar.fromCigarOperators(getCigarOperatorsForTest(numOps));

        builder.addFrag("frag1", 0, 1, false, false, cigar.toString(), null, 30);

        final Path bamFile = Files.createTempFile("test.", FileExtensions.BAM);
        bamFile.toFile().deleteOnExit();

        try (final SAMFileWriter bamWriter =
                new SAMFileWriterFactory().makeSAMOrBAMWriter(builder.getHeader(), false, bamFile)) {
            for (final SAMRecord record : builder.getRecords()) bamWriter.addAlignment(record);
        }

        try (final SamReader reader = SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.SILENT)
                .open(bamFile)) {
            reader.iterator().forEachRemaining(samRecord -> {
                samRecord.getCigar();
                samRecord.computeIndexingBin();
            });
        }
    }

    @DataProvider
    public Object[][] longCigarsData() {
        return new Object[][] {
            {1},
            {10},
            {100},
            {1_000},
            {10_000},
            {BAMRecord.MAX_CIGAR_OPERATORS - 1},
            {BAMRecord.MAX_CIGAR_OPERATORS},
            {BAMRecord.MAX_CIGAR_OPERATORS + 1},
            {100_000},
            {1_000_000}
        };
    }

    private static final CigarOperator[] operatorsToUse =
            new CigarOperator[] {CigarOperator.M, CigarOperator.D, CigarOperator.M, CigarOperator.I};

    @Test(dataProvider = "longCigarsData")
    public void testLongCigarsOneRead(final int numOps) throws Exception {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        final Cigar cigar = Cigar.fromCigarOperators(getCigarOperatorsForTest(numOps));

        builder.addFrag("frag1", 0, 1, false, false, cigar.toString(), null, 30);
        testHelper(builder, SAMFileHeader.SortOrder.coordinate, true);
    }

    @Test(dataProvider = "longCigarsData")
    public void testLongCigarsZerolengthRead(final int numOps) throws Exception {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        final Cigar cigar = Cigar.fromCigarOperators(getCigarOperatorsForTest(numOps));

        final SAMRecord sam = builder.addFrag("frag1", 0, 1, false, false, cigar.toString(), null, 30);
        sam.setReadBases(new byte[] {});
        sam.setBaseQualityString("");
        // in htsjdk only secondary alignments are allowed to have read-length zero (doesn't validate otherwise)
        sam.setSecondaryAlignment(true);

        testHelper(builder, SAMFileHeader.SortOrder.coordinate, true);
    }

    private List<CigarOperator> getCigarOperatorsForTest(final int numOps) {
        final List<CigarOperator> operators = new ArrayList<>(numOps);
        for (int i = 0; i < numOps; i++) {
            operators.add(operatorsToUse[i % operatorsToUse.length]);
        }
        operators.add(CigarOperator.M);
        return operators;
    }

    @Test(dataProvider = "longCigarsData")
    public void testLongCigars(final int numOps) throws Exception {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        final Cigar cigar = Cigar.fromCigarOperators(getCigarOperatorsForTest(numOps));

        builder.addFrag("frag1", 0, 1, false, false, cigar.toString(), null, 30);
        builder.addPair("pair1", 0, 1, 100_000, false, false, cigar.toString(), cigar.toString(), true, false, 30);

        testHelper(builder, SAMFileHeader.SortOrder.coordinate, true);
    }

    @DataProvider
    public Object[][] sentinelCigarData() {
        return new Object[][] {
            {1, 0, 1},
            {0, 1, 1},
            {0, 0, 0},
        };
    }

    @Test(dataProvider = "sentinelCigarData", expectedExceptions = IllegalStateException.class)
    public void testWrongCGTagCigars(final int readOffset, final int refOffset, final int cigarOpsOffset)
            throws Exception {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        final List<CigarOperator> operators = getCigarOperatorsForTest(BAMRecord.MAX_CIGAR_OPERATORS + cigarOpsOffset);
        final Cigar cigar = Cigar.fromCigarOperators(operators);
        final int[] cigarEncoding = BinaryCigarCodec.encode(cigar);

        final Cigar sentinelCigar = BAMRecordCodec.makeSentinelCigar(cigar);
        final List<CigarElement> sentinelCigarElements = new ArrayList<>(2);

        sentinelCigarElements.add(new CigarElement(
                sentinelCigar.getCigarElement(0).getLength() + readOffset,
                sentinelCigar.getCigarElement(0).getOperator()));
        sentinelCigarElements.add(new CigarElement(
                sentinelCigar.getCigarElement(1).getLength() + refOffset,
                sentinelCigar.getCigarElement(1).getOperator()));

        final String sentinelCigarString = new Cigar(sentinelCigarElements).toString();

        builder.addPair(
                "pair1", 0, 1, 100_000, false, false, sentinelCigarString, sentinelCigarString, true, false, 30);

        final SAMRecord frag = builder.addFrag("frag1", 0, 1, false, false, sentinelCigarString, null, 30);
        frag.setAttribute(SAMTag.CG, cigarEncoding);

        final List<SAMRecord> pairOfReads = builder.addPair(
                "pair1", 0, 1, 100_000, false, false, cigar.toString(), cigar.toString(), true, false, 30);
        for (final SAMRecord rec : pairOfReads) {
            rec.setAttribute(SAMTag.CG, cigarEncoding);
        }

        final Path bamFile = Files.createTempFile("test.", FileExtensions.BAM);
        bamFile.toFile().deleteOnExit();

        try (final SAMFileWriter bamWriter =
                new SAMFileWriterFactory().makeSAMOrBAMWriter(builder.getHeader(), false, bamFile)) {
            for (final SAMRecord record : builder.getRecords()) bamWriter.addAlignment(record);
        }

        try (final SamReader reader = SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.SILENT)
                .open(bamFile)) {
            reader.iterator().forEachRemaining(SAMRecord::getCigar);
        }
    }

    @Test(dataProvider = "longCigarsData")
    public void testNoCGTagCigars(final int numOps) throws Exception {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        final List<CigarOperator> operators = getCigarOperatorsForTest(numOps);
        final Cigar cigar = Cigar.fromCigarOperators(operators);

        builder.addFrag("frag1", 0, 1, false, false, cigar.toString(), null, 30);
        builder.addPair("pair1", 0, 1, 100_000, false, false, cigar.toString(), cigar.toString(), true, false, 30);

        final Path bamFile = Files.createTempFile("test.", FileExtensions.BAM);
        bamFile.toFile().deleteOnExit();

        try (final SAMFileWriter bamWriter =
                new SAMFileWriterFactory().makeSAMOrBAMWriter(builder.getHeader(), false, bamFile)) {
            for (SAMRecord record : builder.getRecords()) bamWriter.addAlignment(record);
        }

        try (final SamReader reader = SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.SILENT)
                .open(bamFile)) {
            reader.iterator().forEachRemaining(rec -> Assert.assertFalse(rec.hasAttribute(SAMTag.CG)));
        }
    }

    @Test(dataProvider = "longCigarsData", expectedExceptions = AssertionError.class)
    public void testMisplacedCGTag(final int numOps) throws Exception {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        builder.setUseBamFile(false);

        final List<CigarOperator> operators = getCigarOperatorsForTest(numOps);
        final Cigar cigar = Cigar.fromCigarOperators(operators);

        final SAMRecord record = builder.addFrag("frag1", 0, 1, false, false, cigar.toString(), null, 30);
        record.setAttribute(SAMTag.CG, "Ceci n'est pas une pipe!");

        final List<SAMRecord> pairOfReads = builder.addPair(
                "pair1", 0, 1, 100_000, false, false, cigar.toString(), cigar.toString(), true, false, 30);
        for (final SAMRecord rec : pairOfReads) {
            rec.setAttribute(SAMTag.CG, "Ceci n'est pas une pipe!");
        }

        testHelper(builder, SAMFileHeader.SortOrder.coordinate, true);
    }

    @Test
    public void testRealDataLongCigar() throws Exception {
        final Path samFile = Path.of("src/test/resources/htsjdk/samtools/BAMCigarOverflowTest/cigar-64k.sam.gz");
        final Path bamFile = Files.createTempFile("test.", FileExtensions.BAM);
        bamFile.toFile().deleteOnExit();

        try (final SamReader samReader = SamReaderFactory.make().open(samFile);
                final SAMFileWriter bamWriter =
                        new SAMFileWriterFactory().makeSAMOrBAMWriter(samReader.getFileHeader(), true, bamFile);
                final CloseableIterator<SAMRecord> it = samReader.iterator()) {
            while (it.hasNext()) {
                bamWriter.addAlignment(it.next());
            }
        }
        try (final SamReader samReader = SamReaderFactory.make().open(samFile);
                final SamReader bamReader = SamReaderFactory.make().open(bamFile)) {

            verifySamReadersEqual(samReader, bamReader);
        }
    }

    @Test
    public void setAttributeOnBamRecord() throws IOException {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);

        builder.addPair("test", 0, 100, 150, false, false, null, null, true, false, 30);

        // encode as BAM into ByteArray
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final BAMFileWriter writer = new BAMFileWriter(baos, null)) {
            writer.setHeader(builder.getHeader());
            builder.forEach(r -> r.setAttribute("xx", "Testing123"));
            builder.forEach(writer::addAlignment);
        }

        // read from ByteArray
        final BAMFileReader reader = new BAMFileReader(
                new ByteArrayInputStream(baos.toByteArray()),
                (Path) null,
                false,
                false,
                ValidationStringency.SILENT,
                new DefaultSAMRecordFactory());

        for (final SAMRecord rec : (Iterable<SAMRecord>) reader::getIterator) {

            // clear attribute before explicitly accessing cigar or attributes
            rec.setAttribute("xx", null);
            Assert.assertNull(rec.getAttribute("xx"));
        }
    }

    // On-the-fly BAM index matches createIndex of the finished file

    /**
     * Reads on references 0 and 2, reference 1 left empty, one in fifty long enough to occupy
     * higher bins and cause small-bin folding. Produces many BGZF blocks.
     */
    private static SAMRecordSetBuilder recordsEndingWithPlacedRead() {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        for (int i = 0; i < 8_000; i++) {
            final int ref = i < 4_000 ? 0 : 2;
            final int start = 1 + 50 * (i % 4_000);
            if (i % 50 == 0) {
                builder.addFrag("long" + i, ref, start, false, false, "20000M", null, 30);
            } else {
                builder.addFrag("read" + i, ref, start, i % 2 == 0);
            }
        }
        return builder;
    }

    private static SAMRecordSetBuilder recordsEndingWithUnplacedReads() {
        final SAMRecordSetBuilder builder = recordsEndingWithPlacedRead();
        for (int i = 0; i < 20; i++) {
            builder.addUnmappedFragment("unplaced" + i);
        }
        return builder;
    }

    private BinningIndex onTheFlyIndex(final SAMRecordSetBuilder records, final BamIndexType type, final Path directory)
            throws IOException {
        final Path bam = directory.resolve("reads.bam");
        try (SAMFileWriter writer = new SAMFileWriterFactory()
                .setCreateIndex(true)
                .setCreateMd5File(false)
                .setBamIndexType(type)
                .makeBAMWriter(records.getHeader(), true, bam)) {
            records.getRecords().forEach(writer::addAlignment);
        }
        final Path indexPath = type.resolve(records.getHeader().getSequenceDictionary()) == BamIndexType.CSI
                ? bam.resolveSibling("reads.bam.csi")
                : bam.resolveSibling("reads.bai");
        return loadIndex(indexPath, type.resolve(records.getHeader().getSequenceDictionary()) == BamIndexType.CSI);
    }

    private BinningIndex createIndexOfFinishedFile(final Path directory, final BamIndexType type) throws IOException {
        final Path bam = directory.resolve("reads.bam");
        final Path indexPath = Files.createTempFile("createIndex.", type == BamIndexType.CSI ? ".csi" : ".bai");
        indexPath.toFile().deleteOnExit();
        try (SamReader reader = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(bam)) {
            BAMIndexer.createIndex(reader, indexPath, null, type);
        }
        return loadIndex(indexPath, type == BamIndexType.CSI);
    }

    private static BinningIndex loadIndex(final Path path, final boolean csi) {
        if (csi) {
            try (InputStream in = new BlockCompressedInputStream(Files.newInputStream(path))) {
                return BinningIndex.readCsi(new BinaryCodec(in)).index();
            } catch (final IOException e) {
                throw new RuntimeIOException(e);
            }
        } else {
            try (FileBackedBinningIndex idx = FileBackedBinningIndex.open(path, true)) {
                return idx.loadAll();
            }
        }
    }

    @Test
    public void testOnTheFlyBaiEqualsCreateIndexEndingWithPlacedRead() throws IOException {
        final SAMRecordSetBuilder records = recordsEndingWithPlacedRead();
        final Path directory = Files.createTempDirectory("bamBaiPlaced");
        directory.toFile().deleteOnExit();
        final BinningIndex onTheFly = onTheFlyIndex(records, BamIndexType.BAI, directory);
        final BinningIndex afterTheFact = createIndexOfFinishedFile(directory, BamIndexType.BAI);
        Assert.assertEquals(onTheFly, afterTheFact);
        IOUtil.recursiveDelete(directory);
    }

    @Test
    public void testOnTheFlyCsiEqualsCreateIndexEndingWithPlacedRead() throws IOException {
        final SAMRecordSetBuilder records = recordsEndingWithPlacedRead();
        final Path directory = Files.createTempDirectory("bamCsiPlaced");
        directory.toFile().deleteOnExit();
        final BinningIndex onTheFly = onTheFlyIndex(records, BamIndexType.CSI, directory);
        final BinningIndex afterTheFact = createIndexOfFinishedFile(directory, BamIndexType.CSI);
        Assert.assertEquals(onTheFly, afterTheFact);
        IOUtil.recursiveDelete(directory);
    }

    @Test
    public void testOnTheFlyBaiEqualsCreateIndexEndingWithUnplacedReads() throws IOException {
        final SAMRecordSetBuilder records = recordsEndingWithUnplacedReads();
        final Path directory = Files.createTempDirectory("bamBaiUnplaced");
        directory.toFile().deleteOnExit();
        final BinningIndex onTheFly = onTheFlyIndex(records, BamIndexType.BAI, directory);
        final BinningIndex afterTheFact = createIndexOfFinishedFile(directory, BamIndexType.BAI);
        Assert.assertEquals(onTheFly, afterTheFact);
        IOUtil.recursiveDelete(directory);
    }

    @Test
    public void testOnTheFlyBaiEqualsSamtoolsIndex() throws IOException {
        if (!SamtoolsTestUtils.isSamtoolsAvailable()) {
            throw new SkipException("samtools not available");
        }
        final SAMRecordSetBuilder records = recordsEndingWithPlacedRead();
        final Path directory = Files.createTempDirectory("bamBaiSamtools");
        directory.toFile().deleteOnExit();
        final BinningIndex onTheFly = onTheFlyIndex(records, BamIndexType.BAI, directory);
        final Path samBai = Files.createTempFile("samtools.", ".bai");
        samBai.toFile().deleteOnExit();
        SamtoolsTestUtils.executeSamToolsCommand("index -b -o " + samBai + " " + directory.resolve("reads.bam"));
        final BinningIndex samtoolsIndex = loadIndex(samBai, false);
        Assert.assertEquals(onTheFly, samtoolsIndex);
        IOUtil.recursiveDelete(directory);
    }

    @Test
    public void testOnTheFlyIndexSpansMultipleBgzfBlocks() throws IOException {
        final SAMRecordSetBuilder records = recordsEndingWithPlacedRead();
        final Path directory = Files.createTempDirectory("bamMultiBlock");
        directory.toFile().deleteOnExit();
        final BinningIndex index = onTheFlyIndex(records, BamIndexType.BAI, directory);

        long largestBlockAddress = 0;
        for (int ref = 0; ref < index.getReferenceCount(); ref++) {
            final htsjdk.index.ReferenceBins bins = index.getReference(ref);
            for (int i = 0; i < bins.getBinCount(); i++) {
                for (final Chunk chunk : bins.getChunks(i)) {
                    largestBlockAddress = Math.max(
                            largestBlockAddress,
                            htsjdk.samtools.util.BlockCompressedFilePointerUtil.getBlockAddress(chunk.getChunkEnd()));
                }
            }
        }
        Assert.assertTrue(
                largestBlockAddress > 200_000,
                "the index should span many BGZF blocks, but the largest block address was only "
                        + largestBlockAddress);
        IOUtil.recursiveDelete(directory);
    }

    @Test
    public void testWriteHeader() throws IOException {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BAMFileWriter.writeHeader(baos, builder.getHeader());
        baos.close();

        try (BinaryCodec binaryCodec = new BinaryCodec(
                new DataInputStream(new BlockCompressedInputStream(new ByteArrayInputStream(baos.toByteArray()))))) {
            SAMFileHeader samFileHeader = BAMFileReader.readHeader(binaryCodec, ValidationStringency.STRICT, null);
            Assert.assertEquals(samFileHeader, builder.getHeader());
        }
    }

    // Indexing failure behaviour

    private static final int LONG_SEQ = 600_000_000;
    private static final int BEYOND_BAI = (1 << 29) + 1_000;

    /** A header with a sequence too long for BAI, and one normal read plus one beyond 2^29. */
    private static SAMFileWriter failingBamWriter(final Path bam) {
        final SAMRecordSetBuilder records =
                new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate, true, LONG_SEQ);
        return new SAMFileWriterFactory()
                .setCreateIndex(true)
                .setCreateMd5File(false)
                .setBamIndexType(BamIndexType.BAI)
                .makeBAMWriter(records.getHeader(), true, bam);
    }

    private static SAMRecord normalRead(final SAMFileHeader header) {
        final SAMRecordSetBuilder b = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.coordinate);
        b.setHeader(header);
        b.addFrag("normal", 0, 100, false);
        return b.getRecords().iterator().next();
    }

    private static SAMRecord beyondBaiRead(final SAMFileHeader header) {
        final SAMRecordSetBuilder b = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.coordinate);
        b.setHeader(header);
        b.addFrag("beyond", 0, BEYOND_BAI, false);
        return b.getRecords().iterator().next();
    }

    @Test(expectedExceptions = SAMException.class)
    public void testBamIndexingFailureThrowsSamException() throws IOException {
        final Path dir = Files.createTempDirectory("bamIdxFail");
        IOUtil.deleteOnExit(dir);
        final Path bam = dir.resolve("reads.bam");
        try (SAMFileWriter writer = failingBamWriter(bam)) {
            writer.addAlignment(normalRead(writer.getFileHeader()));
            writer.addAlignment(beyondBaiRead(writer.getFileHeader()));
        } finally {
            IOUtil.recursiveDelete(dir);
        }
    }

    @Test
    public void testBamIndexingFailureRemovesTheIndexFile() throws IOException {
        final Path dir = Files.createTempDirectory("bamIdxRemove");
        IOUtil.deleteOnExit(dir);
        final Path bam = dir.resolve("reads.bam");
        try (SAMFileWriter writer = failingBamWriter(bam)) {
            writer.addAlignment(normalRead(writer.getFileHeader()));
            writer.addAlignment(beyondBaiRead(writer.getFileHeader()));
        } catch (final SAMException expected) {
            // expected
        }
        try (Stream<Path> files = Files.list(dir)) {
            final List<String> names =
                    files.map(p -> p.getFileName().toString()).sorted().toList();
            Assert.assertFalse(names.stream().anyMatch(n -> n.endsWith(".bai")), "index file should be gone: " + names);
        }
        IOUtil.recursiveDelete(dir);
    }

    @Test
    public void testBamFurtherWriteAfterIndexingFailureThrows() throws IOException {
        final Path dir = Files.createTempDirectory("bamIdxFurther");
        IOUtil.deleteOnExit(dir);
        final Path bam = dir.resolve("reads.bam");
        final SAMFileWriter writer = failingBamWriter(bam);
        writer.addAlignment(normalRead(writer.getFileHeader()));
        try {
            writer.addAlignment(beyondBaiRead(writer.getFileHeader()));
            Assert.fail("should have thrown");
        } catch (final SAMException expected) {
            // expected
        }
        final long sizeAfterFailure = Files.size(bam);
        try {
            writer.addAlignment(beyondBaiRead(writer.getFileHeader()));
            Assert.fail("should have thrown on subsequent write");
        } catch (final SAMException expected) {
            Assert.assertTrue(expected.getMessage().contains("indexing failure"), expected.getMessage());
        }
        Assert.assertEquals(Files.size(bam), sizeAfterFailure, "nothing should have been written");
        try {
            writer.close();
        } catch (final SAMException ignored) {
            // close throws too
        }
        IOUtil.recursiveDelete(dir);
    }

    @Test
    public void testBamCloseAfterIndexingFailureThrows() throws IOException {
        final Path dir = Files.createTempDirectory("bamIdxClose");
        IOUtil.deleteOnExit(dir);
        final Path bam = dir.resolve("reads.bam");
        final SAMFileWriter writer = failingBamWriter(bam);
        writer.addAlignment(normalRead(writer.getFileHeader()));
        try {
            writer.addAlignment(beyondBaiRead(writer.getFileHeader()));
        } catch (final SAMException ignored) {
        }
        try {
            writer.close();
            Assert.fail("close should throw after an indexing failure");
        } catch (final SAMException expected) {
            // expected
        }
        IOUtil.recursiveDelete(dir);
    }

    @Test
    public void testBamCloseAfterIndexingFailureClosesTheDataStream() throws IOException {
        final Path dir = Files.createTempDirectory("bamIdxStream");
        IOUtil.deleteOnExit(dir);
        final Path bam = dir.resolve("reads.bam");
        final SAMFileWriter writer = failingBamWriter(bam);
        writer.addAlignment(normalRead(writer.getFileHeader()));
        try {
            writer.addAlignment(beyondBaiRead(writer.getFileHeader()));
        } catch (final SAMException ignored) {
        }
        try {
            writer.close();
        } catch (final SAMException ignored) {
        }
        final byte[] tail = Files.readAllBytes(bam);
        final byte[] eofBlock = BlockCompressedStreamConstants.EMPTY_GZIP_BLOCK;
        final byte[] fileTail = Arrays.copyOfRange(tail, tail.length - eofBlock.length, tail.length);
        Assert.assertEquals(fileTail, eofBlock, "data stream should be properly closed with EOF block");
        IOUtil.recursiveDelete(dir);
    }
}
