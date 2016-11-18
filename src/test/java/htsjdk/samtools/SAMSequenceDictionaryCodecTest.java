/*
 * The MIT License
 *
 * Copyright (c) 20016 The Broad Institute
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
import htsjdk.samtools.util.StringLineReader;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.util.Random;

import static org.testng.Assert.*;

/**
 * @author Pavel_Silin@epam.com, EPAM Systems, Inc. <www.epam.com>
 */
public class SAMSequenceDictionaryCodecTest {

    private static final Random RANDOM = new Random();
    private SAMSequenceDictionary dictionary;
    private StringWriter writer;
    private SAMSequenceDictionaryCodec codec;
    private BufferedWriter bufferedWriter;

    @BeforeMethod
    public void setUp() throws Exception {
        String[] seqs = new String[]{"chr1", "chr2", "chr12", "chr16", "chrX"};
        dictionary = new SAMSequenceDictionary();
        for (String seq : seqs) {
            dictionary.addSequence(new SAMSequenceRecord(seq, RANDOM.nextInt(10_000_000)));
        }
        writer = new StringWriter(10);
        bufferedWriter = new BufferedWriter(writer);
        codec = new SAMSequenceDictionaryCodec(bufferedWriter);
    }

    @Test
    public void testEncodeDecode() throws Exception {
        LineReader reader = null;
        try {
            codec.encode(dictionary);
            bufferedWriter.close();
            reader = new StringLineReader(writer.toString());
            SAMSequenceDictionary actual = codec.decode(reader, null);
            assertEquals(actual, dictionary);
        }finally {
            assert reader != null;
            reader.close();
        }
    }

}