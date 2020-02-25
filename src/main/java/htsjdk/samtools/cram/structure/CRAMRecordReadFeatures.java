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
import htsjdk.samtools.cram.build.Utils;
import htsjdk.samtools.cram.encoding.readfeatures.*;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.utils.ValidationUtils;

import java.util.*;

/**
 * Class for handling the read features for a {@link CRAMCompressionRecord}.
 */
public class CRAMRecordReadFeatures {
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
            //for SAMRecords with SEQ="*", manufacture 'N's
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

    public final List<ReadFeature> getReadFeaturesList() { return readFeatures; }

    private void addSoftClip(
            final int zeroBasedPositionInRead,
            final int cigarElementLength,
            final byte[] readBases) {
        final byte[] insertedBases = Arrays.copyOfRange(
                readBases,
                zeroBasedPositionInRead,
                zeroBasedPositionInRead + cigarElementLength);
        readFeatures.add(new SoftClip(zeroBasedPositionInRead + 1, insertedBases));
    }

    private void addInsertion(
            final int zeroBasedPositionInRead,
            final int cigarElementLength,
            final byte[] readBases) {
        final byte[] insertedBases = Arrays.copyOfRange(
                readBases,
                zeroBasedPositionInRead,
                zeroBasedPositionInRead + cigarElementLength);
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
    //Visible for testing
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
            refBase = refIndex >= refBases.length ?
                    (byte) 'N' :
                    refBases[refIndex];

            final byte readBase = bases[i + fromPosInRead];

            if (readBase != refBase) {
                final boolean isSubstitution = SequenceUtil.isUpperACGTN(readBase) && SequenceUtil.isUpperACGTN(refBase);
                if (isSubstitution) {
                    features.add(new Substitution(oneBasedPositionInRead, readBase, refBase));
                } else {
                    final byte score =
                            baseQualities.equals(SAMRecord.NULL_QUALS) ?
                                    CRAMCompressionRecord.MISSING_QUALITY_SCORE :
                                    baseQualities[i + fromPosInRead] ;
                    features.add(new ReadBase(oneBasedPositionInRead, readBase, score));
                }
            }
        }
    }

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
     * Get the set of readBases given these read features.
     * @param isUnknownBases
     * @param readAlignmentStart 1-based alignment start for this record
     * @param readLength
     * @param referenceBases
     * @param zeroBasedReferenceOffset
     * @param substitutionMatrix
     * @return
     */
    public static byte[] restoreReadBases(
            final List<ReadFeature> readFeatures,
            final boolean isUnknownBases,
            final int readAlignmentStart,
            final int readLength,
            final byte[] referenceBases,
            final int zeroBasedReferenceOffset,
            final SubstitutionMatrix substitutionMatrix) {
        if (isUnknownBases || readLength == 0) {
            return SAMRecord.NULL_SEQUENCE;
        }
        final byte[] bases = new byte[readLength];

        // ReadFeatures use a 0-based feature position, but the CRAMRecord uses SAM (1 based) coordinates
        int posInRead = 1;
        final int alignmentStart = readAlignmentStart - 1;

        int posInSeq = 0;
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
                System.arraycopy(
                        referenceBases,
                        alignmentStart - zeroBasedReferenceOffset,
                        bases,
                        0,
                        bases.length);
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
                    byte refBase = getByteOrDefault(referenceBases, alignmentStart + posInSeq - zeroBasedReferenceOffset, (byte) 'N');
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
                case Bases.operator:
                    final Bases readBases = (Bases) variation;
                    for (byte b : readBases.getBases()) {
                        bases[posInRead++ - 1] = b;
                    }
                    break;
                case RefSkip.operator:
                    posInSeq += ((RefSkip) variation).getLength();
                    break;
            }
        }

        for (; posInRead <= readLength
                && alignmentStart + posInSeq - zeroBasedReferenceOffset < referenceBases.length; posInRead++, posInSeq++) {
            bases[posInRead - 1] = referenceBases[alignmentStart + posInSeq - zeroBasedReferenceOffset];
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

        return getReadFeaturesList() != null ?
                getReadFeaturesList().equals(that.getReadFeaturesList()) :
                that.getReadFeaturesList() == null;
    }

    @Override
    public int hashCode() {
        return getReadFeaturesList() != null ? getReadFeaturesList().hashCode() : 0;
    }
}
