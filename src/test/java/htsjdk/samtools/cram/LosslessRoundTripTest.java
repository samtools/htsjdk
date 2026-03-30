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
 * Round-trip tests that write SAMRecords through the CRAM encode/decode pipeline and verify
 * that bases, qualities, CIGAR, MD, NM, and alignment fields are preserved.  These exercise
 * the fused {@code restoreBasesAndTags} method with a variety of read features and edge cases.
 */
public class LosslessRoundTripTest extends HtsjdkTest {

    // A 200bp reference sequence with enough variety to detect mismatches.
    // Uses a repeating pattern so we can easily predict what the reference base is at any position.
    private static final String CONTIG = "1";
    private static final int REF_LENGTH = 200;
    private static final byte[] REF_BASES = buildReference();
    private static final String READ_GROUP = "rg1";

    private static byte[] buildReference() {
        final byte[] bases = new byte[REF_LENGTH];
        final byte[] pattern = "ACGTACGTAC".getBytes();
        for (int i = 0; i < REF_LENGTH; i++) {
            bases[i] = pattern[i % pattern.length];
        }
        return bases;
    }

    /** Round-trips one or more SAMRecords through CRAM and verifies equality. */
    private void assertRoundTrip(final String testName, final SAMRecord... records) throws IOException {
        final InMemoryReferenceSequenceFile rsf = new InMemoryReferenceSequenceFile();
        rsf.add(CONTIG, REF_BASES);
        final ReferenceSource source = new ReferenceSource(rsf);

        final SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord(CONTIG, REF_LENGTH));
        header.addReadGroup(new SAMReadGroupRecord(READ_GROUP));

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (CRAMFileWriter writer = new CRAMFileWriter(baos, source, header, null)) {
            for (final SAMRecord record : records) {
                writer.addAlignment(record);
            }
        }

        try (CRAMFileReader reader = new CRAMFileReader(
                new ByteArrayInputStream(baos.toByteArray()), (File) null, source, ValidationStringency.STRICT)) {
            final SAMRecordIterator iterator = reader.getIterator();
            for (int i = 0; i < records.length; i++) {
                Assert.assertTrue(iterator.hasNext(), testName + ": too few records returned from CRAM");
                final SAMRecord actual = iterator.next();
                Assert.assertEquals(actual, records[i], testName + ": record " + i + " mismatch");
            }
            Assert.assertFalse(iterator.hasNext(), testName + ": extra records returned from CRAM");
        }
    }

    /** Creates a mapped SAMRecord with the given CIGAR, filling bases from the reference or with provided bases. */
    private SAMRecord createRecord(final SAMFileHeader header, final String name, final int alignmentStart,
                                   final String cigarString, final byte[] readBases, final byte[] qualities) {
        final SAMRecord record = new SAMRecord(header);
        record.setReadName(name);
        record.setAlignmentStart(alignmentStart);
        record.setReferenceIndex(0);
        record.setCigarString(cigarString);
        record.setReadUnmappedFlag(false);
        record.setReadBases(readBases);
        record.setBaseQualities(qualities);
        record.setAttribute("RG", READ_GROUP);

        // Compute and set correct MD/NM so they survive the strip-and-regenerate round-trip
        SequenceUtil.calculateMdAndNmTags(record, REF_BASES, true, true);
        return record;
    }

    /** Creates a mapped SAMRecord with no quality scores (SAM '*'). */
    private SAMRecord createRecordNoQuals(final SAMFileHeader header, final String name, final int alignmentStart,
                                          final String cigarString, final byte[] readBases) {
        return createRecord(header, name, alignmentStart, cigarString, readBases, SAMRecord.NULL_QUALS);
    }

    /** Builds a SAMFileHeader consistent with our reference. */
    private SAMFileHeader buildHeader() {
        final SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord(CONTIG, REF_LENGTH));
        header.addReadGroup(new SAMReadGroupRecord(READ_GROUP));
        return header;
    }

    /** Returns bases from the reference at the given 1-based position for the given length. */
    private byte[] refBases(final int start1Based, final int length) {
        final byte[] bases = new byte[length];
        System.arraycopy(REF_BASES, start1Based - 1, bases, 0, length);
        return bases;
    }

    /** Returns a quality array of the given length with all values set to 30. */
    private byte[] quals(final int length) {
        final byte[] q = new byte[length];
        java.util.Arrays.fill(q, (byte) 30);
        return q;
    }

    /** Returns read bases that differ from the reference at every position (each base complemented). */
    private byte[] mismatchBases(final int start1Based, final int length) {
        final byte[] bases = refBases(start1Based, length);
        for (int i = 0; i < bases.length; i++) {
            bases[i] = SequenceUtil.complement(bases[i]);
        }
        return bases;
    }

    // ---- Basic feature coverage ----

    @Test
    public void testPerfectMatch() throws IOException {
        final SAMFileHeader header = buildHeader();
        final SAMRecord rec = createRecord(header, "perfectMatch", 1, "10M", refBases(1, 10), quals(10));
        assertRoundTrip("perfectMatch", rec);
    }

    @Test
    public void testAllMismatches() throws IOException {
        final SAMFileHeader header = buildHeader();
        final SAMRecord rec = createRecord(header, "allMis", 1, "5M", mismatchBases(1, 5), quals(5));
        assertRoundTrip("allMismatches", rec);
    }

    @Test
    public void testSingleInsertion() throws IOException {
        final SAMFileHeader header = buildHeader();
        // 5M2I5M: 5 ref-matching bases, 2 inserted bases, 5 more ref-matching bases
        final byte[] bases = new byte[12];
        System.arraycopy(REF_BASES, 0, bases, 0, 5);          // pos 1-5 match ref
        bases[5] = 'G'; bases[6] = 'G';                        // 2 inserted bases
        System.arraycopy(REF_BASES, 5, bases, 7, 5);           // pos 6-10 match ref
        final SAMRecord rec = createRecord(header, "ins", 1, "5M2I5M", bases, quals(12));
        assertRoundTrip("singleInsertion", rec);
    }

    @Test
    public void testSingleDeletion() throws IOException {
        final SAMFileHeader header = buildHeader();
        // 5M3D5M: 5 ref-matching bases, 3 deleted ref bases, 5 more ref-matching (from pos 9)
        final byte[] bases = new byte[10];
        System.arraycopy(REF_BASES, 0, bases, 0, 5);          // pos 1-5 match ref
        System.arraycopy(REF_BASES, 8, bases, 5, 5);          // pos 9-13 match ref (after 3bp deletion)
        final SAMRecord rec = createRecord(header, "del", 1, "5M3D5M", bases, quals(10));
        assertRoundTrip("singleDeletion", rec);
    }

    @Test
    public void testSoftClipAtStart() throws IOException {
        final SAMFileHeader header = buildHeader();
        // 3S7M: 3 soft-clipped bases + 7 matching bases starting at alignment pos 1
        final byte[] bases = new byte[10];
        bases[0] = 'T'; bases[1] = 'T'; bases[2] = 'T';       // soft-clipped (arbitrary)
        System.arraycopy(REF_BASES, 0, bases, 3, 7);           // pos 1-7 match ref
        final SAMRecord rec = createRecord(header, "sClipStart", 1, "3S7M", bases, quals(10));
        assertRoundTrip("softClipAtStart", rec);
    }

    @Test
    public void testSoftClipAtEnd() throws IOException {
        final SAMFileHeader header = buildHeader();
        // 7M3S: 7 matching bases + 3 soft-clipped
        final byte[] bases = new byte[10];
        System.arraycopy(REF_BASES, 0, bases, 0, 7);           // pos 1-7 match ref
        bases[7] = 'T'; bases[8] = 'T'; bases[9] = 'T';       // soft-clipped
        final SAMRecord rec = createRecord(header, "sClipEnd", 1, "7M3S", bases, quals(10));
        assertRoundTrip("softClipAtEnd", rec);
    }

    @Test
    public void testSoftClipsAtBothEnds() throws IOException {
        final SAMFileHeader header = buildHeader();
        // 2S6M2S
        final byte[] bases = new byte[10];
        bases[0] = 'T'; bases[1] = 'T';                        // leading soft-clip
        System.arraycopy(REF_BASES, 0, bases, 2, 6);           // pos 1-6 match ref
        bases[8] = 'T'; bases[9] = 'T';                        // trailing soft-clip
        final SAMRecord rec = createRecord(header, "sClipBoth", 1, "2S6M2S", bases, quals(10));
        assertRoundTrip("softClipsBothEnds", rec);
    }

    @Test
    public void testHardClipAtStart() throws IOException {
        final SAMFileHeader header = buildHeader();
        // 3H7M: hard-clipped bases don't appear in read bases
        final SAMRecord rec = createRecord(header, "hClipStart", 1, "3H7M", refBases(1, 7), quals(7));
        assertRoundTrip("hardClipAtStart", rec);
    }

    @Test
    public void testHardClipsAtBothEnds() throws IOException {
        final SAMFileHeader header = buildHeader();
        // 3H4M3H
        final SAMRecord rec = createRecord(header, "hClipBoth", 1, "3H4M3H", refBases(1, 4), quals(4));
        assertRoundTrip("hardClipsBothEnds", rec);
    }

    // ---- Feature interactions and edge cases ----

    @Test
    public void testDeletionThenInsertion() throws IOException {
        final SAMFileHeader header = buildHeader();
        // 5M2D2I5M: position tracking edge case — deletion advances ref, insertion advances read
        final byte[] bases = new byte[12];
        System.arraycopy(REF_BASES, 0, bases, 0, 5);           // pos 1-5 match ref
        bases[5] = 'G'; bases[6] = 'G';                        // 2 inserted bases
        System.arraycopy(REF_BASES, 7, bases, 7, 5);           // pos 8-12 match ref (after 2bp deletion)
        final SAMRecord rec = createRecord(header, "delIns", 1, "5M2D2I5M", bases, quals(12));
        assertRoundTrip("deletionThenInsertion", rec);
    }

    @Test
    public void testInsertionThenDeletion() throws IOException {
        final SAMFileHeader header = buildHeader();
        // 5M2I2D5M
        final byte[] bases = new byte[12];
        System.arraycopy(REF_BASES, 0, bases, 0, 5);           // pos 1-5 match ref
        bases[5] = 'G'; bases[6] = 'G';                        // 2 inserted bases
        System.arraycopy(REF_BASES, 7, bases, 7, 5);           // pos 8-12 match ref (after 2bp deletion)
        final SAMRecord rec = createRecord(header, "insDel", 1, "5M2I2D5M", bases, quals(12));
        assertRoundTrip("insertionThenDeletion", rec);
    }

    @Test
    public void testMultipleInsertionsAndDeletions() throws IOException {
        final SAMFileHeader header = buildHeader();
        // 3M1I3M2D3M1I2M: read bases = 3+1+3+3+1+2 = 13, ref consumed = 3+3+2(del)+3+2 = 13
        final byte[] bases = new byte[13];
        int readPos = 0;
        System.arraycopy(REF_BASES, 0, bases, readPos, 3);     // 3M from ref pos 1
        readPos += 3;
        bases[readPos++] = 'G';                                  // 1I
        System.arraycopy(REF_BASES, 3, bases, readPos, 3);     // 3M from ref pos 4
        readPos += 3;
        // 2D skips ref pos 7-8
        System.arraycopy(REF_BASES, 8, bases, readPos, 3);     // 3M from ref pos 9
        readPos += 3;
        bases[readPos++] = 'G';                                  // 1I
        System.arraycopy(REF_BASES, 11, bases, readPos, 2);    // 2M from ref pos 12
        final SAMRecord rec = createRecord(header, "multiIndel", 1, "3M1I3M2D3M1I2M", bases, quals(13));
        assertRoundTrip("multipleInsertionsAndDeletions", rec);
    }

    @Test
    public void testSoftAndHardClipsCombined() throws IOException {
        final SAMFileHeader header = buildHeader();
        // 2H3S5M3S2H: hard clips don't consume read bases, soft clips do
        final byte[] bases = new byte[11];
        bases[0] = 'T'; bases[1] = 'T'; bases[2] = 'T';       // 3S
        System.arraycopy(REF_BASES, 0, bases, 3, 5);           // 5M from ref pos 1
        bases[8] = 'T'; bases[9] = 'T'; bases[10] = 'T';      // 3S
        final SAMRecord rec = createRecord(header, "hardSoft", 1, "2H3S5M3S2H", bases, quals(11));
        assertRoundTrip("softAndHardClipsCombined", rec);
    }

    @Test
    public void testRefSkipSplicedAlignment() throws IOException {
        final SAMFileHeader header = buildHeader();
        // 5M100N5M: spliced alignment skipping 100 ref bases
        final byte[] bases = new byte[10];
        System.arraycopy(REF_BASES, 0, bases, 0, 5);           // pos 1-5 match ref
        System.arraycopy(REF_BASES, 105, bases, 5, 5);         // pos 106-110 match ref
        final SAMRecord rec = createRecord(header, "splice", 1, "5M100N5M", bases, quals(10));
        assertRoundTrip("refSkipSplicedAlignment", rec);
    }

    // ---- MD/NM computation ----

    @Test
    public void testMismatchesAtReadBoundaries() throws IOException {
        final SAMFileHeader header = buildHeader();
        // 10M with first and last bases mismatched
        final byte[] bases = refBases(1, 10);
        bases[0] = SequenceUtil.complement(bases[0]);           // mismatch at start
        bases[9] = SequenceUtil.complement(bases[9]);           // mismatch at end
        final SAMRecord rec = createRecord(header, "boundaryMis", 1, "10M", bases, quals(10));
        assertRoundTrip("mismatchesAtReadBoundaries", rec);
    }

    @Test
    public void testDeletionMdString() throws IOException {
        final SAMFileHeader header = buildHeader();
        // 3M2D3M: verify deletion produces ^XY in MD string
        final byte[] bases = new byte[6];
        System.arraycopy(REF_BASES, 0, bases, 0, 3);           // pos 1-3 match ref
        System.arraycopy(REF_BASES, 5, bases, 3, 3);           // pos 6-8 match ref
        final SAMRecord rec = createRecord(header, "delMd", 1, "3M2D3M", bases, quals(6));
        // Verify MD was set and contains deletion marker
        final String md = rec.getStringAttribute("MD");
        Assert.assertNotNull(md);
        Assert.assertTrue(md.contains("^"), "deletionMdString: MD should contain deletion marker '^': " + md);
        assertRoundTrip("deletionMdString", rec);
    }

    @Test
    public void testMixedMatchesMismatchesDeletion() throws IOException {
        final SAMFileHeader header = buildHeader();
        // 2M1X2M1D3M: 2 matches, 1 mismatch, 2 matches, 1bp deletion, 3 matches
        // CIGARs with X aren't standard for CRAM encoding — use M and set mismatched bases
        final byte[] bases = new byte[8];
        System.arraycopy(REF_BASES, 0, bases, 0, 2);           // pos 1-2 match
        bases[2] = SequenceUtil.complement(REF_BASES[2]);       // pos 3 mismatch
        System.arraycopy(REF_BASES, 3, bases, 3, 2);           // pos 4-5 match
        // 1D skips ref pos 6
        System.arraycopy(REF_BASES, 6, bases, 5, 3);           // pos 7-9 match
        final SAMRecord rec = createRecord(header, "mixedMd", 1, "5M1D3M", bases, quals(8));
        assertRoundTrip("mixedMatchesMismatchesDeletion", rec);
    }

    // ---- Boundary conditions ----

    @Test
    public void testSingleBaseRead() throws IOException {
        final SAMFileHeader header = buildHeader();
        final SAMRecord rec = createRecord(header, "single", 1, "1M", refBases(1, 1), quals(1));
        assertRoundTrip("singleBaseRead", rec);
    }

    @Test
    public void testUnmappedRead() throws IOException {
        final SAMFileHeader header = buildHeader();
        final SAMRecord rec = new SAMRecord(header);
        rec.setReadName("unmapped");
        rec.setReadUnmappedFlag(true);
        rec.setReferenceIndex(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
        rec.setAlignmentStart(SAMRecord.NO_ALIGNMENT_START);
        rec.setReadBases("ACGTACGT".getBytes());
        rec.setBaseQualities(quals(8));
        rec.setAttribute("RG", READ_GROUP);
        assertRoundTrip("unmappedRead", rec);
    }

    @Test
    public void testReadNearEndOfReference() throws IOException {
        final SAMFileHeader header = buildHeader();
        // Align a 10bp read at the very end of the 200bp reference
        final int start = REF_LENGTH - 9;  // positions 191-200
        final SAMRecord rec = createRecord(header, "refEnd", start, "10M", refBases(start, 10), quals(10));
        assertRoundTrip("readNearEndOfReference", rec);
    }

    @Test
    public void testReadExtendsPastReferenceWithExplicitMdNm() throws IOException {
        final SAMFileHeader header = buildHeader();
        // 6M read starting at position 198, so positions 198-200 overlap the 200bp reference
        // and positions 201-203 extend past the end.  We set MD/NM to treat the 3 past-reference
        // bases as mismatches to N.  Since these values don't match what SequenceUtil.calculateMdAndNm
        // would produce (it stops at the reference boundary), they'll be kept verbatim through
        // the CRAM strip-and-regenerate logic.
        final int start = REF_LENGTH - 2;  // position 198
        final int readLen = 6;
        final int refOverlap = REF_LENGTH - start + 1;  // 3 bases overlap the reference
        final int pastRef = readLen - refOverlap;         // 3 bases past the end

        final byte[] bases = new byte[readLen];
        System.arraycopy(REF_BASES, start - 1, bases, 0, refOverlap);
        for (int i = refOverlap; i < readLen; i++) {
            bases[i] = 'A';  // arbitrary non-N bases past end
        }

        final SAMRecord rec = new SAMRecord(header);
        rec.setReadName("pastRef");
        rec.setAlignmentStart(start);
        rec.setReferenceIndex(0);
        rec.setCigarString(readLen + "M");
        rec.setReadUnmappedFlag(false);
        rec.setReadBases(bases);
        rec.setBaseQualities(quals(readLen));
        rec.setAttribute("RG", READ_GROUP);

        // Set MD/NM that treat past-reference bases as mismatches to N:
        // 3 matching ref bases, then 3 Ns (one per past-reference base)
        rec.setAttribute("MD", refOverlap + "N" + "N" + "N");
        rec.setAttribute("NM", pastRef);

        assertRoundTrip("readExtendsPastReference", rec);
    }

    // ---- Existing test (preserved) ----

    @Test
    public void testMdNmRegeneration() throws IOException {
        final SAMFileHeader header = buildHeader();
        final SAMRecord record = new SAMRecord(header);
        record.setReadName("mdNmRegen");
        record.setAlignmentStart(1);
        record.setReferenceIndex(0);
        record.setCigarString("3M");
        record.setReadUnmappedFlag(false);
        // Use first 3 ref bases but mismatch the last one
        final byte[] bases = refBases(1, 3);
        bases[2] = SequenceUtil.complement(bases[2]);
        record.setReadBases(bases);
        record.setBaseQualities("!!!".getBytes());
        record.setAttribute("RG", READ_GROUP);

        // Set bogus MD/NM — they don't match computed values so they will be kept verbatim
        record.setAttribute("MD", "nonsense");
        record.setAttribute("NM", 123);
        assertRoundTrip("mdNmRegeneration", record);
    }

    // ---- Multiple records in one container ----

    @Test
    public void testMultipleDiverseRecords() throws IOException {
        final SAMFileHeader header = buildHeader();
        final SAMRecord[] records = {
            createRecord(header, "multi1", 1,  "10M",    refBases(1, 10),  quals(10)),
            createRecord(header, "multi2", 20, "5M2I5M", buildInsertionBases(20, 5, "GG", 5), quals(12)),
            createRecord(header, "multi3", 50, "3S7M",   buildSoftClipStartBases(50, 3, 7), quals(10)),
        };
        assertRoundTrip("multipleDiverseRecords", records);
    }

    /** Helper: builds read bases for an insertion in the middle of ref-matching flanks. */
    private byte[] buildInsertionBases(final int start1Based, final int leftMatch, final String inserted, final int rightMatch) {
        final byte[] ins = inserted.getBytes();
        final byte[] bases = new byte[leftMatch + ins.length + rightMatch];
        System.arraycopy(REF_BASES, start1Based - 1, bases, 0, leftMatch);
        System.arraycopy(ins, 0, bases, leftMatch, ins.length);
        System.arraycopy(REF_BASES, start1Based - 1 + leftMatch, bases, leftMatch + ins.length, rightMatch);
        return bases;
    }

    /** Helper: builds read bases with soft-clipped start followed by ref-matching bases. */
    private byte[] buildSoftClipStartBases(final int start1Based, final int clipLen, final int matchLen) {
        final byte[] bases = new byte[clipLen + matchLen];
        java.util.Arrays.fill(bases, 0, clipLen, (byte) 'T');
        System.arraycopy(REF_BASES, start1Based - 1, bases, clipLen, matchLen);
        return bases;
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
        final SAMFileHeader header = buildHeader();

        // 1. Real qualities, perfect match (no read features carry quality)
        final SAMRecord withQualsMatch = createRecord(header, "qualsMatch", 1, "10M", refBases(1, 10), quals(10));

        // 2. No qualities, perfect match (no read features at all — featureless case)
        final SAMRecord noQualsMatch = createRecordNoQuals(header, "noQualsMatch", 11, "10M", refBases(11, 10));

        // 3. No qualities, mismatches (ReadBase features with embedded quality — synthetic 0xFF path)
        final SAMRecord noQualsMismatch = createRecordNoQuals(header, "noQualsMis", 21, "5M", mismatchBases(21, 5));

        // 4. Real qualities, mismatches (ReadBase features with real quality — contrast with #3)
        final SAMRecord withQualsMismatch = createRecord(header, "qualsMis", 26, "5M", mismatchBases(26, 5), quals(5));

        // 5. No qualities, insertion (Insertion feature, no quality-carrying features)
        final SAMRecord noQualsInsertion = createRecordNoQuals(header, "noQualsIns", 31, "5M2I5M",
                buildInsertionBases(31, 5, "GG", 5));

        // 6. No qualities, soft clip + matches
        final SAMRecord noQualsSoftClip = createRecordNoQuals(header, "noQualsSC", 41, "3S7M",
                buildSoftClipStartBases(41, 3, 7));

        assertRoundTrip("mixedQualityPresence",
                withQualsMatch, noQualsMatch, noQualsMismatch, withQualsMismatch, noQualsInsertion, noQualsSoftClip);
    }
}
