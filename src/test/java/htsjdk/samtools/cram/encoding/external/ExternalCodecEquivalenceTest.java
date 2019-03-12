package htsjdk.samtools.cram.encoding.external;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.io.IOTestCases;
import htsjdk.samtools.util.RuntimeIOException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ExternalCodecEquivalenceTest extends HtsjdkTest {
    // show that External codecs are equivalent within certain ranges

    // if all values are 0 <= v <= 0x7F (high bit 7)
    // ExternalByteCodec, ExternalIntegerCodec, and ExternalLongCodec streams are identical
    // because raw bytes, ITF8 and LTF8 are identical

    @Test(dataProvider = "testPositiveByteLists", dataProviderClass = IOTestCases.class)
    public void byteEquivalenceTest(final List<Byte> values) {
        codecPairTest(values, this::externalByteCodeWrite,      this::externalIntegerCodecRead);
        codecPairTest(values, this::externalByteCodeWrite,      this::externalLongCodecRead);
        codecPairTest(values, this::externalIntegerCodecWrite,  this::externalByteCodecRead);
        codecPairTest(values, this::externalIntegerCodecWrite,  this::externalLongCodecRead);
        codecPairTest(values, this::externalLongCodecWrite,     this::externalByteCodecRead);
        codecPairTest(values, this::externalLongCodecWrite,     this::externalIntegerCodecRead);
    }

    // if all values are 0 <= v <= 0x0F FF FF FF (high bit 28)
    // ExternalIntegerCodec, and ExternalLongCodec streams are identical
    // because ITF8 and LTF8 are identical

    @Test(dataProvider = "testUint28Lists", dataProviderClass = IOTestCases.class)
    public void intEquivalenceTest(final List<Integer> values) {
        codecPairTest(values, this::externalIntegerCodecWrite,  this::externalLongCodecRead);
        codecPairTest(values, this::externalLongCodecWrite,     this::externalIntegerCodecRead);
    }

    private <T extends Number> void codecPairTest(final List<T> values, Writer<T> writer, Reader reader) {
        byte[] written = writer.write(values);
        final List<Long> read = reader.read(written, values.size());

        final List<Long> expected = values
                .stream()
                .map(Number::longValue)
                .collect(Collectors.toList());

        Assert.assertEquals(read, expected);
    }

    private interface Writer<T> {
        byte[] write(final List<T> toWrite);
    }

    // byte, integer, and long can all be read as Long
    private interface Reader {
        List<Long> read(final byte[] toRead, final int count);
    }

    private <T extends Number> byte[] externalByteCodeWrite(List<T> values) {
        byte[] written;
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            final CRAMCodec<Byte> writeCodec = new ExternalByteCodec(null, os);

            for (final T value : values) {
                writeCodec.write(value.byteValue());
            }
            os.flush();
            written = os.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return written;
    }

    private <T extends Number> byte[] externalIntegerCodecWrite(List<T> values) {
        byte[] written;
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            final CRAMCodec<Integer> writeCodec = new ExternalIntegerCodec(null, os);

            for (final T value : values) {
                writeCodec.write(value.intValue());
            }
            os.flush();
            written = os.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return written;
    }


    private <T extends Number> byte[] externalLongCodecWrite(List<T> values) {
        byte[] written;
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            final CRAMCodec<Long> writeCodec = new ExternalLongCodec(null, os);

            for (final T value : values) {
                writeCodec.write(value.longValue());
            }
            os.flush();
            written = os.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return written;
    }

    private List<Long> externalByteCodecRead(byte[] written, final int length) {
        return read(written, length, this::externalByteCodecReaderConstructor);
    }

    private List<Long> externalIntegerCodecRead(byte[] written, final int length) {
        return read(written, length, this::externalIntegerCodecReaderConstructor);
    }

    private List<Long> externalLongCodecRead(byte[] written, final int length) {
        return read(written, length, this::externalLongCodecReaderConstructor);
    }

    private <T extends Number> List<Long> read(byte[] written, final int length, final ReadCodecConstructor<T> readCC) {
        List<Long> retval = new ArrayList<>();

        try (final ByteArrayInputStream is = new ByteArrayInputStream(written)) {
            final CRAMCodec<T> readCodec = readCC.reader(is);
            for (int i = 0; i < length; i++) {
                retval.add(readCodec.read().longValue());
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return retval;
    }

    // TODO: move these into the Codec classes?

    private interface ReadCodecConstructor<T> {
        CRAMCodec<T> reader(final ByteArrayInputStream is);
    }

    private CRAMCodec<Byte> externalByteCodecReaderConstructor(final ByteArrayInputStream is) {
        return new ExternalByteCodec(is, null);
    }

    private CRAMCodec<Integer> externalIntegerCodecReaderConstructor(final ByteArrayInputStream is) {
        return new ExternalIntegerCodec(is, null);
    }

    private CRAMCodec<Long> externalLongCodecReaderConstructor(final ByteArrayInputStream is) {
        return new ExternalLongCodec(is, null);
    }
}
