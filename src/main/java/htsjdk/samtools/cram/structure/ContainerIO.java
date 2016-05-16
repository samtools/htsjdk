package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.CramVersionPolicies;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.io.ExposedByteArrayOutputStream;
import htsjdk.samtools.util.Log;
import org.apache.commons.compress.utils.CountingOutputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Methods to read and write CRAM containers.
 */
public class ContainerIO {
    private static final Log log = Log.getInstance(ContainerIO.class);

    /**
     * Reads a CRAM container from the input stream. Returns an EOF container when there is no more data or the EOF marker found.
     *
     * @param version CRAM version to expect
     * @param inputStream      the stream to read from
     * @return a new container object read from the stream
     * @throws IOException as per java IO contract
     */
    public static Container readContainer(final Version version, final InputStream inputStream) throws IOException {
        final Container container = readContainer(version.major, inputStream);
        if (container == null) {
            // this will cause System.exit(1):
            CramVersionPolicies.eofNotFound(version);

            return readContainer(version.major, new ByteArrayInputStream(CramIO.ZERO_B_EOF_MARKER));
        }
        if (container.isEOF()) log.debug("EOF marker found, file/stream is complete.");

        return container;
    }

    /**
     * Reads next container from the stream.
     *
     * @param inputStream the stream to read from
     * @return CRAM container or null if no more data
     * @throws IOException
     */
    private static Container readContainer(final int major, final InputStream inputStream) throws IOException {
        return readContainer(major, inputStream, 0, Integer.MAX_VALUE);
    }

    /**
     * Reads container header only from a {@link InputStream}.
     *
     * @param major the CRAM version to assume
     * @param inputStream    the input stream to read from
     * @return a new {@link Container} object with container header values filled out but empty body (no slices and blocks).
     * @throws IOException as per java IO contract
     */
    public static Container readContainerHeader(final int major, final InputStream inputStream) throws IOException {
        final Container container = new Container();
        final ContainerHeaderIO containerHeaderIO = new ContainerHeaderIO();
        if (!containerHeaderIO.readContainerHeader(major, container, inputStream)) {
            containerHeaderIO.readContainerHeader(container, new ByteArrayInputStream((major >= 3 ? CramIO.ZERO_F_EOF_MARKER : CramIO.ZERO_B_EOF_MARKER)));
            return container;
        }
        return container;
    }

    @SuppressWarnings("SameParameterValue")
    private static Container readContainer(final int major, final InputStream inputStream, final int fromSlice, int howManySlices) throws IOException {

        final long time1 = System.nanoTime();
        final Container container = readContainerHeader(major, inputStream);
        if (container.isEOF()) return container;

        final Block block = Block.readFromInputStream(major, inputStream);
        if (block.getContentType() != BlockContentType.COMPRESSION_HEADER)
            throw new RuntimeException("Content type does not match: " + block.getContentType().name());
        container.header = new CompressionHeader();
        container.header.read(block.getRawContent());

        howManySlices = Math.min(container.landmarks.length, howManySlices);

        if (fromSlice > 0) //noinspection ResultOfMethodCallIgnored
            inputStream.skip(container.landmarks[fromSlice]);

        final List<Slice> slices = new ArrayList<Slice>();
        for (int sliceCount = fromSlice; sliceCount < howManySlices - fromSlice; sliceCount++) {
            final Slice slice = new Slice();
            SliceIO.read(major, slice, inputStream);
            slice.index = sliceCount;
            slices.add(slice);
        }

        container.slices = slices.toArray(new Slice[slices.size()]);

        calculateSliceOffsetsAndSizes(container);

        final long time2 = System.nanoTime();

        log.debug("READ CONTAINER: " + container.toString());
        container.readTime = time2 - time1;

        return container;
    }

    private static void calculateSliceOffsetsAndSizes(final Container container) {
        if (container.slices.length == 0) return;
        for (int i = 0; i < container.slices.length - 1; i++) {
            final Slice slice = container.slices[i];
            slice.offset = container.landmarks[i];
            slice.size = container.landmarks[i + 1] - slice.offset;
            slice.containerOffset = container.offset;
            slice.index = i;
        }
        final Slice lastSlice = container.slices[container.slices.length - 1];
        lastSlice.offset = container.landmarks[container.landmarks.length - 1];
        lastSlice.size = container.containerByteSize - lastSlice.offset;
        lastSlice.containerOffset = container.offset;
        lastSlice.index = container.slices.length - 1;
    }

    /**
     * Writes a {@link Container} header information to a {@link OutputStream}.
     *
     * @param major     the CRAM version to assume
     * @param container the container holding the header to write
     * @param outputStream        the stream to write to
     * @return the number of bytes written
     * @throws IOException as per java IO contract
     */
    public static int writeContainerHeader(final int major, final Container container, final OutputStream outputStream) throws IOException {
        return new ContainerHeaderIO().writeContainerHeader(major, container, outputStream);
    }

    /**
     * Writes a complete {@link Container} with it's header to a {@link OutputStream}. The method is aware of file header containers and is
     * suitable for general purpose use: basically any container is allowed.
     *
     * @param version   the CRAM version to assume
     * @param container the container to write
     * @param outputStream        the stream to write to
     * @return the number of bytes written out
     * @throws IOException as per java IO contract
     */
    public static int writeContainer(final Version version, final Container container, final OutputStream outputStream) throws IOException {
        {
            if (container.blocks != null && container.blocks.length > 0) {

                final Block firstBlock = container.blocks[0];
                final boolean isFileHeaderContainer = firstBlock.getContentType() == BlockContentType.FILE_HEADER;
                if (isFileHeaderContainer) {
                    final ExposedByteArrayOutputStream byteArrayOutputStream = new ExposedByteArrayOutputStream();
                    firstBlock.write(version.major, byteArrayOutputStream);
                    container.containerByteSize = byteArrayOutputStream.size();

                    final int containerHeaderByteSize = new ContainerHeaderIO().writeContainerHeader(version.major, container, outputStream);
                    outputStream.write(byteArrayOutputStream.getBuffer(), 0, byteArrayOutputStream.size());
                    return containerHeaderByteSize + byteArrayOutputStream.size();
                }
            }
        }

        final long time1 = System.nanoTime();
        final ExposedByteArrayOutputStream byteArrayOutputStream = new ExposedByteArrayOutputStream();

        final Block block = new Block();
        block.setContentType(BlockContentType.COMPRESSION_HEADER);
        block.setContentId(0);
        block.setMethod(BlockCompressionMethod.RAW);
        final byte[] bytes;
        try {
            bytes = container.header.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeException("This should have never happened.");
        }
        block.setRawContent(bytes);
        block.write(version.major, byteArrayOutputStream);
        container.blockCount = 1;

        final List<Integer> landmarks = new ArrayList<Integer>();
        for (int i = 0; i < container.slices.length; i++) {
            final Slice slice = container.slices[i];
            landmarks.add(byteArrayOutputStream.size());
            SliceIO.write(version.major, slice, byteArrayOutputStream);
            container.blockCount++;
            container.blockCount++;
            if (slice.embeddedRefBlock != null) container.blockCount++;
            container.blockCount += slice.external.size();
        }
        container.landmarks = new int[landmarks.size()];
        for (int i = 0; i < container.landmarks.length; i++)
            container.landmarks[i] = landmarks.get(i);

        container.containerByteSize = byteArrayOutputStream.size();
        calculateSliceOffsetsAndSizes(container);

        int length = new ContainerHeaderIO().writeContainerHeader(version.major, container, outputStream);
        outputStream.write(byteArrayOutputStream.getBuffer(), 0, byteArrayOutputStream.size());
        length += byteArrayOutputStream.size();

        final long time2 = System.nanoTime();

        log.debug("CONTAINER WRITTEN: " + container.toString());
        container.writeTime = time2 - time1;

        return length;
    }

    /**
     * Calculates the byte size of a container based on the CRAM version.
     *
     * @param version   the CRAM version to assume
     * @param container the container to be weighted
     * @return the total number of bytes the container would occupy if written out
     */
    public static long getByteSize(final Version version, final Container container) {
        final CountingOutputStream countingOutputStream = new CountingOutputStream(new OutputStream() {
            @Override
            public void write(final int b) throws IOException {
            }
        });

        try {
            writeContainer(version, container, countingOutputStream);
            countingOutputStream.close();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        return countingOutputStream.getBytesWritten();
    }
}
