package htsjdk.samtools.reference;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A fake reference sequence file that returns all-N bases for any contig. Intended for testing only.
 *
 * <p><b>Memory optimization:</b> Uses a single shared byte[] buffer that grows to accommodate the
 * largest requested sequence. This avoids allocating hundreds of megabytes per chromosome for
 * dictionaries with large reference sequences (e.g., human genome), which previously caused
 * OOM errors when multiple test classes ran in parallel.</p>
 *
 * <p><b>WARNING:</b> Because a shared buffer is used, the byte[] inside the returned
 * {@link ReferenceSequence} may be <em>larger</em> than the sequence's reported length.
 * The {@link ReferenceSequence#length()} will be correct, but callers must not assume
 * {@code referenceSequence.getBases().length == referenceSequence.length()}. The extra
 * trailing bytes are all 'N' and should not be accessed.</p>
 */
public class FakeReferenceSequenceFile implements ReferenceSequenceFile {
    private final Map<String, SAMSequenceRecord> map = new HashMap<>();
    private final List<String> index = new ArrayList<>();
    private int current = 0;

    // Single shared buffer that grows to accommodate the largest requested sequence
    private static byte[] sharedBases = new byte[0];

    public FakeReferenceSequenceFile(List<SAMSequenceRecord> sequences) {
        for (SAMSequenceRecord s : sequences) {
            map.put(s.getSequenceName(), s);
            index.add(s.getSequenceName());
        }
    }

    private static synchronized byte[] getBasesOfLength(int length) {
        if (sharedBases.length < length) {
            sharedBases = new byte[length];
            Arrays.fill(sharedBases, (byte) 'N');
        }
        return sharedBases;
    }

    /**
     * Returns a {@link ReferenceSequence} for the given contig, filled with 'N' bases.
     *
     * <p><b>WARNING:</b> The returned ReferenceSequence's {@code getBases()} array may be larger
     * than the contig's sequence length due to shared buffer reuse. Use {@code length()} for
     * the true sequence length, not {@code getBases().length}.</p>
     */
    private static ReferenceSequence buildReferenceSequence(SAMSequenceRecord samSequenceRecord) {
        final byte[] bases = getBasesOfLength(samSequenceRecord.getSequenceLength());
        return new ReferenceSequence(samSequenceRecord.getSequenceName(), samSequenceRecord.getSequenceIndex(), bases);
    }

    @Override
    public void reset() {
        current = 0;
    }

    @Override
    public ReferenceSequence nextSequence() {
        if (current >= index.size()) return null;
        return buildReferenceSequence(map.get(index.get(current++)));
    }

    @Override
    public boolean isIndexed() {
        return true;
    }

    /**
     * Returns a {@link ReferenceSequence} for the requested sub-region, filled with 'N' bases.
     * Unlike {@link #getSequence}, this returns a copy of the exact requested length.
     */
    @Override
    public ReferenceSequence getSubsequenceAt(final String contig, final long start, final long stop) {
        int length = (int) (stop - start + 1);
        final byte[] bases = getBasesOfLength(length);
        return new ReferenceSequence(contig, length, Arrays.copyOf(bases, length));
    }

    @Override
    public SAMSequenceDictionary getSequenceDictionary() {
        return null;
    }

    /**
     * Returns a {@link ReferenceSequence} for the given contig, filled with 'N' bases.
     *
     * <p><b>WARNING:</b> The returned ReferenceSequence's {@code getBases()} array may be larger
     * than the contig's sequence length due to shared buffer reuse. Use {@code length()} for
     * the true sequence length, not {@code getBases().length}.</p>
     */
    @Override
    public ReferenceSequence getSequence(final String contig) {
        return buildReferenceSequence(map.get(contig));
    }

    @Override
    public void close() throws IOException {
        map.clear();
        index.clear();
        current = 0;
    }
}
