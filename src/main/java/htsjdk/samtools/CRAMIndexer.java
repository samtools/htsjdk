package htsjdk.samtools;

import htsjdk.samtools.cram.structure.CompressorCache;
import htsjdk.samtools.cram.structure.Container;

/**
 * Interface for indexing CRAM.
 */
public interface CRAMIndexer {
    /**
     * Create index entries for a single container.
     * @param container the container to index
     * @param validationStringency stringency for validating records (used when processing multi-reference slices,
     *                             since creating an index on a multi-ref slices requires actually decoding the
     *                             records in order to resove the constituent reference spans}
     */
    void processContainer(final Container container, final ValidationStringency validationStringency);

    /**
     * Finish creating the index by writing the accumulated entries out.
     */
    void finish();
}
