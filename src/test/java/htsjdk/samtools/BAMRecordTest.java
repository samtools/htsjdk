package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BAMRecordTest extends HtsjdkTest {

    private static final SAMFileHeader HEADER = new SAMFileHeader();

    /** A record decoded from BAM, with an NM tag and no DT tag. */
    private static BAMRecord decodedRecordWithNm() {
        final SAMRecord rec = new SAMRecord(HEADER);
        rec.setReadName("r1");
        rec.setReadUnmappedFlag(true);
        rec.setReadString("ACGT");
        rec.setBaseQualityString("IIII");
        rec.setAttribute("NM", 1);
        return encodeAndDecode(rec);
    }

    private static BAMRecord encodeAndDecode(final SAMRecord rec) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final BAMRecordCodec encoder = new BAMRecordCodec(HEADER);
        encoder.setOutputStream(bytes);
        encoder.encode(rec);
        final BAMRecordCodec decoder = new BAMRecordCodec(HEADER);
        decoder.setInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        return (BAMRecord) decoder.decode();
    }

    @Test
    public void testRemovingAnAbsentTagKeepsTheBytesTheRecordWasReadFrom() {
        final BAMRecord rec = decodedRecordWithNm();
        final byte[] original = rec.getVariableBinaryRepresentation().clone();
        rec.setAttribute("DT", null);
        Assert.assertEquals(rec.getVariableBinaryRepresentation(), original);
    }

    @Test
    public void testRemovingAPresentTagStopsTheRecordUsingTheBytesItWasReadFrom() {
        final BAMRecord rec = decodedRecordWithNm();
        rec.setAttribute("NM", null);
        Assert.assertNull(rec.getVariableBinaryRepresentation());
    }

    @Test
    public void testARemovedTagIsNotWritten() {
        final BAMRecord rec = decodedRecordWithNm();
        rec.setAttribute("NM", null);
        Assert.assertNull(encodeAndDecode(rec).getAttribute("NM"));
    }
}
