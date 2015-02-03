package htsjdk.samtools.cram.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Methods to read and write CRAM array of integers data type.
 */
public class CramArray {
    /**
     * Read CRAM int array from a {@link InputStream}.
     * @param is the inputs stream to read from
     * @return array of integers from the input stream
     * @throws IOException as per java IO contract
     */
    public static int[] array(final InputStream is) throws IOException {
        final int size = ITF8.readUnsignedITF8(is);
        final int[] array = new int[size];
        for (int i = 0; i < size; i++)
            array[i] = ITF8.readUnsignedITF8(is);

        return array;
    }

    /**
     * Write CRAM int array to a {@link OutputStream}.
     * @param array the array to be written
     * @param os the output stream to write to
     * @return the number of bits written out
     * @throws IOException as per java IO contract
     */
    public static int write(final int[] array, final OutputStream os) throws IOException {
        int len = ITF8.writeUnsignedITF8(array.length, os);
        for (final int anArray : array) len += ITF8.writeUnsignedITF8(anArray, os);

        return len;
    }
}
