package htsjdk.samtools.cram.encoding.rans;

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

    static void reverse(final ByteBuffer ptr) {
        byte tmp;
        if (ptr.hasArray()) {
            reverse(ptr.array(), ptr.arrayOffset(), ptr.limit());
        } else {
            for (int i = 0; i < ptr.limit(); i++) {
                tmp = ptr.get(i);
                ptr.put(i, ptr.get(ptr.limit() - i - 1));
                ptr.put(ptr.limit() - i - 1, tmp);
            }
        }
    }
}
