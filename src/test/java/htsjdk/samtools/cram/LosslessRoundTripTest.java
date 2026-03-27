package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.*;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import htsjdk.samtools.util.SequenceUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by vadim on 19/02/2016.
 */
public class LosslessRoundTripTest extends HtsjdkTest {
    @Test
    public void test_MD_NM() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InMemoryReferenceSequenceFile rsf = new InMemoryReferenceSequenceFile();
        rsf.add("1", "AAA".getBytes());
        ReferenceSource source = new ReferenceSource(rsf);

        SAMFileHeader samFileHeader = new SAMFileHeader();
        samFileHeader.addSequence(new SAMSequenceRecord("1", 3));
        samFileHeader.addReadGroup(new SAMReadGroupRecord("some read group"));

        CRAMFileWriter w = new CRAMFileWriter(baos, source, samFileHeader, null);
        SAMRecord record = new SAMRecord(samFileHeader);
        record.setReadName("name");
        record.setAlignmentStart(1);
        record.setReferenceIndex(0);
        record.setCigarString("3M");
        record.setReadUnmappedFlag(false);
        record.setReadBases("AAC".getBytes());
        record.setBaseQualities("!!!".getBytes());

        record.setAttribute("RG", "some read group");
        // setting some bizzar values to provoke test failure if the values are auto-restored while reading CRAM:
        record.setAttribute("MD", "nonsense");
        record.setAttribute("NM", 123);
        w.addAlignment(record);
        w.close();

        byte[] cramBytes = baos.toByteArray();
        InputStream cramInputStream = new ByteArrayInputStream(cramBytes);
        CRAMFileReader reader = new CRAMFileReader(cramInputStream, (File) null, source, ValidationStringency.STRICT);
        final SAMRecordIterator iterator = reader.getIterator();
        Assert.assertTrue(iterator.hasNext());
        SAMRecord record2 = iterator.next();
        Assert.assertNotNull(record2);

        Assert.assertEquals(record2, record);
        reader.close();
    }

    // ---- Mate linking ----

    /**
     * Tests that paired-end reads survive CRAM round-trip with mate fields intact.
     * Exercises {@code Slice.linkMatesWithinSlice()} which links mates as "attached"
     * (storing only the NF offset) rather than "detached" (storing full mate info).
     * Uses {@link SAMRecordSetBuilder} to create properly-paired records with all mate
     * fields set correctly via {@code SamPairUtil.setMateInfo()}.
     */
    @Test
    public void testMateLinking() throws IOException {
        final SAMFileHeader header = buildHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);

        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.coordinate, false);
        builder.setHeader(header);
        builder.setReadLength(10);
        builder.setUnmappedHasBasesAndQualities(true);

        // 1. Normal pair, both mapped — basic attached mate case
        builder.addPair("pair1", 0, 1, 50, false, false, "10M", "10M", false, true, 30);

        // 2. One mate unmapped — mate linking skips unmapped; verifies mate unmapped flag, TLEN=0
        builder.addPair("pairUnmap", 0, 0, 80, 0, false, true, "10M", "10M", false, false, false, false, 30);

        // 3. Unpaired fragment — should pass through unaffected by mate linking
        builder.addFrag("fragment", 0, 60, false, false, "10M", null, 30);

        // 4. Pair + supplementary with same name — only primary pair should be linked
        builder.addPair("suppPair", 0, 100, 150, false, false, "10M", "10M", false, true, 30);
        builder.addFrag("suppPair", 0, 120, false, false, "10M", null, 30, false, true);

        // Sort by coordinate since CRAM expects coordinate order
        final List<SAMRecord> records = new ArrayList<>(builder.getRecords());
        records.sort(header.getSortOrder().getComparatorInstance());

        // Set RG and compute MD/NM on all records so they survive the strip-and-regenerate round-trip.
        // The builder doesn't set RG (we passed addReadGroup=false) or MD/NM.
        for (final SAMRecord rec : records) {
            rec.setAttribute("RG", READ_GROUP);
            if (!rec.getReadUnmappedFlag()) {
                SequenceUtil.calculateMdAndNmTags(rec, REF_BASES, true, true);
            }
        }

        assertRoundTrip("mateLinking", records.toArray(new SAMRecord[0]));
    }

    /**
     * Tests that a mix of records with and without quality scores round-trips correctly.
     * Exercises the FQZComp per-record context handling when some records have zero-length quality data.
     */
    @Test
    public void testMixedQualityPresence() throws IOException {
        final String contig = "1";
        final int refLength = 100;
        final byte[] refBases = new byte[refLength];
        final byte[] pattern = "ACGTACGTAC".getBytes();
        for (int i = 0; i < refLength; i++) {
            refBases[i] = pattern[i % pattern.length];
        }

        final InMemoryReferenceSequenceFile rsf = new InMemoryReferenceSequenceFile();
        rsf.add(contig, refBases);
        final ReferenceSource source = new ReferenceSource(rsf);

        final SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord(contig, refLength));
        header.addReadGroup(new SAMReadGroupRecord("rg1"));
        final String rg = "rg1";

        // 1. Real qualities, perfect match (no read features carry quality)
        final SAMRecord withQualsMatch = makeMappedRecord(header, "qualsMatch", 1, "10M",
                Arrays.copyOfRange(refBases, 0, 10), quals(10), rg, refBases);

        // 2. No qualities, perfect match (no read features at all — featureless case)
        final SAMRecord noQualsMatch = makeMappedRecord(header, "noQualsMatch", 11, "10M",
                Arrays.copyOfRange(refBases, 10, 20), SAMRecord.NULL_QUALS, rg, refBases);

        // 3. No qualities, mismatches (ReadBase features with embedded quality — synthetic 0xFF path)
        final byte[] mismatchBases = Arrays.copyOfRange(refBases, 20, 25);
        for (int i = 0; i < mismatchBases.length; i++) mismatchBases[i] = SequenceUtil.complement(mismatchBases[i]);
        final SAMRecord noQualsMismatch = makeMappedRecord(header, "noQualsMis", 21, "5M",
                mismatchBases, SAMRecord.NULL_QUALS, rg, refBases);

        // 4. Real qualities, mismatches (ReadBase features with real quality — contrast with #3)
        final byte[] mismatchBases2 = Arrays.copyOfRange(refBases, 25, 30);
        for (int i = 0; i < mismatchBases2.length; i++) mismatchBases2[i] = SequenceUtil.complement(mismatchBases2[i]);
        final SAMRecord withQualsMismatch = makeMappedRecord(header, "qualsMis", 26, "5M",
                mismatchBases2, quals(5), rg, refBases);

        // 5. No qualities, insertion (Insertion feature, no quality-carrying features)
        final byte[] insBases = new byte[12];
        System.arraycopy(refBases, 30, insBases, 0, 5);
        insBases[5] = 'G'; insBases[6] = 'G';
        System.arraycopy(refBases, 35, insBases, 7, 5);
        final SAMRecord noQualsInsertion = makeMappedRecord(header, "noQualsIns", 31, "5M2I5M",
                insBases, SAMRecord.NULL_QUALS, rg, refBases);

        // 6. No qualities, soft clip + matches
        final byte[] scBases = new byte[10];
        Arrays.fill(scBases, 0, 3, (byte) 'T');
        System.arraycopy(refBases, 40, scBases, 3, 7);
        final SAMRecord noQualsSoftClip = makeMappedRecord(header, "noQualsSC", 41, "3S7M",
                scBases, SAMRecord.NULL_QUALS, rg, refBases);

        final SAMRecord[] records = {
                withQualsMatch, noQualsMatch, noQualsMismatch, withQualsMismatch, noQualsInsertion, noQualsSoftClip
        };

        // Write
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (CRAMFileWriter writer = new CRAMFileWriter(baos, source, header, null)) {
            for (final SAMRecord rec : records) {
                writer.addAlignment(rec);
            }
        }

        // Read back and verify
        try (CRAMFileReader reader = new CRAMFileReader(
                new ByteArrayInputStream(baos.toByteArray()), (File) null, source, ValidationStringency.STRICT)) {
            final SAMRecordIterator iterator = reader.getIterator();
            for (int i = 0; i < records.length; i++) {
                Assert.assertTrue(iterator.hasNext(), "mixedQualityPresence: too few records returned from CRAM");
                final SAMRecord actual = iterator.next();
                Assert.assertEquals(actual, records[i], "mixedQualityPresence: record " + i + " mismatch");
            }
            Assert.assertFalse(iterator.hasNext(), "mixedQualityPresence: extra records returned from CRAM");
        }
    }

    private static SAMRecord makeMappedRecord(final SAMFileHeader header, final String name, final int alignmentStart,
                                              final String cigarString, final byte[] readBases, final byte[] qualities,
                                              final String readGroup, final byte[] refBases) {
        final SAMRecord rec = new SAMRecord(header);
        rec.setReadName(name);
        rec.setAlignmentStart(alignmentStart);
        rec.setReferenceIndex(0);
        rec.setCigarString(cigarString);
        rec.setReadUnmappedFlag(false);
        rec.setReadBases(readBases);
        rec.setBaseQualities(qualities);
        rec.setAttribute("RG", readGroup);
        SequenceUtil.calculateMdAndNmTags(rec, refBases, true, true);
        return rec;
    }

    private static byte[] quals(final int length) {
        final byte[] q = new byte[length];
        Arrays.fill(q, (byte) 30);
        return q;
    }
}
