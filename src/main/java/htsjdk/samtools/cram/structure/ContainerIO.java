package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.CramVersionPolicies;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.structure.block.BlockContentType;
import htsjdk.samtools.cram.structure.slice.IndexableSlice;
import htsjdk.samtools.cram.structure.slice.Slice;
import htsjdk.samtools.util.Log;

import java.io.*;
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

    /**
     * Reads next container from the stream.
     *
     * @param inputStream the stream to read from
     * @return CRAM container or null if no more data
     * @throws IOException
     */
    public static Container readContainer(final int major, final InputStream inputStream) throws IOException {
        final Container container = readContainerHeader(major, inputStream);
        if (container.isEOF()) {
            return container;
        }

        container.header = CompressionHeader.read(major, inputStream);

        container.slices = new IndexableSlice[container.landmarks.length];
        for (int sliceIndex = 0; sliceIndex < container.landmarks.length; sliceIndex++) {
            final Slice slice = Slice.read(major, inputStream);

            final int byteOffset = container.landmarks[sliceIndex];
            final boolean lastSlice = (sliceIndex == container.landmarks.length - 1);
            final int sliceEnd = lastSlice ? container.containerByteSize : container.landmarks[sliceIndex + 1];
            final int byteSize = sliceEnd - byteOffset;

            container.slices[sliceIndex] = slice.withIndexingMetadata(byteOffset, byteSize, sliceIndex);
        }

        log.debug("READ CONTAINER: " + container.toString());

        return container;
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
                    final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    firstBlock.write(version.major, byteArrayOutputStream);
                    container.containerByteSize = byteArrayOutputStream.size();

                    final int containerHeaderByteSize = new ContainerHeaderIO().writeContainerHeader(version.major, container, outputStream);
                    outputStream.write(byteArrayOutputStream.toByteArray(), 0, byteArrayOutputStream.size());
                    return containerHeaderByteSize + byteArrayOutputStream.size();
                }
            }
        }

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        container.header.write(version, byteArrayOutputStream);
        container.blockCount = 1;

        final List<Integer> landmarks = new ArrayList<>();
        for (final Slice slice : container.slices) {
            landmarks.add(byteArrayOutputStream.size());
            slice.write(version.major, byteArrayOutputStream);
            container.blockCount++;
            container.blockCount++;
            // TODO for Container refactor: is this right?
            // this should be counted in the external blocks
            // also we should use the slice header data block count instead
            if (slice.hasEmbeddedRefBlock()) {
                container.blockCount++;
            }
            container.blockCount += slice.getExternalBlockCount();
        }
        container.landmarks = new int[landmarks.size()];
        for (int i = 0; i < container.landmarks.length; i++)
            container.landmarks[i] = landmarks.get(i);

        container.containerByteSize = byteArrayOutputStream.size();

        int length = new ContainerHeaderIO().writeContainerHeader(version.major, container, outputStream);
        outputStream.write(byteArrayOutputStream.toByteArray(), 0, byteArrayOutputStream.size());
        length += byteArrayOutputStream.size();

        log.debug("CONTAINER WRITTEN: " + container.toString());

        return length;
    }
}
