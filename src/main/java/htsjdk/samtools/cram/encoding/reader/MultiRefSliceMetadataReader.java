/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools.cram.encoding.reader;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.cram.structure.slice.MappedSliceMetadata;
import htsjdk.samtools.cram.structure.slice.Slice;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A reader that only keeps track of alignment metadata.
 * The intended use is to split multiref slices by reference, for CRAI/BAI indexing.
 *
 * @author vadim
 */
public class MultiRefSliceMetadataReader extends CramRecordReader {
    /**
     * Alignment start to start counting from
     */
    private int currentAlignmentStart;

    /**
     * Detected sequences with their metadata
     */
    private final Map<Integer, MappedSliceMetadata> metadataMap = new HashMap<>();

    /**
     * Initializes a Multiple Reference Sequence ID Reader.
     * The intended use is for CRAI indexing.
     *
     * @param coreInputStream       Core data block bit stream, to be read by non-external Encodings
     * @param externalInputMap      External data block byte stream map, to be read by external Encodings
     * @param header                the associated Cram Compression Header
     * @param validationStringency  how strict to be when reading this CRAM record
     * @param initialAlignmentStart the alignmentStart used for initial calculation of metadataMap
     * @param recordCount           the number of CRAM records to read
     */
    public MultiRefSliceMetadataReader(final BitInputStream coreInputStream,
                                       final Map<Integer, ByteArrayInputStream> externalInputMap,
                                       final CompressionHeader header,
                                       final ValidationStringency validationStringency,
                                       final int initialAlignmentStart,
                                       final int recordCount) {
        super(coreInputStream, externalInputMap, header, Slice.MULTI_REFERENCE, validationStringency);

        this.currentAlignmentStart = initialAlignmentStart;

        for (int i = 0; i < recordCount; i++) {
            readCramRecord();
        }
    }

    public Map<Integer, MappedSliceMetadata> getReferenceMetadata() {
        return Collections.unmodifiableMap(metadataMap);
    }

    private void readCramRecord() {
        final CramCompressionRecord cramRecord = new CramCompressionRecord();
        super.read(cramRecord);

        if (cramRecord.sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            throw new CRAMException("Multiple-reference slices can only contain CRAM records which are mapped to valid reference sequences");
        }

        if (APDelta) {
            currentAlignmentStart += cramRecord.alignmentDelta;
        } else {
            currentAlignmentStart = cramRecord.alignmentStart;
        }

        metadataMap.merge(cramRecord.sequenceId, new MappedSliceMetadata(currentAlignmentStart, cramRecord.readLength), MappedSliceMetadata::add);
    }
}
