package htsjdk.beta.plugin.hapref;

import htsjdk.beta.plugin.HtsDecoder;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.reference.ReferenceSequence;

public interface HaploidReferenceDecoder extends HtsDecoder<HaploidReferenceFormat, SAMSequenceDictionary, ReferenceSequence> { }
