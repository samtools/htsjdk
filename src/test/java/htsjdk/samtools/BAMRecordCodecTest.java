package htsjdk.samtools;

import htsjdk.HtsjdkTest;
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
}
