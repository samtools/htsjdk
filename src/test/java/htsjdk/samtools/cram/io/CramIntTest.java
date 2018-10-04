package htsjdk.samtools.cram.io;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class CramIntTest extends HtsjdkTest {

    @Test(dataProvider = "testInt32Arrays", dataProviderClass = IOTestCases.class)
    public void runTest32(List<Integer> ints) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int value : ints) {
            CramInt.writeInt32(value, baos);
        }

        byte[] bytes = baos.toByteArray();

        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ByteBuffer bb = ByteBuffer.wrap(bytes);

        // a manually-advanced byte buffer
        final int manualBufferSize = 4;
        int manualBufferOffset = 0;
        byte[] manualBuffer = new byte[manualBufferSize];

        byte[] manualWriteBuffer;

        for (int value : ints) {
            int fromStream = CramInt.int32(bais);
            Assert.assertEquals(fromStream, value, "Value did not match");

            int fromBuffer = CramInt.int32(bb);
            Assert.assertEquals(fromBuffer, value, "Value did not match");

            System.arraycopy(bytes, manualBufferOffset, manualBuffer, 0, manualBufferSize);
            int fromManualBuffer = CramInt.int32(manualBuffer);
            Assert.assertEquals(fromManualBuffer, value, "Value did not match");
            manualBufferOffset += manualBufferSize;

            manualWriteBuffer = CramInt.writeInt32(value);
            int fromManualWriteBuffer = CramInt.int32(manualWriteBuffer);
            Assert.assertEquals(fromManualWriteBuffer, value, "Value did not match");
        }

        baos.close();
        bais.close();
    }
}
