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
 * in the sequence dictionary -- most often because the reference fasta has not been indexed.
 * See https://github.com/samtools/htsjdk/issues/1732.
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

    @Test
    public void missing_bases_error_names_the_offending_sequence() {
        Assert.assertTrue(
                messageFromFailedFetch().contains(SEQUENCE_NAME),
                "the error should name the sequence that could not be resolved");
    }

    @Test
    public void missing_bases_error_mentions_the_fasta_index() {
        final String message = messageFromFailedFetch();
        Assert.assertTrue(
                message.contains(".fai"),
                "the error should point at the missing fasta index as the likely cause, but was: " + message);
    }

    @Test
    public void missing_bases_error_mentions_the_sequence_dictionary() {
        final String message = messageFromFailedFetch();
        Assert.assertTrue(
                message.contains(".dict"),
                "the error should mention the required sequence dictionary, but was: " + message);
    }
}
