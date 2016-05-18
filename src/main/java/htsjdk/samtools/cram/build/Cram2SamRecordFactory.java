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

import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.encoding.readfeatures.Deletion;
import htsjdk.samtools.cram.encoding.readfeatures.HardClip;
import htsjdk.samtools.cram.encoding.readfeatures.InsertBase;
import htsjdk.samtools.cram.encoding.readfeatures.Insertion;
import htsjdk.samtools.cram.encoding.readfeatures.Padding;
import htsjdk.samtools.cram.encoding.readfeatures.ReadBase;
import htsjdk.samtools.cram.encoding.readfeatures.ReadFeature;
import htsjdk.samtools.cram.encoding.readfeatures.RefSkip;
import htsjdk.samtools.cram.encoding.readfeatures.SoftClip;
import htsjdk.samtools.cram.encoding.readfeatures.Substitution;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.ReadTag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Cram2SamRecordFactory {

    private final SAMFileHeader header;

    public Cram2SamRecordFactory(final SAMFileHeader header) {
        this.header = header;
    }

    public SAMRecord create(final CramCompressionRecord cramRecord) {
        final SAMRecord samRecord = new SAMRecord(header);

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
                    .setMateAlignmentStart(cramRecord.mateAlignmentStart > 0 ? cramRecord.mateAlignmentStart : SAMRecord
                            .NO_ALIGNMENT_START);
            samRecord.setMateNegativeStrandFlag(cramRecord.isMateNegativeStrand());
            samRecord.setMateUnmappedFlag(cramRecord.isMateUnmapped());
        } else {
            samRecord
                    .setMateReferenceIndex(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
            samRecord.setMateAlignmentStart(SAMRecord.NO_ALIGNMENT_START);
        }

        samRecord.setInferredInsertSize(cramRecord.templateSize);
        samRecord.setReadBases(cramRecord.readBases);
        samRecord.setBaseQualities(cramRecord.qualityScores);

        if (cramRecord.tags != null)
            for (final ReadTag tag : cramRecord.tags)
                samRecord.setAttribute(tag.getKey(), tag.getValue());

        if (cramRecord.readGroupID > -1) {
            final SAMReadGroupRecord readGroupRecord = header.getReadGroups().get(
                    cramRecord.readGroupID);
            samRecord.setAttribute("RG", readGroupRecord.getId());
        }

        return samRecord;
    }

    private static void copyFlags(final CramCompressionRecord cramRecord, final SAMRecord samRecord) {
        samRecord.setReadPairedFlag(cramRecord.isMultiFragment());
        samRecord.setProperPairFlag(cramRecord.isProperPair());
        samRecord.setReadUnmappedFlag(cramRecord.isSegmentUnmapped());
        samRecord.setReadNegativeStrandFlag(cramRecord.isNegativeStrand());
        samRecord.setFirstOfPairFlag(cramRecord.isFirstSegment());
        samRecord.setSecondOfPairFlag(cramRecord.isLastSegment());
        samRecord.setNotPrimaryAlignmentFlag(cramRecord.isSecondaryAlignment());
        samRecord.setReadFailsVendorQualityCheckFlag(cramRecord.isVendorFiltered());
        samRecord.setDuplicateReadFlag(cramRecord.isDuplicate());
        samRecord.setSupplementaryAlignmentFlag(cramRecord.isSupplementary());
    }

    private static Cigar getCigar2(final Collection<ReadFeature> features,
                                   final int readLength) {
        if (features == null || features.isEmpty()) {
            final CigarElement cigarElement = new CigarElement(readLength, CigarOperator.M);
            return new Cigar(Collections.singletonList(cigarElement));
        }

        final List<CigarElement> list = new ArrayList<CigarElement>();
        int totalOpLen = 1;
        CigarElement cigarElement;
        CigarOperator lastOperator = CigarOperator.MATCH_OR_MISMATCH;
        int lastOpLen = 0;
        int lastOpPos = 1;
        CigarOperator cigarOperator;
        int readFeatureLength;
        for (final ReadFeature feature : features) {

            final int gap = feature.getPosition() - (lastOpPos + lastOpLen);
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

            switch (feature.getOperator()) {
                case Insertion.operator:
                    cigarOperator = CigarOperator.INSERTION;
                    readFeatureLength = ((Insertion) feature).getSequence().length;
                    break;
                case SoftClip.operator:
                    cigarOperator = CigarOperator.SOFT_CLIP;
                    readFeatureLength = ((SoftClip) feature).getSequence().length;
                    break;
                case HardClip.operator:
                    cigarOperator = CigarOperator.HARD_CLIP;
                    readFeatureLength = ((HardClip) feature).getLength();
                    break;
                case InsertBase.operator:
                    cigarOperator = CigarOperator.INSERTION;
                    readFeatureLength = 1;
                    break;
                case Deletion.operator:
                    cigarOperator = CigarOperator.DELETION;
                    readFeatureLength = ((Deletion) feature).getLength();
                    break;
                case RefSkip.operator:
                    cigarOperator = CigarOperator.SKIPPED_REGION;
                    readFeatureLength = ((RefSkip) feature).getLength();
                    break;
                case Padding.operator:
                    cigarOperator = CigarOperator.PADDING;
                    readFeatureLength = ((Padding) feature).getLength();
                    break;
                case Substitution.operator:
                case ReadBase.operator:
                    cigarOperator = CigarOperator.MATCH_OR_MISMATCH;
                    readFeatureLength = 1;
                    break;
                default:
                    continue;
            }

            if (lastOperator != cigarOperator) {
                // add last feature
                if (lastOpLen > 0) {
                    list.add(new CigarElement(lastOpLen, lastOperator));
                    totalOpLen += lastOpLen;
                }
                lastOperator = cigarOperator;
                lastOpLen = readFeatureLength;
                lastOpPos = feature.getPosition();
            } else
                lastOpLen += readFeatureLength;

            if (!cigarOperator.consumesReadBases())
                lastOpPos -= readFeatureLength;
        }

        if (lastOperator != null) {
            if (lastOperator != CigarOperator.M) {
                list.add(new CigarElement(lastOpLen, lastOperator));
                if (readLength >= lastOpPos + lastOpLen) {
                    cigarElement = new CigarElement(readLength - (lastOpLen + lastOpPos)
                            + 1, CigarOperator.M);
                    list.add(cigarElement);
                }
            } else if (readLength == 0 || readLength > lastOpPos - 1) {
                if (readLength == 0)
                    cigarElement = new CigarElement(lastOpLen, CigarOperator.M);
                else
                    cigarElement = new CigarElement(readLength - lastOpPos + 1,
                            CigarOperator.M);
                list.add(cigarElement);
            }
        }

        if (list.isEmpty()) {
            cigarElement = new CigarElement(readLength, CigarOperator.M);
            return new Cigar(Collections.singletonList(cigarElement));
        }

        return new Cigar(list);
    }
}
