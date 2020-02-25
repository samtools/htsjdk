/**
 * ****************************************************************************
 * Copyright 2013 EMBL-EBI
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.*;
import htsjdk.samtools.cram.BAIEntry;
import htsjdk.samtools.cram.CRAIEntry;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.build.CRAMReferenceRegion;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.util.BufferedLineReader;
import htsjdk.samtools.util.LineReader;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Notes: Container will construct a container out of as many CRAMCompressionRecords as it is handed, respecting only
 * the maximum number of slices. The policy around how to break up lists of records into containers is enforced
 * by ContainerFactory.
 */
public class Container {
    private final ContainerHeader containerHeader;
    private final CompressionHeader compressionHeader;
    private final List<Slice> slices;

    // container's byte offset from the start of the containing stream, used for indexing
    private final long containerByteOffset;

    /**
     * Create a Container with a {@link ReferenceContext} derived from its {@link Slice}s.
     *
     * A Single Reference Container contains only Single Reference Slices mapped to the same reference.
     * - set the Container's ReferenceContext to be the same as those slices
     * - set the Container's Alignment Start and Span to cover all slices
     *
     * A Multiple Reference Container contains only Multiple Reference Slices.
     * - set the Container's ReferenceContext to MULTIPLE_REFERENCE_CONTEXT
     * - unset the Container's Alignment Start and Span
     *
     * An Unmapped Container contains only Unmapped Slices.
     * - set the Container's ReferenceContext to UNMAPPED_UNPLACED_CONTEXT
     * - unset the Container's Alignment Start and Span
     *
     * Any other combination is invalid.
     *
     * @param compressionHeader the CRAM {@link CompressionHeader} to use for the Container
     * @param containerSlices the {@link Slice}s for the Container
     * @param containerByteOffset the Container's byte offset from the start of the stream
     * @param globalRecordCounter the global record count for the first record in this container
     * @throws CRAMException for invalid Container states
     */
    public Container(
            final CompressionHeader compressionHeader,
            final List<Slice> containerSlices,
            final long containerByteOffset,
            final long globalRecordCounter) {

        this.compressionHeader = compressionHeader;
        this.slices = containerSlices;
        this.containerByteOffset = containerByteOffset;

        final ReferenceContext commonRefContext = getDerivedReferenceContextFromSlices(slices);
        final AlignmentContext alignmentContext = getDerivedAlignmentContext(commonRefContext);

        int baseCount = 0;
        int blockCount = 0;
        int recordCount = 0;
        for (final Slice slice : slices) {
            recordCount += slice.getNumberOfRecords();
            blockCount += slice.getNumberOfBlocks();
            baseCount += slice.getBaseCount();
        }

        this.containerHeader = new ContainerHeader(
                alignmentContext,
                blockCount,
                recordCount,
                globalRecordCounter,
                baseCount);

        checkSliceReferenceContexts(commonRefContext.getReferenceContextID());
    }

    /**
     * Create a container for use by CramContainerHeaderIterator, which is only used to
     * find the offsets within a CRAM stream where containers start.
     *
     * @param containerHeader the container header for the container
     * @param containerByteOffset the byte offset of this container in the containing stream
     */
    public Container(final ContainerHeader containerHeader, final long containerByteOffset) {
        this.containerHeader = containerHeader;
        this.containerByteOffset = containerByteOffset;
        compressionHeader = null;
        slices = Collections.emptyList();
    }

    /**
     * Read a Container from a CRAM stream. This reads the container header and all slice blocks, but does
     * not resolve the blocks into CRAMRecords, since we don't want to do that until its necessary (and it
     * might not be if, for example, we're indexing the container).
     *
     * @param cramVersion CRAM version of the input stream
     * @param inputStream input stream to read
     * @param containerByteOffset byte offset within the stream of this container
     */
    public Container(final CRAMVersion cramVersion, final InputStream inputStream, final long containerByteOffset) {
        containerHeader = new ContainerHeader(cramVersion, inputStream);
        if (containerHeader.isEOF()) {
            compressionHeader = null;
            slices = Collections.EMPTY_LIST;
            this.containerByteOffset = containerByteOffset;

            // Read-through and consume the remainder of the EOF container.
            try {
                final byte[] eofBytes = new byte[containerHeader.getContainerBlocksByteSize()];
                inputStream.read(eofBytes, 0, containerHeader.getContainerBlocksByteSize());
            } catch (IOException e) {
                throw new RuntimeIOException("Malformed CRAM EOF block", e);
            }
            return;
        }

        this.containerByteOffset = containerByteOffset;
        compressionHeader = new CompressionHeader(cramVersion, inputStream);

        this.slices = new ArrayList<>();
        for (int sliceCounter = 0; sliceCounter < containerHeader.getLandmarks().size(); sliceCounter++) {
            final Slice slice = new Slice(
                    cramVersion,
                    compressionHeader,
                    inputStream,
                    containerByteOffset);
            slices.add(slice);
        }

        // sets index, byteOffset and byte size for each slice...
        distributeIndexingParametersToSlices();
        checkSliceReferenceContexts(getAlignmentContext().getReferenceContext().getReferenceContextID());
    }

    /**
     * Writes a complete {@link Container} with it's header to a {@link OutputStream}.
     *
     * @param cramVersion   the CRAM version to assume
     * @param outputStream  the stream to write to
     * @return the number of bytes written out
     */
    public int write(final CRAMVersion cramVersion, final OutputStream outputStream) {

        // The first thing that needs to be written to the final output stream is the Container header,
        // but that contains the size of the compressed header, as well as the landmarks, which are the
        // offsets of the start of slices, and these are not known until the compressed slices blocks are
        // written out. So we first write the
        try (final ByteArrayOutputStream tempOutputStream = new ByteArrayOutputStream()) {

            // Before we can write out the container header, we need to update it with the length of the
            // compression header that follows, as well as with the landmark (slice offsets), so write the
            // compression header out to a temporary stream first to get it's length.
            getCompressionHeader().write(cramVersion, tempOutputStream);

            // ...then write out the slice blocks, computing the landmarks along the way
            final List<Integer> landmarks = new ArrayList<>();
            for (final Slice slice : getSlices()) {
                // landmark 0 = byte length of the compression header
                // landmarks after 0 = byte length of the compression header plus all slices before this one
                landmarks.add(tempOutputStream.size());
                slice.write(cramVersion, tempOutputStream);
            }
            getContainerHeader().setLandmarks(landmarks);

            // compression header plus all slices, if any (EOF Containers do not; File Header Containers are handled above)
            getContainerHeader().setContainerBlocksByteSize(tempOutputStream.size());

            // Slices require the Container's landmarks and containerBlocksByteSize in case we're indexing
            distributeIndexingParametersToSlices();

            // ...then write the container header
            final int containerHeaderLength = getContainerHeader().write(cramVersion, outputStream);

            // .. and finally, write the entire stream to the actual output stream, now that we know how big it is
            tempOutputStream.writeTo(outputStream);
            return containerHeaderLength + getContainerHeader().getContainerBlocksByteSize();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Reads the special container that contains the SAMFileHeader from a CRAM stream, and returns just
     * the SAMFileHeader (we don't want to hand out the container since its not a real container in that
     * it has no compression header block, slices, etc).
     *
     * @param cramVersion CRAM version being read
     * @param inputStream stream from which to read the header container
     * @param id id from the cram header, for error reporting
     * @return the {@link SAMFileHeader} for this CRAM stream
     */
    public static SAMFileHeader readSAMFileHeaderContainer(final CRAMVersion cramVersion,
                                                           final InputStream inputStream,
                                                           final String id) {
        final ContainerHeader containerHeader = new ContainerHeader(cramVersion, inputStream);
        final Block block;
        if (cramVersion.compatibleWith(CramVersions.CRAM_v3)) {
            final byte[] bytes = new byte[containerHeader.getContainerBlocksByteSize()];
            InputStreamUtils.readFully(inputStream, bytes, 0, bytes.length);
            final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            block = Block.read(cramVersion, bais);
            // ignore any remaining blocks that we may have consumed from this container (i.e., samtools adds a
            // second 10,000 byte (raw) block of 0s as expansion padding)
        }
        else {
            // The version 2.1 test files appear to have header containers that have a container block size that is
            // (2 or 4 bytes) shorter than the size of the actual embedded block containing the header. To compensate,
            // this code path relies on the block size value instead. It unclear where these files came from, or what
            // CRAM implementation created them, but the spec historically under-specified the structure and contents
            // of the special (header and eof containers). See https://github.com/samtools/hts-specs/issues/450
            // and https://github.com/samtools/htsjdk/issues/1452.
            block = Block.read(cramVersion, inputStream);
        }

        // Use a temporary, single-use compressor cache for this block, since the SAMFileHeader block
        // is prescribed by the spec to be gzipped only and thus not perf sensitive.
        try (final InputStream blockStream = new ByteArrayInputStream(block.getUncompressedContent(new CompressorCache()))) {
            final ByteBuffer buffer = ByteBuffer.allocate(4);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            for (int i = 0; i < 4; i++) {
                buffer.put((byte) blockStream.read());
            }

            buffer.flip();
            final int size = buffer.asIntBuffer().get();

            final DataInputStream dataInputStream = new DataInputStream(blockStream);
            final byte[] bytes = new byte[size];
            dataInputStream.readFully(bytes);
            final SAMTextHeaderCodec codec = new SAMTextHeaderCodec();
            try (final InputStream byteStream = new ByteArrayInputStream(bytes);
                 final LineReader lineReader = new BufferedLineReader(byteStream)) {
                return codec.decode(lineReader, id);
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Write a SAMFileHeader container to a CRAM stream.
     * @param cramVersion CRAM version being written.
     * @param samFileHeader SAMFileHeader to write
     * @param os stream to which the header container should be written
     * @return the number of bytes written to the stream
     */
    public static long writeSAMFileHeaderContainer(final CRAMVersion cramVersion, final SAMFileHeader samFileHeader, final OutputStream os) {
        final byte[] samFileHeaderBytes = CramIO.samHeaderToByteArray(samFileHeader);
        // The spec recommends "reserving" 50% more space than is required by the header, buts not
        // clear how to do that if you compress the block. Samtools appears to add a second empty
        // block (of 10,0000 0's) to the first container, but its not clear if thats spec-conforming.
        final Block block = Block.createGZIPFileHeaderBlock(samFileHeaderBytes);

        try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            // need to write the block out first to determine how big it is
            block.write(cramVersion, byteArrayOutputStream);
            int containerBlocksByteSize = byteArrayOutputStream.size();
            final ContainerHeader containerHeader = ContainerHeader.makeSAMFileHeaderContainer(containerBlocksByteSize);
            final int containerHeaderByteSize = containerHeader.write(cramVersion, os);
            byteArrayOutputStream.writeTo(os);
            return containerHeaderByteSize + containerHeader.getContainerBlocksByteSize();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Get SAMRecords from all Slices in this container. This is a 3 step process:
     *
     * 1) deserialize the slice blocks and create a list of CRAMRecords
     * 2) Normalize the CRAMRecords
     * 3) Convert the normalized CRAMRecords into SAMRecords
     *
     * @param validationStringency validation stringency to use (when reading tags)
     * @param cramReferenceRegion reference region to use to restore bases
     * @param compressorCache compressor cache to use for decompressing streams
     * @param samFileHeader the SAMFileHeader for this CRAM stream (for resolving read groups)
     * @return the {@link SAMRecord}s from this container
     */
    public List<SAMRecord> getSAMRecords(
            final ValidationStringency validationStringency,
            final CRAMReferenceRegion cramReferenceRegion,
            final CompressorCache compressorCache,
            final SAMFileHeader samFileHeader) {
        final List<SAMRecord> samRecords = new ArrayList<>(getContainerHeader().getNumberOfRecords());
        for (final Slice slice : getSlices()) {
            final List<CRAMCompressionRecord> cramCompressionRecords = slice.deserializeCRAMRecords(compressorCache, validationStringency);
            // before we convert to SAMRecord, we need to normalize the CRAMCompressionRecord in each Slice
            slice.normalizeCRAMRecords(
                    cramCompressionRecords,
                    cramReferenceRegion);
            for (final CRAMCompressionRecord cramCompressionRecord : cramCompressionRecords) {
                final SAMRecord samRecord = cramCompressionRecord.toSAMRecord(samFileHeader);
                samRecord.setValidationStringency(validationStringency);
                samRecords.add(samRecord);
            }
        }
        return samRecords;
    }

    public ContainerHeader getContainerHeader() { return containerHeader; }
    public CompressionHeader getCompressionHeader() { return compressionHeader; }
    public AlignmentContext getAlignmentContext() { return containerHeader.getAlignmentContext(); }
    public long getContainerByteOffset() { return containerByteOffset; }
    public List<Slice> getSlices() { return slices; }
    public boolean isEOF() {
        return containerHeader.isEOF() && (getSlices() == null || getSlices().size() == 0);
    }

    /**
     * Retrieve the list of CRAI Index entries corresponding to this Container
     * @return the list of CRAI Index entries
     */
    public List<CRAIEntry> getCRAIEntries(final CompressorCache compressorCache) {
        if (isEOF()) {
            return Collections.emptyList();
        }

        return getSlices().stream()
                .map(s -> s.getCRAIEntries(compressorCache))
                .flatMap(List::stream)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Retrieve the list of BAIEntry Index entries corresponding to this Container
     * @return the list of BAIEntry Index entries
     */
    public List<BAIEntry> getBAIEntries(final CompressorCache compressorCache) {
        if (isEOF()) {
            return Collections.emptyList();
        }

        return getSlices().stream()
                .map(s -> s.getBAIEntries(compressorCache))
                .flatMap(List::stream)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Populate the indexing parameters of this Container's slices
     *
     * Requires: valid landmarks and containerBlocksByteSize
     *
     * @throws CRAMException when the Container is in an invalid state
     */
    private void distributeIndexingParametersToSlices() {
        final int lastSliceIndex = slices.size() - 1;
        for (int i = 0; i < lastSliceIndex; i++) {
            final Slice slice = slices.get(i);
            slice.setLandmarkIndex(i);
            slice.setByteOffsetOfSliceHeaderBlock(containerHeader.getLandmarks().get(i));
            slice.setByteSizeOfSliceBlocks(containerHeader.getLandmarks().get(i + 1) - slice.getByteOffsetOfSliceHeaderBlock());
        }

        // get the last slice in the list, and
        final Slice lastSlice = slices.get(lastSliceIndex);
        lastSlice.setLandmarkIndex(lastSliceIndex);
        lastSlice.setByteOffsetOfSliceHeaderBlock(containerHeader.getLandmarks().get(lastSliceIndex));
        lastSlice.setByteSizeOfSliceBlocks(containerHeader.getContainerBlocksByteSize() - lastSlice.getByteOffsetOfSliceHeaderBlock());
    }

    private void checkSliceReferenceContexts(final int actualReferenceContextID) {
        if (actualReferenceContextID == ReferenceContext.MULTIPLE_REFERENCE_ID) {
            for (final Slice slice : getSlices()) {
                if (slice.getAlignmentContext().getReferenceContext().getReferenceContextID() != ReferenceContext.MULTIPLE_REFERENCE_ID) {
                    throw new CRAMException(
                            String.format("Found slice with reference context (%d). Multi-reference container can only contain multi-ref slices.",
                                    slice.getAlignmentContext().getReferenceContext().getReferenceContextID()));
                }
            }
        }
    }

    // Determine the aggregate alignment context for this container by inspecting the constituent
    // slices.
    private final AlignmentContext getDerivedAlignmentContext(final ReferenceContext commonRefContext) {
        int alignmentStart = SAMRecord.NO_ALIGNMENT_START;
        int alignmentSpan = AlignmentContext.NO_ALIGNMENT_SPAN;

        if (commonRefContext.isMappedSingleRef()) {
            int start = Integer.MAX_VALUE;
            // end is start + span - 1.  We can do slightly easier math instead.
            int endPlusOne = Integer.MIN_VALUE;

            for (final Slice slice : slices) {
                final AlignmentContext alignmentContext = slice.getAlignmentContext();
                start = Math.min(start, alignmentContext.getAlignmentStart());
                endPlusOne = Math.max(endPlusOne, alignmentContext.getAlignmentStart() + alignmentContext.getAlignmentSpan());
            }
            alignmentStart = start;
            alignmentSpan = endPlusOne - start;
        } else if (commonRefContext.isUnmappedUnplaced()) {
            return AlignmentContext.UNMAPPED_UNPLACED_CONTEXT;
        } else if (commonRefContext.isMultiRef()) {
            return AlignmentContext.MULTIPLE_REFERENCE_CONTEXT;
        }

        // since we're creating this container, ensure that it has a valid alignment context
        AlignmentContext.validateAlignmentContext(true, commonRefContext, alignmentStart, alignmentSpan);
        return new AlignmentContext(commonRefContext, alignmentStart, alignmentSpan);
    }

    private static ReferenceContext getDerivedReferenceContextFromSlices(final List<Slice> containerSlices) {
        final Set<ReferenceContext> sliceRefContexts = containerSlices.stream()
                .map(s -> s.getAlignmentContext().getReferenceContext())
                .collect(Collectors.toSet());
        if (sliceRefContexts.isEmpty()) {
            throw new CRAMException("Cannot construct a container without any slices");
        }
        else if (sliceRefContexts.size() > 1) {
            return ReferenceContext.MULTIPLE_REFERENCE_CONTEXT;
        }

        return sliceRefContexts.iterator().next();
    }

    @Override
    public String toString() {
        return String.format("%s offset %d nSlices %d",
                containerHeader.toString(),
                getContainerByteOffset(),
                getSlices() == null ?
                        -1 :
                        getSlices().size());
    }

}
