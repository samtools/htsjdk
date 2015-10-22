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
    public void testHuffmanByteHelper() throws IOException {
        final int size = 100;

        final HuffmanParamsCalculator cal = new HuffmanParamsCalculator();
        for (byte i = Byte.MIN_VALUE; i < Byte.MAX_VALUE; i++) {
            cal.add(i, (i + Byte.MIN_VALUE )/3 + 1);
        }
        cal.calculate();

        final HuffmanByteHelper helper = new HuffmanByteHelper(cal.getValuesAsBytes(), cal.getBitLens());

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DefaultBitOutputStream bos = new DefaultBitOutputStream(baos);

        for (int i = 0; i < size; i++) {
            for (final byte b : cal.getValuesAsBytes()) {
                helper.write(bos, b);
            }
        }

        bos.close();

        final ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        final DefaultBitInputStream bis = new DefaultBitInputStream(bais);

        int counter = 0;
        for (int i = 0; i < size; i++) {
            for (final byte b : cal.getValuesAsBytes()) {
                final byte v = helper.read(bis);
                if (v != b) {
                    Assert.fail("Mismatch: " + v + " vs " + b + " at " + counter);
                }

                counter++;
            }
        }
    }

    @Test
    public void testHuffmanIntHelper() throws IOException {
        final int size = 100;

        final HuffmanParamsCalculator cal = new HuffmanParamsCalculator();
        for (int i = -300; i < 300; i++) {
            cal.add(i, 1+ (i + 300) / 3);
        }
        cal.calculate();

        final HuffmanIntHelper helper = new HuffmanIntHelper(cal.getValues(), cal.getBitLens());

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DefaultBitOutputStream bos = new DefaultBitOutputStream(baos);

        for (int i = 0; i < size; i++) {
            for (final int b : cal.getValues()) {
                helper.write(bos, b);
            }
        }

        bos.close();

        final ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        final DefaultBitInputStream bis = new DefaultBitInputStream(bais);

        int counter = 0;
        for (int i = 0; i < size; i++) {
            for (final int b : cal.getValues()) {
                final int v = helper.read(bis);
                if (v != b) {
                    Assert.fail("Mismatch: " + v + " vs " + b + " at " + counter);
                }

                counter++;
            }
        }
    }
}
