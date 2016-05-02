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
     *
     * @param inputStream the inputs stream to read from
     * @return array of integers from the input stream
     * @throws IOException as per java IO contract
     */
    public static int[] array(final InputStream inputStream) throws IOException {
        final int size = ITF8.readUnsignedITF8(inputStream);
        final int[] array = new int[size];
        for (int i = 0; i < size; i++)
            array[i] = ITF8.readUnsignedITF8(inputStream);

        return array;
    }

    /**
     * Write CRAM int array to a {@link OutputStream}.
     *
     * @param array the array to be written
     * @param outputStream    the output stream to write to
     * @return the number of bits written out
     * @throws IOException as per java IO contract
     */
    public static int write(final int[] array, final OutputStream outputStream) throws IOException {
        int length = ITF8.writeUnsignedITF8(array.length, outputStream);
        for (final int intValue : array) length += ITF8.writeUnsignedITF8(intValue, outputStream);

        return length;
    }
}
