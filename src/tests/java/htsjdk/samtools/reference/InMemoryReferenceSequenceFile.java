package htsjdk.samtools.reference;

import htsjdk.samtools.SAMSequenceDictionary;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryReferenceSequenceFile implements
        ReferenceSequenceFile {
    Map<String, ReferenceSequence> map = new HashMap<String, ReferenceSequence>();
    List<String> index;
    int current = 0;

    public void add(final String name, final byte[] bases) {
        final ReferenceSequence sequence = new ReferenceSequence(name,
                map.size(), bases);
        map.put(sequence.getName(), sequence);
    }

    @Override
    public void reset() {
        current = 0;
    }

    @Override
    public ReferenceSequence nextSequence() {
        if (current >= index.size()) return null;
        return map.get(index.get(current++));
    }

    @Override
    public boolean isIndexed() {
        return true;
    }

    @Override
    public ReferenceSequence getSubsequenceAt(final String contig, final long start,
                                              final long stop) {
        final ReferenceSequence sequence = getSequence(contig);
        if (sequence == null) return null;
        final byte[] bases = new byte[(int) (stop - start) + 1];
        System.arraycopy(sequence.getBases(), (int) start - 1, bases, 0,
                bases.length);
        return new ReferenceSequence(contig, sequence.getContigIndex(),
                bases);
    }

    @Override
    public SAMSequenceDictionary getSequenceDictionary() {
        return null;
    }

    @Override
    public ReferenceSequence getSequence(final String contig) {
        return map.get(contig);
    }

    @Override
    public void close() throws IOException {
        map.clear();
        index.clear();
        current = 0;
    }
}