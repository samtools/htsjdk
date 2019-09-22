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
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.structure.CRAMRecord;
import htsjdk.samtools.cram.structure.SubstitutionMatrix;

import java.util.List;

public class CramNormalizer {
    private final SAMFileHeader header;
    private CRAMReferenceSource referenceSource;
    private int readCounter = 0;

    public CramNormalizer(final SAMFileHeader header, final CRAMReferenceSource referenceSource) {
        if (referenceSource == null) {
            throw new IllegalArgumentException("A reference is required.");
        }
        this.header = header;
        this.referenceSource = referenceSource;
    }

    // Normalize a list of CramCompressionRecords that have been read in from a CRAM stream.
    public void normalize(final List<CRAMRecord> records,
                          final byte[] ref,
                          final int refOffset_zeroBased,
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
                final CRAMRecord downMate = records.get(record.getSequentialIndex() + record.getRecordsToNextFragment() - startCounter);
                record.setNextSegment(downMate);
                downMate.setPreviousSegment(record);
            }
        }

        for (final CRAMRecord record : records) {
//            if (record.getPreviousSegment() != null) {
//                continue;
//            }
//            if (record.getNextSegment() == null) {
//                continue;
//            }
//            record.restoreMateInfo();
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
                byte[] refBases = ref;
                // ref could be supplied (aka forced) already or needs looking up:
                // ref.length=0 is a special case of seqId=-2 (multiref)
                if ((ref == null || ref.length == 0) && referenceSource != null) {
                    refBases = referenceSource.getReferenceBases(header.getSequence(record.getReferenceIndex()), true);
                }
                record.restoreReadBases(refBases, refOffset_zeroBased, substitutionMatrix);
            }
        }

        // restore quality scores:
        final byte defaultQualityScore = '?' - '!';
        CRAMRecord.restoreQualityScores(defaultQualityScore, records);
    }
    
}
