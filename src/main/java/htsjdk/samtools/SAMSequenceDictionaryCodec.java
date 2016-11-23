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
import java.io.BufferedWriter;

/**
 * "On the fly" codec SAMSequenceDictionaryCodec.
 * Encodes each sequence and directly writes it to the Dictionary file.
 *
 * To use this class you should provide BufferedWriter to it, and so you should close it as you stop using this class.
 * You can work with this class as shown below.
 *
 * Example of using this class:
 *
 * List<SAMSequenceRecord> dict = ...;
 *
 * //open BufferedReader and close in try-with-resources
 * try(BufferedWriter writer = new BufferedWriter(new FileWriter("path/to/file"))) {
 *      SAMSequenceDictionaryCodec codec = new SAMSequenceDictionaryCodec(writer);
 *
 *      //we have list of sequences, so encode header line and after that encode each sequence
 *      codec.encodeHeaderLine(false);
 *      dict.forEach(codec::encodeSequenceRecord);
 *}
 *
 * or
 *
 * SAMSequenceDictionary dict = ...;
 *
 * //open BufferedReader and close in try-with-resources
 * try(BufferedWriter writer = new BufferedWriter(new FileWriter("path/to/file"))) {
 *      SAMSequenceDictionaryCodec codec = new SAMSequenceDictionaryCodec(writer);
 *
 *      //we have complete {@link SAMSequenceDictionary}, so just encode it.
 *      codec.encode(dict);
 *}
 *
 * @author Pavel_Silin@epam.com, EPAM Systems, Inc. <www.epam.com>
 */
public class SAMSequenceDictionaryCodec {

    private static final SAMFileHeader EMPTY_HEADER = new SAMFileHeader();

    private final SAMTextHeaderCodec codec;

    public SAMSequenceDictionaryCodec(final BufferedWriter writer) {
        codec = new SAMTextHeaderCodec();
        codec.setmFileHeader(EMPTY_HEADER);
        codec.setWriter(writer);
    }

    /**
     * Write {@link SAMSequenceRecord}.
     * @param sequenceRecord object to be converted to text.
     */
    public void encodeSequenceRecord(final SAMSequenceRecord sequenceRecord) {
        codec.encodeSequenceRecord(sequenceRecord);
    }

    /**
     * Write Header line.
     * @param keepExistingVersionNumber boolean flag to keep existing version number.
     */
    public void encodeHeaderLine(final boolean keepExistingVersionNumber) {
        codec.encodeHeaderLine(keepExistingVersionNumber);
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
        codec.encodeHeaderLine(false);
        dictionary.getSequences().forEach(this::encodeSequenceRecord);
    }

    public void setValidationStringency(final ValidationStringency validationStringency) {
        codec.setValidationStringency(validationStringency);
    }
}
