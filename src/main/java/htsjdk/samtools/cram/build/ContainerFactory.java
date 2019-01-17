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
import htsjdk.samtools.cram.digest.ContentDigests;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.encoding.writer.CramRecordWriter;
import htsjdk.samtools.cram.io.DefaultBitOutputStream;
import htsjdk.samtools.cram.structure.block.ExternalBlock;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.Slice;
import htsjdk.samtools.cram.structure.SubstitutionMatrix;
import htsjdk.samtools.util.RuntimeIOException;

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

    public Container buildContainer(final List<CramCompressionRecord> records) {
        return buildContainer(records, null);
    }

    Container buildContainer(final List<CramCompressionRecord> records,
                             final SubstitutionMatrix substitutionMatrix) {

        // sets header APDelta
        final boolean coordinateSorted = samFileHeader.getSortOrder() == SAMFileHeader.SortOrder.coordinate;
        final CompressionHeader header = new CompressionHeaderFactory().build(records, substitutionMatrix, coordinateSorted);

        header.readNamesIncluded = preserveReadNames;

        final List<Slice> slices = new ArrayList<Slice>();

        final Container container = new Container();
        container.header = header;
        container.nofRecords = records.size();
        container.globalRecordCounter = globalRecordCounter;
        container.bases = 0;
        container.blockCount = 0;

        long lastGlobalRecordCounter = container.globalRecordCounter;
        for (int i = 0; i < records.size(); i += recordsPerSlice) {
            final List<CramCompressionRecord> sliceRecords = records.subList(i,
                    Math.min(records.size(), i + recordsPerSlice));
            final Slice slice = buildSlice(sliceRecords, header);
            slice.globalRecordCounter = lastGlobalRecordCounter;
            lastGlobalRecordCounter += slice.nofRecords;
            container.bases += slice.bases;
            slices.add(slice);

            // assuming one sequence per container max:
            if (container.sequenceId == -1 && slice.sequenceId != -1)
                container.sequenceId = slice.sequenceId;
        }

        container.slices = slices.toArray(new Slice[slices.size()]);
        calculateAlignmentBoundaries(container);

        globalRecordCounter += records.size();
        return container;
    }

    private static void calculateAlignmentBoundaries(final Container container) {
        int start = Integer.MAX_VALUE;
        int end = Integer.MIN_VALUE;
        for (final Slice s : container.slices) {
            if (s.sequenceId != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                start = Math.min(start, s.alignmentStart);
                end = Math.max(end, s.alignmentStart + s.alignmentSpan);
            }
        }

        if (start < Integer.MAX_VALUE) {
            container.alignmentStart = start;
            container.alignmentSpan = end - start;
        }
    }

    private static Slice buildSlice(final List<CramCompressionRecord> records,
                                    final CompressionHeader header) {
        final Map<Integer, ByteArrayOutputStream> externalBlockMap = new HashMap<>();
        for (final int id : header.externalIds) {
            externalBlockMap.put(id, new ByteArrayOutputStream());
        }

        final Slice slice = new Slice();
        slice.nofRecords = records.size();

        int minAlStart = Integer.MAX_VALUE;
        int maxAlEnd = SAMRecord.NO_ALIGNMENT_START;
        {
            // @formatter:off
            /*
             * 1) Count slice bases.
			 * 2) Decide if the slice is single ref, unmapped or multi reference.
			 * 3) Detect alignment boundaries for the slice if not multi reference.
			 */
            // @formatter:on
            slice.sequenceId = records.get(0).sequenceId;
            final ContentDigests hasher = ContentDigests.create(ContentDigests.ALL);
            for (final CramCompressionRecord record : records) {
                slice.bases += record.readLength;
                hasher.add(record);
                if (slice.sequenceId == Slice.MULTI_REFERENCE) continue;

                if (slice.sequenceId != record.sequenceId) {
                    slice.sequenceId = Slice.MULTI_REFERENCE;
                } else if (record.alignmentStart != SAMRecord.NO_ALIGNMENT_START) {
                    minAlStart = Math.min(record.alignmentStart, minAlStart);
                    maxAlEnd = Math.max(record.getAlignmentEnd(), maxAlEnd);
                }
            }

            slice.sliceTags = hasher.getAsTags();
        }

        if (slice.sequenceId == Slice.MULTI_REFERENCE
                || minAlStart == Integer.MAX_VALUE) {
            slice.alignmentStart = Slice.NO_ALIGNMENT_START;
            slice.alignmentSpan = Slice.NO_ALIGNMENT_SPAN;
        } else {
            slice.alignmentStart = minAlStart;
            slice.alignmentSpan = maxAlEnd - minAlStart + 1;
        }

        try (final ByteArrayOutputStream bitBAOS = new ByteArrayOutputStream();
             final DefaultBitOutputStream bitOutputStream = new DefaultBitOutputStream(bitBAOS)) {

            final CramRecordWriter writer = new CramRecordWriter(bitOutputStream, externalBlockMap, header, slice.sequenceId);
            writer.writeCramCompressionRecords(records, slice.alignmentStart);

            bitOutputStream.close();
            slice.coreBlock = Block.createRawCoreDataBlock(bitBAOS.toByteArray());
        }
        catch (final IOException e) {
            throw new RuntimeIOException(e);
        }

        slice.external = new HashMap<>();
        for (final Integer contentId : externalBlockMap.keySet()) {
            // remove after https://github.com/samtools/htsjdk/issues/1232
            if (contentId == Block.NO_CONTENT_ID) {
                throw new CRAMException("Valid Content ID required.  Given: " + contentId);
            }

            final ExternalCompressor compressor = header.externalCompressors.get(contentId);
            final byte[] rawContent = externalBlockMap.get(contentId).toByteArray();
            final ExternalBlock externalBlock = new ExternalBlock(compressor.getMethod(), contentId,
                    compressor.compress(rawContent), rawContent.length);

            slice.external.put(contentId, externalBlock);
        }

        return slice;
    }

    public boolean isPreserveReadNames() {
        return preserveReadNames;
    }

    public void setPreserveReadNames(final boolean preserveReadNames) {
        this.preserveReadNames = preserveReadNames;
    }
}
