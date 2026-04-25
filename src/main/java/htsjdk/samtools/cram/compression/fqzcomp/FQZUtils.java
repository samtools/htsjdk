package htsjdk.samtools.cram.compression.fqzcomp;

import java.nio.ByteBuffer;

/**
 * Utility methods for FQZComp table serialization. Tables (qtab, ptab, dtab, stab) are stored
 * using a two-level run-length encoding scheme where values are sequential from 0.
 *
 * @see FQZCompEncode#storeArray(java.nio.ByteBuffer, int[], int)
 */
public class FQZUtils {

    /**
     * Read a table from a two-level run-length encoded stream. The encoding first stores
     * run lengths for each successive value (0, 1, 2, ...), using 255 as a continuation marker.
     * A second RLE level compresses consecutive identical run-length values.
     *
     * @param inBuffer the input stream to read from
     * @param table output array to populate with decoded values
     * @param size number of elements to decode into the table
     */
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
                while (copy-- > 0) rle[j++] = run;
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

            while (run_len-- > 0) table[z++] = i;
            i++;
        }
    }
}
