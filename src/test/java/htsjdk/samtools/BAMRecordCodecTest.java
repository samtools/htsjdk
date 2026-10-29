package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.RuntimeEOFException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BAMRecordCodecTest extends HtsjdkTest {

    /** Encodes {@code record} with a fresh codec and returns the bytes as Latin-1 text, so tags can be searched for. */
    private static String encode(final SAMFileHeader header, final SAMRecord record) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final BAMRecordCodec codec = new BAMRecordCodec(header);
        codec.setOutputStream(bytes);
        codec.encode(record);
        return new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private static SAMRecord unmappedRecord(final SAMFileHeader header, final String readName) {
        final SAMRecord record = new SAMRecord(header);
        record.setReadName(readName);
        record.setReadUnmappedFlag(true);
        record.setReadString("ACGT");
        record.setBaseQualityString("IIII");
        return record;
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testStringTagWithCharAboveFFIsRejected() {
        final SAMFileHeader header = new SAMFileHeader();
        final SAMRecord record = unmappedRecord(header, "read1");
        // U+0100 would lose its high byte and end the string early as a NUL.
        record.setAttribute("XS", "ab\u0100XT");
        encode(header, record);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testStringTagWithNulIsRejected() {
        final SAMFileHeader header = new SAMFileHeader();
        final SAMRecord record = unmappedRecord(header, "read1");
        record.setAttribute("XS", "ab\0XT");
        encode(header, record);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testReadNameWithCharAboveFFIsRejected() {
        final SAMFileHeader header = new SAMFileHeader();
        encode(header, unmappedRecord(header, "read\u010D"));
    }

    @Test
    public void testStringTagWithLatin1CharIsStoredAsOneByte() {
        final SAMFileHeader header = new SAMFileHeader();
        final SAMRecord record = unmappedRecord(header, "read1");
        record.setAttribute("XS", "café");
        Assert.assertTrue(encode(header, record).contains("XSZcafé\0"));
    }

    @Test
    public void testStringTagWithTabIsStored() {
        final SAMFileHeader header = new SAMFileHeader();
        final SAMRecord record = unmappedRecord(header, "read1");
        record.setAttribute("XS", "a\tb");
        Assert.assertTrue(encode(header, record).contains("XSZa\tb\0"));
    }

    @Test
    public void testUracilBasesRoundTripAsThymine() {
        final SAMFileHeader header = new SAMFileHeader();
        final SAMRecord record = unmappedRecord(header, "read1");
        record.setReadBases("ACGUuACGT".getBytes());
        record.setBaseQualityString("IIIIIIIII");

        final BAMRecordCodec codec = new BAMRecordCodec(header);
        codec.setInputStream(new ByteArrayInputStream(encode(header, record).getBytes(StandardCharsets.ISO_8859_1)));
        Assert.assertEquals(codec.decode().getReadString(), "ACGTTACGT");
    }

    @Test
    public void testCigarTooLongForBamIsStoredAsUnsignedIntArrayInCgTag() {
        final SAMFileHeader header =
                new SAMFileHeader(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1", 1_000_000))));
        // Alternating 1M1D ending in 1M, with more operators than the BAM CIGAR field can hold.
        final List<CigarElement> elements = new ArrayList<>();
        for (int i = 0; i <= BAMRecord.MAX_CIGAR_OPERATORS / 2; i++) {
            elements.add(new CigarElement(1, CigarOperator.M));
            elements.add(new CigarElement(1, CigarOperator.D));
        }
        elements.add(new CigarElement(1, CigarOperator.M));
        final Cigar cigar = new Cigar(elements);
        Assert.assertTrue(cigar.numCigarElements() > BAMRecord.MAX_CIGAR_OPERATORS);

        final SAMRecord record = new SAMRecord(header);
        record.setReadName("long");
        record.setReferenceIndex(0);
        record.setAlignmentStart(1);
        record.setCigar(cigar);
        final byte[] bases = new byte[cigar.getReadLength()];
        Arrays.fill(bases, (byte) 'A');
        record.setReadBases(bases);
        record.setBaseQualities(new byte[bases.length]);

        final String encoded = encode(header, record);
        Assert.assertTrue(encoded.contains("CGBI"), "CG tag is not stored as B:I");
        Assert.assertFalse(encoded.contains("CGBi"), "CG tag is stored as B:i");
    }

    private static SAMRecord decode(final SAMFileHeader header, final byte[] bytes) {
        final BAMRecordCodec codec = new BAMRecordCodec(header);
        codec.setInputStream(new ByteArrayInputStream(bytes));
        return codec.decode();
    }

    @Test
    public void testFixedLengthFieldsRoundTripAtTheEndsOfTheirRanges() {
        final SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord("chr1", 1_000_000));
        header.addSequence(new SAMSequenceRecord("chr2", 1_000_000));
        final SAMRecord record = new SAMRecord(header);
        record.setReadName("r1");
        record.setReferenceIndex(1);
        record.setAlignmentStart(999_000);
        record.setMappingQuality(255);
        record.setCigarString("4M");
        // Every flag bit, so the unsigned 16-bit field is read as unsigned
        record.setFlags(0xFFFF);
        record.setReadString("ACGT");
        record.setBaseQualityString("IIII");
        record.setMateReferenceIndex(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
        record.setMateAlignmentStart(SAMRecord.NO_ALIGNMENT_START);
        record.setInferredInsertSize(-123_456);

        final SAMRecord decoded = decode(header, encode(header, record).getBytes(StandardCharsets.ISO_8859_1));
        Assert.assertEquals(decoded.getReferenceIndex().intValue(), 1);
        Assert.assertEquals(decoded.getAlignmentStart(), 999_000);
        Assert.assertEquals(decoded.getMappingQuality(), 255);
        Assert.assertEquals(decoded.getFlags(), 0xFFFF);
        Assert.assertEquals(decoded.getReadLength(), 4);
        Assert.assertEquals(decoded.getCigarString(), "4M");
        Assert.assertEquals(decoded.getReadName(), "r1");
        Assert.assertEquals(decoded.getMateReferenceIndex().intValue(), SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
        Assert.assertEquals(decoded.getMateAlignmentStart(), SAMRecord.NO_ALIGNMENT_START);
        Assert.assertEquals(decoded.getInferredInsertSize(), -123_456);
    }

    @Test(expectedExceptions = RuntimeEOFException.class)
    public void testARecordCutShortInItsFixedLengthFieldsFailsWithEof() {
        final SAMFileHeader header = new SAMFileHeader();
        final byte[] bytes = encode(header, unmappedRecord(header, "r1")).getBytes(StandardCharsets.ISO_8859_1);
        // block_size, then 20 of the 32 bytes of fixed-length fields
        decode(header, Arrays.copyOf(bytes, 4 + 20));
    }
}
