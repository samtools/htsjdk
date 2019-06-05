package htsjdk.samtools.cram.build;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.encoding.readfeatures.Substitution;
import htsjdk.samtools.cram.structure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by vadim on 07/01/2016.
 */
public class CompressionHeaderFactoryTest extends HtsjdkTest {

    @Test
    public void testAP_delta() {
        boolean sorted = true;
        CompressionHeader header = new CompressionHeaderFactory(new CRAMEncodingStrategy()).createCompressionHeader(new ArrayList<>(), sorted);
        Assert.assertEquals(header.isAPDelta(), sorted);

        sorted = false;
        header = new CompressionHeaderFactory(new CRAMEncodingStrategy()).createCompressionHeader(new ArrayList<>(), sorted);
        Assert.assertEquals(header.isAPDelta(), sorted);
    }

    @Test
    public void testGetDataForTag() {
        final int tagID = ReadTag.name3BytesToInt("ACi".getBytes());
        final byte[] data = new byte[]{1, 2, 3, 4};

        final CRAMCompressionRecord cramCompressionRecord = CRAMRecordTestHelper.getCRAMRecordWithTags(
                "rname", 10, 1, 1, 10, new byte[]{'a', 'c', 'g', 't'},2,
                Collections.singletonList(new ReadTag(tagID, data, ValidationStringency.STRICT)));

        final CompressionHeaderFactory factory = new CompressionHeaderFactory(new CRAMEncodingStrategy());

        final byte[] dataForTag = factory.getDataForTag(Collections.singletonList(cramCompressionRecord), tagID);
        Assert.assertEquals(dataForTag, data);
    }

    @Test
    public void testGetBestExternalCompressor() {
        final CRAMEncodingStrategy encodingStrategy = new CRAMEncodingStrategy();
        final CompressionHeaderFactory compressionHeaderFactory = new CompressionHeaderFactory(encodingStrategy);
        Assert.assertNotNull(compressionHeaderFactory.getBestExternalCompressor("".getBytes()));
        Assert.assertNotNull(compressionHeaderFactory.getBestExternalCompressor("qwe".getBytes()));
    }

    @Test
    public void testGeByteSizeRangeOfTagValues() {
        final int tagID = ReadTag.name3BytesToInt("ACi".getBytes());
        // test empty list:
        CompressionHeaderFactory.ByteSizeRange range = CompressionHeaderFactory.getByteSizeRangeOfTagValues(Collections.EMPTY_LIST, tagID);
        Assert.assertNotNull(range);
        Assert.assertEquals(range.min, Integer.MAX_VALUE);
        Assert.assertEquals(range.max, Integer.MIN_VALUE);

        // test single record with a single tag:
        final CRAMCompressionRecord cramCompressionRecord = CRAMRecordTestHelper.getCRAMRecordWithTags(
                "rname", 10, 1, 1, 10, new byte[]{'a', 'c', 'g', 't'},2,
                Collections.singletonList(new ReadTag(tagID, new byte[]{1, 2, 3, 4}, ValidationStringency.STRICT)));

        range = CompressionHeaderFactory.getByteSizeRangeOfTagValues(Collections.singletonList(cramCompressionRecord), tagID);
        Assert.assertNotNull(range);
        Assert.assertEquals(range.min, 4);
        Assert.assertEquals(range.max, 4);
    }

    @Test
    public void testGetTagType() {
        Assert.assertEquals(CompressionHeaderFactory.getTagType(ReadTag.name3BytesToInt("ACi".getBytes())), 'i');
    }

    @Test
    public void testGetUnusedByte() {
        final byte[] data = new byte[256];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        int unusedByte = CompressionHeaderFactory.getUnusedByte(data);
        Assert.assertEquals(unusedByte, -1);

        data[5] = 0;
        unusedByte = CompressionHeaderFactory.getUnusedByte(data);
        Assert.assertEquals(unusedByte, 5);
        data[5] = 5;

        data[150] = 0;
        unusedByte = CompressionHeaderFactory.getUnusedByte(data);
        Assert.assertEquals(unusedByte, 150);
    }

    @Test
    public void testUpdateSubstitutionCodes() {
        final byte refBase = 'A';
        final byte readBase = 'C';
        final Substitution s = new Substitution(1, readBase, refBase);

        final CRAMCompressionRecord cramCompressionRecord = CRAMRecordTestHelper.getCRAMRecordWithReadFeatures(
                "rname", 10, 1, 1, 0,10, new byte[]{'a', 'c', 'g', 't'},2,
                Collections.singletonList(s));

        final SubstitutionMatrix matrix = new SubstitutionMatrix(Collections.singletonList(cramCompressionRecord));

        Assert.assertTrue(s.getCode() == -1);
        CompressionHeaderFactory.updateSubstitutionCodes(Collections.singletonList(cramCompressionRecord), matrix);
        Assert.assertFalse(s.getCode() == -1);
        Assert.assertEquals(s.getCode(), matrix.code(refBase, readBase));
    }

    @Test
    public void testGetTagValueByteSize() {
        Assert.assertEquals(CompressionHeaderFactory.getTagValueByteSize((byte) 'i', 1), 4);
        Assert.assertEquals(CompressionHeaderFactory.getTagValueByteSize((byte) 'I', 1), 4);
        Assert.assertEquals(CompressionHeaderFactory.getTagValueByteSize((byte) 'c', (byte) 1), 1);
        Assert.assertEquals(CompressionHeaderFactory.getTagValueByteSize((byte) 'C', -(byte) 1), 1);
        Assert.assertEquals(CompressionHeaderFactory.getTagValueByteSize((byte) 's', (short) 1), 2);
        Assert.assertEquals(CompressionHeaderFactory.getTagValueByteSize((byte) 'S', -(short) 1), 2);
        Assert.assertEquals(CompressionHeaderFactory.getTagValueByteSize((byte) 'A', 1), 1);
        Assert.assertEquals(CompressionHeaderFactory.getTagValueByteSize((byte) 'f', 1f), 4);

        // string values are null-terminated:
        Assert.assertEquals(CompressionHeaderFactory.getTagValueByteSize((byte) 'Z', "blah-blah"), "blah-blah".length() + 1);

        // byte length of an array tag value is: element type (1 byte) + nof bytes (4 bytes) + nof elements * byte size of element
        int elementTypeLength = 1;
        int arraySizeByteLength = 4;
        int arraySize = 3;
        int byteElementSize = 1;
        int int_float_long_elementSize = 4;
        Assert.assertEquals(CompressionHeaderFactory.getTagValueByteSize((byte) 'B', new byte[]{0, 1, 2}), elementTypeLength + arraySizeByteLength + arraySize * byteElementSize);
        Assert.assertEquals(CompressionHeaderFactory.getTagValueByteSize((byte) 'B', new int[]{0, 1, 2}), elementTypeLength + arraySizeByteLength + arraySize * int_float_long_elementSize);
        Assert.assertEquals(CompressionHeaderFactory.getTagValueByteSize((byte) 'B', new float[]{0, 1, 2}), elementTypeLength + arraySizeByteLength + arraySize * int_float_long_elementSize);
        Assert.assertEquals(CompressionHeaderFactory.getTagValueByteSize((byte) 'B', new long[]{0, 1, 2}), elementTypeLength + arraySizeByteLength + arraySize * int_float_long_elementSize);
    }
}
