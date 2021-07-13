package htsjdk.beta.plugin.hapref;

import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.plugin.HtsDecoder;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.reference.ReferenceSequence;

/**
 *  Base class for all {@link HtsContentType#HAPLOID_REFERENCE} decoders.
 */
public interface HaploidReferenceDecoder extends HtsDecoder<SAMSequenceDictionary, ReferenceSequence> { }
