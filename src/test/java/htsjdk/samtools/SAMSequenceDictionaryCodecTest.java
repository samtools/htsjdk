package htsjdk.samtools;

import htsjdk.samtools.util.LineReader;
import htsjdk.samtools.util.StringLineReader;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.util.Random;

import static org.testng.Assert.*;

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