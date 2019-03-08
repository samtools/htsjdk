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

public class ExternalCodecEquivalenceTest extends HtsjdkTest {
    // show that External codecs are equivalent within certain ranges

    // if all values are 0 <= v <= 0x7F (high bit 7)
    // ExternalByteCodec, ExternalIntegerCodec, and ExternalLongCodec streams are identical
    // because raw bytes, ITF8 and LTF8 are identical

    @Test(dataProvider = "testPositiveByteLists", dataProviderClass = IOTestCases.class)
    public void byteEquivalenceTest(final List<Byte> values) {
        codecPairTestForByte(values, this::writeByteForByte, this::readIntegerForByte);
        codecPairTestForByte(values, this::writeByteForByte, this::readLongForByte);
        codecPairTestForByte(values, this::writeIntegerForByte, this::readByteForByte);
        codecPairTestForByte(values, this::writeIntegerForByte, this::readLongForByte);
        codecPairTestForByte(values, this::writeLongForByte, this::readByteForByte);
        codecPairTestForByte(values, this::writeLongForByte, this::readIntegerForByte);
    }

    private void codecPairTestForByte(final List<Byte> values, ByteWriter writer, ByteReader reader) {
        byte[] written = writer.write(values);
        final List<Byte> read = reader.read(written, values.size());
        Assert.assertEquals(read, values);
    }

    // if all values are 0 <= v <= 0x0F FF FF FF (high bit 28)
    // ExternalIntegerCodec, and ExternalLongCodec streams are identical
    // because ITF8 and LTF8 are identical

    @Test(dataProvider = "testUint28Lists", dataProviderClass = IOTestCases.class)
    public void intEquivalenceTest(final List<Integer> values) {
        codecPairTestForInteger(values, this::writeIntegerForInteger, this::readLongForInteger);
        codecPairTestForInteger(values, this::writeLongForInteger, this::readIntegerForInteger);
    }

    private void codecPairTestForInteger(final List<Integer> values, IntegerWriter writer, IntegerReader reader) {
        byte[] written = writer.write(values);
        final List<Integer> read = reader.read(written, values.size());
        Assert.assertEquals(read, values);
    }

    private interface ByteWriter {
        byte[] write(final List<Byte> toWrite);
    }

    private interface ByteReader {
        List<Byte> read(final byte[] toRead, final int count);
    }

    private byte[] writeByteForByte(List<Byte> values) {
        byte[] written;
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            final CRAMCodec<Byte> writeCodec = new ExternalByteCodec(null, os);

            for (final byte value : values) {
                writeCodec.write(value);
            }
            os.flush();
            written = os.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return written;
    }

    private byte[] writeIntegerForByte(List<Byte> values) {
        byte[] written;
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            final CRAMCodec<Integer> writeCodec = new ExternalIntegerCodec(null, os);

            for (final byte value : values) {
                writeCodec.write((int)value);
            }
            os.flush();
            written = os.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return written;
    }


    private byte[] writeLongForByte(List<Byte> values) {
        byte[] written;
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            final CRAMCodec<Long> writeCodec = new ExternalLongCodec(null, os);

            for (final byte value : values) {
                writeCodec.write((long)value);
            }
            os.flush();
            written = os.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return written;
    }

    private List<Byte> readByteForByte(byte[] written, final int length) {
        final List<Byte> actual = new ArrayList<>(length);
        try (final ByteArrayInputStream is = new ByteArrayInputStream(written)) {
            final CRAMCodec<Byte> readCodec = new ExternalByteCodec(is, null);

            for (int i = 0; i < length; i++) {
                actual.add(readCodec.read());
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return actual;
    }

    private List<Byte> readIntegerForByte(byte[] written, final int length) {
        final List<Byte> actual = new ArrayList<>(length);
        try (final ByteArrayInputStream is = new ByteArrayInputStream(written)) {
            final CRAMCodec<Integer> readCodec = new ExternalIntegerCodec(is, null);

            for (int i = 0; i < length; i++) {
                actual.add(readCodec.read().byteValue());
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return actual;
    }

    private List<Byte> readLongForByte(byte[] written, final int length) {
        final List<Byte> actual = new ArrayList<>(length);
        try (final ByteArrayInputStream is = new ByteArrayInputStream(written)) {
            final CRAMCodec<Long> readCodec = new ExternalLongCodec(is, null);

            for (int i = 0; i < length; i++) {
                actual.add(readCodec.read().byteValue());
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return actual;
    }

    private interface IntegerWriter {
        byte[] write(final List<Integer> toWrite);
    }

    private interface IntegerReader {
        List<Integer> read(final byte[] toRead, final int count);
    }

    private byte[] writeIntegerForInteger(List<Integer> values) {
        byte[] written;
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            final CRAMCodec<Integer> writeCodec = new ExternalIntegerCodec(null, os);

            for (final int value : values) {
                writeCodec.write(value);
            }
            os.flush();
            written = os.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return written;
    }


    private byte[] writeLongForInteger(List<Integer> values) {
        byte[] written;
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            final CRAMCodec<Long> writeCodec = new ExternalLongCodec(null, os);

            for (final int value : values) {
                writeCodec.write((long)value);
            }
            os.flush();
            written = os.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return written;
    }


    private List<Integer> readIntegerForInteger(byte[] written, final int length) {
        final List<Integer> actual = new ArrayList<>(length);
        try (final ByteArrayInputStream is = new ByteArrayInputStream(written)) {
            final CRAMCodec<Integer> readCodec = new ExternalIntegerCodec(is, null);

            for (int i = 0; i < length; i++) {
                actual.add(readCodec.read());
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return actual;
    }

    private List<Integer> readLongForInteger(byte[] written, final int length) {
        final List<Integer> actual = new ArrayList<>(length);
        try (final ByteArrayInputStream is = new ByteArrayInputStream(written)) {
            final CRAMCodec<Long> readCodec = new ExternalLongCodec(is, null);

            for (int i = 0; i < length; i++) {
                actual.add(readCodec.read().intValue());
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return actual;
    }

}
