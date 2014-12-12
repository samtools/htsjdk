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
package htsjdk.samtools.cram.build;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.encoding.read_features.Deletion;
import htsjdk.samtools.cram.encoding.read_features.HardClip;
import htsjdk.samtools.cram.encoding.read_features.InsertBase;
import htsjdk.samtools.cram.encoding.read_features.Insertion;
import htsjdk.samtools.cram.encoding.read_features.Padding;
import htsjdk.samtools.cram.encoding.read_features.ReadBase;
import htsjdk.samtools.cram.encoding.read_features.ReadFeature;
import htsjdk.samtools.cram.encoding.read_features.RefSkip;
import htsjdk.samtools.cram.encoding.read_features.SoftClip;
import htsjdk.samtools.cram.encoding.read_features.Substitution;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.ReadTag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class Cram2SamRecordFactory {

    private SAMFileHeader header;

    public Cram2SamRecordFactory(SAMFileHeader header) {
        this.header = header;
    }

    public SAMRecord create(CramCompressionRecord cramRecord) {
        SAMRecord samRecord = new SAMRecord(header);

        samRecord.setReadName(cramRecord.readName);
        copyFlags(cramRecord, samRecord);

        if (cramRecord.sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            samRecord.setAlignmentStart(SAMRecord.NO_ALIGNMENT_START);
            samRecord.setMappingQuality(SAMRecord.NO_MAPPING_QUALITY);
            samRecord.setReferenceIndex(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
        } else {
            samRecord.setReferenceIndex(cramRecord.sequenceId);
            samRecord.setAlignmentStart(cramRecord.alignmentStart);
            samRecord.setMappingQuality(cramRecord.mappingQuality);
        }

        if (cramRecord.isSegmentUnmapped())
            samRecord.setCigarString(SAMRecord.NO_ALIGNMENT_CIGAR);
        else
            samRecord.setCigar(getCigar2(cramRecord.readFeatures,
                    cramRecord.readLength));

        if (samRecord.getReadPairedFlag()) {
            samRecord.setMateReferenceIndex(cramRecord.mateSequenceID);
            samRecord
                    .setMateAlignmentStart(cramRecord.mateAlignmentStart > 0 ? cramRecord.mateAlignmentStart : SAMRecord.NO_ALIGNMENT_START);
            samRecord.setMateNegativeStrandFlag(cramRecord.isMateNegativeStrand());
            samRecord.setMateUnmappedFlag(cramRecord.isMateUmapped());
        } else {
            samRecord
                    .setMateReferenceIndex(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
            samRecord.setMateAlignmentStart(SAMRecord.NO_ALIGNMENT_START);
        }

        samRecord.setInferredInsertSize(cramRecord.templateSize);
        samRecord.setReadBases(cramRecord.readBases);
        samRecord.setBaseQualities(cramRecord.qualityScores);

        if (cramRecord.tags != null)
            for (ReadTag tag : cramRecord.tags)
                samRecord.setAttribute(tag.getKey(), tag.getValue());

        if (cramRecord.readGroupID > -1) {
            SAMReadGroupRecord readGroupRecord = header.getReadGroups().get(
                    cramRecord.readGroupID);
            samRecord.setAttribute("RG", readGroupRecord.getId());
        }

        return samRecord;
    }

    private static final void copyFlags(CramCompressionRecord cr, SAMRecord sr) {
        sr.setReadPairedFlag(cr.isMultiFragment());
        sr.setProperPairFlag(cr.isProperPair());
        sr.setReadUnmappedFlag(cr.isSegmentUnmapped());
        sr.setReadNegativeStrandFlag(cr.isNegativeStrand());
        sr.setFirstOfPairFlag(cr.isFirstSegment());
        sr.setSecondOfPairFlag(cr.isLastSegment());
        sr.setNotPrimaryAlignmentFlag(cr.isSecondaryAlignment());
        sr.setReadFailsVendorQualityCheckFlag(cr.isVendorFiltered());
        sr.setDuplicateReadFlag(cr.isDuplicate());
    }

    private static final Cigar getCigar2(Collection<ReadFeature> features,
                                         int readLength) {
        if (features == null || features.isEmpty()) {
            CigarElement ce = new CigarElement(readLength, CigarOperator.M);
            return new Cigar(Arrays.asList(ce));
        }

        List<CigarElement> list = new ArrayList<CigarElement>();
        int totalOpLen = 1;
        CigarElement ce;
        CigarOperator lastOperator = CigarOperator.MATCH_OR_MISMATCH;
        int lastOpLen = 0;
        int lastOpPos = 1;
        CigarOperator co = null;
        int rfLen = 0;
        for (ReadFeature f : features) {

            int gap = f.getPosition() - (lastOpPos + lastOpLen);
            if (gap > 0) {
                if (lastOperator != CigarOperator.MATCH_OR_MISMATCH) {
                    list.add(new CigarElement(lastOpLen, lastOperator));
                    lastOpPos += lastOpLen;
                    totalOpLen += lastOpLen;
                    lastOpLen = gap;
                } else {
                    lastOpLen += gap;
                }

                lastOperator = CigarOperator.MATCH_OR_MISMATCH;
            }

            switch (f.getOperator()) {
                case Insertion.operator:
                    co = CigarOperator.INSERTION;
                    rfLen = ((Insertion) f).getSequence().length;
                    break;
                case SoftClip.operator:
                    co = CigarOperator.SOFT_CLIP;
                    rfLen = ((SoftClip) f).getSequence().length;
                    break;
                case HardClip.operator:
                    co = CigarOperator.HARD_CLIP;
                    rfLen = ((HardClip) f).getLength();
                    break;
                case InsertBase.operator:
                    co = CigarOperator.INSERTION;
                    rfLen = 1;
                    break;
                case Deletion.operator:
                    co = CigarOperator.DELETION;
                    rfLen = ((Deletion) f).getLength();
                    break;
                case RefSkip.operator:
                    co = CigarOperator.SKIPPED_REGION;
                    rfLen = ((RefSkip) f).getLength();
                    break;
                case Padding.operator:
                    co = CigarOperator.PADDING;
                    rfLen = ((Padding) f).getLength();
                    break;
                case Substitution.operator:
                case ReadBase.operator:
                    co = CigarOperator.MATCH_OR_MISMATCH;
                    rfLen = 1;
                    break;
                default:
                    continue;
            }

            if (lastOperator != co) {
                // add last feature
                if (lastOpLen > 0) {
                    list.add(new CigarElement(lastOpLen, lastOperator));
                    totalOpLen += lastOpLen;
                }
                lastOperator = co;
                lastOpLen = rfLen;
                lastOpPos = f.getPosition();
            } else
                lastOpLen += rfLen;

            if (!co.consumesReadBases())
                lastOpPos -= rfLen;
        }

        if (lastOperator != null) {
            if (lastOperator != CigarOperator.M) {
                list.add(new CigarElement(lastOpLen, lastOperator));
                if (readLength >= lastOpPos + lastOpLen) {
                    ce = new CigarElement(readLength - (lastOpLen + lastOpPos)
                            + 1, CigarOperator.M);
                    list.add(ce);
                }
            } else if (readLength > lastOpPos - 1) {
                ce = new CigarElement(readLength - lastOpPos + 1,
                        CigarOperator.M);
                list.add(ce);
            }
        }

        if (list.isEmpty()) {
            ce = new CigarElement(readLength, CigarOperator.M);
            return new Cigar(Arrays.asList(ce));
        }

        return new Cigar(list);
    }
}
