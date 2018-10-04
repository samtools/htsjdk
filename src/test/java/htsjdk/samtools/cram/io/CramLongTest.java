package htsjdk.samtools.cram.io;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class CramLongTest extends HtsjdkTest {
    @Test(dataProvider = "testInt64Arrays", dataProviderClass = IOTestCases.class)
    public void runTest64(List<Long> longs) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (long value : longs) {
            CramLong.writeInt64(value, baos);
        }

        byte[] bytes = baos.toByteArray();

        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ByteBuffer bb = ByteBuffer.wrap(bytes);

        // a manually-advanced byte buffer
        final int manualBufferSize = 8;
        int manualBufferOffset = 0;
        byte[] manualBuffer = new byte[manualBufferSize];

        byte[] manualWriteBuffer;

        for (long value : longs) {
            long fromStream = CramLong.int64(bais);
            Assert.assertEquals(fromStream, value, "Value did not match");

            long fromBuffer = CramLong.int64(bb);
            Assert.assertEquals(fromBuffer, value, "Value did not match");

            System.arraycopy(bytes, manualBufferOffset, manualBuffer, 0, manualBufferSize);
            long fromManualBuffer = CramLong.int64(manualBuffer);
            Assert.assertEquals(fromManualBuffer, value, "Value did not match");
            manualBufferOffset += manualBufferSize;

            manualWriteBuffer = CramLong.writeInt64(value);
            long fromManualWriteBuffer = CramLong.int64(manualWriteBuffer);
            Assert.assertEquals(fromManualWriteBuffer, value, "Value did not match");
        }

        baos.close();
        bais.close();
    }


}
