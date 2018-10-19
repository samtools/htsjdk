package htsjdk.samtools.cram.io;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class CramIntTest extends HtsjdkTest {
    private byte[] streamWritten(List<Integer> ints) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (int value : ints) {
                CramInt.writeInt32(value, baos);
            }
            return baos.toByteArray();
        }
    }

    private byte[] byteArrayWritten(List<Integer> ints) {
        final int bufSize = 4;
        final int arraySize = bufSize * ints.size();
        byte[] array = new byte[arraySize];

        int offset = 0;
        byte[] arrayBuffer;

        for (int value : ints) {
            arrayBuffer = CramInt.writeInt32(value);
            System.arraycopy(arrayBuffer, 0, array, offset, bufSize);
            offset += bufSize;
        }

        return array;
    }

    @Test(dataProvider = "littleEndianTests32", dataProviderClass = IOTestCases.class)
    public void checkStreamLittleEndian(Integer testInt, byte[] expected) throws IOException {
        List<Integer> ints = new ArrayList<>();
        ints.add(testInt);

        byte[] actual = streamWritten(ints);
        Assert.assertEquals(actual, expected);
    }

    @Test(dataProvider = "littleEndianTests32", dataProviderClass = IOTestCases.class)
    public void checkByteArrayLittleEndian(Integer testInt, byte[] expected) {
        List<Integer> ints = new ArrayList<>();
        ints.add(testInt);

        byte[] actual = byteArrayWritten(ints);
        Assert.assertEquals(actual, expected);
    }

    // Combinatorial tests of 2 CramInt write methods x 3 CramInt read methods

    @Test(dataProvider = "testInt32Arrays", dataProviderClass = IOTestCases.class)
    public void matchStreamRead(List<Integer> ints) throws IOException {
        byte[][] inputs = {streamWritten(ints), byteArrayWritten(ints)};

        for (byte[] byteArray : inputs) {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(byteArray)) {
                for (int value : ints) {
                    int fromStream = CramInt.readInt32(bais);
                    Assert.assertEquals(fromStream, value, "Value did not match");
                }
            }
        }
    }

    @Test(dataProvider = "testInt32Arrays", dataProviderClass = IOTestCases.class)
    public void matchBufferRead(List<Integer> ints) throws IOException {
        byte[][] inputs = {streamWritten(ints), byteArrayWritten(ints)};

        for (byte[] byteArray : inputs) {
            ByteBuffer bb = ByteBuffer.wrap(byteArray);

            for (int value : ints) {
                int fromBuffer = CramInt.readInt32(bb);
                Assert.assertEquals(fromBuffer, value, "Value did not match");
            }
        }
    }

    @Test(dataProvider = "testInt32Arrays", dataProviderClass = IOTestCases.class)
    public void matchByteArrayRead(List<Integer> ints) throws IOException {
        byte[][] inputs = {streamWritten(ints), byteArrayWritten(ints)};

        for (byte[] inputArray : inputs) {
            final int bufSize = 4;
            byte[] outBuf = new byte[bufSize];
            int offset = 0;

            for (int value : ints) {
                System.arraycopy(inputArray, offset, outBuf, 0, bufSize);
                int fromBuffer = CramInt.readInt32(outBuf);
                Assert.assertEquals(fromBuffer, value, "Value did not match");
                offset += bufSize;
            }
        }
    }
}
