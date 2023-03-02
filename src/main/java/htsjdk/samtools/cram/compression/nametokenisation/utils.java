package htsjdk.samtools.cram.compression.nametokenisation;

import java.nio.ByteBuffer;

public class utils {

    public static int readUint7(ByteBuffer cp) {
        int i = 0;
        int c;
        do {
            //read byte
            c = cp.get();
            i = (i << 7) | (c & 0x7f);
        } while ((c & 0x80) != 0);
        return i;
    }
}