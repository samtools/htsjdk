package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.common.CramVersionPolicies;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.structure.block.BlockContentType;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Methods to read and write CRAM containers.
 */
public class ContainerIO {
    private static final Log log = Log.getInstance(ContainerIO.class);

    /**
     * Reads a CRAM container from an {@link InputStream}.
     * Returns an EOF container when there is no more data or the EOF marker found.
     *
     * @param version CRAM version to expect
     * @param inputStream the {@link InputStream} stream to read from
     * @param containerByteOffset the byte offset from the start of the stream
     * @return a new container object read from the stream
     */
    public static Container readContainer(final Version version,
                                          final InputStream inputStream,
                                          final long containerByteOffset) {
        Container container = readContainerInternal(version, inputStream, containerByteOffset);
        if (container == null) {
            // this will cause System.exit(1):
            CramVersionPolicies.eofNotFound(version);

            return readContainerInternal(version, new ByteArrayInputStream(CramVersions.eofForVersion(CramVersions.CRAM_v2_1)), containerByteOffset);
        }

        if (container.isEOF()) {
            log.debug("EOF marker found, file/stream is complete.");
        }

        return container;
    }

    // convenience methods for SeekableStream and CountingInputStream
    // TODO: merge these two classes?

    /**
     * Reads a CRAM container from a Seekable input stream.
     * Returns an EOF container when there is no more data or the EOF marker found.
     *
     * @param version CRAM version to expect
     * @param seekableInputStream the {@link SeekableStream} stream to read from
     * @return a new container object read from the stream
     */
    public static Container readContainer(final Version version, final SeekableStream seekableInputStream) {
        try {
            final long containerByteOffset = seekableInputStream.position();
            return readContainer(version, seekableInputStream, containerByteOffset);
        }
        catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Reads a CRAM container from a Counting input stream.
     * Returns an EOF container when there is no more data or the EOF marker found.
     *
     * @param version CRAM version to expect
     * @param countingInputStream the {@link CountingInputStream} stream to read from
     * @return a new container object read from the stream
     */
    public static Container readContainer(final Version version, final CountingInputStream countingInputStream) {
        final long containerByteOffset = countingInputStream.getCount();
        return readContainer(version, countingInputStream, containerByteOffset);
    }

    /**
     * Reads a CRAM container from the input stream. Returns an EOF container when there is no more data or the EOF marker found.
     *
     * @param cramVersion CRAM version to expect
     * @param inputStream the stream to read from
     * @param containerByteOffset the byte offset from the start of the stream
     * @return CRAM container.  EOF container if no more data
     */
    private static Container readContainerInternal(final Version cramVersion,
                                                   final InputStream inputStream,
                                                   final long containerByteOffset) {
        final Container container = ContainerHeaderIO.readContainerHeader(cramVersion, inputStream, containerByteOffset);
        if (container.isEOF()) {
            return container;
        }

        container.compressionHeader = CompressionHeader.read(cramVersion.major, inputStream);

        final int howManySlices = container.landmarks.length;

        final ArrayList<Slice> slices = new ArrayList<>();
        for (int sliceCount = 0; sliceCount < howManySlices; sliceCount++) {
            final Slice slice = SliceIO.read(cramVersion.major, inputStream);
            slices.add(slice);
        }

        container.setSlicesAndByteOffset(slices, containerByteOffset);
        container.distributeIndexingParametersToSlices();

        log.debug("READ CONTAINER: " + container.toString());

        return container;
    }

    /**
     * Writes a complete {@link Container} with it's header to a {@link OutputStream}. The method is aware of file header containers and is
     * suitable for general purpose use: basically any container is allowed.
     *
     * @param version   the CRAM version to assume
     * @param container the container to write
     * @param outputStream        the stream to write to
     * @return the number of bytes written out
     */
    public static int writeContainer(final Version version, final Container container, final OutputStream outputStream) {
        {
            if (container.blocks != null && container.blocks.length > 0) {

                final Block firstBlock = container.blocks[0];
                final boolean isFileHeaderContainer = firstBlock.getContentType() == BlockContentType.FILE_HEADER;
                if (isFileHeaderContainer) {
                    final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    firstBlock.write(version.major, byteArrayOutputStream);
                    container.containerByteSize = byteArrayOutputStream.size();

                    final int containerHeaderByteSize = ContainerHeaderIO.writeContainerHeader(version.major, container, outputStream);
                    try {
                        outputStream.write(byteArrayOutputStream.toByteArray(), 0, byteArrayOutputStream.size());
                    } catch (final IOException e) {
                        throw new RuntimeIOException(e);
                    }
                    return containerHeaderByteSize + byteArrayOutputStream.size();
                }
            }
        }

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        container.compressionHeader.write(version, byteArrayOutputStream);
        container.blockCount = 1;

        final List<Integer> landmarks = new ArrayList<>();
        for (final Slice slice : container.getSlices()) {
            landmarks.add(byteArrayOutputStream.size());
            SliceIO.write(version.major, slice, byteArrayOutputStream);
            container.blockCount++;
            container.blockCount++;
            if (slice.embeddedRefBlock != null) container.blockCount++;
            container.blockCount += slice.external.size();
        }
        container.landmarks = landmarks.stream().mapToInt(Integer::intValue).toArray();
        container.containerByteSize = byteArrayOutputStream.size();

        // Slices require the Container's landmarks and containerByteSize before indexing
        container.distributeIndexingParametersToSlices();

        int length = ContainerHeaderIO.writeContainerHeader(version.major, container, outputStream);
        try {
            outputStream.write(byteArrayOutputStream.toByteArray(), 0, byteArrayOutputStream.size());
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        length += byteArrayOutputStream.size();

        log.debug("CONTAINER WRITTEN: " + container.toString());

        return length;
    }
}
