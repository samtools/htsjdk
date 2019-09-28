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

import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.structure.CRAMRecord;
import htsjdk.samtools.cram.structure.SubstitutionMatrix;

import java.util.List;

//TODO: this should just move to Slice
public class CramNormalizer {
    private int readCounter = 0;

    public CramNormalizer(final CRAMReferenceSource referenceSource) {
        if (referenceSource == null) {
            throw new IllegalArgumentException("A reference is required.");
        }
    }

    // Normalize a list of CramCompressionRecords that have been read in from a CRAM stream.
    // The records in this list should be an entire slice, not a container, since the relative positions
    // of mate records are determined relative to the slice (downstream) offsets.
    public void normalize(final List<CRAMRecord> records,
                          CRAMReferenceState cramReferenceState,
                          final int refOffset_zeroBased,  //TODO: WTF is this - its always 0
                          final SubstitutionMatrix substitutionMatrix) {
        final int startCounter = readCounter;

        for (final CRAMRecord record : records) {
            //TODO: does this need to be reset (its passed to both constructors at creation time ??)
            record.setSequentialIndex(++readCounter);
        }

        // restore pairing first:
        for (final CRAMRecord record : records) {
            if (record.isMultiFragment() &&
                    !record.isDetached() &&
                    record.isHasMateDownStream()) {
                final CRAMRecord downMate = records.get(
                        record.getSequentialIndex() + record.getRecordsToNextFragment() - startCounter
                );
                record.setNextSegment(downMate);
                downMate.setPreviousSegment(record);
            }
        }

        for (final CRAMRecord record : records) {
            if (record.getPreviousSegment() == null && record.getNextSegment() != null) {
                record.restoreMateInfo();
            }
        }

        // assign some read names if needed:
        for (final CRAMRecord record : records) {
            // TODO: need encodingStrategy for read name prefix
            record.assignReadName();
        }

        // resolve bases:
        for (final CRAMRecord record : records) {
            if (!record.isSegmentUnmapped()) {
                byte[] refBases = cramReferenceState.getReferenceBases(record.getReferenceIndex());
                record.restoreReadBases(refBases, refOffset_zeroBased, substitutionMatrix);
            }
        }

        // restore quality scores:
        final byte defaultQualityScore = '?' - '!';
        //TODO: this should be a CRAMRecord instance method
        CRAMRecord.restoreQualityScores(defaultQualityScore, records);
    }
    
}
