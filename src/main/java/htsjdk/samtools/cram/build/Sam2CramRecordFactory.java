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

import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecord.SAMTagAndValue;
import htsjdk.samtools.SAMTag;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.encoding.readfeatures.*;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.ReadTag;
import htsjdk.samtools.util.Log;

import javax.rmi.CORBA.Util;
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

    private final static byte QS_asciiOffset = 33;
    public final static byte unsetQualityScore = 32;
    public final static byte ignorePositionsWithQualityScore = -1;

    private byte[] refBases;
    private final Version version;
    private byte[] refSNPs;

    final private SAMFileHeader header;

    private static final Log log = Log.getInstance(Sam2CramRecordFactory.class);

    private final Map<String, Integer> readGroupMap = new HashMap<String, Integer>();

    private long landedRefMaskScores = 0;
    private long landedTotalScores = 0;

    public boolean captureAllTags = false;
    public boolean preserveReadNames = false;
    public final Set<String> captureTags = new TreeSet<String>();
    public final Set<String> ignoreTags = new TreeSet<String>();

    {
        ignoreTags.add(SAMTag.RG.name());
    }

    private final List<ReadTag> readTagList = new ArrayList<ReadTag>();

    private long baseCount = 0;
    private long featureCount = 0;

    public Sam2CramRecordFactory(final byte[] refBases, final SAMFileHeader samFileHeader, final Version version) {
        this.refBases = refBases;
        this.version = version;
        this.header = samFileHeader;

        final List<SAMReadGroupRecord> readGroups = samFileHeader.getReadGroups();
        for (int i = 0; i < readGroups.size(); i++) {
            final SAMReadGroupRecord readGroupRecord = readGroups.get(i);
            readGroupMap.put(readGroupRecord.getId(), i);
        }
    }

    /**
     * Create a CramCompressionRecord.
     *
     * @param record If the input record does not have an associated SAMFileHeader, it will be updated
     *               with the header used for the factory in order to allow reference indices to be resolved.
     * @return CramCompressionRecord
     */
    public CramCompressionRecord createCramRecord(final SAMRecord record) {
        if (null == record.getHeader()) {
            record.setHeader(header);
        }
        final CramCompressionRecord cramRecord = new CramCompressionRecord();
        if (record.getReadPairedFlag()) {
            cramRecord.mateAlignmentStart = record.getMateAlignmentStart();
            cramRecord.setMateUnmapped(record.getMateUnmappedFlag());
            cramRecord.setMateNegativeStrand(record.getMateNegativeStrandFlag());
            cramRecord.mateSequenceID = record.getMateReferenceIndex();
        } else cramRecord.mateSequenceID = -1;
        cramRecord.sequenceId = record.getReferenceIndex();
        cramRecord.readName = record.getReadName();
        cramRecord.alignmentStart = record.getAlignmentStart();

        cramRecord.setMultiFragment(record.getReadPairedFlag());
        cramRecord.setProperPair(record.getReadPairedFlag() && record.getProperPairFlag());
        cramRecord.setSegmentUnmapped(record.getReadUnmappedFlag());
        cramRecord.setNegativeStrand(record.getReadNegativeStrandFlag());
        cramRecord.setFirstSegment(record.getReadPairedFlag() && record.getFirstOfPairFlag());
        cramRecord.setLastSegment(record.getReadPairedFlag() && record.getSecondOfPairFlag());
        cramRecord.setSecondaryAlignment(record.getNotPrimaryAlignmentFlag());
        cramRecord.setVendorFiltered(record.getReadFailsVendorQualityCheckFlag());
        cramRecord.setDuplicate(record.getDuplicateReadFlag());
        cramRecord.setSupplementary(record.getSupplementaryAlignmentFlag());

        cramRecord.readLength = record.getReadLength();
        cramRecord.mappingQuality = record.getMappingQuality();
        cramRecord.setDuplicate(record.getDuplicateReadFlag());

        cramRecord.templateSize = record.getInferredInsertSize();

        final SAMReadGroupRecord readGroup = record.getReadGroup();
        if (readGroup != null) cramRecord.readGroupID = readGroupMap.get(readGroup.getId());
        else cramRecord.readGroupID = -1;

        if (!record.getReadPairedFlag()) cramRecord.setLastSegment(false);
        else {
            if (record.getFirstOfPairFlag()) cramRecord.setLastSegment(false);
            else if (record.getSecondOfPairFlag()) cramRecord.setLastSegment(true);
        }

        cramRecord.readBases = record.getReadBases();
        cramRecord.qualityScores = record.getBaseQualities();
        if (cramRecord.readBases.length == 0) {
            cramRecord.readBases = new byte[record.getCigar().getReadLength()];
            Arrays.fill(cramRecord.readBases, (byte) 'N');
        } else {
            // normalize bases:
            for (int i=0; i<cramRecord.readBases.length; i++) {
                cramRecord.readBases[i] = Utils.normalizeBase(cramRecord.readBases[i]);
            }
        }

        if (!record.getReadUnmappedFlag() && record.getAlignmentStart() != SAMRecord.NO_ALIGNMENT_START) {
            cramRecord.readFeatures = checkedCreateVariations(cramRecord, record.getCigar().getCigarElements());
        } else cramRecord.readFeatures = Collections.emptyList();

        landedTotalScores += cramRecord.readLength;
        if (version.compatibleWith(CramVersions.CRAM_v3))
            cramRecord.setUnknownBases(record.getReadBases() == SAMRecord.NULL_SEQUENCE);

        readTagList.clear();
        if (captureAllTags) {
            final List<SAMTagAndValue> attributes = record.getAttributes();
            for (final SAMTagAndValue tagAndValue : attributes) {
                if (ignoreTags.contains(tagAndValue.tag)) continue;
                readTagList.add(ReadTag.deriveTypeFromValue(tagAndValue.tag, tagAndValue.value));
            }
        } else {
            if (!captureTags.isEmpty()) {
                final List<SAMTagAndValue> attributes = record.getAttributes();
                cramRecord.tags = new ReadTag[attributes.size()];
                for (final SAMTagAndValue tagAndValue : attributes) {
                    if (captureTags.contains(tagAndValue.tag)) {
                        readTagList.add(ReadTag.deriveTypeFromValue(tagAndValue.tag, tagAndValue.value));
                    }
                }
            }
        }
        cramRecord.tags = readTagList.toArray(new ReadTag[readTagList.size()]);

        cramRecord.setVendorFiltered(record.getReadFailsVendorQualityCheckFlag());

        if (preserveReadNames) cramRecord.readName = record.getReadName();

        return cramRecord;
    }

    /**
     * A wrapper method to provide better diagnostics for ArrayIndexOutOfBoundsException.
     *
     * @param cramRecord    CRAM record
     * @param cigarElements a list of cigar elements for the read
     * @return a list of read features created for the given {@link htsjdk.samtools.SAMRecord}
     */
    private List<ReadFeature> checkedCreateVariations(final CramCompressionRecord cramRecord, final List<CigarElement> cigarElements) {
        try {
            return createVariations(cramRecord, cigarElements);
        } catch (final ArrayIndexOutOfBoundsException e) {
            log.error("Reference bases array length=" + refBases.length);
            log.error("Offensive CRAM record: " + cramRecord.toString());
            throw e;
        }
    }

    private List<ReadFeature> createVariations(final CramCompressionRecord cramRecord, final List<CigarElement> cigarElements) {
        final List<ReadFeature> features = new LinkedList<ReadFeature>();
        int zeroBasedPositionInRead = 0;
        int alignmentStartOffset = 0;
        int cigarElementLength;

        byte[] bases = cramRecord.readBases;
        final byte[] qualityScore = cramRecord.qualityScores;

        for (final CigarElement cigarElement : cigarElements) {
            cigarElementLength = cigarElement.getLength();
            final CigarOperator operator = cigarElement.getOperator();

            switch (operator) {
                case D:
                    features.add(new Deletion(zeroBasedPositionInRead + 1, cigarElementLength));
                    break;
                case N:
                    features.add(new RefSkip(zeroBasedPositionInRead + 1, cigarElementLength));
                    break;
                case P:
                    features.add(new Padding(zeroBasedPositionInRead + 1, cigarElementLength));
                    break;
                case H:
                    features.add(new HardClip(zeroBasedPositionInRead + 1, cigarElementLength));
                    break;
                case S:
                    addSoftClip(features, zeroBasedPositionInRead, cigarElementLength, bases);
                    break;
                case I:
                    addInsertion(features, zeroBasedPositionInRead, cigarElementLength, bases);
                    break;
                case M:
                case X:
                case EQ:
                    addSubstitutionsAndMaskedBases(cramRecord, features, zeroBasedPositionInRead, alignmentStartOffset,
                            cigarElementLength, bases, qualityScore);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported cigar operator: " + cigarElement.getOperator());
            }

            if (cigarElement.getOperator().consumesReadBases()) zeroBasedPositionInRead += cigarElementLength;
            if (cigarElement.getOperator().consumesReferenceBases()) alignmentStartOffset += cigarElementLength;
        }

        this.baseCount += bases.length;
        this.featureCount += features.size();

        return features;
    }

    private void addSoftClip(final List<ReadFeature> features, final int zeroBasedPositionInRead, final int cigarElementLength, final byte[] bases) {
        final byte[] insertedBases = Arrays.copyOfRange(bases, zeroBasedPositionInRead, zeroBasedPositionInRead + cigarElementLength);

        final SoftClip softClip = new SoftClip(zeroBasedPositionInRead + 1, insertedBases);
        features.add(softClip);
    }

    private void addHardClip(final List<ReadFeature> features, final int zeroBasedPositionInRead, final int cigarElementLength, final byte[] bases) {
        final byte[] insertedBases = Arrays.copyOfRange(bases, zeroBasedPositionInRead, zeroBasedPositionInRead + cigarElementLength);

        final HardClip hardClip = new HardClip(zeroBasedPositionInRead + 1, insertedBases.length);
        features.add(hardClip);
    }

    private void addInsertion(final List<ReadFeature> features, final int zeroBasedPositionInRead, final int cigarElementLength, final byte[] bases) {
        final byte[] insertedBases = Arrays.copyOfRange(bases, zeroBasedPositionInRead, zeroBasedPositionInRead + cigarElementLength);


        for (int i = 0; i < insertedBases.length; i++) {
            // single base insertion:
            final InsertBase insertBase = new InsertBase();
            insertBase.setPosition(zeroBasedPositionInRead + 1 + i);
            insertBase.setBase(insertedBases[i]);
            features.add(insertBase);
        }
    }

    private void addSubstitutionsAndMaskedBases(final CramCompressionRecord cramRecord, final List<ReadFeature> features, final int fromPosInRead, final int
            alignmentStartOffset, final int nofReadBases, final byte[] bases, final byte[] qualityScore) {
        int oneBasedPositionInRead;
        final boolean noQS = (qualityScore.length == 0);

        int i;
        boolean qualityAdded;
        byte refBase;
        for (i = 0; i < nofReadBases; i++) {
            oneBasedPositionInRead = i + fromPosInRead + 1;
            final int referenceCoordinates = cramRecord.alignmentStart + i + alignmentStartOffset - 1;
            qualityAdded = false;
            if (referenceCoordinates >= refBases.length) refBase = 'N';
            else refBase = refBases[referenceCoordinates];
            refBase = Utils.normalizeBase(refBase);

            final byte base = bases[i + fromPosInRead];
            if (base != refBase) {
                switch (base) {
                    case 'A':
                    case 'C':
                    case 'G':
                    case 'T':
                        final Substitution substitution = new Substitution();
                        substitution.setPosition(oneBasedPositionInRead);
                        substitution.setBase(bases[i + fromPosInRead]);
                        substitution.setReferenceBase(refBase);

                        features.add(substitution);

                        if (refSNPs != null) {
                            final byte snpOrNot = refSNPs[referenceCoordinates];
                            if (snpOrNot != 0) {
                                final byte score = (byte) (QS_asciiOffset + qualityScore[i + fromPosInRead]);
                                features.add(new BaseQualityScore(oneBasedPositionInRead, score));
                                qualityAdded = true;
                                landedRefMaskScores++;
                            }
                        }
                        break;
                    default:
                        if (qualityScore == null || qualityScore.length == 0) {
                            Bases bb = new Bases(oneBasedPositionInRead, new byte[]{base});
                            features.add(bb);
                        } else {
                            final byte score = (byte) (QS_asciiOffset + qualityScore[i + fromPosInRead]);
                            ReadBase rb = new ReadBase(oneBasedPositionInRead, base, score);
                            features.add(rb);
                            qualityAdded = true;
                        }
                        break;
                }
            } else {
                if (!noQS) {
                    if (refSNPs != null) {
                        final byte snpOrNot = refSNPs[referenceCoordinates];
                        if (snpOrNot != 0) {
                            final byte score = (byte) (QS_asciiOffset + qualityScore[i + fromPosInRead]);
                            features.add(new BaseQualityScore(oneBasedPositionInRead, score));
                            qualityAdded = true;
                            landedRefMaskScores++;
                        }
                    }
                }
            }

            if (qualityAdded) landedTotalScores++;
        }
    }

    public long getLandedRefMaskScores() {
        return landedRefMaskScores;
    }

    public long getLandedTotalScores() {
        return landedTotalScores;
    }

    public byte[] getRefBases() {
        return refBases;
    }

    public void setRefBases(final byte[] refBases) {
        this.refBases = refBases;
    }

    public byte[] getRefSNPs() {
        return refSNPs;
    }

    public void setRefSNPs(final byte[] refSNPs) {
        this.refSNPs = refSNPs;
    }

    public Map<String, Integer> getReadGroupMap() {
        return readGroupMap;
    }


    public long getBaseCount() {
        return baseCount;
    }

    public long getFeatureCount() {
        return featureCount;
    }

}
