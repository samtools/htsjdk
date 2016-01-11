package htsjdk.samtools.sra;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import ngs.ErrorMsg;
import ngs.ReadCollection;
import ngs.Reference;
import ngs.ReferenceIterator;

import java.io.IOException;
import java.util.Iterator;

/**
 * Allows reading Reference data from SRA
 */
public class SRAIndexedSequenceFile implements ReferenceSequenceFile {
    private SRAAccession acc;
    private ReadCollection run;
    private Reference cachedReference;

    private Iterator<SAMSequenceRecord> sequenceRecordIterator;

    protected SAMSequenceDictionary sequenceDictionary;

    /**
     * @param acc accession
     */
    public SRAIndexedSequenceFile(SRAAccession acc) {
        this.acc = acc;

        if (!acc.isValid()) {
            throw new RuntimeException("Passed an invalid SRA accession into SRA reader: " + acc);
        }

        try {
            run = gov.nih.nlm.ncbi.ngs.NGS.openReadCollection(acc.toString());
            sequenceDictionary = loadSequenceDictionary();
        } catch (final ErrorMsg e) {
            throw new RuntimeException(e);
        }

        reset();
    }

    @Override
    public SAMSequenceDictionary getSequenceDictionary() {
        return sequenceDictionary;
    }

    @Override
    public ReferenceSequence nextSequence() {
        SAMSequenceRecord sequence = sequenceRecordIterator.next();
        return getSubsequenceAt(sequence.getSequenceName(), 1L, sequence.getSequenceLength());
    }

    @Override
    public void reset() {
        sequenceRecordIterator = sequenceDictionary.getSequences().iterator();
    }

    @Override
    public boolean isIndexed() {
        return true;
    }

    @Override
    public ReferenceSequence getSequence(String contig) {
        return getSubsequenceAt(contig, 1L, sequenceDictionary.getSequence(contig).getSequenceLength());
    }

    @Override
    public ReferenceSequence getSubsequenceAt(String contig, long start, long stop) {
        SAMSequenceRecord sequence = sequenceDictionary.getSequence(contig);
        int referenceIndex = sequence.getSequenceIndex();

        byte[] bases;

        try {
            Reference reference;
            synchronized (this) {
                if (cachedReference == null || !cachedReference.getCanonicalName().equals(contig)) {
                    cachedReference = run.getReference(contig);
                }
                reference = cachedReference;

                bases = reference.getReferenceBases(start - 1, stop - (start - 1)).getBytes();
            }
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }

        return new ReferenceSequence(contig, referenceIndex, bases);
    }

    @Override
    public void close() throws IOException {
        cachedReference = null;
    }

    protected SAMSequenceDictionary loadSequenceDictionary() throws ErrorMsg {
        SAMSequenceDictionary dict = new SAMSequenceDictionary();

        ReferenceIterator itRef = run.getReferences();
        while (itRef.nextReference()) {
            dict.addSequence(new SAMSequenceRecord(itRef.getCanonicalName(), (int) itRef.getLength()));
        }

        return dict;
    }
}