package htsjdk.samtools.cram.encoding.rans;

import java.nio.ByteBuffer;

/**
 * Created by vadim on 03/02/2015.
 */
class Utils {
    private static void reverse(byte[] array, int offset, int size) {
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

    static void reverse(ByteBuffer ptr) {
        byte tmp ;
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
