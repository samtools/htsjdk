package htsjdk.samtools.sra;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import ngs.ErrorMsg;
import ngs.ReadCollection;
import ngs.Reference;

import java.util.ArrayList;
import java.util.List;

/**
 * That is a thread-safe wrapper for a list of cache Reference objects.
 * Those objects can be used from different threads without issues, however to load and save a Reference object, we
 * need to acquire a lock.
 *
 * Created by andrii.nikitiuk on 10/28/15.
 */
public class ReferenceCache {
    private ReadCollection run;
    private SAMFileHeader virtualHeader;
    private final List<Reference> cachedReferences;

    public ReferenceCache(ReadCollection run, SAMFileHeader virtualHeader) {
        this.run = run;
        this.virtualHeader = virtualHeader;
        cachedReferences = initializeReferenceCache();
    }

    /**
     * This method returns Reference objects by reference indexes in SAM header
     * Those obejcts can be used from different threads
     *
     * This method maintains thread safety, so that if Reference object is set already, it can be easily returned
     * without locks. However, if Reference object is null, we need to acquire a lock, load the object and save it in
     * array.
     *
     * @param referenceIndex reference index in
     * @return a Reference object
     */
    public Reference get(int referenceIndex) {
        Reference reference = cachedReferences.get(referenceIndex);

        if (reference != null) {
            return reference;
        }

        // maintain thread safety
        synchronized (this) {
            reference = cachedReferences.get(referenceIndex);
            if (reference == null) {
                try {
                    reference = run.getReference(virtualHeader.getSequence(referenceIndex).getSequenceName());
                } catch (ErrorMsg e) {
                    throw new RuntimeException(e);
                }
                cachedReferences.set(referenceIndex, reference);
            }
        }


        return reference;
    }

    private List<Reference> initializeReferenceCache() {
        if (virtualHeader == null) {
            throw new RuntimeException("Cannot cache references - header is uninitialized");
        }

        SAMSequenceDictionary sequenceDictionary = virtualHeader.getSequenceDictionary();
        List<Reference> references = new ArrayList<Reference>(sequenceDictionary.size());
        for (SAMSequenceRecord sequence : sequenceDictionary.getSequences()) {
            references.add(null);
        }

        return references;
    }
}
