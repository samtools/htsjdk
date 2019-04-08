package htsjdk.samtools;

import htsjdk.samtools.cram.structure.Container;

/**
 * Base class for indexing CRAM.
 */
public abstract class AbstractCRAMIndexer {
    /**
     * Create index entries for a single container.
     * @param container the container to index
     * @param validationStringency stringency for validating records, passed to {@link Container#getSpans(ValidationStringency)}
     */
    abstract void processContainer(final Container container, final ValidationStringency validationStringency);

    /**
     * Finish creating the index by writing the accumulated entries out.
     */
    public abstract void finish();
}
