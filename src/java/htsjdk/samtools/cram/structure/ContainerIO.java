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
     * @param is the stream to read from
     * @return a new container object read from the stream
     * @throws IOException as per java IO contract
     */
    public static Container readContainer(final Version version, final InputStream is) throws IOException {
        final Container c = readContainer(version.major, is);
        if (c == null) {
            // this will cause System.exit(1):
            CramVersionPolicies.eofNotFound(version);

            return readContainer(version.major, new ByteArrayInputStream(CramIO.ZERO_B_EOF_MARKER));
        }
        if (c.isEOF()) log.debug("EOF marker found, file/stream is complete.");

        return c;
    }

    /**
     * Reads next container from the stream.
     *
     * @param is the stream to read from
     * @return CRAM container or null if no more data
     * @throws IOException
     */
    private static Container readContainer(final int major, final InputStream is) throws IOException {
        return readContainer(major, is, 0, Integer.MAX_VALUE);
    }

    /**
     * Reads container header only from a {@link InputStream}.
     *
     * @param major the CRAM version to assume
     * @param is    the input stream to read from
     * @return a new {@link Container} object with container header values filled out but empty body (no slices and blocks).
     * @throws IOException as per java IO contract
     */
    public static Container readContainerHeader(final int major, final InputStream is) throws IOException {
        final Container c = new Container();
        final ContainerHeaderIO containerHeaderIO = new ContainerHeaderIO();
        if (!containerHeaderIO.readContainerHeader(major, c, is)) {
            containerHeaderIO.readContainerHeader(c, new ByteArrayInputStream((major >= 3 ? CramIO.ZERO_F_EOF_MARKER : CramIO.ZERO_B_EOF_MARKER)));
            return c;
        }
        return c;
    }

    @SuppressWarnings("SameParameterValue")
    private static Container readContainer(final int major, final InputStream is, final int fromSlice, int howManySlices) throws IOException {

        final long time1 = System.nanoTime();
        final Container c = readContainerHeader(major, is);
        if (c.isEOF()) return c;

        final Block chb = Block.readFromInputStream(major, is);
        if (chb.getContentType() != BlockContentType.COMPRESSION_HEADER)
            throw new RuntimeException("Content type does not match: " + chb.getContentType().name());
        c.h = new CompressionHeader();
        c.h.read(chb.getRawContent());

        howManySlices = Math.min(c.landmarks.length, howManySlices);

        if (fromSlice > 0) //noinspection ResultOfMethodCallIgnored
            is.skip(c.landmarks[fromSlice]);

        final List<Slice> slices = new ArrayList<Slice>();
        for (int s = fromSlice; s < howManySlices - fromSlice; s++) {
            final Slice slice = new Slice();
            SliceIO.read(major, slice, is);
            slice.index = s;
            slices.add(slice);
        }

        c.slices = slices.toArray(new Slice[slices.size()]);

        calculateSliceOffsetsAndSizes(c);

        final long time2 = System.nanoTime();

        log.debug("READ CONTAINER: " + c.toString());
        c.readTime = time2 - time1;

        return c;
    }

    private static void calculateSliceOffsetsAndSizes(final Container c) {
        if (c.slices.length == 0) return;
        for (int i = 0; i < c.slices.length - 1; i++) {
            final Slice s = c.slices[i];
            s.offset = c.landmarks[i];
            s.size = c.landmarks[i + 1] - s.offset;
            s.containerOffset = c.offset;
            s.index = i ;
        }
        final Slice lastSlice = c.slices[c.slices.length - 1];
        lastSlice.offset = c.landmarks[c.landmarks.length - 1];
        lastSlice.size = c.containerByteSize - lastSlice.offset;
        lastSlice.containerOffset = c.offset;
        lastSlice.index = c.slices.length - 1;
    }

    /**
     * Writes a {@link Container} header information to a {@link OutputStream}.
     *
     * @param major     the CRAM version to assume
     * @param container the container holding the header to write
     * @param os        the stream to write to
     * @return the number of bytes written
     * @throws IOException as per java IO contract
     */
    public static int writeContainerHeader(final int major, final Container container, final OutputStream os) throws IOException {
        return new ContainerHeaderIO().writeContainerHeader(major, container, os);
    }

    /**
     * Writes a complete {@link Container} with it's header to a {@link OutputStream}. The method is aware of file header containers and is
     * suitable for general purpose use: basically any container is allowed.
     *
     * @param version   the CRAM version to assume
     * @param container the container to write
     * @param os        the stream to write to
     * @return the number of bytes written out
     * @throws IOException as per java IO contract
     */
    public static int writeContainer(final Version version, final Container container, final OutputStream os) throws IOException {
        {
            if (container.blocks != null && container.blocks.length > 0) {

                final Block firstBlock = container.blocks[0];
                final boolean isFileHeaderContainer = firstBlock.getContentType() == BlockContentType.FILE_HEADER;
                if (isFileHeaderContainer) {
                    final ExposedByteArrayOutputStream baos = new ExposedByteArrayOutputStream();
                    firstBlock.write(version.major, baos);
                    container.containerByteSize = baos.size();

                    final int containerHeaderByteSize = new ContainerHeaderIO().writeContainerHeader(version.major, container, os);
                    os.write(baos.getBuffer(), 0, baos.size());
                    return containerHeaderByteSize + baos.size();
                }
            }
        }

        final long time1 = System.nanoTime();
        final ExposedByteArrayOutputStream baos = new ExposedByteArrayOutputStream();

        final Block block = new Block();
        block.setContentType(BlockContentType.COMPRESSION_HEADER);
        block.setContentId(0);
        block.setMethod(BlockCompressionMethod.RAW);
        final byte[] bytes;
        try {
            bytes = container.h.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeException("This should have never happened.");
        }
        block.setRawContent(bytes);
        block.write(version.major, baos);
        container.blockCount = 1;

        final List<Integer> landmarks = new ArrayList<Integer>();
        for (int i = 0; i < container.slices.length; i++) {
            final Slice s = container.slices[i];
            landmarks.add(baos.size());
            SliceIO.write(version.major, s, baos);
            container.blockCount++;
            container.blockCount++;
            if (s.embeddedRefBlock != null) container.blockCount++;
            container.blockCount += s.external.size();
        }
        container.landmarks = new int[landmarks.size()];
        for (int i = 0; i < container.landmarks.length; i++)
            container.landmarks[i] = landmarks.get(i);

        container.containerByteSize = baos.size();
        calculateSliceOffsetsAndSizes(container);

        int len = new ContainerHeaderIO().writeContainerHeader(version.major, container, os);
        os.write(baos.getBuffer(), 0, baos.size());
        len += baos.size();

        final long time2 = System.nanoTime();

        log.debug("CONTAINER WRITTEN: " + container.toString());
        container.writeTime = time2 - time1;

        return len;
    }

    /**
     * Calculates the byte size of a container based on the CRAM version.
     *
     * @param version   the CRAM version to assume
     * @param container the container to be weighted
     * @return the total number of bytes the container would occupy if written out
     */
    public static long getByteSize(final Version version, final Container container) {
        final CountingOutputStream cos = new CountingOutputStream(new OutputStream() {
            @Override
            public void write(final int b) throws IOException {
            }
        });

        try {
            writeContainer(version, container, cos);
            cos.close();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        return cos.getBytesWritten();
    }
}
