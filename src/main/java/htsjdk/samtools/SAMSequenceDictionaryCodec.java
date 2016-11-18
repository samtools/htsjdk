/*
 * The MIT License
 *
 * Copyright (c) 2016 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package htsjdk.samtools;

import htsjdk.samtools.util.LineReader;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * "On fly" codec SAMSequenceDictionaryCodec.
 * Encodes each sequence and directly writes it to the Dictionary file.
 *
 * @author Pavel_Silin@epam.com, EPAM Systems, Inc. <www.epam.com>
 */
public class SAMSequenceDictionaryCodec {

    private final SAMTextHeaderCodec codec;

    public SAMSequenceDictionaryCodec(final BufferedWriter writer) {
        codec = new SAMTextHeaderCodec();
        codec.setWriter(writer);
    }

    /**
     * Write {@link SAMSequenceRecord}.
     * @param sequenceRecord object to be converted to text.
     */
    public void encodeSQLine(final SAMSequenceRecord sequenceRecord) {
        codec.writeSQLine(sequenceRecord);
    }

    /**
     * Reads text SAM header and converts to a SAMSequenceDictionary object.
     * @param reader Where to get header text from.
     * @param source Name of the input file, for error messages.  May be null.
     * @return complete SAMSequenceDictionary object.
     */
    public SAMSequenceDictionary decode(final LineReader reader, final String source) {
       return codec.decode(reader, source).getSequenceDictionary();
    }

    /**
     * Convert {@link SAMSequenceDictionary} from in-memory representation to text representation.
     * @param dictionary object to be converted to text.
     */
    public void encode(final SAMSequenceDictionary dictionary) {
        dictionary.getSequences().forEach(this::encodeSQLine);

    }

    public void setValidationStringency(final ValidationStringency validationStringency) {
        codec.setValidationStringency(validationStringency);
    }
}
