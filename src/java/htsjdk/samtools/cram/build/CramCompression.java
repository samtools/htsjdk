package htsjdk.samtools.cram.build;

import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.SubstitutionMatrix;

import java.util.List;

/**
 * CRAM compression decisions: given the data come up with a way to compress it.
 */
public interface CramCompression {
    /**
     * Analyze the records and build a {@link CompressionHeader} object
     * describing how to compress the records.
     *
     * @param records            the data to be compressed
     * @param substitutionMatrix a matrix of base substitution frequencies
     * @param sorted             if true the records are assumed to be sorted by alignment
     *                           position
     * @return an object describing how the data should be compressed
     */
    CompressionHeader buildCompressionHeader(final List<CramCompressionRecord> records,
                                             final SubstitutionMatrix substitutionMatrix, final boolean sorted);
}
