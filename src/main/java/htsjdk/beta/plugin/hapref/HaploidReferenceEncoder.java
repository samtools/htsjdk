package htsjdk.beta.plugin.hapref;

import htsjdk.beta.plugin.HtsEncoder;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.reference.ReferenceSequence;

/**
 *  Defines the type parameters instantiated for haploid reference encoders.
 */
public interface HaploidReferenceEncoder extends HtsEncoder<HaploidReferenceFormat, SAMSequenceDictionary, ReferenceSequence> { }
