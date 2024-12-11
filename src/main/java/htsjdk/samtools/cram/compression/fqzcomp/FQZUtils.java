package htsjdk.samtools.cram.compression.fqzcomp;

import java.nio.ByteBuffer;

public class FQZUtils {

    public static void readArray(final ByteBuffer inBuffer, final int[] table, final int size) {
        int j = 0; // array value
        int z = 0; // array index: table[j]
        int last = -1;

        // Remove first level of run-length encoding
        final int[] rle = new int[1024]; // runs
        while (z < size) {
            final int run = inBuffer.get() & 0xFF;
            rle[j++] = run;
            z += run;

            if (run == last) {
                int copy = inBuffer.get() & 0xFF;
                z += run * copy;
                while (copy-- > 0)
                    rle[j++] = run;
            }
            last = run;
        }

        // Now expand runs in rle to table, noting 255 is max run
        int i = 0;
        j = 0;
        z = 0;
        int part;
        while (z < size) {
            int run_len = 0;
            do {
                part = rle[j++];
                run_len += part;
            } while (part == 255);

            while (run_len-- > 0)
                table[z++] = i;
            i++;
        }
    }
}
