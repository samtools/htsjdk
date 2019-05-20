package htsjdk.samtools;

import htsjdk.samtools.cram.structure.Container;

/**
 * Interface for indexing CRAM.
 */
public interface CRAMIndexer {
    /**
     * Create index entries for a single container.
     * @param container the container to index
     * @param validationStringency stringency for validating records, passed to {@link Container#getSpans(ValidationStringency)}
     */
    void processContainer(final Container container, final ValidationStringency validationStringency);

    /**
     * Finish creating the index by writing the accumulated entries out.
     */
    void finish();
}
