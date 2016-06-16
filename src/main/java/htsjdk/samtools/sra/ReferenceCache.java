package htsjdk.samtools.sra;

import htsjdk.samtools.SAMFileHeader;
import ngs.ErrorMsg;
import ngs.ReadCollection;
import ngs.Reference;


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
    private Reference cachedReference;

    public ReferenceCache(ReadCollection run, SAMFileHeader virtualHeader) {
        this.run = run;
        this.virtualHeader = virtualHeader;
    }

    /**
     * This method returns Reference objects by reference indexes in SAM header
     * Those objects do not maintain thread safety
     *
     * @param referenceIndex reference index in
     * @return a Reference object
     */
    public Reference get(int referenceIndex) {
        String contig = virtualHeader.getSequence(referenceIndex).getSequenceName();

        try {
            if (cachedReference == null || !cachedReference.getCanonicalName().equals(contig)) {
                cachedReference = run.getReference(contig);
            }
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }

        return cachedReference;
    }
}
