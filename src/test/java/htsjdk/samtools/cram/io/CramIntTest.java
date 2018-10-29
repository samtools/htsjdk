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
    private byte[] streamWritten(final List<Integer> ints) throws IOException {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (final int value : ints) {
                CramInt.writeInt32(value, baos);
            }
            return baos.toByteArray();
        }
    }

    private byte[] byteArrayWritten(final List<Integer> ints) {
        final int bufSize = 4;
        final int arraySize = bufSize * ints.size();
        final byte[] array = new byte[arraySize];

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
    public void checkStreamLittleEndian(final Integer testInt, final byte[] expected) throws IOException {
        final List<Integer> ints = new ArrayList<>();
        ints.add(testInt);

        final byte[] actual = streamWritten(ints);
        Assert.assertEquals(actual, expected);
    }

    @Test(dataProvider = "littleEndianTests32", dataProviderClass = IOTestCases.class)
    public void checkByteArrayLittleEndian(final Integer testInt, final byte[] expected) {
        final List<Integer> ints = new ArrayList<>();
        ints.add(testInt);

        final byte[] actual = byteArrayWritten(ints);
        Assert.assertEquals(actual, expected);
    }

    // Combinatorial tests of 2 CramInt write methods x 3 CramInt read methods

    @Test(dataProvider = "testInt32Lists", dataProviderClass = IOTestCases.class)
    public void matchStreamRead(final List<Integer> ints) throws IOException {
        final byte[][] inputs = {streamWritten(ints), byteArrayWritten(ints)};

        for (final byte[] byteArray : inputs) {
            try (final ByteArrayInputStream bais = new ByteArrayInputStream(byteArray)) {
                for (final int value : ints) {
                    final int fromStream = CramInt.readInt32(bais);
                    Assert.assertEquals(fromStream, value, "Value did not match");
                }
            }
        }
    }

    @Test(dataProvider = "testInt32Lists", dataProviderClass = IOTestCases.class)
    public void matchBufferRead(final List<Integer> ints) throws IOException {
        final byte[][] inputs = {streamWritten(ints), byteArrayWritten(ints)};

        for (final byte[] byteArray : inputs) {
            final ByteBuffer bb = ByteBuffer.wrap(byteArray);

            for (final int value : ints) {
                final int fromBuffer = CramInt.readInt32(bb);
                Assert.assertEquals(fromBuffer, value, "Value did not match");
            }
        }
    }

    @Test(dataProvider = "testInt32Lists", dataProviderClass = IOTestCases.class)
    public void matchByteArrayRead(final List<Integer> ints) throws IOException {
        final byte[][] inputs = {streamWritten(ints), byteArrayWritten(ints)};

        for (final byte[] inputArray : inputs) {
            final int bufSize = 4;
            final byte[] outBuf = new byte[bufSize];

            int offset = 0;

            for (final int value : ints) {
                System.arraycopy(inputArray, offset, outBuf, 0, bufSize);
                final int fromBuffer = CramInt.readInt32(outBuf);
                Assert.assertEquals(fromBuffer, value, "Value did not match");
                offset += bufSize;
            }
        }
    }
}
