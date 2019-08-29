package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.CramVersionPolicies;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.io.CountingInputStream;
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
            return readContainerInternal(version.major, seekableInputStream, containerByteOffset);
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
        return readContainerInternal(version.major, countingInputStream, containerByteOffset);
    }

    /**
     * Reads next container from the stream.
     *
     * @param major the CRAM version to assume
     * @param inputStream the stream to read from
     * @param containerByteOffset the byte offset from the start of the stream
     * @return CRAM container or null if no more data
     */
    private static Container readContainerInternal(final int major,
                                                   final InputStream inputStream,
                                                   final long containerByteOffset) {

        final ContainerHeader containerHeader = ContainerHeaderIO.readContainerHeader(major, inputStream);
        if (containerHeader.isEOF()) {
            return new Container(containerHeader, containerByteOffset);
        }

        final CompressionHeader compressionHeader = new CompressionHeader(major, inputStream);

        final ArrayList<Slice> slices = new ArrayList<>();
        for (int sliceCounter = 0; sliceCounter < containerHeader.getLandmarks().length; sliceCounter++) {
            final Slice slice = SliceIO.read(major, compressionHeader, inputStream);
            slice.containerByteOffset = containerByteOffset;
            slices.add(slice);
        }

        final Container container = new Container(
                containerHeader,
                compressionHeader,
                slices,
                containerByteOffset);

        container.distributeIndexingParametersToSlices();

        return container;
    }

    /**
     * Writes a complete {@link Container} with it's header to a {@link OutputStream}.
     *
     * @param version   the CRAM version to assume
     * @param container the container to write
     * @param outputStream  the stream to write to
     * @return the number of bytes written out
     */
    public static int writeContainer(final Version version, final Container container, final OutputStream outputStream) {
        // use this BAOS for two purposes: writing out and counting bytes for landmarks/containerBlocksByteSize
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // write out the compression header...
        container.compressionHeader.write(version, byteArrayOutputStream);

        // TODO: ensure that the Container blockCount stays in sync with the
        // Slice's blockCount in SliceIO.write()
        // 1 Compression Header Block

        // ...then write out the slice blocks....
        int blockCount = 1;
        final List<Integer> landmarks = new ArrayList<>();
        for (final Slice slice : container.getSlices()) {
            // landmark 0 = byte length of the compression header
            // landmarks after 0 = byte length of the compression header plus all slices before this one
            landmarks.add(byteArrayOutputStream.size());
            SliceIO.write(version.major, slice, byteArrayOutputStream);
            // 1 Slice Header Block
            blockCount++;
            // 1 Core Data Block per Slice
            blockCount++;
            // TODO: should we count the embedded reference block as an additional block?
            if (slice.getEmbeddedReferenceBlock() != null) {
                blockCount++;
            }
            // Each Slice has a variable number of External Data Blocks
            blockCount += slice.getSliceBlocks().getNumberOfExternalBlocks();
        }
        container.getContainerHeader().setLandmarks(landmarks.stream().mapToInt(Integer::intValue).toArray());
        // compression header plus all slices, if any (EOF Containers do not; File Header Containers are handled above)
        container.getContainerHeader().setContainerBlocksByteSize(byteArrayOutputStream.size());

        // Slices require the Container's landmarks and containerBlocksByteSize in case we're indexing
        container.distributeIndexingParametersToSlices();

        // ...then write the container header
        final int containerHeaderLength = ContainerHeaderIO.writeContainerHeader(version.major, container.getContainerHeader(), outputStream);

        // .. and finally, write the entire stream to the actual output stream, now that we know how big it is
        try {
            outputStream.write(byteArrayOutputStream.toByteArray(), 0, container.getContainerHeader().getContainerBlocksByteSize());
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }

        return containerHeaderLength + container.getContainerHeader().getContainerBlocksByteSize();
    }

}
