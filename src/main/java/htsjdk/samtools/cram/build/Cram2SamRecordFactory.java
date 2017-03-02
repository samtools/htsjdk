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

import htsjdk.samtools.*;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Cram2SamRecordFactory {

    private final SAMFileHeader header;

    public Cram2SamRecordFactory(final SAMFileHeader header) {
        this.header = header;
    }

    /**
     * Convert a {@link CramCompressionRecord} to a new {@link SAMRecord}. This is "shallow" rather then "deep" copy method:
     * properties are set directly where possible, therefore changes to the CRAM record may affect the created previously SAM record.
     * @param cramRecord a record to be converted
     * @return a new {@link SAMRecord} backed by the CRAM record
     */
    public SAMRecord create(final CramCompressionRecord cramRecord) {
        final SAMRecord samRecord = new InternalBinaryTagsSAMRecord(header, cramRecord.tags);

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
            samRecord.setCigar(getCigar(cramRecord.readFeatures,
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

        if (cramRecord.readGroupID > -1) {
            final SAMReadGroupRecord readGroupRecord = header.getReadGroups().get(
                    cramRecord.readGroupID);
            samRecord.setAttribute("RG", readGroupRecord.getId());
        }

        return samRecord;
    }

    /**
     * Transfer bit flags from {@link CramCompressionRecord} into {@link SAMRecord}
     * @param cramRecord destination record
     * @param samRecord source record
     */
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

    /**
     * Create a new {@link Cigar} given a collection of {@link ReadFeature} and read length.
     * The logic is that a valid cigar can be represented as a list of read features and therefore restored from back.
     * By default it is assumed that the whole read is {@link CigarOperator#MATCH_OR_MISMATCH} with exceptions provided
     * by read features.
     * Zero read length is a special case because it is unclear how many read-consuming bases should be in the cigar.
     *
     * @param features CRAM read features to be converted to CIGAR
     * @param readLength number of bases in the read
     * @return a new {@link Cigar} equivalent of the read features
     */
    public static Cigar getCigar(final Collection<ReadFeature> features,
                                 final int readLength) {
        if (readLength == 0 && (features == null || features.isEmpty())) {
            return new Cigar();
        }

        final List<CigarElement> list = new ArrayList<>();
        CigarElement cigarElement;
        CigarOperator lastOperator = CigarOperator.MATCH_OR_MISMATCH;
        int lastOpLen = 0;
        int lastOpPos = 1;
        CigarOperator cigarOperator;
        int readFeatureLength;
        if (features != null) {
            // the main loop to convert read features into cigar:
            for (final ReadFeature feature : features) {

                // gap between this and previous feature positions:
                final int gap = feature.getPosition() - (lastOpPos + lastOpLen);
                if (gap > 0) {
                    if (lastOperator != CigarOperator.MATCH_OR_MISMATCH) {
                        // fill the gap with M operator:
                        list.add(new CigarElement(lastOpLen, lastOperator));
                        lastOpPos += lastOpLen;
                        lastOpLen = gap;
                    } else {
                        // extend the last op with the gap found:
                        lastOpLen += gap;
                    }

                    // by default a gap is match or mismatch:
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

                // flush or extend previous element:
                if (lastOperator != cigarOperator) {
                    // add last feature
                    if (lastOpLen > 0) {
                        list.add(new CigarElement(lastOpLen, lastOperator));
                    }
                    lastOperator = cigarOperator;
                    lastOpLen = readFeatureLength;
                    lastOpPos = feature.getPosition();
                } else
                    lastOpLen += readFeatureLength;

                // positions are in read coordinates, so adjust previous element position:
                if (!cigarOperator.consumesReadBases())
                    lastOpPos -= readFeatureLength;
            }
        }

        // flush the last element:
        if (lastOperator != null) {
            if (lastOperator != CigarOperator.M) {
                list.add(new CigarElement(lastOpLen, lastOperator));
                // fill up the cigar with M operator to the read length:
                if (readLength >= lastOpPos + lastOpLen) {
                    cigarElement = new CigarElement(readLength - (lastOpLen + lastOpPos)
                            + 1, CigarOperator.M);
                    list.add(cigarElement);
                }
            } else if (readLength == 0 || readLength > lastOpPos - 1) {
                if (readLength == 0)
                    // unknown bases, so the last feature defines read length:
                    cigarElement = new CigarElement(lastOpLen, CigarOperator.M);
                else
                    // fill up the cigar with M operator:
                    cigarElement = new CigarElement(readLength - lastOpPos + 1, CigarOperator.M);

                list.add(cigarElement);
            }
        }

        return new Cigar(list);
    }


    /**
     * {@link SAMRecord#setAttributes(SAMBinaryTagAndValue)} is protected probably due to binary tags being BAM-specific.
     * To overcome the access restrictions we need to subclass SAMRecord. The only difference is that this class
     * accepts {@link SAMBinaryTagAndValue} tag list in the constructor.
     */
    private static class InternalBinaryTagsSAMRecord extends SAMRecord {

        public InternalBinaryTagsSAMRecord(SAMFileHeader header, SAMBinaryTagAndValue tags) {
            super(header);
            setAttributes(tags);
        }
    }
}
