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
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.*;

import java.util.*;

/**
 * A reader that only keeps track of alignment spans.
 * The intended use is for CRAI indexing.
 *
 * @author vadim
 */
public class MultiRefSliceAlignmentSpanReader extends CramRecordReader {
    /**
     * Detected sequence spans
     */
    private final Map<ReferenceContext, AlignmentSpan> spans = new HashMap<>();

    /**
     * Initializes a Multiple Reference Sequence ID Reader.
     * The intended use is for CRAI indexing.
     *
     * @param slice                 target slice from which to retrieve spans
     * @param validationStringency  how strict to be when reading this CRAM record
     * @param initialAlignmentStart the alignmentStart used for initial calculation of spans
     * @param recordCount           the number of CRAM records to read
     */
    public MultiRefSliceAlignmentSpanReader(final Slice slice,
                                            final ValidationStringency validationStringency,
                                            final int initialAlignmentStart,
                                            final int recordCount) {
        //TODO: is it ok to just new up a CompressorCache in this case ?
        //TODO: its equivalent to creating one for eery record reader, which is more granular
        //TODO: than the per-iterator case when using a CRAMIterator
        super(slice, new CompressorCache(), validationStringency);

        if (!slice.getReferenceContext().isMultiRef()) {
            throw new IllegalStateException("can only create multiref span reader for multiref context slice");
        }
        // Alignment start of the previous record, for delta-encoding if necessary
        int prevAlignmentStart = initialAlignmentStart;

        for (int i = 0; i < recordCount; i++) {
            //TODO: its a little sketchy to require
            final CRAMRecord cramRecord = super.read(slice.getIndex(),0, prevAlignmentStart);
            prevAlignmentStart = cramRecord.getAlignmentStart();
            processRecordSpan(cramRecord);
        }
    }

    public Map<ReferenceContext, AlignmentSpan> getReferenceSpans() {
        return Collections.unmodifiableMap(spans);
    }

    private void processRecordSpan(final CRAMRecord cramRecord) {
        // if unplaced: create or replace the current spans map entry.
        // we don't need to combine entries for different records because
        // we count them elsewhere and span is irrelevant

        if (! cramRecord.isPlaced()) {
            spans.put(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, AlignmentSpan.UNPLACED_SPAN);
            return;
        }

        // for placed records we do need to combine the records' spans for counting and span calculation

        AlignmentSpan span;
        if (cramRecord.isSegmentUnmapped()) {
            final int mappedCount = 0;
            final int unmappedCount = 1;
            span = new AlignmentSpan(cramRecord.getAlignmentStart(), cramRecord.getReadLength(), mappedCount, unmappedCount);
        }
        else {
            final int mappedCount = 1;
            final int unmappedCount = 0;
            span = new AlignmentSpan(cramRecord.getAlignmentStart(), cramRecord.getReadLength(), mappedCount, unmappedCount);
        }

        final ReferenceContext recordContext = new ReferenceContext(cramRecord.getReferenceIndex());
        spans.merge(recordContext, span, AlignmentSpan::combine);
    }
}
