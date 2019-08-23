package htsjdk.samtools.cram.structure;

import htsjdk.samtools.*;
import htsjdk.samtools.cram.build.Utils;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.MutableInt;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.encoding.readfeatures.*;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.utils.ValidationUtils;

import java.util.*;

//TODO: should this just hold a reference to the SAMRecord and only model the CRAM part ?
//TODO: ...but that wont work correctly when reading these from a stream...
//TODO rename to CRAMCompressionRecord
public class CRAMRecord {
    private static final Log log = Log.getInstance(CRAMRecord.class);

    // CF data series flags (defined by the CRAM spec)
    public static final int CF_FORCE_PRESERVE_QS   = 0x1;  // preserve quality scores
    public static final int CF_DETACHED            = 0x2;  // mate is stored literally vs as record offset
    public static final int CF_HAS_MATE_DOWNSTREAM = 0x4;
    // sequence is unknown; encoded reference differences are present only to recreate the CIGAR string
    public static final int CF_UNKNOWN_BASES       = 0x8;

    // MF data series flags (defined by the CRAM spec)
    private static final int MF_MATE_NEG_STRAND = 0x1;
    private static final int MF_MATE_UNMAPPED   = 0x2;    // same as SAMFlag.MATE_UNMAPPED, but different value

    // SAMRecord fields
    // (TODO: is this consistently 1-based ?) start position of this read, using a 1-based coordinate system
    // TODO: these 4 fields are immutable (fixes
    private final int alignmentStart;
    private final int readLength;
    private final List<ReadFeature> readFeatures;
    private final int alignmentEnd; // derived alignmentStart, readFeatures, and readLength by initializeAlignmentEnd

    private final int referenceIndex;
    private final int mappingQuality;
    private final int readGroupID;
    private final List<ReadTag> tags;

    //TODO: used when indexing records being read from a CRAM stream ?
    public final static int SLICE_INDEX_DEFAULT = -1;
    //TODO: is this zero-based or 1 based ?
    private final int sliceIndex;

    // sequential index of the record in a stream:
    public final static int SEQUENTIAL_INDEX_DEFAULT = -1;
    //TODO: is this zero-based or 1 based ?
    private int sequentialIndex;

    private int bamFlags;
    private int cramFlags;
    private int templateSize;
    private String readName;
    //readBases is never null since the contents hasher doesnt handle nulls; its always at least SAMRecord.NULL_SEQUENCE (byte[0])
    private byte[] readBases;
    private byte[] qualityScores;
    private MutableInt tagIdsIndex = new MutableInt(0);

    //TODO: abstract into mate info
    private int mateFlags;
    private int mateAlignmentStart;
    private int mateReferenceIndex;
    private int recordsToNextFragment = -1;
    private CRAMRecord nextSegment = null;
    private CRAMRecord previousSegment = null;

    /**
     * Create a CRAMRecord from a SAMRecord.
     *
     * @param cramVersion
     * @param encodingStrategy
     * @param samRecord
     * @param refBases
     * @param sequentialIndex
     * @param readGroupMap
     */
    public CRAMRecord(
            final Version cramVersion,
            final CRAMEncodingStrategy encodingStrategy,
            final SAMRecord samRecord,
            final byte[] refBases,
            final int sequentialIndex,
            final Map<String, Integer> readGroupMap) {
        ValidationUtils.nonNull(cramVersion);
        ValidationUtils.nonNull(encodingStrategy);
        ValidationUtils.nonNull(samRecord, "samRecord must have a valid header");
        ValidationUtils.nonNull(samRecord.getHeader(), "samRecord must have a valid header");
        ValidationUtils.nonNull(refBases);
        ValidationUtils.validateArg(sequentialIndex > SEQUENTIAL_INDEX_DEFAULT, "must have a valid sequential index");
        ValidationUtils.nonNull(readGroupMap);
        ValidationUtils.validateArg(sequentialIndex >= 0, "index must be >= 0");

        //TODO: delegate to the other constructor ?
        //TODO: is sliceIndex correct ? does it get reset later?
        sliceIndex = SLICE_INDEX_DEFAULT;
        this.sequentialIndex = sequentialIndex;

        // default to detached state until the actual mate information state is resolved during mate
        // matching after all the CRAMRecords for a the containing container are known
        setToDetachedState();

        // flags
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

        readName = encodingStrategy.getPreserveReadNames() ? samRecord.getReadName() : null;
        referenceIndex = samRecord.getReferenceIndex();

        //TODO: These 3 values need to mutate together; if readLength, alignmentStart, or readFeatures change,
        // then alignmentEnd needs to be recalculated
        readLength = samRecord.getReadLength();
        alignmentStart = samRecord.getAlignmentStart();
        readFeatures = samRecord.getReadUnmappedFlag() || (samRecord.getAlignmentStart() == SAMRecord.NO_ALIGNMENT_START) ?
                null :
                createReadFeatures(samRecord, refBases);
        alignmentEnd = isPlaced() ?
                initializeAlignmentEnd(alignmentStart, readLength, readFeatures ) :
                Slice.NO_ALIGNMENT_END;

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

        // CRAM read bases are limited to ACGTN, see https://github.com/samtools/hts-specs/blob/master/CRAMv3.pdf
        // passage 10.2 on read bases. However, BAM format allows upper case IUPAC codes without a dot, so we
        // follow the same approach to reproduce the behaviour of samtools.
        // copy read bases to avoid changing the original record:
        final byte[] bases = samRecord.getReadBases();
        readBases = bases == null ?
                    SAMRecord.NULL_SEQUENCE :
                    SequenceUtil.toBamReadBasesInPlace(Arrays.copyOf(bases, samRecord.getReadLength()));
        //TODO: do we need to retain this if (drop writing 2.1 /
        if (cramVersion.compatibleWith(CramVersions.CRAM_v3)) {
            setUnknownBases(samRecord.getReadBases().equals(SAMRecord.NULL_SEQUENCE));
        }

        final byte[] qualScores = samRecord.getBaseQualities();
        qualityScores = qualScores == null ? SAMRecord.NULL_QUALS: samRecord.getBaseQualities();
        if (!qualityScores.equals(SAMRecord.NULL_QUALS)) {
            setForcePreserveQualityScores(true);
        }

        final SAMReadGroupRecord readGroup = samRecord.getReadGroup();
        readGroupID = readGroup == null ? -1 : readGroupMap.get(readGroup.getId());

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

    ////////////////////////////////////////////////////////////////////////////////////////

    public CRAMRecord(
            final int sliceIndex,
            final int sequentialIndex,
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
            final List<ReadFeature>readFeatures,
            final int readGroupID,
            final int mateFlags,
            final int mateReferenceIndex,
            final int mateAlignmentStart,
            final int recordsToNextFragment) {
        //ValidationUtils.validateArg(sliceIndex > SLICE_INDEX_DEFAULT, "must have a valid slice index");
        ValidationUtils.nonNull( qualityScores,"quality scores argument must be null or nonzero length");
        ValidationUtils.nonNull(readBases,"read bases argument cannot be null");
        ValidationUtils.validateArg(readTags == null || readTags.size() > 0, "invalid read tag argument");
        ValidationUtils.validateArg(readFeatures == null || readFeatures.size() > 0, "invalid read features argument");
        ValidationUtils.validateArg(sequentialIndex >= 0, "index must be >= 0");

        this.sliceIndex = sliceIndex;
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
        this.readFeatures = readFeatures;
        this.readGroupID = readGroupID;
        this.mateFlags = mateFlags;
        this.mateReferenceIndex = mateReferenceIndex;
        this.mateAlignmentStart = mateAlignmentStart;
        this.recordsToNextFragment = recordsToNextFragment;
        alignmentEnd = isPlaced() ?
                initializeAlignmentEnd(alignmentStart, readLength, readFeatures ) :
                Slice.NO_ALIGNMENT_END;
    }

    public SAMRecord toSAMRecord(final SAMFileHeader header) {
        final SAMRecord samRecord = new SAMRecord(header);

        samRecord.setReadName(readName);
        copyFlags(this, samRecord);

        if (referenceIndex == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            samRecord.setAlignmentStart(SAMRecord.NO_ALIGNMENT_START);
            samRecord.setMappingQuality(SAMRecord.NO_MAPPING_QUALITY);
            samRecord.setReferenceIndex(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
        } else {
            samRecord.setReferenceIndex(referenceIndex);
            samRecord.setAlignmentStart(alignmentStart);
            samRecord.setMappingQuality(mappingQuality);
        }

        if (isSegmentUnmapped())
            samRecord.setCigarString(SAMRecord.NO_ALIGNMENT_CIGAR);
        else
            samRecord.setCigar(getCigar2(readFeatures, readLength));

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

        if (tags != null)
            for (final ReadTag tag : tags)
                samRecord.setAttribute(tag.getKey(), tag.getValue());

        if (readGroupID > -1) {
            final SAMReadGroupRecord readGroupRecord = header.getReadGroups().get(readGroupID);
            samRecord.setAttribute("RG", readGroupRecord.getId());
        }

        return samRecord;
    }

//TODO: factor out ReadFeatures and Mate code ?
    private static List<ReadFeature> createReadFeatures(final SAMRecord samRecord, final byte[] refBases) {
        final List<ReadFeature> features = new LinkedList<>();
        final List<CigarElement> cigarElements = samRecord.getCigar().getCigarElements();

        int cigarLen = 0;
        for (final CigarElement cigarElement : cigarElements) {
            if (cigarElement.getOperator().consumesReadBases()) {
                cigarLen += cigarElement.getLength();
            }
        }

        byte[] bases = samRecord.getReadBases();
        if (bases.length == 0) {
            bases = new byte[cigarLen];
            Arrays.fill(bases, (byte) 'N');
        }

        final byte[] qualityScore = samRecord.getBaseQualities();
        int zeroBasedPositionInRead = 0;
        int alignmentStartOffset = 0;
        for (final CigarElement cigarElement : cigarElements) {
            int cigarElementLength = cigarElement.getLength();
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
                    addMismatchReadFeatures(
                            refBases,
                            samRecord.getAlignmentStart(),
                            features,
                            zeroBasedPositionInRead,
                            alignmentStartOffset,
                            cigarElementLength,
                            bases,
                            qualityScore);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported cigar operator: " + cigarElement.getOperator());
            }

            if (cigarElement.getOperator().consumesReadBases()) {
                zeroBasedPositionInRead += cigarElementLength;
            }
            if (cigarElement.getOperator().consumesReferenceBases()) {
                alignmentStartOffset += cigarElementLength;
            }
        }

        //used in Sam2CramRecordFactory for sequentialIndex error reporting
        //this.baseCount += bases.length;
        //this.featureCount += features.size();

        return features.size() == 0 ? null : features;
    }

    private static void addSoftClip(final List<ReadFeature> features, final int zeroBasedPositionInRead, final int cigarElementLength, final byte[] bases) {
        final byte[] insertedBases = Arrays.copyOfRange(bases, zeroBasedPositionInRead, zeroBasedPositionInRead + cigarElementLength);
        features.add(new SoftClip(zeroBasedPositionInRead + 1, insertedBases));
    }

    //TODO: why is this unused ?
    private void addHardClip(final List<ReadFeature> features, final int zeroBasedPositionInRead, final int cigarElementLength, final byte[] bases) {
        final byte[] insertedBases = Arrays.copyOfRange(bases, zeroBasedPositionInRead, zeroBasedPositionInRead + cigarElementLength);
        features.add(new HardClip(zeroBasedPositionInRead + 1, insertedBases.length));
    }

    private static void addInsertion(final List<ReadFeature> features, final int zeroBasedPositionInRead, final int cigarElementLength, final byte[] bases) {
        final byte[] insertedBases = Arrays.copyOfRange(bases, zeroBasedPositionInRead, zeroBasedPositionInRead + cigarElementLength);
        for (int i = 0; i < insertedBases.length; i++) {
            // single base insertion:
            final InsertBase insertBase = new InsertBase();
            insertBase.setPosition(zeroBasedPositionInRead + 1 + i);
            insertBase.setBase(insertedBases[i]);
            features.add(insertBase);
        }
    }

    /**
     * Processes a stretch of read bases marked as match or mismatch and emits appropriate read features.
     * Briefly the algorithm is:
     * <ul><li>emit nothing for a read base matching corresponding reference base.</li>
     * <li>emit a {@link Substitution} read feature for each ACTGN-ACTGN mismatch.</li>
     * <li>emit {@link ReadBase} for a non-ACTGN mismatch. The side effect is the quality score stored twice.</li>
     * <p>
     * IMPORTANT: reference and read bases are always compared for match/mismatch in upper case due to BAM limitations.
     *
     * @param alignmentStart       CRAM record alignment start
     * @param features             a list of read features to add to
     * @param fromPosInRead        a zero based position in the read to start with
     * @param alignmentStartOffset offset into the reference array
     * @param nofReadBases         how many read bases to process
     * @param bases                the read bases array
     * @param qualityScore         the quality score array
     */
    static void addMismatchReadFeatures(
            final byte[] refBases,
            final int alignmentStart,
            final List<ReadFeature> features,
            final int fromPosInRead,
            final int alignmentStartOffset,
            final int nofReadBases,
            final byte[] bases,
            final byte[] qualityScore) {
        int oneBasedPositionInRead = fromPosInRead + 1;
        int refIndex = alignmentStart + alignmentStartOffset - 1;

        byte refBase;
        for (int i = 0; i < nofReadBases; i++, oneBasedPositionInRead++, refIndex++) {
            if (refIndex >= refBases.length) refBase = 'N';
            else refBase = refBases[refIndex];

            final byte readBase = bases[i + fromPosInRead];

            if (readBase != refBase) {
                final boolean isSubstitution = SequenceUtil.isUpperACGTN(readBase) && SequenceUtil.isUpperACGTN(refBase);
                if (isSubstitution) {
                    features.add(new Substitution(oneBasedPositionInRead, readBase, refBase));
                } else {
                    final byte score = qualityScore[i + fromPosInRead];
                    features.add(new ReadBase(oneBasedPositionInRead, readBase, score));
                }
            }
        }
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

    public List<ReadFeature> getReadFeatures() { return readFeatures; }

    public int getReadGroupID() { return readGroupID; }

    public int getBamFlags() { return bamFlags; }

    public int getMateReferenceIndex() { return mateReferenceIndex; }

    public int getMateAlignmentStart() { return mateAlignmentStart; }

    public void setTagIdsIndex(MutableInt tagIdsIndex) {
        //TODO: why does this value appear to be deliberately shared across records
        this.tagIdsIndex = tagIdsIndex;
    }

    public MutableInt getTagIdsIndex() { return tagIdsIndex; }

    public int getMateFlags() { return (0xFF & mateFlags); }

    public int getCRAMFlags() { return (0xFF & cramFlags); }

    /**
     * @return the initialized alignmentEnd
     */
    public int getAlignmentEnd() { return alignmentEnd; }

    public void setSequentialIndex(int sequentialIndex) {
        if (this.sequentialIndex != sequentialIndex) {
            // TODO: if this ever gets hit then we do need to reset the index in CRAMNormalizer
            // happens all the time...
            //throw new IllegalArgumentException("Note to self: setting sequential index to a new value");
        }
        this.sequentialIndex = sequentialIndex;
    }

    //TODO: used in read name generation and mate restoration
    public int getSequentialIndex() {
        return sequentialIndex;
    }

    public int getSliceIndex() { return sliceIndex; }

    public void assignReadName() {
        if (readName == null) {
            final String readNamePrefix = "";
            final String name = readNamePrefix + getSequentialIndex();
            readName = name;
            if (nextSegment != null)
                nextSegment.readName = name;
            if (previousSegment != null)
                previousSegment.readName = name;
        }

    }
    public CRAMRecord getNextSegment() {
        return nextSegment;
    }

    public void setNextSegment(CRAMRecord nextSegment) {
        this.nextSegment = nextSegment;
    }

    public CRAMRecord getPreviousSegment() {
        return previousSegment;
    }

    public void setPreviousSegment(CRAMRecord previousSegment) {
        this.previousSegment = previousSegment;
    }

    // https://github.com/samtools/htsjdk/issues/1301
    // does not update alignmentSpan/alignmentEnd when the record changes
    private static int initializeAlignmentEnd(int alignmentStart, int readLength, final List<ReadFeature> readFeatures) {
        int alignmentSpan = readLength;
        if (readFeatures != null) {
            for (final ReadFeature readFeature : readFeatures) {
                switch (readFeature.getOperator()) {
                    case InsertBase.operator:
                        alignmentSpan--;
                        break;
                    case Insertion.operator:
                        alignmentSpan -= ((Insertion) readFeature).getSequence().length;
                        break;
                    case SoftClip.operator:
                        alignmentSpan -= ((SoftClip) readFeature).getSequence().length;
                        break;
                    case Deletion.operator:
                        alignmentSpan += ((Deletion) readFeature).getLength();
                        break;

                    default:
                        break;
                }
            }
        }

        return alignmentStart + alignmentSpan - 1;
    }

    /**
     * Does this record have a valid placement/alignment location?
     * <p>
     * It must have a valid reference sequence ID and a valid alignment start position
     * to be considered placed.
     * <p>
     * Normally we expect to see that the unmapped flag is set for unplaced reads,
     * so we log a WARNING here if the read is unplaced yet somehow mapped.
     *
     * @return true if the record is placed
     * @see #isSegmentUnmapped()
     */
    public boolean isPlaced() {
        // placement requires a valid sequence ID and alignment start coordinate
        boolean placed = referenceIndex != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX &&
                alignmentStart != SAMRecord.NO_ALIGNMENT_START;

        if (!placed && !isSegmentUnmapped()) {
            final String warning = String.format(
                    "Cram Compression Record [%s] does not have the unmapped flag set, " +
                            "but also does not have a valid placement on a reference sequence.",
                    this.toString());
            log.warn(warning);
        }

        return placed;
    }

    public void setToDetachedState() {
        setDetached(true);
        setHasMateDownStream(false);
        recordsToNextFragment = -1;
    }

    public void attachToMate(final CRAMRecord r) {
        // go back to the beginning of the graph...
        CRAMRecord prev = this;
        while (prev.nextSegment != null) {
            prev = prev.nextSegment;
        }
        prev.recordsToNextFragment = r.sequentialIndex - prev.sequentialIndex - 1;
        prev.nextSegment = r;
        r.previousSegment = prev;
        r.previousSegment.setHasMateDownStream(true);
        r.setHasMateDownStream(false);
        r.setDetached(false);
        r.previousSegment.setDetached(false);
    }

    /**
     * Traverse the graph and mark all segments as detached.
     */
    private void detachAllMateFragments() {
        CRAMRecord cramRecord = this;
        do {
            cramRecord.setToDetachedState();
        }
        while ((cramRecord = cramRecord.nextSegment) != null);
    }

    public void updateDetachedState() {
        if (nextSegment == null || previousSegment != null) {
            return;
        }
        CRAMRecord last = this;
        while (last.nextSegment != null) {
            last = last.nextSegment;
        }

        if (isFirstSegment() && last.isLastSegment()) {
            final int templateLength = computeInsertSize(this, last);

            if (templateSize == templateLength) {
                last = nextSegment;
                while (last.nextSegment != null) {
                    if (last.templateSize != -templateLength)
                        break;

                    last = last.nextSegment;
                }
                if (last.templateSize != -templateLength) {
                    detachAllMateFragments();
                }
            } else {
                detachAllMateFragments();
            }
        } else {
            detachAllMateFragments();
        }
    }

    public boolean isSecondaryAlignment() {
        return (bamFlags & SAMFlag.SECONDARY_ALIGNMENT.intValue()) != 0;
    }

    private void setSecondaryAlignment(final boolean secondaryAlignment) {
        bamFlags = secondaryAlignment ? bamFlags | SAMFlag.SECONDARY_ALIGNMENT.intValue() : bamFlags & ~SAMFlag.SECONDARY_ALIGNMENT.intValue();
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

    public static boolean isForcePreserveQualityScores(final int cramFlags) {return (cramFlags & CF_FORCE_PRESERVE_QS) != 0; }

    public boolean isUnknownBases() {
        return isUnknownBases(cramFlags);
    }

    public static boolean isUnknownBases(final int cramFlags) {
        return (cramFlags & CF_UNKNOWN_BASES) != 0;
    }

    public boolean isMultiFragment() {
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

    //TODO: this should reset alignment start and
    private void setSegmentUnmapped(final boolean segmentUnmapped) {
        bamFlags = segmentUnmapped ? bamFlags | SAMFlag.READ_UNMAPPED.intValue() : bamFlags & ~SAMFlag.READ_UNMAPPED.intValue();
    }

    private boolean isFirstSegment() {
        return (bamFlags & SAMFlag.FIRST_OF_PAIR.intValue()) != 0;
    }

    private void setFirstSegment(final boolean firstSegment) {
        bamFlags = firstSegment ? bamFlags | SAMFlag.FIRST_OF_PAIR.intValue() : bamFlags & ~SAMFlag.FIRST_OF_PAIR.intValue();
    }

    private boolean isLastSegment() {
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

    private boolean isMateUnmapped() {
        return (mateFlags & MF_MATE_UNMAPPED) != 0;
    }

    private void setMateUnmapped(final boolean mateUnmapped) {
        mateFlags = mateUnmapped ? mateFlags | MF_MATE_UNMAPPED : mateFlags & ~MF_MATE_UNMAPPED;
    }

    private boolean isMateNegativeStrand() {
        return (mateFlags & MF_MATE_NEG_STRAND) != 0;
    }

    private void setMateNegativeStrand(final boolean mateNegativeStrand) {
        mateFlags = mateNegativeStrand ? mateFlags | MF_MATE_NEG_STRAND : mateFlags & ~MF_MATE_NEG_STRAND;
    }

    private void setHasMateDownStream(final boolean hasMateDownStream) {
        cramFlags = hasMateDownStream ? cramFlags | CF_HAS_MATE_DOWNSTREAM : cramFlags & ~CF_HAS_MATE_DOWNSTREAM;
    }

    private void setDetached(final boolean detached) {
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
    private static int computeInsertSize(final CRAMRecord firstEnd, final CRAMRecord secondEnd) {
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

    private void setForcePreserveQualityScores(final boolean forcePreserveQualityScores) {
        cramFlags = forcePreserveQualityScores ?
                cramFlags | CF_FORCE_PRESERVE_QS :
                cramFlags & ~CF_FORCE_PRESERVE_QS;
    }

    private static void copyFlags(final CRAMRecord cramRecord, final SAMRecord samRecord) {
        samRecord.setReadPairedFlag(cramRecord.isMultiFragment());
        samRecord.setProperPairFlag(cramRecord.isProperPair());
        samRecord.setReadUnmappedFlag(cramRecord.isSegmentUnmapped());
        samRecord.setReadNegativeStrandFlag(cramRecord.isNegativeStrand());
        samRecord.setFirstOfPairFlag(cramRecord.isFirstSegment());
        samRecord.setSecondOfPairFlag(cramRecord.isLastSegment());
        samRecord.setSecondaryAlignment(cramRecord.isSecondaryAlignment());
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

    public void restoreMateInfo() {
        if (getNextSegment() == null) {
            return;
        }
        CRAMRecord cur;
        cur = this;
        while (cur.getNextSegment() != null) {
            cur.setNextMate(cur.getNextSegment());
            cur = cur.getNextSegment();
        }

        // cur points to the last segment now:
        final CRAMRecord last = cur;
        last.setNextMate(this);
        //record.setFirstSegment(true);
        //last.setLastSegment(true);

        final int templateLength = computeInsertSize(this, last);
        templateSize = templateLength;
        last.templateSize = -templateLength;
    }

    private void setNextMate(final CRAMRecord next) {
        mateAlignmentStart = next.alignmentStart;
        setMateUnmapped(next.isSegmentUnmapped());
        setMateNegativeStrand(next.isNegativeStrand());
        mateReferenceIndex = next.referenceIndex;
        if (mateReferenceIndex == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            mateAlignmentStart = SAMRecord.NO_ALIGNMENT_START;
        }
    }

    public static void restoreQualityScores(final byte defaultQualityScore, final List<CRAMRecord> records) {
        for (final CRAMRecord record : records) {
            if (!record.isForcePreserveQualityScores()) {
                boolean star = true;
                final byte[] scores = new byte[record.readLength];
                Arrays.fill(scores, defaultQualityScore);
                if (record.readFeatures != null)
                    for (final ReadFeature feature : record.readFeatures) {
                        switch (feature.getOperator()) {
                            case BaseQualityScore.operator:
                                int pos = feature.getPosition();
                                scores[pos - 1] = ((BaseQualityScore) feature).getQualityScore();
                                star = false;
                                break;
                            case ReadBase.operator:
                                pos = feature.getPosition();
                                scores[pos - 1] = ((ReadBase) feature).getQualityScore();
                                star = false;
                                break;

                            default:
                                break;
                        }
                    }

                if (star) {
                    record.qualityScores = SAMRecord.NULL_QUALS;
                } else {
                    record.qualityScores = scores;
                }
            } else {
                final byte[] scores = record.qualityScores;
                int missingScores = 0;
                for (int i = 0; i < scores.length; i++) {
                    if (scores[i] == -1) {
                        scores[i] = defaultQualityScore;
                        missingScores++;
                    }
                }
                if (missingScores == scores.length) {
                    record.qualityScores = SAMRecord.NULL_QUALS;
                }
            }
        }
    }

    public void establishReadBases(
           final byte[] refBases,
           final int refOffset_zeroBased,
           final SubstitutionMatrix substitutionMatrix) {
        if (isUnknownBases()) {
            readBases = SAMRecord.NULL_SEQUENCE;
        } else {
            readBases = restoreReadBases(refBases, refOffset_zeroBased, substitutionMatrix);
        }
    }

    private byte[] restoreReadBases(
            final byte[] ref,
            final int refOffsetZeroBased,
            final SubstitutionMatrix substitutionMatrix) {
        if (isUnknownBases() || readLength == 0) {
            return SAMRecord.NULL_SEQUENCE;
        }
        final byte[] bases = new byte[readLength];

        int posInRead = 1;
        final int alignmentStart = this.alignmentStart - 1;

        int posInSeq = 0;
        if (readFeatures == null || readFeatures.isEmpty()) {
            if (ref.length + refOffsetZeroBased < alignmentStart
                    + bases.length) {
                Arrays.fill(bases, (byte) 'N');
                System.arraycopy(
                        ref,
                        alignmentStart - refOffsetZeroBased,
                        bases,
                        0,
                        Math.min(bases.length, ref.length + refOffsetZeroBased
                                - alignmentStart));
            } else
                System.arraycopy(ref, alignmentStart - refOffsetZeroBased,
                        bases, 0, bases.length);

            return SequenceUtil.toBamReadBasesInPlace(bases);
        }

        final List<ReadFeature> variations = readFeatures;
        for (final ReadFeature variation : variations) {
            for (; posInRead < variation.getPosition(); posInRead++) {
                final int rp = alignmentStart + posInSeq++ - refOffsetZeroBased;
                bases[posInRead - 1] = getByteOrDefault(ref, rp, (byte) 'N');
            }

            switch (variation.getOperator()) {
                case Substitution.operator:
                    final Substitution substitution = (Substitution) variation;
                    byte refBase = getByteOrDefault(ref, alignmentStart + posInSeq
                            - refOffsetZeroBased, (byte) 'N');
                    // substitution requires ACGTN only:
                    refBase = Utils.normalizeBase(refBase);
                    final byte base = substitutionMatrix.base(refBase, substitution.getCode());
                    substitution.setBase(base);
                    substitution.setReferenceBase(refBase);
                    bases[posInRead++ - 1] = base;
                    posInSeq++;
                    break;
                case Insertion.operator:
                    final Insertion insertion = (Insertion) variation;
                    for (int i = 0; i < insertion.getSequence().length; i++)
                        bases[posInRead++ - 1] = insertion.getSequence()[i];
                    break;
                case SoftClip.operator:
                    final SoftClip softClip = (SoftClip) variation;
                    for (int i = 0; i < softClip.getSequence().length; i++)
                        bases[posInRead++ - 1] = softClip.getSequence()[i];
                    break;
                case Deletion.operator:
                    final Deletion deletion = (Deletion) variation;
                    posInSeq += deletion.getLength();
                    break;
                case InsertBase.operator:
                    final InsertBase insert = (InsertBase) variation;
                    bases[posInRead++ - 1] = insert.getBase();
                    break;
                case RefSkip.operator:
                    posInSeq += ((RefSkip) variation).getLength();
                    break;
            }
        }

        for (; posInRead <= readLength
                && alignmentStart + posInSeq - refOffsetZeroBased < ref.length; posInRead++, posInSeq++) {
            bases[posInRead - 1] = ref[alignmentStart + posInSeq
                    - refOffsetZeroBased];
        }

        // ReadBase overwrites bases:
        for (final ReadFeature variation : variations) {
            switch (variation.getOperator()) {
                case ReadBase.operator:
                    final ReadBase readBase = (ReadBase) variation;
                    bases[variation.getPosition() - 1] = readBase.getBase();
                    break;
                default:
                    break;
            }
        }

        return SequenceUtil.toBamReadBasesInPlace(bases);
    }

    //TODO: should sliceIndex be required to match ?
    //if (getSliceIndex() != that.getSliceIndex()) return false;
    //TODO: should sequentialIndex be required to match ?
    //if (getSequentialIndex() != that.getSequentialIndex()) return false;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CRAMRecord that = (CRAMRecord) o;

        if (getAlignmentStart() != that.getAlignmentStart()) return false;
        if (getReadLength() != that.getReadLength()) return false;
        if (getAlignmentEnd() != that.getAlignmentEnd()) return false;
        if (getReferenceIndex() != that.getReferenceIndex()) return false;
        if (getMappingQuality() != that.getMappingQuality()) return false;
        if (getReadGroupID() != that.getReadGroupID()) return false;
        //TODO: should sliceIndex be required to match ?
        //if (getSliceIndex() != that.getSliceIndex()) return false;
        //TODO: should sequentialIndex be required to match ?
        //if (getSequentialIndex() != that.getSequentialIndex()) return false;
        if (getBamFlags() != that.getBamFlags()) return false;
        if (cramFlags != that.cramFlags) return false;
        //if (getTemplateSize() != that.getTemplateSize()) return false;
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
        result = 31 * result + getSliceIndex();
        result = 31 * result + getSequentialIndex();
        result = 31 * result + getBamFlags();
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

    private static byte getByteOrDefault(final byte[] array, final int pos, final byte outOfBoundsValue) {
        return pos >= array.length ?
                outOfBoundsValue :
                array[pos];
    }



}
