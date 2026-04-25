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

import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.build.CRAMReferenceRegion;
import htsjdk.samtools.cram.build.Utils;
import htsjdk.samtools.cram.encoding.readfeatures.*;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.utils.ValidationUtils;
import java.util.*;

/**
 * Class for handling the read features for a {@link CRAMCompressionRecord}.
 */
public class CRAMRecordReadFeatures {
    private static final byte[] BAM_READ_BASE_LOOKUP = SequenceUtil.getBamReadBaseLookup();

    final List<ReadFeature> readFeatures;

    /**
     * Create a CRAMRecordReadFeatures with no actual read features (i.e. an unmapped record).
     */
    public CRAMRecordReadFeatures() {
        this(Collections.EMPTY_LIST);
    }

    /**
     * Create a CRAMRecordReadFeatures from a list of read features consumed from a stream.
     * @param readFeatures
     */
    public CRAMRecordReadFeatures(final List<ReadFeature> readFeatures) {
        ValidationUtils.nonNull(readFeatures);
        this.readFeatures = readFeatures;
    }

    /**
     * Create the read features for a given SAMRecord.
     * @param samRecord the {@link SAMRecord} for which to create read features
     * @param bamReadBases a modifiable copy of the readbases from the original SAM/BAM record, with the individual
     *                     bases mapped to BAM bases (upper case)
     * @param refBases the reference bases for the entire reference contig to which this record is mapped
     */
    public CRAMRecordReadFeatures(final SAMRecord samRecord, final byte[] bamReadBases, final byte[] refBases) {
        readFeatures = new ArrayList<>();
        final List<CigarElement> cigarElements = samRecord.getCigar().getCigarElements();
        int cigarLen = Cigar.getReadLength(cigarElements);

        byte[] readBases = bamReadBases;
        if (readBases.length == 0) {
            // for SAMRecords with SEQ="*", manufacture 'N's
            readBases = new byte[cigarLen];
            Arrays.fill(readBases, (byte) 'N');
        }

        final byte[] baseQualities = samRecord.getBaseQualities();
        int zeroBasedPositionInRead = 0;
        int alignmentStartOffset = 0;
        for (final CigarElement cigarElement : cigarElements) {
            final int cigarElementLength = cigarElement.getLength();
            final CigarOperator cigarOperator = cigarElement.getOperator();
            switch (cigarOperator) {
                case D:
                    readFeatures.add(new Deletion(zeroBasedPositionInRead + 1, cigarElementLength));
                    break;
                case N:
                    readFeatures.add(new RefSkip(zeroBasedPositionInRead + 1, cigarElementLength));
                    break;
                case P:
                    readFeatures.add(new Padding(zeroBasedPositionInRead + 1, cigarElementLength));
                    break;
                case H:
                    readFeatures.add(new HardClip(zeroBasedPositionInRead + 1, cigarElementLength));
                    break;
                case S:
                    addSoftClip(zeroBasedPositionInRead, cigarElementLength, readBases);
                    break;
                case I:
                    addInsertion(zeroBasedPositionInRead, cigarElementLength, readBases);
                    break;
                case M:
                case X:
                case EQ:
                    addMismatchReadFeatures(
                            refBases,
                            samRecord.getAlignmentStart(),
                            readFeatures,
                            zeroBasedPositionInRead,
                            alignmentStartOffset,
                            cigarElementLength,
                            readBases,
                            baseQualities);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported cigar operator: " + cigarElement.getOperator());
            }

            if (cigarOperator.consumesReadBases()) {
                zeroBasedPositionInRead += cigarElementLength;
            }
            if (cigarOperator.consumesReferenceBases()) {
                alignmentStartOffset += cigarElementLength;
            }
        }
    }

    /** Return the list of read features for this record. */
    public final List<ReadFeature> getReadFeaturesList() {
        return readFeatures;
    }

    private void addSoftClip(final int zeroBasedPositionInRead, final int cigarElementLength, final byte[] readBases) {
        final byte[] insertedBases =
                Arrays.copyOfRange(readBases, zeroBasedPositionInRead, zeroBasedPositionInRead + cigarElementLength);
        readFeatures.add(new SoftClip(zeroBasedPositionInRead + 1, insertedBases));
    }

    private void addInsertion(final int zeroBasedPositionInRead, final int cigarElementLength, final byte[] readBases) {
        final byte[] insertedBases =
                Arrays.copyOfRange(readBases, zeroBasedPositionInRead, zeroBasedPositionInRead + cigarElementLength);
        for (int i = 0; i < insertedBases.length; i++) {
            // Note: when cigarElementLength > 1, this should use a Bases read feature instead of using n
            // InsertBases read features, but doing so require a ByteArrayLenEncoding, which requires
            // a length subencoding with varying lengths, which in turn requires computing a frequency
            // distribution over the lengths
            final InsertBase insertBase = new InsertBase(zeroBasedPositionInRead + 1 + i, insertedBases[i]);
            readFeatures.add(insertBase);
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
     * @param baseQualities        the quality score array
     */
    // Visible for testing
    static void addMismatchReadFeatures(
            final byte[] refBases,
            final int alignmentStart,
            final List<ReadFeature> features,
            final int fromPosInRead,
            final int alignmentStartOffset,
            final int nofReadBases,
            final byte[] bases,
            final byte[] baseQualities) {
        int oneBasedPositionInRead = fromPosInRead + 1;
        int refIndex = alignmentStart + alignmentStartOffset - 1;

        byte refBase;
        for (int i = 0; i < nofReadBases; i++, oneBasedPositionInRead++, refIndex++) {
            refBase = refIndex >= refBases.length ? (byte) 'N' : refBases[refIndex];

            final byte readBase = bases[i + fromPosInRead];

            if (readBase != refBase) {
                final boolean isSubstitution =
                        SequenceUtil.isUpperACGTN(readBase) && SequenceUtil.isUpperACGTN(refBase);
                if (isSubstitution) {
                    features.add(new Substitution(oneBasedPositionInRead, readBase, refBase));
                } else {
                    final byte score = baseQualities.equals(SAMRecord.NULL_QUALS)
                            ? CRAMCompressionRecord.MISSING_QUALITY_SCORE
                            : baseQualities[i + fromPosInRead];
                    features.add(new ReadBase(oneBasedPositionInRead, readBase, score));
                }
            }
        }
    }

    /**
     * Compute the alignment end position from the read features, alignment start, and read length.
     *
     * @param alignmentStart 1-based alignment start position
     * @param readLength length of the read in bases
     * @return 1-based alignment end position
     */
    public int getAlignmentEnd(int alignmentStart, int readLength) {
        int alignmentSpan = readLength;
        if (readFeatures != null) {
            for (final ReadFeature readFeature : readFeatures) {
                // only adjust for read features that affect alignment end
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
                    case RefSkip.operator:
                        alignmentSpan += ((RefSkip) readFeature).getLength();
                        break;
                    default:
                        break;
                }
            }
        }

        return alignmentStart + alignmentSpan - 1;
    }

    /**
     * Build a {@link Cigar} from these read features and the given read length.
     *
     * @param readLength the length of the read in bases
     * @return the reconstructed CIGAR
     */
    public Cigar getCigarForReadFeatures(final int readLength) {
        if (readFeatures == null) {
            final CigarElement cigarElement = new CigarElement(readLength, CigarOperator.M);
            return new Cigar(Collections.singletonList(cigarElement));
        }

        final List<CigarElement> list = new ArrayList<>();
        CigarElement cigarElement;
        CigarOperator lastOperator = CigarOperator.MATCH_OR_MISMATCH;
        int lastOpLen = 0;
        int lastOpPos = 1;
        CigarOperator cigarOperator;
        int readFeatureLength;
        for (final ReadFeature feature : readFeatures) {

            final int gap = feature.getPosition() - (lastOpPos + lastOpLen);
            if (gap > 0) {
                if (lastOperator != CigarOperator.MATCH_OR_MISMATCH) {
                    list.add(new CigarElement(lastOpLen, lastOperator));
                    lastOpPos += lastOpLen;
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
                }
                lastOperator = cigarOperator;
                lastOpLen = readFeatureLength;
                lastOpPos = feature.getPosition();
            } else {
                lastOpLen += readFeatureLength;
            }

            if (!cigarOperator.consumesReadBases()) {
                lastOpPos -= readFeatureLength;
            }
        }

        if (lastOperator != null) {
            if (lastOperator != CigarOperator.M) {
                list.add(new CigarElement(lastOpLen, lastOperator));
                if (readLength >= lastOpPos + lastOpLen) {
                    cigarElement = new CigarElement(readLength - (lastOpLen + lastOpPos) + 1, CigarOperator.M);
                    list.add(cigarElement);
                }
            } else if (readLength == 0 || readLength > lastOpPos - 1) {
                if (readLength == 0) {
                    cigarElement = new CigarElement(lastOpLen, CigarOperator.M);
                } else {
                    cigarElement = new CigarElement(readLength - lastOpPos + 1, CigarOperator.M);
                }
                list.add(cigarElement);
            }
        }

        if (list.isEmpty()) {
            cigarElement = new CigarElement(readLength, CigarOperator.M);
            return new Cigar(Collections.singletonList(cigarElement));
        }

        return new Cigar(list);
    }

    /**
     * Get the read bases for a CRAMRecord given a set of read feaures and a reference region.
     *
     * @param readFeatures list of ReadFeatures for this record. may be null
     * @param isUnknownBases true if CF_UNKNOWN_BASES CRAM flag is set for this read
     * @param readAlignmentStart 1-based CRAM record alignment start
     * @param readLength read length for this read
     * @param cramReferenceRegion CRAMReferenceRegion spanning the reference bases required for this read,
     *                            if reference-compressed. It is the caller's responsibility to have already
     *                            fetched the correct bases (that is, the CRAMReferenceRegion's current bases
     *                            must overlap this read's reference span. It is permissible for the
     *                            region's span to be less than the entire read span in the case
     *                            where the read span exceeds beyond the end of the underlying reference
     *                            sequence.
     * @param substitutionMatrix substitution matrix to use for base resolution
     * @return byte[] of read bases for this read
     */
    public static byte[] restoreReadBases(
            final List<ReadFeature> readFeatures,
            final boolean isUnknownBases,
            final int readAlignmentStart,
            final int readLength,
            final CRAMReferenceRegion cramReferenceRegion,
            final SubstitutionMatrix substitutionMatrix) {
        if (isUnknownBases || readLength == 0) {
            return SAMRecord.NULL_SEQUENCE;
        }
        final byte[] bases = new byte[readLength];

        // ReadFeatures use a 0-based feature position, but the CRAMRecord uses SAM (1 based) coordinates
        int posInRead = 1;
        int posInSeq = 0;
        final int alignmentStart = readAlignmentStart - 1;
        final int zeroBasedReferenceOffset = cramReferenceRegion.getRegionStart();
        final byte[] referenceBases = cramReferenceRegion.getCurrentReferenceBases();

        if (readFeatures == null) {
            if (referenceBases.length + zeroBasedReferenceOffset < alignmentStart + bases.length) {
                Arrays.fill(bases, (byte) 'N');
                System.arraycopy(
                        referenceBases,
                        alignmentStart - zeroBasedReferenceOffset,
                        bases,
                        0,
                        Math.min(bases.length, referenceBases.length + zeroBasedReferenceOffset - alignmentStart));
            } else {
                System.arraycopy(referenceBases, alignmentStart - zeroBasedReferenceOffset, bases, 0, bases.length);
            }
            return SequenceUtil.toBamReadBasesInPlace(bases);
        }

        final List<ReadFeature> variations = readFeatures;
        for (final ReadFeature variation : variations) {
            for (; posInRead < variation.getPosition(); posInRead++) {
                final int rp = alignmentStart + posInSeq++ - zeroBasedReferenceOffset;
                bases[posInRead - 1] = getByteOrDefault(referenceBases, rp, (byte) 'N');
            }

            switch (variation.getOperator()) {
                case Substitution.operator:
                    byte refBase = getByteOrDefault(
                            referenceBases, alignmentStart + posInSeq - zeroBasedReferenceOffset, (byte) 'N');
                    // substitution requires ACGTN only:
                    refBase = Utils.normalizeBase(refBase);
                    final Substitution substitution = (Substitution) variation;
                    final byte base = substitutionMatrix.base(refBase, substitution.getCode());
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
                case Bases.operator:
                case ReadBase.operator:
                    break; // defer until after the reference bases are retrieved
                case Scores.operator:
                case BaseQualityScore.operator:
                    break; // handled by resolveQualityScores
                case Padding.operator:
                case HardClip.operator:
                    break; // handled by getCigarForReadFeatures
                default:
                    throw new CRAMException(
                            String.format("Unrecognized read feature code: %c", variation.getOperator()));
            }
        }

        if (referenceBases != null) {
            for (;
                    posInRead <= readLength
                            && alignmentStart + posInSeq - zeroBasedReferenceOffset < referenceBases.length;
                    posInRead++, posInSeq++) {
                bases[posInRead - 1] = referenceBases[alignmentStart + posInSeq - zeroBasedReferenceOffset];
            }
        }

        // ReadBase and Bases feature codes overwrite bases:
        for (final ReadFeature variation : variations) {
            switch (variation.getOperator()) {
                case ReadBase.operator:
                    final ReadBase readBase = (ReadBase) variation;
                    bases[variation.getPosition() - 1] = readBase.getBase();
                    break;
                case Bases.operator:
                    final Bases basesOp = (Bases) variation;
                    System.arraycopy(
                            basesOp.getBases(), 0, bases, variation.getPosition() - 1, basesOp.getBases().length);
                    break;
                default:
                    break;
            }
        }

        return SequenceUtil.toBamReadBasesInPlace(bases);
    }

    private static byte getByteOrDefault(final byte[] array, final int pos, final byte outOfBoundsValue) {
        if (array == null) {
            return outOfBoundsValue;
        }
        return pos >= array.length ? outOfBoundsValue : array[pos];
    }

    /**
     * Result of the fused single-pass decode: read bases, CIGAR, and optionally MD string + NM count.
     */
    public static final class DecodeResult {
        public final byte[] readBases;
        public final Cigar cigar;
        public final String mdString; // null if not computed
        public final int nmCount; // -1 if not computed

        DecodeResult(final byte[] readBases, final Cigar cigar, final String mdString, final int nmCount) {
            this.readBases = readBases;
            this.cigar = cigar;
            this.mdString = mdString;
            this.nmCount = nmCount;
        }
    }

    /**
     * Fused single-pass decode: restore read bases from the reference + read features, build the CIGAR,
     * and optionally compute the MD string and NM edit distance, all in a single iteration through the
     * features list. This replaces the previous 3-4 pass approach (restoreReadBases + getCigarForReadFeatures
     * + calculateMdAndNm + toBamReadBasesInPlace).
     *
     * <p>Base normalization (upper-casing, replacing invalid bases with N) is done inline as bases are
     * written, eliminating the need for a separate {@code toBamReadBasesInPlace} pass.
     *
     * @param readFeatures list of read features (may be null for pure reference matches)
     * @param isUnknownBases true if the CF_UNKNOWN_BASES flag is set
     * @param readAlignmentStart 1-based alignment start
     * @param readLength read length
     * @param cramReferenceRegion reference region covering this read's span
     * @param substitutionMatrix substitution matrix for base resolution
     * @param computeMdNm whether to compute MD string and NM count
     * @return DecodeResult containing bases, CIGAR, and optionally MD/NM
     */
    public static DecodeResult restoreBasesAndTags(
            final List<ReadFeature> readFeatures,
            final boolean isUnknownBases,
            final int readAlignmentStart,
            final int readLength,
            final CRAMReferenceRegion cramReferenceRegion,
            final SubstitutionMatrix substitutionMatrix,
            final boolean computeMdNm) {

        if (readLength == 0) {
            final Cigar cigar = new Cigar(Collections.singletonList(new CigarElement(readLength, CigarOperator.M)));
            return new DecodeResult(SAMRecord.NULL_SEQUENCE, cigar, null, -1);
        }

        // When isUnknownBases (CF_UNKNOWN_BASES / seq '*'), we still need to process read features
        // to reconstruct the CIGAR (e.g. soft clips stored in SC data series), but skip all base
        // restoration, reference lookups, and MD/NM computation.
        final byte[] bases = isUnknownBases ? null : new byte[readLength];
        final int alignmentStart = readAlignmentStart - 1; // 0-based
        final int refOffset = isUnknownBases ? 0 : cramReferenceRegion.getRegionStart();
        final byte[] refBases = isUnknownBases ? null : cramReferenceRegion.getCurrentReferenceBases();
        final boolean doBasesAndMdNm = !isUnknownBases;

        // MD/NM state — mdActive tracks whether we're still within the reference boundary.
        // Once we exceed the reference, we stop MD/NM computation (matching calculateMdAndNm's break behavior).
        int nmCount = 0;
        final boolean actuallyComputeMdNm = computeMdNm && doBasesAndMdNm;
        final StringBuilder mdString = actuallyComputeMdNm ? new StringBuilder(readLength) : null;
        int mdMatchRun = 0;
        boolean mdActive = actuallyComputeMdNm;

        // No features: pure reference match (fast path)
        if (readFeatures == null || readFeatures.isEmpty()) {
            if (isUnknownBases) {
                final Cigar cigar = new Cigar(Collections.singletonList(new CigarElement(readLength, CigarOperator.M)));
                return new DecodeResult(SAMRecord.NULL_SEQUENCE, cigar, null, -1);
            }
            final int srcStart = alignmentStart - refOffset;
            final int copyLen = Math.min(readLength, Math.max(0, refBases.length - srcStart));
            if (copyLen < readLength) {
                Arrays.fill(bases, (byte) 'N');
                if (copyLen > 0) System.arraycopy(refBases, srcStart, bases, 0, copyLen);
            } else {
                System.arraycopy(refBases, srcStart, bases, 0, readLength);
            }

            // Normalize bases and compute MD/NM — only within reference boundary
            for (int i = 0; i < readLength; i++) {
                final byte rawRef = bases[i];
                bases[i] = BAM_READ_BASE_LOOKUP[rawRef & 0x7F];
                if (computeMdNm && i < copyLen) {
                    if (SequenceUtil.basesEqual(bases[i], rawRef) || bases[i] == 0) {
                        mdMatchRun++;
                    } else {
                        mdString.append(mdMatchRun);
                        mdString.append((char) (rawRef & 0xFF));
                        mdMatchRun = 0;
                        nmCount++;
                    }
                }
            }

            if (mdActive) mdString.append(mdMatchRun);
            final Cigar cigar = new Cigar(Collections.singletonList(new CigarElement(readLength, CigarOperator.M)));
            return new DecodeResult(bases, cigar, computeMdNm ? mdString.toString() : null, computeMdNm ? nmCount : -1);
        }

        // CIGAR building state
        final List<CigarElement> cigarElements = new ArrayList<>();
        CigarOperator lastCigOp = CigarOperator.MATCH_OR_MISMATCH;
        int lastCigLen = 0;
        int lastCigPos = 1;

        // Position tracking (1-based read position, 0-based ref offset from alignment start)
        int posInRead = 1;
        int posInSeq = 0;

        for (final ReadFeature feature : readFeatures) {
            final int featurePos = feature.getPosition();

            // Fill gap from reference (advance positions; fill bases only when not unknownBases)
            if (doBasesAndMdNm) {
                while (posInRead < featurePos) {
                    final int rp = alignmentStart + posInSeq - refOffset;
                    if (rp >= refBases.length) mdActive = false;
                    final byte rawRef = getByteOrDefault(refBases, rp, (byte) 'N');
                    final byte nb = BAM_READ_BASE_LOOKUP[rawRef & 0x7F];
                    bases[posInRead - 1] = nb;
                    if (mdActive) {
                        if (SequenceUtil.basesEqual(nb, rawRef) || nb == 0) {
                            mdMatchRun++;
                        } else {
                            mdString.append(mdMatchRun);
                            mdString.append((char) (rawRef & 0xFF));
                            mdMatchRun = 0;
                            nmCount++;
                        }
                    }
                    posInRead++;
                    posInSeq++;
                }
            } else {
                final int gap = featurePos - posInRead;
                posInSeq += gap;
                posInRead = featurePos;
            }

            // Deactivate MD/NM if the current reference position is beyond the reference boundary,
            // flushing any accumulated match run first
            if (mdActive && (alignmentStart + posInSeq - refOffset) >= refBases.length) {
                mdString.append(mdMatchRun);
                mdMatchRun = 0;
                mdActive = false;
            }

            // CIGAR gap
            final int gap = featurePos - (lastCigPos + lastCigLen);
            if (gap > 0) {
                if (lastCigOp != CigarOperator.MATCH_OR_MISMATCH) {
                    cigarElements.add(new CigarElement(lastCigLen, lastCigOp));
                    lastCigPos += lastCigLen;
                    lastCigLen = gap;
                } else {
                    lastCigLen += gap;
                }
                lastCigOp = CigarOperator.MATCH_OR_MISMATCH;
            }

            CigarOperator featureCigOp;
            int featureCigLen;

            switch (feature.getOperator()) {
                case Substitution.operator: {
                    if (doBasesAndMdNm) {
                        final int rp = alignmentStart + posInSeq - refOffset;
                        final byte rawRef = getByteOrDefault(refBases, rp, (byte) 'N');
                        final byte normRef = Utils.normalizeBase(rawRef);
                        bases[posInRead - 1] = BAM_READ_BASE_LOOKUP[
                                substitutionMatrix.base(normRef, ((Substitution) feature).getCode()) & 0x7F];
                        if (mdActive) {
                            mdString.append(mdMatchRun);
                            mdString.append((char) (rawRef & 0xFF));
                            mdMatchRun = 0;
                            nmCount++;
                        }
                    }
                    posInRead++;
                    posInSeq++;
                    featureCigOp = CigarOperator.MATCH_OR_MISMATCH;
                    featureCigLen = 1;
                    break;
                }
                case ReadBase.operator: {
                    if (doBasesAndMdNm) {
                        final byte readBase = BAM_READ_BASE_LOOKUP[((ReadBase) feature).getBase() & 0x7F];
                        bases[posInRead - 1] = readBase;
                        if (mdActive) {
                            final int rp = alignmentStart + posInSeq - refOffset;
                            final byte rawRef = getByteOrDefault(refBases, rp, (byte) 'N');
                            if (SequenceUtil.basesEqual(readBase, rawRef)) {
                                mdMatchRun++;
                            } else {
                                mdString.append(mdMatchRun);
                                mdString.append((char) (rawRef & 0xFF));
                                mdMatchRun = 0;
                                nmCount++;
                            }
                        }
                    }
                    posInRead++;
                    posInSeq++;
                    featureCigOp = CigarOperator.MATCH_OR_MISMATCH;
                    featureCigLen = 1;
                    break;
                }
                case Bases.operator: {
                    final byte[] fb = ((Bases) feature).getBases();
                    if (doBasesAndMdNm) {
                        for (int i = 0; i < fb.length; i++) {
                            bases[posInRead - 1 + i] = BAM_READ_BASE_LOOKUP[fb[i] & 0x7F];
                            if (mdActive) {
                                final int rp = alignmentStart + posInSeq + i - refOffset;
                                final byte rawRef = getByteOrDefault(refBases, rp, (byte) 'N');
                                if (SequenceUtil.basesEqual(bases[posInRead - 1 + i], rawRef)) {
                                    mdMatchRun++;
                                } else {
                                    mdString.append(mdMatchRun);
                                    mdString.append((char) (rawRef & 0xFF));
                                    mdMatchRun = 0;
                                    nmCount++;
                                }
                            }
                        }
                    }
                    posInRead += fb.length;
                    posInSeq += fb.length;
                    continue; // Bases are within M region, no CIGAR update
                }
                case Insertion.operator: {
                    final byte[] seq = ((Insertion) feature).getSequence();
                    if (doBasesAndMdNm) {
                        for (int i = 0; i < seq.length; i++)
                            bases[posInRead - 1 + i] = BAM_READ_BASE_LOOKUP[seq[i] & 0x7F];
                        if (mdActive) nmCount += seq.length;
                    }
                    posInRead += seq.length;
                    featureCigOp = CigarOperator.INSERTION;
                    featureCigLen = seq.length;
                    break;
                }
                case InsertBase.operator: {
                    if (doBasesAndMdNm) {
                        bases[posInRead - 1] = BAM_READ_BASE_LOOKUP[((InsertBase) feature).getBase() & 0x7F];
                        if (mdActive) nmCount++;
                    }
                    posInRead++;
                    featureCigOp = CigarOperator.INSERTION;
                    featureCigLen = 1;
                    break;
                }
                case SoftClip.operator: {
                    final byte[] seq = ((SoftClip) feature).getSequence();
                    if (doBasesAndMdNm) {
                        for (int i = 0; i < seq.length; i++)
                            bases[posInRead - 1 + i] = BAM_READ_BASE_LOOKUP[seq[i] & 0x7F];
                    }
                    posInRead += seq.length;
                    featureCigOp = CigarOperator.SOFT_CLIP;
                    featureCigLen = seq.length;
                    break;
                }
                case Deletion.operator: {
                    final int delLen = ((Deletion) feature).getLength();
                    if (mdActive) {
                        mdString.append(mdMatchRun);
                        mdMatchRun = 0;
                        mdString.append('^');
                        for (int i = 0; i < delLen; i++) {
                            final int rp = alignmentStart + posInSeq + i - refOffset;
                            final byte rawRef = getByteOrDefault(refBases, rp, (byte) 'N');
                            mdString.append((char) (rawRef & 0xFF));
                        }
                        nmCount += delLen;
                    }
                    posInSeq += delLen;
                    featureCigOp = CigarOperator.DELETION;
                    featureCigLen = delLen;
                    break;
                }
                case RefSkip.operator:
                    posInSeq += ((RefSkip) feature).getLength();
                    featureCigOp = CigarOperator.SKIPPED_REGION;
                    featureCigLen = ((RefSkip) feature).getLength();
                    break;
                case HardClip.operator:
                    featureCigOp = CigarOperator.HARD_CLIP;
                    featureCigLen = ((HardClip) feature).getLength();
                    break;
                case Padding.operator:
                    featureCigOp = CigarOperator.PADDING;
                    featureCigLen = ((Padding) feature).getLength();
                    break;
                case Scores.operator:
                case BaseQualityScore.operator:
                    continue;
                default:
                    throw new CRAMException(String.format("Unrecognized read feature code: %c", feature.getOperator()));
            }

            // Update CIGAR
            if (lastCigOp != featureCigOp) {
                if (lastCigLen > 0) cigarElements.add(new CigarElement(lastCigLen, lastCigOp));
                lastCigOp = featureCigOp;
                lastCigLen = featureCigLen;
                lastCigPos = feature.getPosition();
            } else {
                lastCigLen += featureCigLen;
            }
            if (!featureCigOp.consumesReadBases()) lastCigPos -= featureCigLen;
        }

        // Fill trailing reference bases (skip when unknownBases -- just advance positions)
        if (doBasesAndMdNm) {
            while (posInRead <= readLength) {
                final int rp = alignmentStart + posInSeq - refOffset;
                if (rp >= refBases.length) {
                    if (mdActive) {
                        mdString.append(mdMatchRun);
                        mdMatchRun = 0;
                        mdActive = false;
                    }
                    while (posInRead <= readLength) {
                        bases[posInRead - 1] = 'N';
                        posInRead++;
                    }
                    break;
                }
                final byte rawRef = refBases[rp];
                bases[posInRead - 1] = BAM_READ_BASE_LOOKUP[rawRef & 0x7F];
                if (mdActive) {
                    if (SequenceUtil.basesEqual(bases[posInRead - 1], rawRef) || bases[posInRead - 1] == 0) {
                        mdMatchRun++;
                    } else {
                        mdString.append(mdMatchRun);
                        mdString.append((char) (rawRef & 0xFF));
                        mdMatchRun = 0;
                        nmCount++;
                    }
                }
                posInRead++;
                posInSeq++;
            }
        } else {
            posInRead = readLength + 1;
        }

        // Finalize CIGAR
        if (lastCigOp != CigarOperator.M) {
            if (lastCigLen > 0) cigarElements.add(new CigarElement(lastCigLen, lastCigOp));
            if (readLength >= lastCigPos + lastCigLen) {
                cigarElements.add(new CigarElement(readLength - (lastCigLen + lastCigPos) + 1, CigarOperator.M));
            }
        } else if (readLength > lastCigPos - 1) {
            cigarElements.add(new CigarElement(readLength - lastCigPos + 1, CigarOperator.M));
        }

        final Cigar cigar = cigarElements.isEmpty()
                ? new Cigar(Collections.singletonList(new CigarElement(readLength, CigarOperator.M)))
                : new Cigar(cigarElements);

        if (mdActive) mdString.append(mdMatchRun);

        return new DecodeResult(
                isUnknownBases ? SAMRecord.NULL_SEQUENCE : bases,
                cigar,
                actuallyComputeMdNm ? mdString.toString() : null,
                actuallyComputeMdNm ? nmCount : -1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CRAMRecordReadFeatures that = (CRAMRecordReadFeatures) o;

        return getReadFeaturesList() != null
                ? getReadFeaturesList().equals(that.getReadFeaturesList())
                : that.getReadFeaturesList() == null;
    }

    @Override
    public int hashCode() {
        return getReadFeaturesList() != null ? getReadFeaturesList().hashCode() : 0;
    }
}
