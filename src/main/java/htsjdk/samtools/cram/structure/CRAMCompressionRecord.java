/*
 * Copyright (c) 2019 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.*;
import htsjdk.samtools.cram.build.CRAMReferenceRegion;
import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.MutableInt;
import htsjdk.samtools.cram.encoding.readfeatures.BaseQualityScore;
import htsjdk.samtools.cram.encoding.readfeatures.ReadBase;
import htsjdk.samtools.cram.encoding.readfeatures.ReadFeature;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.utils.ValidationUtils;

import java.util.*;

/**
 * A CRAMRecord represents a SAMRecord that has been transformed to CRAM-style representation. This includes
 * representing read bases as reference-relative read features, and representation of quality scores,
 * tags, and BAM and CRAM flags.
 */
public class CRAMCompressionRecord {
    private static final Log log = Log.getInstance(CRAMCompressionRecord.class);

    // CF data series flags (defined by the CRAM spec)
    public static final int CF_QS_PRESERVED_AS_ARRAY = 0x1;  // preserve quality scores as array
    public static final int CF_DETACHED            = 0x2;  // mate is stored literally vs as record offset
    public static final int CF_HAS_MATE_DOWNSTREAM = 0x4;
    // sequence is unknown; encoded reference differences are present only to recreate the CIGAR string
    public static final int CF_UNKNOWN_BASES       = 0x8;

    public final static int NO_READGROUP_ID = -1;
    public final static byte MISSING_QUALITY_SCORE = -1; // SAMRecord.UNKNOWN_MAPPING_QUALITY: 255
    private final static byte DEFAULT_QUALITY_SCORE = '?' - '!';

    // NOTE: "mate unmapped" and "mate negative strand" (MF_MATE_UNMAPPED and MF_MATE_NEG_STRAND, aka
    // MATE_REVERSE_STRAND and MATE_UNMAPPED in SAMRecord flag space) have different values in CRAM
    // mateFlags space and SAMFlags space) are modeled redundantly in the bamFlags and mateFlags fields.
    // MF data series flags (defined by the CRAM spec)
    public static final int MF_MATE_NEG_STRAND = 0x1;  // same meaning as SAMFlag.MATE_REVERSE_STRAND, but different value
    public static final int MF_MATE_UNMAPPED   = 0x2;  // same meaning as SAMFlag.MATE_UNMAPPED, but different value

    private final int referenceIndex;
    private final int alignmentStart; // position on the reference where this read starts (1-based (SAM) coordinates)
    private final int alignmentEnd;  // position on the reference where this alignment ends
    private final int readLength;
    private final CRAMRecordReadFeatures readFeatures;
    private final int mappingQuality;
    private final int readGroupID;
    private final List<ReadTag> tags;
    private final long sequentialIndex; // 1 based sequential index of this record in the cram stream

    private int bamFlags;
    private int cramFlags;
    private int templateSize;
    private String readName;
    // readBases is always initialized to at least SAMRecord.NULL_SEQUENCE (byte[0], since
    // the contents hasher doesn't handle nulls
    private byte[] readBases;
    private byte[] qualityScores;
    private MutableInt tagIdsIndex = new MutableInt(0);

    //mate info
    private int mateFlags;
    private int mateAlignmentStart;
    private int mateReferenceIndex;
    private int recordsToNextFragment = -1;
    private CRAMCompressionRecord nextSegment = null;
    private CRAMCompressionRecord previousSegment = null;

    // keep track of whether this record is raw or normalized
    private boolean isNormalized = false;

    /**
     * Create a CRAMRecord from a SAMRecord.
     *
     * @param cramVersion
     * @param encodingStrategy
     * @param samRecord
     * @param referenceBases
     * @param sequentialIndex
     * @param readGroupMap
     */
    public CRAMCompressionRecord(
            final CRAMVersion cramVersion,
            final CRAMEncodingStrategy encodingStrategy,
            final SAMRecord samRecord,
            final byte[] referenceBases,
            final long sequentialIndex,
            final Map<String, Integer> readGroupMap) {
        ValidationUtils.nonNull(cramVersion);
        ValidationUtils.nonNull(encodingStrategy);
        ValidationUtils.nonNull(samRecord, "a valid SAMRecord is required");
        ValidationUtils.nonNull(samRecord.getHeader(), "SAMRecord must have a valid header");
        ValidationUtils.nonNull(referenceBases != null || samRecord.getReadUnmappedFlag() == false);
        ValidationUtils.validateArg(sequentialIndex >= 0, "sequential index must be >= 0");
        ValidationUtils.nonNull(readGroupMap);

        this.sequentialIndex = sequentialIndex;

        // all records are written as detached state
        setToDetachedState();

        // flags:
        // Although the CRAM spec allows some of these flags (it doesn't explicitly state which ones) to be
        // omitted from the actual bam flags data series ("Note however some of these flags can be derived during
        // decode, so may be omitted in the CRAM file and the bits computed based on both reads of a pair-end library
        // residing within the same slice."), this implementation preserves them all on write since we emit
        // all pairs as detached, even when in the same slice.
        setMultiFragment(samRecord.getReadPairedFlag());
        setProperPair(samRecord.getReadPairedFlag() && samRecord.getProperPairFlag());
        setSegmentUnmapped(samRecord.getReadUnmappedFlag());
        setFirstSegment(samRecord.getReadPairedFlag() && samRecord.getFirstOfPairFlag());
        setLastSegment(samRecord.getReadPairedFlag() && samRecord.getSecondOfPairFlag());
        setNegativeStrand(samRecord.getReadNegativeStrandFlag());
        setSecondaryAlignment(samRecord.isSecondaryAlignment());
        setSupplementary(samRecord.getSupplementaryAlignmentFlag());
        setVendorFiltered(samRecord.getReadFailsVendorQualityCheckFlag());
        setDuplicate(samRecord.getDuplicateReadFlag());

        readName = samRecord.getReadName();
        referenceIndex = samRecord.getReferenceIndex();

        readLength = samRecord.getReadLength();
        alignmentStart = samRecord.getAlignmentStart();
        // CRAM read base substitutions are limited to substitutions for ACGTN in the reference (see
        // https://github.com/samtools/hts-specs/blob/master/CRAMv3.pdf). However, BAM format allows upper case
        // IUPAC codes without a dot, so we follow the same approach to reproduce the behaviour of samtools.
        // copy read bases before we modify the bases to BAM bases to avoid changing the original record:
        final byte[] originalBases = samRecord.getReadBases();
        readBases = originalBases == null || originalBases.equals(SAMRecord.NULL_SEQUENCE) ?
                SAMRecord.NULL_SEQUENCE :
                SequenceUtil.toBamReadBasesInPlace(Arrays.copyOf(originalBases, samRecord.getReadLength()));
        if (samRecord.getReadUnmappedFlag()) {
            readFeatures = new CRAMRecordReadFeatures();
            alignmentEnd = AlignmentContext.NO_ALIGNMENT_END;
        } else {
            readFeatures = new CRAMRecordReadFeatures(samRecord, readBases, referenceBases);
            alignmentEnd = readFeatures.getAlignmentEnd(alignmentStart, readLength);
        }

        templateSize = samRecord.getInferredInsertSize();
        mappingQuality = samRecord.getMappingQuality();

        if (samRecord.getReadPairedFlag()) {
            mateAlignmentStart = samRecord.getMateAlignmentStart();
            setMateUnmapped(samRecord.getMateUnmappedFlag());
            setMateNegativeStrand(samRecord.getMateNegativeStrandFlag());
            mateReferenceIndex = samRecord.getMateReferenceIndex();
        } else {
            mateAlignmentStart = 0;
            mateReferenceIndex = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        }

        if (cramVersion.compatibleWith(CramVersions.CRAM_v3)) {
            setUnknownBases(samRecord.getReadBases().equals(SAMRecord.NULL_SEQUENCE));
        }

        qualityScores = samRecord.getBaseQualities();
        if (!qualityScores.equals(SAMRecord.NULL_QUALS)) {
            setForcePreserveQualityScores(true);
        } else if (readFeatures.getReadFeaturesList().size() > 0) {
            // Some ReadFeatures (such as ReadBase) have an associated quality score. If our read has no
            // quality scores, we need to synthesize an array of missing scores and force it to be serialized
            // so that the reader knows to ignore the quality scores that accompany the read features (the
            // reader will recognize that the serialized scores are all missing and will set the record to "*",
            // see restoreQualityScores.)
            qualityScores = new byte[readLength];
            Arrays.fill(qualityScores, MISSING_QUALITY_SCORE);
            setForcePreserveQualityScores(true);
        }

        final SAMReadGroupRecord readGroup = samRecord.getReadGroup();
        readGroupID = readGroup == null ?
                NO_READGROUP_ID :
                readGroupMap.get(readGroup.getId());

        if (samRecord.getAttributes().size() > 0) {
            tags = new ArrayList();
            for (final SAMRecord.SAMTagAndValue tagAndValue : samRecord.getAttributes()) {
                // Skip read group, since read group have a dedicated data series
                if (!SAMTag.RG.name().equals(tagAndValue.tag)) {
                    tags.add(ReadTag.deriveTypeFromValue(tagAndValue.tag, tagAndValue.value));
                }
            }
        } else {
            tags = null;
        }
    }

    /**
     * Create a CRAMRecord from a set of values retrieved from a serialized Slice's data series streams.
     *
     * @param sequentialIndex
     * @param bamFlags
     * @param cramFlags
     * @param readName
     * @param readLength
     * @param referenceIndex
     * @param alignmentStart
     * @param templateSize
     * @param mappingQuality
     * @param qualityScores
     * @param readBases
     * @param readTags
     * @param readFeaturesList
     * @param readGroupID
     * @param mateFlags
     * @param mateReferenceIndex
     * @param mateAlignmentStart
     * @param recordsToNextFragment
     */
    public CRAMCompressionRecord(
            final long sequentialIndex,
            final int bamFlags,
            final int cramFlags,
            final String readName,
            final int readLength,
            final int referenceIndex,
            final int alignmentStart,
            final int templateSize,
            final int mappingQuality,
            final byte[] qualityScores,
            final byte[] readBases,
            final List<ReadTag> readTags,
            final List<ReadFeature>readFeaturesList,
            final int readGroupID,
            final int mateFlags,
            final int mateReferenceIndex,
            final int mateAlignmentStart,
            final int recordsToNextFragment) {
        ValidationUtils.nonNull( qualityScores,"quality scores argument must be null or nonzero length");
        ValidationUtils.nonNull(readBases,"read bases argument cannot be null");
        ValidationUtils.validateArg(readTags == null || readTags.size() > 0, "invalid read tag argument");
        ValidationUtils.validateArg(readFeaturesList == null || readFeaturesList.size() > 0, "invalid read features argument");
        ValidationUtils.validateArg(sequentialIndex >= 0, "index must be >= 0");

        this.sequentialIndex = sequentialIndex;
        this.bamFlags = bamFlags;
        this.cramFlags = cramFlags;
        this.readName = readName;
        this.readLength = readLength;
        this.referenceIndex = referenceIndex;
        this.alignmentStart = alignmentStart;
        this.templateSize = templateSize;
        this.mappingQuality = mappingQuality;
        this.qualityScores = qualityScores;
        this.readBases = readBases;
        this.tags = readTags;
        this.readGroupID = readGroupID;
        this.mateFlags = mateFlags;
        this.mateReferenceIndex = mateReferenceIndex;
        this.mateAlignmentStart = mateAlignmentStart;
        this.recordsToNextFragment = recordsToNextFragment;
        // its acceptable to have a mapped, placed read, but no read features, if the read matches the
        // reference exactly
        readFeatures = readFeaturesList == null ?
                    new CRAMRecordReadFeatures() :
                    new CRAMRecordReadFeatures(readFeaturesList);
        alignmentEnd = isPlaced() ?
                this.readFeatures.getAlignmentEnd(alignmentStart, readLength) :
                AlignmentContext.NO_ALIGNMENT_END;
    }

    /**
     * Create a SAMRecord from the CRAMRecord.
     * @param samFileHeader SAMFileHeader
     * @return a SAMRecord
     */
    public SAMRecord toSAMRecord(final SAMFileHeader samFileHeader) {
        ValidationUtils.nonNull( samFileHeader,"a valid sam header is required");
        ValidationUtils.validateArg( isNormalized,"record must be normalized to convert to SAMRecord");
        final SAMRecord samRecord = new SAMRecord(samFileHeader);

        samRecord.setReadName(readName);
        copyFlags(this, samRecord);

        if (referenceIndex == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            samRecord.setReferenceIndex(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
            samRecord.setAlignmentStart(SAMRecord.NO_ALIGNMENT_START);
            samRecord.setMappingQuality(SAMRecord.NO_MAPPING_QUALITY);
        } else {
            samRecord.setReferenceIndex(referenceIndex);
            samRecord.setAlignmentStart(alignmentStart);
            samRecord.setMappingQuality(mappingQuality);
        }

        if (isSegmentUnmapped()) {
            samRecord.setCigarString(SAMRecord.NO_ALIGNMENT_CIGAR);
        } else {
            samRecord.setCigar(readFeatures.getCigarForReadFeatures(readLength));
        }

        if (samRecord.getReadPairedFlag()) {
            samRecord.setMateReferenceIndex(mateReferenceIndex);
            samRecord.setMateAlignmentStart(mateAlignmentStart > 0 ?
                            mateAlignmentStart :
                            SAMRecord.NO_ALIGNMENT_START);
            samRecord.setMateNegativeStrandFlag(isMateNegativeStrand());
            samRecord.setMateUnmappedFlag(isMateUnmapped());
        } else {
            samRecord.setMateReferenceIndex(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
            samRecord.setMateAlignmentStart(SAMRecord.NO_ALIGNMENT_START);
        }

        samRecord.setInferredInsertSize(templateSize);
        samRecord.setReadBases(readBases);
        samRecord.setBaseQualities(qualityScores);

        if (tags != null) {
            for (final ReadTag tag : tags) {
                samRecord.setAttribute(tag.getKey(), tag.getValue());
            }
        }

        if (readGroupID != NO_READGROUP_ID) {
            final SAMReadGroupRecord readGroupRecord = samFileHeader.getReadGroups().get(readGroupID);
            samRecord.setAttribute("RG", readGroupRecord.getId());
        }

        return samRecord;
    }

    //TODO: how to resolve readnames when we don’t save them for supplementary / secondary reads that don’t
    //appear near their primaries and don’t have a primary linking to them?
    public void assignReadName() {
        if (readName == null) {
            readName = Long.toString(getSequentialIndex());
            if (nextSegment != null) {
                nextSegment.readName = readName;
            }
            if (previousSegment != null) {
                previousSegment.readName = readName;
            }
        }
    }

    /**
     * Set this record's state to normalized. When a CRAM record is read from a CRAM stream, it is "raw" in
     * that the record's read bases, quality scores, and mate graph are not stored directly as part of the
     * record. Normalization is the process of resolving these values, and is performed at Slice granularity,
     * across all records in a Slice.
     * (see {@link Slice#normalizeCRAMRecords(List, CRAMReferenceRegion, SubstitutionMatrix)}).
     */
    void setIsNormalized() { isNormalized = true; }

    /**
     * When a CRAM record is read from a CRAM stream, it is "raw" in that the record's read bases, quality
     * scores, and mate graph are not stored directly as part of the record. These values must be resolved
     * through the separate process of normalization, which is performed at Slice granularity (all records in a
     * Slice are normalized at the same time).
     * (see {@link Slice#normalizeCRAMRecords(List, CRAMReferenceRegion, SubstitutionMatrix)}).
     * @return true if this record is normalized
     */
    public boolean isNormalized() { return isNormalized; }

    /**
     * Resolve the quality scores for this CRAM record based on preserved scores, read features and flags.
     */
    public void resolveQualityScores() {
        if (!isForcePreserveQualityScores()) {
            boolean hasMissingScores = true;
            final byte[] scores = new byte[readLength];
            Arrays.fill(scores, DEFAULT_QUALITY_SCORE);
            for (final ReadFeature feature : getReadFeatures()) {
                switch (feature.getOperator()) {
                    case BaseQualityScore.operator:
                        int pos = feature.getPosition();
                        scores[pos - 1] = ((BaseQualityScore) feature).getQualityScore();
                        hasMissingScores = false;
                        break;
                    case ReadBase.operator:
                        pos = feature.getPosition();
                        scores[pos - 1] = ((ReadBase) feature).getQualityScore();
                        hasMissingScores = false;
                        break;
                    default:
                        break;
                }
            }
            qualityScores = hasMissingScores ? SAMRecord.NULL_QUALS : scores;
        } else {
            final byte[] scores = qualityScores;
            int missingScores = 0;
            for (int i = 0; i < scores.length; i++) {
                if (scores[i] == MISSING_QUALITY_SCORE) {
                    scores[i] = DEFAULT_QUALITY_SCORE;
                    missingScores++;
                }
            }
            if (missingScores == scores.length) {
                qualityScores = SAMRecord.NULL_QUALS;
            }
        }
    }

    /**
     * The method is similar in semantics to
     * {@link htsjdk.samtools.SamPairUtil#computeInsertSize(SAMRecord, SAMRecord)
     * computeInsertSize} but operates on CRAM native records instead of
     * SAMRecord objects.
     *
     * @param firstEnd  first mate of the pair
     * @param secondEnd second mate of the pair
     * @return template length
     */
    private static int computeInsertSize(final CRAMCompressionRecord firstEnd, final CRAMCompressionRecord secondEnd) {
        if (firstEnd.isSegmentUnmapped() ||
                secondEnd.isSegmentUnmapped()||
                firstEnd.referenceIndex != secondEnd.referenceIndex) {
            return 0;
        }

        final int firstEnd5PrimePosition = firstEnd.isNegativeStrand() ? firstEnd.getAlignmentEnd() : firstEnd.alignmentStart;
        final int secondEnd5PrimePosition = secondEnd.isNegativeStrand() ? secondEnd.getAlignmentEnd() : secondEnd.alignmentStart;

        final int adjustment = (secondEnd5PrimePosition >= firstEnd5PrimePosition) ? +1 : -1;
        return secondEnd5PrimePosition - firstEnd5PrimePosition + adjustment;
    }

    /**
     *
     * @param referenceBases reference bases for this reference, if one is required (may be null for records with
     *                 RR=false in the compression header)
     * @param zeroBasedReferenceOffset zero-based reference offset of the first base in {@code referenceBases}
     * @param substitutionMatrix substitution matrix
     */
    public void restoreReadBases(
            final byte[] referenceBases,
            final int zeroBasedReferenceOffset,
            final SubstitutionMatrix substitutionMatrix) {
        if (isUnknownBases()) {
            readBases = SAMRecord.NULL_SEQUENCE;
        } else {
            readBases = CRAMRecordReadFeatures.restoreReadBases(
                    readFeatures == null ?
                            Collections.EMPTY_LIST :
                            readFeatures.getReadFeaturesList(),
                    isUnknownBases(),
                    alignmentStart,
                    readLength,
                    referenceBases,
                    zeroBasedReferenceOffset,
                    substitutionMatrix);
        }
    }

    //////////////////////////////////////
    // Start Mate code
    //////////////////////////////////////
    public void restoreMateInfo() {
        if (getNextSegment() == null) {
            return;
        }
        CRAMCompressionRecord cur = this;
        while (cur.getNextSegment() != null) {
            cur.setNextMate(cur.getNextSegment());
            cur = cur.getNextSegment();
        }

        // cur points to the last segment now:
        final CRAMCompressionRecord last = cur;
        last.setNextMate(this);

        final int templateLength = computeInsertSize(this, last);
        templateSize = templateLength;
        last.templateSize = -templateLength;
    }

    public void setToDetachedState() {
        setDetached(true);
        setHasMateDownStream(false);
        recordsToNextFragment = -1;
    }

    private void setNextMate(final CRAMCompressionRecord next) {
        mateAlignmentStart = next.alignmentStart;
        setMateUnmapped(next.isSegmentUnmapped());
        setMateNegativeStrand(next.isNegativeStrand());
        mateReferenceIndex = next.referenceIndex;
        if (mateReferenceIndex == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            mateAlignmentStart = SAMRecord.NO_ALIGNMENT_START;
        }
    }

    //////////////////////////////////////
    // End mate code
    //////////////////////////////////////

    /**
     * Determine is read is "placed". For consistency with the rest of htsjdk, this only consults the
     * alignmentStart (placed reads should also have a valid reference index, and unplaced reads should
     * be unmapped; those two abberant conditions are logged as warnings).
     * <p>
     *
     * @return true if the record is placed
     * @see #isSegmentUnmapped()
     */
    public boolean isPlaced() {
        // to be consistent with the rest of htsjdk, and with the BAM indexing code,
        // we use only the alignmentStart to determine if a record is placed or not,
        // though technically it is possible the read does not have a valid reference index
        boolean placed = alignmentStart != SAMRecord.NO_ALIGNMENT_START;
        if (placed) {
            if (referenceIndex == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                final String warning = String.format(
                        "CRAMRecord [%s] has an valid alignment start but not a valid reference index.",
                        this.toString());
                log.warn(warning);
            }
        } else if (!isSegmentUnmapped()) {
            final String warning = String.format(
                    "CRAMRecord [%s] appears to be mapped but does not have a valid alignment start.",
                    this.toString());
            log.warn(warning);
        }

        return placed;
    }

    public String getReadName() { return readName; }

    public int getAlignmentStart() { return alignmentStart; }

    public int getReadLength() { return readLength; }

    public byte[] getReadBases() { return readBases; }

    public byte[] getQualityScores() { return qualityScores; }

    public int getMappingQuality() { return mappingQuality; }

    public int getReferenceIndex() { return referenceIndex; }

    public int getTemplateSize() { return templateSize; }

    public List<ReadTag> getTags() { return tags; }

    public int getRecordsToNextFragment() { return recordsToNextFragment; }

    public List<ReadFeature> getReadFeatures() {
        return readFeatures == null ?
                null :
                readFeatures.getReadFeaturesList();
    }

    /**
     * @return read group id, or {@link #NO_READGROUP_ID} if no read group assigned
     */
    public int getReadGroupID() { return readGroupID; }

    public int getBAMFlags() { return bamFlags; }

    public int getMateReferenceIndex() { return mateReferenceIndex; }

    public int getMateAlignmentStart() { return mateAlignmentStart; }

    public void setTagIdsIndex(MutableInt tagIdsIndex) {
        //TODO: why is this value deliberately shared across records
        this.tagIdsIndex = tagIdsIndex;
    }

    public MutableInt getTagIdsIndex() { return tagIdsIndex; }

    public int getMateFlags() { return (0xFF & mateFlags); }

    public int getCRAMFlags() { return (0xFF & cramFlags); }

    /**
     * @return the initialized alignmentEnd
     */
    public int getAlignmentEnd() { return alignmentEnd; }

    // used in read name generation and mate restoration
    public long getSequentialIndex() {
        return sequentialIndex;
    }

    public CRAMCompressionRecord getNextSegment() {
        return nextSegment;
    }

    public void setNextSegment(CRAMCompressionRecord nextSegment) {
        this.nextSegment = nextSegment;
    }

    public CRAMCompressionRecord getPreviousSegment() {
        return previousSegment;
    }

    public void setPreviousSegment(CRAMCompressionRecord previousSegment) {
        this.previousSegment = previousSegment;
    }

    public boolean isSecondaryAlignment() {
        return (bamFlags & SAMFlag.SECONDARY_ALIGNMENT.intValue()) != 0;
    }

    private void setSecondaryAlignment(final boolean secondaryAlignment) {
        bamFlags = secondaryAlignment ?
                bamFlags | SAMFlag.SECONDARY_ALIGNMENT.intValue() :
                bamFlags & ~SAMFlag.SECONDARY_ALIGNMENT.intValue();
    }

    public boolean isHasMateDownStream() {
        return isHasMateDownStream(cramFlags);
    }

    public static boolean isHasMateDownStream(final int cramFlags) {
        return (cramFlags & CF_HAS_MATE_DOWNSTREAM) != 0;
    }

    public boolean isDetached() {
        return isDetached(cramFlags);
    }

    public static boolean isDetached(final int cramFlags) { return (cramFlags & CF_DETACHED) != 0; }

    public boolean isForcePreserveQualityScores() {
        return isForcePreserveQualityScores(cramFlags);
    }

    public static boolean isForcePreserveQualityScores(final int cramFlags) {return (cramFlags & CF_QS_PRESERVED_AS_ARRAY) != 0; }

    public boolean isUnknownBases() {
        return isUnknownBases(cramFlags);
    }

    public static boolean isUnknownBases(final int cramFlags) {
        return (cramFlags & CF_UNKNOWN_BASES) != 0;
    }

    public boolean isReadPaired() {
        return (bamFlags & SAMFlag.READ_PAIRED.intValue()) != 0;
    }

    private void setMultiFragment(final boolean multiFragment) {
        bamFlags = multiFragment ? bamFlags | SAMFlag.READ_PAIRED.intValue() : bamFlags & ~SAMFlag.READ_PAIRED.intValue();
    }

    /**
     * Does this record have the mapped flag set? This is independent of placement/alignment status.
     * Unmapped records may be stored in the same {@link Slice}s and {@link Container}s as mapped
     * records if they are placed.
     *
     * @return true if the record is unmapped
     * @see #isPlaced()
     */
    public boolean isSegmentUnmapped() {
        return isSegmentUnmapped(bamFlags);
    }

    public static boolean isSegmentUnmapped(final int bamFlags) { return (bamFlags & SAMFlag.READ_UNMAPPED.intValue()) != 0; }

    private void setSegmentUnmapped(final boolean segmentUnmapped) {
        bamFlags = segmentUnmapped ? bamFlags | SAMFlag.READ_UNMAPPED.intValue() : bamFlags & ~SAMFlag.READ_UNMAPPED.intValue();
    }

    public boolean isFirstSegment() {
        return (bamFlags & SAMFlag.FIRST_OF_PAIR.intValue()) != 0;
    }

    private void setFirstSegment(final boolean firstSegment) {
        bamFlags = firstSegment ? bamFlags | SAMFlag.FIRST_OF_PAIR.intValue() : bamFlags & ~SAMFlag.FIRST_OF_PAIR.intValue();
    }

    public boolean isLastSegment() {
        return (bamFlags & SAMFlag.SECOND_OF_PAIR.intValue()) != 0;
    }

    private void setLastSegment(final boolean lastSegment) {
        bamFlags = lastSegment ? bamFlags | SAMFlag.SECOND_OF_PAIR.intValue() : bamFlags & ~SAMFlag.SECOND_OF_PAIR.intValue();
    }

    private boolean isVendorFiltered() {
        return (bamFlags & SAMFlag.READ_FAILS_VENDOR_QUALITY_CHECK.intValue()) != 0;
    }

    private void setVendorFiltered(final boolean vendorFiltered) {
        bamFlags = vendorFiltered ? bamFlags | SAMFlag.READ_FAILS_VENDOR_QUALITY_CHECK.intValue() : bamFlags & ~SAMFlag.READ_FAILS_VENDOR_QUALITY_CHECK.intValue();
    }

    private boolean isProperPair() {
        return (bamFlags & SAMFlag.PROPER_PAIR.intValue()) != 0;
    }

    private void setProperPair(final boolean properPair) {
        bamFlags = properPair ? bamFlags | SAMFlag.PROPER_PAIR.intValue() : bamFlags & ~SAMFlag.PROPER_PAIR.intValue();
    }

    private boolean isDuplicate() {
        return (bamFlags & SAMFlag.DUPLICATE_READ.intValue()) != 0;
    }

    private void setDuplicate(final boolean duplicate) {
        bamFlags = duplicate ? bamFlags | SAMFlag.DUPLICATE_READ.intValue() : bamFlags & ~SAMFlag.DUPLICATE_READ.intValue();
    }

    private boolean isNegativeStrand() {
        return (bamFlags & SAMFlag.READ_REVERSE_STRAND.intValue()) != 0;
    }

    private void setNegativeStrand(final boolean negativeStrand) {
        bamFlags = negativeStrand ? bamFlags | SAMFlag.READ_REVERSE_STRAND.intValue() : bamFlags & ~SAMFlag.READ_REVERSE_STRAND.intValue();
    }

    // NOTE: "mate unmapped" and "mate negative strand" (MF_MATE_UNMAPPED and MF_MATE_NEG_STRAND, aka
    // MATE_REVERSE_STRAND and MATE_UNMAPPED in SAMRecord flag space; note that the flag values are different
    // in mateFlags space and samFlags space) are modeled redundantly in the bamFlags and mateFlags fields.
    // The spec is ambiguous about whether the serialized bamFlags field must match the bamFlags field for the
    // underlying record (in particular, the spec says these flags need not be written, and that they can be
    // derived, which implies that the need not be included in the serialized bamFlags field, but that puts
    // the onus on a read implementation to propagate them manually).
    // MF data series flags (defined by the CRAM spec)
    private boolean isMateUnmapped() {
        return (mateFlags & MF_MATE_UNMAPPED) != 0;
    }
    private void setMateUnmapped(final boolean mateUnmapped) {
        mateFlags = mateUnmapped ?
                mateFlags | MF_MATE_UNMAPPED :
                mateFlags & ~MF_MATE_UNMAPPED;
        bamFlags = mateUnmapped ?
                bamFlags | SAMFlag.MATE_UNMAPPED.intValue() :
                bamFlags & ~SAMFlag.MATE_UNMAPPED.intValue();
    }
    // Note that this flag is maintained in both the bamFlags and mateFlags; we only test the mate flags here
    private boolean isMateNegativeStrand() {
        return (mateFlags & MF_MATE_NEG_STRAND) != 0;
    }

    // Note that this flag is maintained in both the bamFlags and mateFlags, so we set both here
    private void setMateNegativeStrand(final boolean mateNegativeStrand) {
        mateFlags = mateNegativeStrand ?
                mateFlags | MF_MATE_NEG_STRAND :
                mateFlags & ~MF_MATE_NEG_STRAND;
        bamFlags = mateNegativeStrand ?
                bamFlags | SAMFlag.MATE_REVERSE_STRAND.intValue() :
                bamFlags & ~SAMFlag.MATE_REVERSE_STRAND.intValue();
    }

    private void setHasMateDownStream(final boolean hasMateDownStream) {
        cramFlags = hasMateDownStream ? cramFlags | CF_HAS_MATE_DOWNSTREAM : cramFlags & ~CF_HAS_MATE_DOWNSTREAM;
    }

    public void setDetached(final boolean detached) {
        cramFlags = detached ? cramFlags | CF_DETACHED : cramFlags & ~CF_DETACHED;
    }

   private void setUnknownBases(final boolean unknownBases) {
        cramFlags = unknownBases ? cramFlags | CF_UNKNOWN_BASES : cramFlags & ~CF_UNKNOWN_BASES;
    }

    private boolean isSupplementary() {
        return (bamFlags & SAMFlag.SUPPLEMENTARY_ALIGNMENT.intValue()) != 0;
    }

    private void setSupplementary(final boolean supplementary) {
        bamFlags = supplementary ? bamFlags | SAMFlag.SUPPLEMENTARY_ALIGNMENT.intValue() : bamFlags & ~SAMFlag.SUPPLEMENTARY_ALIGNMENT.intValue();
    }

    private void setForcePreserveQualityScores(final boolean forcePreserveQualityScores) {
        cramFlags = forcePreserveQualityScores ?
                cramFlags | CF_QS_PRESERVED_AS_ARRAY :
                cramFlags & ~CF_QS_PRESERVED_AS_ARRAY;
    }

    private static void copyFlags(final CRAMCompressionRecord cramCompressionRecord, final SAMRecord samRecord) {
        samRecord.setReadPairedFlag(cramCompressionRecord.isReadPaired());
        samRecord.setProperPairFlag(cramCompressionRecord.isProperPair());
        samRecord.setReadUnmappedFlag(cramCompressionRecord.isSegmentUnmapped());
        samRecord.setReadNegativeStrandFlag(cramCompressionRecord.isNegativeStrand());
        samRecord.setFirstOfPairFlag(cramCompressionRecord.isFirstSegment());
        samRecord.setSecondOfPairFlag(cramCompressionRecord.isLastSegment());
        samRecord.setSecondaryAlignment(cramCompressionRecord.isSecondaryAlignment());
        samRecord.setReadFailsVendorQualityCheckFlag(cramCompressionRecord.isVendorFiltered());
        samRecord.setDuplicateReadFlag(cramCompressionRecord.isDuplicate());
        samRecord.setSupplementaryAlignmentFlag(cramCompressionRecord.isSupplementary());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CRAMCompressionRecord that = (CRAMCompressionRecord) o;

        if (getAlignmentStart() != that.getAlignmentStart()) return false;
        if (getReadLength() != that.getReadLength()) return false;
        if (getAlignmentEnd() != that.getAlignmentEnd()) return false;
        if (getReferenceIndex() != that.getReferenceIndex()) return false;
        if (getMappingQuality() != that.getMappingQuality()) return false;
        if (getReadGroupID() != that.getReadGroupID()) return false;
        if (getBAMFlags() != that.getBAMFlags()) return false;
        if (getCRAMFlags() != that.getCRAMFlags()) return false;
        if (getTemplateSize() != that.getTemplateSize()) return false;
        if (getMateFlags() != that.getMateFlags()) return false;
        if (getMateAlignmentStart() != that.getMateAlignmentStart()) return false;
        if (getMateReferenceIndex() != that.getMateReferenceIndex()) return false;
        if (getRecordsToNextFragment() != that.getRecordsToNextFragment()) return false;
        if (getReadFeatures() != null ?
                !getReadFeatures().equals(that.getReadFeatures()) :
                that.getReadFeatures() != null)
            return false;
        if (getTags() != null ? !getTags().equals(that.getTags()) : that.getTags() != null) return false;
        if (getReadName() != null ? !getReadName().equals(that.getReadName()) : that.getReadName() != null)
            return false;
        if (!Arrays.equals(getReadBases(), that.getReadBases())) {
            return false;
        }
        if (!Arrays.equals(getQualityScores(), that.getQualityScores())) return false;
        if (!getTagIdsIndex().equals(that.getTagIdsIndex()))
            return false;
        if (getNextSegment() != null ? !getNextSegment().equals(that.getNextSegment()) : that.getNextSegment() != null)
            return false;
        return getPreviousSegment() != null ? getPreviousSegment().equals(that.getPreviousSegment()) :
                that.getPreviousSegment() == null;
    }

    @Override
    public int hashCode() {
        int result = getAlignmentStart();
        result = 31 * result + getReadLength();
        result = 31 * result + (getReadFeatures() != null ? getReadFeatures().hashCode() : 0);
        result = 31 * result + getAlignmentEnd();
        result = 31 * result + getReferenceIndex();
        result = 31 * result + getMappingQuality();
        result = 31 * result + getReadGroupID();
        result = 31 * result + (getTags() != null ? getTags().hashCode() : 0);
        result = 31 * result + Long.hashCode(getSequentialIndex());
        result = 31 * result + getBAMFlags();
        result = 31 * result + cramFlags;
        result = 31 * result + getTemplateSize();
        result = 31 * result + (getReadName() != null ? getReadName().hashCode() : 0);
        result = 31 * result + Arrays.hashCode(getReadBases());
        result = 31 * result + Arrays.hashCode(getQualityScores());
        result = 31 * result + (getTagIdsIndex() != null ? getTagIdsIndex().hashCode() : 0);
        result = 31 * result + getMateFlags();
        result = 31 * result + getMateAlignmentStart();
        result = 31 * result + getMateReferenceIndex();
        result = 31 * result + getRecordsToNextFragment();
        result = 31 * result + (getNextSegment() != null ? getNextSegment().hashCode() : 0);
        result = 31 * result + (getPreviousSegment() != null ? getPreviousSegment().hashCode() : 0);
        return result;
    }

}
