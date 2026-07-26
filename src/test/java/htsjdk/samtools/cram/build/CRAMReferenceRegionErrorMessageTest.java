package htsjdk.samtools.cram.build;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import java.util.Collections;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests the diagnostics produced when a reference source cannot supply bases for a sequence that is present
 * in the sequence dictionary -- most often because the reference fasta has not been indexed. Covers both the
 * by-region and whole-contig lookups, which fail for the same reason and should say the same thing.
 * See https://github.com/samtools/htsjdk/issues/1732 and https://github.com/samtools/htsjdk/issues/998.
 */
public class CRAMReferenceRegionErrorMessageTest extends HtsjdkTest {

    private static final String SEQUENCE_NAME = "chr1";

    /** A reference source that knows about a sequence but can never supply bases for it. */
    private static final class NoBasesReferenceSource implements CRAMReferenceSource {
        @Override
        public byte[] getReferenceBases(final SAMSequenceRecord sequenceRecord, final boolean tryNameVariants) {
            return null;
        }

        @Override
        public byte[] getReferenceBasesByRegion(
                final SAMSequenceRecord sequenceRecord, final int zeroBasedStart, final int requestedRegionLength) {
            return null;
        }
    }

    private static CRAMReferenceRegion makeRegion() {
        final SAMSequenceDictionary dictionary =
                new SAMSequenceDictionary(Collections.singletonList(new SAMSequenceRecord(SEQUENCE_NAME, 1000)));
        return new CRAMReferenceRegion(new NoBasesReferenceSource(), dictionary);
    }

    private static String messageFromFailedFetch() {
        try {
            makeRegion().fetchReferenceBasesByRegion(0, 0, 100);
            Assert.fail("expected an exception when the reference source returns no bases");
            return null; // unreachable
        } catch (final IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    /** The whole-contig lookup, as opposed to the by-region one above. */
    private static String messageFromFailedWholeContigFetch() {
        try {
            makeRegion().fetchReferenceBases(0);
            Assert.fail("expected an exception when the reference source returns no bases");
            return null; // unreachable
        } catch (final IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    @Test
    public void testMissingBasesErrorNamesOffendingSequence() {
        Assert.assertTrue(
                messageFromFailedFetch().contains(SEQUENCE_NAME),
                "the error should name the sequence that could not be resolved");
    }

    @Test
    public void testMissingBasesErrorMentionsFastaIndex() {
        final String message = messageFromFailedFetch();
        Assert.assertTrue(
                message.contains(".fai"),
                "the error should point at the missing fasta index as the likely cause, but was: " + message);
    }

    @Test
    public void testMissingBasesErrorMentionsSequenceDictionary() {
        final String message = messageFromFailedFetch();
        Assert.assertTrue(
                message.contains(".dict"),
                "the error should mention the required sequence dictionary, but was: " + message);
    }

    /**
     * The whole-contig lookup used to report "A reference must be supplied (reference sequence ... not found)",
     * which is misleading when a reference was supplied but simply was not indexed, and which dumped the whole
     * SAMSequenceRecord into the message. See https://github.com/samtools/htsjdk/issues/998.
     */
    @Test
    public void testWholeContigFetchExplainsLikelyCause() {
        final String message = messageFromFailedWholeContigFetch();
        Assert.assertTrue(message.contains(SEQUENCE_NAME), "the error should name the sequence, but was: " + message);
        Assert.assertTrue(
                message.contains(".fai"),
                "the error should point at the missing fasta index as the likely cause, but was: " + message);
        Assert.assertTrue(
                message.contains(".dict"),
                "the error should mention the required sequence dictionary, but was: " + message);
    }

    /** Both lookup paths fail for the same reason, so they should say the same thing. */
    @Test
    public void testBothLookupPathsReportSameDiagnostic() {
        Assert.assertEquals(
                messageFromFailedWholeContigFetch(),
                messageFromFailedFetch(),
                "the whole-contig and by-region lookups should report an identical diagnostic");
    }

    /** The message should not dump the raw SAMSequenceRecord toString() at the user. */
    @Test
    public void testMessageDoesNotDumpRawSequenceRecord() {
        final String message = messageFromFailedWholeContigFetch();
        Assert.assertFalse(
                message.contains("SAMSequenceRecord("),
                "the error should name the sequence, not dump the record, but was: " + message);
    }
}
