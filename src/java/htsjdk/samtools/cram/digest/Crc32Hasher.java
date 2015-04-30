package htsjdk.samtools.cram.digest;

import java.nio.ByteOrder;
import java.util.zip.CRC32;

class Crc32Hasher extends AbstractSerialDigest<Integer> {
    private final CRC32 crc32 = new CRC32();
    private final ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;

    Crc32Hasher(final Combine<Integer> combine) {
        super(combine, null);
    }

    @Override
    protected void resetAndUpdate(final byte[] data) {
        crc32.reset();
        crc32.update(data);
    }

    @Override
    protected Integer getValue() {
        return (int) (crc32.getValue() & 0xFFFFFFFFL);
    }

    @Override
    protected byte[] asByteArray() {
        final byte[] array = new byte[4];
        if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
            array[3] = (byte) ((value >>> 24) & 0xFF);
            array[2] = (byte) ((value >>> 16) & 0xFF);
            array[1] = (byte) ((value >>> 8) & 0xFF);
            array[0] = (byte) ((value) & 0xFF);
        } else {
            array[0] = (byte) ((value >>> 24) & 0xFF);
            array[1] = (byte) ((value >>> 16) & 0xFF);
            array[2] = (byte) ((value >>> 8) & 0xFF);
            array[3] = (byte) ((value) & 0xFF);
        }
        return array;
    }

}
