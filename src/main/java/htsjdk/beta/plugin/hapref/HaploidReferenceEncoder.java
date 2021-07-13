package htsjdk.beta.plugin.hapref;

import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.plugin.HtsEncoder;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.reference.ReferenceSequence;

/**
 *  Base class for all {@link HtsContentType#HAPLOID_REFERENCE}  encoders.
 */
public interface HaploidReferenceEncoder extends HtsEncoder<SAMSequenceDictionary, ReferenceSequence> { }
