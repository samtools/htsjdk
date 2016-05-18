package htsjdk.samtools.reference;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.ref.ReferenceSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by vadim on 17/03/2015.
 */
public class FakeReferenceSequenceFile implements
        ReferenceSequenceFile {
    Map<String, SAMSequenceRecord> map = new HashMap<String, SAMSequenceRecord>();
    List<String> index= new ArrayList<String>() ;
    int current = 0;

    public FakeReferenceSequenceFile(List<SAMSequenceRecord> sequences) {
        for (SAMSequenceRecord s:sequences) {
            map.put(s.getSequenceName(), s) ;
            index.add(s.getSequenceName()) ;
        }
    }

    private static ReferenceSequence buildReferenceSequence (SAMSequenceRecord samSequenceRecord) {
        byte[] bases = new byte[samSequenceRecord.getSequenceLength()] ;
        Arrays.fill(bases, (byte)'N');
        return new ReferenceSequence(samSequenceRecord.getSequenceName(), samSequenceRecord.getSequenceIndex(), bases) ;
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

    @Override
    public ReferenceSequence getSubsequenceAt(final String contig, final long start,
                                              final long stop) {
        byte[] bases = new byte[(int) (stop-start+1)] ;
        Arrays.fill(bases, (byte)'N');
        return new ReferenceSequence(contig, bases.length, bases) ;
    }

    @Override
    public SAMSequenceDictionary getSequenceDictionary() {
        return null;
    }

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
