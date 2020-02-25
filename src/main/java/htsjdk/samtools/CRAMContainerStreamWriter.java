package htsjdk.samtools;

import htsjdk.samtools.cram.build.ContainerFactory;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Class for writing SAMRecords into a series of CRAM containers on an output stream, with an optional index.
 */
public class CRAMContainerStreamWriter {
    private final OutputStream outputStream;
    private final String outputStreamIdentifier;
    private final SAMFileHeader samFileHeader;
    private final ContainerFactory containerFactory;
    private final CRAMIndexer cramIndexer;

    private long streamOffset = 0;

    /**
     * Create a CRAMContainerStreamWriter for writing SAM records into a series of CRAM
     * containers on output stream, with an optional index.
     *
     * @param recordOutputStream where to write the CRAM stream.
     * @param indexOutputStream where to write the output index. Can be null if no index is required.
     * @param source reference cramReferenceSource
     * @param samFileHeader {@link SAMFileHeader} to be used. Sort order is determined by the sortOrder property of this arg.
     * @param outputIdentifier used for display in error message display
     */
    public CRAMContainerStreamWriter(
            final OutputStream recordOutputStream,
            final OutputStream indexOutputStream,
            final CRAMReferenceSource source,
            final SAMFileHeader samFileHeader,
            final String outputIdentifier) {
        this(recordOutputStream,
                source,
                samFileHeader,
                outputIdentifier,
                indexOutputStream == null ?
                        null :
                        new CRAMBAIIndexer(indexOutputStream, samFileHeader)); // default to BAI index
    }

    /**
     * Create a CRAMContainerStreamWriter for writing SAM records into a series of CRAM
     * containers on output stream, with an optional index.
     *
     * @param outputStream where to write the CRAM stream.
     * @param source reference cramReferenceSource
     * @param samFileHeader {@link SAMFileHeader} to be used. Sort order is determined by the sortOrder property of this arg.
     * @param outputIdentifier used for display in error message display
     * @param indexer CRAM indexer. Can be null if no index is required.
     */
    public CRAMContainerStreamWriter(
            final OutputStream outputStream,
            final CRAMReferenceSource source,
            final SAMFileHeader samFileHeader,
            final String outputIdentifier,
            final CRAMIndexer indexer) {
        this(new CRAMEncodingStrategy(), source, samFileHeader, outputStream, indexer, outputIdentifier);
    }

    /**
     * Create a CRAMContainerStreamWriter for writing SAM records into a series of CRAM
     * containers on output stream, with an optional index.
     *
     * @param encodingStrategy encoding strategy values
     * @param referenceSource reference cramReferenceSource
     * @param samFileHeader {@link SAMFileHeader} to be used. Sort order is determined by the sortOrder property of this arg.
     * @param outputStream where to write the CRAM stream.
     * @param indexer CRAM indexer. Can be null if no index is required.
     * @param outputIdentifier informational string included in error reporting
     */
    public CRAMContainerStreamWriter(
            final CRAMEncodingStrategy encodingStrategy,
            final CRAMReferenceSource referenceSource,
            final SAMFileHeader samFileHeader,
            final OutputStream outputStream,
            final CRAMIndexer indexer,
            final String outputIdentifier) {
        this.samFileHeader = samFileHeader;
        this.outputStream = outputStream;
        this.cramIndexer = indexer;
        this.outputStreamIdentifier = outputIdentifier;
        this.containerFactory = new ContainerFactory(samFileHeader, encodingStrategy, referenceSource);
    }

    /**
     * Accumulate alignment records until we meet the threshold to flush a container.
     * @param alignment must not be null
     */
    public void writeAlignment(final SAMRecord alignment) {
        final Container container = containerFactory.getNextContainer(alignment, streamOffset);
        if (container != null) {
            writeContainer(container);
        }
    }

    /**
     * Write a CRAM file header and the previously provided SAM header to the stream.
     */
    // TODO: retained for backward compatibility for disq in order to run GATK tests (remove before merging this branch)
    public void writeHeader(final SAMFileHeader requestedSAMFileHeader) {
        final CramHeader cramHeader = new CramHeader(CramVersions.DEFAULT_CRAM_VERSION, outputStreamIdentifier);
        streamOffset = CramIO.writeCramHeader(cramHeader, outputStream);
        streamOffset += Container.writeSAMFileHeaderContainer(cramHeader.getCRAMVersion(), requestedSAMFileHeader, outputStream);
    }

    /**
     * Write a CRAM file header and the previously provided SAM header to the stream.
     */
    public void writeHeader() {
        writeHeader(samFileHeader);
    }

    /**
     * Finish writing to the stream. Flushes the record cache and optionally emits an EOF container.
     * @param writeEOFContainer true if an EOF container should be written. Only use false if writing a CRAM file
     *                          fragment which will later be aggregated into a complete CRAM file.
     */
    public void finish(final boolean writeEOFContainer) {
        try {
            final Container container = containerFactory.getFinalContainer(streamOffset);
            if (container != null) {
                writeContainer(container);
            }
            if (writeEOFContainer) {
                CramIO.writeCramEOF(CramVersions.DEFAULT_CRAM_VERSION, outputStream);
            }
            outputStream.flush();
            if (cramIndexer != null) {
                cramIndexer.finish();
            }
            outputStream.close();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    protected void writeContainer(final Container container) {
        streamOffset += container.write(CramVersions.DEFAULT_CRAM_VERSION, outputStream);
        if (cramIndexer != null) {
            // using silent validation here because the reads have been through validation already or
            // they have been generated somehow through the htsjdk
            cramIndexer.processContainer(container,  ValidationStringency.SILENT);
        }
    }

}
