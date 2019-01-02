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
package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.digest.ContentDigests;
import htsjdk.samtools.cram.encoding.writer.CramRecordWriter;
import htsjdk.samtools.cram.io.DefaultBitOutputStream;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.block.ExternalBlock;
import htsjdk.samtools.cram.structure.slice.IndexableSlice;
import htsjdk.samtools.cram.structure.slice.SliceHeader;
import htsjdk.samtools.cram.structure.slice.Slice;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.cram.structure.block.Block;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContainerFactory {
    private final SAMFileHeader samFileHeader;
    private int recordsPerSlice = 10000;
    private boolean preserveReadNames = true;
    private long globalRecordCounter = 0;

    public ContainerFactory(final SAMFileHeader samFileHeader, final int recordsPerSlice) {
        this.samFileHeader = samFileHeader;
        this.recordsPerSlice = recordsPerSlice;
    }

    public Container buildContainer(final List<CramCompressionRecord> records,
                                    final byte[] refBases,
                                    final long offset) {

        // sets header APDelta
        final boolean coordinateSorted = samFileHeader.getSortOrder() == SAMFileHeader.SortOrder.coordinate;
        final CompressionHeader header = new CompressionHeaderFactory().build(records, null, coordinateSorted);

        header.readNamesIncluded = preserveReadNames;

        final Container container = new Container();
        container.header = header;
        container.nofRecords = records.size();
        container.globalRecordCounter = globalRecordCounter;
        container.bases = 0;
        container.blockCount = 0;
        container.offset = offset;

        final List<IndexableSlice> slices = new ArrayList<>();

        // write to a stream to determine byte sizes for Slice BAI Metadata
        try (final ByteArrayOutputStream osForIndexMetadata = new ByteArrayOutputStream()) {
            final Version version = CramVersions.CRAM_v3;
            container.header.write(version, osForIndexMetadata);

            // TODO: is this copy necessary?
            long globalRecordCounterCopy = container.globalRecordCounter;
            int sliceIndex = 0;
            for (int i = 0; i < records.size(); i += recordsPerSlice) {
                final List<CramCompressionRecord> sliceRecords = records.subList(i,
                        Math.min(records.size(), i + recordsPerSlice));
                final Slice slice = buildSlice(sliceRecords, header, globalRecordCounterCopy, refBases);
                globalRecordCounterCopy += sliceRecords.size();

                // TODO for Container refactoring:
                // these are also the container.landmarks so let's centralize their construction here

                final int sliceByteOffset = osForIndexMetadata.size();
                slice.write(version.major, osForIndexMetadata);
                final int sliceByteSize = osForIndexMetadata.size() - sliceByteOffset;

                slices.add(slice.withIndexingMetadata(sliceByteOffset, sliceByteSize, sliceIndex++));

                container.bases += sliceRecords.stream().map(r -> r.readLength).reduce(Integer::sum).orElse(0);

                // assuming one sequence per container max:
                if (container.sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX && !slice.hasNoReference())
                    container.sequenceId = slice.getSequenceId();
            }
        }
        catch (final IOException e) {
            throw new RuntimeIOException(e);
        }

        container.slices = slices.toArray(new IndexableSlice[0]);
        calculateAlignmentBoundaries(container);

        globalRecordCounter += records.size();

        return container;
    }

    private static void calculateAlignmentBoundaries(final Container container) {
        int start = Integer.MAX_VALUE;
        int end = Integer.MIN_VALUE;
        for (final Slice slice : container.slices) {
            if (!slice.hasNoReference()) {
                start = Math.min(start, slice.getAlignmentStart());
                end = Math.max(end, slice.getAlignmentStart() + slice.getAlignmentSpan());
            }
        }

        if (start < Integer.MAX_VALUE) {
            container.alignmentStart = start;
            container.alignmentSpan = end - start;
        }
    }

    private static Slice buildSlice(final List<CramCompressionRecord> records,
                                    final CompressionHeader compressionHeader,
                                    final long globalRecordCounter,
                                    final byte[] refBases) {

        // @formatter:off
        /*
         * 1) Decide if the slice is single ref, unmapped or multi reference.
         * 2) Detect alignment boundaries for the slice if not multi reference.
         */
        // @formatter:on

        int sequenceId = records.get(0).sequenceId;
        int alignmentStart = Integer.MAX_VALUE;
        int alignmentEnd = Integer.MIN_VALUE;

        final ContentDigests hasher = ContentDigests.create(ContentDigests.ALL);
        for (final CramCompressionRecord record : records) {
            hasher.add(record);
            if (sequenceId == SliceHeader.REFERENCE_INDEX_MULTI) continue;

            if (sequenceId != record.sequenceId) {
                sequenceId = SliceHeader.REFERENCE_INDEX_MULTI;
            } else if (record.alignmentStart != SAMRecord.NO_ALIGNMENT_START) {
                alignmentStart = Math.min(record.alignmentStart, alignmentStart);
                alignmentEnd = Math.max(record.getAlignmentEnd(), alignmentEnd);
            }
        }

        int alignmentSpan;
        if (sequenceId == SliceHeader.REFERENCE_INDEX_MULTI || alignmentStart == Integer.MAX_VALUE) {
            alignmentStart = SliceHeader.NO_ALIGNMENT_START;
            alignmentSpan = SliceHeader.NO_ALIGNMENT_SPAN;
        } else {
            alignmentSpan = alignmentEnd - alignmentStart + 1;
        }

        final Map<Integer, ByteArrayOutputStream> externalStreamMap = new HashMap<>();
        for (final int id : compressionHeader.externalIds) {
            externalStreamMap.put(id, new ByteArrayOutputStream());
        }

        Block coreBlock;
        try (final ByteArrayOutputStream bitBAOS = new ByteArrayOutputStream();
             final DefaultBitOutputStream bitOutputStream = new DefaultBitOutputStream(bitBAOS)) {

            final CramRecordWriter writer = new CramRecordWriter(bitOutputStream, externalStreamMap, compressionHeader, sequenceId);
            writer.writeCramCompressionRecords(records, alignmentStart);
            bitOutputStream.close();
            coreBlock = Block.createRawCoreDataBlock(bitBAOS.toByteArray());
        }
        catch (final IOException e) {
            throw new RuntimeIOException(e);
        }

        // core block + externals
        final int dataBlockCount = 1 + externalStreamMap.size();
        final int[] contentIDs = externalStreamMap.keySet().stream().mapToInt(Integer::intValue).toArray();
        final int embeddedRefBlockContentId = SliceHeader.NO_EMBEDDED_REFERENCE;
        final byte[] refMD5 = SliceHeader.calculateRefMD5(refBases, sequenceId, alignmentStart, alignmentSpan, globalRecordCounter);

        final SliceHeader sliceHeader = new SliceHeader(sequenceId, alignmentStart, alignmentSpan, records.size(), globalRecordCounter,
            dataBlockCount, contentIDs, embeddedRefBlockContentId, refMD5, hasher.getAsTags());

        return new Slice(sliceHeader, coreBlock, buildBlocksFromStreams(compressionHeader.externalCompressors, externalStreamMap));
    }

    private static Map<Integer, Block> buildBlocksFromStreams(final Map<Integer, ExternalCompressor> compressors, final Map<Integer, ByteArrayOutputStream> externalDataBlockMap) {
        final Map<Integer, Block> ext = new HashMap<>();
        for (final Integer contentId : externalDataBlockMap.keySet()) {
            // remove after https://github.com/samtools/htsjdk/issues/1232
            if (contentId == Block.NO_CONTENT_ID) {
                throw new CRAMException("Valid Content ID required.  Given: " + contentId);
            }

            final ExternalCompressor compressor = compressors.get(contentId);
            final byte[] rawContent = externalDataBlockMap.get(contentId).toByteArray();
            final ExternalBlock externalBlock = new ExternalBlock(compressor.getMethod(), contentId,
                    compressor.compress(rawContent), rawContent.length);

            ext.put(contentId, externalBlock);
        }
        return ext;
    }

    public boolean isPreserveReadNames() {
        return preserveReadNames;
    }

    public void setPreserveReadNames(final boolean preserveReadNames) {
        this.preserveReadNames = preserveReadNames;
    }
}
