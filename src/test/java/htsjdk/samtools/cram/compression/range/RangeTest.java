package htsjdk.samtools.cram.compression.range;

import htsjdk.HtsjdkTest;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;

public class RangeTest extends HtsjdkTest {

    private static class TestDataEnvelope {
        public final byte[] testArray;
        public TestDataEnvelope(final byte[] testdata) {
            this.testArray = testdata;
        }
        public String toString() {
            return String.format("Array of size %d", testArray.length);
        }
    }


    @Test
    public void testRoundTrip(){
        final RangeEncode rangeEncode = new RangeEncode();
        final RangeParams rangeParams = new RangeParams(0);
        TestDataEnvelope td = new TestDataEnvelope(new byte[]{0, 1, 2, 3});

        ByteBuffer inputData = ByteBuffer.wrap(td.testArray);
        final ByteBuffer outBuffer = rangeEncode.compress(inputData,rangeParams);

    }

}