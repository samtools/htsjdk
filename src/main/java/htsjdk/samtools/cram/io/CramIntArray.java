package htsjdk.samtools.cram.io;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Methods to read and write CRAM array of integers data type.
 */
public class CramIntArray {
    /**
     * Read CRAM int array from a {@link InputStream}.
     *
     * @param inputStream the inputs stream to read from
     * @return array of integers from the input stream
     */
    public static int[] array(final InputStream inputStream) {
        final int size = ITF8.readUnsignedITF8(inputStream);
        final int[] array = new int[size];
        for (int i = 0; i < size; i++)
            array[i] = ITF8.readUnsignedITF8(inputStream);

        return array;
    }

    /**
     * Read CRAM int array from a {@link InputStream} as a List.
     *
     * @param inputStream the inputs stream to read from
     * @return List of integers from the input stream
     */
    public static List<Integer> arrayAsList(final InputStream inputStream) {
        final int size = ITF8.readUnsignedITF8(inputStream);
        final List<Integer> intList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            intList.add(ITF8.readUnsignedITF8(inputStream));
        }

        return intList;
    }

    /**
     * Write CRAM int List to a {@link OutputStream}.
     *
     * @param intList the List to be written
     * @param outputStream    the output stream to write to
     * @return the number of bits written out
     */
    public static int write(final List<Integer> intList, final OutputStream outputStream) {
        int length = ITF8.writeUnsignedITF8(intList.size(), outputStream);
        for (final int intValue : intList) {
            length += ITF8.writeUnsignedITF8(intValue, outputStream);
        }

        return length;
    }
    /**
     * Write CRAM int array to a {@link OutputStream}.
     *
     * @param array the array to be written
     * @param outputStream    the output stream to write to
     * @return the number of bits written out
     */
    public static int write(final int[] array, final OutputStream outputStream) {
        int length = ITF8.writeUnsignedITF8(array.length, outputStream);
        for (final int intValue : array) {
            length += ITF8.writeUnsignedITF8(intValue, outputStream);
        }

        return length;
    }
}
