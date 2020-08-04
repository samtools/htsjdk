package htsjdk.beta.plugin.hapref;

import htsjdk.beta.plugin.HtsEncoder;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.reference.ReferenceSequence;

public interface HaploidReferenceEncoder extends HtsEncoder<HaploidReferenceFormat, SAMSequenceDictionary, ReferenceSequence> { }
