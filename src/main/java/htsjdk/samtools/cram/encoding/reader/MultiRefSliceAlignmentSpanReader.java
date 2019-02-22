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

import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.structure.*;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A reader that only keeps track of alignment spans.
 * The intended use is for CRAI indexing.
 *
 * @author vadim
 */
public class MultiRefSliceAlignmentSpanReader extends CramRecordReader {
    /**
     * Alignment start to of the previous record, for delta-encoding if necessary
     */
    private int prevAlignmentStart;

    /**
     * Detected sequence spans
     */
    private final Map<Integer, AlignmentSpan> spans = new HashMap<>();

    /**
     * Initializes a Multiple Reference Sequence ID Reader.
     * The intended use is for CRAI indexing.
     *
     * @param coreInputStream       Core data block bit stream, to be read by non-external Encodings
     * @param externalInputMap      External data block byte stream map, to be read by external Encodings
     * @param header                the associated Cram Compression Header
     * @param validationStringency  how strict to be when reading this CRAM record
     * @param initialAlignmentStart the alignmentStart used for initial calculation of spans
     * @param recordCount           the number of CRAM records to read
     */
    public MultiRefSliceAlignmentSpanReader(final BitInputStream coreInputStream,
                                            final Map<Integer, ByteArrayInputStream> externalInputMap,
                                            final CompressionHeader header,
                                            final ValidationStringency validationStringency,
                                            final int initialAlignmentStart,
                                            final int recordCount) {
        super(coreInputStream, externalInputMap, header, Slice.MULTI_REFERENCE, validationStringency);

        this.prevAlignmentStart = initialAlignmentStart;

        for (int i = 0; i < recordCount; i++) {
            readCramRecord();
        }
    }

    public Map<Integer, AlignmentSpan> getReferenceSpans() {
        return Collections.unmodifiableMap(spans);
    }

    private void readCramRecord() {
        final CramCompressionRecord cramRecord = new CramCompressionRecord();
        super.read(cramRecord, prevAlignmentStart);
        prevAlignmentStart = cramRecord.alignmentStart;

        if (!spans.containsKey(cramRecord.sequenceId)) {
            spans.put(cramRecord.sequenceId, new AlignmentSpan(cramRecord.alignmentStart, cramRecord.readLength));
        } else {
            spans.get(cramRecord.sequenceId).addSingle(cramRecord.alignmentStart, cramRecord.readLength);
        }
    }
}
