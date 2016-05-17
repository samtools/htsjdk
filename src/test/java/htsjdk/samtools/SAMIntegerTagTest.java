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

import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.CloserUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Confirm that integer tag types are stored and retrieved properly.
 *
 * @author alecw@broadinstitute.org
 */
public class SAMIntegerTagTest {
    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/SAMIntegerTagTest");

    private static final String BYTE_TAG = "BY";
    private static final String SHORT_TAG = "SH";
    private static final String INTEGER_TAG = "IN";
    private static final String UNSIGNED_INTEGER_TAG = "UI";
    private static final String STRING_TAG = "ST";

    private static final long TOO_LARGE_UNSIGNED_INT_VALUE = BinaryCodec.MAX_UINT + 1L;

    enum FORMAT {SAM, BAM, CRAM}

    @Test
    public void testBAM() throws Exception {
        final SAMRecord rec = writeAndReadSamRecord("bam");
        Assert.assertTrue(rec.getAttribute(BYTE_TAG) instanceof Integer);
        Assert.assertEquals(((Number) rec.getAttribute(BYTE_TAG)).intValue(), 1);
        Assert.assertTrue(rec.getAttribute(SHORT_TAG) instanceof Integer);
        Assert.assertEquals(((Number) rec.getAttribute(SHORT_TAG)).intValue(), 1);
        Assert.assertTrue(rec.getAttribute(INTEGER_TAG) instanceof Integer);
        Assert.assertEquals(((Number) rec.getAttribute(INTEGER_TAG)).intValue(), 1);
    }

    @Test
    public void testSAM() throws Exception {
        final SAMRecord rec = writeAndReadSamRecord("sam");
        Assert.assertTrue(rec.getAttribute(BYTE_TAG) instanceof Integer);
        Assert.assertEquals(((Number) rec.getAttribute(BYTE_TAG)).intValue(), 1);
        Assert.assertTrue(rec.getAttribute(SHORT_TAG) instanceof Integer);
        Assert.assertEquals(((Number) rec.getAttribute(SHORT_TAG)).intValue(), 1);
        Assert.assertTrue(rec.getAttribute(INTEGER_TAG) instanceof Integer);
        Assert.assertEquals(((Number) rec.getAttribute(INTEGER_TAG)).intValue(), 1);
    }

    @Test
    public void testUnsignedIntegerSAM() throws Exception {
        final SAMRecord rec = createSamRecord();
        final long val = 1l + Integer.MAX_VALUE;
        rec.setAttribute(UNSIGNED_INTEGER_TAG, val);
        final Object roundTripValue = rec.getAttribute(UNSIGNED_INTEGER_TAG);
        Assert.assertTrue(roundTripValue instanceof Long);
        Assert.assertEquals(((Long)roundTripValue).longValue(), val);
    }

    @Test
    public void testGetTypedAttributeMethods() throws Exception {
        final SAMRecord rec = writeAndReadSamRecord("bam");
        Assert.assertEquals(rec.getByteAttribute(INTEGER_TAG).intValue(), 1);
        Assert.assertEquals(rec.getShortAttribute(INTEGER_TAG).intValue(), 1);
        Assert.assertEquals(rec.getIntegerAttribute(INTEGER_TAG).intValue(), 1);
    }

    /**
     * Should be an exception if a typed attribute call is made for the wrong type.
     */
    @Test(expectedExceptions = RuntimeException.class)
    public void testGetTypedAttributeForWrongType() throws Exception {
        final SAMRecord rec = createSamRecord();
        rec.setAttribute(STRING_TAG, "Hello, World!");
        writeAndReadSamRecord("bam", rec);
        rec.getIntegerAttribute(STRING_TAG);
        Assert.fail("Exception should have been thrown.");
    }

    /**
     * Should be an exception if a typed attribute call is made for a value that cannot
     * be coerced into the correct type.
     * This test is a little lame because a RuntimeException could be thrown for some other reason.
     */
    @Test(expectedExceptions = RuntimeException.class)
    public void testGetTypedAttributeOverflow() throws Exception {
        final SAMRecord rec = createSamRecord();
        rec.setAttribute(INTEGER_TAG, Integer.MAX_VALUE);
        writeAndReadSamRecord("bam", rec);
        rec.getShortAttribute(INTEGER_TAG);
        Assert.fail("Exception should have been thrown.");
    }

    /**
     * Should be an exception if a typed attribute call is made for a value that cannot
     * be coerced into the correct type.
     * This test is a little lame because a RuntimeException could be thrown for some other reason.
     */
    @Test(expectedExceptions = RuntimeException.class)
    public void testGetTypedAttributeUnerflow() throws Exception {
        final SAMRecord rec = createSamRecord();
        rec.setAttribute(INTEGER_TAG, Integer.MIN_VALUE);
        writeAndReadSamRecord("bam", rec);
        rec.getShortAttribute(INTEGER_TAG);
        Assert.fail("Exception should have been thrown.");
    }

    /**
     * Create a SAMRecord with integer tags of various sizes, write to a file, and read it back.
     *
     * @param format "sam" or "bam".
     * @return The record after having being read from file.
     */
    private SAMRecord writeAndReadSamRecord(final String format) throws IOException {
        SAMRecord rec = createSamRecord();
        rec.setAttribute(BYTE_TAG, (byte) 1);
        rec.setAttribute(SHORT_TAG, (short) 1);
        rec.setAttribute(INTEGER_TAG, 1);
        rec = writeAndReadSamRecord(format, rec);
        return rec;
    }

    /**
     * Write a SAMRecord to a SAM file in the given format, and read it back.
     *
     * @param format "sam" or "bam".
     * @param rec    The record to write.
     * @return The same record, after having being written and read back.
     */
    private SAMRecord writeAndReadSamRecord(final String format, SAMRecord rec) throws IOException {
        final File bamFile = File.createTempFile("htsjdk-writeAndReadSamRecord.", "." + format);
        final SAMFileWriter bamWriter = new SAMFileWriterFactory().makeSAMOrBAMWriter(rec.getHeader(), false, bamFile);
        bamWriter.addAlignment(rec);
        bamWriter.close();
        final SamReader reader = SamReaderFactory.makeDefault().open(bamFile);
        rec = reader.iterator().next();
        reader.close();
        bamFile.delete();
        return rec;
    }

    private SAMRecord createSamRecord() {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.unsorted);
        builder.addFrag("readA", 20, 140, false);
        return builder.iterator().next();
    }

    private static SamInputResource createSamForIntAttr(long value) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        ps.println("@HD\tVN:1.0");
        ps.print("1\t4\t*\t0\t0\t*\t*\t0\t0\tA\t<\tUI:i:");
        ps.println(value);
        ps.close();

        return new SamInputResource(new InputStreamInputResource(new ByteArrayInputStream(baos.toByteArray())));
    }

    @Test
    public void testGoodSamStrict() throws IOException {
        final SamReaderFactory factory = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT);

        Assert.assertEquals(0, ((Number) factory.open(createSamForIntAttr(0)).iterator().next().getAttribute("UI")).intValue());
        Assert.assertEquals(-1, ((Number) factory.open(createSamForIntAttr(-1)).iterator().next().getAttribute("UI")).intValue());
        Assert.assertEquals(Integer.MIN_VALUE, ((Number) factory.open(createSamForIntAttr(Integer.MIN_VALUE)).iterator().next().getAttribute("UI")).intValue());
        Assert.assertEquals(Integer.MAX_VALUE, ((Number) factory.open(createSamForIntAttr(Integer.MAX_VALUE)).iterator().next().getAttribute("UI")).intValue());
        Assert.assertEquals(1L + (long) Integer.MAX_VALUE, ((Number) factory.open(createSamForIntAttr(1L + (long) Integer.MAX_VALUE)).iterator().next().getAttribute("UI")).longValue());
        Assert.assertEquals(BinaryCodec.MAX_UINT, ((Number) factory.open(createSamForIntAttr(BinaryCodec.MAX_UINT)).iterator().next().getAttribute("UI")).longValue());
    }

    @Test(expectedExceptions = SAMException.class)
    public void testBadSamStrict() throws IOException {
        final SamReader reader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT).open(createSamForIntAttr(BinaryCodec.MAX_UINT + 1L));
        reader.iterator().next();
    }

    @Test
    public void testBadSamSilent() throws IOException {
        final SamReader reader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT).open(createSamForIntAttr(BinaryCodec.MAX_UINT + 1L));
        reader.iterator().next();
    }

    @DataProvider(name = "legalIntegerAttributesFiles")
    public Object[][] getLegalIntegerAttributesFiles() {
        return new Object[][] {
                { new File(TEST_DATA_DIR, "variousAttributes.sam") },
                { new File(TEST_DATA_DIR, "variousAttributes.bam") }
        };
    }

    @Test(dataProvider = "legalIntegerAttributesFiles")
    public void testLegalIntegerAttributesFilesStrict( final File inputFile ) {
        final SamReader reader = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.EAGERLY_DECODE)
                .validationStringency(ValidationStringency.STRICT)
                .open(inputFile);

        final SAMRecord rec = reader.iterator().next();
        final Map<String, Number> expectedTags = new HashMap<String, Number>();
        expectedTags.put("SB", -128);
        expectedTags.put("UB", 129);
        expectedTags.put("SS", 32767);
        expectedTags.put("US", 65535);
        expectedTags.put("SI", 2147483647);
        expectedTags.put("I2", -2147483647);
        expectedTags.put("UI", 4294967295L);
        for (final Map.Entry<String, Number> entry : expectedTags.entrySet()) {
            final Object value = rec.getAttribute(entry.getKey());
            Assert.assertTrue(((Number) value).longValue() == entry.getValue().longValue());
        }
        CloserUtil.close(reader);
    }

    @DataProvider(name = "valid_set")
    public static Object[][] valid_set() {
        List<Object[]> params = new ArrayList<Object[]>();
        for (FORMAT format:FORMAT.values()) {
            for (ValidationStringency stringency:ValidationStringency.values()) {
                params.add(new Object[]{0, format, stringency});
                params.add(new Object[]{1, format, stringency});
                params.add(new Object[]{-1, format, stringency});
                params.add(new Object[]{Integer.MIN_VALUE, format, stringency});
                params.add(new Object[]{Integer.MAX_VALUE, format, stringency});

                params.add(new Object[]{1L, format, stringency});
                params.add(new Object[]{-1L, format, stringency});
                params.add(new Object[]{(long)Integer.MAX_VALUE+1L, format, stringency});
                params.add(new Object[]{BinaryCodec.MAX_UINT, format, stringency});
            }
        }

        return params.toArray(new Object[3][params.size()]);
    }

    @DataProvider(name = "invalid_set")
    public static Object[][] invalid_set() {
        List<Object[]> params = new ArrayList<Object[]>();
        for (FORMAT format:FORMAT.values()) {
            for (ValidationStringency stringency:ValidationStringency.values()) {
                params.add(new Object[]{(long)Integer.MIN_VALUE -1L, format, stringency});
                params.add(new Object[]{TOO_LARGE_UNSIGNED_INT_VALUE, format, stringency});
            }
        }

        return params.toArray(new Object[3][params.size()]);
    }

    @Test(dataProvider = "valid_set")
    public void testValidIntegerAttributeRoundtrip(final long value, final FORMAT format, ValidationStringency validationStringency) throws IOException {
        testRoundtripIntegerAttribute(value, format, validationStringency);
    }

    @Test(dataProvider = "invalid_set", expectedExceptions = RuntimeException.class)
    public void testInvalidIntegerAttributeRoundtrip(final long value, final FORMAT format, ValidationStringency validationStringency) throws IOException {
        testRoundtripIntegerAttribute(value, format, validationStringency);
    }

    private void testRoundtripIntegerAttribute(final Number value, final FORMAT format, ValidationStringency validationStringency) throws IOException {
        final SAMFileHeader header = new SAMFileHeader();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        final SAMFileWriter w;
        switch (format) {
            case SAM:
                w = new SAMFileWriterFactory().makeSAMWriter(header, false, baos);
                break;
            case BAM:
                w = new SAMFileWriterFactory().makeBAMWriter(header, false, baos);
                break;
            case CRAM:
                w = new SAMFileWriterFactory().makeCRAMWriter(header, baos, null);
                break;
            default:
                throw new RuntimeException("Unknown format: " + format);
        }

        final SAMRecord record = new SAMRecord(header);
        record.setAttribute("UI", value);
        record.setReadName("1");
        record.setReadUnmappedFlag(true);
        record.setReadBases("A".getBytes());
        record.setBaseQualityString("!");
        Assert.assertEquals(value, record.getAttribute("UI"));

        w.addAlignment(record);
        w.close();

        final SamReader reader = SamReaderFactory.make().validationStringency(validationStringency).referenceSource(new ReferenceSource((File)null)).
                open(SamInputResource.of(new ByteArrayInputStream(baos.toByteArray())));
        final SAMRecordIterator iterator = reader.iterator();
        Assert.assertTrue(iterator.hasNext());
        final SAMRecord record2 = iterator.next();
        final Number returnedValue = (Number) record2.getAttribute("UI");
        Assert.assertEquals(value.longValue(), returnedValue.longValue());
    }
}
