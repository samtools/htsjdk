package htsjdk.samtools.cram.encoding.reader;

import htsjdk.samtools.SAMBinaryTagAndValue;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.common.IntHashMap;
import htsjdk.samtools.cram.structure.CramReadTagSeries;
import htsjdk.samtools.cram.structure.CramTagValueSerialization;
import org.junit.Test;
import org.testng.Assert;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Created by vadim on 17/02/2017.
 */
public class AbstractReaderTest {
    /**
     * Simulate tag reading
     *
     * @throws IOException
     */
    @Test
    public void test_readRecordTags() throws IOException {
        AbstractReader reader = new AbstractReader() {
        };

        // the tags to be used:
        CramReadTagSeries s1 = new CramReadTagSeries("AS", (byte) 'c');
        CramReadTagSeries s2 = new CramReadTagSeries("OQ", (byte) 'Z');
        // the tag values to be used:
        byte[] d1 = CramTagValueSerialization.writeTagValue(new SAMBinaryTagAndValue(s1.bamTagCode, 123));
        byte[] d2 = CramTagValueSerialization.writeTagValue(new SAMBinaryTagAndValue(s2.bamTagCode, "ABCD"));

        // init index codec:
        reader.tagIdListCodec = new ListValuesDataReader(Arrays.asList(0, 1).iterator());
        // init value codecs:
        reader.tagValueCodecs = new IntHashMap<>();
        reader.tagValueCodecs.put(s1.cramTagId, new ListValuesDataReader(Arrays.asList(d1).iterator()));
        reader.tagValueCodecs.put(s2.cramTagId, new ListValuesDataReader(Arrays.asList(d2).iterator()));

        // build tag id dictionary:
        byte[][][] dic = new byte[2][][];
        // the first one is empty, so the first "record" should have no tags:
        dic[0] = new byte[0][];
        // the second "record" should have 2 tags:
        dic[1] = new byte[2][];
        dic[1][0] = CramReadTagSeries.writeCramTagId(s1.cramTagId);
        dic[1][1] = CramReadTagSeries.writeCramTagId(s2.cramTagId);
        reader.setTagIdDictionary(dic);

        // first reading, should return null:
        SAMBinaryTagAndValue tv = reader.readRecordTags(ValidationStringency.STRICT);
        Assert.assertNull(tv);

        // now we should see 2 tags:
        tv = reader.readRecordTags(ValidationStringency.STRICT);
        Assert.assertNotNull(tv.find(s1.bamTagCode));
        Assert.assertNotNull(tv.find(s2.bamTagCode));

        // check the values are what we expect:
        Assert.assertEquals(tv.find(s1.bamTagCode).value, 123);
        Assert.assertEquals(tv.find(s2.bamTagCode).value, "ABCD");
    }

    @Test
    public void test_skipRecordTags() throws IOException {
        AbstractReader reader = new AbstractReader() {
        };

        // the tags to be used:
        CramReadTagSeries s1 = new CramReadTagSeries("AS", (byte) 'c');
        CramReadTagSeries s2 = new CramReadTagSeries("OQ", (byte) 'Z');
        // the tag values to be used:
        byte[] d1 = CramTagValueSerialization.writeTagValue(new SAMBinaryTagAndValue(s1.bamTagCode, 123));
        byte[] d2 = CramTagValueSerialization.writeTagValue(new SAMBinaryTagAndValue(s2.bamTagCode, "ABCD"));

        // init index codec:
        Iterator<Integer> tagIdListCodecIterator = Arrays.asList(0, 1).iterator();
        reader.tagIdListCodec = new ListValuesDataReader(tagIdListCodecIterator);
        // init value codecs:
        reader.tagValueCodecs = new IntHashMap<>();
        Iterator<byte[]> s1_tagValueCodecIterator = Arrays.asList(d1).iterator();
        reader.tagValueCodecs.put(s1.cramTagId, new ListValuesDataReader(s1_tagValueCodecIterator));
        Iterator<byte[]> s2_tagValudeCodecIterator = Arrays.asList(d2).iterator();
        reader.tagValueCodecs.put(s2.cramTagId, new ListValuesDataReader(s2_tagValudeCodecIterator));

        // build tag id dictionary:
        byte[][][] dic = new byte[2][][];
        // the first one is empty, so the first "record" should have no tags:
        dic[0] = new byte[0][];
        // the second "record" should have 2 tags:
        dic[1] = new byte[2][];
        dic[1][0] = CramReadTagSeries.writeCramTagId(s1.cramTagId);
        dic[1][1] = CramReadTagSeries.writeCramTagId(s2.cramTagId);
        reader.setTagIdDictionary(dic);

        // first reading, should have more:
        reader.skipRecordTags();
        Assert.assertTrue(tagIdListCodecIterator.hasNext());
        Assert.assertTrue(s1_tagValueCodecIterator.hasNext());
        Assert.assertTrue(s2_tagValudeCodecIterator.hasNext());

        // second call, nothing left:
        reader.skipRecordTags();
        Assert.assertFalse(tagIdListCodecIterator.hasNext());
        Assert.assertFalse(s1_tagValueCodecIterator.hasNext());
        Assert.assertFalse(s2_tagValudeCodecIterator.hasNext());
    }

    /**
     * A dumb impl to simulate a data reader, backed by an iterator.
     */
    private static class ListValuesDataReader<T> implements DataReader<T> {
        private Iterator<T> it;

        public ListValuesDataReader(Iterator<T> it) {
            this.it = it;
        }

        @Override
        public T readData() throws IOException {
            return it.next();
        }

        @Override
        public T readDataArray(int length) throws IOException {
            return null;
        }
    }
}
