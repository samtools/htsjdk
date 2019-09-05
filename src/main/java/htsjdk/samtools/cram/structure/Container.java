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

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMTextHeaderCodec;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.CRAIEntry;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
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

public class Container {
    private final ContainerHeader containerHeader;
    private final CompressionHeader compressionHeader;
    private final List<Slice> slices;

    // container's byte offset from the start of the containing stream, used for indexing
    private final long containerByteOffset;

     /**
      * Derive the container's {@link ReferenceContext} from its {@link Slice}s.
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
      * @param containerSlices the constituent Slices of the Container
      * @param compressionHeader the CRAM {@link CompressionHeader} to assign to the Container
      * @param containerByteOffset the Container's byte offset from the start of the stream
      * @throws CRAMException for invalid Container states
      * @return the initialized Container
      */
     // TODO: this is the case where we're writing a container from SAMRecords (CRAMContainerStreamWriter)
     // TODO: blockCount abd baseCount can be removed from this arg list ???
    public Container(
            final CompressionHeader compressionHeader,
            final List<Slice> containerSlices,
            final long containerByteOffset,
            final long globalRecordCounter,
            final int blockCount,
            final int baseCount) {
        final Set<ReferenceContext> sliceRefContexts = containerSlices.stream()
                .map(Slice::getReferenceContext)
                .collect(Collectors.toSet());
        if (sliceRefContexts.isEmpty()) {
            throw new CRAMException("Cannot construct a container without any slices");
        }
        else if (sliceRefContexts.size() > 1) {
            final String msg = String.format(
                    "Attempt to construct a container from slices with conflicting types or reference contexts: %s",
                    sliceRefContexts.stream()
                            .map(ReferenceContext::toString)
                            .collect(Collectors.joining(", ")));
            throw new CRAMException(msg);
        }

        final ReferenceContext commonRefContext = sliceRefContexts.iterator().next();

        int recordCount = 0;
        for (final Slice slice : containerSlices) {
            recordCount += slice.getNofRecords();
            slice.setContainerByteOffset(containerByteOffset);
        }

        int alignmentStart = Slice.NO_ALIGNMENT_START;
        int alignmentSpan = Slice.NO_ALIGNMENT_SPAN;

        if (commonRefContext.isMappedSingleRef()) {
            int start = Integer.MAX_VALUE;
            // end is start + span - 1.  We can do slightly easier math instead.
            int endPlusOne = Integer.MIN_VALUE;

            for (final Slice slice : containerSlices) {
                start = Math.min(start, slice.getAlignmentStart());
                endPlusOne = Math.max(endPlusOne, slice.getAlignmentStart() + slice.getAlignmentSpan());
            }
            alignmentStart = start;
            alignmentSpan = endPlusOne - start;
        }

        this.containerHeader = new ContainerHeader(
                commonRefContext,
                alignmentStart,
                alignmentSpan,
                globalRecordCounter,
                blockCount,
                recordCount,
                baseCount);
        this.compressionHeader = compressionHeader;
        this.slices = containerSlices;
        this.containerByteOffset = containerByteOffset;
    }

    //TODO: this is the degenerate case of the CramContainerHeaderIterator, which for disq really only
    //TODO: cares about the byte offset...
    public Container(final ContainerHeader containerHeader, final long containerByteOffset) {
        this.containerHeader = containerHeader;
        this.containerByteOffset = containerByteOffset;
        compressionHeader = null;
        slices = Collections.EMPTY_LIST;
    }

    //TODO: this is the case where we're reading a container from a stream
    public Container(final Version version, final InputStream inputStream, final long containerByteOffset) {
        containerHeader = ContainerHeader.readContainerHeader(version.major, inputStream);
        if (containerHeader.isEOF()) {
            compressionHeader = null;
            slices = Collections.EMPTY_LIST;
            this.containerByteOffset = containerByteOffset;
            return;
        }

        this.containerByteOffset = containerByteOffset;
        compressionHeader = new CompressionHeader(version.major, inputStream);

        this.slices = new ArrayList<>();
        for (int sliceCounter = 0; sliceCounter < containerHeader.getLandmarks().length; sliceCounter++) {
            final Slice slice = new Slice(version.major, compressionHeader, inputStream);
            slice.setContainerByteOffset(containerByteOffset);
            slices.add(slice);
        }

        distributeIndexingParametersToSlices();
    }

    /**
     * Writes a complete {@link Container} with it's header to a {@link OutputStream}.
     *
     * @param version   the CRAM version to assume
     * @param outputStream  the stream to write to
     * @return the number of bytes written out
     */
    public int writeContainer(final Version version, final OutputStream outputStream) {
        // use this BAOS for two purposes: writing out and counting bytes for landmarks/containerBlocksByteSize
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // write out the compression header...
        getCompressionHeader().write(version, byteArrayOutputStream);

        // TODO: ensure that the Container blockCount stays in sync with the
        // Slice's blockCount in slice.write()
        // 1 Compression Header Block

        // ...then write out the slice blocks....
        int blockCount = 1;
        final List<Integer> landmarks = new ArrayList<>();
        for (final Slice slice : getSlices()) {
            // landmark 0 = byte length of the compression header
            // landmarks after 0 = byte length of the compression header plus all slices before this one
            landmarks.add(byteArrayOutputStream.size());
            slice.write(version.major, byteArrayOutputStream);
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
        getContainerHeader().setLandmarks(landmarks.stream().mapToInt(Integer::intValue).toArray());
        // compression header plus all slices, if any (EOF Containers do not; File Header Containers are handled above)
        getContainerHeader().setContainerBlocksByteSize(byteArrayOutputStream.size());

        // Slices require the Container's landmarks and containerBlocksByteSize in case we're indexing
        distributeIndexingParametersToSlices();

        // ...then write the container header
        final int containerHeaderLength = getContainerHeader().writeContainerHeader(version.major, outputStream);

        // .. and finally, write the entire stream to the actual output stream, now that we know how big it is
        try {
            outputStream.write(byteArrayOutputStream.toByteArray(), 0, getContainerHeader().getContainerBlocksByteSize());
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }

        return containerHeaderLength + getContainerHeader().getContainerBlocksByteSize();
    }

    /**
     * Reads the special container that contains the SAMFileHeader from a CRAM stream, and return just
     * the SAMFileHeader (we don't want to hand out the container since its not really a container in that
     * while it has a container header, it has compression header block, no slices, etc).
     * @param version
     * @param inputStream
     * @param id
     * @return
     */
    public static SAMFileHeader getSAMFileHeader(final Version version,
                                                 final InputStream inputStream,
                                                 final String id) {
        //TODO: this needs to just read the header!
        final ContainerHeader containerHeader = ContainerHeader.readContainerHeader(version.major, inputStream);
        final Block block;
        {
            if (version.compatibleWith(CramVersions.CRAM_v3)) {
                final byte[] bytes = new byte[containerHeader.getContainerBlocksByteSize()];
                InputStreamUtils.readFully(inputStream, bytes, 0, bytes.length);
                block = Block.read(version.major, new ByteArrayInputStream(bytes));
                // ignore the rest of the container
            } else {
                /*
                 * pending issue: container.containerBlocksByteSize inputStream 2 bytes shorter
                 * than needed in the v21 test cram files.
                 */
                block = Block.read(version.major, inputStream);
            }
        }

        byte[] bytes;
        // SAMFileHeader block is prescribed by the spec to be gzip compressed
        //TODO: this compressor cache is bogus
        try (final InputStream blockStream = new ByteArrayInputStream(block.getUncompressedContent(new CompressorCache()))) {

            final ByteBuffer buffer = ByteBuffer.allocate(4);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            for (int i = 0; i < 4; i++) {
                buffer.put((byte) blockStream.read());
            }

            buffer.flip();
            final int size = buffer.asIntBuffer().get();

            final DataInputStream dataInputStream = new DataInputStream(blockStream);
            bytes = new byte[size];
            dataInputStream.readFully(bytes);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }

        final SAMTextHeaderCodec codec = new SAMTextHeaderCodec();

        try (final InputStream byteStream = new ByteArrayInputStream(bytes);
             final LineReader lineReader = new BufferedLineReader(byteStream)) {
            return codec.decode(lineReader, id);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    public static long writeSAMFileHeaderContainer(final int major, final SAMFileHeader samFileHeader, final OutputStream os) {
        final byte[] data = CramIO.toByteArray(samFileHeader);
        // The spec recommends "reserving" 50% more space than is required by the header.
        final int length = Math.max(1024, data.length + data.length / 2);
        final byte[] blockContent = new byte[length];
        System.arraycopy(data, 0, blockContent, 0, Math.min(data.length, length));
        final Block block = Block.createRawFileHeaderBlock(blockContent);

        try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            block.write(major, byteArrayOutputStream);
            int containerBlocksByteSize = byteArrayOutputStream.size();
            // TODO: make sure this container is initialized correctly/fully
            //container.blockCount = 1;
            //container.landmarks = new int[0];
            final ContainerHeader containerHeader = new ContainerHeader(
                    containerBlocksByteSize,
                    new ReferenceContext(0),
                    0,
                    0,
                    0,
                    0,
                    0,
                    1,
                    new int[]{},
                    0);
            final int containerHeaderByteSize = containerHeader.writeContainerHeader(major, os);
            os.write(byteArrayOutputStream.toByteArray(), 0, containerBlocksByteSize);
            return containerHeaderByteSize + containerHeader.getContainerBlocksByteSize();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    public ContainerHeader getContainerHeader() { return containerHeader; }
    public CompressionHeader getCompressionHeader() {
        return compressionHeader;
    }
    public ReferenceContext getReferenceContext() { return containerHeader.getReferenceContext(); }
    public long getContainerByteOffset() {
        return containerByteOffset;
    }
    public List<Slice> getSlices() { return slices; }

    public List<CRAMRecord> getCRAMRecords(final ValidationStringency validationStringency, final CompressorCache compressorCache) {
        if (isEOF()) {
            return Collections.emptyList();
        }

        final ArrayList<CRAMRecord> records = new ArrayList<>(getContainerHeader().getRecordCount());
        for (final Slice slice : getSlices()) {
            records.addAll(slice.getRecords(compressorCache, validationStringency));
        }
        return records;
    }

    /**
     * Populate the indexing parameters of this Container's slices
     *
     * Requires: valid landmarks and containerBlocksByteSize
     *
     * @throws CRAMException when the Container is in an invalid state
     */
    private void distributeIndexingParametersToSlices() {
        if (slices.size() == 0) {
            return;
        }

        if (containerHeader.getLandmarks() == null) {
            throw new CRAMException("Cannot set Slice indexing parameters if this Container does not have landmarks");
        }

        if (containerHeader.getLandmarks().length != slices.size()) {
            final String format = "This Container's landmark and slice counts do not match: %d landmarks and %d slices";
            throw new CRAMException(String.format(format, containerHeader.getLandmarks().length, slices.size()));
        }

        if (containerHeader.getContainerBlocksByteSize() == 0) {
            throw new CRAMException("Cannot set Slice indexing parameters if the byte size of this Container's blocks is unknown");
        }

        final int lastSliceIndex = slices.size() - 1;
        for (int i = 0; i < lastSliceIndex; i++) {
            final Slice slice = slices.get(i);
            slice.setIndex(i);
            slice.setByteOffsetFromCompressionHeaderStart(containerHeader.getLandmarks()[i]);
            slice.setByteSize(containerHeader.getLandmarks()[i + 1] - slice.getByteOffsetFromCompressionHeaderStart());
        }

        final Slice lastSlice = slices.get(lastSliceIndex);
        lastSlice.setIndex(lastSliceIndex);
        lastSlice.setByteOffsetFromCompressionHeaderStart(containerHeader.getLandmarks()[lastSliceIndex]);
        lastSlice.setByteSize(containerHeader.getContainerBlocksByteSize() - lastSlice.getByteOffsetFromCompressionHeaderStart());
    }

    /**
     * Retrieve the list of CRAI Index entries corresponding to this Container
     * @return the list of CRAI Index entries
     */
    public List<CRAIEntry> getCRAIEntries() {
        if (isEOF()) {
            return Collections.emptyList();
        }

        return getSlices().stream()
                .map(s -> s.getCRAIEntries(compressionHeader))
                .flatMap(List::stream)
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return String.format("%s slices=%d",
                containerHeader.toString(),
                getSlices() == null ?
                        -1 :
                        getSlices().size());
    }

    public boolean isEOF() {
        return containerHeader.isEOF() && (getSlices() == null || getSlices().size() == 0);
    }

    /**
     * Iterate through all of this container's {@link Slice}s to derive a map of reference sequence IDs
     * to {@link AlignmentSpan}s.  Used to create BAI Indexes.
     *
     * @param validationStringency stringency for validating records, passed to
     * {@link Slice#getMultiRefAlignmentSpans(ValidationStringency)}
     * @return the map of map of reference sequence IDs to AlignmentSpans.
     */
    public Map<ReferenceContext, AlignmentSpan> getSpans(final ValidationStringency validationStringency) {
        final Map<ReferenceContext, AlignmentSpan> containerSpanMap  = new HashMap<>();
        for (final Slice slice : getSlices()) {
            switch (slice.getReferenceContext().getType()) {
                case UNMAPPED_UNPLACED_TYPE:
                    containerSpanMap.put(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, AlignmentSpan.UNPLACED_SPAN);
                    break;
                case MULTIPLE_REFERENCE_TYPE:
                    final Map<ReferenceContext, AlignmentSpan> spans = slice.getMultiRefAlignmentSpans(validationStringency);
                    for (final Map.Entry<ReferenceContext, AlignmentSpan> entry : spans.entrySet()) {
                        containerSpanMap.merge(entry.getKey(), entry.getValue(), AlignmentSpan::combine);
                    }
                    break;
                default:
                    final AlignmentSpan alignmentSpan = new AlignmentSpan(slice.getAlignmentStart(), slice.getAlignmentSpan(), slice.getMappedReadsCount(), slice.getUnmappedReadsCount());
                    containerSpanMap.merge(slice.getReferenceContext(), alignmentSpan, AlignmentSpan::combine);
                    break;
            }
        }
        return containerSpanMap;
    }

}
