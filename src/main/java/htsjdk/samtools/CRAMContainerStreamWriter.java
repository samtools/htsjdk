package htsjdk.samtools;

import htsjdk.samtools.cram.build.ContainerFactory;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.ref.CRAMLazyReferenceSource;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.util.SequenceUtil;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Class for writing SAMRecords into a series of CRAM containers on an output stream, with an optional index.
 */
public class CRAMContainerStreamWriter {
    private static final Log log = Log.getInstance(CRAMContainerStreamWriter.class);
    private static final int UPPER_CASE_BUFFER_SIZE = 64 * 1024;
    private static final int MAX_MISSING_CONTIGS_LISTED = 10;

    private final CRAMReferenceSource referenceSource;
    private final OutputStream outputStream;
    private final String outputStreamIdentifier;
    private final SAMFileHeader samFileHeader;
    private final ContainerFactory containerFactory;
    private final CRAMIndexer cramIndexer;
    private final CRAMVersion cramVersion;

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
        this(
                recordOutputStream,
                source,
                samFileHeader,
                outputIdentifier,
                indexOutputStream == null
                        ? null
                        : new CRAMBAIIndexer(indexOutputStream, samFileHeader)); // default to BAI index
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
     * @param encodingStrategy encoding strategy values (includes CRAM version)
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
        this.referenceSource = referenceSource;
        this.outputStream = outputStream;
        this.cramIndexer = indexer;
        this.outputStreamIdentifier = outputIdentifier;
        this.cramVersion = encodingStrategy.getCramVersion();
        this.containerFactory = new ContainerFactory(samFileHeader, encodingStrategy, referenceSource);
    }

    /**
     * Accumulate alignment records until we meet the threshold to flush a container.
     * @param alignment must not be null
     */
    public void writeAlignment(final SAMRecord alignment) {
        WritableText.requireInRecord(alignment, WritableText.Destination.CRAM_RECORD);
        final Container container = containerFactory.getNextContainer(alignment, streamOffset);
        if (container != null) {
            writeContainer(container);
        }
    }

    /**
     * Write a CRAM file header and the provided SAM header to the stream.
     * Retained for backward compatibility with external projects (disq, GATK).
     *
     * <p>Any {@code @SQ} line lacking an {@code M5} gets one, as the CRAM specification requires, taken from the
     * reference's sequence dictionary or computed from the reference bases. The header passed in is not modified.
     */
    public void writeHeader(final SAMFileHeader requestedSAMFileHeader) {
        final CramHeader cramHeader = new CramHeader(cramVersion, outputStreamIdentifier);
        streamOffset = CramIO.writeCramHeader(cramHeader, outputStream);
        streamOffset += Container.writeSAMFileHeaderContainer(
                cramHeader.getCRAMVersion(), withReferenceMD5s(requestedSAMFileHeader), outputStream);
    }

    /**
     * Returns a copy of {@code header} in which every {@code @SQ} line without an {@code M5} has one, or the
     * header itself when there is no reference to take them from. An {@code M5} comes from the reference's own
     * dictionary when that has the same contig name and length with an {@code M5}, and is otherwise the MD5 of
     * the reference bases. A contig missing from the reference is left without {@code M5}. Existing
     * {@code M5}s are kept unchecked.
     *
     * <p>Cost: reads each contig that lacks an {@code M5} the reference's dictionary cannot supply, once, when the
     * header is written (twice overall for a whole genome, as the reference source caches only weakly).
     */
    private SAMFileHeader withReferenceMD5s(final SAMFileHeader header) {
        if (referenceSource == null || referenceSource instanceof CRAMLazyReferenceSource) {
            return header;
        }
        final SAMFileHeader copy = header.clone();
        final SAMSequenceDictionary referenceDictionary = referenceSource.getSequenceDictionary();
        final List<String> missingContigs = new ArrayList<>();
        int resolvedContigs = 0;
        for (final SAMSequenceRecord sequence : copy.getSequenceDictionary().getSequences()) {
            if (sequence.getAttribute(SAMSequenceRecord.MD5_TAG) != null) {
                continue;
            }
            final SAMSequenceRecord referenceSequence =
                    referenceDictionary == null ? null : referenceDictionary.getSequence(sequence.getSequenceName());
            final String dictionaryMD5 =
                    referenceSequence != null && referenceSequence.getSequenceLength() == sequence.getSequenceLength()
                            ? referenceSequence.getAttribute(SAMSequenceRecord.MD5_TAG)
                            : null;
            if (dictionaryMD5 != null) {
                sequence.setMd5(dictionaryMD5);
                resolvedContigs++;
                continue;
            }
            final byte[] bases = referenceSource.getReferenceBases(sequence, true);
            if (bases == null) {
                missingContigs.add(sequence.getSequenceName());
            } else {
                sequence.setMd5(md5OfUpperCaseBases(bases));
                resolvedContigs++;
            }
        }

        if (!missingContigs.isEmpty()) {
            if (resolvedContigs == 0) {
                log.warn("No reference bases available; @SQ lines are written without M5.");
            } else {
                final int listed = Math.min(missingContigs.size(), MAX_MISSING_CONTIGS_LISTED);
                final String names = String.join(", ", missingContigs.subList(0, listed));
                final int more = missingContigs.size() - listed;
                log.warn(
                        "No reference bases available for ",
                        names,
                        more > 0 ? " and " + more + " more" : "",
                        "; their @SQ lines are written without M5, so readers will need the reference supplied explicitly.");
            }
        }
        return copy;
    }

    /**
     * MD5 of the bases in upper case, as 32 lower-case hex digits. Upper-cases through a small buffer rather than in
     * place, because the reference source may cache the array.
     */
    private static String md5OfUpperCaseBases(final byte[] bases) {
        final MessageDigest digest = newMD5Digest();
        final byte[] buffer = new byte[UPPER_CASE_BUFFER_SIZE];
        // Advance by the chunk just hashed, so start never passes bases.length (and can't overflow near the largest
        // array)
        for (int start = 0, length; start < bases.length; start += length) {
            length = Math.min(buffer.length, bases.length - start);
            for (int i = 0; i < length; i++) {
                final byte base = bases[start + i];
                buffer[i] = base >= 'a' && base <= 'z' ? (byte) (base - ('a' - 'A')) : base;
            }
            digest.update(buffer, 0, length);
        }
        return SequenceUtil.md5DigestToString(digest.digest());
    }

    private static MessageDigest newMD5Digest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Write a CRAM file header and the previously provided SAM header to the stream.
     */
    public void writeHeader() {
        writeHeader(samFileHeader);
    }

    /**
     * Finish writing to the stream. Flushes the record cache, optionally emits an EOF container, and then
     * closes the underlying output stream.
     *
     * <p>Note that this closes a stream that was supplied by the caller. Callers that own the lifecycle of
     * the stream themselves should use {@link #finish(boolean, boolean)} with {@code closeStream} set to
     * false, and close the stream on their own terms.
     *
     * @param writeEOFContainer true if an EOF container should be written. Only use false if writing a CRAM file
     *                          fragment which will later be aggregated into a complete CRAM file.
     */
    public void finish(final boolean writeEOFContainer) {
        finish(writeEOFContainer, true);
    }

    /**
     * Finish writing to the stream. Flushes the record cache and optionally emits an EOF container.
     *
     * @param writeEOFContainer true if an EOF container should be written. Only use false if writing a CRAM file
     *                          fragment which will later be aggregated into a complete CRAM file.
     * @param closeStream true if the underlying output stream should be closed once writing is complete. Pass
     *                    false when the caller created the stream and is responsible for closing it; the stream
     *                    is always flushed regardless.
     */
    public void finish(final boolean writeEOFContainer, final boolean closeStream) {
        try {
            final Container container = containerFactory.getFinalContainer(streamOffset);
            if (container != null) {
                writeContainer(container);
            }
            if (writeEOFContainer) {
                CramIO.writeCramEOF(cramVersion, outputStream);
            }
            outputStream.flush();
            if (cramIndexer != null) {
                cramIndexer.finish();
            }
            if (closeStream) {
                outputStream.close();
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    protected void writeContainer(final Container container) {
        streamOffset += container.write(cramVersion, outputStream);
        if (cramIndexer != null) {
            // using silent validation here because the reads have been through validation already or
            // they have been generated somehow through the htsjdk
            cramIndexer.processContainer(container, ValidationStringency.SILENT);
        }
    }
}
