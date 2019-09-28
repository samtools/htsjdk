package htsjdk.samtools.cram.structure;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.build.Utils;
import htsjdk.samtools.cram.encoding.readfeatures.*;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.utils.ValidationUtils;

import java.util.*;

/**
 * Class for handling the read features in CRAMRecord.
 */
public class CRAMRecordReadFeatures {
    //TODO: this used to be a LinkedList but there seems to be no reason for that ??
    final List<ReadFeature> readFeatures;

    /**
     * Create a CRAMRecordReadFeatures.
     */
    public CRAMRecordReadFeatures() {
        this(Collections.EMPTY_LIST);
    }

    /**
     * Create a CRAMRecordReadFeatures froma list of read features consumed from a stream.
     * @param readFeatures
     */
    public CRAMRecordReadFeatures(final List<ReadFeature> readFeatures) {
        ValidationUtils.nonNull(readFeatures);
        this.readFeatures = readFeatures;
    }

    /**
     * Create the read features for a given SAMRecord
     * @param samRecord
     * @param refBases
     */
    public CRAMRecordReadFeatures(final SAMRecord samRecord, final byte[] refBases) {
        readFeatures = new ArrayList<>();
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
            final int cigarElementLength = cigarElement.getLength();
            final CigarOperator operator = cigarElement.getOperator();
            switch (operator) {
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
                    addSoftClip(zeroBasedPositionInRead, cigarElementLength, bases);
                    break;
                case I:
                    addInsertion(zeroBasedPositionInRead, cigarElementLength, bases);
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

        //was used in Sam2CramRecordFactory for sequentialIndex error reporting
        //this.baseCount += bases.length;
        //this.featureCount += features.size();
    }

    public final List<ReadFeature> getReadFeatures() { return readFeatures; }

    private void addSoftClip(
            final int zeroBasedPositionInRead,
            final int cigarElementLength,
            final byte[] bases) {
        final byte[] insertedBases = Arrays.copyOfRange(
                bases,
                zeroBasedPositionInRead,
                zeroBasedPositionInRead + cigarElementLength);
        readFeatures.add(new SoftClip(zeroBasedPositionInRead + 1, insertedBases));
    }

    private void addInsertion(
            final int zeroBasedPositionInRead,
            final int cigarElementLength,
            final byte[] bases) {
        final byte[] insertedBases = Arrays.copyOfRange(
                bases,
                zeroBasedPositionInRead,
                zeroBasedPositionInRead + cigarElementLength);
        for (int i = 0; i < insertedBases.length; i++) {
            //TODO: why does this use N InsertBase features instead of a single
            // single base insertion:
            final InsertBase insertBase = new InsertBase();
            insertBase.setPosition(zeroBasedPositionInRead + 1 + i);
            insertBase.setBase(insertedBases[i]);
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
     * @param qualityScore         the quality score array
     */
    //TODO: static/protected for tests
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

        //TODO: NPE surfaces here if no ref bases (or ref mismatch) ?
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

    // https://github.com/samtools/htsjdk/issues/1301
    // does not update alignmentSpan/alignmentEnd when the record changes
    public int initializeAlignmentEnd(int alignmentStart, int readLength) {
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
     * Get a Cigar fo this set of read features.
     * @param readLength
     * @return
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

    /**
     * Get the set of readBases given these read features.
     * @param isUnknownBases
     * @param alignStart
     * @param readLength
     * @param ref
     * @param refOffsetZeroBased
     * @param substitutionMatrix
     * @return
     */
    public static byte[] restoreReadBases(
            final List<ReadFeature> readFeatures,
            final boolean isUnknownBases,
            final int alignStart,
            final int readLength,
            final byte[] ref,
            final int refOffsetZeroBased,
            final SubstitutionMatrix substitutionMatrix) {
        if (isUnknownBases || readLength == 0) {
            return SAMRecord.NULL_SEQUENCE;
        }
        final byte[] bases = new byte[readLength];

        int posInRead = 1;
        //TODO: why is this -1 ? 1/0 based coord transform ?
        final int alignmentStart = alignStart - 1;

        int posInSeq = 0;
        if (readFeatures == null) {
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

    private static byte getByteOrDefault(final byte[] array, final int pos, final byte outOfBoundsValue) {
        return pos >= array.length ?
                outOfBoundsValue :
                array[pos];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CRAMRecordReadFeatures that = (CRAMRecordReadFeatures) o;

        return getReadFeatures() != null ? getReadFeatures().equals(that.getReadFeatures()) : that.getReadFeatures() == null;
    }

    @Override
    public int hashCode() {
        return getReadFeatures() != null ? getReadFeatures().hashCode() : 0;
    }
}
