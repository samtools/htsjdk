package htsjdk.samtools;

/**
 * Default factory for creating SAM and BAM records used by the SAMFileReader classes.
 *
 * @author Tim Fennell
 */
public class DefaultSAMRecordFactory implements SAMRecordFactory {

    private static final DefaultSAMRecordFactory INSTANCE = new DefaultSAMRecordFactory();
    
    public static DefaultSAMRecordFactory getInstance() {
        return INSTANCE;   
    }

    /** Create a new SAMRecord to be filled in */
    public SAMRecord createSAMRecord(final SAMFileHeader header) {
        return new SAMRecord(header);
    }

    /**
     * Create a new BAM Record. If the reference sequence index or mate reference sequence index are
     * any value other than NO_ALIGNMENT_REFERENCE_INDEX, the values must be resolvable against the sequence
     * dictionary in the header argument.
     */
    public BAMRecord createBAMRecord (final SAMFileHeader header,
                                      final int referenceSequenceIndex,
                                      final int alignmentStart,
                                      final short readNameLength,
                                      final short mappingQuality,
                                      final int indexingBin,
                                      final int cigarLen,
                                      final int flags,
                                      final int readLen,
                                      final int mateReferenceSequenceIndex,
                                      final int mateAlignmentStart,
                                      final int insertSize,
                                      final byte[] variableLengthBlock) {

        return new BAMRecord(header,
                             referenceSequenceIndex,
                             alignmentStart,
                             readNameLength,
                             mappingQuality,
                             indexingBin,
                             cigarLen,
                             flags,
                             readLen,
                             mateReferenceSequenceIndex,
                             mateAlignmentStart,
                             insertSize,
                             variableLengthBlock);
    }
}
