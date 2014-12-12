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

import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecord.SAMTagAndValue;
import htsjdk.samtools.SAMTag;
import htsjdk.samtools.cram.encoding.read_features.BaseQualityScore;
import htsjdk.samtools.cram.encoding.read_features.Deletion;
import htsjdk.samtools.cram.encoding.read_features.HardClip;
import htsjdk.samtools.cram.encoding.read_features.InsertBase;
import htsjdk.samtools.cram.encoding.read_features.Padding;
import htsjdk.samtools.cram.encoding.read_features.ReadFeature;
import htsjdk.samtools.cram.encoding.read_features.RefSkip;
import htsjdk.samtools.cram.encoding.read_features.SoftClip;
import htsjdk.samtools.cram.encoding.read_features.Substitution;
import htsjdk.samtools.cram.mask.RefMaskUtils;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.ReadTag;
import htsjdk.samtools.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class Sam2CramRecordFactory {

    public static final String UNKNOWN_READ_GROUP_ID = "UNKNOWN";
    public static final String UNKNOWN_READ_GROUP_SAMPLE = "UNKNOWN";

    public final static byte QS_asciiOffset = 33;
    public final static byte unsetQualityScore = 32;
    public final static byte ignorePositionsWithQualityScore = -1;

    private byte[] refBases;
    private byte[] refSNPs;
    private RefMaskUtils.RefMask refPile;

    private static Log log = Log.getInstance(Sam2CramRecordFactory.class);

    private Map<String, Integer> readGroupMap = new HashMap<String, Integer>();

    private long landedRefMaskScores = 0;
    private long landedPiledScores = 0;
    private long landedTotalScores = 0;

    public boolean captureAllTags = false;
    public boolean preserveReadNames = false;
    public Set<String> captureTags = new TreeSet<String>();
    public Set<String> ignoreTags = new TreeSet<String>();

    {
        ignoreTags.add(SAMTag.NM.name());
        ignoreTags.add(SAMTag.MD.name());
        ignoreTags.add(SAMTag.RG.name());
    }

    public boolean losslessQS = false;

    private List<ReadTag> readTagList = new ArrayList<ReadTag>();

    private long baseCount = 0;
    private long featureCount = 0;

    public Sam2CramRecordFactory(int samSequenceIndex, byte[] refBases,
                                 SAMFileHeader samFileHeader) {
        this.refBases = refBases;

        List<SAMReadGroupRecord> readGroups = samFileHeader.getReadGroups();
        for (int i = 0; i < readGroups.size(); i++) {
            SAMReadGroupRecord readGroupRecord = readGroups.get(i);
            readGroupMap.put(readGroupRecord.getId(), i);
        }

    }

    public CramCompressionRecord createCramRecord(SAMRecord record) {
        CramCompressionRecord cramRecord = new CramCompressionRecord();
        if (record.getReadPairedFlag()) {
            cramRecord.mateAlignmentStart = record.getMateAlignmentStart();
            cramRecord.setMateUmapped(record.getMateUnmappedFlag());
            cramRecord
                    .setMateNegativeStrand(record.getMateNegativeStrandFlag());
            cramRecord.mateSequenceID = record.getMateReferenceIndex();
        } else
            cramRecord.mateSequenceID = -1;
        cramRecord.sequenceId = record.getReferenceIndex();
        cramRecord.readName = record.getReadName();
        cramRecord.alignmentStart = record.getAlignmentStart();

        cramRecord.setMultiFragment(record.getReadPairedFlag());
        cramRecord.setProperPair(record.getReadPairedFlag()
                && record.getProperPairFlag());
        cramRecord.setSegmentUnmapped(record.getReadUnmappedFlag());
        cramRecord.setNegativeStrand(record.getReadNegativeStrandFlag());
        cramRecord.setFirstSegment(record.getReadPairedFlag()
                && record.getFirstOfPairFlag());
        cramRecord.setLastSegment(record.getReadPairedFlag()
                && record.getSecondOfPairFlag());
        cramRecord.setSecondaryAlignment(record.getNotPrimaryAlignmentFlag());
        cramRecord.setVendorFiltered(record
                .getReadFailsVendorQualityCheckFlag());
        cramRecord.setDuplicate(record.getDuplicateReadFlag());

        cramRecord.readLength = record.getReadLength();
        cramRecord.mappingQuality = record.getMappingQuality();
        cramRecord.setDuplicate(record.getDuplicateReadFlag());

        cramRecord.templateSize = record.getInferredInsertSize();

        SAMReadGroupRecord readGroup = record.getReadGroup();
        if (readGroup != null)
            cramRecord.readGroupID = readGroupMap.get(readGroup.getId());
        else
            cramRecord.readGroupID = -1;

        if (!record.getReadPairedFlag())
            cramRecord.setLastSegment(false);
        else {
            if (record.getFirstOfPairFlag())
                cramRecord.setLastSegment(false);
            else if (record.getSecondOfPairFlag())
                cramRecord.setLastSegment(true);
            else
                cramRecord.setLastSegment(true);
        }

        if (!record.getReadUnmappedFlag()
                && record.getAlignmentStart() != SAMRecord.NO_ALIGNMENT_START) {
            List<ReadFeature> features = checkedCreateVariations(cramRecord,
                    record);
            cramRecord.readFeatures = features;
        } else
            cramRecord.readFeatures = Collections.emptyList();

        cramRecord.readBases = record.getReadBases();
        cramRecord.qualityScores = record.getBaseQualities();
        landedTotalScores += cramRecord.readLength;

        readTagList.clear();
        if (captureAllTags) {
            List<SAMTagAndValue> attributes = record.getAttributes();
            for (SAMTagAndValue tv : attributes) {
                if (ignoreTags.contains(tv.tag))
                    continue;
                readTagList.add(ReadTag.deriveTypeFromValue(tv.tag, tv.value));
            }
        } else {
            if (!captureTags.isEmpty()) {
                List<SAMTagAndValue> attributes = record.getAttributes();
                cramRecord.tags = new ReadTag[attributes.size()];
                for (SAMTagAndValue tv : attributes) {
                    if (captureTags.contains(tv.tag)) {
                        readTagList.add(ReadTag.deriveTypeFromValue(tv.tag,
                                tv.value));
                    }
                }
            }
        }
        cramRecord.tags = (ReadTag[]) readTagList
                .toArray(new ReadTag[readTagList.size()]);

        cramRecord.setVendorFiltered(record
                .getReadFailsVendorQualityCheckFlag());

        if (preserveReadNames)
            cramRecord.readName = record.getReadName();

        return cramRecord;
    }

    /**
     * A wrapper method to provide better diagnostics for
     * ArrayIndexOutOfBoundsException.
     *
     * @param cramRecord
     * @param samRecord
     * @return
     */
    private List<ReadFeature> checkedCreateVariations(
            CramCompressionRecord cramRecord, SAMRecord samRecord) {
        try {
            return createVariations(cramRecord, samRecord);
        } catch (ArrayIndexOutOfBoundsException e) {
            log.error("Reference bases array length=" + refBases.length);
            log.error("Offensive CRAM record: " + cramRecord.toString());
            log.error("Offensive SAM record: " + samRecord.getSAMString());
            throw e;
        }
    }

    private List<ReadFeature> createVariations(
            CramCompressionRecord cramRecord, SAMRecord samRecord) {
        List<ReadFeature> features = new LinkedList<ReadFeature>();
        int zeroBasedPositionInRead = 0;
        int alignmentStartOffset = 0;
        int cigarElementLength = 0;

        List<CigarElement> cigarElements = samRecord.getCigar()
                .getCigarElements();

        byte[] bases = samRecord.getReadBases();
        byte[] qualityScore = samRecord.getBaseQualities();

        for (CigarElement cigarElement : cigarElements) {
            cigarElementLength = cigarElement.getLength();
            CigarOperator operator = cigarElement.getOperator();

            switch (operator) {
                case D:
                    features.add(new Deletion(zeroBasedPositionInRead + 1,
                            cigarElementLength));
                    break;
                case N:
                    features.add(new RefSkip(zeroBasedPositionInRead + 1,
                            cigarElementLength));
                    break;
                case P:
                    features.add(new Padding(zeroBasedPositionInRead + 1,
                            cigarElementLength));
                    break;
                case H:
                    features.add(new HardClip(zeroBasedPositionInRead + 1,
                            cigarElementLength));
                    break;
                case S:
                    addSoftClip(features, zeroBasedPositionInRead,
                            cigarElementLength, bases, qualityScore);
                    break;
                case I:
                    addInsertion(features, zeroBasedPositionInRead,
                            cigarElementLength, bases, qualityScore);
                    break;
                case M:
                case X:
                case EQ:
                    addSubstitutionsAndMaskedBases(cramRecord, features,
                            zeroBasedPositionInRead, alignmentStartOffset,
                            cigarElementLength, bases, qualityScore);
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unsupported cigar operator: "
                                    + cigarElement.getOperator());
            }

            if (cigarElement.getOperator().consumesReadBases())
                zeroBasedPositionInRead += cigarElementLength;
            if (cigarElement.getOperator().consumesReferenceBases())
                alignmentStartOffset += cigarElementLength;
        }

        this.baseCount += bases.length;
        this.featureCount += features.size();

        return features;
    }

    private void addSoftClip(List<ReadFeature> features,
                             int zeroBasedPositionInRead, int cigarElementLength, byte[] bases,
                             byte[] scores) {
        byte[] insertedBases = Arrays.copyOfRange(bases,
                zeroBasedPositionInRead, zeroBasedPositionInRead
                        + cigarElementLength);

        SoftClip v = new SoftClip(zeroBasedPositionInRead + 1, insertedBases);
        features.add(v);
    }

    private void addInsertion(List<ReadFeature> features,
                              int zeroBasedPositionInRead, int cigarElementLength, byte[] bases,
                              byte[] scores) {
        byte[] insertedBases = Arrays.copyOfRange(bases,
                zeroBasedPositionInRead, zeroBasedPositionInRead
                        + cigarElementLength);

        for (int i = 0; i < insertedBases.length; i++) {
            // single base insertion:
            InsertBase ib = new InsertBase();
            ib.setPosition(zeroBasedPositionInRead + 1 + i);
            ib.setBase(insertedBases[i]);
            features.add(ib);
        }
    }

    private void addSubstitutionsAndMaskedBases(
            CramCompressionRecord cramRecord, List<ReadFeature> features,
            int fromPosInRead, int alignmentStartOffset, int nofReadBases,
            byte[] bases, byte[] qualityScore) {
        int oneBasedPositionInRead;
        boolean noQS = (qualityScore.length == 0);

        int i = 0;
        boolean qualityAdded = false;
        byte refBase;
        for (i = 0; i < nofReadBases; i++) {
            oneBasedPositionInRead = i + fromPosInRead + 1;
            int refCoord = (int) (cramRecord.alignmentStart + i + alignmentStartOffset) - 1;
            qualityAdded = false;
            if (refCoord >= refBases.length)
                refBase = 'N';
            else
                refBase = refBases[refCoord];
            refBase = Utils.normalizeBase(refBase);

            if (bases[i + fromPosInRead] != refBase) {
                Substitution sv = new Substitution();
                sv.setPosition(oneBasedPositionInRead);
                sv.setBase(bases[i + fromPosInRead]);
                sv.setRefernceBase(refBase);
                sv.setBaseChange(null);

                features.add(sv);

                if (losslessQS || noQS)
                    continue;
            }

            if (noQS)
                continue;

            if (!qualityAdded && refSNPs != null) {
                byte snpOrNot = refSNPs[refCoord];
                if (snpOrNot != 0) {
                    byte score = (byte) (QS_asciiOffset + qualityScore[i
                            + fromPosInRead]);
                    features.add(new BaseQualityScore(oneBasedPositionInRead,
                            score));
                    qualityAdded = true;
                    landedRefMaskScores++;
                }
            }

            if (!qualityAdded && refPile != null) {
                if (refPile.shouldStore(refCoord, refBase)) {
                    byte score = (byte) (QS_asciiOffset + qualityScore[i
                            + fromPosInRead]);
                    features.add(new BaseQualityScore(oneBasedPositionInRead,
                            score));
                    qualityAdded = true;
                    landedPiledScores++;
                }
            }

            if (qualityAdded)
                landedTotalScores++;
        }
    }

    public long getLandedRefMaskScores() {
        return landedRefMaskScores;
    }

    public long getLandedPiledScores() {
        return landedPiledScores;
    }

    public long getLandedTotalScores() {
        return landedTotalScores;
    }

    public byte[] getRefBases() {
        return refBases;
    }

    public void setRefBases(byte[] refBases) {
        this.refBases = refBases;
    }

    public byte[] getRefSNPs() {
        return refSNPs;
    }

    public void setRefSNPs(byte[] refSNPs) {
        this.refSNPs = refSNPs;
    }

    public RefMaskUtils.RefMask getRefPile() {
        return refPile;
    }

    public Map<String, Integer> getReadGroupMap() {
        return readGroupMap;
    }

    public void setRefPile(RefMaskUtils.RefMask refPile) {
        this.refPile = refPile;
    }

    public long getBaseCount() {
        return baseCount;
    }

    public long getFeatureCount() {
        return featureCount;
    }

}
