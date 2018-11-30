package htsjdk.samtools.cram.encoding;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.core.BetaIntegerCodec;
import htsjdk.samtools.cram.encoding.external.ExternalByteArrayCodec;
import htsjdk.samtools.cram.io.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ByteArrayLenCodecTest extends HtsjdkTest {
    @Test(dataProvider = "testByteArrays", dataProviderClass = IOTestCases.class)
    public void codecTest(final byte[] values) throws IOException {
        byte[] writtenCore;
        byte[] writtenExternal;
        try (final ByteArrayOutputStream coreOS = new ByteArrayOutputStream();
             final BitOutputStream coreBitOS = new DefaultBitOutputStream(coreOS);
             final ByteArrayOutputStream externalOS = new ByteArrayOutputStream()) {

            // arbitrary choice here: any Integer, byte[] codec pair will do

            final CRAMCodec<Integer> lenWriteCodec = new BetaIntegerCodec(null, coreBitOS, 0, 8);
            final CRAMCodec<byte[]> valWriteCodec = new ExternalByteArrayCodec(null, externalOS);

            final CRAMCodec<byte[]> writeCodec = new ByteArrayLenCodec(lenWriteCodec, valWriteCodec);
            writeCodec.write(values);

            coreBitOS.flush();
            writtenCore = coreOS.toByteArray();

            externalOS.flush();
            writtenExternal = externalOS.toByteArray();
        }

        try (final ByteArrayInputStream coreIS = new ByteArrayInputStream(writtenCore);
             final BitInputStream coreBitIS = new DefaultBitInputStream(coreIS);
             final ByteArrayInputStream externalIS = new ByteArrayInputStream(writtenExternal)) {

            final CRAMCodec<Integer> lenReadCodec = new BetaIntegerCodec(coreBitIS, null, 0, 8);
            final CRAMCodec<byte[]> valReadCodec = new ExternalByteArrayCodec(externalIS, null);

            final CRAMCodec<byte[]> readCodec = new ByteArrayLenCodec(lenReadCodec, valReadCodec);

            final byte[] actual = readCodec.read();
            Assert.assertEquals(actual, values);
        }
    }
}