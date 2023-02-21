package htsjdk.samtools.fastq;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

public class BasicFastqWriterTest extends HtsjdkTest {
    public class OutputStreamWrapper extends ByteArrayOutputStream {
        public int flushCalled = 0;
        public int writeCalled = 0;
        @Override
        public void flush() throws IOException {
            flushCalled++;
            super.flush();
        }

        @Override
        public void write(byte[] b) throws IOException {
            writeCalled++;
            super.write(b);
        }
    }

    /**
     * #1497
     */
    @Test
    public void testFlushNotSpammed() {
        OutputStreamWrapper loggedStream = new OutputStreamWrapper();
        PrintStream ps = new PrintStream(loggedStream);
        try (BasicFastqWriter fqw = new BasicFastqWriter(ps)) {
            for (int i = 0; i < 10000; i++) {
                fqw.write(new FastqRecord("name", "NNNN", null, "...."));
            }
        }
        // flush()/close() results in two flushes
        Assert.assertTrue(loggedStream.flushCalled <= 5, "flush called " + loggedStream.flushCalled + " times which is > 5");
    }
}
