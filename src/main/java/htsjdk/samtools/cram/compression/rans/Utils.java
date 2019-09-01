package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;

class Utils {

    private static void reverse(final byte[] array, final int offset, final int size) {
        if (array == null) {
            return;
        }
        int i = offset;
        int j = offset + size - 1;
        byte tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }

    static void reverse(final ByteBuffer byteBuffer) {
        byte tmp;
        if (byteBuffer.hasArray()) {
            reverse(byteBuffer.array(), byteBuffer.arrayOffset(), byteBuffer.limit());
        } else {
            for (int i = 0; i < byteBuffer.limit(); i++) {
                tmp = byteBuffer.get(i);
                byteBuffer.put(i, byteBuffer.get(byteBuffer.limit() - i - 1));
                byteBuffer.put(byteBuffer.limit() - i - 1, tmp);
            }
        }
    }
}
