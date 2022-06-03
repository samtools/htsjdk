package htsjdk.samtools.cram.ref;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.build.CRAMReferenceRegion;
import htsjdk.samtools.cram.structure.CRAMStructureTestHelper;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;

public class CRAMReferenceRegionTest extends HtsjdkTest {

    @DataProvider(name="getReferenceBasesByIndexPositive")
    private Object[][] getReferenceBasesByIndexTests() {
        return new Object[][] {
                // index - only 0 or 1 should be accepted
                {  new CRAMReferenceRegion(CRAMStructureTestHelper.REFERENCE_SOURCE, CRAMStructureTestHelper.SAM_FILE_HEADER.getSequenceDictionary()),
                   CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO,
                   CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO_BYTE },
                {  new CRAMReferenceRegion(CRAMStructureTestHelper.REFERENCE_SOURCE, CRAMStructureTestHelper.SAM_FILE_HEADER.getSequenceDictionary()),
                   CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE,
                   CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE_BYTE
                },
        };
    }

    @Test(dataProvider = "getReferenceBasesByIndexPositive")
    public void testGetReferenceBasesByIndexPositive(
            final CRAMReferenceRegion cramReferenceRegion,
            final int index,
            final byte expectedReferenceByte) {
        final byte[] expectedBases = new byte[CRAMStructureTestHelper.SAM_FILE_HEADER.getSequence(index).getSequenceLength()];
        Arrays.fill(expectedBases, expectedReferenceByte);
        cramReferenceRegion.fetchReferenceBases(index);
        final byte[] bases = cramReferenceRegion.getCurrentReferenceBases();

        Assert.assertEquals(bases.length, CRAMStructureTestHelper.SAM_FILE_HEADER.getSequence(index).getSequenceLength());
        Assert.assertEquals(bases, expectedBases);
    }

    @DataProvider(name="getReferenceBasesByIndexNegative")
    private Object[][] getReferenceBasesByIndexNegative() {
        return new Object[][] {
                // index - anything but 0 or 1 should be rejected
                {  new CRAMReferenceRegion(
                        CRAMStructureTestHelper.REFERENCE_SOURCE,
                        CRAMStructureTestHelper.SAM_FILE_HEADER.getSequenceDictionary()), -1},
                {  new CRAMReferenceRegion(
                        CRAMStructureTestHelper.REFERENCE_SOURCE,
                        CRAMStructureTestHelper.SAM_FILE_HEADER.getSequenceDictionary()), 2},
        };
    }

    @Test(dataProvider = "getReferenceBasesByIndexNegative", expectedExceptions = IllegalArgumentException.class)
    public void testGetReferenceBasesByIndexNegative(final CRAMReferenceRegion cramReferenceRegion, final int index) {
        cramReferenceRegion.fetchReferenceBases(index);
    }

    @DataProvider(name="getReferenceBasesByRegionPositive")
    private Object[][] getReferenceBasesByRegionPositive() {
        // these tests use a backing reference source of alternating bases (forward and reversed repeat cycles of ACGT)
        // to test alignment of results

        return new Object[][] {
                // region, requested contig index, requested offset, requested length, expect reversed sequence
                {
                        // entirety of contig 0
                        getAlternatingReferenceRegion(),
                        CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO,
                        0,
                        CRAMStructureTestHelper.REFERENCE_CONTIG_LENGTH,
                        false
                },
                {
                        // entirety of contig 1
                        getAlternatingReferenceRegion(),
                        CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE,
                        0,
                        CRAMStructureTestHelper.REFERENCE_CONTIG_LENGTH,
                        true
                },
                {
                        // first byte of contig 0
                        getAlternatingReferenceRegion(),
                        CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO,
                        0,
                        1,
                        false
                },
                {
                        // first byte of contig 1
                        getAlternatingReferenceRegion(),
                        CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE,
                        0,
                        1,
                        true
                },
                {
                        // 1 byte of contig 0 at offset 1
                        getAlternatingReferenceRegion(),
                        CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO,
                        1,
                        1,
                        false
                },
                {
                        // 1 byte of contig 1 at offset 1
                        getAlternatingReferenceRegion(),
                        CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE,
                        1,
                        1,
                        true
                },
                {
                        // last byte of contig 0
                        getAlternatingReferenceRegion(),
                        CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO,
                        CRAMStructureTestHelper.REFERENCE_CONTIG_LENGTH - 1,
                        1,
                        false
                },
                {
                        // last byte of contig 1
                        getAlternatingReferenceRegion(),
                        CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE,
                        CRAMStructureTestHelper.REFERENCE_CONTIG_LENGTH - 1,
                        1,
                        true
                },
                {
                        // middle bytes of contig 0
                        getAlternatingReferenceRegion(),
                        CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO,
                        CRAMStructureTestHelper.REFERENCE_CONTIG_LENGTH / 2,
                        CRAMStructureTestHelper.REFERENCE_CONTIG_LENGTH / 2,
                        false
                },
                {
                        // middle bytes of contig 1
                        getAlternatingReferenceRegion(),
                        CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE,
                        CRAMStructureTestHelper.REFERENCE_CONTIG_LENGTH / 2,
                        CRAMStructureTestHelper.REFERENCE_CONTIG_LENGTH / 2,
                        true
                },
        };
    }

    @Test(dataProvider = "getReferenceBasesByRegionPositive")
    public void testGetReferenceBasesByRegionPositive(
            final CRAMReferenceRegion cramReferenceRegion,
            final int index,
            final int requestedOffset,
            final int requestedLength,
            final boolean reversed) {
        cramReferenceRegion.fetchReferenceBasesByRegion(index, requestedOffset, requestedLength);
        final byte[] bases = cramReferenceRegion.getCurrentReferenceBases();
        Assert.assertEquals(cramReferenceRegion.getRegionStart(), requestedOffset);
        Assert.assertEquals(cramReferenceRegion.getRegionLength(), requestedLength);
        Assert.assertEquals(bases.length, requestedLength);
        Assert.assertEquals(cramReferenceRegion.getCurrentReferenceBases(), bases);

        final byte[] fullContigBases = getRepeatingBaseSequence(CRAMStructureTestHelper.REFERENCE_CONTIG_LENGTH, reversed);
        Assert.assertEquals(bases, Arrays.copyOfRange(fullContigBases, requestedOffset, requestedOffset + requestedLength));
    }

    @Test
    public void testGetReferenceBasesByRegionExceedsContigLength() {
        int regionStart = CRAMStructureTestHelper.REFERENCE_CONTIG_LENGTH / 2;
        int requestedFragmentLength = CRAMStructureTestHelper.REFERENCE_CONTIG_LENGTH;

        final CRAMReferenceRegion cramReferenceRegion = getAlternatingReferenceRegion();
        cramReferenceRegion.fetchReferenceBasesByRegion(
                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO,
                regionStart,
                requestedFragmentLength);
        Assert.assertEquals(cramReferenceRegion.getRegionStart(), regionStart);
        Assert.assertEquals(cramReferenceRegion.getRegionLength(), requestedFragmentLength / 2);
        final byte[] bases = cramReferenceRegion.getCurrentReferenceBases();
        Assert.assertEquals(bases.length, requestedFragmentLength /2);
        final byte[] fullContigBases = getRepeatingBaseSequence(
                CRAMStructureTestHelper.REFERENCE_CONTIG_LENGTH,
                false);
        Assert.assertEquals(bases,
                Arrays.copyOfRange(fullContigBases, regionStart, CRAMStructureTestHelper.REFERENCE_CONTIG_LENGTH));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRequestedOffstBeyondEndOfContig() {
        final CRAMReferenceRegion cramReferenceRegion = getAlternatingReferenceRegion();
        cramReferenceRegion.fetchReferenceBasesByRegion(
                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO,
                CRAMStructureTestHelper.REFERENCE_CONTIG_LENGTH,
                1);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    private void testRejectNegativeOffset() {
        final CRAMReferenceRegion referenceRegion =
                new CRAMReferenceRegion(CRAMStructureTestHelper.REFERENCE_SOURCE, CRAMStructureTestHelper.SAM_FILE_HEADER.getSequenceDictionary());
        referenceRegion.fetchReferenceBasesByRegion(
                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO, -1, 10);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    private void testRejectNegativeContigIndex() {
        final CRAMReferenceRegion referenceRegion =
                new CRAMReferenceRegion(CRAMStructureTestHelper.REFERENCE_SOURCE, CRAMStructureTestHelper.SAM_FILE_HEADER.getSequenceDictionary());
        referenceRegion.fetchReferenceBasesByRegion(
                -1, 0, 10);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    private void testRejectNonExistentContigIndex() {
        final CRAMReferenceRegion referenceRegion =
                new CRAMReferenceRegion(CRAMStructureTestHelper.REFERENCE_SOURCE, CRAMStructureTestHelper.SAM_FILE_HEADER.getSequenceDictionary());
        referenceRegion.fetchReferenceBasesByRegion(
                27, 0, 10);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    private void testUncooperativeReferenceSource() {
        final CRAMReferenceRegion referenceRegion =
                new CRAMReferenceRegion(getUncooperativeReferenceSource(), CRAMStructureTestHelper.SAM_FILE_HEADER.getSequenceDictionary());
        referenceRegion.fetchReferenceBasesByRegion(
                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO,
                0,
                10);
    }

    private static CRAMReferenceSource getUncooperativeReferenceSource() {
        return new CRAMReferenceSource() {
                    @Override
                    public byte[] getReferenceBases(SAMSequenceRecord sequenceRecord, boolean tryNameVariants) {
                        return null;
                    }

                    @Override
                    public byte[] getReferenceBasesByRegion(SAMSequenceRecord sequenceRecord,
                                                            int zeroBasedStart, int requestedRegionLength) {
                        return null;
                    }
                };
    }

    private static CRAMReferenceRegion getAlternatingReferenceRegion() {
        return new CRAMReferenceRegion(
                new ReferenceSource(getReferenceFileWithAlternatingBases(CRAMStructureTestHelper.REFERENCE_CONTIG_LENGTH)),
                CRAMStructureTestHelper.SAM_FILE_HEADER.getSequenceDictionary());
    }

    private static InMemoryReferenceSequenceFile getReferenceFileWithAlternatingBases(final int length) {
        final InMemoryReferenceSequenceFile referenceFile = new InMemoryReferenceSequenceFile();

        // one contig with repeated ACGT...
        final byte[] seq0Bases = getRepeatingBaseSequence(CRAMStructureTestHelper.REFERENCE_CONTIG_LENGTH, false);
        referenceFile.add("0", seq0Bases);

        // one contig with repeated TGCA...
        final byte[] seq1Bases = getRepeatingBaseSequence(CRAMStructureTestHelper.REFERENCE_CONTIG_LENGTH, true);
        referenceFile.add("1", seq1Bases);

        return referenceFile;
    }

    // fill an array with the repeated base sequence "ACGTACGTACGT...", or reversed
    private static byte[] getRepeatingBaseSequence(final int length, final boolean reversed) {
        byte[] bases = new byte[length];
        for (int i = 0; (i + 4) < bases.length; i += 4) {
            bases[i] = (byte) (reversed ? 'T' : 'A');
            bases[i+1] = (byte) (reversed ? 'G' : 'C');
            bases[i+2] = (byte) (reversed ? 'C' : 'G');
            bases[i+3] = (byte) (reversed ? 'A' : 'T');
        }
        return bases;
    }

}
