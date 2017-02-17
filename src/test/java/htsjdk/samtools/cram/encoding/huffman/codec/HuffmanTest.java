package htsjdk.samtools.cram.encoding.huffman.codec;

import htsjdk.samtools.cram.io.DefaultBitInputStream;
import htsjdk.samtools.cram.io.DefaultBitOutputStream;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by vadim on 22/04/2015.
 */
public class HuffmanTest {

    @Test
    public void testHuffmanIntHelper() throws IOException {
        int size = 1000000;

        HuffmanParamsCalculator cal = new HuffmanParamsCalculator();
        // CRAM read tag ids for tags: OQZ X0C X0c X0s X1C X1c X1s XAZ XCc XTA OPi OCZ BQZ AMc
        cal.add(5198170);
        cal.add(5779523);
        cal.add(5779555);
        cal.add(5779571);
        cal.add(5779779);
        cal.add(5779811);
        cal.add(5779827);
        cal.add(5783898);
        cal.add(5784419);
        cal.add(5788737);
        cal.add(5197929);
        cal.add(5194586);
        cal.add(4346202);
        cal.add(4279651);

        cal.calculate();

        HuffmanIntHelper helper = new HuffmanIntHelper(cal.values(), cal.bitLens());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DefaultBitOutputStream bos = new DefaultBitOutputStream(baos);

        for (int i = 0; i < size; i++) {
            for (int b : cal.values()) {
                helper.write(bos, b);
            }
        }

        bos.close();
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        DefaultBitInputStream bis = new DefaultBitInputStream(bais);

        int counter = 0;
        for (int i = 0; i < size; i++) {
            for (int b : cal.values()) {
                int v = helper.read(bis);
                if (v != b) {
                    Assert.fail("Mismatch: " + v + " vs " + b + " at " + counter);
                }

                counter++;
            }
        }
    }

    @Test
    public void testHuffmanByteHelper() throws IOException {
        int size = 1000000;

        long time5 = System.nanoTime();
        HuffmanParamsCalculator cal = new HuffmanParamsCalculator();
        for (byte i = 33; i < 33 + 15; i++) {
            cal.add(i);
        }
        cal.calculate();

        HuffmanByteHelper helper = new HuffmanByteHelper(cal.valuesAsBytes(), cal.bitLens());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DefaultBitOutputStream bos = new DefaultBitOutputStream(baos);

        for (int i = 0; i < size; i++) {
            for (byte b : cal.valuesAsBytes()) {
                helper.write(bos, b);
            }
        }

        bos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        DefaultBitInputStream bis = new DefaultBitInputStream(bais);

        int counter = 0;
        for (int i = 0; i < size; i++) {
            for (int b : cal.values()) {
                int v = helper.read(bis);
                if (v != b) {
                    Assert.fail("Mismatch: " + v + " vs " + b + " at " + counter);
                }

                counter++;
            }
        }
    }
}
