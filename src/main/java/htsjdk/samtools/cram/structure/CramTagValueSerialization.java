package htsjdk.samtools.cram.structure;

import htsjdk.samtools.*;
import htsjdk.samtools.util.StringUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

/**
 * A helper class to read and write CRAM tag values.
 * The functionality is the same as {@link BinaryTagCodec} except there is no underlying BinaryCodec.
 */
public class CramTagValueSerialization {
    public static byte[] writeTagValue(SAMBinaryTagAndValue tv) {
        final byte type = getTagValueType(tv.value);
        return writeSingleValue(type, tv.value, tv.isUnsignedArray());
    }

    public static SAMBinaryTagAndValue readTagValue(short bamTagCode, byte valueType, byte[] data, ValidationStringency validationStringency) {
        final ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        return BinaryTagCodec.readSingleTagValue(bamTagCode, valueType, buf, validationStringency);
    }

    private static final long MAX_INT = Integer.MAX_VALUE;
    private static final long MAX_UINT = MAX_INT * 2 + 1;
    private static final long MAX_SHORT = Short.MAX_VALUE;
    private static final long MAX_USHORT = MAX_SHORT * 2 + 1;
    private static final long MAX_BYTE = Byte.MAX_VALUE;
    private static final long MAX_UBYTE = MAX_BYTE * 2 + 1;

    private static final Charset charset = Charset.forName("US-ASCII");
    private static final int VALUE_BUFFER_LEN = 10 * 1024 * 1024;

    private static final ThreadLocal<ByteBuffer> bufferLocal = new ThreadLocal<ByteBuffer>() {
        @Override
        protected ByteBuffer initialValue() {
            final ByteBuffer buf = ByteBuffer.allocateDirect(VALUE_BUFFER_LEN);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            return buf;
        }
    };


    // copied from net.sf.samtools.BinaryTagCodec 1.62:
    static byte getTagValueType(final Object value) {
        if (value instanceof String) {
            return 'Z';
        } else if (value instanceof Character) {
            return 'A';
        } else if (value instanceof Float) {
            return 'f';
        } else if (value instanceof Number) {
            if (!(value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long)) {
                throw new IllegalArgumentException("Unrecognized tag type "
                        + value.getClass().getName());
            }
            return getIntegerType(((Number) value).longValue());
        } /*
         * Note that H tag type is never written anymore, because B style is
		 * more compact. else if (value instanceof byte[]) { return 'H'; }
		 */ else if (value instanceof byte[] || value instanceof short[]
                || value instanceof int[] || value instanceof float[]) {
            return 'B';
        } else {
            throw new IllegalArgumentException(
                    "When writing BAM, unrecognized tag type "
                            + value.getClass().getName());
        }
    }

    // copied from net.sf.samtools.BinaryTagCodec:
    static byte getIntegerType(final long val) {
        if (val > MAX_UINT) {
            throw new IllegalArgumentException("Integer attribute value too large: " + val);
        }
        if (val > MAX_INT) {
            return 'I';
        }
        if (val > MAX_USHORT) {
            return 'i';
        }
        if (val > MAX_SHORT) {
            return 'S';
        }
        if (val > MAX_UBYTE) {
            return 's';
        }
        if (val > MAX_BYTE) {
            return 'C';
        }
        if (val >= Byte.MIN_VALUE) {
            return 'c';
        }
        if (val >= Short.MIN_VALUE) {
            return 's';
        }
        if (val >= Integer.MIN_VALUE) {
            return 'i';
        }
        throw new IllegalArgumentException(
                "Integer attribute value too negative to be encoded in BAM");
    }

    static byte[] writeSingleValue(final byte tagType, final Object value,
                                          final boolean isUnsignedArray) {
        final ByteBuffer buffer = bufferLocal.get();
        buffer.clear();
        switch (tagType) {
            case 'Z':
                String s = (String) value;
                buffer.put(s.getBytes(charset));
                buffer.put((byte) 0);
                break;
            case 'A':
                buffer.put((byte) ((Character) value).charValue());
                break;
            case 'I':
                // this is tricky:
                buffer.putLong((Long) value);
                buffer.position(buffer.position() - 4);
                break;
            case 'i':
                buffer.putInt(((Number) value).intValue());
                break;
            case 's':
                buffer.putShort(((Number) value).shortValue());
                break;
            case 'S':
                // Convert to unsigned short stored in an int
                buffer.putInt(((Number) value).intValue());
                buffer.position(buffer.position() - 2);
                break;
            case 'c':
                buffer.put(((Number) value).byteValue());
                break;
            case 'C':
                // Convert to unsigned byte stored in an int
                buffer.putShort(((Integer) value).shortValue());
                buffer.position(buffer.position() - 1);
                break;
            case 'f':
                buffer.putFloat((Float) value);
                break;
            case 'H':
                s = StringUtil.bytesToHexString((byte[]) value);
                buffer.put(s.getBytes(charset));
                buffer.put((byte) 0);
                break;
            case 'B':
                writeArray(value, isUnsignedArray, buffer);
                break;
            default:
                throw new SAMFormatException("Unrecognized tag type: "
                        + (char) tagType);
        }

        buffer.flip();
        final byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);

        return bytes;
    }

    static void writeArray(final Object value,
                                   final boolean isUnsignedArray, final ByteBuffer buffer) {
        if (value instanceof byte[]) {
            buffer.put((byte) (isUnsignedArray ? 'C' : 'c'));
            final byte[] array = (byte[]) value;
            buffer.putInt(array.length);
            for (final byte element : array)
                buffer.put(element);

        } else if (value instanceof short[]) {
            buffer.put((byte) (isUnsignedArray ? 'S' : 's'));
            final short[] array = (short[]) value;
            buffer.putInt(array.length);
            for (final short element : array)
                buffer.putShort(element);

        } else if (value instanceof int[]) {
            buffer.put((byte) (isUnsignedArray ? 'I' : 'i'));
            final int[] array = (int[]) value;
            buffer.putInt(array.length);
            for (final int element : array)
                buffer.putInt(element);

        } else if (value instanceof float[]) {
            buffer.put((byte) 'f');
            final float[] array = (float[]) value;
            buffer.putInt(array.length);
            for (final float element : array)
                buffer.putFloat(element);

        } else
            throw new SAMException("Unrecognized array value type: "
                    + value.getClass());
    }
}
