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
import htsjdk.samtools.util.CloserUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SAMTextReaderTest extends HtsjdkTest {
    private static final String ARRAY_TAG = "xa";

    // Simple input, spot check that parsed correctly, and make sure nothing blows up.
    @Test
    public void testBasic() throws Exception {
        final String seq1 = "AGCTTAGCTAGCTACCTATATCTTGGTCTTGGCCG";
        final String seq2 = "ACCTATATCTTGGCCTTGGCCGATGCGGCCTTGCA";
        final String qual1 = "<<<<<<<<<<<<<<<<<<<<<:<9/,&,22;;<<<";
        final String qual2 = "<<<<<;<<<<7;:<<<6;<<<<<<<<<<<<7<<<<";
        final String fileFormatVersion = "1.0";
        final String sequence = "chr20";
        final int sequenceLength = 62435964;
        final String charTag = "XC";
        final char charValue = 'q';
        final String intTag = "XI";
        final int intValue = 12345;
        final String floatTag = "XF";
        final float floatValue = 1.2345f;
        final String stringTag = "XS";
        final String stringValue = "Hi,Mom!";
        final String samExample = "@HD\tVN:" + fileFormatVersion + "\t" + charTag + ":" + charValue + "\n" + "@SQ\tSN:"
                + sequence + "\tAS:HG18\tLN:" + sequenceLength + "\t" + intTag + ":" + intValue + "\n"
                + "@RG\tID:L1\tPU:SC_1_10\tLB:SC_1\tSM:NA12891"
                + "\t" + floatTag + ":" + floatValue + "\n" + "@RG\tID:L2\tPU:SC_2_12\tLB:SC_2\tSM:NA12891\n"
                + "@PG\tID:0\tVN:1.0\tCL:yo baby\t"
                + stringTag + ":" + stringValue + "\n" + "@PG\tID:2\tVN:1.1\tCL:whassup? ? ? ?\n"
                + "read_28833_29006_6945\t99\tchr20\t28833\t20\t10M1D25M\t=\t28993\t195\t"
                + seq1.toLowerCase()
                + "\t" + qual1 + "\t" + "MF:i:130\tNm:i:1\tH0:i:0\tH1:i:0\tRG:Z:L1\n"
                + "read_28701_28881_323b\t147\tchr20\t28834\t30\t35M\t=\t28701\t-168\t"
                + seq2
                + "\t" + qual2 + "\t" + "MF:i:18\tNm:i:0\tH0:i:1\tH1:i:0\tRG:Z:L2\n";

        final String[] samResults = {
            "read_28833_29006_6945\t99\tchr20\t28833\t20\t10M1D25M\t=\t28993\t195\t" + seq1 + "\t" + qual1
                    + "\tH0:i:0\tH1:i:0\tMF:i:130\tRG:Z:L1\tNm:i:1",
            "read_28701_28881_323b\t147\tchr20\t28834\t30\t35M\t=\t28701\t-168\t" + seq2 + "\t" + qual2
                    + "\tH0:i:1\tH1:i:0\tMF:i:18\tRG:Z:L2\tNm:i:0"
        };

        final SamReader samReader = createSamFileReader(samExample);
        final SAMFileHeader fileHeader = samReader.getFileHeader();

        Assert.assertEquals(fileHeader.getVersion(), fileFormatVersion);
        Assert.assertEquals(fileHeader.getAttribute(charTag), Character.toString(charValue));
        final SAMSequenceRecord sequenceRecord = fileHeader.getSequence(sequence);
        Assert.assertNotNull(sequenceRecord);
        Assert.assertEquals(sequenceRecord.getSequenceLength(), sequenceLength);
        Assert.assertEquals(sequenceRecord.getAttribute(intTag), Integer.toString(intValue));
        Assert.assertEquals(fileHeader.getReadGroup("L1").getAttribute(floatTag), Float.toString(floatValue));
        Assert.assertEquals(fileHeader.getProgramRecord("0").getAttribute(stringTag), stringValue);

        final CloseableIterator<SAMRecord> iterator = samReader.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            final SAMRecord rec = iterator.next();
            Assert.assertEquals(rec.getSAMString(), samResults[i++]);
        }
        iterator.close();
        iterator.close();
        samReader.close();
    }

    private SamReader createSamFileReader(final String samExample) {
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(samExample.getBytes());
        return SamReaderFactory.makeDefault().open(SamInputResource.of(inputStream));
    }

    @Test
    public void testUnmapped() {
        final String alignmentFromKris =
                "0\t4\t*\t0\t0\t*\t*\t0\t0\tGCCTCGTAGTGCGCCATCAGTCTATCGATGTCGTTG\t44\"44===;;;;;;;;;::::88844\"4\"\"\"\"\"\"\"\"\n";
        final SamReader samReader = createSamFileReader(alignmentFromKris);
        final CloseableIterator<SAMRecord> iterator = samReader.iterator();
        while (iterator.hasNext()) {
            iterator.next();
        }
        iterator.close();
        CloserUtil.close(samReader);
    }

    /**
     * Colon separates fields of a text tag, but colon is also valid in a tag value, so assert that works properly.
     */
    @Test
    public void testTagWithColon() {
        // Create a SAMRecord with a String tag containing a colon
        final SAMRecordSetBuilder samBuilder = new SAMRecordSetBuilder();
        samBuilder.addUnmappedFragment("Hi,Mom!");
        final SAMRecord rec = samBuilder.iterator().next();
        final String valueWithColons = "A:B::C:::";
        rec.setAttribute(SAMTag.CQ, valueWithColons);
        // Write the record as SAM Text
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        final SAMFileWriter textWriter = new SAMFileWriterFactory().makeSAMWriter(samBuilder.getHeader(), true, os);
        textWriter.addAlignment(rec);
        textWriter.close();

        final SamReader reader =
                SamReaderFactory.makeDefault().open(SamInputResource.of(new ByteArrayInputStream(os.toByteArray())));
        final SAMRecord recFromText = reader.iterator().next();
        Assert.assertEquals(recFromText.getAttribute(SAMTag.CQ), valueWithColons);
        CloserUtil.close(reader);
    }

    @DataProvider
    public Object[][] getRecordsWithArrays() {
        final String recordBase = "Read\t4\tchr1\t1\t0\t*\t*\t0\t0\tG\t%\t";
        return new Object[][] {
            {recordBase + ARRAY_TAG + ":B:i", new int[0]},
            {recordBase + ARRAY_TAG + ":B:i,", new int[0]},
            {recordBase + ARRAY_TAG + ":B:i,1,2,3,", new int[] {1, 2, 3}},
        };
    }

    @Test(dataProvider = "getRecordsWithArrays")
    public void testSamRecordCanHandleArrays(String samRecord, Object array) {
        final SAMLineParser samLineParser = new SAMLineParser(new SAMFileHeader());
        final SAMRecord record = samLineParser.parseLine(samRecord);
        Assert.assertEquals(record.getAttribute(ARRAY_TAG), array);
    }

    @Test
    public void testFlagAboveSignedIntRangeIsRejected() {
        // The fast FLAG parser must not silently sign-extend values > Integer.MAX_VALUE.
        // The spec restricts FLAG to 16 bits; the legacy path threw, and we want to preserve that.
        final SAMLineParser parser = new SAMLineParser(new SAMFileHeader());
        final String line = "Read\t4294967295\tchr1\t1\t0\t*\t*\t0\t0\t*\t*";
        try {
            parser.parseLine(line);
            Assert.fail("Expected SAMFormatException for FLAG above signed-int range");
        } catch (final SAMFormatException expected) {
            // expected
        }
    }

    @Test
    public void testMrnmEqualsWithRnameNotInDictionaryDoesNotNpe() {
        // Regression: when RNAME is specified but the name is not present in the sequence
        // dictionary, parsing a record with MRNM='=' must not pass a null mate reference name
        // through to setMateReferenceNameAndIndex (which would NPE callers downstream).
        // The mate reference name should mirror the record's reference name.
        final SAMFileHeader header = new SAMFileHeader();
        // Header has no @SQ records, so any RNAME lookup will miss.
        final SAMLineParser parser =
                new SAMLineParser(new DefaultSAMRecordFactory(), ValidationStringency.SILENT, header, null, null);
        // FLAG 99 = paired + proper-pair + mate-reverse + first-of-pair; mate must be mapped.
        final String line = "Read\t99\tchrUnknown\t100\t30\t50M\t=\t150\t100\t"
                + "ACGTACGTACGTACGTACGTACGTACGTACGTACGTACGTACGTACGTAC\t"
                + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
        final SAMRecord record = parser.parseLine(line);
        Assert.assertEquals(record.getReferenceName(), "chrUnknown");
        Assert.assertEquals(record.getMateReferenceName(), "chrUnknown");
    }

    @Test
    public void testErrorMessageReportsCurrentLineAfterMixedParseModes() {
        // Regression test: after a successful parseLine(String) call, parseLineFromBytes on the
        // same parser must produce error messages referencing the BYTE line just handed to it,
        // not a stale line from the earlier call (the error message is reconstructed from the byte
        // range that parseLineFromBytes records on every call).
        final SAMLineParser strict = new SAMLineParser(
                new DefaultSAMRecordFactory(), ValidationStringency.STRICT, new SAMFileHeader(), null, null);
        // Warm up the same parser with a well-formed line through the String API.
        final String good = "Read\t4\tchr1\t1\t0\t*\t*\t0\t0\tG\t%";
        strict.parseLine(good);
        // Now feed an obviously malformed byte line (too few tab-separated fields); STRICT surfaces it.
        final byte[] bad = "Read\tNOT-AN-INT\t".getBytes();
        try {
            strict.parseLineFromBytes(bad, 0, bad.length, 7);
            Assert.fail("Expected an exception");
        } catch (final SAMFormatException e) {
            // The error message must reference the byte line "Read\tNOT-AN-INT\t", not the
            // earlier "good" line.
            final String message = e.getMessage();
            Assert.assertTrue(
                    message.contains("NOT-AN-INT") || message.contains("Read\tNOT-AN-INT"),
                    "Expected error message to reference the byte line; got: " + message);
            Assert.assertFalse(
                    message.contains("chr1"), "Error message must not reference the stale prior line; got: " + message);
        }
    }
}
