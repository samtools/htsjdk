package htsjdk.samtools;

import htsjdk.samtools.util.LineReader;

import java.io.BufferedWriter;
import java.io.Writer;

public class SAMSequenceDictionaryCodec {

    private final SAMTextHeaderCodec codec;

    public SAMSequenceDictionaryCodec(final Writer writer) {
        codec = new SAMTextHeaderCodec();
        codec.setWriter(new BufferedWriter(writer));
    }

    public void encodeSQLine(final SAMSequenceRecord sequenceRecord) {
        codec.writeSQLine(sequenceRecord);
    }

    public SAMSequenceDictionary decode(final LineReader reader, final String source) {
       return codec.decode(reader, source).getSequenceDictionary();
    }

    public void encode(final SAMSequenceDictionary dictionary) {
        dictionary.getSequences().forEach(this::encodeSQLine);
    }

    public void setValidationStringency(final ValidationStringency validationStringency) {
        codec.setValidationStringency(validationStringency);
    }
}
