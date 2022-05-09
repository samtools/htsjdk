package htsjdk.samtools;

import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Rewrite a series of containers to a new stream. The CRAM header and SAMFileHeader containers are automatically
 * written to the stream when this class is instantiated. An EOF container is automatically written when
 * {@link #finish()} is called.
 */
public class CRAMContainerStreamRewriter {
    private final OutputStream outputStream;
    private final String outputStreamIdentifier;
    private final CramHeader cramHeader;
    private final SAMFileHeader samFileHeader;
    private final CRAMIndexer cramIndexer;

    private long streamOffset = 0L;
    private long recordCounter = 0L;

    /**
     * Create a CRAMContainerStreamRewriter for writing a series of CRAM containers into an output
     * stream, with an optional output index.
     *
     * @param outputStream where to write the CRAM stream.
     * @param samFileHeader {@link SAMFileHeader} to be used. Sort order is determined by the sortOrder property of this arg.
     * @param outputStreamIdentifier used for display in error message display
     * @param indexer CRAM indexer. Can be null if no index is required.
     */
    public CRAMContainerStreamRewriter(
            final OutputStream outputStream,
            final CramHeader cramHeader,
            final SAMFileHeader samFileHeader,
            final String outputStreamIdentifier,
            final CRAMIndexer indexer) {
        this.outputStream = outputStream;
        this.cramHeader = cramHeader;
        this.samFileHeader = samFileHeader;
        this.outputStreamIdentifier = outputStreamIdentifier;
        this.cramIndexer = indexer;

        streamOffset = CramIO.writeCramHeader(cramHeader, outputStream);
        streamOffset += Container.writeSAMFileHeaderContainer(cramHeader.getCRAMVersion(), samFileHeader, outputStream);
    }

    /**
     * Writes a container to a stream, updating the (stream-relative) global record counter and byte offsets.
     *
     * Since this method mutates the values in the container, the container is no longer valid in the context
     * of the stream from which it originated.
     *
     * @param container the container to emit to the stream. the container must conform to the version and sort
     *                  order specified in the CRAM header and SAM header provided to the constructor
     *                  {@link #CRAMContainerStreamRewriter(OutputStream, CramHeader, SAMFileHeader, String, CRAMIndexer)}.
     *                  All the containers serialized to a single stream using this method must have originated from the
     *                  same original context(/stream), obtained via {@link htsjdk.samtools.cram.build.CramContainerIterator}.
     */
     public void rewriteContainer(final Container container) {
         // update the container and slices with the correct global record counter and byte offsets
         // (required for indexing)
         container.relocateContainer(recordCounter, streamOffset);

         // re-serialize the entire container and slice(s), block by block
        streamOffset += container.write(cramHeader.getCRAMVersion(), outputStream);
        recordCounter += container.getContainerHeader().getNumberOfRecords();

        if (cramIndexer != null) {
            cramIndexer.processContainer(container,  ValidationStringency.SILENT);
        }
    }

    /**
     * Finish writing to the stream. Flushes the record cache and optionally emits an EOF container.
     */
    public void finish() {
        try {
            CramIO.writeCramEOF(cramHeader.getCRAMVersion(), outputStream);
            outputStream.flush();
            if (cramIndexer != null) {
                cramIndexer.finish();
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(String.format("IOException closing stream for %s", outputStreamIdentifier));
        }
    }

}
