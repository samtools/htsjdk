/**
 * ****************************************************************************
 * Copyright 2013 EMBL-EBI
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMFormatException;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecord.SAMTagAndValue;
import htsjdk.samtools.SAMTagUtil;
import htsjdk.samtools.SAMUtils;
import htsjdk.samtools.SAMValidationError;
import htsjdk.samtools.TagValueAndUnsignedArrayFlag;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.StringUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

/**
 * CRAM counterpart of {@link htsjdk.samtools.SAMTag}.
 * TODO: consider merging/dropping this class in favour of SAMTag or a SAMTag implementation.
 */
public class ReadTag implements Comparable<ReadTag> {
    private static final long MAX_INT = Integer.MAX_VALUE;
    private static final long MAX_UINT = MAX_INT * 2 + 1;
    private static final long MAX_SHORT = Short.MAX_VALUE;
    private static final long MAX_USHORT = MAX_SHORT * 2 + 1;
    private static final long MAX_BYTE = Byte.MAX_VALUE;
    private static final long MAX_UBYTE = MAX_BYTE * 2 + 1;

    // non-null
    private String key;
    private String keyAndType;
    public String keyType3Bytes;
    public int keyType3BytesAsInt;
    private char type;
    private Object value;
    private short code;
    private byte index;

    public ReadTag(final int id, final byte[] dataAsByteArray, ValidationStringency validationStringency) {
        this.type = (char) (0xFF & id);
        key = new String(new char[]{(char) ((id >> 16) & 0xFF), (char) ((id >> 8) & 0xFF)});
        value = restoreValueFromByteArray(type, dataAsByteArray, validationStringency);
        keyType3Bytes = this.key + this.type;

        keyType3BytesAsInt = id;

        code = SAMTagUtil.getSingleton().makeBinaryTag(this.key);
    }

    private ReadTag(final String key, final char type, final Object value) {
        if (key == null)
            throw new NullPointerException("Tag key cannot be null.");
        if (value == null)
            throw new NullPointerException("Tag value cannot be null.");

        this.value = value;

        if (key.length() == 2) {
            this.key = key;
            this.type = type;
            // this.type = getTagValueType(value);
            keyAndType = key + ":" + getType();
        } else if (key.length() == 4) {
            this.key = key.substring(0, 2);
            this.type = key.charAt(3);
        }

        keyType3Bytes = this.key + this.type;
        keyType3BytesAsInt = nameType3BytesToInt(this.key, this.type);

        code = SAMTagUtil.getSingleton().makeBinaryTag(this.key);
    }

    public static int name3BytesToInt(final byte[] name) {
        int value = 0xFF & name[0];
        value <<= 8;
        value |= 0xFF & name[1];
        value <<= 8;
        value |= 0xFF & name[2];

        return value;
    }

    public static int nameType3BytesToInt(final String name, final char type) {
        int value = 0xFF & name.charAt(0);
        value <<= 8;
        value |= 0xFF & name.charAt(1);
        value <<= 8;
        value |= 0xFF & type;

        return value;
    }

    public static String intToNameType3Bytes(final int value) {
        final byte b3 = (byte) (0xFF & value);
        final byte b2 = (byte) (0xFF & (value >> 8));
        final byte b1 = (byte) (0xFF & (value >> 16));

        return new String(new byte[]{b1, b2, b3});
    }

    public static String intToNameType4Bytes(final int value) {
        final byte b3 = (byte) (0xFF & value);
        final byte b2 = (byte) (0xFF & (value >> 8));
        final byte b1 = (byte) (0xFF & (value >> 16));

        return new String(new byte[]{b1, b2, ':', b3});
    }

    public SAMTagAndValue createSAMTag() {
        return new SAMTagAndValue(key, value);
    }

    public static ReadTag deriveTypeFromKeyAndType(final String keyAndType, final Object value) {
        if (keyAndType.length() != 4)
            throw new RuntimeException("Tag key and type must be 4 char long: " + keyAndType);

        return new ReadTag(keyAndType.substring(0, 2), keyAndType.charAt(3), value);
    }

    public static ReadTag deriveTypeFromValue(final String key, final Object value) {
        if (key.length() != 2)
            throw new RuntimeException("Tag key must be 2 char long: " + key);

        return new ReadTag(key, getTagValueType(value), value);
    }

    public String getKey() {
        return key;
    }

    @Override
    public int compareTo(@SuppressWarnings("NullableProblems") final ReadTag o) {
        return key.compareTo(o.key);
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof ReadTag))
            return false;

        final ReadTag foe = (ReadTag) obj;
        return key.equals(foe.key) && (value == null && foe.value == null || value != null && value.equals(foe.value));

    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    public Object getValue() {
        return value;
    }

    char getType() {
        return type;
    }

    public String getKeyAndType() {
        return keyAndType;
    }

    public byte[] getValueAsByteArray() {
        return writeSingleValue((byte) type, value, false);
    }

    private static Object restoreValueFromByteArray(final char type, final byte[] array, ValidationStringency validationStringency) {
        final ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return readSingleValue((byte) type, buffer, validationStringency);
    }

    // copied from net.sf.samtools.BinaryTagCodec 1.62:
    private static char getTagValueType(final Object value) {
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
    static private char getIntegerType(final long val) {
        if (val > MAX_UINT) {
            throw new IllegalArgumentException("Integer attribute value too large: "+val);
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

    public void setIndex(final byte i) {
        this.index = i;
    }

    public byte getIndex() {
        return index;
    }

    // yeah, I'm that risky:
    // with a little less thread risky.
    private static final ThreadLocal<ByteBuffer> bufferLocal = new ThreadLocal<ByteBuffer>() {
        @Override
        protected ByteBuffer initialValue() {
            final ByteBuffer buf = ByteBuffer.allocateDirect(10 * 1024 * 1024);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            return buf;
        }
    };

    private static final Charset charset = Charset.forName("US-ASCII");

    public static byte[] writeSingleValue(final byte tagType, final Object value,
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

    private static void writeArray(final Object value,
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

    public static Object readSingleValue(final byte tagType,
                                         final ByteBuffer byteBuffer, ValidationStringency validationStringency) {
        switch (tagType) {
            case 'Z':
                return readNullTerminatedString(byteBuffer);
            case 'A':
                return (char) byteBuffer.get();
            case 'I':
                final long val = byteBuffer.getInt() & 0xffffffffL;
                if (val <= Integer.MAX_VALUE) {
                    return (int)val;
                }
                // If it won't fit into a signed integer, but is within range for an unsigned 32-bit integer,
                // return it directly as a long
                if (! SAMUtils.isValidUnsignedIntegerAttribute(val)) {
                    SAMUtils.processValidationError(new SAMValidationError(SAMValidationError.Type.TAG_VALUE_TOO_LARGE,
                            "Unsigned integer is out of range for a 32-bit unsigned value: " + val, null), validationStringency);
                }
                return val;
            case 'i':
                return byteBuffer.getInt();
            case 's':
                return (int) byteBuffer.getShort();
            case 'S':
                // Convert to unsigned short stored in an int
                return byteBuffer.getShort() & 0xffff;
            case 'c':
                return (int) byteBuffer.get();
            case 'C':
                // Convert to unsigned byte stored in an int
                return byteBuffer.get() & 0xff;
            case 'f':
                return byteBuffer.getFloat();
            case 'H':
                final String hexRep = readNullTerminatedString(byteBuffer);
                return StringUtil.hexStringToBytes(hexRep);
            case 'B':
                final TagValueAndUnsignedArrayFlag valueAndFlag = readArray(
                        byteBuffer);
                return valueAndFlag.value;
            default:
                throw new SAMFormatException("Unrecognized tag type: "
                        + (char) tagType);
        }
    }

    /**
     * Read value of specified type.
     *
     * @param byteBuffer Little-ending byte buffer to read value from.
     * @return CVO containing the value in in-memory Object form, and a flag
     * indicating whether it is unsigned or not.
     */
    private static TagValueAndUnsignedArrayFlag readArray(
            final ByteBuffer byteBuffer) {
        final byte arrayType = byteBuffer.get();
        final boolean isUnsigned = Character.isUpperCase(arrayType);
        final int length = byteBuffer.getInt();
        final Object value;
        switch (Character.toLowerCase(arrayType)) {
            case 'c': {
                final byte[] array = new byte[length];
                value = array;
                byteBuffer.get(array);
                break;
            }
            case 's': {
                final short[] array = new short[length];
                value = array;
                for (int i = 0; i < length; ++i) {
                    array[i] = byteBuffer.getShort();
                }
                break;
            }

            case 'i': {
                final int[] array = new int[length];
                value = array;
                for (int i = 0; i < length; ++i) {
                    array[i] = byteBuffer.getInt();
                }
                break;
            }

            case 'f': {
                final float[] array = new float[length];
                value = array;
                for (int i = 0; i < length; ++i) {
                    array[i] = byteBuffer.getFloat();
                }
                break;
            }

            default:
                throw new SAMFormatException("Unrecognized tag array type: "
                        + (char) arrayType);
        }
        return new TagValueAndUnsignedArrayFlag(value, isUnsigned);
    }

    private static String readNullTerminatedString(final ByteBuffer byteBuffer) {
        // Count the number of bytes in the string
        byteBuffer.mark();
        final int startPosition = byteBuffer.position();
        //noinspection StatementWithEmptyBody
        while (byteBuffer.get() != 0) ;
        final int endPosition = byteBuffer.position();

        // Don't count null terminator
        final byte[] buf = new byte[endPosition - startPosition - 1];
        // Go back to the start of the string and read out the bytes
        byteBuffer.reset();
        byteBuffer.get(buf);
        // Skip over the null terminator
        byteBuffer.get();
        return StringUtil.bytesToString(buf);
    }
}
