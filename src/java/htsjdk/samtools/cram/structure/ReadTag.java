/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFormatException;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecord.SAMTagAndValue;
import htsjdk.samtools.SAMTagUtil;
import htsjdk.samtools.TagValueAndUnsignedArrayFlag;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.StringUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;

public class ReadTag implements Comparable<ReadTag> {
    private static final long MAX_INT = Integer.MAX_VALUE;
    private static final long MAX_UINT = MAX_INT * 2 + 1;
    private static final long MAX_SHORT = Short.MAX_VALUE;
    private static final long MAX_USHORT = MAX_SHORT * 2 + 1;
    private static final long MAX_BYTE = Byte.MAX_VALUE;
    private static final long MAX_UBYTE = MAX_BYTE * 2 + 1;

    public static final int OQZ = (('O' << 16) | ('Q' << 8)) | 'Z';
    public static final int BQZ = (('B' << 16) | ('Q' << 8)) | 'Z';

    // non-null
    private String key;
    private String keyAndType;
    public String keyType3Bytes;
    public int keyType3BytesAsInt;
    private char type;
    private Object value;
    public short code;
    private byte index;

    public ReadTag(int id, byte[] dataAsByteArray) {
        this.type = (char) (0xFF & id);
        key = new String(new char[]{(char) ((id >> 16) & 0xFF),
                (char) ((id >> 8) & 0xFF)});
        value = restoreValueFromByteArray(type, dataAsByteArray);
        keyType3Bytes = this.key + this.type;

        keyType3BytesAsInt = id;

        code = SAMTagUtil.getSingleton().makeBinaryTag(this.key);
    }

    public ReadTag(String key, char type, Object value) {
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

    public static int name3BytesToInt(byte[] name) {
        int value = 0xFF & name[0];
        value <<= 8;
        value |= 0xFF & name[1];
        value <<= 8;
        value |= 0xFF & name[2];

        return value;
    }

    public static int nameType3BytesToInt(String name, char type) {
        int value = 0xFF & name.charAt(0);
        value <<= 8;
        value |= 0xFF & name.charAt(1);
        value <<= 8;
        value |= 0xFF & type;

        return value;
    }

    public static String intToNameType3Bytes(int value) {
        byte b3 = (byte) (0xFF & value);
        byte b2 = (byte) (0xFF & (value >> 8));
        byte b1 = (byte) (0xFF & (value >> 16));

        return new String(new byte[]{b1, b2, b3});
    }

    public static String intToNameType4Bytes(int value) {
        byte b3 = (byte) (0xFF & value);
        byte b2 = (byte) (0xFF & (value >> 8));
        byte b1 = (byte) (0xFF & (value >> 16));

        return new String(new byte[]{b1, b2, ':', b3});
    }

    public SAMTagAndValue createSAMTag() {
        return new SAMTagAndValue(key, value);
    }

    public static ReadTag deriveTypeFromKeyAndType(String keyAndType,
                                                   Object value) {
        if (keyAndType.length() != 4)
            throw new RuntimeException("Tag key and type must be 4 char long: "
                    + keyAndType);

        return new ReadTag(keyAndType.substring(0, 2), keyAndType.charAt(3),
                value);
    }

    public static ReadTag deriveTypeFromValue(String key, Object value) {
        if (key.length() != 2)
            throw new RuntimeException("Tag key must be 2 char long: " + key);

        return new ReadTag(key, getTagValueType(value), value);
    }

    public String getKey() {
        return key;
    }

    @Override
    public int compareTo(ReadTag o) {
        return key.compareTo(o.key);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ReadTag))
            return false;

        ReadTag foe = (ReadTag) obj;
        if (!key.equals(foe.key))
            return false;
        if (value == null && foe.value == null)
            return true;
        if (value != null && value.equals(foe.value))
            return true;

        return false;
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    public Object getValue() {
        return value;
    }

    public char getType() {
        return type;
    }

    public String getKeyAndType() {
        return keyAndType;
    }

    public byte[] getValueAsByteArray() {
        return writeSingleValue((byte) type, value, false);
    }

    public static Object restoreValueFromByteArray(char type, byte[] array) {
        ByteBuffer buf = ByteBuffer.wrap(array);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return readSingleValue((byte) type, buf, null);
    }

    // copied from net.sf.samtools.BinaryTagCodec 1.62:
    public static char getTagValueType(final Object value) {
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
            throw new IllegalArgumentException(
                    "Integer attribute value too large to be encoded in BAM");
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

    public void setIndex(byte i) {
        this.index = i;
    }

    public byte getIndex() {
        return index;
    }

    // yeah, I'm that risky:
    private static final ByteBuffer buf = ByteBuffer
            .allocateDirect(10 * 1024 * 1024);

    static {
        buf.order(ByteOrder.LITTLE_ENDIAN);
    }

    private static final Charset charset = Charset.forName("US-ASCII");

    public static byte[] writeSingleValue(byte tagType, Object value,
                                          boolean isUnsignedArray) {

        buf.clear();
        switch (tagType) {
            case 'Z':
                String s = (String) value;
                buf.put(s.getBytes(charset));
                buf.put((byte) 0);
                break;
            case 'A':
                buf.put((byte) ((Character) value).charValue());
                break;
            case 'I':
                // this is tricky:
                buf.putLong((Long) value);
                buf.position(buf.position() - 4);
                break;
            case 'i':
                buf.putInt((Integer) value);
                break;
            case 's':
                buf.putShort(((Number) value).shortValue());
                break;
            case 'S':
                // Convert to unsigned short stored in an int
                buf.putInt(((Number) value).intValue());
                buf.position(buf.position() - 2);
                break;
            case 'c':
                buf.put(((Number) value).byteValue());
                break;
            case 'C':
                // Convert to unsigned byte stored in an int
                buf.putShort(((Integer) value).shortValue());
                buf.position(buf.position() - 1);
                break;
            case 'f':
                buf.putFloat((Float) value);
                break;
            case 'H':
                s = StringUtil.bytesToHexString((byte[]) value);
                buf.put(s.getBytes(charset));
                buf.put((byte) 0);
                break;
            case 'B':
                writeArray(value, isUnsignedArray, buf);
                break;
            default:
                throw new SAMFormatException("Unrecognized tag type: "
                        + (char) tagType);
        }

        buf.flip();
        byte[] bytes = new byte[buf.limit()];
        buf.get(bytes);

        return bytes;
    }

    private static void writeArray(final Object value,
                                   final boolean isUnsignedArray, ByteBuffer buf) {
        if (value instanceof byte[]) {
            buf.put((byte) (isUnsignedArray ? 'C' : 'c'));
            final byte[] array = (byte[]) value;
            buf.putInt(array.length);
            for (final byte element : array)
                buf.put(element);

        } else if (value instanceof short[]) {
            buf.put((byte) (isUnsignedArray ? 'S' : 's'));
            final short[] array = (short[]) value;
            buf.putInt(array.length);
            for (final short element : array)
                buf.putShort(element);

        } else if (value instanceof int[]) {
            buf.put((byte) (isUnsignedArray ? 'I' : 'i'));
            final int[] array = (int[]) value;
            buf.putInt(array.length);
            for (final int element : array)
                buf.putInt(element);

        } else if (value instanceof float[]) {
            buf.put((byte) 'f');
            final float[] array = (float[]) value;
            buf.putInt(array.length);
            for (final float element : array)
                buf.putFloat(element);

        } else
            throw new SAMException("Unrecognized array value type: "
                    + value.getClass());
    }

    public static Object readSingleValue(final byte tagType,
                                         final ByteBuffer byteBuffer,
                                         final ValidationStringency validationStringency) {
        switch (tagType) {
            case 'Z':
                return readNullTerminatedString(byteBuffer);
            case 'A':
                return (char) byteBuffer.get();
            case 'I':
                final long val = byteBuffer.getInt() & 0xffffffffL;
                if (val <= Integer.MAX_VALUE) {
                    return (int) val;
                }
                throw new RuntimeException(
                        "Tag value is too large to store as signed integer.");
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
                        byteBuffer, validationStringency);
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
            final ByteBuffer byteBuffer,
            final ValidationStringency validationStringency) {
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
        while (byteBuffer.get() != 0) {
        }
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

    public static void main(String[] args) {
        SAMFileHeader h = new SAMFileHeader();
        SAMRecord r = new SAMRecord(h);
        r.setAttribute("OQ", "A:LKAS:LKASDJKL".getBytes());
        r.setAttribute("XA", 1333123);
        r.setAttribute("XB", (byte) 31);
        r.setAttribute("XB", 'Q');
        r.setAttribute("XC", "A STRING");

        int intValue = 1123123123;
        byte[] data = writeSingleValue((byte) 'i', intValue, false);
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        Object value = readSingleValue((byte) 'i', byteBuffer, null);
        if (intValue != ((Integer) value).intValue())
            throw new RuntimeException("Failed for " + intValue);

        String sValue = "qwe";
        data = writeSingleValue((byte) 'Z', sValue, false);
        byteBuffer = ByteBuffer.wrap(data);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        value = readSingleValue((byte) 'Z', byteBuffer, null);
        if (!sValue.equals(value))
            throw new RuntimeException("Failed for " + sValue);

        byte[] baValue = "qwe".getBytes();
        data = writeSingleValue((byte) 'B', baValue, false);
        byteBuffer = ByteBuffer.wrap(data);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        value = readSingleValue((byte) 'B', byteBuffer, null);
        if (!Arrays.equals(baValue, (byte[]) value))
            throw new RuntimeException("Failed for " + baValue);
    }

}
